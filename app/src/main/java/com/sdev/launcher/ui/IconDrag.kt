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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sdev.launcher.data.AppInfo
import com.sdev.launcher.data.HomeArea
import com.sdev.launcher.data.WidgetPlacement
import kotlin.math.roundToInt

/** Which part of the launcher an icon was picked up from. */
enum class DragArea { DRAWER, HOME, DOCK, FOLDER }

/**
 * What is being carried: an app, or a folder of apps.
 *
 * A folder is carried the way an app is - it can be picked up and dropped
 * somewhere else - so it is part of what a drag holds rather than something
 * the drag has to know about separately.
 */
sealed interface Dragged {

    /** What tells this thing apart from every other one. */
    val key: String

    /** An app: from the drawer, a home cell, the dock, or a folder. */
    data class App(val app: AppInfo) : Dragged {
        override val key: String get() = app.key
    }

    /** A folder, carried from the home screen. */
    data class Folder(val folderId: String) : Dragged {
        override val key: String get() = folderId
    }
}

/** An icon being carried: where it came from and where the finger is. */
data class DragState(
    val dragged: Dragged,
    val area: DragArea,
    /** Home cell, dock slot, or place in a folder; unused for the drawer. */
    val index: Int,
    /** Finger position in root coordinates. */
    val position: Offset,
    /**
     * True once the press has moved far enough to be a drag rather than a long
     * press. A long press on its own only asks for the app's actions.
     */
    val moved: Boolean = false,
    /**
     * The folder the app came out of, when it came out of one.
     *
     * It is not in [dragged] because the app itself is the thing being carried:
     * what has to be remembered is where it has to go back to.
     */
    val folderId: String? = null,
) {
    /** The app being carried, when it is an app. */
    val app: AppInfo? get() = (dragged as? Dragged.App)?.app
}

/** What an icon does when it is tapped, long-pressed without moving, or dropped. */
class IconGestureHandlers(
    /**
     * Tapped. [DragArea] says where the icon was, which matters when the same
     * gesture means different things in different places: a tap on an app
     * inside a folder opens the app, and a tap on the home screen behind the
     * folder puts the folder away.
     */
    val onTap: (Dragged, DragArea) -> Unit,
    /**
     * Long-pressed: the actions for this icon are wanted. [Rect] is where the
     * icon is, so the card that offers them can hang off it.
     */
    val onLongPress: (Dragged, DragArea, Int, Rect) -> Unit,
    /** The press has started to move, so it is a drag and not a request. */
    val onDragStart: () -> Unit,
    val onDrop: (DragState, Offset) -> Unit,
)

/**
 * A widget in the air.
 *
 * It is either one that is already on the home screen - [from] says where - or
 * one being brought in from the widget page, which has no place yet: until it is
 * let go, all that is known is the room it needs and the entry it came from.
 */
