package com.sdev.launcher.ui

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sdev.launcher.R
import kotlinx.coroutines.withTimeoutOrNull

/** How tall the live widget inside a preview card is. */
private val PREVIEW_TILE_HEIGHT = 116.dp

/** The corner of a preview card. */
private val PREVIEW_CARD_CORNER = 22.dp

/**
 * The widgets that can be added to the home screen, grouped by the app that
 * provides them and drawn as they really are.
 *
 * A preview is the widget itself and not a picture of it: each one is bound to an
 * id of its own while the page is showing, drawn at the size it asks for, and
 * scaled down to fit its card. That is why the previews cost what they cost, and
 * why the ids are handed back when the page goes - all but the widget that is
 * taken, which is bound from scratch for the home screen.
 *
 * A widget the system will not hand over is offered by name instead: taking it
 * puts the system's own question to the user, as it always did.
 *
 * A card can be tapped to send its widget to the first place it fits, or held and
 * carried up to the home screen to be put exactly where it is let go.
 */
@Composable
fun WidgetPage(
    groups: List<WidgetGroup>,
    host: WidgetHost,
    controller: IconDragController,
    /**
     * True while the page is the one being looked at, which is what makes a
     * visit a visit: the search is emptied and the cards are drawn afresh.
     */
    active: Boolean,
    onPick: (WidgetChoice) -> Unit,
    onDragStart: () -> Unit,
    onDrop: WidgetDropHandler,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    // Which visit to the page this is. Every visit draws its cards afresh, which
    // is what makes the previews work: the ids a visit binds are handed back when
    // the page is left, but the page itself stays composed - it has to, or a
    // widget being carried off it would have its gesture torn out from under it
    // as the pager moved away - so without this the cards would come back holding
    // ids the host has already let go of, and draw widgets that are no longer
    // listening.
    var visit by remember { mutableStateOf(0) }

    LaunchedEffect(active) {
        if (active) {
            // A fresh visit starts from the whole list, the way a fresh visit to
            // the drawer starts from the whole list of apps.
            query = ""
            visit++
        }
    }

    val shown = filterWidgetGroups(groups, query)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            WidgetPageHeader(onDismiss = onDismiss)

            LauncherSearchField(
                query = query,
                onQueryChange = { query = it },
                hint = R.string.search_widgets_hint,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    groups.isEmpty() -> WidgetPageMessage(R.string.widget_picker_empty)

                    shown.isEmpty() -> WidgetPageMessage(R.string.widget_no_results)

                    else -> key(visit) {
                        WidgetGrid(
                            groups = shown,
                            host = host,
                            controller = controller,
                            onPick = onPick,
                            onDragStart = onDragStart,
                            onDrop = onDrop,
                        )
                    }
                }
            }
        }
    }
}

/** The page's own title bar, with the way back out of it. */
@Composable
private fun WidgetPageHeader(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.widget_picker_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.onSurface,
            )

            Text(
                text = stringResource(R.string.widget_page_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    onClick = onDismiss,
                    onClickLabel = stringResource(R.string.close),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(glyph = Glyph.CLOSE, tint = colors.onSurfaceVariant, size = 18.dp)
        }
    }
}

/**
 * The groups that match [query], with the widgets that do not match taken out of
 * them. A group left with nothing to show is dropped, heading and all.
 *
 * A group also matches on the name of the app that provides it, so that searching
 * for an app finds its widgets even when none of them is called after it - which
 * is the way most of them are: "Clock" is the app, "Analog clock" and "Digital
 * clock" are its widgets.
 */
internal fun filterWidgetGroups(groups: List<WidgetGroup>, query: String): List<WidgetGroup> {
    val wanted = query.trim()
    if (wanted.isEmpty()) return groups

    return groups.mapNotNull { group ->
        if (group.label.contains(wanted, ignoreCase = true)) {
            group
        } else {
            val choices = group.choices.filter {
                it.label.contains(wanted, ignoreCase = true)
            }

            if (choices.isEmpty()) null else group.copy(choices = choices)
        }
    }
}

/** The widget cards, under the heading of the app each one comes from. */
@Composable
private fun WidgetGrid(
    groups: List<WidgetGroup>,
    host: WidgetHost,
    controller: IconDragController,
    onPick: (WidgetChoice) -> Unit,
    onDragStart: () -> Unit,
    onDrop: WidgetDropHandler,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // A widget in the air must not also scroll the list it was taken from.
        userScrollEnabled = !controller.active,
    ) {
        groups.forEach { group ->
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "app:${group.packageName}",
            ) {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }

            items(items = group.choices, key = { it.key }) { choice ->
                WidgetPreviewCard(
                    choice = choice,
                    host = host,
                    controller = controller,
                    onPick = onPick,
                    onDragStart = onDragStart,
                    onDrop = onDrop,
                )
            }
        }
    }
}

/**
 * One widget: a live preview of it, its name, and how much of the grid it takes.
 *
 * The preview is bound when its card first appears and kept for as long as the
 * page is showing, so coming back to it costs nothing. Scrolling away does not
 * release it either: a widget let go of and asked for again - which is what
 * releasing and re-binding would be - is a widget that could come back looking
 * different.
 */
