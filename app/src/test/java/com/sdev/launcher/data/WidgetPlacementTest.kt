package com.sdev.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widgets that have been put on the home screen: how they are written down,
 * and where they sit on a grid whose height the user chooses.
 *
 * The provider is kept as plain text rather than as a framework component, so
 * all of this is checkable without a device.
 */
class WidgetPlacementTest {

    private val provider = "com.example.clock/com.example.clock.ClockWidget"

    /** The grid the launcher starts with. */
    private val grid = HomeGrid(LauncherSettings.HOME_ROWS)

    @Test
    fun `a widget survives a write and a read`() {
        val placed = mapOf(
            0 to WidgetPlacement(11, provider, cell = 0, spanX = 4, spanY = 1),
            24 to WidgetPlacement(12, provider, cell = 24, spanX = 2, spanY = 2),
        )

        assertEquals(placed, decodeWidgets(grid, encodeWidgets(placed)))
    }

    @Test
    fun `nothing at all reads as no widgets`() {
        assertEquals(emptyMap<Int, WidgetPlacement>(), decodeWidgets(grid, null))
        assertEquals(emptyMap<Int, WidgetPlacement>(), decodeWidgets(grid, ""))
    }

    @Test
    fun `malformed widgets are skipped rather than throwing`() {
        val good = "0=11;2;1;com.example.clock/com.example.clock.ClockWidget"
        val raw = listOf(
            good,
            "not a widget",
            "1=11;2;1", // too few parts
            "2=11;2;1;com.example.clock/com.example.clock.ClockWidget;extra",
            "3=x;2;1;com.example.clock/com.example.clock.ClockWidget", // id is not a number
            "4=11;2;1;nonsense", // provider is not a component
            "9999=11;2;1;com.example.clock/com.example.clock.ClockWidget", // off the screen
        ).joinToString("\n")

        assertEquals(
            mapOf(
                0 to WidgetPlacement(
                    hostId = 11,
                    provider = provider,
                    cell = 0,
                    spanX = 2,
                    spanY = 1,
                ),
            ),
            decodeWidgets(grid, raw),
        )
    }

    @Test
    fun `a span larger than the grid is clamped when read back`() {
        val raw = "0=11;99;99;com.example.clock/com.example.clock.ClockWidget"

        val widget = decodeWidgets(grid, raw).getValue(0)
        assertEquals(grid.columns, widget.spanX)
        assertEquals(grid.rows, widget.spanY)
    }

    @Test
    fun `a widget too tall for a short grid is clamped to it`() {
        val short = HomeGrid(LauncherSettings.HOME_MIN_ROWS)
        val raw = "0=11;4;9;com.example.clock/com.example.clock.ClockWidget"

        val widget = decodeWidgets(short, raw).getValue(0)
        assertEquals(short.rows, widget.spanY)
        assertTrue(widget.inside(short))
    }

    @Test
    fun `a widget sits on the page its cell is on`() {
        val perPage = grid.cellCount

        assertEquals(0, WidgetPlacement(1, provider, 5, 1, 1).pageIn(grid))
        assertEquals(1, WidgetPlacement(1, provider, perPage, 1, 1).pageIn(grid))
        assertEquals(2, WidgetPlacement(1, provider, perPage * 2 + 3, 1, 1).pageIn(grid))
    }

    @Test
    fun `the same cell is on an earlier page once a page holds more`() {
        val short = HomeGrid(5)
        val tall = HomeGrid(10)
        val widget = WidgetPlacement(1, provider, cell = 60, spanX = 1, spanY = 1)

        assertEquals(3, widget.pageIn(short))
        assertEquals(1, widget.pageIn(tall))
    }

    @Test
    fun `a widget knows which app it came from`() {
        val widget = WidgetPlacement(3, provider, cell = 0, spanX = 1, spanY = 1)

        assertEquals("com.example.clock", widget.providerPackage)
    }

    @Test
    fun `a widget covers the block of cells it was given`() {
        // Columns are four wide, so cells 5, 6, 9 and 10 are a 2x2 block
        // starting at row 1, column 1.
        assertEquals(setOf(5, 6, 9, 10), widgetCells(grid, 5, spanX = 2, spanY = 2))

        // And everything on one row is just that row.
        assertEquals(setOf(4, 5, 6, 7), widgetCells(grid, 4, spanX = 4, spanY = 1))
    }

    @Test
    fun `a widget never reaches onto the next page`() {
        val perPage = grid.cellCount
        val lastCellOfPage = perPage - 1

        val cells = widgetCells(grid, lastCellOfPage, spanX = 2, spanY = 2)

        assertTrue(cells.all { it < perPage })
        assertTrue(cells.all { it >= perPage - grid.columns })
    }

