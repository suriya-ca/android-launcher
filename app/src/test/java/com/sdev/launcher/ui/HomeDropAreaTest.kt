package com.sdev.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.sdev.launcher.data.HomeGrid
import com.sdev.launcher.data.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where something let go on the home screen lands.
 *
 * The places worth pinning down are the ones the old arithmetic got wrong: a
 * drop over the band along the very top - above the grid, but still on the page
 * - and a drop aimed at a column rather than at "somewhere on the page". Both
 * used to end up in the first free cell of the first row.
 */
class HomeDropAreaTest {

    private val columns = LauncherSettings.DEFAULT_HOME_COLUMNS
    private val rows = 5

    /** A grid of 100 pixel cells, starting at its own top left corner. */
    private val size = Size(columns * 100f, rows * 100f)
    private val grid = HomeGrid(rows)

    private fun cell(x: Float, y: Float): Int? =
        cellInGrid(Offset(x, y), size, grid, page = 0)

    @Test
    fun `a drop on the grid is the cell it is over`() {
        // First cell of the third row, second column.
        assertEquals(2 * columns + 1, cell(150f, 250f))
        assertEquals(0, cell(0f, 0f))
        assertEquals(rows * columns - 1, cell(columns * 100f - 1f, rows * 100f - 1f))
    }

    @Test
    fun `a drop over the band is the top row, under the finger`() {
        assertEquals(0, cell(10f, -1f))
        assertEquals(1, cell(150f, -50f))
        assertEquals(columns - 1, cell(columns * 100f - 1f, -500f))
    }

    @Test
    fun `the band is not a cell when nothing was carried in`() {
        // Something being moved on the page is not carried in from anywhere:
        // above the grid is nowhere, and it stays where it was.
        assertNull(
            cellInGrid(
                local = Offset(10f, -1f),
                size = size,
                grid = grid,
                page = 0,
                allowAbove = false,
            ),
        )
    }

    @Test
    fun `a drop below or beside the grid names nothing`() {
        assertNull(cell(10f, rows * 100f + 40f))
        assertNull(cell(-40f, 50f))
        assertNull(cell(columns * 100f + 40f, 50f))
    }

    @Test
    fun `a grid that has not been laid out names nothing`() {
        assertNull(cellInGrid(Offset(10f, 0f), Size.Zero, grid, page = 0))
    }

    @Test
    fun `the page being shown is the page named`() {
        assertEquals(
            grid.firstCellOfPage(1) + 2 * columns + 1,
            cellInGrid(Offset(150f, 250f), size, grid, page = 1),
        )
        assertEquals(
            grid.firstCellOfPage(2),
            cellInGrid(Offset(10f, -5f), size, grid, page = 2),
        )
    }

    @Test
    fun `the top row of the band follows the column under the finger`() {
        val area = HomeDropArea().apply {
            gridRect = Rect(Offset(0f, 40f), Size(columns * 100f, rows * 100f))
            shape = HomeGrid(rows)
            page = 1
        }

        val first = HomeGrid(rows).firstCellOfPage(1)
        assertEquals(first + 2, area.topRowAt(Offset(250f, 20f), band = 132f))
        assertEquals(first + columns - 1, area.topRowAt(Offset(399f, 0f), band = 132f))

        // Below the band is the grid's own business, and beside it is nothing.
        assertNull(area.topRowAt(Offset(250f, 200f), band = 132f))
        assertNull(area.topRowAt(Offset(-10f, 20f), band = 132f))
    }

    @Test
    fun `the dock is cut into slots across its width`() {
        val dock = Size(columns * 100f, 100f)
        val last = LauncherSettings.DEFAULT_DOCK_SLOTS - 1

        assertEquals(0, slotInDock(Offset(0f, 50f), dock))
        assertEquals(1, slotInDock(Offset(150f, 50f), dock))
        assertEquals(last, slotInDock(Offset(columns * 100f - 1f, 50f), dock))
        assertNull(slotInDock(Offset(10f, -1f), dock))
        assertNull(slotInDock(Offset(10f, 100f), dock))
        assertNull(slotInDock(Offset(10f, 10f), Size.Zero))
    }

    @Test
    fun `dock drops follow the apps that are shown`() {
        val dock = Size(300f, 100f)

        // Three apps in slots 0, 2 and 4, drawn evenly: the middle of the bar
        // is the second app, not the middle of the five configured slots.
        assertEquals(0, dockTargetAt(Offset(10f, 50f), dock, listOf(0, 2, 4)))
        assertEquals(2, dockTargetAt(Offset(150f, 50f), dock, listOf(0, 2, 4)))
        assertEquals(4, dockTargetAt(Offset(290f, 50f), dock, listOf(0, 2, 4)))

        // An empty dock still has somewhere for the first app to go.
        assertEquals(0, dockTargetAt(Offset(10f, 50f), dock, emptyList()))
        assertNull(dockTargetAt(Offset(-1f, 50f), dock, listOf(0, 2, 4)))
        assertNull(dockTargetAt(Offset(10f, 100f), dock, listOf(0, 2, 4)))
    }
}
