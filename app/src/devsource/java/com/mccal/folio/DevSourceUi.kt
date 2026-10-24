package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/*
 * The Folio Dev page and its row in the Market's Sources tab. Nothing here animates: progress is a number, not a bar
 * that moves, so Reduce Motion has nothing to turn off and nothing can flash.
 */

private val Amber = FolioColors.Orange

/** The Sources tab row: where the Folio Dev builds are. Says what is missing before it says anything else. */
@Composable
internal fun DevSourceRow(selected: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val name = stringResource(R.string.dev_source_name)
    val hasCode = remember(selected) { DevSource.code(context) != null }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .background(if (selected) Color.White.copy(alpha = .06f) else Color.Transparent)
            .clickable(onClickLabel = name, onClick = onOpen)
            .padding(FolioSpace.COMFY.dp)
            .testTag("market-dev-source-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Build, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = FolioSpace.COMPACT.dp).weight(1f)) {
            Text(name, color = Color.White, fontSize = FolioType.CALLOUT.sp)
            Text(stringResource(if (hasCode) R.string.dev_source_row_ready else R.string.dev_source_row_needs_code),
                color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.size(18.dp).mirroredForRtl())
    }
}

/** The Folio Dev page: the dev code, the signed list of builds with Install, and the WIP tweaks source. */
@Composable
internal fun DevSourcePage(showBack: Boolean, backTitle: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val list by DevSource.list.collectAsStateWithLifecycle()
    val work by DevSource.work.collectAsStateWithLifecycle()
    var hasCode by remember { mutableStateOf(DevSource.code(context) != null) }
    var stub by remember { mutableStateOf(DevSource.loopback(context)) }
    var rejected by remember { mutableStateOf(false) }
    var tweaksAdded by remember { mutableStateOf(false) }
    val running = remember { DevSource.running(context) }
    // Opening the page is asking, once: a list already shown is not fetched again until Check is tapped.
    LaunchedEffect(hasCode, stub) { if (hasCode && list == DevSource.ListState.Idle) DevSource.refresh(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = FolioSpace.LARGE.dp).testTag("market-dev-source-page")) {
        if (showBack) {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.back), onClick = onBack).padding(vertical = FolioSpace.COMPACT.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = LocalAccent.current.ink, modifier = Modifier.size(18.dp).mirroredForRtl())
                Text(backTitle, color = LocalAccent.current.ink, fontSize = FolioType.CALLOUT.sp)
            }
        }
        Row(Modifier.padding(top = FolioSpace.COMPACT.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Build, contentDescription = null, tint = Amber, modifier = Modifier.size(34.dp))
            Column(Modifier.padding(start = FolioSpace.MEDIUM.dp)) {
                Text(stringResource(R.string.dev_source_name), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.dev_source_row_ready), color = Color.White.copy(alpha = .55f), fontSize = FolioType.COMPACT.sp)
            }
        }

        SheetGroupLabel(stringResource(R.string.dev_source_code_label))
        if (!hasCode) {
            CodeField(rejected) { text ->
                if (DevSource.redeem(context, text)) { hasCode = true; rejected = false; DevSource.refresh(context) } else rejected = true
            }
        } else {
            SheetGroup(Modifier.padding(bottom = FolioSpace.MEDIUM.dp)) {
                IosActionRow(stringResource(R.string.dev_source_forget_code), "dev-source-forget", destructive = true) {
                    DevSource.forgetCode(context); hasCode = false
                }
            }
        }

        if (hasCode) {
            SheetGroupLabel(stringResource(R.string.dev_source_builds))
            when (val state = list) {
                is DevSource.ListState.Loaded -> BuildsList(state, running, work, context)
                is DevSource.ListState.Failed -> CardNote(stringResource(failureText(state.failure)))
                else -> CardNote(stringResource(R.string.dev_source_checking))
            }
            WorkNote(work)
            SheetGroup(Modifier.padding(top = FolioSpace.SMALL.dp)) {
                IosActionRow(stringResource(R.string.dev_source_check), "dev-source-check", enabled = list != DevSource.ListState.Loading) {
                    DevSource.refresh(context)
                }
            }
            SheetGroup(Modifier.padding(top = FolioSpace.SMALL.dp)) {
                IosActionRow(stringResource(R.string.dev_source_tweaks_add), "dev-source-tweaks", enabled = !stub) {
                    addTweaksSource(context); tweaksAdded = true
                }
            }
            CardNote(stringResource(if (tweaksAdded) R.string.dev_source_tweaks_added else R.string.dev_source_tweaks_note),
                Modifier.padding(top = FolioSpace.SMALL.dp))
        }

        BetaSection()

        SheetGroup(Modifier.padding(top = FolioSpace.MEDIUM.dp)) {
            IosCheckRow(stringResource(R.string.dev_source_local_stub), stub, { DevSource.setLoopback(context, true); stub = true }, "dev-source-stub-on")
            if (stub) {
                MenuDivider()
                IosActionRow(stringResource(R.string.dev_source_stub_off), "dev-source-stub-off") {
                    DevSource.setLoopback(context, false); stub = false
                }
            }
        }
        if (stub) CardNote(stringResource(R.string.dev_source_local_stub_on), Modifier.padding(top = FolioSpace.SMALL.dp))
        CardNote(stringResource(R.string.dev_source_page_note), Modifier.padding(top = FolioSpace.MEDIUM.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BuildsList(state: DevSource.ListState.Loaded, running: DevBuildInfo?, work: DevSource.Work, context: android.content.Context) {
    if (state.builds.isEmpty()) { CardNote(stringResource(R.string.dev_source_none)); return }
    val busy = work is DevSource.Work.Downloading || work is DevSource.Work.Installing
    val installedCode = remember { DevSource.installedCode(context) }
    SheetGroup {
        state.builds.forEachIndexed { index, build ->
            if (index > 0) MenuDivider()
            val here = running?.sha == build.sha
            Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp).testTag("dev-build-${build.name}"),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(build.branch, color = Color.White, fontSize = FolioType.CALLOUT.sp, maxLines = 1)
                        if (build.wip) Badge(stringResource(R.string.dev_source_wip))
                    }
                    Text(DevBuilds.displayVersion(build, state.builds), color = Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp)
                    Text(build.subject.ifBlank { build.sha }, color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 2)
                    Text(stringResource(R.string.dev_source_built_with, build.builtAt.take(16).replace('T', ' '),
                        android.text.format.Formatter.formatShortFileSize(context, build.size)),
                        color = Color.White.copy(alpha = .45f), fontSize = FolioType.FOOTNOTE.sp)
                    // Only for a build older than the one installed: the running build and anything newer have nothing to warn about.
                    if (!here && DevBuilds.olderThanInstalled(build, installedCode)) {
                        Text(stringResource(R.string.dev_source_older_build), color = FolioColors.Warning, fontSize = FolioType.FOOTNOTE.sp)
                    }
                }
                if (here) Text(stringResource(R.string.dev_source_running), color = FolioColors.Green, fontSize = FolioType.FOOTNOTE.sp)
                else Text(stringResource(R.string.dev_source_install),
                    color = if (busy) Color.White.copy(alpha = .3f) else LocalAccent.current.ink, fontSize = FolioType.BODY.sp,
                    modifier = Modifier.heightIn(min = 44.dp).clickable(enabled = !busy, onClickLabel = build.name) { DevSource.install(context, build) }
                        .padding(start = FolioSpace.MEDIUM.dp, top = 10.dp).testTag("dev-install-${build.name}"))
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(text, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = FolioSpace.COMPACT.dp).clip(RoundedCornerShape(4.dp)).background(Amber).padding(horizontal = 5.dp, vertical = 1.dp))
}

