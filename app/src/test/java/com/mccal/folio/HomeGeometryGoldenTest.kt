package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Today's Home layout, pinned. Every window in the shared device list (rotated, and split in half where it is big
 * enough), under every dock placement, two presets (Folio's defaults, and a set of tweaked sliders) and with labels on
 * and off, lays out to a digest of the numbers Home draws from: the page, the grid, the dock, the status rail and the
 * cell positions. The digests are recorded in `src/test/resources/home-geometry-golden.txt`.
 *
 * It exists so changes to how Home is measured (a third preset, more dock slots, a full-width Home) can prove they
 * left every existing window exactly where it was: with the new settings at their defaults this test must stay green
 * without touching the file. If a change is meant to move something, regenerate the file with
 * `FOLIO_GEOMETRY_GOLDEN_WRITE=1 ./gradlew :app:testDebugUnitTest --tests '*HomeGeometryGoldenTest*'` and say in the
 * pull request which windows moved and why. Only the fields listed in [digest] count, so adding a field to
 * [HomeGeometry] does not move anything by itself.
 */
class HomeGeometryGoldenTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val goldenFile = File(root, "app/src/test/resources/home-geometry-golden.txt")

    private data class Window(val name: String, val width: Float, val height: Float)

    private val windows: List<Window> = org.json.JSONObject(javaClass.getResource("/screen-matrix.json")!!.readText()).getJSONArray("devices").let { list ->
        (0 until list.length()).map { i -> list.getJSONObject(i).let { Window(it.getString("name"), it.getDouble("width").toFloat(), it.getDouble("height").toFloat()) } }
    }.flatMap { d ->
        listOf(d, Window("${d.name} rotated", d.height, d.width)) +
            (if (maxOf(d.width, d.height) >= 800f) listOf(Window("${d.name} split half", maxOf(d.width, d.height) / 2f, minOf(d.width, d.height))) else emptyList())
    }

    private val presets = listOf(
        "defaults" to LayoutPreset(),
        "tweaked" to LayoutPreset(iconSize = 52f, rowGap = 6f, dockWidth = 64f, dockPosition = .3f, dockAlignToGrid = false, statusAlignToGrid = false,
            statusPosition = .5f, columnGap = 24f, dockSpacing = 8f, widgetScale = 1.1f),
    )

    private fun f(x: Float) = x.toBits().toString(16)

    private fun digest(g: HomeGeometry): String {
        val cells = HomeCellLayout.forPage(g, listOf(0 to 2))
        val cellXY = (0 until 4 * g.appRows).joinToString(",") { i -> val row = 2 + i / 4; "${f(cells.x(i % 4, row))}:${f(cells.y(row))}" }
        return listOf(g.expanded, g.splitColumns, g.horizontalDock, g.dockBesideRail, g.appRows, g.fitAppRows).joinToString(",") + "|" +
            listOf(g.homeWidth, g.gridWidth, g.iconSize, g.rowHeight, g.widgetHeight, g.contentTop, g.statusTop, g.dockTop, g.dockHeight,
                g.dockRowHeight, g.dockBarHeight, g.cellWidth, g.zoneGap, g.rowGap, g.columnsInset, g.dockPitch,
                cells.x(2, 0), cells.y(0), cells.spanHeight(0, 2)).joinToString(",") { f(it) } + "|" + cellXY
    }

    private fun cases(): List<String> = buildList {
        for (w in windows) {
            val scale = uiScale(w.width, w.height)
            val width = w.width / scale; val height = w.height / scale
            for (placement in DockPlacement.entries) for ((presetName, preset) in presets) for (labels in listOf(true, false)) {
                val p = preset.copy(dockPlacement = placement)
                val base = homeGeometry(width, height, p, labels, statusHeight = 160f)
                val fit = homeGeometry(width, height, p, labels, statusHeight = 160f, appRows = base.fitAppRows, fillSpace = true)
                val text = digest(base) + "#" + digest(fit)
                val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
                add("${w.name} | $placement | $presetName | labels=$labels | $hash")
            }
        }
    }

    @Test fun `every window lays out exactly where it did`() {
        val now = cases()
        if (System.getenv("FOLIO_GEOMETRY_GOLDEN_WRITE") != null) {
            goldenFile.parentFile.mkdirs(); goldenFile.writeText(now.joinToString("\n") + "\n"); return
        }
        assertTrue("home-geometry-golden.txt is missing: run with FOLIO_GEOMETRY_GOLDEN_WRITE=1", goldenFile.exists())
        val recorded = goldenFile.readLines().filter { it.isNotBlank() }
        val moved = now.indices.filter { it >= recorded.size || now[it] != recorded[it] }.map { now[it].substringBeforeLast(" | ") }
        assertEquals("Home moved for ${moved.size} of ${now.size} layouts, first: ${moved.take(8)}", emptyList<String>(), moved)
        assertEquals("the sweep changed size", recorded.size, now.size)
    }

    @Test fun `the sweep covers every window and option`() {
        assertEquals(windows.size * DockPlacement.entries.size * presets.size * 2, cases().size)
        assertTrue(windows.any { it.name.contains("split half") } && windows.any { it.name.contains("rotated") })
    }
}
