package com.sdev.launcher.ui

import android.view.Gravity
import android.widget.TextView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sdev.launcher.R
import com.sdev.launcher.data.AppInfo
import com.sdev.launcher.data.HomeArea
import com.sdev.launcher.data.HomeGrid
import com.sdev.launcher.data.LauncherSettings
import com.sdev.launcher.data.WidgetPlacement
import com.sdev.launcher.data.findNearestWidgetCell
import com.sdev.launcher.data.widgetCells
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** How far the finger has to travel down before the shade is asked for. */
private val SHADE_SWIPE_DISTANCE = 64.dp

/** How far the cell marker is pulled in from the cell's edges. */
private val CELL_OUTLINE_INSET = 6.dp

/** How close to a side of the screen an icon has to be held to turn the page. */
private val PAGE_EDGE_WIDTH = 56.dp

/** Breathing room around a widget, so it does not touch its neighbours. */
private val WIDGET_MARGIN = 4.dp

/** How long a freshly pinned cell keeps pulsing once it is on screen. */
private const val REVEAL_PULSE_MS = 1400L

/** How long to wait before deciding a widget the system cannot describe is gone. */
private const val WIDGET_INFO_RETRY_MS = 600L

/** A swipe that takes longer than this is not a swipe, it is a drag. */
private const val SHADE_SWIPE_TIMEOUT_MS = 500L

/**
 * The home screen: the wallpaper, plus whatever the user has pinned to it.
 *
 * Icons are placed by hand rather than by a layout that scrolls, because a cell
 * has to mean something stable while it is being dragged between cells.
 * Long-pressing empty space offers the home options, long-pressing an icon and
 * letting go offers that app's actions, and a swipe down asks for the
 * notification shade.
 *
 * Where the cells are is published to [dropArea] as it is laid out, and the
 * cells a drag would land in are asked of it: the drag that places an icon is
 * decided above this screen, because it outlives the page, and it has to be
 * answered from the page as it is drawn right now.
 *
 * The home screen can be several pages wide. [page] is the one to show, and
 * [onPageChange] reports back the one the user turned to.
 */
