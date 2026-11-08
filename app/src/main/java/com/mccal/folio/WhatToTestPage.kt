package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Settings > Help > What to Test, for beta builds: the testers' checklist from `assets/what-to-test.json`, most likely
 * broken first. Each item opens to its steps and three answers (It Worked, Something's Wrong, Skip). Something's Wrong
 * goes to the same two ways to report a bug as Help does, with the item's name added. The ticks stay on the phone.
 */
@Composable
internal fun WhatToTestPage(onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val list = remember { WhatToTest.load(context) }
    if (list == null) {
        CardNote(stringResource(R.string.what_to_test_none), Modifier.padding(horizontal = FolioSpace.LARGE.dp))
        return
    }
    val results = remember(list.release) { WhatToTest.Results(context, list.release) }
    var answers by remember(list.release) { mutableStateOf(results.all(list)) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf<TestItem?>(null) }
    val reportScope = rememberCoroutineScope()
    fun answer(item: TestItem, result: WhatToTest.Result) {
        results.set(item.id, result)
        answers = results.all(list)
        if (result == WhatToTest.Result.BAD) reporting = item
        // Move on to what is next: close this item, so the list shows what is left.
        open = null
    }

    CardNote(stringResource(R.string.what_to_test_note, list.release), Modifier.padding(horizontal = FolioSpace.LARGE.dp))
    val done = WhatToTest.answered(list, answers)
    val progress = stringResource(R.string.what_to_test_progress, done, list.items.size)
    Column(Modifier.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp).semantics { contentDescription = progress }) {
        Text(progress, color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.padding(top = FolioSpace.SMALL.dp).fillMaxWidth().size(width = 0.dp, height = 8.dp).background(Color.White.copy(alpha = .2f), CircleShape)) {
            Box(Modifier.fillMaxWidth(if (list.items.isEmpty()) 0f else done.toFloat() / list.items.size).size(width = 0.dp, height = 8.dp)
                .background(LocalAccent.current.fill, CircleShape))
        }
    }
    val todo = list.items.filter { it.id !in answers }
    val finished = list.items.filter { it.id in answers }
    TestSection(stringResource(R.string.what_to_test_to_try, todo.size), todo, answers, open, { open = if (open == it.id) null else it.id }, ::answer, onOpenSetup)
    TestSection(stringResource(R.string.what_to_test_done_label, finished.size), finished, answers, open, { open = if (open == it.id) null else it.id }, ::answer, onOpenSetup)
    reporting?.let { item ->
        val note = item.title
        AlertDialog(onDismissRequest = { reporting = null },
            title = { Text(stringResource(R.string.how_should_this_report_go)) },
            text = { Text(stringResource(R.string.email_needs_no_account)) },
            confirmButton = { TextButton(onClick = { reporting = null
                reportScope.launch { runCatching { context.startActivity(Diagnostics.reportIntent(context, email = true, note = note)) } } },
                modifier = Modifier.testTag("what-to-test-report-email")) { Text(stringResource(R.string.email_a_report)) } },
            dismissButton = { TextButton(onClick = { reporting = null
                reportScope.launch {
                    Diagnostics.copy(context)
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(BugReport.url(context, note)))) }
                } },
                modifier = Modifier.testTag("what-to-test-report-github")) { Text(stringResource(R.string.use_github_instead)) } })
    }
}

/** A heading and its entries; nothing at all when the list is empty, so a finished list shows only Done. */
@Composable
private fun TestSection(label: String, items: List<TestItem>, answers: Map<String, WhatToTest.Result>, open: String?,
    onToggle: (TestItem) -> Unit, onAnswer: (TestItem, WhatToTest.Result) -> Unit, onOpenSetup: () -> Unit) {
    if (items.isEmpty()) return
    SheetGroupLabel(label)
    items.forEach { item -> TestEntry(item, answers[item.id], open == item.id, { onToggle(item) }, { onAnswer(item, it) }, onOpenSetup) }
}

