package com.sdev.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * The shape of One UI: the rounded card that hangs off whatever was long
 * pressed, and the small line glyphs that go in it.
 *
 * The glyphs are drawn rather than taken from an icon font: the launcher needs
 * eight of them, and drawing them keeps the app free of a dependency that would
 * add more code than the whole launcher.
 */

/** How far the card sits from the thing it belongs to. */
private val POPUP_GAP = 10.dp

/** Room left inside the popup window for the card's shadow to fall into. */
private val POPUP_SHADOW_ROOM = 10.dp

/** The corner of a One UI card. */
private val CARD_CORNER = 26.dp

/** A line glyph One UI would use for one of the actions. */
enum class Glyph {
    REMOVE,
    ADD,
    INFO,
    UNINSTALL,
    HIDE,
    HOME,
    SETTINGS,
    WALLPAPER,
    WIDGETS,
    CLOSE,
    EDIT,
    ARROW_LEFT,
    ARROW_RIGHT,
}

/** The dim behind a card, which also swallows the taps that dismiss it. */
@Composable
fun OneUiScrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    )
}

/**
 * A One UI card: a rounded panel with a soft shadow, hung above [anchor] - the
 * bounds of whatever was pressed, in root coordinates - and pushed below it
 * instead when there is not enough room up there.
 *
 * [content] is the rows, which are [OneUiAction]s.
 */