@Composable
fun HomeScreen(
    state: LauncherUiState,
    controller: IconDragController,
    dropArea: HomeDropArea,
    handlers: IconGestureHandlers,
    widgetHost: WidgetHost,
    /** True while this page is the one being looked at. */
    active: Boolean,
    page: Int,
    onPageChange: (Int) -> Unit,
    onEmptyLongPress: () -> Unit,
    onEmptyTap: () -> Unit,
    onWidgetLongPress: (WidgetPlacement, Rect) -> Unit,
    onWidgetSelect: (Int) -> Unit,
    onDragStart: () -> Unit,
    onWidgetDrop: WidgetDropHandler,
    onWidgetRevealShown: () -> Unit,
    onSwipeDown: () -> Unit,
    onRevealShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val density = LocalDensity.current
    val iconSize = settings.iconSize.sizeDp.dp

    // While the home screen is being rearranged it is drawn pulled back a
    // little, so all of a page can be taken in at once.
    val editing = state.editing
    val editScale = if (editing) EDIT_CONTENT_SCALE else 1f

    var homeSize by remember { mutableStateOf(Size.Zero) }

    // Kept as well, so that a tap can be turned from a place within the home
    // screen into a place on the screen - which is the only space the grid
    // underneath can be asked about, since the two differ while the home screen
    // is drawn pulled back.
    var homeAt by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val pages = settings.homePages
    val pagePagerState = rememberPagerState(pageCount = { pages })

    // The ViewModel says which page should be showing - it moves the home
    // screen when an app is pinned to another page...
    LaunchedEffect(page) {
        if (pagePagerState.currentPage != page) pagePagerState.animateScrollToPage(page)
    }

    // ...and the pager reports back the page the user turned to.
    LaunchedEffect(pagePagerState) {
        snapshotFlow { pagePagerState.settledPage }
            .distinctUntilChanged()
            .collect(onPageChange)
    }

    // A separate collector for the page being turned to, so a drop counts
    // against the page coming in rather than the one it is leaving.
    LaunchedEffect(pagePagerState, dropArea) {
        snapshotFlow { pagePagerState.currentPage }
            .distinctUntilChanged()
            .collect { dropArea.page = it }
    }

    val drag = controller.drag
    val widgetDrag = controller.widgetDrag
    val moved = controller.moved

    // The band along the top that brings the home screen out of another page.
    val band = with(density) { DRAWER_PULL_ZONE.toPx() }

    val dropSlot = if (moved) {
        drag?.let { dropArea.dockSlotAt(it.position, enabled = !editing) }
    } else {
        null
    }
    val dropCell = if (drag != null && moved && dropSlot == null) {
        // An icon out of the drawer is on its way onto this page, and over the
        // band above the grid the top row is what the finger is over: that is
        // the cell marked out, and the cell it lands in. While the page is still
        // arriving the band answers by column instead, exactly as the drop
        // does - so the mark and the landing cannot disagree.
        val incoming = drag.area == DragArea.DRAWER
        dropArea.cellAt(drag.position, allowAbove = incoming)
            ?: if (incoming) dropArea.topRowAt(drag.position, band) else null
    } else {
        null
    }

    // A widget takes up a block of cells rather than one, so all of them are
    // marked out, and the block is shown as refused while any part of it is
    // taken or would hang off the grid. The anchor is the cell the drop itself
    // will use, so the hint cannot disagree with where the widget lands.
    val widgetDrop = if (moved) {
        widgetDrag?.let { current ->
            // A widget being brought in is aimed at the top row when it is over
            // the band above the grid, exactly as the drop itself reads it.
            val incoming = current.from == null
            val anchor = dropArea.cellAt(current.anchor, allowAbove = incoming)
                ?: dropArea.cellAt(current.position, allowAbove = incoming)
                ?: if (incoming) dropArea.topRowAt(current.position, band) else null

            widgetDropTarget(
                spanX = current.spanX,
                spanY = current.spanY,
                // A widget being carried does not count as being in its own
                // way, so the room it is leaving is free for it to land in.
                ignoring = current.from?.cell,
                anchor = anchor,
                settings = settings,
            )
        }
    } else {
        null
    }

    val dropCells = dropCell?.let { setOf(it) } ?: widgetDrop?.cells ?: emptySet()
    val dropAllowed = if (dropCell != null) {
        // Letting an icon go on a cell a widget is sitting on does nothing, so
        // the cell says so rather than the drop quietly vanishing.
        dropCell !in settings.widgetCells()
    } else {
        widgetDrop?.allowed != false
    }

    // Holding an icon or a widget against either side of the screen turns the
    // page, which is how things are carried from one home page to another.
    //
    // Only something that has actually been moved turns a page: a long press on
    // its own asks for a card, and an icon in the first column is close enough to
    // the edge that holding it still would otherwise turn the page by itself.
    val edge = with(density) { PAGE_EDGE_WIDTH.toPx() }
    val draggedX = (drag?.position ?: widgetDrag?.position)?.x
    val dragX = if (moved) draggedX else null
    val pageTurn = when {
        dragX == null || homeSize.width <= 0f -> 0
        dragX < edge -> -1
        dragX > homeSize.width - edge -> 1
        else -> 0
    }

    LaunchedEffect(pageTurn) {
        if (pageTurn == 0) return@LaunchedEffect

        val target = (pagePagerState.currentPage + pageTurn).coerceIn(0, pages - 1)
        if (target != pagePagerState.currentPage) pagePagerState.animateScrollToPage(target)
    }

    /** True when [position] is a cell that holds nothing and has no widget on it. */
    fun isEmptyCell(position: Offset): Boolean {
        // Not the band: this is about a press on the page as it is, and the
        // space above the grid is not a cell of it.
        val cell = dropArea.cellAt(position, allowAbove = false)
        if (cell != null) {
            return cell !in state.homeApps && cell !in state.settings.widgetCells()
        }

        val slot = dropArea.dockSlotAt(position, enabled = !editing)
        if (slot != null) return slot !in state.dockApps

        return true
    }

    // How many cells a page has, and how far it is drawn pulled back: the first
    // for anything working out a cell, the second for overlays that have to be
    // sized like the thing they are drawn over.
    SideEffect {
        dropArea.shape = settings.grid
        dropArea.scale = editScale
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                homeAt = it
                homeSize = it.size.toSize()
            }
            // Empty space: a tap puts the widget handles and the edit mode away
            // again, and a long press asks for the home options. Cells under a
            // widget are not empty, and a widget long-presses for its own card.
            .pointerInput(
                state.homeApps,
                state.dockApps,
                state.settings.widgets,
                state.settings.homeItems,
            ) {
                detectTapGestures(
                    onTap = { position ->
                        val at = homeAt?.localToRoot(position)
                        if (at != null && isEmptyCell(at)) onEmptyTap()
                    },
                    onLongPress = { position ->
                        val at = homeAt?.localToRoot(position)
                        if (at != null && isEmptyCell(at)) onEmptyLongPress()
                    },
                )
            }
            // Anywhere: a quick swipe down asks for the notification shade.
            .pointerInput(Unit) {
                val distance = with(density) { SHADE_SWIPE_DISTANCE.toPx() }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedAt = System.currentTimeMillis()
                    var traveledY = 0f
                    var traveledX = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            val quick = System.currentTimeMillis() - startedAt < SHADE_SWIPE_TIMEOUT_MS
                            val downward = traveledY > distance && traveledY > abs(traveledX) * 1.5f

                            if (!controller.active && !editing && quick && downward) onSwipeDown()
                            break
                        }

                        val delta = change.position - change.previousPosition
                        traveledY += delta.y
                        traveledX += delta.x
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                // Room along the bottom for the edit mode's strip and buttons,
                // which is outside the scaled part.
                .padding(bottom = if (editing) EDIT_MODE_HEIGHT else 0.dp)
                .graphicsLayer {
                    scaleX = editScale
                    scaleY = editScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        // The layout itself, so that a place on the screen can
                        // be turned into a cell through the transforms Compose
                        // applied - the page this grid sits on included.
                        dropArea.grid = coordinates
                        dropArea.gridRect = Rect(
                            coordinates.positionInRoot(),
                            coordinates.size.toSize(),
                        )
                    },
            ) {
                val cellWidth = maxWidth / settings.grid.columns.toFloat()
                val cellHeight = maxHeight / settings.grid.rows.toFloat()

                // How big a cell really is, in dp, is what the widget system
                // needs told: a widget's room is the cells it covers, and how
                // many of them there are is the user's to choose.
                SideEffect {
                    widgetHost.cellWidthDp = cellWidth.value
                    widgetHost.cellHeightDp = cellHeight.value
                }

                HorizontalPager(
                    state = pagePagerState,
                    beyondViewportPageCount = 1,
                    // An icon in the air must not also turn the page: the side
                    // of the screen is what does that.
                    userScrollEnabled = !controller.active,
                    modifier = Modifier.fillMaxSize(),
                ) { homePage ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // The page's own outline, while the home screen is being
                        // rearranged: it says where one page stops and the next
                        // one starts, and it is drawn inside the page so that it
                        // travels with it - mid-swipe both edges can be seen.
                        if (editing) PageFrame()

                        HomeGrid(
                            state = state,
                            page = homePage,
                            iconSize = iconSize,
                            controller = controller,
                            handlers = handlers,
                            drag = drag,
                            reveal = state.revealCell,
                            active = active,
                            onRevealShown = onRevealShown,
                        )

                        WidgetLayer(
                            state = state,
                            page = homePage,
                            widgetHost = widgetHost,
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            controller = controller,
                            onLongPress = onWidgetLongPress,
                            onSelect = onWidgetSelect,
                            onDragStart = onDragStart,
                            onDrop = onWidgetDrop,
                            revealed = active && state.revealWidget != null,
                            onRevealShown = onWidgetRevealShown,
                            onEditingTap = onEmptyTap,
                        )

                        // Where the thing in the air would land. This is drawn
                        // last on the page, and so over the widgets as well: a
                        // widget is an opaque view of its own, and a hint painted
                        // underneath one is a hint nobody can see.
                        if (moved) {
                            DropGridOverlay(
                                grid = settings.grid,
                                page = homePage,
                                dropCells = dropCells,
                                dropAllowed = dropAllowed,
                            )
                        }
                    }
                }
            }

            // The dots say which page is showing; in the edit mode the strip of
            // page thumbnails says it instead, and the dock gives up its place
            // to the buttons.
            if (pages > 1 && !editing && !moved) {
                PageDots(
                    pageCount = pages,
                    currentPage = pagePagerState.currentPage,
                )
            }

            if (!editing && settings.showDock) {
                HomeDock(
                    state = state,
                    iconSize = iconSize,
                    dockSlots = settings.dockSlots,
                    controller = controller,
                    handlers = handlers,
                    drag = drag,
                    dropSlot = dropSlot,
                    reveal = state.revealCell,
                    active = active,
                    onRevealShown = onRevealShown,
                    onBounds = { coordinates ->
                        dropArea.dock = coordinates
                    },
                )
            }
        }

        // Which cells on a page are taken, for the little page thumbnails.
        val cellsOnPage: (Int) -> Set<Int> = { page ->
            val cells = settings.grid.cellsOfPage(page)
            (state.homeApps.keys + state.settings.widgetCells())
                .filter { it in cells }
                .toSet()
        }

        // The edit mode's own strip and buttons are drawn above the pages, up in
        // the launcher, so that they are never under the menu the pages are
        // holding open.

        // While something is being carried, the pages appear along the bottom so
        // that it can be put down on another one.
        if (moved && pages > 1 && !editing) {
            DragPageStrip(
                pages = pages,
                currentPage = pagePagerState.currentPage,
                rows = settings.grid.rows,
                targetPage = (drag?.position ?: widgetDrag?.position)?.let {
                    controller.dragPageAt(it)
                },
                cellsOnPage = cellsOnPage,
                onBounds = { controller.dragPages = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = DRAG_STRIP_CLEARANCE),
            )
        }

        // The thumbnails are what a drop is aimed at, so when they are not on
        // screen nothing should be dropped on them: without this, letting
        // something go where the strip used to be would put it on the page the
        // strip showed the last time it was up.
        LaunchedEffect(moved, pages, editing) {
            if (!moved || pages <= 1 || editing) controller.dragPages = emptyList()
        }
    }
}

