package com.sdev.launcher.ui

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sdev.launcher.R

/** How tall the live widget inside a preview card is. */
private val PREVIEW_TILE_HEIGHT = 116.dp

/** The corner of a preview card. */
private val PREVIEW_CARD_CORNER = 22.dp

/**
 * The widgets that can be added to the home screen, grouped by the app that
 * provides them and drawn as they really are.
 *
 * A preview is the widget itself and not a picture of it: each one is bound to
 * an id of its own while the sheet is open, drawn at the size it asks for, and
 * scaled down to fit its card. That is why the previews cost what they cost, and
 * why the ids are handed back when the sheet closes - all but the widget that is
 * taken, which keeps its id and becomes the one on the home screen.
 *
 * A widget the system will not hand over is offered by name instead: taking it
 * puts the system's own question to the user, as it always did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    groups: List<WidgetGroup>,
    host: WidgetHost,
    onPick: (WidgetChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opened to be looked at rather than glanced at: half a sheet of widget
        // previews is no use to anybody.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.widget_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.widget_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

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
                        WidgetPreviewCard(choice = choice, host = host, onPick = onPick)
                    }
                }
            }
        }
    }
}

/**
 * One widget: a live preview of it, its name, and how much of the grid it takes.
 *
 * The preview is bound when its card first appears and kept for as long as the
 * sheet is open, so coming back to it costs nothing. Scrolling away does not
 * release it either: a widget let go of and asked for again - which is what
 * releasing and re-binding would be - is a widget that could come back looking
 * different.
 */
@Composable
private fun WidgetPreviewCard(
    choice: WidgetChoice,
    host: WidgetHost,
    onPick: (WidgetChoice) -> Unit,
) {
    var id by remember(choice.key) { mutableStateOf<Int?>(null) }
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
            .previewTap { onPick(choice) },
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

/**
 * Takes a tap on a preview card for the picker, and keeps it from the widget.
 *
 * A preview is a real widget, and plenty of widgets open their app or run an
 * action when they are touched - which is not what a tap on a card is for. A
 * press that stays put is therefore swallowed on the way in and turned into a
 * tap on the card, while a press that moves is left alone so that the list still
 * scrolls under the finger. It is the same bargain the home screen strikes with
 * its widgets, and for the same reason.
 */
private fun Modifier.previewTap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        var traveled = 0f

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            traveled += (change.position - change.previousPosition).getDistance()

            if (!change.pressed) {
                // Lifted in the same place: a tap on the card, and the widget
                // underneath is not to hear about it.
                if (traveled <= slop) {
                    change.consume()
                    onTap()
                }
                break
            }

            // Far enough to be the list being scrolled: from here the press
            // belongs to the sheet.
            if (traveled > slop) break
        }
    }
}

/** Shown when a widget on the home screen is long-pressed. */
@Composable
fun WidgetActionCard(
    label: String,
    anchor: Rect,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    OneUiPopup(anchor = anchor, onDismiss = onDismiss) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        OneUiAction(
            glyph = Glyph.REMOVE,
            label = stringResource(R.string.widget_remove),
            onClick = onRemove,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