data class WidgetDragState(
    /** Where the widget sits, when it was picked up off the home screen. */
    val from: WidgetPlacement? = null,
    /** The picker entry it came from, when it is being brought in. */
    val choice: WidgetChoice? = null,
    /** How many cells of the grid it needs. */
    val spanX: Int,
    val spanY: Int,
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
 * What to do with a widget that has been let go: the widget that was in the air,
 * the corner it was being carried by, and where the finger was, all in root
 * coordinates.
 */
typealias WidgetDropHandler = (WidgetDragState, Offset, Offset) -> Unit

/**
 * The one drag that is in flight.
 *
 * This lives above the pages rather than inside the home screen because a drag
 * has to survive the pages moving underneath it: an icon picked up in the
 * drawer is still being carried when the drawer slides away to reveal home.
 *
 * It deliberately holds no geometry. Where a drag would land is a question about
 * the home screen as it is laid out at this instant, and the home screen answers
 * it - see [HomeDropArea]. Anything cached here would be right until the page
 * moved, and the page moving is the whole problem.
 */
@Stable
class IconDragController {

    var drag by mutableStateOf<DragState?>(null)
        private set

    var widgetDrag by mutableStateOf<WidgetDragState?>(null)
        private set

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

    fun start(
        dragged: Dragged,
        area: DragArea,
        index: Int,
        position: Offset,
        folderId: String? = null,
    ) {
        drag = DragState(dragged, area, index, position, folderId = folderId)
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
     * Drops the drag if it is the one [dragged] at [area]/[index] started. Used
     * when a gesture is cut short - the icon being disposed, say - so that the
     * launcher cannot be left with an icon stuck in the air.
     */
    fun cancelIf(dragged: Dragged, area: DragArea, index: Int) {
        val current = drag ?: return
        if (current.dragged.key == dragged.key && current.area == area && current.index == index) {
            drag = null
        }
    }

    /** Picks up a widget that is already on the home screen. */
    fun startWidget(placement: WidgetPlacement, position: Offset, grab: Offset) {
        widgetDrag = WidgetDragState(
            from = placement,
            spanX = placement.spanX,
            spanY = placement.spanY,
            position = position,
            grab = grab,
        )
    }

    /**
     * Picks up a widget out of the widget page, which has no place on the grid
     * yet: the room it needs comes from the entry it was taken from, and where
     * it lands is not settled until it is let go.
     */
    fun startPendingWidget(choice: WidgetChoice, position: Offset, grab: Offset) {
        widgetDrag = WidgetDragState(
            choice = choice,
            spanX = choice.spanX,
            spanY = choice.spanY,
            position = position,
            grab = grab,
        )
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
        if (widgetDrag?.from?.cell == cell) widgetDrag = null
    }

    /** Drops a widget drag that the picker entry [key] started, if it is the one in flight. */
    fun cancelPendingWidgetIf(key: String) {
        if (widgetDrag?.choice?.key == key) widgetDrag = null
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
 * Where a place [local] within this layout is on the screen, as the layout is
 * *now*.
 *
 * This is the live conversion, not a rect measured earlier: the parent pages
 * slide about while something is being carried, and a position measured once
 * would stay behind when they do. [localToRoot] walks the transforms at the
 * moment it is asked, so a page that moved is accounted for even when no
 * `onGloballyPositioned` ran in between.
 */
internal fun LayoutCoordinates?.rootOf(local: Offset): Offset? =
    this?.takeIf { it.isAttached }?.localToRoot(local)

/**
 * Tap, long press and drag for one icon, wherever it is.
 *
 * The long press is what separates the two kinds of press: letting go without
 * moving asks for the icon's actions, and moving hands the icon over to
 * [controller] to be carried.
 *
 * The finger is turned into a place on the screen through [coordinatesAt] on
 * every event, so the position stays under the finger when the page it was
 * picked up from slides away underneath it. [boundsAt] is only for the actions
 * card, which sits where the icon is at the moment of the long press.
 *
 * [folderId] is the folder the icon came out of, when it came out of one: the
 * app has to go back to it if the drop does not work out.
 */
fun Modifier.iconGesture(
    dragged: Dragged,
    area: DragArea,
    index: Int,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    coordinatesAt: () -> LayoutCoordinates?,
    boundsAt: () -> Rect,
    folderId: String? = null,
): Modifier = pointerInput(dragged.key, area, index, folderId) {
    // The fallback keeps an icon that has just been detached (and so cannot be
    // converted) from being dropped somewhere arbitrary.
    fun at(local: Offset): Offset =
        coordinatesAt().rootOf(local) ?: (boundsAt().topLeft + local)

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)

        if (longPress == null) {
            val up = waitForUpOrCancellation()
            if (up != null) {
                up.consume()
                handlers.onTap(dragged, area)
            }
            return@awaitEachGesture
        }

        longPress.consume()
        controller.start(dragged, area, index, at(longPress.position), folderId)

        // The actions are asked for as soon as the press is long enough, the way
        // One UI shows them, rather than when the finger comes up.
        handlers.onLongPress(dragged, area, index, boundsAt())

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

                    controller.move(at(change.position))
                    change.consume()
                }
            }
        } finally {
            // Covers the case where the gesture is cancelled rather than let
            // go: without this the icon would stay in the air for good.
            controller.cancelIf(dragged, area, index)
        }
    }
}

