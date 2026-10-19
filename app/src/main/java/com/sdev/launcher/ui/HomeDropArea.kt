package com.sdev.launcher.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.sdev.launcher.data.HomeGrid
import com.sdev.launcher.data.LauncherSettings

/**
 * How much of the top of a page is the band that something is carried onto to
 * bring the home screen out. The whole band is the target - the pill drawn in it
 * says where it is - because a finger travelling up from the bottom of the
 * drawer needs somewhere to arrive at that is bigger than it looks.
 *
 * It is a place along the top of the screen rather than the bounds of anything
 * drawn, so the pull and the drop test the same thing.
 */
internal val DRAWER_PULL_ZONE = 132.dp

/**
 * Where the home screen is, and which cell a place on the screen belongs to.
 *
 * A drag is carried in the root's coordinates, and the home screen sits inside a
 * pager that slides it about. So a place on the screen has to be turned into a
 * place in the grid *through* the transforms Compose applied, rather than by
 * arithmetic on a position and a size copied out of a layout: a copied position
 * is only right until the page moves, and the page is exactly what moves while
 * something is being carried onto it.
 *
 * [LayoutCoordinates.localPositionOf] does that conversion, page offset and all.
 * That is the whole point of this class: there is one place that answers "which
 * cell is here", and both the mark that says where something will land and the
 * drop that puts it there ask it. They cannot disagree.
 */
@Stable
class HomeDropArea {

    /** The launcher's own surface, in root coordinates: the space a drag reports. */
    var root: LayoutCoordinates? by mutableStateOf(null)

    /** The grid as it is laid out, for turning a place on the screen into a cell. */
    var grid: LayoutCoordinates? by mutableStateOf(null)

    /** The dock as it is laid out, for turning a place on the screen into a slot. */
    var dock: LayoutCoordinates? by mutableStateOf(null)

    /**
     * The grid as it is drawn, in root coordinates.
     *
     * Only overlays that hang off the grid use this - the widget handles, the
     * shape that follows a carried widget. Hit testing never does: it asks
     * [grid] through [localPositionOf] instead, so a stale number cannot decide
     * where something lands.
     */
    var gridRect: Rect by mutableStateOf(Rect.Zero)

    /** The shape of a page: how many rows and columns the grid has. */
    var shape: HomeGrid by mutableStateOf(HomeGrid(LauncherSettings.HOME_ROWS))

    /**
     * Which home page is under the drag.
     *
     * Kept up to date as the page is turned rather than once it settles, so that
     * a page being turned to on the way past is the page a drop counts against.
     */
    var page: Int by mutableIntStateOf(0)

    /**
     * How much the home screen is drawn pulled back while it is being
     * rearranged.
     *
     * Only the *drawn* size needs this: a place on the screen is turned into a
     * place in the grid by [LayoutCoordinates.localPositionOf], which already
     * undoes the transform. This is for overlays that have to be sized like the
     * thing they are drawn over - the widget handles and the carried widget's
     * ghost - and it is 1 the rest of the time.
     */
    var scale: Float by mutableFloatStateOf(1f)

    /**
     * The cell of the home screen [position] is over, or null when it is over
     * none.
     *
     * [allowAbove] is what makes the band along the top of the page part of the
     * top row. Something carried in from another page is let go there on
     * purpose - the band is what brought the page out - so it lands in the top
     * row under the finger. Something being moved *on* the home screen is not
     * carried in, and letting it go above the grid leaves it where it was.
     *
     * Below the grid is the dock, which has slots of its own, and a place on
     * another page is not this page at all: both answer nothing.
     */
    fun cellAt(position: Offset, allowAbove: Boolean = true): Int? {
        val local = gridPoint(position) ?: return null
        val layout = grid ?: return null

        return cellInGrid(
            local = local,
            size = layout.size.toSize(),
            grid = shape,
            page = page,
            allowAbove = allowAbove,
        )
    }

