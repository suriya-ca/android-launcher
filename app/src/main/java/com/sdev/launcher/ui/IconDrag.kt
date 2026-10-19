package com.sdev.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sdev.launcher.data.AppInfo
import com.sdev.launcher.data.HomeArea
import com.sdev.launcher.data.HomeGrid
import com.sdev.launcher.data.LauncherSettings
import com.sdev.launcher.data.WidgetPlacement
import kotlin.math.roundToInt

/** Which part of the launcher an icon was picked up from. */
enum class DragArea { DRAWER, HOME, DOCK }

/** An icon being carried: where it came from and where the finger is. */
data class DragState(
    val app: AppInfo,
    val area: DragArea,
    /** Home cell or dock slot; unused for the drawer. */
    val index: Int,
    /** Finger position in root coordinates. */
    val position: Offset,
    /**
     * True once the press has moved far enough to be a drag rather than a long
     * press. A long press on its own only asks for the app's actions.
     */
    val moved: Boolean = false,
)

/** What an icon does when it is tapped, long-pressed without moving, or dropped. */
class IconGestureHandlers(
    val onTap: (AppInfo) -> Unit,
    /**
     * Long-pressed: the actions for this app are wanted. [Rect] is where the
     * icon is, so the card that offers them can hang off it.
     */
    val onLongPress: (AppInfo, DragArea, Int, Rect) -> Unit,
    /** The press has started to move, so it is a drag and not a request. */
    val onDragStart: () -> Unit,
    val onDrop: (DragState, Offset) -> Unit,
)

/** A widget being carried to another cell. */
data class WidgetDragState(
    val placement: WidgetPlacement,
    /** Finger position in root coordinates. */
    val position: Offset,
    /** Where in the widget the finger took hold of it. */
    val grab: Offset,
    /** True once the press has moved far enough to be a drag. */
    val moved: Boolean = false,
) {
    /**
     * The widget's top left corner, in root coordinates, where it is being
     * carried.
     *
     * The widget keeps the point the finger took hold of, so it does not jump
     * when it is picked up, and this is also the corner the cell it would land
     * in is worked out from - which means it lands exactly where it was shown.
     */
    val anchor: Offset get() = position - grab
}

/**
 * The one drag that is in flight, plus the home screen geometry needed to work
 * out where it would land.
 *
 * This lives above the pages rather than inside the home screen because a drag
 * has to survive the pages moving underneath it: an icon picked up in the
 * drawer is still being carried when the drawer slides away to reveal home.
 * The home screen keeps the geometry here up to date, in root coordinates.
 */
@Stable
class IconDragController {

    var drag by mutableStateOf<DragState?>(null)
        private set

    var widgetDrag by mutableStateOf<WidgetDragState?>(null)
        private set

    var gridOrigin by mutableStateOf(Offset.Zero)
    var gridSize by mutableStateOf(Size.Zero)
    var dockOrigin by mutableStateOf(Offset.Zero)
    var dockSize by mutableStateOf(Size.Zero)

    /**
     * Which home screen page is showing while a drag is in flight. Cells are
     * numbered across every page, so this is what turns a place on the visible
     * grid into an index the rest of the launcher understands.
     */
    var homePage by mutableIntStateOf(0)

    /**
     * The shape of the home screen grid, kept up to date by the home screen
     * because the user can change how many rows a page has.
     */
    var grid by mutableStateOf(HomeGrid(LauncherSettings.HOME_ROWS))

    /**
     * How much the home screen is drawn pulled back while it is being
     * rearranged.
     *
     * The grid is laid out at its full size and only *drawn* smaller, so the size
     * it reports when it is laid out is not the size a finger on the screen sees.
     * This is what turns one into the other, and it is 1 the rest of the time.
     */
    var contentScale by mutableFloatStateOf(1f)

    /** The grid as it is actually drawn, which is smaller while the home screen is. */
    val shownGridSize: Size get() = gridSize * contentScale

    /** The dock as it is actually drawn. */
    val shownDockSize: Size get() = dockSize * contentScale

    /**
     * Where the page thumbnails are while a drag is in flight, in root
     * coordinates, so that letting go over one puts the thing being carried on
     * that page.
     *
     * The strip is drawn outside the pulled-back part of the home screen, so
     * these need no adjusting: a finger on the screen is already in the same
     * place they are.
     */
    var dragPages by mutableStateOf<List<Pair<Int, Rect>>>(emptyList())

