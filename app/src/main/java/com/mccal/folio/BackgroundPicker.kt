package com.mccal.folio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter

/*
 * The wallpaper picker: one grid of everything Folio can put behind Home.
 *
 * **Why one grid.** A photo you picked, a print that shipped with Folio and a piece installed from the Market are the
 * same kind of thing now: a picture, with a file and a credit. They used to need separate UI because Folio's own
 * backgrounds were code that drew a scene. Under [DES-2a] they are pictures, so they belong in one grid with one
 * behaviour, the way iOS shows you wallpapers rather than sorting them by where they came from. The headings say
 * where a picture came from because that is worth knowing, not because the tiles behave differently.
 *
 * **The credit is on the tile, not one tap away.** DES-2b says Folio must be able to name the artist, the work and
 * the license for every image it shows. A credit that lives only on a detail page is a credit nobody reads, so the
 * artist and the license sit under every tile at all times.
 *
 * **Not here on purpose:** whether Android's own wallpaper shows through instead. That is a different question, "who
 * paints", and it stays in its own card above. This page decides which picture.
 *
 * Dynamic class: D5 Ambient, and D1 Reactive.
 * Source of truth: [currentTime] for the clock on each tile; [backgroundChoice] for which tile is ticked;
 *   [BackgroundLibrary] for what there is to choose from, re-read when [LauncherBackgroundCache.revision] moves.
 * Persistent state: none of its own. Choosing writes through [setBackgroundChoice], whose owner is the controller.
 * Reduce Motion: nothing here moves. The clock changes a digit once a minute, which is a value changing rather than
 *   an animation, so there is nothing to reduce.
 *
 * **Why the tiles carry a clock.** A wallpaper is not judged on its own, it is judged with Home's white text on top
 * of it, and half the art that looks best in a grid is the art that makes the clock unreadable. A static crop hides
 * that; the real time in the real ink does not. It is the live clock Home uses, not a drawn-on 9:41, because a
 * picker that lies about what you are choosing is worse than one that shows less.
 */

/** How wide a tile wants to be, before the grid decides how many fit. Two on a cover screen, three on an inner one. */
private val TILE_MIN = 150.dp

/** A tile is a phone, near enough: tall enough to judge a wallpaper, short enough that a row of them fits. */
private const val TILE_RATIO = 0.62f

/** The clock's size as a share of a tile's width, taken from how big Home's own clock is against a screen. */
private const val CLOCK_SHARE = 0.14f

@Composable
internal fun BackgroundPicker(
    controller: LauncherBackgroundController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The cache's revision moves whenever the background changes, from here or from a package installing one, so
    // reading the choice against it keeps the ticks honest without this owning any state of its own (STA-1).
    val revision = LauncherBackgroundCache.revision.intValue
    val choice = remember(revision, controller.photoSelected) { backgroundChoice(context) }
    val art = remember(revision) { BackgroundLibrary.all(context) }
    val builtIn = art.filter { it.builtIn }
    val installed = art.filterNot { it.builtIn }

    fun choose(next: BackgroundChoice) {
        setBackgroundChoice(context, next)
        // Drop the decoded picture rather than decoding the new one here: whatever draws next asks for it, on its
        // own thread, through the one loader.
        LauncherBackgroundCache.changed(null)
    }

    Column(modifier) {
        SheetGroupLabel(stringResource(R.string.your_photo))
        BackgroundGrid(listOf(Unit)) {
            PhotoTile(
                selected = choice == BackgroundChoice.Photo,
                hasPhoto = launcherPhotoFile(context).isFile,
                onPick = controller::choosePhoto,
                onSelect = { choose(BackgroundChoice.Photo) },
            )
        }

        if (builtIn.isNotEmpty()) {
            SheetGroupLabel(stringResource(R.string.from_folio))
            BackgroundGrid(builtIn) { ArtTile(it, choice == BackgroundChoice.Art(it.id)) { choose(BackgroundChoice.Art(it.id)) } }
        }

        if (installed.isNotEmpty()) {
            SheetGroupLabel(stringResource(R.string.installed))
            BackgroundGrid(installed) { ArtTile(it, choice == BackgroundChoice.Art(it.id)) { choose(BackgroundChoice.Art(it.id)) } }
        }

        CardNote(stringResource(R.string.wallpapers_you_install_from_the_market), Modifier.padding(top = FolioSpace.SMALL.dp))
    }
}

/**
 * Lays tiles out in as many columns as fit, and fills the last row so tiles keep their width.
 *
 * A grid rather than a list because a wallpaper is judged by looking at it. Not `LazyVerticalGrid`: this sits inside
 * a page that already scrolls, and nesting a lazy scroller inside a scroller is how you get a list that fights the
 * finger. There are a handful of tiles, so measuring them all costs nothing (CMP).
 */
