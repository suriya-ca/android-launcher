package com.sdev.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.sdev.launcher.data.ResizeEdge
import com.sdev.launcher.data.WidgetPlacement
import com.sdev.launcher.data.resized
import kotlin.math.roundToInt

/** How big a handle is drawn, and how far around it a finger counts. */
private val HANDLE_DOT = 18.dp

/**
 * How far around a handle a press is taken as being on it.
 *
 * This is generous on purpose: a handle at the middle of the edge of a widget
 * that reaches the side of the screen is nearly out of reach otherwise, and a
 * handle that cannot be caught is the same as no handle at all.
 */
private val HANDLE_TOUCH = 56.dp

/**
 * How far in from the side of the screen a handle is kept, so that the whole dot
 * stays on it even when the widget reaches right to the edge.
 */
private val HANDLE_MARGIN = 6.dp

private val EDGES = ResizeEdge.entries

/**
 * The handles a widget wears while it is selected, and everything that can be
 * done from them: stretched by an edge, carried by its middle, or put away with
 * a tap.
 *
 * One handle sits at the middle of each edge rather than at the corners: a
 * corner on a widget that reaches the side of the screen is half off it, and a
 * handle that sits between two directions is unclear about which way a drag will
 * go. Each one here changes one dimension only.
 *
 * This is drawn above the home screen rather than inside it, and it is the whole
 * screen rather than the widget, for two reasons. A handle on the edge of the
 * screen would otherwise be clipped by the page the widget sits in, and hidden
 * behind the dim that says it is selected. And a finger has to be measured in a
 * space that does not move: a handle that resized its own container would be
 * dragging the ground out from under itself, which is how a corner drag ends up
 * going nowhere.
 *
 * While a widget is selected this swallows every touch, so the widget's own
 * content cannot be used by accident. A tap anywhere else in the launcher puts
 * the selection away, which is also what the dim behind is for.
 */
