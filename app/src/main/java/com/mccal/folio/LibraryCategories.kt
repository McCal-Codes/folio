package com.mccal.folio

import androidx.compose.foundation.combinedClickable
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** App Library categories, from the category apps declare plus simple package hints. */
internal enum class LibraryCategory(val title: String) {
    SUGGESTIONS("Suggestions"), SOCIAL("Social"), PRODUCTIVITY("Productivity & Finance"), CREATIVITY("Photo & Video"),
    ENTERTAINMENT("Entertainment"), GAMES("Games"), INFO("Information & Reading"), TRAVEL("Travel & Maps"),
    SHOPPING("Shopping & Food"), UTILITIES("Utilities"), OTHER("Other");

    companion object {
        fun of(pm: PackageManager, packageName: String): LibraryCategory {
            val p0 = packageName.lowercase()
            // Browsers and system tools often declare odd categories; our hints win for them.
            if (hint(p0, "chrome", "firefox", "browser", "opera", "brave", "edge", "duckduckgo", "settings", "myfiles", "files",
                    "clock", "calculator", "contacts", "dialer", "vending", "authenticator", "callfilter", "call.filter", "vpn")) return UTILITIES
            val declared = runCatching { pm.getApplicationInfo(packageName, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
            when (declared) {
                ApplicationInfo.CATEGORY_SOCIAL -> return SOCIAL
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return PRODUCTIVITY
                ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_VIDEO -> return if (hint(packageName, "youtube", "netflix", "tv", "hulu", "disney", "twitch")) ENTERTAINMENT else CREATIVITY
                ApplicationInfo.CATEGORY_AUDIO -> return ENTERTAINMENT
                ApplicationInfo.CATEGORY_GAME -> return GAMES
                ApplicationInfo.CATEGORY_NEWS -> return INFO
                ApplicationInfo.CATEGORY_MAPS -> return TRAVEL
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> return UTILITIES
            }
            val p = packageName.lowercase()
            return when {
                hint(p, "discord", "twitter", "instagram", "facebook", "whatsapp", "telegram", "snapchat", "reddit", "linkedin", "tiktok", "threads", "messenger", "signal", "teams", "slack") -> SOCIAL
                hint(p, "mail", "gmail", "calendar", "office", "docs", "sheets", "notes", "drive", "bank", "pay", "wallet", "finance", "cash", "venmo", "chatgpt", "claude", "perplexity", "bard", "calendly") -> PRODUCTIVITY
                hint(p, "spotify", "music", "youtube", "netflix", "hulu", "disney", "twitch", "podcast", "audible", "tv") -> ENTERTAINMENT
                hint(p, "camera", "gallery", "photo", "lightroom", "snapseed", "video", "capcut") -> CREATIVITY
                hint(p, "game", "games", "roblox", "minecraft", "chess") -> GAMES
                hint(p, "news", "weather", "kindle", "books", "health", "fitness", "wiki") -> INFO
                hint(p, "maps", "uber", "lyft", "airline", "travel", "transit", "waze", "airbnb") -> TRAVEL
                hint(p, "shop", "amazon", "store", "ebay", "doordash", "ubereats", "grubhub", "food", "starbucks", "target", "walmart") -> SHOPPING
                hint(p, "settings", "files", "myfiles", "clock", "calculator", "contacts", "dialer", "phone", "messaging", "vending", "chrome", "browser", "firefox", "vpn", "authenticator", "security") -> UTILITIES
                else -> OTHER
            }
        }

        private fun hint(p: String, vararg words: String) = words.any { p.contains(it) }
    }
}

/** iOS App Library tile: three big icons and a mini cluster that opens the whole category. */
@Composable
internal fun CategoryCard(title: String, apps: List<AppEntry>, modifier: Modifier, labelColor: Color = Color.White, onLaunch: (AppEntry) -> Unit, onOpen: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(FolioGlass.card)
            .border(FolioGlass.edge, RoundedCornerShape(24.dp)).padding(12.dp)) {
            val gap = 10.dp
            val cell = (maxWidth - gap) / 2
            val big = if (apps.size > 4) apps.take(3) else apps.take(4)
            val rest = if (apps.size > 4) apps.drop(3) else emptyList()
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until 2) {
                        val index = row * 2 + col
                        when {
                            index < big.size -> AppIcon(big[index], big[index].label, Modifier.size(cell)
                                .clickable { onLaunch(big[index]) }, shape = RoundedCornerShape(cell * .24f))
                            index == 3 && rest.isNotEmpty() -> Box(Modifier.size(cell).clip(RoundedCornerShape(cell * .24f))
                                .clickable(onClick = onOpen).semantics { contentDescription = "Show all ${apps.size} $title apps" }) {
                                val mini = (cell - 4.dp) / 2
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    rest.take(4).chunked(2).forEach { pair ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            pair.forEach { AppIcon(it, null, Modifier.size(mini).clip(RoundedCornerShape(mini * .24f))) }
                                        }
                                    }
                                }
                            }
                            else -> Spacer(Modifier.size(cell))
                        }
                    }
                }
            }
        }
        Text(title, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).clickable(onClick = onOpen))
    }
}

/** A category opened full size: a simple icon grid with labels. */
/** An App Library category opened like an iOS folder: big title and a rounded glass card of every app, over a dimmed background. */
@Composable
internal fun CategoryFolder(title: String, apps: List<AppEntry>, onDismiss: () -> Unit, onLaunch: (AppEntry) -> Unit, onActions: (AppEntry) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FolioDialogWindow(dim = 0f, blurRadiusDp = 24)
        val appear = rememberEntrance(stiffness = 600f, dampingRatio = .82f)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = .45f)).clickable(androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onDismiss)
            .testTag("category-folder-scrim"))
        FoldAvoidingBox(Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing).padding(24.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val width = minOf(maxWidth, 560.dp)
                val availableHeight = maxHeight
                // ~84dp per app: three or four columns on the cover, up to six unfolded.
                val columns = evenColumnsOnHinge(((width - 40.dp) / 84.dp).toInt().coerceIn(3, 6), 3)
                Column(Modifier.width(width).graphicsLayer {
                    alpha = appear.value; scaleX = .9f + .1f * appear.value; scaleY = scaleX
                }) {
                    Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                    // Lazy, so a category with dozens of apps only builds the rows on screen as it opens.
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                        Modifier.fillMaxWidth().heightIn(max = availableHeight - 64.dp).clip(RoundedCornerShape(36.dp))
                            .background(Color(0xFF2C2C2E).copy(alpha = .96f)).border(FolioGlass.edge, RoundedCornerShape(36.dp))
                            .pointerInput(Unit) { detectTapGestures() }
                            .testTag("category-folder"),
                        state = gridState, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(apps.size, key = { apps[it].id }) { index ->
                            val app = apps[index]
                            Column(Modifier.clip(RoundedCornerShape(14.dp))
                                .combinedClickable(onLongClick = { onActions(app) }) { onLaunch(app) }.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(app, null, Modifier.size(54.dp), shape = RoundedCornerShape(13.dp))
                                Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