    @Test
    fun `a widget is only inside the grid when all of it is`() {
        assertTrue(widgetFitsGrid(grid, 0, spanX = grid.columns, spanY = grid.rows))

        assertFalse(widgetFitsGrid(grid, 0, spanX = grid.columns + 1, spanY = 1))
        assertFalse(widgetFitsGrid(grid, 0, spanX = 1, spanY = grid.rows + 1))

        // The bottom right cell holds a 1x1 and nothing bigger.
        val corner = grid.cellCount - 1

        assertTrue(widgetFitsGrid(grid, corner, spanX = 1, spanY = 1))
        assertFalse(widgetFitsGrid(grid, corner, spanX = 2, spanY = 1))
        assertFalse(widgetFitsGrid(grid, corner, spanX = 1, spanY = 2))
    }

    @Test
    fun `the row a widget sits on is read off the grid in use`() {
        val short = HomeGrid(5)
        val tall = HomeGrid(10)

        // Cell 12 is row 3 of the page, so a widget four rows tall reaches past
        // the bottom of the short grid and not of the tall one.
        val cell = 12

        assertFalse(widgetFitsGrid(short, cell, spanX = 1, spanY = 4))
        assertTrue(widgetFitsGrid(tall, cell, spanX = 1, spanY = 4))

        // A widget given more rows than the short grid has does not fit it.
        assertFalse(widgetFitsGrid(short, 0, spanX = 4, spanY = 6))
        assertTrue(widgetFitsGrid(tall, 0, spanX = 4, spanY = 6))
    }

    @Test
    fun `moving a widget gives it the new cell`() {
        val widget = WidgetPlacement(7, provider, cell = 2, spanX = 2, spanY = 2)

        assertEquals(9, widget.at(9).cell)
        assertEquals(7, widget.at(9).hostId)
        assertEquals(setOf(9, 10, 13, 14), widget.at(9).cellsIn(grid))
    }

    @Test
    fun `the home screen knows which page holds something`() {
        val perPage = grid.cellCount
        val settings = LauncherSettings(
            homeItems = mapOf(
                0 to HomeItem.App("app.a"),
                perPage + 1 to HomeItem.App("app.b"),
            ),
            homePages = 2,
            widgets = mapOf(
                perPage * 2 to WidgetPlacement(5, provider, perPage * 2, 1, 1),
            ),
        )

        assertFalse(settings.isEmptyHomePage(0))
        assertFalse(settings.isEmptyHomePage(1))
        assertFalse(settings.isEmptyHomePage(2))
    }

    @Test
    fun `a page holding only a widget is not empty`() {
        val perPage = grid.cellCount
        val settings = LauncherSettings(
            homeItems = mapOf(0 to HomeItem.App("app.a")),
            homePages = 2,
            widgets = mapOf(perPage to WidgetPlacement(5, provider, perPage, 1, 1)),
        )

        assertFalse(settings.isEmptyHomePage(1))
    }

    @Test
    fun `the cells a widget covers are never handed to an icon`() {
        val settings = LauncherSettings(
            homeItems = emptyMap(),
            widgets = mapOf(0 to WidgetPlacement(5, provider, 0, spanX = 2, spanY = 2)),
        )

        assertEquals(setOf(0, 1, 4, 5), settings.widgetCells())
    }

    @Test
    fun `the settings carry the grid the home screen is laid out on`() {
        val short = LauncherSettings(homeRows = 5)
        val tall = LauncherSettings(homeRows = 10)

        assertEquals(5, short.grid.rows)
        assertEquals(10, tall.grid.rows)
        assertEquals(LauncherSettings.DEFAULT_HOME_COLUMNS * 5, short.grid.cellCount)
        assertEquals(LauncherSettings.DEFAULT_HOME_COLUMNS * 10, tall.grid.cellCount)
    }

    @Test
    fun `each handle changes only its own dimension`() {
        // A 2x2 widget at cell 5: row 1, column 1, so it covers columns 1-2 and
        // rows 1-2.
        val widget = WidgetPlacement(7, provider, cell = 5, spanX = 2, spanY = 2)

        // Dragged out to the left: the left edge moves, the right one stays, so
        // the widget is one column wider and hangs from column 0.
        assertEquals(
            WidgetPlacement(7, provider, cell = 4, spanX = 3, spanY = 2),
            widget.resized(grid, ResizeEdge.LEFT, columns = -1, rows = 0),
        )

        // The right handle leaves the cell alone and only widens it.
        assertEquals(
            WidgetPlacement(7, provider, cell = 5, spanX = 3, spanY = 2),
            widget.resized(grid, ResizeEdge.RIGHT, columns = 1, rows = 0),
        )

        // The top handle moves the row the widget hangs from, and leaves the
        // width as it was.
        assertEquals(
            WidgetPlacement(7, provider, cell = 1, spanX = 2, spanY = 3),
            widget.resized(grid, ResizeEdge.TOP, columns = 0, rows = -1),
        )

        // The bottom handle only makes it taller.
        assertEquals(
            WidgetPlacement(7, provider, cell = 5, spanX = 2, spanY = 3),
            widget.resized(grid, ResizeEdge.BOTTOM, columns = 0, rows = 1),
        )
    }