    /**
     * Where the widget whose handles are showing is being stretched to, while a
     * corner is held. The home screen draws it at that size so the room it is
     * being given can be seen; the widget itself only takes it on when the
     * finger comes up.
     */
    var widgetPreview by mutableStateOf<WidgetPlacement?>(null)

    /** The home page whose thumbnail is under [position], if any. */
    fun dragPageAt(position: Offset): Int? =
        dragPages.firstOrNull { (_, bounds) -> bounds.contains(position) }?.first

    /** True while an icon or a widget is in the air; every pager stands still. */
    val active: Boolean get() = drag != null || widgetDrag != null

    /**
     * True once what is in the air has actually been moved. The difference
     * matters: holding an icon still asks for its actions, and only moving it
     * turns it into a drag that can be dropped somewhere.
     */
    val moved: Boolean get() = drag?.moved == true || widgetDrag?.moved == true

    /**
     * The home cell under [position], if any.
     *
     * Everything here is in root coordinates and so is the geometry the home
     * screen reports: a position read from a layout is already where it is drawn,
     * pull-back and all, so only the size has to be brought down to match.
     */
    fun cellAt(position: Offset): Int? {
        val columns = grid.columns
        val rows = grid.rows
        val shown = shownGridSize
        val cellWidth = if (shown.width > 0f) shown.width / columns else 0f
        val cellHeight = if (shown.height > 0f) shown.height / rows else 0f
        if (cellWidth <= 0f || cellHeight <= 0f) return null

        val x = position.x - gridOrigin.x
        val y = position.y - gridOrigin.y
        if (x < 0f || y < 0f || x >= shown.width || y >= shown.height) return null

        val column = (x / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = (y / cellHeight).toInt().coerceIn(0, rows - 1)
        return homePage * grid.cellCount + row * columns + column
    }

    /**
     * The dock slot under [position], if any. The dock is put away while the
     * home screen is being rearranged, so [enabled] is false then: a drop where
     * the dock used to be must not think it landed in it.
     */
    fun dockSlotAt(position: Offset, enabled: Boolean = true): Int? {
        if (!enabled) return null

        val slots = LauncherSettings.DOCK_SLOTS
        val shown = shownDockSize
        val slotWidth = if (shown.width > 0f) shown.width / slots else 0f
        if (slotWidth <= 0f || shown.height <= 0f) return null

        val x = position.x - dockOrigin.x
        val y = position.y - dockOrigin.y
        if (x < 0f || y < 0f || x >= shown.width || y >= shown.height) return null

        return (x / slotWidth).toInt().coerceIn(0, slots - 1)
    }

    fun start(app: AppInfo, area: DragArea, index: Int, position: Offset) {
        drag = DragState(app, area, index, position)
    }

    fun move(position: Offset) {
        drag = drag?.copy(position = position)
    }

    /** The press has moved far enough to count as a drag. */
    fun markMoved() {
        drag = drag?.copy(moved = true)
        widgetDrag = widgetDrag?.copy(moved = true)
    }

    /** Ends the drag and hands back what was being carried. */
    fun finish(): DragState? {
        val finished = drag
        drag = null
        return finished
    }

    /**
     * Drops the drag if it is the one [app] at [area]/[index] started. Used when
     * a gesture is cut short - the icon being disposed, say - so that the
     * launcher cannot be left with an icon stuck in the air.
     */
    fun cancelIf(app: AppInfo, area: DragArea, index: Int) {
        val current = drag ?: return
        if (current.app.key == app.key && current.area == area && current.index == index) {
            drag = null
        }
    }

    fun startWidget(placement: WidgetPlacement, position: Offset, grab: Offset) {
        widgetDrag = WidgetDragState(placement, position, grab)
    }

    fun moveWidget(position: Offset) {
        widgetDrag = widgetDrag?.copy(position = position)
    }

    /** Ends a widget drag and hands back what was being carried. */
    fun finishWidget(): WidgetDragState? {
        val finished = widgetDrag
        widgetDrag = null
        return finished
    }

    /** Drops a widget drag that [cell] started, if it is the one in flight. */
    fun cancelWidgetIf(cell: Int) {
        if (widgetDrag?.placement?.cell == cell) widgetDrag = null
    }
}

/** [HomeArea] as the drag layer sees it. */
fun HomeArea.asDragArea(): DragArea = if (this == HomeArea.HOME) DragArea.HOME else DragArea.DOCK

/** The two ways an icon that is already on the screen can be got rid of. */
enum class DragTarget { REMOVE, UNINSTALL }

/**
 * The target an icon carried from [area] would be let go over, if either.
 *
 * Only something already pinned somewhere has anything to take away or to
 * uninstall by being dragged: an icon picked up in the drawer is on its way
 * *to* the home screen, and the targets along the top are not for it. That is
 * not merely a nicety - the top of the drawer is exactly where an icon is held
 * to bring the home screen out, so a drawer icon let go up there would otherwise
 * be "removed" (which for something that was never pinned means nothing at all
 * happens) or uninstalled outright.
 */
fun dragTargetFor(
    area: DragArea,
    remove: Rect?,
    uninstall: Rect?,
    position: Offset,
): DragTarget? {
    if (area == DragArea.DRAWER) return null

    return when {
        remove?.contains(position) == true -> DragTarget.REMOVE
        uninstall?.contains(position) == true -> DragTarget.UNINSTALL
        else -> null
    }
}

/**
 * Tap, long press and drag for one icon, wherever it is.
 *
 * The long press is what separates the two kinds of press: letting go without
 * moving asks for the app's actions, and moving hands the icon over to
 * [controller] to be carried.
 *
 * The icon's position is asked for with [originAt] on every event rather than
 * measured once, because the page under the finger can slide away mid-drag.
 */
fun Modifier.iconGesture(
    app: AppInfo,
    area: DragArea,
    index: Int,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    boundsAt: () -> Rect,
): Modifier = pointerInput(app.key, area, index) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)

        if (longPress == null) {
            val up = waitForUpOrCancellation()
            if (up != null) {
                up.consume()
                handlers.onTap(app)
            }
            return@awaitEachGesture
        }

        longPress.consume()
        controller.start(app, area, index, boundsAt().topLeft + longPress.position)

        // The actions are asked for as soon as the press is long enough, the way
        // One UI shows them, rather than when the finger comes up.
        handlers.onLongPress(app, area, index, boundsAt())

        try {
            var traveled = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == longPress.id }

                if (change == null || !change.pressed) {
                    change?.consume()

                    // Held in the same place: the actions were wanted, and they
                    // are already on screen. Moved: it was a drop.
                    controller.finish()?.let { finished ->
                        if (traveled > viewConfiguration.touchSlop) {
                            handlers.onDrop(finished, finished.position)
                        }
                    }
                    break
                }

                val delta = change.position - change.previousPosition
                if (delta.getDistance() > 0f) {
                    traveled += delta.getDistance()
                    if (traveled > viewConfiguration.touchSlop) {
                        controller.markMoved()
                        handlers.onDragStart()
                    }

                    controller.move(boundsAt().topLeft + change.position)
                    change.consume()
                }
            }
        } finally {
            // Covers the case where the gesture is cancelled rather than let
            // go: without this the icon would stay in the air for good.
            controller.cancelIf(app, area, index)
        }
    }
}

