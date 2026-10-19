package com.sdev.launcher.data

/**
 * A widget the user has put on the home screen.
 *
 * [cell] is the top left cell it occupies, numbered across every page the same
 * way app cells are, and [spanX]/[spanY] are how many cells it covers.
 *
 * [provider] is the widget's provider as `package/class`, the same text
 * [android.content.ComponentName.flattenToString] gives. Keeping it as text
 * means the home screen's model has nothing in it from the framework, which is
 * what lets its arithmetic be checked off the device.
 */
data class WidgetPlacement(
    /** The host's id for this widget; the system knows the widget by it. */
    val hostId: Int,
    val provider: String,
    val cell: Int,
    val spanX: Int,
    val spanY: Int,
) {
    /** The app the widget came from. */
    val providerPackage: String get() = provider.substringBefore('/')

    /** The page the widget sits on. */
    fun pageIn(grid: HomeGrid): Int = grid.pageOf(cell)

    /** True when the widget stays inside the grid instead of hanging off it. */
    fun inside(grid: HomeGrid): Boolean = widgetFitsGrid(grid, cell, spanX, spanY)

    /** Every cell the widget covers. */
    fun cellsIn(grid: HomeGrid): Set<Int> = widgetCells(grid, cell, spanX, spanY)

    /** The same widget, moved to [cell]. */
    fun at(cell: Int): WidgetPlacement = copy(cell = cell)
}

/** True when a [spanX] by [spanY] widget anchored at [cell] stays on the grid. */
fun widgetFitsGrid(grid: HomeGrid, cell: Int, spanX: Int, spanY: Int): Boolean {
    val within = grid.cellOf(cell)
    val column = within % grid.columns
    val row = within / grid.columns

    return column + spanX <= grid.columns && row + spanY <= grid.rows
}

/** Every cell a [spanX] by [spanY] widget anchored at [cell] would cover. */
fun widgetCells(grid: HomeGrid, cell: Int, spanX: Int, spanY: Int): Set<Int> {
    val within = grid.cellOf(cell)
    val column = within % grid.columns
    val row = within / grid.columns
    val base = grid.firstCellOfPage(grid.pageOf(cell))

    return buildSet {
        for (dy in 0 until spanY) {
            for (dx in 0 until spanX) {
                val x = column + dx
                val y = row + dy
                if (x < grid.columns && y < grid.rows) {
                    add(base + y * grid.columns + x)
                }
            }
        }
    }
}

/**
 * Which edge of a selected widget a finger has hold of.
 *
 * One handle sits at the middle of each edge rather than at the corners, which
 * is both easier to catch on a widget that reaches the edge of the screen and
 * clearer about what a drag is going to do.
 */
enum class ResizeEdge { LEFT, RIGHT, TOP, BOTTOM }

/**
 * The widget as it would be if the handle on [edge] were dragged [columns] cells
 * across and [rows] cells down.
 *
 * Only the edge's own axis counts: the left and right handles change the width,
 * the top and bottom ones change the height, and a finger that wanders the other
 * way changes nothing. Keeping a handle to its own axis is what stops a resize
 * from quietly wrecking the other dimension.
 *
 * The edge that is not being dragged stays where it is, so a widget grows in the
 * direction the finger went. The result is kept on the grid and never made
 * smaller than [minSpanX] by [minSpanY], which both default to a single cell:
 * a widget can be squeezed to one cell even where its provider asked for more
 * room, because what a provider says about its own size is a preference rather
 * than a rule.
 */
fun WidgetPlacement.resized(
    grid: HomeGrid,
    edge: ResizeEdge,
    columns: Int,
    rows: Int,
    minSpanX: Int = 1,
    minSpanY: Int = 1,
): WidgetPlacement {
    val gridColumns = grid.columns
    val gridRows = grid.rows

    val within = grid.cellOf(cell)
    val startColumn = within % gridColumns
    val startRow = within / gridColumns
    val base = grid.firstCellOfPage(grid.pageOf(cell))

    var left = startColumn
    var right = startColumn + spanX
    var top = startRow
    var bottom = startRow + spanY

    val floorX = minSpanX.coerceIn(1, gridColumns)
    val floorY = minSpanY.coerceIn(1, gridRows)

    // Only the edge the finger has hold of is clamped, and only against the edge
    // opposite it. Clamping both would drag the far edge along when this one is
    // pulled in, which is how a resize from one side comes to move the widget
    // across the screen instead of making it smaller.
    when (edge) {
        ResizeEdge.LEFT -> left = (left + columns).coerceIn(0, (right - floorX).coerceAtLeast(0))
        ResizeEdge.RIGHT -> right = (right + columns).coerceIn((left + floorX).coerceAtMost(gridColumns), gridColumns)
        ResizeEdge.TOP -> top = (top + rows).coerceIn(0, (bottom - floorY).coerceAtLeast(0))
        ResizeEdge.BOTTOM -> bottom = (bottom + rows).coerceIn((top + floorY).coerceAtMost(gridRows), gridRows)
    }

    return copy(
        cell = base + top * gridColumns + left,
        spanX = right - left,
        spanY = bottom - top,
    )
}

/**
 * Widgets are stored as `cell=hostId;spanX;spanY;package/class` lines, one per
 * widget. None of those parts can contain a `;` or an `=`, so splitting is
 * enough.
 */
fun encodeWidgets(widgets: Map<Int, WidgetPlacement>): String = widgets.entries
    .sortedBy { it.key }
    .joinToString("\n") { (cell, widget) ->
        "$cell=${widget.hostId};${widget.spanX};${widget.spanY};${widget.provider}"
    }

fun decodeWidgets(grid: HomeGrid, raw: String?): Map<Int, WidgetPlacement> {
    if (raw.isNullOrEmpty()) return emptyMap()

    return raw.lineSequence().mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) return@mapNotNull null

        val cell = line.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
        if (cell !in 0 until grid.capacity) return@mapNotNull null

        val parts = line.substring(separator + 1).split(';')
        if (parts.size != 4) return@mapNotNull null

        val hostId = parts[0].toIntOrNull() ?: return@mapNotNull null
        val spanX = parts[1].toIntOrNull()?.coerceIn(1, grid.columns)
            ?: return@mapNotNull null
        val spanY = parts[2].toIntOrNull()?.coerceIn(1, grid.rows)
            ?: return@mapNotNull null

        // A provider is only usable when it names both halves of the pair.
        val provider = parts[3].takeIf { it.contains('/') } ?: return@mapNotNull null

        cell to WidgetPlacement(hostId, provider, cell, spanX, spanY)
    }.toMap()
}
