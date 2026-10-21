package com.sdev.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdev.launcher.R
import com.sdev.launcher.data.AppInfo

/** How many icons a folder's own icon shows at most. */
internal const val FOLDER_PREVIEW_APPS = 4

/** How many icons are in the preview, for a folder of [size] apps. */
internal fun folderPreviewCount(size: Int): Int = size.coerceIn(0, FOLDER_PREVIEW_APPS)

/** How many apps the folder holds beyond the ones on its icon. */
internal fun folderExtraCount(size: Int): Int = (size - FOLDER_PREVIEW_APPS).coerceAtLeast(0)

/**
 * The icon of a folder: up to four of the apps in it, on a soft tinted tile.
 *
 * A folder with one app in it shows that app alone, which is what a launcher
 * does: a folder of one is just an app with a name on it. A folder holding more
 * than the four that fit wears a small count of the ones it cannot show.
 */
@Composable
fun FolderPreviewIcon(
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.24f)
    val colours = MaterialTheme.colorScheme
    val previewSize = size * 0.42f

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        colours.surfaceContainerHighest.copy(alpha = 0.95f),
                        colours.surfaceContainerHigh.copy(alpha = 0.85f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = colours.outlineVariant.copy(alpha = 0.45f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            apps.isEmpty() -> FolderMark(size = size * 0.46f)

            apps.size == 1 -> AppIconImage(
                icon = icons[apps.first().key],
                label = apps.first().label,
                size = size * 0.8f,
                modifier = Modifier.clip(RoundedCornerShape(size * 0.18f)),
            )

            else -> {
                val shown = apps.take(folderPreviewCount(apps.size))

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    shown.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { app ->
                                AppIconImage(
                                    icon = icons[app.key],
                                    label = app.label,
                                    size = previewSize,
                                    modifier = Modifier.clip(RoundedCornerShape(previewSize * 0.22f)),
                                )
                            }
                            // A row of one is padded so the pair stays in the middle.
                            if (row.size == 1) Spacer(Modifier.size(previewSize))
                        }
                    }
                }
            }
        }

        val extra = folderExtraCount(apps.size)
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(size * 0.045f)
                    .clip(CircleShape)
                    .background(colours.secondaryContainer)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = stringResource(R.string.folder_more_apps, extra),
                    style = MaterialTheme.typography.labelSmall,
                    color = colours.onSecondaryContainer,
                )
            }
        }
    }
}

/** A simple folder outline, for a folder that has been left with nothing in it. */
@Composable
private fun FolderMark(size: Dp) {
    val colour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    Canvas(modifier = Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.075f
        val w = this.size.width
        val h = this.size.height

        val folder = Path().apply {
            moveTo(w * 0.08f, h * 0.82f)
            lineTo(w * 0.08f, h * 0.3f)
            lineTo(w * 0.4f, h * 0.3f)
            lineTo(w * 0.5f, h * 0.4f)
            lineTo(w * 0.9f, h * 0.4f)
            lineTo(w * 0.9f, h * 0.82f)
            close()
        }

        drawPath(
            path = folder,
            color = colour,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * A folder on the home screen: its icon, its name, and the gesture that
 * picks it up.
 *
 * A folder is carried the way an app is, so that it can be moved to another
 * cell or merged with what is there. On a locked home screen it is still
 * opened by a tap, but cannot be picked up.
 *
 * [opened] shrinks the icon a little while its panel is on screen, so the
 * folder and the panel read as the same thing.
 */
@Composable
fun DraggableFolder(
    folder: FolderEntry,
    icons: Map<String, ImageBitmap>,
    area: DragArea,
    index: Int,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelColor: Color? = null,
    labelShadow: Boolean = false,
    movable: Boolean = true,
    opened: Boolean = false,
) {
    // The folder's place, as a layout to convert through rather than as a rect
    // copied out of one: the page it sits on slides away under the finger
    // during a drag, and only the live conversion follows it.
    var coordinates by remember(folder.id, area, index) {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    var bounds by remember(folder.id, area, index) { mutableStateOf(Rect.Zero) }

    val shownScale by animateFloatAsState(
        targetValue = if (opened) 0.93f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "folder-icon-open",
    )

    val count = pluralStringResource(
        R.plurals.folder_apps_count,
        folder.apps.size,
        folder.apps.size,
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = shownScale
                scaleY = shownScale
            }
            .semantics { contentDescription = "${folder.label}, $count" }
            .onGloballyPositioned {
                coordinates = it
                bounds = it.boundsInRoot()
            }
            .iconGesture(
                dragged = Dragged.Folder(folder.id),
                area = area,
                index = index,
                controller = controller,
                handlers = handlers,
                coordinatesAt = { coordinates },
                boundsAt = { bounds },
                movable = movable,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolderPreviewIcon(
            apps = folder.apps,
            icons = icons,
            size = iconSize,
        )

        if (showLabel) {
            Spacer(Modifier.size(6.dp))

            Text(
                text = folder.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    shadow = if (labelShadow) LabelShadow else null,
                ),
                color = labelColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
