package com.sdev.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored form of the home screen and the arithmetic that moves cells around
 * when pages are added or taken away, or when the grid changes height.
 */
class HomeSlotsTest {

    /**
     * The grid most of this is written against: six rows, which is what the
     * launcher used before the rows were the user's to choose, and which makes
     * one page the twenty four cells the older tests were counted in.
     */
    private val grid = HomeGrid(6)

    /** One page of that grid. */
    private val page = grid.cellCount

    @Test
    fun `cells survive a write and a read`() {
        val stored = mapOf(0 to "app.a", 5 to "app.b", 23 to "app.c")

        assertEquals(stored, decodeSlots(encodeSlots(stored), page))
    }

    @Test
    fun `an empty cell is not written`() {
        // Emptied cells are held as empty strings while a move is in progress.
        val stored = mapOf(0 to "app.a", 1 to "")

        assertEquals("0=app.a", encodeSlots(stored))
    }

    @Test
    fun `nothing at all reads as no cells`() {
        assertEquals(emptyMap<Int, String>(), decodeSlots(null, page))
        assertEquals(emptyMap<Int, String>(), decodeSlots("", page))
    }

    @Test
    fun `cells beyond the capacity are ignored`() {
        // The stored form may be longer than the screen it is being read into.
        val raw = "0=app.a\n$page=app.b\n${grid.capacity}=app.c"

        assertEquals(mapOf(0 to "app.a"), decodeSlots(raw, page))
        assertEquals(
            mapOf(0 to "app.a", page to "app.b"),
            decodeSlots(raw, page * 2),
        )
    }

    @Test
    fun `malformed lines are skipped`() {
        val raw = "0=app.a\nnonsense\n=app.b\n7=\n9=app.c"

        assertEquals(mapOf(0 to "app.a", 9 to "app.c"), decodeSlots(raw, grid.capacity))
    }

    @Test
    fun `a key containing an equals sign survives`() {
        // Nothing stops a package name from holding one, and only the first
        // separator is the one that counts.
        val raw = "3=com.some.one=two/app.Activity"

        assertEquals(mapOf(3 to "com.some.one=two/app.Activity"), decodeSlots(raw, page))
    }

    @Test
    fun `inserting a page moves the pages after it along`() {
        val last = page - 1
        val cells = mapOf(0 to "a", last to "b", page to "c", page + last to "d")

        // A page inserted in front of everything: every cell shifts by one page.
        assertEquals(
            mapOf(
                page to "a",
                page + last to "b",
                page * 2 to "c",
                page * 2 + last to "d",
            ),
            shiftCellsFrom(cells, first = 0, delta = page),
        )

        // A page inserted between the first and the second.
        assertEquals(
            mapOf(
                0 to "a",
                last to "b",
                page * 2 to "c",
                page * 2 + last to "d",
            ),
            shiftCellsFrom(cells, first = page, delta = page),
        )

        // Appended at the end: nothing moves.
        assertEquals(
            cells,
            shiftCellsFrom(cells, first = page * 2, delta = page),
        )
    }

    @Test
    fun `removing a page closes the gap`() {
        // Page 0 is going and is empty, so everything after it moves back.
        val cells = mapOf(page to "b", page * 2 to "c")

        assertEquals(
            mapOf(0 to "b", page to "c"),
            shiftCellsFrom(cells, first = page, delta = -page),
        )
    }

    @Test
    fun `an icon goes where it was dropped when that cell is free`() {
        assertEquals(7, findFreeCell(occupied = setOf(0, 1, 2), capacity = 24, preferred = 7))
    }

    @Test
    fun `a drop on a taken cell lands at the nearest free one`() {
        // Forwards first, because that is the direction the finger was moving.
        assertEquals(8, findFreeCell(setOf(5, 6, 7), capacity = 9, preferred = 7))

        // And backwards when there is nothing free forwards.
        assertEquals(4, findFreeCell(setOf(5, 6, 7), capacity = 8, preferred = 7))

        // The last cell of the screen has only one direction left to look in.
        assertEquals(7, findFreeCell(setOf(8), capacity = 9, preferred = 8))
    }

    @Test
    fun `an icon added from the drawer takes the first free cell`() {
        assertEquals(1, findFreeCell(setOf(0), capacity = 24, preferred = null))
    }

    @Test
    fun `there is no cell when the screen is full`() {
        val full = (0 until 24).toSet()

        assertNull(findFreeCell(full, capacity = 24, preferred = null))
        assertNull(findFreeCell(full, capacity = 24, preferred = 10))
    }