    @Test
    fun `a finger that wanders sideways does not change the height`() {
        val widget = WidgetPlacement(7, provider, cell = 5, spanX = 2, spanY = 2)

        // Dragging the right handle down the screen is not a resize at all.
        assertEquals(widget, widget.resized(grid, ResizeEdge.RIGHT, columns = 0, rows = 4))
        assertEquals(widget, widget.resized(grid, ResizeEdge.BOTTOM, columns = 4, rows = 0))
    }

    @Test
    fun `an edge dragged past the far side of the widget stops at its own floor`() {
        // A 3x1 across columns 1-3 of the second row.
        val wide = WidgetPlacement(7, provider, cell = 5, spanX = 3, spanY = 1)

        // The left edge cannot pass the right one: it stops when only the two
        // columns the provider asked for are left.
        assertEquals(
            WidgetPlacement(7, provider, cell = 6, spanX = 2, spanY = 1),
            wide.resized(grid, ResizeEdge.LEFT, columns = 3, rows = 0, minSpanX = 2),
        )

        // And the same from the other side: pulling the right edge back towards
        // the left stops at those same two columns.
        assertEquals(
            WidgetPlacement(7, provider, cell = 5, spanX = 2, spanY = 1),
            wide.resized(grid, ResizeEdge.RIGHT, columns = -3, rows = 0, minSpanX = 2),
        )
    }

    @Test
    fun `no edge is ever dragged off the grid`() {
        // A widget filling the first column cannot grow to the left of it.
        val leftmost = WidgetPlacement(7, provider, cell = 0, spanX = 2, spanY = 2)

        assertEquals(
            WidgetPlacement(7, provider, cell = 0, spanX = 2, spanY = 2),
            leftmost.resized(grid, ResizeEdge.LEFT, columns = -3, rows = 0),
        )

        // Nor can one on the last row grow downwards off the bottom.
        val lastRow = grid.cellCount - grid.columns
        val bottomRow = WidgetPlacement(7, provider, cell = lastRow, spanX = 1, spanY = 1)

        assertEquals(
            bottomRow,
            bottomRow.resized(grid, ResizeEdge.BOTTOM, columns = 0, rows = 3),
        )

        // A widget filling the grid has nowhere further to go at all.
        val whole = WidgetPlacement(
            7,
            provider,
            cell = 0,
            spanX = grid.columns,
            spanY = grid.rows,
        )

        assertEquals(whole, whole.resized(grid, ResizeEdge.RIGHT, columns = 2, rows = 0))
        assertEquals(whole, whole.resized(grid, ResizeEdge.BOTTOM, columns = 0, rows = 2))
    }

    @Test
    fun `the bottom of the grid is where the grid says it is`() {
        // The same widget, stretched on two grids of different heights: what it
        // can be given follows the grid it is on. Cell 12 is row 3, so two rows
        // are left under it on the short grid and seven on the tall one.
        val short = HomeGrid(5)
        val tall = HomeGrid(10)
        val widget = WidgetPlacement(7, provider, cell = 12, spanX = 1, spanY = 1)

        val shortStretched = widget.resized(short, ResizeEdge.BOTTOM, columns = 0, rows = 20)
        val tallStretched = widget.resized(tall, ResizeEdge.BOTTOM, columns = 0, rows = 20)

        assertEquals(2, shortStretched.spanY)
        assertEquals(7, tallStretched.spanY)
        assertEquals(12, shortStretched.cell)
        assertEquals(12, tallStretched.cell)
    }

    @Test
    fun `a widget stretched on a later page keeps to that page`() {
        val perPage = grid.cellCount
        val widget = WidgetPlacement(7, provider, cell = perPage + 5, spanX = 2, spanY = 2)

        val wider = widget.resized(grid, ResizeEdge.RIGHT, columns = 1, rows = 0)

        assertEquals(perPage + 5, wider.cell)
        assertEquals(3, wider.spanX)
        assertTrue(wider.cellsIn(grid).all { it in perPage until perPage * 2 })
    }
}
