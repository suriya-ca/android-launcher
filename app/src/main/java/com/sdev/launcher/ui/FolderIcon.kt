package com.sdev.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdev.launcher.data.AppInfo

/**
 * The icon of a folder: up to four of the apps in it, in a square.
 *
 * A folder with one app in it shows that app alone, which is what a launcher
 * does: a folder of one is just an app with a name on it.
 */
@Composable
fun FolderPreviewIcon(
    apps: List<AppInfo>,
    icons: Map<String, ImageBitmap>,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        if (apps.size == 1) {
            AppIconImage(
                icon = icons[apps.first().key],
                label = apps.first().label,
                size = size * 0.86f,
            )
            return@Box
        }

        val shown = apps.take(4)
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            shown.chunked(2).forEach { row ->
                Row {
                    row.forEach { app ->
                        AppIconImage(
                            icon = icons[app.key],
                            label = app.label,
                            size = size * 0.44f,
                            modifier = Modifier.padding(1.dp),
                        )
                    }
                    // A row of one is padded so the pair stays in the middle.
                    if (row.size == 1) Spacer(Modifier.size(size * 0.44f))
                }
            }
        }
    }
}

/**
 * A folder on the home screen: its icon, its name, and the gesture that
 * picks it up.
 *
 * A folder is carried the way an app is, so that it can be moved to another
 * cell or merged with what is there.
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
) {
    // The folder's place, as a layout to convert through rather than as a rect
    // copied out of one: the page it sits on slides away under the finger
    // during a drag, and only the live conversion follows it.
    var coordinates by remember(folder.id, area, index) {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    var bounds by remember(folder.id, area, index) { mutableStateOf(Rect.Zero) }

    Column(
        modifier = modifier
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
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolderPreviewIcon(
            apps = folder.apps,
            icons = icons,
            size = iconSize,
        )

        if (showLabel) {
            Spacer(Modifier.height(6.dp))

            Text(
                text = folder.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    shadow = if (labelShadow) LabelShadow else null,
                ),
                color = labelColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
