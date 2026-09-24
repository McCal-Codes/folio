@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/**
 * Side-key assistant picker. Folio appears in Settings › Default apps › Digital assistant app
 * because this activity handles ACTION_ASSIST; holding the side key (set to Digital assistant)
 * then opens this small chooser instead of a single fixed assistant.
 */
class AssistPickerActivity : ComponentActivity() {
    /** Follows the latest side-key intent: this activity is singleTask, so a new press arrives in onNewIntent. */
    private val searchOnlyState = androidx.compose.runtime.mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        searchOnlyState.value = intent.getBooleanExtra(EXTRA_SEARCH_ONLY, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchOnlyState.value = intent.getBooleanExtra(EXTRA_SEARCH_ONLY, false)
        val targets = AssistTarget.entries.mapNotNull { target ->
            val launch = packageManager.getLaunchIntentForPackage(target.packageName) ?: return@mapNotNull null
            val icon = runCatching { packageManager.getApplicationIcon(target.packageName).toBitmap(144, 144) }.getOrNull()
            Triple(target, launch, icon)
        }
        setContent {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            var query by remember { mutableStateOf("") }
            val focus = remember { FocusRequester() }
            // Side key set to "Search Google Without AI": just the search field, ready to type.
            val searchOnly = searchOnlyState.value
            LaunchedEffect(searchOnly) { if (searchOnly) { kotlinx.coroutines.delay(250); runCatching { focus.requestFocus() } } }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
                .clickable(remember { MutableInteractionSource() }, null) { finish() }, contentAlignment = Alignment.BottomCenter) {
                AnimatedVisibility(shown, enter = fadeIn() + slideInVertically(spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow)) { it / 3 }) {
                    Column(Modifier.navigationBarsPadding().windowInsetsPadding(WindowInsets.imeAnimationTarget).padding(FolioSpace.LARGE.dp).widthIn(max = 520.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp)).background(FolioColors.SecondaryBackground.copy(alpha = .94f))
                        .clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(FolioSpace.LARGE.dp)) {
                        if (!searchOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            targets.forEach { (target, launch, icon) ->
                                Column(Modifier.clip(RoundedCornerShape(FolioRadius.GROUP.dp)).clickable { start(launch) }.padding(FolioSpace.SMALL.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    icon?.let { Image(it.asImageBitmap(), target.label, Modifier.size(56.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp))) }
                                    Text(target.label, color = Color.White, fontSize = FolioType.GROUP_LABEL.sp, modifier = Modifier.padding(top = FolioSpace.SNUG.dp))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .1f))
                            .padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.MEDIUM.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = .7f))
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text(stringResource(R.string.search_google_without_ai), color = Color.White.copy(alpha = .5f), fontSize = FolioType.BODY.sp)
                                BasicTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = FolioType.BODY.sp), cursorBrush = SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (query.isNotBlank()) { openWebSearch(this@AssistPickerActivity, WebSearchTarget.GOOGLE, query); finish() }
                                    }))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                            PickerPill(stringResource(R.string.spotlight), Modifier.weight(1f)) {
                                SpotlightRequest.request()
                                startActivity(Intent(this@AssistPickerActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }
                            PickerPill(stringResource(R.string.duckduckgo), Modifier.weight(1f)) {
                                if (query.isNotBlank()) { openWebSearch(this@AssistPickerActivity, WebSearchTarget.DUCKDUCKGO, query); finish() }
                                else focus.requestFocus()
                            }
                        }
                    }
                }
            }
        }
    }

    // Never come back stale (old query, old app list) on the next side-key press.
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) finish() }

    private fun start(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    companion object {
        const val EXTRA_SEARCH_ONLY = "folio_search_only"
        fun isDefaultAssistant(context: Context): Boolean =
            runCatching { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)

        fun settingsIntent() = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** In-process request to open Spotlight; unlike an intent extra, other apps can't trigger it. */
internal object SpotlightRequest {
    @Volatile private var pending = false
    fun request() { pending = true }
    fun consume(): Boolean = pending.also { pending = false }
}

internal enum class AssistTarget(val label: String, val packageName: String) {
    CHATGPT("ChatGPT", "com.openai.chatgpt"),
    CLAUDE("Claude", "com.anthropic.claude"),
    PERPLEXITY("Perplexity", "ai.perplexity.app.android"),
    GEMINI("Gemini", "com.google.android.apps.bard"),
}

@Composable
private fun PickerPill(label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .1f)).clickable(onClick = onClick)
        .padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Public, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
