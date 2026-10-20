package com.sdev.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import com.sdev.launcher.R
import com.sdev.launcher.data.AppInfo

/**
 * The panel that shows what is in a folder.
 *
 * It is drawn in the launcher's own window rather than in a popup, so that an
 * app can be carried out of it and onto the home screen behind: a popup is a
 * window of its own, and what is carried in it could never leave.
 *
 * The apps in the folder are in a grid. Carrying one of them and letting it go
 * over another takes its place; carrying it off the panel leaves the folder.
 *
 * [onGeometry] reports where the card and each of its apps are on the screen,
 * so that a drop can be told apart from one on the home screen behind: the
 * card covers part of the grid, and the two have to be told apart.
 *
 * [alpha] is how much of the panel is seen. It goes to nothing while an app is
 * on its way out of it, so that the home screen behind can be seen and can say
 * where the app would land. The panel is still drawn: taking it away would take
 * the app that is being carried away with it.
 */
@Composable
fun FolderPanel(
    folder: FolderEntry,
    icons: Map<String, ImageBitmap>,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    onDismiss: () -> Unit,
    onGeometry: (card: Rect, members: Map<Int, Rect>) -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val shape = RoundedCornerShape(28.dp)

    // Where the card is, and where each of its apps is, so that a drop over
    // one of them can be told from a drop over the home screen behind.
    var card by remember(folder.id) { mutableStateOf(Rect.Zero) }
    var members by remember(folder.id) { mutableStateOf<Map<Int, Rect>>(emptyMap()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha),
        // The card sits in the middle of the screen, so that the home screen
        // above it and the dock below it can both be seen and can both be
        // dropped on: an app carried out of the folder goes onto the grid
        // behind, or into the dock.
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp)
                .onGloballyPositioned {
                    card = it.boundsInRoot()
                    onGeometry(card, members)
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = folder.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GRID_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(folder.apps) { index, app ->
                    FolderApp(
                        app = app,
                        icon = icons[app.key],
                        folderId = folder.id,
                        index = index,
                        controller = controller,
                        handlers = handlers,
                        onBounds = { bounds ->
                            members = members + (index to bounds)
                            onGeometry(card, members)
                        },
                    )
                }
            }
        }
    }
}

/**
 * One app inside a folder.
 *
 * It is carried the way an app on the home screen is, so that it can be
 * taken out of the folder and put on the screen, or dropped on another app in
 * the folder to take its place.
 */
@Composable
private fun FolderApp(
    app: AppInfo,
    icon: ImageBitmap?,
    folderId: String,
    index: Int,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    onBounds: (Rect) -> Unit,
) {
    DraggableIcon(
        app = app,
        icon = icon,
        area = DragArea.FOLDER,
        index = index,
        controller = controller,
        handlers = handlers,
        iconSize = 48.dp,
        showLabel = false,
        folderId = folderId,
        modifier = Modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) },
    )
}

/** How many apps sit across the grid of a folder. */
private const val COLUMNS = 4

/** How tall the grid of a folder is. */
private val GRID_HEIGHT = 320.dp

/**
 * The question asked before a folder is given a name.
 *
 * A name is a line of text, so the field takes one line and the keyboard it
 * brings up is the one for typing rather than the one for writing.
 */
@Composable
fun RenameFolderDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onConfirm(name.trim())
                        onDismiss()
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim())
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.folder_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