    @Test
    fun `an icon avoids the cells under a widget`() {
        // A 2x1 widget at the top left covers cells 0 and 1.
        val covered = widgetCells(grid, 0, spanX = 2, spanY = 1)

        assertEquals(setOf(0, 1), covered)
        assertEquals(2, findFreeCell(covered, capacity = 24, preferred = null))
    }

    @Test
    fun `preferred cells outside the capacity are pulled back inside it`() {
        assertEquals(23, findFreeCell(emptySet(), capacity = 24, preferred = 999))
    }

    @Test
    fun `a widget only goes where all of it fits`() {
        // A 2x2 widget cannot start on cell 1: it would overhang the column.
        // Cell 0 is taken, so the first place it fits is cell 2.
        assertEquals(
            2,
            findFreeWidgetCell(grid, setOf(0, 1), spanX = 2, spanY = 2),
        )

        // Cell 1 is wide enough for a 2x1, so it is used as asked.
        assertEquals(
            1,
            findFreeWidgetCell(grid, emptySet(), spanX = 2, spanY = 1, preferred = 1),
        )

        // Cell 3 is the last column and cannot hold a 2x1, so the search falls
        // back to the first cell that can.
        assertEquals(
            0,
            findFreeWidgetCell(grid, emptySet(), spanX = 2, spanY = 1, preferred = 3),
        )
    }

    @Test
    fun `a widget never lands on an icon or on another widget`() {
        // Cells 0..9 taken by a row of icons and a 2x1 widget.
        val occupied = (0..9).toSet()

        // Rows 0 and 1 are blocked where the 2x2 widget would sit, so the first
        // place it fits is row 2, columns 2 and 3.
        assertEquals(10, findFreeWidgetCell(grid, occupied, spanX = 2, spanY = 2))
    }

    @Test
    fun `a widget that cannot fit anywhere gets nothing`() {
        val full = (0 until grid.capacity).toSet()

        assertNull(findFreeWidgetCell(grid, full, spanX = 1, spanY = 1))
    }

    @Test
    fun `a widget only fits where all of it is on the grid`() {
        // The last row of the grid, where a one row widget is as tall as it can
        // be and a two row one cannot fit at all.
        val lastRow = grid.cellCount - grid.columns

        assertTrue(widgetFitsGrid(grid, lastRow, spanX = 1, spanY = 1))
        assertTrue(!widgetFitsGrid(grid, lastRow, spanX = 1, spanY = 2))

        // Column 3 is the last of the four, so nothing can be two wide there.
        val lastColumn = grid.columns - 1

        assertTrue(widgetFitsGrid(grid, lastColumn, spanX = 1, spanY = 1))
        assertTrue(!widgetFitsGrid(grid, lastColumn, spanX = 2, spanY = 1))
    }

    @Test
    fun `how many rows there are decides how tall a widget may be`() {
        val short = HomeGrid(5)
        val tall = HomeGrid(10)

        val lastRowOfTall = tall.cellCount - tall.columns
        val lastRowOfShort = short.cellCount - short.columns

        // A two row widget fits at the bottom of neither, but three rows fit in
        // the taller grid and not in the shorter one.
        assertTrue(!widgetFitsGrid(tall, lastRowOfTall, spanX = 1, spanY = 2))
        assertTrue(widgetFitsGrid(tall, tall.firstCellOfPage(0) + 7 * tall.columns, spanX = 1, spanY = 3))
        assertTrue(!widgetFitsGrid(short, lastRowOfShort, spanX = 1, spanY = 2))
    }

    @Test
    fun `widget cells are counted from the page the widget is on`() {
        val second = grid.cellCount

        assertEquals(setOf(second, second + 1), widgetCells(grid, second, spanX = 2, spanY = 1))
    }

    @Test
    fun `a widget's size is read the way providers state it`() {
        // The system's own model: 70dp a cell plus 30dp, so one column is 40dp,
        // two are 110dp and four are 250dp.
        assertEquals(1, widgetSpanFor(40, 4))
        assertEquals(2, widgetSpanFor(110, 4))
        assertEquals(3, widgetSpanFor(180, 4))
        assertEquals(4, widgetSpanFor(250, 4))

        // A widget wider than the grid gets the whole grid, not more.
        assertEquals(4, widgetSpanFor(320, 4))
        assertEquals(4, widgetSpanFor(1000, 4))

        // A provider that says nothing still gets a cell, and rows are limited
        // by the grid's own height.
        assertEquals(1, widgetSpanFor(0, 4))
        assertEquals(grid.rows, widgetSpanFor(1000, grid.rows))
    }