/**
 * Long press and drag for a widget on the home screen.
 *
 * A tap is left alone so that the widget itself keeps working: only a long
 * press takes it over, and then it follows the finger from cell to cell.
 *
 * [onDrop] is handed the widget, the top left corner it was let go at, and
 * where the finger was, all in root coordinates.
 */
/**
 * Long-pressing a widget, which has to be done on the way *in*: a widget is a
 * real Android view, and if it is handed the press first it will do whatever
 * its provider asked for - which for most of them means opening the app.
 *
 * Nothing is consumed unless the press turns out to be a long one, so a tap on
 * a widget still does what the widget is for.
 */
fun Modifier.widgetGesture(
    placement: WidgetPlacement,
    controller: IconDragController,
    boundsAt: () -> Rect,
    onSelect: (WidgetPlacement) -> Unit,
    onDragStart: () -> Unit,
    onLongPress: (WidgetPlacement, Rect) -> Unit,
    onDrop: (WidgetPlacement, Offset, Offset) -> Unit,
): Modifier = pointerInput(placement.hostId, placement.cell) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )

        val slop = viewConfiguration.touchSlop

        // Until the press has lasted long enough it is the widget's, and the
        // only thing worth watching for is the finger wandering off - which
        // means the widget is being swiped or scrolled and the press is not
        // ours after all.
        val strayed = withTimeoutOrNull<Boolean>(viewConfiguration.longPressTimeoutMillis) {
            var traveled = 0f
            var wandering = false

            while (!wandering) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }

                if (change == null) {
                    wandering = true
                } else {
                    traveled += (change.position - change.previousPosition).getDistance()
                    if (!change.pressed || traveled > slop) wandering = true
                }
            }

            wandering
        }

        // Nothing came back, so the finger stayed where it was for long enough:
        // the widget is being rearranged rather than used. From here on every
        // event is swallowed, so the widget never sees the press end and never
        // acts on it.
        if (strayed != null) return@awaitEachGesture

        // The finger keeps the grip it had, measured from the widget's corner.
        controller.startWidget(
            placement = placement,
            position = boundsAt().topLeft + down.position,
            grab = down.position,
        )
        onSelect(placement)

        var moved = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()

                if (!change.pressed) {
                    controller.finishWidget()?.let { finished ->
                        if (moved) {
                            onDrop(finished.placement, finished.anchor, finished.position)
                        } else {
                            // Held and let go in the same place: the widget is
                            // asking to be taken away.
                            onLongPress(finished.placement, boundsAt())
                        }
                    }
                    break
                }

                if (!moved && (change.position - down.position).getDistance() > slop) {
                    moved = true
                    controller.markMoved()
                    onDragStart()
                }

                if (moved) controller.moveWidget(boundsAt().topLeft + change.position)
            }
        } finally {
            controller.cancelWidgetIf(placement.cell)
        }
    }
}

