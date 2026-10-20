package com.sdev.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdev.launcher.R
import com.sdev.launcher.data.LauncherSettings

/** The size of one page thumbnail in the edit mode's strip. */
private val THUMB_WIDTH = 56.dp
private val THUMB_HEIGHT = 92.dp

/** Keys for the two tiles that make room for another page, one at each end. */
private const val ADD_LEFT_KEY = "add-left"
private const val ADD_RIGHT_KEY = "add-right"

/** How far a page's little icons are pulled in from its edges. */
private val THUMB_INSET = 7.dp

/**
 * How much the home screen is pulled back while it is being rearranged. Small
 * enough to see the whole page at once, large enough to still read the icons.
 */
internal val EDIT_CONTENT_SCALE = 0.9f

/** How much room the edit mode's strip and buttons need along the bottom. */
internal val EDIT_MODE_HEIGHT = 158.dp

/**
 * The bottom of the home screen's edit mode: the pages as little thumbnails,
 * with a tile at either end to make room for another one, and the buttons that
 * reach the wallpaper, the widgets and the launcher's own settings.
 *
 * This is the One UI arrangement - the pages are dragged back so they can be
 * seen all at once, and everything to do with them is along the bottom rather
 * than in a menu.
 */
@Composable
fun HomeEditMode(
    pages: Int,
    currentPage: Int,
    rows: Int,
    columns: Int = LauncherSettings.DEFAULT_HOME_COLUMNS,
    canAddPage: Boolean,
    cellsOnPage: (Int) -> Set<Int>,
    onSelectPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onAddPageLeft: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onWallpaper: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /** The page whose options were asked for, and where its thumbnail is. */
    var options by remember { mutableStateOf<Pair<Int, Rect>?>(null) }

    /** A page asked to be taken away that still has something on it. */
    var confirming by remember { mutableStateOf<Int?>(null) }

    /**
     * Takes a page away, asking first when there is something on it.
     *
     * Removing a page removes everything that is on it, so a page with icons or
     * widgets on it is not taken away on one tap by mistake.
     */
    fun remove(page: Int) {
        if (cellsOnPage(page).isEmpty()) {
            onRemovePage(page)
        } else {
            confirming = page
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A tile at either end, so that a page can be made room for at the
            // front of the home screen as well as after the last one.
            if (canAddPage) {
                item(key = ADD_LEFT_KEY) {
                    AddPageTile(onClick = onAddPageLeft)
                }
            }

            items(count = pages, key = { it }) { page ->
                PageThumbnail(
                    occupied = cellsOnPage(page),
                    rows = rows,
                    columns = columns,
                    current = page == currentPage,
                    // Any page but the last can be taken away, and everything on
                    // it goes with it. The home screen always has a page to show.
                    removable = pages > 1,
                    onTap = { onSelectPage(page) },
                    onRemove = { remove(page) },
                    onLongPress = { anchor -> options = page to anchor },
                )
            }

            if (canAddPage) {
                item(key = ADD_RIGHT_KEY) {
                    AddPageTile(onClick = onAddPage)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditModeButton(
                glyph = Glyph.WALLPAPER,
                label = stringResource(R.string.edit_wallpapers),
                onClick = onWallpaper,
            )
            EditModeButton(
                glyph = Glyph.WIDGETS,
                label = stringResource(R.string.edit_widgets),
                onClick = onWidgets,
            )
            EditModeButton(
                glyph = Glyph.SETTINGS,
                label = stringResource(R.string.edit_settings),
                onClick = onSettings,
            )
        }
    }

    options?.let { (page, anchor) ->
        PageOptionsCard(
            anchor = anchor,
            canMoveLeft = page > 0,
            canMoveRight = page < pages - 1,
            canRemove = pages > 1,
            onMoveLeft = {
                onMovePage(page, page - 1)
                options = null
            },
            onMoveRight = {
                onMovePage(page, page + 1)
                options = null
            },
            onRemove = {
                remove(page)
                options = null
            },
            onDismiss = { options = null },
        )
    }

    confirming?.let { page ->
        RemovePageDialog(
            onConfirm = {
                confirming = null
                onRemovePage(page)
            },
            onDismiss = { confirming = null },
        )
    }
}

/**
 * The question asked before a page that still holds something is taken away.
 *
 * One UI asks rather than undoing: everything on the page goes with it, and
 * there is no way back.
 */
@Composable
private fun RemovePageDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_page_remove_title)) },
        text = { Text(stringResource(R.string.edit_page_remove_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.edit_page_remove_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * One page, drawn as a miniature: a rounded card with a mark for everything
 * sitting on that page, in the place it sits in.
 *
 * Tapping it turns to that page; holding it asks what should happen to it; and
 * an empty page carries a cross that takes it away.
 */
@Composable
private fun PageThumbnail(
    occupied: Set<Int>,
    rows: Int,
    columns: Int = LauncherSettings.DEFAULT_HOME_COLUMNS,
    current: Boolean,
    removable: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .then(
                if (current) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onTap)
            .pointerInput(current, occupied) {
                detectTapGestures(onLongPress = { onLongPress(bounds) })
            },
        contentAlignment = Alignment.Center,
    ) {
        PageMiniature(occupied = occupied, rows = rows, columns = columns)

        if (removable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(
                    glyph = Glyph.CLOSE,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 12.dp,
                )
            }
        }
    }
}

/** The marks standing in for the icons and widgets on one page. */
@Composable
internal fun PageMiniature(
    occupied: Set<Int>,
    rows: Int,
    columns: Int = LauncherSettings.DEFAULT_HOME_COLUMNS,
    dot: Dp = 7.dp,
    inset: Dp = THUMB_INSET,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(inset),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    Box(
                        modifier = Modifier
                            .size(dot)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index in occupied) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.07f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** How big the page thumbnails are while something is being carried. */
private val DRAG_THUMB_WIDTH = 38.dp
private val DRAG_THUMB_HEIGHT = 62.dp

/**
 * The pages, along the bottom, while something is being carried: letting go
 * over one puts it on that page.
 *
 * Each thumbnail reports where it is so that the drop can be told which page
 * the finger was over, which is why they are measured one by one rather than
 * having their places worked out from the strip's width.
 */
@Composable
internal fun DragPageStrip(
    pages: Int,
    currentPage: Int,
    rows: Int,
    targetPage: Int?,
    cellsOnPage: (Int) -> Set<Int>,
    onBounds: (List<Pair<Int, Rect>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measured = remember(pages) { mutableMapOf<Int, Rect>() }

    DisposableEffect(Unit) {
        onDispose { onBounds(emptyList()) }
    }

    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pages) { page ->
            val shape = RoundedCornerShape(10.dp)
            val over = page == targetPage

            Box(
                modifier = Modifier
                    .size(width = DRAG_THUMB_WIDTH, height = DRAG_THUMB_HEIGHT)
                    .onGloballyPositioned { coordinates ->
                        measured[page] = coordinates.boundsInRoot()
                        onBounds(measured.toList())
                    }
                    .clip(shape)
                    .background(
                        if (over) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                    )
                    .border(
                        width = if (page == currentPage) 2.dp else 1.dp,
                        color = if (page == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = shape,
                    ),
            ) {
                PageMiniature(
                    occupied = cellsOnPage(page),
                    rows = rows,
                    dot = 4.dp,
                    inset = 5.dp,
                )
            }
        }
    }
}

/**
 * The tile that makes room for another page. There is one at either end of the
 * strip: the one on the left puts the page in front of the first, and the one on
 * the right puts it after the last.
 */
@Composable
private fun AddPageTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(
            glyph = Glyph.ADD,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 22.dp,
        )
    }
}

/** One of the round-cornered buttons along the bottom of the edit mode. */
@Composable
private fun EditModeButton(glyph: Glyph, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(glyph = glyph, tint = LocalContentColor.current, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current,
            )
        }
    }
}

/** What can be done with one page, offered on the thumbnail it belongs to. */
@Composable
private fun PageOptionsCard(
    anchor: Rect,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canRemove: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    OneUiPopup(anchor = anchor, onDismiss = onDismiss) {
        if (canMoveLeft) {
            OneUiAction(
                glyph = Glyph.ARROW_LEFT,
                label = stringResource(R.string.edit_page_left),
                onClick = onMoveLeft,
            )
        }
        if (canMoveRight) {
            OneUiAction(
                glyph = Glyph.ARROW_RIGHT,
                label = stringResource(R.string.edit_page_right),
                onClick = onMoveRight,
            )
        }
        if (canRemove) {
            OneUiAction(
                glyph = Glyph.CLOSE,
                label = stringResource(R.string.edit_page_remove),
                onClick = onRemove,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