/**
 * Long press and drag for a widget on the home screen.
 *
 * A tap is left alone so that the widget itself keeps working: only a long
 * press takes it over, and then it follows the finger from cell to cell.
 *
 * The press has to be taken on the way *in*: a widget is a real Android view,
 * and if it is handed the press first it will do whatever its provider asked
 * for - which for most of them means opening the app. Nothing is consumed
 * unless the press turns out to be a long one, so a tap still does what the
 * widget is for.
 *
 * [onDrop] is handed the widget, the top left corner it was let go at, and
 * where the finger was, all in root coordinates.
 */
fun Modifier.widgetGesture(
    placement: WidgetPlacement,
    controller: IconDragController,
    coordinatesAt: () -> LayoutCoordinates?,
    boundsAt: () -> Rect,
    onSelect: (WidgetPlacement) -> Unit,
    onDragStart: () -> Unit,
    onLongPress: (WidgetPlacement, Rect) -> Unit,
    onDrop: WidgetDropHandler,
): Modifier = pointerInput(placement.hostId, placement.cell) {
    fun at(local: Offset): Offset =
        coordinatesAt().rootOf(local) ?: (boundsAt().topLeft + local)

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
        // Both are read in the same space at the same moment, so the grip stays
        // a vector even when the home screen is drawn pulled back.
        val atDown = at(down.position)
        // A widget only moves while the screen is at its normal size, so a rect
        // measured earlier is the same corner when the conversion is not
        // available.
        val corner = coordinatesAt().rootOf(Offset.Zero) ?: boundsAt().topLeft
        controller.startWidget(
            placement = placement,
            position = atDown,
            grab = atDown - corner,
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
                            onDrop(finished, finished.anchor, finished.position)
                        } else {
                            // Held and let go in the same place: the widget is
                            // asking to be taken away.
                            finished.from?.let { onLongPress(it, boundsAt()) }
                        }
                    }
                    break
                }

                if (!moved && (change.position - down.position).getDistance() > slop) {
                    moved = true
                    controller.markMoved()
                    onDragStart()
                }

                if (moved) controller.moveWidget(at(change.position))
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
    badgeCount: Int = 0,
    folderId: String? = null,
) {
    // The icon's place, as a layout to convert through rather than as a rect
    // copied out of one: the page it sits on slides away under the finger
    // during a drag, and only the live conversion follows it. The rect is kept
    // as well, because the actions card is put up where the icon is at the
    // moment of the long press.
    var coordinates by remember(app.key, area, index, folderId) {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    var bounds by remember(app.key, area, index, folderId) { mutableStateOf(Rect.Zero) }

    AppIconTileContent(
        app = app,
        icon = icon,
        modifier = modifier
            .onGloballyPositioned {
                coordinates = it
                bounds = it.boundsInRoot()
            }
            .iconGesture(
                dragged = Dragged.App(app),
                area = area,
                index = index,
                controller = controller,
                handlers = handlers,
                coordinatesAt = { coordinates },
                boundsAt = { bounds },
                folderId = folderId,
            ),
        iconSize = iconSize,
        showLabel = showLabel,
        labelColor = labelColor,
        labelShadow = labelShadow,
        badgeCount = badgeCount,
    )
}

/**
 * The icon that follows the finger while it is being carried.
 *
 * It is drawn by the launcher rather than by a page, so it stays visible when
 * the page it was picked up from slides away. [origin] is where that layer sits
 * in root coordinates.
 *
 * [content] draws the thing being carried when it is not an app: a folder is
 * carried as the folder it is, not as one of the apps in it.
 */
@Composable
fun DragOverlay(
    drag: DragState,
    icon: ImageBitmap?,
    iconSize: Dp,
    origin: Offset,
    content: (@Composable () -> Unit)? = null,
) {
    val half = with(LocalDensity.current) { iconSize.toPx() / 2f }
    val app = drag.app

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
        if (content != null) {
            content()
        } else if (app != null) {
            AppIconImage(icon = icon, label = app.label, size = iconSize)
        }
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

    val width = cellSize.width * drag.spanX
    val height = cellSize.height * drag.spanY
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
