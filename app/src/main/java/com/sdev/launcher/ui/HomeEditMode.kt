package com.sdev.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdev.launcher.R
import com.sdev.launcher.data.LauncherSettings

/**
 * How much the home screen is pulled back while it is being rearranged. Small
 * enough to see the whole page at once, large enough to still read the icons.
 */
internal val EDIT_CONTENT_SCALE = 0.9f

/** How much room the edit mode's buttons need along the bottom. */
internal val EDIT_MODE_HEIGHT = 84.dp

/** How far a page's outline is pulled in from the edge of its space. */
internal val PAGE_FRAME_INSET = 10.dp

/** The corner of a page's outline. */
internal val PAGE_FRAME_CORNER = 28.dp

/**
 * The bottom of the home screen's edit mode: the buttons that reach the
 * wallpaper, the widgets and the launcher's own settings.
 *
 * The pages used to be drawn here as thumbnails; they are gone now that the
 * page can be managed from the page itself, and the home screen keeps the room
 * along the bottom for this one row.
 */
@Composable
fun HomeEditMode(
    onWallpaper: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
}

/**
 * A page-sized outline shown at either end of the edit mode's pager. It is a
 * place to put a page rather than a page itself: the + in the middle makes one.
 *
 * The outline is only the frame; the button in the middle is the whole target,
 * so a tap anywhere else still falls through and puts the edit mode away.
 */
@Composable
internal fun AddPageOutline(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(PAGE_FRAME_CORNER)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(PAGE_FRAME_INSET)
            .clip(shape)
            .background(colours.surface.copy(alpha = 0.08f))
            .drawBehind {
                drawRoundRect(
                    color = colours.primary.copy(alpha = 0.5f),
                    cornerRadius = CornerRadius(PAGE_FRAME_CORNER.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(14.dp.toPx(), 10.dp.toPx()),
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colours.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(
                    glyph = Glyph.ADD,
                    tint = colours.onPrimaryContainer,
                    size = 26.dp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colours.onSurface,
            )
        }
    }
}

/**
 * What can be done with the page under the finger, offered where it was pressed.
 *
 * A long press on empty space opens this card. Removing a page that still holds
 * something asks first, because everything on it goes with it.
 */
@Composable
internal fun PageOptions(
    page: Int,
    pageCount: Int,
    anchor: Rect,
    hasContent: Boolean,
    isMainPage: Boolean,
    onSetMainPage: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember(page) { mutableStateOf(false) }

    PageOptionsCard(
        anchor = anchor,
        showSetMain = !isMainPage,
        canRemove = pageCount > 1,
        onSetMain = {
            onSetMainPage()
            onDismiss()
        },
        onRemove = {
            if (hasContent) {
                confirming = true
            } else {
                onRemovePage(page)
                onDismiss()
            }
        },
        onLock = {
            onLock()
            onDismiss()
        },
        onDismiss = onDismiss,
    )

    if (confirming) {
        RemovePageDialog(
            onConfirm = {
                confirming = false
                onRemovePage(page)
                onDismiss()
            },
            onDismiss = { confirming = false },
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

/** The marks standing in for the icons and widgets on one page. */
@Composable
internal fun PageMiniature(
    occupied: Set<Int>,
    rows: Int,
    columns: Int = LauncherSettings.DEFAULT_HOME_COLUMNS,
    dot: Dp = 5.dp,
    inset: Dp = 5.dp,
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

/** What can be done with one page, offered where the page was long pressed. */
@Composable
private fun PageOptionsCard(
    anchor: Rect,
    showSetMain: Boolean,
    canRemove: Boolean,
    onSetMain: () -> Unit,
    onRemove: () -> Unit,
    onLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    OneUiPopup(anchor = anchor, onDismiss = onDismiss) {
        if (showSetMain) {
            OneUiAction(
                glyph = Glyph.HOME,
                label = stringResource(R.string.edit_page_set_main),
                onClick = onSetMain,
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
        OneUiAction(
            glyph = Glyph.LOCK,
            label = stringResource(R.string.edit_lock_home),
            onClick = onLock,
        )
    }
}

/**
 * What a long press says while the home screen is locked: why nothing can be
 * moved, and the one way out of it.
 *
 * It is a card rather than a toast so that the way to the switch is part of the
 * message. Nothing on the home screen can be edited until that switch is turned
 * off, so the card is the only place the lock can be explained.
 */
@Composable
internal fun LockedHint(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                glyph = Glyph.LOCK,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 20.dp,
            )
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Text(
                text = stringResource(R.string.home_locked_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_locked_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(2.dp))

    OneUiAction(
        glyph = Glyph.SETTINGS,
        label = stringResource(R.string.home_locked_settings),
        onClick = onSettings,
    )
}