    @Test
    fun `a widget is never given less than one cell`() {
        assertEquals(1, widgetSpanFor(1, 4))
        assertEquals(1, widgetSpanFor(-5, 4))
    }

    @Test
    fun `a widget keeps the cell it was dropped on when all of it fits`() {
        assertEquals(
            8,
            findNearestWidgetCell(grid, emptySet(), page = 0, spanX = 2, spanY = 1, preferred = 8),
        )
    }

    @Test
    fun `a widget that does not fit where it was dropped goes to the nearest place that does`() {
        // An icon on cell 5, and a four-wide widget let go at the start of the
        // row below: the row it was dropped on is no good, and the nearest row
        // that is, is the one above.
        assertEquals(
            0,
            findNearestWidgetCell(grid, setOf(5), page = 0, spanX = 4, spanY = 1, preferred = 4),
        )
    }

    @Test
    fun `a widget dropped on the last corner of a page is pulled back inside it`() {
        // The bottom right corner is where a two by two widget would hang off
        // both edges of the grid. The nearest place it fits is one column and one
        // row in from the corner.
        val corner = grid.cellCount - 1
        val wanted = (grid.rows - 2) * grid.columns + (grid.columns - 2)

        assertEquals(
            wanted,
            findNearestWidgetCell(grid, emptySet(), page = 0, spanX = 2, spanY = 2, preferred = corner),
        )
    }

    @Test
    fun `cells are renumbered when the home screen gains rows`() {
        // Three rows down on the second page, where the older grid held 24 cells
        // to a page and the newer one holds more.
        val perOldPage = LauncherSettings.HOME_COLUMNS * LauncherSettings.LEGACY_HOME_ROWS

        assertEquals(
            LauncherSettings.HOME_COLUMNS * LauncherSettings.HOME_ROWS + 2 * LauncherSettings.HOME_COLUMNS,
            remapHomeCell(perOldPage + 2 * LauncherSettings.HOME_COLUMNS, 6, LauncherSettings.HOME_ROWS),
        )

        // Every page keeps its own place, and its own place within the page.
        val old = mapOf(0 to "a", 5 to "b", perOldPage to "c", perOldPage + 7 to "d")
        val new = remapHomeCells(old, LauncherSettings.LEGACY_HOME_ROWS, LauncherSettings.HOME_ROWS)
        val perNewPage = LauncherSettings.HOME_COLUMNS * LauncherSettings.HOME_ROWS

        assertEquals("a", new[0])
        assertEquals("b", new[5])
        assertEquals("c", new[perNewPage])
        assertEquals("d", new[perNewPage + LauncherSettings.HOME_COLUMNS + 3])
    }

    @Test
    fun `a cell in a row the new grid no longer has is dropped`() {
        // Going the other way - a grid that lost rows - has nowhere to put the
        // bottom of a page, and such a cell is quietly left out rather than
        // landing somewhere it was never dropped.
        assertNull(remapHomeCell(5 * LauncherSettings.HOME_COLUMNS, 8, 4))
        assertEquals(
            mapOf(0 to "a"),
            remapHomeCells(
                mapOf(0 to "a", 5 * LauncherSettings.HOME_COLUMNS to "b"),
                8,
                4,
            ),
        )
    }

    @Test
    fun `renumbering is skipped when the grid is the one in use`() {
        val cells = mapOf(0 to "a", 9 to "b")

        assertEquals(cells, remapHomeCells(cells, LauncherSettings.HOME_ROWS, LauncherSettings.HOME_ROWS))
    }

    @Test
    fun `a widget is never carried onto another page to find room`() {
        val secondPageFull = (page until page * 2).toSet()

        // Page 0 is empty and would happily hold it, but the widget was let go
        // on page 1, and that is where it stays.
        assertNull(
            findNearestWidgetCell(
                grid,
                occupied = secondPageFull,
                page = 1,
                spanX = 1,
                spanY = 1,
                preferred = page,
            ),
        )
    }

    @Test
    fun `a page with no room at all offers nothing`() {
        val full = (0 until page).toSet()

        assertNull(findNearestWidgetCell(grid, full, page = 0, spanX = 1, spanY = 1, preferred = 10))
    }