@Composable
fun WidgetResizeOverlay(
    placement: WidgetPlacement,
    controller: IconDragController,
    area: HomeDropArea,
    preview: WidgetPlacement?,
    fits: (Int, Int, Int) -> Boolean,
    onPreview: (WidgetPlacement?) -> Unit,
    onResize: (Int, Int, Int) -> Unit,
    onMoveStart: () -> Unit,
    onDrop: WidgetDropHandler,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridOrigin = area.gridOrigin
    // The grid is drawn smaller than it is laid out while the home screen is
    // being rearranged, and these handles sit on the screen, so it is the drawn
    // size that matters here.
    val gridSize = area.gridSize
    val density = LocalDensity.current

    val columns = area.shape.columns
    val rows = area.shape.rows
    val cellWidth = if (columns > 0) gridSize.width / columns else 0f
    val cellHeight = if (rows > 0) gridSize.height / rows else 0f

    /** Where a placement sits on the screen, in root coordinates. */
    fun rectOf(placed: WidgetPlacement): Rect {
        val within = area.shape.cellOf(placed.cell)
        val column = within % columns
        val row = within / columns
        return Rect(
            offset = gridOrigin + Offset(column * cellWidth, row * cellHeight),
            size = Size(cellWidth * placed.spanX, cellHeight * placed.spanY),
        )
    }

    // The rectangle the handles are drawn at, and the one a new press is
    // measured against: while an edge is being dragged the widget is drawn at the
    // size it is being stretched to, but the handles were caught where the press
    // began.
    val shownRect = rectOf(preview ?: placement)

    // The overlay covers the screen and never moves, so these stay put: they are
    // what turns a finger's position inside it into a place on the screen.
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(Size.Zero) }

    /**
     * Where the dot at the middle of [edge] sits.
     *
     * Kept a little way in from the side of the screen, so that a widget reaching
     * the edge still has a handle that is not half off it - which is how a
     * handle comes to be grabbable on one side only.
     */
    fun handleAt(edge: ResizeEdge, rect: Rect): Offset {
        val margin = with(density) { HANDLE_DOT.toPx() / 2f + HANDLE_MARGIN.toPx() }
        val x = when (edge) {
            ResizeEdge.LEFT -> rect.left
            ResizeEdge.RIGHT -> rect.right
            else -> rect.center.x
        }
        val y = when (edge) {
            ResizeEdge.TOP -> rect.top
            ResizeEdge.BOTTOM -> rect.bottom
            else -> rect.center.y
        }
        val lowX = overlayOrigin.x + margin
        val highX = overlayOrigin.x + overlaySize.width - margin
        val lowY = overlayOrigin.y + margin
        val highY = overlayOrigin.y + overlaySize.height - margin

        return Offset(
            x = if (lowX <= highX) x.coerceIn(lowX, highX) else x,
            y = if (lowY <= highY) y.coerceIn(lowY, highY) else y,
        )
    }

    // Once the widget has taken the size it was stretched to, the preview has
    // nothing left to say.
    LaunchedEffect(placement.cell, placement.spanX, placement.spanY) {
        val pending = preview
        if (pending != null &&
            pending.cell == placement.cell &&
            pending.spanX == placement.spanX &&
            pending.spanY == placement.spanY
        ) {
            onPreview(null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayOrigin = it.positionInRoot()
                overlaySize = it.size.toSize()
            }
            .pointerInput(placement.hostId, placement.cell, placement.spanX, placement.spanY) {
                val touch = HANDLE_TOUCH.toPx() / 2f
                val place = placement

                awaitEachGesture {
                    val down = awaitFirstDown()
                    val finger = overlayOrigin + down.position

                    // Measured against where the handles are now, which is the
                    // widget as it stands when the press begins.
                    val startRect = rectOf(place)
                    val edge = EDGES.firstOrNull { candidate ->
                        (finger - handleAt(candidate, startRect)).getDistance() <= touch
                    }

                    if (edge == null) {
                        // The widget itself, or the empty launcher around it: a
                        // press that stays put puts the selection away, and one
                        // that moves carries the widget.
                        var carried = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val at = overlayOrigin + change.position

                            if (!carried) {
                                if ((at - finger).getDistance() > viewConfiguration.touchSlop) {
                                    carried = true
                                    onMoveStart()
                                    controller.startWidget(
                                        placement = place,
                                        position = at,
                                        grab = finger - startRect.topLeft,
                                    )
                                    controller.markMoved()
                                }
                            } else {
                                change.consume()
                                controller.moveWidget(at)
                            }
                        }

                        if (carried) {
                            controller.finishWidget()?.let { finished ->
                                onDrop(finished, finished.anchor, finished.position)
                            }
                        } else {
                            onDismiss()
                        }

                        return@awaitEachGesture
                    }

                    if (cellWidth <= 0f || cellHeight <= 0f) return@awaitEachGesture

                    // An edge: stretch the widget along that edge's own axis,
                    // counting whole cells from where the press began.
                    var target: WidgetPlacement? = null

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break

                        val at = overlayOrigin + change.position
                        val across = ((at.x - finger.x) / cellWidth).roundToInt()
                        val downCells = ((at.y - finger.y) / cellHeight).roundToInt()

                        target = place.resized(area.shape, edge, across, downCells)
                        onPreview(target)
                    }

                    val grown = target
                    when {
                        grown == null || grown == place -> onPreview(null)

                        // The preview stays up until the widget itself has grown,
                        // so it does not flash back to the old size first.
                        fits(grown.cell, grown.spanX, grown.spanY) ->
                            onResize(grown.cell, grown.spanX, grown.spanY)

                        else -> onPreview(null)
                    }
                }
            },
    ) {
        val moving = controller.widgetDrag != null

        if (!moving && cellWidth > 0f && cellHeight > 0f) {
            val tint = if (preview == null || fits(preview.cell, preview.spanX, preview.spanY)) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

            val borderLeft = (shownRect.left - overlayOrigin.x).roundToInt()
            val borderTop = (shownRect.top - overlayOrigin.y).roundToInt()
            val half = with(density) { HANDLE_TOUCH.toPx() / 2f }

            Box(
                modifier = Modifier
                    .offset { IntOffset(borderLeft, borderTop) }
                    .size(
                        width = with(density) { shownRect.width.toDp() },
                        height = with(density) { shownRect.height.toDp() },
                    )
                    .border(2.dp, tint, RoundedCornerShape(18.dp)),
            )

            EDGES.forEach { edge ->
                val at = handleAt(edge, shownRect)

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (at.x - overlayOrigin.x - half).roundToInt(),
                                (at.y - overlayOrigin.y - half).roundToInt(),
                            )
                        }
                        .size(HANDLE_TOUCH),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(HANDLE_DOT)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, tint, CircleShape),
                    )
                }
            }
        }
    }
}
