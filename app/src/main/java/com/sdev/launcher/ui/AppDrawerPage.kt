package com.sdev.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sdev.launcher.R
import com.sdev.launcher.data.AppInfo
import com.sdev.launcher.data.DrawerStyle
import kotlin.math.min

/** Vertical breathing room around the icon grid. */
private val GRID_VERTICAL_PADDING = 8.dp

/**
 * Extra height a cell needs on top of the icon itself. Measured from
 * [DraggableIcon]: the label line plus the gap above it plus the cell padding.
 */
private val LABEL_ROW_EXTRA = 38.dp
private val NO_LABEL_ROW_EXTRA = 16.dp

/** Room inside a drawer cell, so neighbouring labels do not touch. */
private val CELL_PADDING = 8.dp

/**
 * The app drawer, presented as a full page rather than an overlay: it is the
 * page after the home screen in [LauncherScreen]'s pager, so it always covers
 * the whole screen and swiping down returns home.
 *
 * Column count, layout, icon size, labels, search bar visibility, sort order
 * and hidden apps all come from [LauncherUiState.settings].
 *
 * Icons here are held with the same gesture as the home screen's, which is what
 * lets one be picked up and dropped onto the home screen.
 */
@Composable
fun AppDrawerPage(
    state: LauncherUiState,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    isActive: Boolean,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val apps = state.visibleApps

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
            if (settings.showSearchBar) {
                LauncherSearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.loading && apps.isEmpty() -> CenteredMessage(
                        text = stringResource(R.string.loading_apps),
                    )

                    apps.isEmpty() -> CenteredMessage(
                        text = stringResource(R.string.no_apps),
                    )

                    settings.drawerStyle == DrawerStyle.PAGES -> PagedApps(
                        apps = apps,
                        state = state,
                        controller = controller,
                        handlers = handlers,
                        isActive = isActive,
                    )

                    else -> ScrollingApps(
                        apps = apps,
                        state = state,
                        controller = controller,
                        handlers = handlers,
                        isActive = isActive,
                    )
                }
            }

            if (settings.enableT9Search) {
                T9DialPad(
                    onDigitPress = { digit -> onQueryChange(state.query + digit) },
                    onBackspace = {
                        if (state.query.isNotEmpty()) onQueryChange(state.query.dropLast(1))
                    },
                )
            }
        }
    }
}

/**
 * The default drawer: fixed pages of icons, swiped through horizontally.
 *
 * Because a page never scrolls, the whole page can be dragged vertically, which
 * is what lets a swipe anywhere on the drawer take you back home.
 */
@Composable
private fun PagedApps(
    apps: List<AppInfo>,
    state: LauncherUiState,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    isActive: Boolean,
) {
    val settings = state.settings
    val columns = settings.drawerColumns
    val rowHeight = settings.iconSize.sizeDp.dp +
        if (settings.showLabels) LABEL_ROW_EXTRA else NO_LABEL_ROW_EXTRA

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // As many rows as fit, so a page itself never has to scroll.
        val rows = ((maxHeight - GRID_VERTICAL_PADDING * 2) / rowHeight)
            .toInt()
            .coerceAtLeast(1)
        val perPage = rows * columns
        val pageCount = ((apps.size + perPage - 1) / perPage).coerceAtLeast(1)
        val pagerState = rememberPagerState(pageCount = { pageCount })

        // A fresh visit, a new search or a layout change starts on page one.
        LaunchedEffect(isActive, apps, perPage) {
            if (isActive) pagerState.scrollToPage(0)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                // An icon in the air must not also flip the page.
                userScrollEnabled = !controller.active,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val from = page * perPage
                AppGridPage(
                    apps = if (from >= apps.size) {
                        emptyList()
                    } else {
                        apps.subList(from, min(from + perPage, apps.size))
                    },
                    rows = rows,
                    columns = columns,
                    state = state,
                    controller = controller,
                    handlers = handlers,
                )
            }

            if (pageCount > 1) {
                PageDots(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                )
            }
        }
    }
}

/** One page of the paged drawer: a fixed grid that fills the available space. */
@Composable
private fun AppGridPage(
    apps: List<AppInfo>,
    rows: Int,
    columns: Int,
    state: LauncherUiState,
    controller: IconDragController,
    handlers: IconGestureHandlers,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = GRID_VERTICAL_PADDING),
    ) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(columns) { column ->
                    val app = apps.getOrNull(row * columns + column)

                    if (app == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val badgeCount = if (state.settings.showNotificationBadges) {
                            state.notificationCounts[app.componentName.packageName] ?: 0
                        } else {
                            0
                        }

                        DraggableIcon(
                            app = app,
                            icon = state.icons[app.key],
                            area = DragArea.DRAWER,
                            index = row * columns + column,
                            controller = controller,
                            handlers = handlers,
                            // An equal share of the row, not the whole row: a
                            // cell that asks for all the width leaves none for
                            // the ones beside it.
                            modifier = Modifier
                                .weight(1f)
                                .drawerTile()
                                .alpha(if (controller.carries(app)) 0.4f else 1f),
                            iconSize = state.settings.iconSize.sizeDp.dp,
                            showLabel = state.settings.showLabels,
                            badgeCount = badgeCount,
                            // A locked home screen cannot be added to, so an
                            // icon in the drawer has nowhere to be carried.
                            movable = !state.settings.homeLocked,
                        )
                    }
                }
            }
        }
    }
}

/** The scrolling drawer, kept as an alternative layout. */
@Composable
private fun ScrollingApps(
    apps: List<AppInfo>,
    state: LauncherUiState,
    controller: IconDragController,
    handlers: IconGestureHandlers,
    isActive: Boolean,
) {
    val gridState = rememberLazyGridState()

    // Each visit starts at the top of the list.
    LaunchedEffect(isActive, apps) {
        if (isActive) gridState.scrollToItem(0)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(state.settings.drawerColumns),
        state = gridState,
        // An icon in the air must not also scroll the list out from under it.
        userScrollEnabled = !controller.active,
        contentPadding = PaddingValues(
            horizontal = 8.dp,
            vertical = GRID_VERTICAL_PADDING,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(apps) { index, app ->
            val badgeCount = if (state.settings.showNotificationBadges) {
                state.notificationCounts[app.componentName.packageName] ?: 0
            } else {
                0
            }

            DraggableIcon(
                app = app,
                icon = state.icons[app.key],
                area = DragArea.DRAWER,
                index = index,
                controller = controller,
                handlers = handlers,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawerTile()
                    .alpha(if (controller.carries(app)) 0.4f else 1f),
                iconSize = state.settings.iconSize.sizeDp.dp,
                showLabel = state.settings.showLabels,
                badgeCount = badgeCount,
                movable = !state.settings.homeLocked,
            )
        }
    }
}

/** Rounded, slightly padded cell, matching the drawer's older look. */
private fun Modifier.drawerTile(): Modifier = clip(RoundedCornerShape(16.dp))
    .padding(horizontal = 2.dp, vertical = CELL_PADDING)

/**
 * True while [app] is the icon in the air, whichever drawer layout it was picked
 * up in. Matched by the app rather than by its place, because the place means
 * something different in each layout and the app does not.
 */
private fun IconDragController.carries(app: AppInfo): Boolean =
    drag?.let { it.area == DragArea.DRAWER && it.app?.key == app.key } == true

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