    /**
     * The dock slot [position] is over, or null when it is over none.
     *
     * The dock is put away while the home screen is being rearranged, so callers
     * pass [enabled] as false then: a drop where the dock used to be must not
     * think it landed in it.
     */
    fun dockSlotAt(position: Offset, enabled: Boolean = true): Int? {
        if (!enabled) return null
        val local = dockPoint(position) ?: return null
        val layout = dock ?: return null
        return slotInDock(local, layout.size.toSize())
    }

    /** [position] in the grid's own space, where its top left corner is (0, 0). */
    private fun gridPoint(position: Offset): Offset? {
        val holder = root ?: return null
        val onto = grid ?: return null
        if (!holder.isAttached || !onto.isAttached) return null
        return onto.localPositionOf(holder, position)
    }

    /** [position] in the dock's own space. */
    private fun dockPoint(position: Offset): Offset? {
        val holder = root ?: return null
        val onto = dock ?: return null
        if (!holder.isAttached || !onto.isAttached) return null
        return onto.localPositionOf(holder, position)
    }

    /**
     * The top row of the page, under [position]'s column, for something let go
     * in the band that brings the home screen out.
     *
     * The band is above the grid, and while the page is still on its way in
     * from another page there is no grid on the screen under the finger at all -
     * so [cellAt] has nothing to read. The band still means the top of the
     * page, and the column is the one the finger is over: that is the cell it
     * gets.
     *
     * This is what keeps a drop in the band from being "the first free place on
     * the page", which is where a drop used to go regardless of where it was
     * aimed. [band] is how deep the band is, in pixels, from the top of the
     * screen.
     */
    fun topRowAt(position: Offset, band: Float): Int? {
        val rect = gridRect
        if (rect.width <= 0f || shape.columns <= 0) return null
        if (position.y > band) return null
        if (position.x < rect.left || position.x >= rect.right) return null

        val column = ((position.x - rect.left) / (rect.width / shape.columns))
            .toInt()
            .coerceIn(0, shape.columns - 1)

        return page * shape.cellCount + column
    }
}

/**
 * The cell of [grid] that [local] sits in, on [page].
 *
 * [local] is in the grid's own space: its top left corner is (0, 0). Anything
 * that moved the grid - the page it sits in, the pull-back of the edit mode -
 * has already been undone by whoever worked out where the finger is; see
 * [HomeDropArea.cellAt].
 *
 * Nothing is answered past the bottom or the sides. Above the top is the band
 * that brings the home screen out of another page, and [allowAbove] makes that
 * part of the top row rather than nowhere.
 */
internal fun cellInGrid(
    local: Offset,
    size: Size,
    grid: HomeGrid,
    page: Int,
    allowAbove: Boolean = true,
): Int? {
    if (size.width <= 0f || size.height <= 0f) return null
    if (grid.columns <= 0 || grid.rows <= 0) return null

    if (local.x < 0f || local.x >= size.width) return null

    val y = when {
        local.y < 0f && !allowAbove -> return null
        local.y < 0f -> 0f
        local.y >= size.height -> return null
        else -> local.y
    }

    val column = (local.x / (size.width / grid.columns)).toInt()
        .coerceIn(0, grid.columns - 1)
    val row = (y / (size.height / grid.rows)).toInt()
        .coerceIn(0, grid.rows - 1)

    return page * grid.cellCount + row * grid.columns + column
}

/** The dock slot [local] sits in, or null when it is over none. */
internal fun slotInDock(local: Offset, size: Size): Int? {
    val slots = LauncherSettings.DOCK_SLOTS
    if (slots <= 0 || size.width <= 0f || size.height <= 0f) return null
    if (local.x < 0f || local.y < 0f) return null
    if (local.x >= size.width || local.y >= size.height) return null

    return (local.x / (size.width / slots)).toInt().coerceIn(0, slots - 1)
}

/** Where the grid is drawn, in root coordinates. */
internal val HomeDropArea.gridOrigin: Offset get() = gridRect.topLeft

/** How big the grid is drawn, which is smaller while the home screen is pulled back. */
internal val HomeDropArea.gridSize: Size get() = gridRect.size * scale