/** How far up the page strip sits while something is being carried, so the dock
 * stays visible underneath it. */
private val DRAG_STRIP_CLEARANCE = 104.dp

/** Where a widget being carried would land, and whether it may land there. */
private data class WidgetDropTarget(val cells: Set<Int>, val allowed: Boolean)

/**
 * The cells a widget being dragged would take, and whether it may take them.
 *
 * [anchor] is the cell under the widget's own corner. A widget covers a block of
 * cells, so the corner having no room does not mean the widget has none: the
 * nearest place that does fit is marked out instead, which is the same place the
 * drop itself will use - so what is shown and what happens cannot disagree.
 * Nothing is marked out when the finger is not over the grid at all.
 */
private fun widgetDropTarget(
    spanX: Int,
    spanY: Int,
    ignoring: Int?,
    anchor: Int?,
    settings: LauncherSettings,
): WidgetDropTarget? {
    if (anchor == null) return null

    val grid = settings.grid
    val occupied = settings.homeItems.keys +
        settings.widgets.values
            .filter { it.cell != ignoring }
            .flatMap { it.cellsIn(grid) }

    val cell = findNearestWidgetCell(
        grid = grid,
        occupied = occupied,
        page = grid.pageOf(anchor),
        spanX = spanX,
        spanY = spanY,
        preferred = anchor,
    ) ?: return WidgetDropTarget(
        cells = widgetCells(grid, anchor, spanX, spanY),
        allowed = false,
    )

    return WidgetDropTarget(
        cells = widgetCells(grid, cell, spanX, spanY),
        allowed = true,
    )
}