/** One entry the way Apple's What's New writes one: a colored tile, a bold title, a gray line, and how it went on the right. */
@Composable
internal fun TestEntry(item: TestItem, result: WhatToTest.Result?, isOpen: Boolean, onToggle: () -> Unit, onAnswer: (WhatToTest.Result) -> Unit, onOpenSetup: () -> Unit) {
    val stateText = when (result) {
        WhatToTest.Result.OK -> stringResource(R.string.it_worked)
        WhatToTest.Result.BAD -> stringResource(R.string.something_is_wrong)
        WhatToTest.Result.SKIP -> stringResource(R.string.skip)
        null -> stringResource(R.string.what_to_test_not_tried)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp).testTag("what-to-test-${item.id}")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onToggle).padding(vertical = FolioSpace.SMALL.dp)
            .semantics(mergeDescendants = true) { stateDescription = stateText }, verticalAlignment = Alignment.CenterVertically) {
            EntryTile(item.title)
            Column(Modifier.padding(start = FolioSpace.LARGE.dp, end = FolioSpace.MEDIUM.dp).weight(1f)) {
                Text(item.title, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
                item.steps.firstOrNull()?.let { Text(it, color = Color.White.copy(alpha = .75f), fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(top = FolioSpace.HAIR.dp)) }
            }
            TestStatus(result)
        }
        if (isOpen) {
            Column(Modifier.padding(start = 38.dp + FolioSpace.LARGE.dp)) {
                if (item.where.isNotBlank()) Text(item.where, color = Color.White.copy(alpha = .75f), fontSize = FolioType.FOOTNOTE.sp)
                item.steps.drop(1).forEach { Text(it, color = Color.White.copy(alpha = .9f), fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(top = FolioSpace.TINY.dp)) }
            }
            if (item.action == "setup") IosActionRow(stringResource(R.string.open_the_new_setup), "what-to-test-open-setup", onClick = onOpenSetup)
            Row(Modifier.fillMaxWidth().padding(top = FolioSpace.SMALL.dp, bottom = FolioSpace.SMALL.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                AnswerButton(stringResource(R.string.it_worked), LocalAccent.current.fill, Color.White, "what-to-test-worked", Modifier.weight(1f)) { onAnswer(WhatToTest.Result.OK) }
                AnswerButton(stringResource(R.string.something_is_wrong), Color.White.copy(alpha = .14f), Color(0xFFFF9D94), "what-to-test-wrong", Modifier.weight(1f)) { onAnswer(WhatToTest.Result.BAD) }
                AnswerButton(stringResource(R.string.skip), Color.White.copy(alpha = .14f), Color.White, "what-to-test-skip", Modifier.weight(1f)) { onAnswer(WhatToTest.Result.SKIP) }
            }
        }
    }
}

/** Folio's own icon tile (as in Settings): a solid color, a white glyph, picked from the words in the title. */
@Composable
internal fun EntryTile(title: String, size: Dp = 38.dp) {
    val (icon, color) = remember(title) { WhatsNew.symbol(title) }
    Box(Modifier.size(size).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(Color(color)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .6f))
    }
}

/** How it went, as a symbol and not only a color: a check, an exclamation mark, a dash, or an empty ring. */
@Composable
internal fun TestStatus(result: WhatToTest.Result?, filled: Boolean = true) {
    val fill = when (result) { WhatToTest.Result.OK -> LocalAccent.current.fill; WhatToTest.Result.BAD -> Color(0xFFC4001A); else -> Color.Transparent }
    val ring = if (result == WhatToTest.Result.OK || result == WhatToTest.Result.BAD) fill else Color.White.copy(alpha = .7f)
    Box(Modifier.size(28.dp).clip(CircleShape).background(if (filled) fill else Color.Transparent).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        Text(when (result) { WhatToTest.Result.OK -> "✓"; WhatToTest.Result.BAD -> "!"; WhatToTest.Result.SKIP -> "–"; null -> "" },
            color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnswerButton(text: String, background: Color, color: Color, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(background).clickable(onClick = onClick)
        .padding(horizontal = FolioSpace.SMALL.dp).testTag(tag), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