@Composable
private fun WorkNote(work: DevSource.Work) {
    when (work) {
        is DevSource.Work.Downloading -> CardNote(stringResource(R.string.dev_source_downloading) + (work.fraction?.let { " " + (it * 100).toInt() + "%" } ?: ""))
        DevSource.Work.Installing -> CardNote(stringResource(R.string.dev_source_installing))
        is DevSource.Work.Refused -> CardNote(stringResource(refusalText(work.reason)))
        DevSource.Work.Failed -> CardNote(stringResource(R.string.dev_source_work_failed))
        DevSource.Work.Idle -> Unit
    }
}

/** The supporter beta, tried from Folio Dev: separate from the dev builds above, with its own code, server and confirmation. */
@Composable
private fun BetaSection() {
    val context = LocalContext.current
    val state by DevBeta.state.collectAsStateWithLifecycle()
    val installed = remember { DevBeta.installedFolio(context) }
    val open = remember { DevBeta.openIntent(context, FOLIO_CLASSES) }
    SheetGroupLabel(stringResource(R.string.dev_beta_title))
    SheetGroup {
        Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp)) {
            Text(stringResource(if (installed != null) R.string.dev_beta_installed else R.string.dev_beta_not_installed), color = Color.White, fontSize = FolioType.CALLOUT.sp)
            installed?.versionName?.let {
                Text(stringResource(R.string.dev_beta_version, it), color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp)
            }
        }
        MenuDivider()
        IosActionRow(stringResource(R.string.dev_beta_check), "dev-beta-check",
            enabled = state !is DevBeta.State.Checking && state !is DevBeta.State.Downloading && state !is DevBeta.State.Installing) { DevBeta.check(context) }
        val found = state as? DevBeta.State.Found ?: (state as? DevBeta.State.Asking)?.let { DevBeta.State.Found(it.release, true) }
        if (found != null && found.newer && installed != null) {
            MenuDivider()
            IosActionRow(stringResource(R.string.dev_beta_install), "dev-beta-install") { DevBeta.ask(found.release) }
        }
        if (open != null) {
            MenuDivider()
            IosActionRow(stringResource(R.string.dev_beta_open_folio), "dev-beta-open") { runCatching { context.startActivity(open) } }
        }
    }
    CardNote(when (val s = state) {
        DevBeta.State.NoCode -> stringResource(R.string.dev_beta_no_code)
        DevBeta.State.Checking -> stringResource(R.string.dev_beta_checking)
        is DevBeta.State.Found -> stringResource(if (s.newer) R.string.dev_beta_found else R.string.dev_beta_same, s.release.version)
        is DevBeta.State.Asking -> stringResource(R.string.dev_beta_found, s.release.version)
        is DevBeta.State.Downloading -> stringResource(R.string.dev_beta_downloading) + (s.fraction?.let { " " + (it * 100).toInt() + "%" } ?: "")
        DevBeta.State.Installing -> stringResource(R.string.dev_beta_installing)
        is DevBeta.State.Refused -> stringResource(when (s.reason) {
            DevRefusal.WRONG_PACKAGE -> R.string.dev_beta_refuse_package
            DevRefusal.WRONG_SIGNER -> R.string.dev_beta_refuse_signer
            DevRefusal.OLDER_VERSION -> R.string.dev_beta_refuse_older
            else -> R.string.dev_beta_refuse_hash
        })
        DevBeta.State.Failed -> stringResource(R.string.dev_beta_failed)
        DevBeta.State.Idle -> stringResource(R.string.dev_beta_note)
    }, Modifier.padding(top = FolioSpace.SMALL.dp))
    (state as? DevBeta.State.Asking)?.let { asking ->
        AlertDialog(onDismissRequest = { DevBeta.cancel() },
            title = { Text(stringResource(R.string.dev_beta_confirm_title)) },
            text = { Text(stringResource(R.string.dev_beta_confirm_body, asking.release.version), fontSize = FolioType.SUBHEAD.sp) },
            confirmButton = { androidx.compose.material3.TextButton({ DevBeta.confirm(context) }, Modifier.testTag("dev-beta-confirm")) { Text(stringResource(R.string.dev_beta_confirm_yes)) } },
            dismissButton = { androidx.compose.material3.TextButton({ DevBeta.cancel() }) { Text(stringResource(R.string.dev_beta_confirm_no)) } })
    }
}

