package com.sdev.launcher.data

import kotlin.math.abs

/**
 * The plain arithmetic behind the home screen: how cells are written down, how
 * they move when a page is added or taken away, and where a new icon or widget
 * can go.
 *
 * None of this needs a context, a device or a store, which is what makes it
 * possible to check it without running the launcher.
 */

/**
 * Cells are stored as `index=key` lines. Keys never contain a newline and never
 * contain `=`, and the index is only ever the leading digits, so a single split
 * is enough.
 */
fun encodeSlots(slots: Map<Int, String>): String = slots.entries
    .filter { it.value.isNotEmpty() }
    .sortedBy { it.key }
    .joinToString("\n") { "${it.key}=${it.value}" }

fun decodeSlots(raw: String?, capacity: Int): Map<Int, String> {
    if (raw.isNullOrEmpty()) return emptyMap()

    return raw.lineSequence().mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) return@mapNotNull null

        val index = line.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
        val key = line.substring(separator + 1)
        if (key.isEmpty() || index !in 0 until capacity) return@mapNotNull null

        index to key
    }.toMap()
}

/**
 * Renumbers everything at or after [first] by [delta] cells, which is what
 * putting a page in or taking one out amounts to. Anything before [first] stays
 * where it is.
 */
fun <T> shiftCellsFrom(items: Map<Int, T>, first: Int, delta: Int): Map<Int, T> =
    items.entries.associate { (index, value) ->
        (if (index >= first) index + delta else index) to value
    }

/**
 * Moves everything on page [from] to page [to], with the pages in between
 * sliding up to close the gap - which is what dragging one page thumbnail past
 * its neighbours amounts to.
 *
 * Everything keeps its place within its own page: only the page number changes.
 */
fun <T> movePageCells(items: Map<Int, T>, grid: HomeGrid, from: Int, to: Int): Map<Int, T> {
    if (from == to) return items

    val count = grid.cellCount

    fun movedTo(page: Int): Int = when {
        page == from -> to
        from < to && page in (from + 1)..to -> page - 1
        to < from && page in to until from -> page + 1
        else -> page
    }

    return items.entries.associate { (index, value) ->
        (movedTo(index / count) * count + index % count) to value
    }
}

/**
 * Rewrites a cell that was numbered on a grid of [fromRows] rows so that it means
 * the same place on one of [toRows] rows.
 *
 * Cells are numbered across the pages, so giving the home screen more rows
 * changes how many cells a page holds and every stored cell past the first page
 * would otherwise point at the wrong place. Null when the place the cell stands
 * for does not exist on the new grid, which is what a row that has been taken
 * away comes to.
 */
fun remapHomeCell(cell: Int, fromRows: Int, toRows: Int): Int? {
    if (cell < 0 || fromRows <= 0 || toRows <= 0) return null

    val columns = LauncherSettings.HOME_COLUMNS
    val perPage = columns * fromRows
    val page = cell / perPage
    val within = cell % perPage
    val row = within / columns
    val column = within % columns

    if (row >= toRows) return null

    return page * columns * toRows + row * columns + column
}

/**
 * [remapHomeCell] over a whole map of cells, dropping the entries that have no
 * place on the new grid.
 */
fun <T> remapHomeCells(items: Map<Int, T>, fromRows: Int, toRows: Int): Map<Int, T> {
    if (fromRows == toRows) return items

    return items.entries
        .mapNotNull { (cell, value) -> remapHomeCell(cell, fromRows, toRows)?.let { it to value } }
        .toMap()
}

/**
 * The cell [key] should go to: [preferred] when it is free, otherwise the
 * nearest free cell forwards from there, then the nearest one backwards, and
 * failing all that the first free cell there is. Null when there is no room.
 *
 * [occupied] is every cell that may not be used, which for the home screen
 * means the cells that already hold an icon or sit under a widget.
 */
fun findFreeCell(occupied: Set<Int>, capacity: Int, preferred: Int?): Int? {
    val free = { index: Int -> index !in occupied }

    if (preferred == null) return (0 until capacity).firstOrNull(free)

    val target = preferred.coerceIn(0, capacity - 1)
    return target.takeIf(free)
        ?: ((target + 1) until capacity).firstOrNull(free)
        ?: (target - 1 downTo 0).firstOrNull(free)
}

/**
 * The first cell on [page] that nothing is sitting on, or null when that page
 * is full. [occupied] is every cell that may not be used, which for the home
 * screen means the cells that hold an icon or sit under a widget.
 */
fun findFreeCellOnPage(occupied: Set<Int>, grid: HomeGrid, page: Int): Int? =
    grid.cellsOfPage(page).firstOrNull { it !in occupied }

/**
 * The cell a widget of [spanX] by [spanY] cells should be anchored at, by the
 * same rules as [findFreeCell] but with the whole of the room it needs kept
 * clear. Null when there is nowhere it fits.
 */
fun findFreeWidgetCell(
    grid: HomeGrid,
    occupied: Set<Int>,
    spanX: Int,
    spanY: Int,
    preferred: Int? = null,
): Int? {
    val capacity = grid.capacity
    val fits = { cell: Int ->
        if (cell < 0 || cell >= capacity) {
            false
        } else {
            val cells = widgetCells(grid, cell, spanX, spanY)
            widgetFitsGrid(grid, cell, spanX, spanY) && cells.all { it < capacity && it !in occupied }
        }
    }

    if (preferred != null && fits(preferred)) return preferred
    return (0 until capacity).firstOrNull(fits)
}

