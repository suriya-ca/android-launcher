package com.sdev.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 *
 * [dropTarget] says an app from outside is over the panel and would join the
 * folder if it were let go. [hoveredMember] is the app an app from inside would
 * take the place of, so that the rearrangement can be seen before it happens.
 */
@Composable
fun FolderPanel(
    folder: FolderEntry,
    icons: Map<String, ImageBitmap>,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    onDismiss: () -> Unit,
    onGeometry: (card: Rect, members: Map<Int, Rect>) -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    locked: Boolean = false,
    showLabels: Boolean = true,
    dropTarget: Boolean = false,
    hoveredMember: Int? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    val colours = MaterialTheme.colorScheme

    // Where the card is, and where each of its apps is, so that a drop over
    // one of them can be told from a drop over the home screen behind.
    var card by remember(folder.id) { mutableStateOf(Rect.Zero) }
    var members by remember(folder.id) { mutableStateOf<Map<Int, Rect>>(emptyMap()) }

    // The panel leans forward a little while an app is over it, so the folder
    // says it will take it before the finger comes up.
    val emphasis by animateFloatAsState(
        targetValue = if (dropTarget) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "folder-drop-target",
    )

    val count = pluralStringResource(
        R.plurals.folder_apps_count,
        folder.apps.size,
        folder.apps.size,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha),
        // The card sits in the middle of the screen, so that the home screen
        // above it and the dock below it can both be seen and can both be
        // dropped on: an app carried out of the folder goes onto the grid
        // behind, or into the dock.
        //
        // Nothing here holds the touch: the card has its own gesture, and
        // everywhere else the touch belongs to the home screen behind, which is
        // what lets an app be carried out of the folder and onto it.
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = 1f + 0.015f * emphasis
                    scaleY = 1f + 0.015f * emphasis
                }
                .onGloballyPositioned {
                    card = it.boundsInRoot()
                    onGeometry(card, members)
                },
            shape = shape,
            color = colours.surfaceContainerHigh,
            shadowElevation = 16.dp,
            border = BorderStroke(
                width = 1.dp + 1.dp * emphasis,
                color = androidx.compose.ui.graphics.lerp(
                    colours.outlineVariant.copy(alpha = 0.4f),
                    colours.primary,
                    emphasis,
                ),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FolderPreviewIcon(
                        apps = folder.apps,
                        icons = icons,
                        size = 34.dp,
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = colours.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (locked) {
                                Modifier
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onRename)
                            },
                        )
                        Text(
                            text = if (dropTarget) {
                                stringResource(R.string.folder_drop_to_add)
                            } else {
                                count
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dropTarget) colours.primary else colours.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!locked) {
                        PanelIconButton(
                            glyph = Glyph.RENAME,
                            description = stringResource(R.string.folder_rename),
                            onClick = onRename,
                        )
                    }

                    PanelIconButton(
                        glyph = Glyph.CLOSE,
                        description = stringResource(R.string.close),
                        onClick = onDismiss,
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (folder.apps.isEmpty()) {
                    EmptyFolder()
                } else {
                    val rows = folderRowCount(folder.apps.size)
                    val rowHeight = if (showLabels) FOLDER_ROW_HEIGHT else FOLDER_BARE_ROW_HEIGHT
                    val gridHeight = rowHeight * rows +
                        FOLDER_ITEM_GAP * (rows - 1).coerceAtLeast(0)

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(FOLDER_COLUMNS),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                        horizontalArrangement = Arrangement.spacedBy(FOLDER_ITEM_GAP),
                        verticalArrangement = Arrangement.spacedBy(FOLDER_ITEM_GAP),
                    ) {
                        itemsIndexed(folder.apps) { index, app ->
                            FolderApp(
                                app = app,
                                icon = icons[app.key],
                                folderId = folder.id,
                                index = index,
                                controller = controller,
                                handlers = handlers,
                                movable = !locked,
                                showLabel = showLabels,
                                highlighted = hoveredMember == index,
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
    }
}

/** One app inside a folder. */
@Composable
private fun FolderApp(
    app: AppInfo,
    icon: ImageBitmap?,
    folderId: String,
    index: Int,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    movable: Boolean,
    showLabel: Boolean,
    highlighted: Boolean,
    onBounds: (Rect) -> Unit,
) {
    val colours = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (highlighted) {
                    colours.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
            )
            .onGloballyPositioned { onBounds(it.boundsInRoot()) },
        contentAlignment = Alignment.Center,
    ) {
        DraggableIcon(
            app = app,
            icon = icon,
            area = DragArea.FOLDER,
            index = index,
            controller = controller,
            handlers = handlers,
            iconSize = 52.dp,
            showLabel = showLabel,
            folderId = folderId,
            movable = movable,
        )
    }
}

/** What a folder says when there is nothing in it. */
@Composable
private fun EmptyFolder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolderPreviewIcon(
            apps = emptyList(),
            icons = emptyMap(),
            size = 46.dp,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.folder_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.folder_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A round, quiet button in the panel's own header. */
@Composable
private fun PanelIconButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(
            glyph = glyph,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 18.dp,
        )
    }
}

/** How many apps sit across the grid of a folder. */
internal const val FOLDER_COLUMNS = 4

/** The most rows of apps a folder shows before it scrolls. */
internal const val FOLDER_MAX_ROWS = 5

/** How many rows a folder of [apps] apps needs, within what it will show. */
internal fun folderRowCount(
    apps: Int,
    columns: Int = FOLDER_COLUMNS,
    maxRows: Int = FOLDER_MAX_ROWS,
): Int = ((apps + columns - 1) / columns).coerceIn(1, maxRows)

/** The height of one row of a folder, with the app's name under it. */
private val FOLDER_ROW_HEIGHT = 74.dp

/** The height of one row when the names are turned off. */
private val FOLDER_BARE_ROW_HEIGHT = 58.dp

/** The gap between the apps in a folder. */
private val FOLDER_ITEM_GAP = 6.dp

/**
 * The question asked before a folder is given a name.
 *
 * A name is a line of text, so the field takes one line and the keyboard it
 * brings up is the one for typing rather than the one for writing. The name
 * that is already there is selected when the question opens, so typing replaces
 * it and leaving it blank gives the folder its default name back.
 */
@Composable
fun RenameFolderDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(current) {
        mutableStateOf(TextFieldValue(current, selection = TextRange(0, current.length)))
    }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_default_name)) },
                supportingText = { Text(stringResource(R.string.folder_rename_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onConfirm(name.text.trim())
                        onDismiss()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.text.trim())
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
