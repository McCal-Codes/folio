package com.mccal.folio

import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes Home's real layout for the review mockup (docs/mockups, local only) when FOLIO_GEOMETRY_OUT is set:
 * every named screen, rotated and split, with each dock and status option. Does nothing in normal test runs.
 */
class GeometryExportTest {
    private val screens = listOf(
        "Small Phone" to (360f to 640f), "Nexus 4" to (384f to 640f), "Pixel 5" to (393f to 851f),
        "Pixel 9" to (411f to 923f), "Pixel 9 Pro" to (427f to 952f), "Pixel 9 Pro XL" to (448f to 997f),
        "Flip phone (open)" to (360f to 879f), "Flip cover (small)" to (260f to 260f), "Flip cover (large)" to (320f to 320f),
        "Rollable" to (610f to 925f), "Galaxy Z Fold8 cover" to (475f to 751f), "Galaxy Z Fold8 inner" to (932f to 704f),
        "Pixel Fold inner" to (841f to 701f), "Pixel 9 Pro Fold inner" to (852f to 883f), "8-inch fold-out" to (838f to 945f),
        "Tri-fold (open)" to (1350f to 900f), "Galaxy Z TriFold main (est.)" to (823f to 603f),
        "Galaxy Z TriFold cover (est.)" to (411f to 960f), "Galaxy Z Fold8 Ultra inner (est.)" to (859f to 954f),
        "Galaxy Z Fold8 Ultra cover (est.)" to (411f to 960f), "7-inch tablet" to (1024f to 600f), "Nexus 7" to (600f to 960f),
        "Pixel Tablet" to (1280f to 800f), "Small desktop" to (1366f to 768f), "Large desktop" to (1920f to 1080f),
    )

    @Test fun export() {
        val out = System.getenv("FOLIO_GEOMETRY_OUT")
        assumeTrue("FOLIO_GEOMETRY_OUT is not set", out != null)
        out!!
        val rows = mutableListOf<String>()
        for ((name, size) in screens) for (rotated in listOf(false, true)) for (half in listOf(false, true)) {
            val (w0, h0) = if (rotated) size.second to size.first else size
            if (half && maxOf(w0, h0) < 800f) continue
            val (rw, rh) = if (half) maxOf(w0, h0) / 2f to minOf(w0, h0) else w0 to h0
            val scale = uiScale(rw, rh)
            val w = rw / scale; val h = rh / scale
            // sim: the status as it is, or 24 dp taller with a second SIM's signal (a proposal).
            for (dock in DockPlacement.entries) for (status in listOf("level", "top", "custom")) for (sim in listOf(1, 2)) {
                val preset = LayoutPreset(dockPlacement = dock, statusAlignToGrid = status == "level",
                    statusPosition = if (status == "custom") .5f else 0f)
                val statusHeight = if (sim == 2) 184f else 160f
                // Rows › Automatic: lay out with as many app rows as fit, like Home does.
                val fit = homeGeometry(w, h, preset, true, statusHeight = statusHeight).fitAppRows
                val g = homeGeometry(w, h, preset, true, statusHeight = statusHeight, appRows = fit)
                val widgets = listOf(0 to 2)
                val cells = HomeCellLayout.forPage(g, widgets)
                val icons = (0 until 4 * g.appRows).joinToString(",") { i -> val row = 2 + i / 4; "[${cells.x(i % 4, row)},${cells.y(row)}]" }
                rows += """{"name":"$name","rot":$rotated,"half":$half,"dock":"$dock","status":"$status","sim":$sim,"rw":$rw,"rh":$rh,""" +
                    """"scale":$scale,"w":$w,"h":$h,"micro":${isMicroWindow(w, h)},"microApps":${microAppCount(w)},"appRows":${g.appRows},""" +
                    """"expanded":${g.expanded},"split":${g.splitColumns},"hDock":${g.horizontalDock},"beside":${g.dockBesideRail},""" +
                    """"homeWidth":${g.homeWidth},"grid":${g.gridWidth},"icon":${g.iconSize},"row":${g.rowHeight},"widget":${g.widgetHeight},""" +
                    """"top":${g.contentTop},"statusTop":${g.statusTop},"dockTop":${g.dockTop},"dockH":${g.dockHeight},""" +
                    """"dockRow":${g.dockRowHeight},"bar":${g.dockBarHeight},"cell":${g.cellWidth},"widgetW":${cells.x(2, 0)},""" +
                    """"widgetRows":[${cells.y(0)},${cells.spanHeight(0, 2)}],"icons":[$icons]}"""
            }
        }
        java.io.File(out).writeText("window.FOLIO_GEOMETRY = [\n" + rows.joinToString(",\n") + "\n];\n")
    }
}