@Composable
fun OneUiPopup(
    anchor: Rect,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val gap = with(density) { POPUP_GAP.roundToPx() }
    val room = with(density) { POPUP_SHADOW_ROOM.roundToPx() }

    // The card grows out of nothing when it arrives, the way a One UI popup
    // does, so a new card does not simply appear.
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    val growth by animateFloatAsState(
        targetValue = if (arrived) 1f else 0.86f,
        animationSpec = tween(durationMillis = 150),
        label = "oneui-popup-growth",
    )

    Popup(
        popupPositionProvider = remember(anchor, gap, room) {
            AnchorPositionProvider(anchor, gap, room)
        },
        onDismissRequest = onDismiss,
        // Not focusable on purpose: the card has to be able to appear while the
        // finger that asked for it is still down, and a focusable popup would
        // take the rest of that gesture away from the icon underneath.
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            modifier = modifier
                .widthIn(min = 196.dp, max = 300.dp)
                .padding(POPUP_SHADOW_ROOM)
                .graphicsLayer {
                    scaleX = growth
                    scaleY = growth
                    alpha = growth
                },
            shape = RoundedCornerShape(CARD_CORNER),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

/** One row of a One UI card: a glyph, then the name of the action. */
@Composable
fun OneUiAction(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(glyph = glyph, tint = tint, size = 18.dp)
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Draws [glyph] in [tint], inside a square of [size]. */
@Composable
fun Glyph(glyph: Glyph, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        drawGlyph(glyph, tint)
    }
}

/**
 * The glyphs themselves, drawn against the middle of the square and scaled off
 * its shortest side, so they fit whatever size they are asked for.
 */
private fun DrawScope.drawGlyph(glyph: Glyph, tint: Color) {
    val side = size.minDimension
    val centre = Offset(size.width / 2f, size.height / 2f)
    val line = side * 0.1f
    val round = Stroke(width = line, cap = StrokeCap.Round)

    fun bar(fromX: Float, fromY: Float, toX: Float, toY: Float) = drawLine(
        color = tint,
        start = Offset(centre.x + fromX * side, centre.y + fromY * side),
        end = Offset(centre.x + toX * side, centre.y + toY * side),
        strokeWidth = line,
        cap = StrokeCap.Round,
    )

    when (glyph) {
        Glyph.REMOVE -> bar(-0.28f, 0f, 0.28f, 0f)

        Glyph.ADD -> {
            bar(-0.28f, 0f, 0.28f, 0f)
            bar(0f, -0.28f, 0f, 0.28f)
        }

        Glyph.INFO -> {
            drawCircle(
                color = tint,
                radius = side * 0.36f,
                center = centre,
                style = Stroke(width = line * 0.9f),
            )
            drawCircle(color = tint, radius = side * 0.055f, center = Offset(centre.x, centre.y - side * 0.17f))
            bar(0f, -0.04f, 0f, 0.2f)
        }

        Glyph.UNINSTALL -> {
            // Lid, then the can hanging under it, then the ribs.
            bar(-0.3f, -0.2f, 0.3f, -0.2f)
            bar(-0.11f, -0.32f, 0.11f, -0.32f)
            bar(-0.23f, -0.14f, -0.19f, 0.3f)
            bar(0.23f, -0.14f, 0.19f, 0.3f)
            bar(-0.19f, 0.3f, 0.19f, 0.3f)
            bar(-0.07f, -0.05f, -0.07f, 0.18f)
            bar(0.07f, -0.05f, 0.07f, 0.18f)
        }

        Glyph.HIDE -> {
            // A closed eye with a line struck through it.
            val width = side * 0.76f
            val height = side * 0.44f
            drawOval(
                color = tint,
                topLeft = Offset(centre.x - width / 2f, centre.y - height / 2f),
                size = Size(width, height),
                style = Stroke(width = line * 0.85f),
            )
            drawCircle(color = tint, radius = side * 0.09f, center = centre)
            bar(-0.32f, 0.28f, 0.32f, -0.28f)
        }

        Glyph.HOME -> {
            // A roof, two walls and a floor.
            bar(0f, -0.34f, -0.32f, -0.02f)
            bar(0f, -0.34f, 0.32f, -0.02f)
            bar(-0.32f, -0.02f, -0.28f, 0.3f)
            bar(0.32f, -0.02f, 0.28f, 0.3f)
            bar(-0.28f, 0.3f, 0.28f, 0.3f)
        }

        Glyph.SETTINGS -> {
            // A ring with eight teeth around it.
            repeat(8) { index ->
                rotate(degrees = index * 45f, pivot = centre) {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(centre.x - side * 0.05f, centre.y - side * 0.5f),
                        size = Size(side * 0.1f, side * 0.18f),
                        cornerRadius = CornerRadius(side * 0.05f),
                    )
                }
            }
            drawCircle(
                color = tint,
                radius = side * 0.27f,
                center = centre,
                style = Stroke(width = line),
            )
        }

        Glyph.WALLPAPER -> {
            // A picture: a frame, a sun, and a hill in the corner.
            val width = side * 0.78f
            val height = side * 0.6f
            val topLeft = Offset(centre.x - width / 2f, centre.y - height / 2f)
            drawRoundRect(
                color = tint,
                topLeft = topLeft,
                size = Size(width, height),
                cornerRadius = CornerRadius(side * 0.12f),
                style = Stroke(width = line * 0.85f),
            )
            drawCircle(
                color = tint,
                radius = side * 0.07f,
                center = Offset(topLeft.x + width * 0.28f, topLeft.y + height * 0.3f),
            )
            val hill = Path().apply {
                moveTo(topLeft.x + width * 0.06f, topLeft.y + height * 0.9f)
                lineTo(topLeft.x + width * 0.44f, topLeft.y + height * 0.42f)
                lineTo(topLeft.x + width * 0.94f, topLeft.y + height * 0.9f)
            }
            drawPath(path = hill, color = tint, style = Stroke(width = line * 0.85f))
        }

        Glyph.WIDGETS -> {
            // Four tiles: the shape of a widget on a grid.
            val gap = side * 0.08f
            val tile = side * 0.3f
            val offset = tile / 2f + gap / 2f
            listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1).forEach { (x, y) ->
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(
                        centre.x + x * offset - tile / 2f,
                        centre.y + y * offset - tile / 2f,
                    ),
                    size = Size(tile, tile),
                    cornerRadius = CornerRadius(side * 0.08f),
                    style = Stroke(width = line * 0.85f),
                )
            }
        }

        Glyph.CLOSE -> {
            bar(-0.26f, -0.26f, 0.26f, 0.26f)
            bar(0.26f, -0.26f, -0.26f, 0.26f)
        }

        Glyph.EDIT -> {
            // A pencil leaning to the right.
            drawLine(
                color = tint,
                start = Offset(centre.x - side * 0.24f, centre.y + side * 0.26f),
                end = Offset(centre.x + side * 0.26f, centre.y - side * 0.24f),
                strokeWidth = line,
                cap = StrokeCap.Round,
            )
            bar(-0.3f, 0.34f, -0.16f, 0.2f)
        }

        Glyph.ARROW_LEFT -> {
            bar(0.16f, -0.26f, -0.24f, 0f)
            bar(-0.24f, 0f, 0.16f, 0.26f)
        }

        Glyph.ARROW_RIGHT -> {
            bar(-0.16f, -0.26f, 0.24f, 0f)
            bar(0.24f, 0f, -0.16f, 0.26f)
        }
    }
}

/**
 * Puts the card above the thing it belongs to, centred on it, and below it when
 * it would run off the top of the screen. Either way it is kept inside the
 * window, so a card for an icon in a corner still shows in full.
 */
private class AnchorPositionProvider(
    private val anchor: Rect,
    private val gap: Int,
    private val room: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // The window is bigger than the card by the shadow's room, which the
        // card is inset by, so the measurements have to lose it first.
        val cardWidth = popupContentSize.width - room * 2
        val cardHeight = popupContentSize.height - room * 2

        val above = anchor.top.roundToInt() - cardHeight - gap - room
        val below = anchor.bottom.roundToInt() + gap + room

        val x = (anchor.left + anchor.width / 2f - cardWidth / 2f).roundToInt()
        val maxX = (windowSize.width - cardWidth - room).coerceAtLeast(room)
        val maxY = (windowSize.height - cardHeight - room).coerceAtLeast(room)

        return IntOffset(
            x.coerceIn(room, maxX),
            (if (above >= room) above else below).coerceIn(room, maxY),
        )
    }
}
