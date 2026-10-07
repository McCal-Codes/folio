package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    SheetGroup {
        list.items.forEachIndexed { index, item ->
            if (index > 0) MenuDivider()
            val result = answers[item.id]
            val isOpen = open == item.id
            val stateText = when (result) {
                WhatToTest.Result.OK -> stringResource(R.string.it_worked)
                WhatToTest.Result.BAD -> stringResource(R.string.something_is_wrong)
                WhatToTest.Result.SKIP -> stringResource(R.string.skip)
                null -> stringResource(R.string.what_to_test_not_tried)
            }
            Column(Modifier.fillMaxWidth().testTag("what-to-test-${item.id}")) {
                Row(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp).clickable { open = if (isOpen) null else item.id }
                    .padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp)
                    .semantics(mergeDescendants = true) { stateDescription = stateText },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(when (result) { WhatToTest.Result.OK -> "✓"; WhatToTest.Result.BAD -> "!"; WhatToTest.Result.SKIP -> "–"; null -> "○" },
                        color = if (result == WhatToTest.Result.BAD) Color(0xFFFF6B63) else Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontSize = FolioType.BODY.sp)
                        if (item.where.isNotBlank()) Text(item.where, color = Color.White.copy(alpha = .75f), fontSize = FolioType.FOOTNOTE.sp)
                    }
                }
                if (isOpen) {
                    Column(Modifier.padding(start = FolioSpace.LARGE.dp + 28.dp, end = FolioSpace.LARGE.dp, bottom = FolioSpace.SMALL.dp)) {
                        item.steps.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", color = Color.White.copy(alpha = .9f), fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(bottom = FolioSpace.TINY.dp))
                        }
                    }
                    if (item.action == "setup") IosActionRow(stringResource(R.string.open_the_new_setup), "what-to-test-open-setup", onClick = onOpenSetup)
                    IosActionRow(stringResource(R.string.it_worked), "what-to-test-worked", onClick = { answer(item, WhatToTest.Result.OK) })
                    IosActionRow(stringResource(R.string.something_is_wrong), "what-to-test-wrong", destructive = true, onClick = { answer(item, WhatToTest.Result.BAD) })
                    IosActionRow(stringResource(R.string.skip), "what-to-test-skip", onClick = { answer(item, WhatToTest.Result.SKIP) })
                }
            }
        }
    }
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