/** One page of the home screen grid. */
@Composable
private fun HomeGrid(
    state: LauncherUiState,
    page: Int,
    iconSize: Dp,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    drag: DragState?,
    reveal: Pair<HomeArea, Int>?,
    active: Boolean,
    onRevealShown: () -> Unit,
) {
    val grid = state.settings.grid
    val firstCell = grid.firstCellOfPage(page)

    Column(modifier = Modifier.fillMaxSize()) {
        repeat(grid.rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(grid.columns) { column ->
                    val index = firstCell + row * grid.columns + column
                    val held = drag?.let { it.area == DragArea.HOME && it.index == index } == true

                    HomeCell(
                        app = state.homeApps[index],
                        area = HomeArea.HOME,
                        index = index,
                        state = state,
                        iconSize = iconSize,
                        controller = controller,
                        handlers = handlers,
                        // The grid a thing would land in is drawn over the whole
                        // page, so the cells themselves have nothing to say about
                        // it - only the pulse a newly pinned icon leaves behind.
                        outlined = false,
                        highlighted = false,
                        dimmed = held,
                        revealed = active && reveal == Pair(HomeArea.HOME, index),
                        onRevealShown = onRevealShown,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The row of always-visible apps at the bottom of the home screen. */
@Composable
private fun HomeDock(
    state: LauncherUiState,
    iconSize: Dp,
    dockSlots: Int,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    drag: DragState?,
    dropSlot: Int?,
    reveal: Pair<HomeArea, Int>?,
    active: Boolean,
    onRevealShown: () -> Unit,
    onBounds: (LayoutCoordinates) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 4.dp, vertical = 8.dp)
            // Measured last so the bounds describe the slots, not the padding
            // around them.
            .onGloballyPositioned { onBounds(it) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(dockSlots) { index ->
            val held = drag?.let { it.area == DragArea.DOCK && it.index == index } == true

            HomeCell(
                app = state.dockApps[index],
                area = HomeArea.DOCK,
                index = index,
                state = state,
                iconSize = iconSize - 4.dp,
                controller = controller,
                handlers = handlers,
                // The dock's slots show themselves as soon as something is in
                // the air and moving, at the same moment as the grid behind them.
                outlined = controller.moved,
                highlighted = dropSlot == index,
                dimmed = held,
                revealed = active && reveal == Pair(HomeArea.DOCK, index),
                onRevealShown = onRevealShown,
                showLabel = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The widgets sitting on one page of the home screen. */
@Composable
private fun WidgetLayer(
    state: LauncherUiState,
    page: Int,
    widgetHost: WidgetHost,
    cellWidth: Dp,
    cellHeight: Dp,
    controller: IconDragController,
    onLongPress: (WidgetPlacement, Rect) -> Unit,
    onSelect: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrop: WidgetDropHandler,
    revealed: Boolean,
    onRevealShown: () -> Unit,
    onEditingTap: () -> Unit,
) {
    val widgets = state.settings.widgets.values
    if (widgets.isEmpty()) return

    val grid = state.settings.grid
    widgets.filter { it.pageIn(grid) == page }.forEach { placement ->
        // Keyed by the widget rather than by its cell, so moving it only moves
        // the view it already has.
        key(placement.hostId) {
            var coordinates by remember(placement.hostId) {
                mutableStateOf<LayoutCoordinates?>(null)
            }
            var bounds by remember(placement.hostId) { mutableStateOf(Rect.Zero) }

            // Where the widget would sit while a corner is being dragged out,
            // so the room it is being given can be seen before it is let go.
            // The handles themselves are drawn up in the launcher, above the
            // dim, where nothing can clip them.
            val shown = controller.widgetPreview
                ?.takeIf { it.hostId == placement.hostId }
                ?: placement
            val within = grid.cellOf(shown.cell)
            val column = within % grid.columns
            val row = within / grid.columns
            val held = controller.widgetDrag?.from?.cell == placement.cell
            val flashing = revealed && state.revealWidget == placement.cell

            Box(
                modifier = Modifier
                    .offset(x = cellWidth * column, y = cellHeight * row)
                    .size(
                        width = cellWidth * shown.spanX,
                        height = cellHeight * shown.spanY,
                    )
                    .alpha(if (held) 0.4f else 1f)
                    .onGloballyPositioned {
                        coordinates = it
                        bounds = it.boundsInRoot()
                    }
                    .widgetGesture(
                        placement = placement,
                        controller = controller,
                        coordinatesAt = { coordinates },
                        boundsAt = { bounds },
                        onSelect = { onSelect(it.cell) },
                        onDragStart = onDragStart,
                        onLongPress = onLongPress,
                        onDrop = onDrop,
                    )
                    .padding(WIDGET_MARGIN),
            ) {
                AppWidgetView(
                    placement = placement,
                    widgetHost = widgetHost,
                    modifier = Modifier.fillMaxSize(),
                )

                // In the edit mode a widget is scenery. The screen is live again
                // behind the dim so that its pages can be swiped between, and
                // that also means a tap would otherwise reach the widget's own
                // controls and start music, or open the app it belongs to. The
                // cover takes the tap and gives it to the mode, which is what
                // the dim over everything used to do with it. It does not take
                // the drag: a swipe across a widget still turns the page.
                if (state.editing) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(placement.hostId) {
                                detectTapGestures { onEditingTap() }
                            },
                    )
                }

                if (flashing) {
                    WidgetFlash(onDone = onRevealShown)
                }
            }
        }
    }
}

/** The widget itself, or a note that the system no longer has it. */
@Composable
private fun AppWidgetView(
    placement: WidgetPlacement,
    widgetHost: WidgetHost,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.widget_unavailable)
    var info by remember(placement.hostId) { mutableStateOf(widgetHost.infoOf(placement.hostId)) }

    // A widget that has only just been bound may not be described yet, so ask
    // again shortly before deciding it has gone away.
    LaunchedEffect(placement.hostId) {
        if (info == null) {
            delay(WIDGET_INFO_RETRY_MS)
            info = widgetHost.infoOf(placement.hostId)
        }
    }

    val found = info
    if (found == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = unavailable,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = {
            // The view comes from the host and is the same one every time, so
            // the widget is only ever drawn by one thing and the updates the
            // system sends have somewhere to land. A widget the system refuses
            // to hand over must not take the whole home screen down with it.
            widgetHost.viewOf(placement.hostId, found)
                ?: TextView(context).apply {
                    text = unavailable
                    gravity = Gravity.CENTER
                }
        },
    )
}

/** A ring around a widget that has just arrived. */
@Composable
private fun BoxScope.WidgetFlash(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(REVEAL_PULSE_MS)
        onDone()
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(18.dp))
            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
    )
}

/** How far the edit mode's page frame is pulled in from the grid it surrounds. */
private val PAGE_FRAME_INSET = 10.dp

/**
 * The outline a page wears while the home screen is being rearranged: a rounded
 * frame, faintly filled, around the whole grid.
 *
 * It is drawn inside the page rather than over the home screen as a whole so that
 * it travels with the page it belongs to - mid-swipe the two pages show their own
 * frames and the seam between them can be seen. It sits under the icons, which is
 * why it is the faintest thing here.
 */
@Composable
private fun BoxScope.PageFrame() {
    val colours = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(PAGE_FRAME_INSET)
            .clip(shape)
            .background(colours.surface.copy(alpha = 0.12f))
            .border(2.dp, colours.primary.copy(alpha = 0.35f), shape),
    )
}

/** How far the drop grid's tiles are pulled in, so that they read as cells. */
private val DROP_TILE_INSET = 3.dp

/**
 * The grid, drawn over everything on a page while something is being carried.
 *
 * Every cell shows as a soft tile, so the shape of the grid is visible, and the
 * cells the thing would land in are filled - in the accent colour, or the error
 * colour when they will not take it.
 *
 * It is drawn over the icons and over the widgets both, rather than by each cell
 * as it draws itself, because a widget is an opaque view of its own: a hint
 * painted underneath one is a hint nobody can see.
 */
@Composable
private fun BoxScope.DropGridOverlay(
    grid: HomeGrid,
    page: Int,
    dropCells: Set<Int>,
    dropAllowed: Boolean,
) {
    val colours = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val first = grid.firstCellOfPage(page)

    Column(modifier = Modifier.matchParentSize()) {
        repeat(grid.rows) { row ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                repeat(grid.columns) { column ->
                    val index = first + row * grid.columns + column
                    val landing = index in dropCells

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(DROP_TILE_INSET)
                            .clip(shape)
                            .background(
                                when {
                                    !landing -> colours.surface.copy(alpha = 0.28f)
                                    dropAllowed -> colours.primary.copy(alpha = 0.55f)
                                    else -> colours.error.copy(alpha = 0.55f)
                                },
                            )
                            .then(
                                if (landing) {
                                    Modifier
                                } else {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = colours.onSurfaceVariant.copy(alpha = 0.35f),
                                        shape = shape,
                                    )
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** One cell of the home screen, empty or not. */
@Composable
private fun HomeCell(
    app: AppInfo?,
    area: HomeArea,
    index: Int,
    state: LauncherUiState,
    iconSize: Dp,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    outlined: Boolean,
    highlighted: Boolean,
    dimmed: Boolean,
    revealed: Boolean,
    onRevealShown: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = state.settings.showLabels,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // While something is in the air the grid shows itself, so it is obvious
        // which cell an icon is about to land in.
        if (outlined || revealed) {
            CellOutline(
                highlighted = highlighted,
                pulsing = revealed,
                onPulseShown = onRevealShown,
            )
        }

        if (app == null) return@Box

        val badgeCount = if (state.settings.showNotificationBadges) {
            state.notificationCounts[app.componentName.packageName] ?: 0
        } else {
            0
        }

        DraggableIcon(
            app = app,
            icon = state.icons[app.key],
            area = area.asDragArea(),
            index = index,
            controller = controller,
            handlers = handlers,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (dimmed) 0.4f else 1f),
            iconSize = iconSize,
            showLabel = showLabel,
            labelColor = Color.White,
            labelShadow = true,
            badgeCount = badgeCount,
        )
    }
}

/** The rounded cell marker: a hint while dragging, a pulse after a pin. */
@Composable
private fun BoxScope.CellOutline(
    highlighted: Boolean,
    pulsing: Boolean,
    onPulseShown: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    // A pin that happened somewhere else - the drawer, usually - is announced
    // once this screen is actually in front of the user.
    LaunchedEffect(pulsing) {
        if (!pulsing) return@LaunchedEffect
        delay(REVEAL_PULSE_MS)
        onPulseShown()
    }

    val pulse = rememberInfiniteTransition(label = "cell-pulse")
    val pulsed by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cell-pulse-alpha",
    )

    val alpha = when {
        pulsing -> pulsed
        highlighted -> 0.45f
        else -> 0.16f
    }

    val colour = when {
        highlighted || pulsing -> colors.primary
        else -> colors.onSurface
    }

    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(CELL_OUTLINE_INSET)
            .clip(RoundedCornerShape(18.dp))
            .background(colour.copy(alpha = alpha)),
    )
}

/** [Size] from an integer size, in the same call so no extra imports are needed. */
private fun androidx.compose.ui.unit.IntSize.toSize(): Size =
    Size(width.toFloat(), height.toFloat())