private fun failureText(failure: DevSource.Failure) = when (failure) {
    DevSource.Failure.NO_CODE -> R.string.dev_source_fail_no_code
    DevSource.Failure.NETWORK -> R.string.dev_source_fail_network
    DevSource.Failure.SERVER_ERROR -> R.string.dev_source_fail_server
    DevSource.Failure.NOT_A_LIST -> R.string.dev_source_fail_not_a_list
    DevSource.Failure.FUTURE -> R.string.dev_source_fail_future
    DevSource.Failure.WORKER_REFUSED -> R.string.dev_source_fail_refused
    DevSource.Failure.BAD_SIGNATURE -> R.string.dev_source_fail_signature
    DevSource.Failure.UNREADABLE -> R.string.dev_source_fail_unreadable
    DevSource.Failure.STALE -> R.string.dev_source_fail_stale
    DevSource.Failure.ROLLBACK -> R.string.dev_source_fail_rollback
}

private fun refusalText(reason: DevRefusal) = when (reason) {
    DevRefusal.WRONG_SIZE -> R.string.dev_source_refuse_size
    DevRefusal.WRONG_HASH -> R.string.dev_source_refuse_hash
    DevRefusal.WRONG_PACKAGE -> R.string.dev_source_refuse_package
    DevRefusal.WRONG_SIGNER -> R.string.dev_source_refuse_signer
    DevRefusal.OLDER_VERSION -> R.string.dev_source_refuse_older
}

/** A paste field and Save. The code is judged on the phone first; the server judges it again on every request. */
@Composable
private fun CodeField(rejected: Boolean, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    BasicTextField(text, { text = it.take(400) },
        Modifier.padding(vertical = FolioSpace.SNUG.dp).fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CONTROL.dp))
            .background(Color.White.copy(alpha = .1f)).padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.MEDIUM.dp)
            .testTag("dev-source-code"),
        singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = FolioType.SUBHEAD.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        decorationBox = { inner -> Box { if (text.isEmpty()) Text(stringResource(R.string.dev_source_code_hint), color = Color.White.copy(alpha = .35f), fontSize = FolioType.SUBHEAD.sp); inner() } })
    SheetGroup(Modifier.padding(bottom = FolioSpace.SMALL.dp)) {
        IosActionRow(stringResource(R.string.dev_source_save_code), "dev-source-save", enabled = text.isNotBlank()) { onSave(text) }
    }
    if (rejected) CardNote(stringResource(R.string.dev_source_code_rejected))
}

/** Adds the signed WIP tweaks source (the publisher key is compiled in, so there is no fingerprint to confirm). */
private fun addTweaksSource(context: android.content.Context) {
    val app = context.applicationContext
    val key = com.mccal.folio.market.SourceKey.parse(DevSourceKeys.PUBLISHER) ?: return
    MarketWork.background {
        val session = MarketSession(app, ReadOnlyMarketLauncher)
        val url = DevSource.tweaksUrl(app)
        if (!session.sources.has(url)) session.sources.trust(url, key, com.mccal.folio.market.Source.Kind.ADDED)
    }
}