/**
 * One icon that can be tapped, long-pressed and carried: the icon and its label
 * plus the gesture, which reports the finger in root coordinates no matter
 * which parent - a page, a lazy grid - the icon happens to sit in.
 */
@Composable
fun DraggableIcon(
    app: AppInfo,
    icon: ImageBitmap?,
    area: DragArea,
    index: Int,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelColor: Color? = null,
    labelShadow: Boolean = false,
) {
    // Where this icon sits, in root coordinates. Kept up to date as its page
    // moves, so the finger stays where the user put it, and so the card that
    // hangs off a long press knows where to go.
    var bounds by remember(app.key, area, index) { mutableStateOf(Rect.Zero) }

    AppIconTileContent(
        app = app,
        icon = icon,
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .iconGesture(
                app = app,
                area = area,
                index = index,
                controller = controller,
                handlers = handlers,
                boundsAt = { bounds },
            ),
        iconSize = iconSize,
        showLabel = showLabel,
        labelColor = labelColor,
        labelShadow = labelShadow,
    )
}

/**
 * The icon that follows the finger while it is being carried.
 *
 * It is drawn by the launcher rather than by a page, so it stays visible when
 * the page it was picked up from slides away. [origin] is where that layer sits
 * in root coordinates.
 */
@Composable
fun DragOverlay(
    drag: DragState,
    icon: ImageBitmap?,
    iconSize: Dp,
    origin: Offset,
) {
    val half = with(LocalDensity.current) { iconSize.toPx() / 2f }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (drag.position.x - origin.x - half).roundToInt(),
                    (drag.position.y - origin.y - half).roundToInt(),
                )
            }
            .alpha(0.9f),
    ) {
        AppIconImage(icon = icon, label = drag.app.label, size = iconSize)
    }
}

/**
 * A widget cannot be lifted out of its view, so while one is being carried a
 * shape the size of it follows the finger instead, marking out the room it
 * needs.
 *
 * It is drawn from the widget's own top left corner, so it sits under the
 * finger exactly as the widget did, and covers the cells that will be marked
 * out for it.
 */
@Composable
fun WidgetGhost(
    drag: WidgetDragState,
    cellSize: Size,
    origin: Offset,
) {
    val density = LocalDensity.current
    if (cellSize.width <= 0f || cellSize.height <= 0f) return

    val width = cellSize.width * drag.placement.spanX
    val height = cellSize.height * drag.placement.spanY
    val outline = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (drag.anchor.x - origin.x).roundToInt(),
                    (drag.anchor.y - origin.y).roundToInt(),
                )
            }
            .size(
                width = with(density) { width.toDp() },
                height = with(density) { height.toDp() },
            )
            .clip(outline)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), outline),
    )
}