    // --- the grid changing height --------------------------------------------

    @Test
    fun `a widget keeps its place when the grid does not change`() {
        val widgets = mapOf(0 to WidgetPlacement(1, PROVIDER, 0, 2, 2))

        val refit = refitWidgets(widgets, emptySet(), fromRows = 8, toRows = 8)

        assertEquals(widgets, refit.widgets)
        assertEquals(emptyList<Int>(), refit.dropped)
    }

    @Test
    fun `a widget on a row the shorter grid still has stays put`() {
        val widgets = mapOf(4 to WidgetPlacement(1, PROVIDER, 4, 2, 1))

        val refit = refitWidgets(widgets, setOf(0, 1, 2, 3), fromRows = 6, toRows = 5)

        assertEquals(4, refit.widgets.keys.single())
        assertEquals(emptyList<Int>(), refit.dropped)
    }

    @Test
    fun `a widget taller than the shorter grid is cut down to it`() {
        val widgets = mapOf(0 to WidgetPlacement(1, PROVIDER, 0, 2, 8))

        val refit = refitWidgets(widgets, emptySet(), fromRows = 8, toRows = 5)

        val fitted = refit.widgets.values.single()
        assertEquals(0, fitted.cell)
        assertEquals(5, fitted.spanY)
        assertEquals(2, fitted.spanX)
    }

    @Test
    fun `a widget on a row that has gone is carried up to the nearest free place`() {
        val sixRows = HomeGrid(6)
        val fiveRows = HomeGrid(5)

        // The bottom left cell of the second page of the older grid, with the
        // cells above it on that page taken by icons.
        val cell = sixRows.firstCellOfPage(1) + sixRows.cellCount - sixRows.columns
        val widgets = mapOf(cell to WidgetPlacement(7, PROVIDER, cell, 1, 1))
        val icons = fiveRows.cellsOfPage(1)
            .filter { fiveRows.cellOf(it) >= fiveRows.columns }
            .toSet()

        val refit = refitWidgets(widgets, icons, fromRows = 6, toRows = 5)

        val moved = refit.widgets.values.single()
        assertEquals(7, moved.hostId)
        assertEquals(1, moved.pageIn(fiveRows))
        assertTrue(moved.inside(fiveRows))
        assertTrue(moved.cell !in icons)
        assertEquals(emptyList<Int>(), refit.dropped)
    }

    @Test
    fun `a widget with nowhere left on its page is handed back`() {
        val sixRows = HomeGrid(6)
        val fiveRows = HomeGrid(5)

        val cell = sixRows.firstCellOfPage(0) + sixRows.cellCount - sixRows.columns
        val widgets = mapOf(cell to WidgetPlacement(42, PROVIDER, cell, 1, 1))
        val everyCellOfTheShorterPage = fiveRows.cellsOfPage(0).toSet()

        val refit = refitWidgets(widgets, everyCellOfTheShorterPage, fromRows = 6, toRows = 5)

        assertEquals(emptyList<WidgetPlacement>(), refit.widgets.values.toList())
        assertEquals(listOf(42), refit.dropped)
    }

    @Test
    fun `a page full of widgets keeps the ones that still fit`() {
        val sixRows = HomeGrid(6)
        val fiveRows = HomeGrid(5)

        // A widget for every cell of the last row of the older grid, all on page
        // 0. The shorter page has no room for them, so all four come back, one
        // per cell of the rows above - which are the nearest free places.
        val firstOfLastRow = sixRows.firstCellOfPage(0) + sixRows.cellCount - sixRows.columns
        val widgets = (0 until sixRows.columns).associate { column ->
            val cell = firstOfLastRow + column
            cell to WidgetPlacement(100 + column, PROVIDER, cell, 1, 1)
        }

        val refit = refitWidgets(widgets, emptySet(), fromRows = 6, toRows = 5)

        assertEquals(4, refit.widgets.size)
        assertEquals(emptyList<Int>(), refit.dropped)
        assertTrue(refit.widgets.values.all { it.inside(fiveRows) })
        assertTrue(refit.widgets.values.all { it.pageIn(fiveRows) == 0 })
        assertEquals(
            (100..103).toList(),
            refit.widgets.values.map { it.hostId }.sorted(),
        )
        assertEquals(4, refit.widgets.keys.toSet().size)
    }

    private companion object {
        const val PROVIDER = "com.example.clock/com.example.clock.ClockWidget"
    }
}