/**
 * The cell nearest [preferred] on [page] where a widget of [spanX] by [spanY]
 * fits, searching outwards a ring at a time. Null when that page has no room for
 * it.
 *
 * A widget covers a block of cells, and the cell its corner is let go over is
 * very often taken by an icon while the widget itself would sit perfectly well a
 * cell to the left. Looking for the nearest place that does fit is what makes
 * letting a big widget go land somewhere sensible rather than refusing to move
 * it at all.
 */
fun findNearestWidgetCell(
    grid: HomeGrid,
    occupied: Set<Int>,
    page: Int,
    spanX: Int,
    spanY: Int,
    preferred: Int,
): Int? {
    val first = grid.firstCellOfPage(page)
    val columns = grid.columns
    val rows = grid.rows

    fun fits(cell: Int): Boolean = widgetFitsGrid(grid, cell, spanX, spanY) &&
        widgetCells(grid, cell, spanX, spanY).none { it in occupied }

    if (fits(preferred)) return preferred

    val wantedColumn = grid.cellOf(preferred) % columns
    val wantedRow = grid.cellOf(preferred) / columns

    var best: Int? = null
    var bestDistance = Int.MAX_VALUE

    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val cell = first + row * columns + column
            val distance = maxOf(abs(column - wantedColumn), abs(row - wantedRow))
            if (distance < bestDistance && fits(cell)) {
                best = cell
                bestDistance = distance
            }
        }
    }

    return best
}

/** The widgets after a grid of [fromRows] rows has become one of [toRows]. */
data class WidgetRefit(
    /** The widgets, keyed by the cell each one now hangs from. */
    val widgets: Map<Int, WidgetPlacement>,
    /**
     * The host ids of widgets there was nowhere left for. The system is still
     * holding those, so whoever asks for the refit is the one to hand them back.
     */
    val dropped: List<Int>,
)

/**
 * The widgets as they stand on a grid of [toRows] rows, given that they were
 * placed on one of [fromRows] and that [icons] are the cells holding an app.
 *
 * A widget keeps its place whenever that place still exists and still holds the
 * whole of it. When the grid has lost the rows a widget sat on, or can no longer
 * fit all of it, it is carried to the nearest cell on its own page where it does
 * fit, with its span cut down to the new grid if it has to be. A widget whose
 * page has no room at all is left out, which is what [WidgetRefit.dropped] is
 * for.
 *
 * Taking rows away is the only way any of this happens, and something has to
 * give: a widget is better off moved and slightly smaller than gone, and better
 * off gone than hanging off the edge of the screen.
 */
fun refitWidgets(
    widgets: Map<Int, WidgetPlacement>,
    icons: Set<Int>,
    fromRows: Int,
    toRows: Int,
): WidgetRefit {
    if (widgets.isEmpty() || fromRows == toRows) return WidgetRefit(widgets, emptyList())

    val grid = HomeGrid(toRows)
    val fromPerPage = grid.columns * fromRows
    val placed = LinkedHashMap<Int, WidgetPlacement>()
    val dropped = mutableListOf<Int>()

    // In cell order, so that a widget whose place is shrinking away is decided
    // against the ones the user put higher up the page.
    widgets.entries.sortedBy { it.key }.forEach { (cell, widget) ->
        val spanX = widget.spanX.coerceIn(1, grid.columns)
        val spanY = widget.spanY.coerceIn(1, grid.rows)
        val occupied = icons + placed.values.flatMap { it.cellsIn(grid) }

        val wanted = remapHomeCell(cell, fromRows, toRows) ?: pullIntoGrid(cell, fromRows, toRows)
        val page = cell / fromPerPage
        val target = wanted
            .takeIf { widgetFitsGrid(grid, it, spanX, spanY) && widgetCells(grid, it, spanX, spanY).none { c -> c in occupied } }
            ?: findNearestWidgetCell(
                grid = grid,
                occupied = occupied,
                page = page.coerceIn(0, LauncherSettings.HOME_PAGE_LIMIT - 1),
                spanX = spanX,
                spanY = spanY,
                preferred = wanted,
            )

        if (target == null) {
            dropped += widget.hostId
        } else {
            placed[target] = widget.copy(cell = target, spanX = spanX, spanY = spanY)
        }
    }

    return WidgetRefit(placed, dropped)
}

/**
 * Where a cell the newer grid has no room for would sit if it were pulled into
 * it: the same column, on the bottom row the new grid has.
 *
 * A widget whose row has been taken away is better off at the bottom of the page
 * it was on than at the top of it: it is the nearest part of the page that is
 * still there.
 */
fun pullIntoGrid(cell: Int, fromRows: Int, toRows: Int): Int {
    val columns = LauncherSettings.HOME_COLUMNS
    val perOldPage = columns * fromRows
    val page = cell / perOldPage
    val within = cell % perOldPage
    val column = within % columns
    val row = (within / columns).coerceAtMost(toRows - 1)

    return page * columns * toRows + row * columns + column
}

/** The system's model of how big one cell is, in dp. */
private const val WIDGET_CELL_DP = 70

/** How much of a widget's stated size is padding around its cells. */
private const val WIDGET_PADDING_DP = 30

/**
 * How many cells wide or tall a widget that asks for [dp] wants, up to [max].
 *
 * A provider states its size against the system's own model of a cell - about
 * 70dp plus 30dp of padding, which is why a widget built for four columns asks
 * for 250dp - rather than against this launcher's grid. Reading it that way
 * means a four column widget is given four columns of a screen that has four,
 * instead of three and a bit rounded up.
 */
fun widgetSpanFor(dp: Int, max: Int): Int {
    val wanted = dp.coerceAtLeast(0) + WIDGET_PADDING_DP

    // Rounded up: a widget with a little room to spare looks better than one
    // that is a cell too small.
    val cells = (wanted + WIDGET_CELL_DP - 1) / WIDGET_CELL_DP

    return cells.coerceIn(1, max)
}