@Composable
private fun <T> BackgroundGrid(items: List<T>, tile: @Composable RowScope.(T) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.TINY.dp)) {
        val columns = gridColumns(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
                    row.forEach { tile(it) }
                    // A short last row would otherwise stretch its tiles across the whole width, so the gaps are
                    // filled with nothing of the same weight.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * How many tiles fit. Two at the narrowest, because one tile is a list with extra steps, and however many [TILE_MIN]
 * allows above that: three on a Fold's inner screen. Measured from the window rather than asked of the device
 * (ADP-1).
 */
internal fun gridColumns(width: Dp): Int = maxOf(2, (width / TILE_MIN).toInt())

@Composable
private fun RowScope.Tile(
    selected: Boolean,
    tag: String,
    description: String,
    onClick: () -> Unit,
    image: @Composable BoxScope.() -> Unit,
    caption: @Composable () -> Unit,
) {
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).testTag(tag)
            .semantics { contentDescription = description },
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(TILE_RATIO)
                .clip(RoundedCornerShape(FolioRadius.CARD.dp))
                .background(FolioColors.SecondaryBackground)
                .then(
                    if (selected) Modifier.border(2.dp, LocalAccent.current.fill, RoundedCornerShape(FolioRadius.CARD.dp))
                    else Modifier,
                ),
        ) {
            image()
            // Home's own chrome, over the picture, so what you are judging is the thing you will get.
            Text(
                currentTime().format(DateTimeFormatter.ofPattern(clockPattern())),
                color = Color.White,
                // A share of the tile rather than a size off the scale (DES-6): this clock is a picture of Home's
                // clock, so it should look the same whether two tiles fit across the page or three.
                fontSize = (maxWidth.value * CLOCK_SHARE).sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = FolioSpace.MEDIUM.dp),
                style = TextStyle(
                    fontFeatureSettings = "tnum",
                    // Home has a scrim behind its text. This grid does not, so the tile borrows the same trick a
                    // wallpaper preview has always used: a shadow that costs nothing and keeps the digits legible
                    // over a pale picture without lying about how pale it is.
                    shadow = Shadow(Color.Black.copy(alpha = .55f), Offset.Zero, 10f),
                ),
            )
            if (selected) Box(
                Modifier.align(Alignment.BottomEnd).padding(FolioSpace.SNUG.dp).size(22.dp)
                    .clip(CircleShape).background(LocalAccent.current.fill),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        caption()
    }
}

/** 24-hour or not, as the phone is set: the same question Home asks. */
@Composable
private fun clockPattern() =
    if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"

@Composable
private fun RowScope.ArtTile(art: Artwork, selected: Boolean, onSelect: () -> Unit) {
    val context = LocalContext.current
    var thumb by remember(art.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(art.id) { thumb = withContext(Dispatchers.IO) { backgroundThumbnail(context, art.id) } }

    Tile(
        selected = selected,
        tag = "background-art-${art.id}",
        description = stringResource(R.string.wallpaper_by_artist, art.title, art.artist),
        onClick = onSelect,
        image = {
            thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        },
        caption = {
            Column(Modifier.padding(top = FolioSpace.SNUG.dp, start = FolioSpace.HAIR.dp)) {
                Text(art.title, color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(art.artist, color = FolioColors.SecondaryLabel, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(art.license, color = FolioColors.SecondaryLabel, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}

@Composable
private fun RowScope.PhotoTile(selected: Boolean, hasPhoto: Boolean, onPick: () -> Unit, onSelect: () -> Unit) {
    val context = LocalContext.current
    var thumb by remember(hasPhoto) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(hasPhoto) {
        thumb = if (hasPhoto) withContext(Dispatchers.IO) { decodeThumbnail(launcherPhotoFile(context)) } else null
    }

    Tile(
        selected = selected && hasPhoto,
        tag = "background-photo",
        description = stringResource(if (hasPhoto) R.string.your_photo else R.string.choose_a_photo),
        // With no photo yet there is nothing to select, so the tile is the picker. With one, tapping shows it and
        // the line underneath is how you replace it.
        onClick = if (hasPhoto) onSelect else onPick,
        image = {
            thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = LocalAccent.current.fill, modifier = Modifier.size(26.dp))
                }
        },
        caption = {
            Column(Modifier.padding(top = FolioSpace.SNUG.dp, start = FolioSpace.HAIR.dp)) {
                Text(
                    stringResource(if (hasPhoto) R.string.your_photo else R.string.choose_a_photo),
                    color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                if (hasPhoto) Text(
                    stringResource(R.string.choose_a_different_photo), color = LocalAccent.current.fill,
                    fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1,
                    modifier = Modifier.clickable(onClick = onPick).testTag("background-replace-photo"),
                )
            }
        },
    )
}

/** How wide a thumbnail is decoded. Generous enough for three columns on an inner screen, small enough to be cheap. */
private const val THUMB_WIDTH = 420

/**
 * A small copy of a background, for the grid.
 *
 * Decoded at a fraction of the file rather than in full: the art Folio ships is 2448 px wide, and six of those
 * decoded whole would be well over a hundred megabytes for pictures drawn at a sixth of the size. `inJustDecodeBounds`
 * reads the header, `inSampleSize` asks the decoder for a power-of-two fraction, which is what every launcher that
 * shows wallpaper thumbnails does (PRF).
 */
private fun backgroundThumbnail(context: Context, id: String): Bitmap? =
    if (BackgroundLibrary.isBuiltIn(id)) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(BackgroundLibrary.assetPath(id)).use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth) }
            context.assets.open(BackgroundLibrary.assetPath(id)).use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    } else {
        decodeThumbnail(BackgroundLibrary.artFile(context, id))
    }

private fun decodeThumbnail(file: File): Bitmap? = runCatching {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth) })
}.getOrNull()

/** The largest power-of-two fraction that still leaves the thumbnail wider than it is drawn. */
internal fun sampleFor(width: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= THUMB_WIDTH) sample *= 2
    return sample
}
