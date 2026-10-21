package com.sdev.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the home screen's cells and pages line up, and how that follows from the
 * height of the grid - which is the user's to choose.
 *
 * Adding and taking away pages is expressed with these, so getting them wrong
 * would put icons on the wrong page.
 */
class HomePagesTest {

    /** The grid the launcher starts with. */
    private val grid = HomeGrid(LauncherSettings.HOME_ROWS)

    @Test
    fun `the first page starts at zero`() {
        assertEquals(0, grid.firstCellOfPage(0))
        assertEquals(0, grid.pageOf(0))
        assertEquals(0, grid.cellOf(0))
    }

    @Test
    fun `each page holds a full grid`() {
        val perPage = grid.cellCount

        // The last cell of page 0 and the first of page 1.
        assertEquals(0, grid.pageOf(perPage - 1))
        assertEquals(perPage - 1, grid.cellOf(perPage - 1))

        assertEquals(1, grid.pageOf(perPage))
        assertEquals(0, grid.cellOf(perPage))
    }

    @Test
    fun `a page and a cell always find their way back to the index`() {
        val perPage = grid.cellCount

        for (page in 0 until LauncherSettings.HOME_PAGE_LIMIT) {
            for (cell in listOf(0, 1, 7, perPage - 1)) {
                val index = grid.firstCellOfPage(page) + cell

                assertEquals(page, grid.pageOf(index))
                assertEquals(cell, grid.cellOf(index))
            }
        }
    }

    @Test
    fun `the screen has room for every page it allows`() {
        assertEquals(
            LauncherSettings.HOME_PAGE_LIMIT * grid.cellCount,
            grid.capacity,
        )
    }

    @Test
    fun `every row count the user may choose works out`() {
        LauncherSettings.HOME_ROW_CHOICES.forEach { rows ->
            val shape = HomeGrid(rows)

            assertEquals(LauncherSettings.DEFAULT_HOME_COLUMNS, shape.columns)
            assertEquals(rows, shape.rows)
            assertEquals(LauncherSettings.DEFAULT_HOME_COLUMNS * rows, shape.cellCount)
            assertEquals(LauncherSettings.HOME_PAGE_LIMIT * shape.cellCount, shape.capacity)

            // Page 1 begins where page 0 ends, whatever the height.
            assertEquals(shape.cellCount, shape.firstCellOfPage(1))
            assertEquals(1, shape.pageOf(shape.cellCount))
            assertEquals(0, shape.cellOf(shape.cellCount))

            // A page's cells are exactly the ones it claims.
            assertEquals(shape.cellCount, shape.cellsOfPage(1).count())
            assertEquals(
                shape.firstCellOfPage(1),
                shape.cellsOfPage(1).first,
            )
        }
    }

    @Test
    fun `an empty screen has no icons on any page`() {
        val settings = LauncherSettings(homePages = 3)

        for (page in 0 until 3) {
            assertEquals(true, settings.isEmptyHomePage(page))
        }
    }

    @Test
    fun `an icon counts on its own page and no other`() {
        val perPage = grid.cellCount
        val settings = LauncherSettings(
            homeItems = mapOf(perPage + 3 to HomeItem.App("app.a")),
            homePages = 3,
        )

        assertEquals(true, settings.isEmptyHomePage(0))
        assertEquals(false, settings.isEmptyHomePage(1))
        assertEquals(true, settings.isEmptyHomePage(2))
    }

    @Test
    fun `a taller grid moves where the pages begin`() {
        // The same cell index is on a different page once a page holds more.
        val short = HomeGrid(5)
        val tall = HomeGrid(10)

        val index = 45

        assertEquals(2, short.pageOf(index))
        assertEquals(1, tall.pageOf(index))
        assertEquals(5, short.cellOf(index))
        assertEquals(5, tall.cellOf(index))
    }

    @Test
    fun `an app is only ever in one place`() {
        val settings = LauncherSettings(
            homeItems = mapOf(4 to HomeItem.App("app.a")),
            dockItems = mapOf(0 to "app.b"),
        )

        assertEquals(HomeArea.HOME to 4, settings.cellOf("app.a"))
        assertEquals(HomeArea.DOCK to 0, settings.cellOf("app.b"))
        assertEquals(null, settings.cellOf("app.c"))
    }

    @Test
    fun `adding a page keeps the same page main`() {
        // Putting a page in front of the main one shifts it along with
        // everything else; adding one after it leaves it alone.
        assertEquals(1, mainPageAfterInsert(mainPage = 0, at = 0))
        assertEquals(3, mainPageAfterInsert(mainPage = 2, at = 2))
        assertEquals(2, mainPageAfterInsert(mainPage = 2, at = 3))
    }

    @Test
    fun `removing a page keeps the same page main where it can`() {
        // A page before the main one takes a page off its index; one after it
        // changes nothing.
        assertEquals(1, mainPageAfterRemove(mainPage = 2, page = 1, pages = 4))
        assertEquals(2, mainPageAfterRemove(mainPage = 2, page = 3, pages = 4))
    }

    @Test
    fun `removing the main page promotes the page that takes its place`() {
        // The next page slides into the gap, so it becomes main. When the main
        // page was the last one there is no next page, and the one before it
        // takes over.
        assertEquals(1, mainPageAfterRemove(mainPage = 1, page = 1, pages = 3))
        assertEquals(1, mainPageAfterRemove(mainPage = 2, page = 2, pages = 3))
        assertEquals(0, mainPageAfterRemove(mainPage = 0, page = 0, pages = 1))
    }
}