@Composable
private fun WidgetPreviewCard(
    choice: WidgetChoice,
    host: WidgetHost,
    controller: IconDragController,
    onPick: (WidgetChoice) -> Unit,
    onDragStart: () -> Unit,
    onDrop: WidgetDropHandler,
) {
    var id by remember(choice.key) { mutableStateOf<Int?>(null) }
    var coordinates by remember(choice.key) {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    var bounds by remember(choice.key) { mutableStateOf(Rect.Zero) }

    LaunchedEffect(choice.key) {
        id = host.previewId(choice.key, choice.provider, choice.spanX, choice.spanY)
    }

    // Asked for once per id rather than on every redraw: the host hands back the
    // view it made, taking it out of wherever it was, so asking again would take
    // it out of the card it is being shown in.
    val view = remember(id, choice.key) {
        id?.let { host.viewOf(it, choice.provider) }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(PREVIEW_CARD_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .onGloballyPositioned {
                coordinates = it
                bounds = it.boundsInRoot()
            }
            .widgetCardGesture(
                choice = choice,
                controller = controller,
                coordinatesAt = { coordinates },
                boundsAt = { bounds },
                onTap = onPick,
                onDragStart = onDragStart,
                onDrop = onDrop,
            ),
    ) {
        WidgetPreview(view = view, choice = choice, host = host)

        Text(
            text = choice.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
        )

        Text(
            text = "${choice.spanX} × ${choice.spanY}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
        )
    }
}

/**
 * The widget itself, filling its card.
 *
 * It is laid out at the size it asks for and scaled down to the card, rather
 * than being laid out at the size of the card: a provider is told what room it
 * has and lays itself out for it, and one told it has a card's worth of room
 * would draw the cramped version of itself - or hide what does not fit and show
 * the user a preview of a widget that does not exist.
 */
@Composable
private fun WidgetPreview(view: AppWidgetHostView?, choice: WidgetChoice, host: WidgetHost) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_TILE_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        if (view == null) {
            // The system would not hand this one over, so there is nothing to
            // draw: it is offered by name and asked for on the way in.
            Glyph(
                glyph = Glyph.WIDGETS,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 30.dp,
            )
        } else {
            val (naturalWidth, naturalHeight) = host.previewSizeDp(
                info = choice.provider,
                spanX = choice.spanX,
                spanY = choice.spanY,
            )

            val scale = minOf(
                maxWidth.value / naturalWidth,
                maxHeight.value / naturalHeight,
                1f,
            )

            AndroidView(
                modifier = Modifier
                    .requiredSize(naturalWidth.dp, naturalHeight.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    },
                factory = { view },
            )
        }
    }
}

/** What a press on a preview card turned out to be. */
private enum class CardPress { TAP, STRAYED }

/**
 * Tapping a preview card sends its widget to the home screen, and holding one
 * picks it up to be carried there instead.
 *
 * The press has to be read on the way *in*, and this is the same bargain the home
 * screen strikes with its widgets: a preview is a real widget, and plenty of them
 * open their app or run an action when they are touched - which is not what a
 * touch on a card is for. Until the press has lasted long enough to be a pickup
 * nothing is consumed, so a press that wanders off is left to scroll the page,
 * and one that lifts in the same place is read as a tap and swallowed before the
 * widget can hear about it.
 *
 * Once the press *is* a pickup every event is swallowed, so the widget never sees
 * the press end and cannot act on it, and the card's own corner - [coordinatesAt]
 * turned into a place on the screen as the card is now - is what the ghost is
 * carried from. The corner is asked for live because the widget page slides away
 * under the finger when the home screen is brought out; a corner measured once
 * would stay behind with it.
 */
private fun Modifier.widgetCardGesture(
    choice: WidgetChoice,
    controller: IconDragController,
    coordinatesAt: () -> LayoutCoordinates?,
    boundsAt: () -> Rect,
    onTap: (WidgetChoice) -> Unit,
    onDragStart: () -> Unit,
    onDrop: WidgetDropHandler,
): Modifier = pointerInput(choice.key) {
    fun at(local: Offset): Offset =
        coordinatesAt().rootOf(local) ?: (boundsAt().topLeft + local)

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop

        val outcome = withTimeoutOrNull<CardPress>(viewConfiguration.longPressTimeoutMillis) {
            pressOutcome(down.id, slop)
        }

        when (outcome) {
            // Lifted in the same place: a tap on the card, and the widget
            // underneath is not to hear about it.
            CardPress.TAP -> {
                onTap(choice)
                return@awaitEachGesture
            }

            // Moved far enough to be the list being scrolled, so the press
            // belongs to the page and not to the card.
            CardPress.STRAYED -> return@awaitEachGesture

            // Nothing came back at all, which is the long press timing out: the
            // press is a pickup.
            null -> Unit
        }

        // The grip is the vector from the card's corner to the finger, both read
        // in the same space at the same moment.
        val atDown = at(down.position)
        val corner = coordinatesAt().rootOf(Offset.Zero) ?: boundsAt().topLeft
        controller.startPendingWidget(
            choice = choice,
            position = atDown,
            grab = atDown - corner,
        )

        var moved = false

        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()

                if (!change.pressed) {
                    controller.finishWidget()?.let { finished ->
                        if (moved) onDrop(finished, finished.anchor, finished.position)
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
            controller.cancelPendingWidgetIf(choice.key)
        }
    }
}

/**
 * Watches the press that started on a card until either it is let go where it
 * began - which is a tap - or it moves far enough to be the page being scrolled.
 *
 * Returning nothing at all means the press outlasted the long press, and is on
 * its way to being a pickup.
 */
private suspend fun AwaitPointerEventScope.pressOutcome(
    pointerId: PointerId,
    slop: Float,
): CardPress {
    var traveled = 0f

    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return CardPress.STRAYED

        traveled += (change.position - change.previousPosition).getDistance()
        if (traveled > slop) return CardPress.STRAYED

        if (!change.pressed) {
            // Swallowed on the way in, so the widget never sees the tap end.
            change.consume()
            return CardPress.TAP
        }
    }
}

/** A line of text in the middle of the page, for when there is nothing to list. */
@Composable
private fun WidgetPageMessage(message: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
