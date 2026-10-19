package com.sdev.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdev.launcher.R
import com.sdev.launcher.data.AppInfo
import com.sdev.launcher.data.HomeArea
import com.sdev.launcher.data.LauncherSettings
import com.sdev.launcher.data.WidgetPlacement
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val HOME_PAGE = 0
private const val DRAWER_PAGE = 1
private const val WIDGETS_PAGE = 2
private const val PAGE_COUNT = 3

/**
 * How far the user has to drag before the launcher moves to the neighbouring
 * page. The pager's default is half a page, which feels sluggish for a
 * launcher: a short swipe up opens the drawer and a short swipe down closes it
 * again. A short flick is still only allowed to turn one page at a time, so the
 * widget page, two pages away, is reached by swiping again rather than by one
 * throw.
 */
private const val SWIPE_THRESHOLD = 0.15f

/**
 * The launcher is three full pages stacked vertically:
 *
 *  - page 0 is the home screen: the wallpaper, plus pinned apps and a dock
 *  - page 1 is the app drawer
 *  - page 2 is the widget page: every widget that can be put on the screen
 *
 * Swiping up moves from home to the drawer and on to the widgets, swiping down
 * moves back. Because each is a page rather than an overlay, each is always
 * fullscreen.
 *
 * An icon can also be picked up in the drawer, and a widget off the widget page,
 * and carried onto the home screen: the drag is owned here rather than by either
 * page, so that it survives the page under the finger changing.
 */
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })

    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var widgetTarget by remember { mutableStateOf<WidgetTarget?>(null) }

    // Where an icon in the air would have to be let go to be taken off the
    // screen, and where it would have to be let go to be uninstalled. Both are
    // measured from the drawn targets themselves.
    var removeZone by remember { mutableStateOf<Rect?>(null) }
    var uninstallZone by remember { mutableStateOf<Rect?>(null) }

    // The one drag in flight, shared by both pages.
    val dragController = remember { IconDragController() }

    // Where the home screen is, and which cell a place on the screen belongs
    // to. The home screen keeps it up to date as it is laid out and as its
    // pages move; a drop asks it rather than doing the arithmetic itself, so
    // there is only ever one answer to "which cell is here".
    val dropArea = remember { HomeDropArea() }
    var dragLayerOrigin by remember { mutableStateOf(Offset.Zero) }

    val band = with(LocalDensity.current) { DRAWER_PULL_ZONE.toPx() }

    // The system may insist on asking the user before this launcher is allowed
    // to show a widget; this is where that answer arrives.
    val widgetBind = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onWidgetBindResult(result.resultCode == Activity.RESULT_OK)
    }

    val widgetRequest = state.widgetRequest
    LaunchedEffect(widgetRequest?.hostId) {
        if (widgetRequest != null) widgetBind.launch(widgetRequest.intent)
    }

    val gestures = remember(dragController, viewModel) {
        IconGestureHandlers(
            onTap = { app ->
                // While the home screen is being rearranged a tap is how the
                // page under it is chosen, never how an app is opened.
                if (!viewModel.state.value.editing) viewModel.launch(app)
            },
            onLongPress = { app, area, index, anchor ->
                // Only an icon that is pinned somewhere can be taken off again.
                widgetTarget = null
                actionTarget = ActionTarget(
                    app = app,
                    area = if (area == DragArea.DRAWER) null else area,
                    index = index,
                    anchor = anchor,
                )
            },
            onDragStart = {
                actionTarget = null
                widgetTarget = null
            },
            onDrop = { drag, position ->
                // Which of the two ways of getting rid of it the icon was let go
                // over, if either. An icon out of the drawer is not offered them
                // at all, so this is null for it whatever it is let go on. The
                // page strip and the grid come after, so anything let go anywhere
                // else lands as it always did.
                val zone = dragTargetFor(drag.area, removeZone, uninstallZone, position)
                val page = dragController.dragPageAt(position)

                // Read through the ViewModel rather than through the state this
                // lambda was remembered with, so it always sees the settings as
                // they are now.
                val editing = viewModel.state.value.editing
                val covered = viewModel.state.value.settings.widgetCells()

                when {
                    zone == DragTarget.REMOVE -> {
                        // Nothing was pinned in the first place if it came from
                        // the drawer, so there is nothing to take away.
                        when (drag.area) {
                            DragArea.HOME -> viewModel.removeFromHome(HomeArea.HOME, drag.index)
                            DragArea.DOCK -> viewModel.removeFromHome(HomeArea.DOCK, drag.index)
                            DragArea.DRAWER -> Unit
                        }
                    }

                    zone == DragTarget.UNINSTALL -> viewModel.uninstall(drag.app)

                    page != null -> viewModel.dropOnPage(drag, page)

                    else -> {
                        val slot = dropArea.dockSlotAt(position, enabled = !editing)

                        // Something carried in from another page is on its way
                        // *onto* this one. Let go in the band above the grid -
                        // the band is what brought the page out - it means the
                        // top row, and lands there rather than nowhere. If the
                        // page has not come out yet the grid is not under the
                        // finger at all, so the band answers by column instead.
                        //
                        // Something being moved on the page is not carried in:
                        // above the grid is nowhere, and it stays where it was.
                        val incoming = drag.area == DragArea.DRAWER
                        val cell = dropArea.cellAt(position, allowAbove = incoming)
                            ?: if (incoming) dropArea.topRowAt(position, band) else null

                        when (drag.area) {
                            // Picked up in the drawer: a new pin, placed where it
                            // was let go.
                            DragArea.DRAWER -> when {
                                slot != null -> viewModel.addToDock(drag.app, slot)

                                cell != null && cell !in covered ->
                                    viewModel.addToHome(drag.app, cell)

                                // Let go inside the drawer: nothing to pin.
                            }

                            // Already on the home screen: this is a move.
                            DragArea.HOME, DragArea.DOCK -> {
                                val ontoWidget = cell != null && cell in covered
                                val from = if (drag.area == DragArea.HOME) {
                                    HomeArea.HOME
                                } else {
                                    HomeArea.DOCK
                                }

                                when {
                                    slot != null -> viewModel.moveHomeItem(
                                        from,
                                        drag.index,
                                        HomeArea.DOCK,
                                        slot,
                                    )

                                    cell != null && !ontoWidget -> viewModel.moveHomeItem(
                                        from,
                                        drag.index,
                                        HomeArea.HOME,
                                        cell,
                                    )
                                    // Let go outside both areas: it stays where
                                    // it was.
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    // Preview widgets are bound to real ids while the widget page is showing, so
    // they are handed back if the launcher's screen goes away with that page
    // still up: the page would not be there to be left, and so nothing would be
    // left to hand them back, and those ids belong to the page.
    DisposableEffect(viewModel) {
        onDispose { viewModel.releaseWidgetPreviews() }
    }

    // The ViewModel says which page *should* be showing (used by the Back key,
    // the widget button and the system Home action)...
    val targetPage = when {
        state.drawerOpen -> DRAWER_PAGE
        state.pickingWidget -> WIDGETS_PAGE
        else -> HOME_PAGE
    }

    LaunchedEffect(targetPage) {
        if (pagerState.currentPage == targetPage) return@LaunchedEffect

        // The drawer is one page from home and slides, which is how it has
        // always felt. Anything further than that - the widget page, two away -
        // arrives at once: sliding it would sweep the drawer past on the way,
        // which reads as having gone somewhere rather than as having gone back.
        val distance = pagerState.currentPage - targetPage
        if (distance in -1..1) {
            pagerState.animateScrollToPage(targetPage)
        } else {
            pagerState.scrollToPage(targetPage)
        }
    }

    // ...and the pager reports back the page the user actually landed on, drawer
    // and widget page together so that neither is ever briefly off on its own.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.setShownPage(
                    drawerOpen = page == DRAWER_PAGE,
                    widgetPageOpen = page == WIDGETS_PAGE,
                )
            }
    }

    BackHandler(
        enabled = state.drawerOpen ||
            state.pickingWidget ||
            actionTarget != null ||
            widgetTarget != null ||
            state.selectedWidget != NO_WIDGET,
    ) {
        when {
            actionTarget != null -> actionTarget = null
            widgetTarget != null -> widgetTarget = null
            state.selectedWidget != NO_WIDGET -> viewModel.selectWidget(NO_WIDGET)
            // Backing out of the widget page lands on the home screen, which is
            // where a widget taken from it would have gone.
            state.pickingWidget -> viewModel.closeWidgetPage()
            else -> viewModel.setDrawerOpen(false)
        }
    }

    // Carrying an icon onto the Home target along the top of the drawer brings
    // the home screen out from under it, so the app can be dropped straight onto
    // the grid. A long press on its own leaves the drawer where it is.
    //
    // The target is up until the home screen has actually arrived, and not just
    // until it starts to: the slide towards it turns the pager's current page
    // over halfway through, and a target that went away then would take the pull
    // with it and leave the two pages stuck halfway between. Once the home
    // screen is there, the whole grid - its first row included - is free to be
    // dropped on, and there is nothing up here left to aim at.
    val drag = dragController.drag
    val widgetDrag = dragController.widgetDrag

    // Something being carried from a page that is not the home screen: an icon
    // out of the drawer, or a widget out of the widget page. Both are carried to
    // the Home target along the top to bring the home screen out for them. A
    // widget already on the home screen is not one of these - it has a place,
    // and is being moved rather than brought in.
    val drawerCarry = drag?.takeIf { it.area == DragArea.DRAWER && it.moved }
    val widgetCarry = widgetDrag?.takeIf { it.choice != null && it.moved }

    val carriedToHome = drawerCarry?.position ?: widgetCarry?.position

    val homeArrived = pagerState.currentPage == HOME_PAGE && !pagerState.isScrollInProgress

    // The band along the top of a page is what asks for the home screen, and it
    // is a place rather than a composable's bounds: the pill drawn in it says
    // where it is, but the test is the same one the drop makes, so the pull and
    // the landing can never mean different things.
    val pullingHome = carriedToHome != null &&
        !state.editing &&
        !homeArrived &&
        carriedToHome.y <= band

    val incoming = drawerCarry != null || widgetCarry != null

    // The pull is acted on in the screen's own scope rather than by the effect
    // that noticed it. The slide takes a moment, and the finger is free to leave
    // the target while it runs: an effect that stopped then would stop the home
    // screen halfway out, leaving the two pages stuck between. Asking for the
    // same page twice is harmless - the second request takes over where the
    // first was.
    val pullScope = rememberCoroutineScope()

    LaunchedEffect(pullingHome) {
        if (!pullingHome || pagerState.currentPage == HOME_PAGE) return@LaunchedEffect

        pullScope.launch {
            // The drawer is one page from home, so it slides the way it always
            // has. The widget page is two away: sliding it would sweep the
            // drawer past on the way, so the home screen is simply there.
            if (drawerCarry != null) {
                pagerState.animateScrollToPage(HOME_PAGE)
            } else {
                pagerState.scrollToPage(HOME_PAGE)
            }
        }
    }

    // A preview belongs to the widget being stretched: when the selection goes,
    // so does the half-finished size.
    LaunchedEffect(state.selectedWidget) {
        dragController.widgetPreview = null
    }

    // Where a widget that has been let go should end up.
    //
    // A page thumbnail under the finger is an explicit "that page, please", and
    // otherwise the grid underneath says which page. Either way the widget is not
    // expected to fit exactly where its own corner was: the nearest place on that
    // page that does fit is where it goes, because letting go of a four by four
    // widget over a page that has a couple of icons on it would otherwise move it
    // nowhere at all.
    //
    // A widget carried in from the widget page has nothing to be moved from: it
    // is being added, and the cell it was aimed at is the place it is asked for.
    val dropWidget: WidgetDropHandler = { carried, anchor, finger ->
        val from = carried.from

        when {
            // Only a widget that is already on the screen has anything to be
            // taken off it: one being brought in was never on it in the first
            // place, so letting it go up here simply leaves it where it came
            // from.
            from != null && removeZone?.contains(finger) == true ->
                viewModel.removeWidget(from.cell)

            else -> {
                // A page thumbnail under the finger is an explicit "that page,
                // please", and wins over whatever grid happens to be showing.
                val onPage = dragController.dragPageAt(finger)
                    ?.let { state.settings.grid.firstCellOfPage(it) }

                // Being brought in, the widget is on its way *onto* the page:
                // in the band above the grid it means the top row, and while the
                // page has not come out yet the band answers by column. Already
                // on the page, it is a move, and above the grid is nowhere.
                val incoming = from == null
                val cell = if (incoming) {
                    dropArea.cellAt(anchor, allowAbove = true)
                        ?: dropArea.cellAt(finger, allowAbove = true)
                        ?: dropArea.topRowAt(finger, band)
                } else {
                    dropArea.cellAt(anchor, allowAbove = false)
                        ?: dropArea.cellAt(finger, allowAbove = false)
                }

                val near = onPage ?: cell

                when {
                    near == null -> Unit

                    from != null -> viewModel.moveWidgetNear(from.cell, near)

                    else -> carried.choice?.let { viewModel.chooseWidget(it, near) }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                dragLayerOrigin = it.positionInRoot()
                // A finger's place is reported in the root's coordinates, so
                // this is the space every other place has to be read against.
                dropArea.root = it
            },
    ) {
        VerticalPager(
            state = pagerState,
            // Every page stays composed: an icon can be dragged out of the page
            // that is sliding away, a widget off the widget page two away, and
            // the home screen has to keep being told where it is while that
            // happens - it is the home screen that owns the geometry a drop is
            // worked out from, and it cannot do that while it is not composed.
            beyondViewportPageCount = 2,
            // Dragging an icon must not also drag the pager, and neither must
            // the home screen while it is being rearranged.
            userScrollEnabled = !dragController.active && !state.editing,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapPositionalThreshold = SWIPE_THRESHOLD,
            ),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                HOME_PAGE -> HomeScreen(
                    state = state,
                    controller = dragController,
                    dropArea = dropArea,
                    handlers = gestures,
                    widgetHost = viewModel.widgetHost,
                    active = pagerState.currentPage == HOME_PAGE,
                    page = state.homePage,
                    onPageChange = viewModel::setHomePage,
                    onEmptyLongPress = { viewModel.setEditing(true) },
                    onEmptyTap = {
                        when {
                            state.selectedWidget != NO_WIDGET -> viewModel.selectWidget(NO_WIDGET)
                            state.editing -> viewModel.setEditing(false)
                        }
                    },
                    onWidgetLongPress = { placement, anchor ->
                        widgetTarget = WidgetTarget(placement.cell, anchor)
                    },
                    onWidgetSelect = viewModel::selectWidget,
                    onDragStart = {
                        actionTarget = null
                        widgetTarget = null
                    },
                    onWidgetDrop = dropWidget,
                    onWidgetRevealShown = viewModel::onWidgetRevealShown,
                    onSwipeDown = viewModel::onHomeSwipeDown,
                    onRevealShown = viewModel::onRevealShown,
                )

                DRAWER_PAGE -> AppDrawerPage(
                    state = state,
                    controller = dragController,
                    handlers = gestures,
                    isActive = pagerState.settledPage == DRAWER_PAGE,
                    onQueryChange = viewModel::onQueryChange,
                )

                else -> WidgetPage(
                    groups = viewModel.widgetGroups(),
                    host = viewModel.widgetHost,
                    controller = dragController,
                    // Taken from the ViewModel rather than from the page the
                    // pager has settled on: the page is asked for before the
                    // pager moves to it, and asking for it is what a fresh visit
                    // is. Waiting for the pager to arrive would leave the last
                    // visit's cards - and the ids they were drawn under, since
                    // handed back - on the screen while it slid in.
                    active = state.pickingWidget,
                    onPick = { choice -> viewModel.chooseWidget(choice) },
                    onDragStart = {
                        actionTarget = null
                        widgetTarget = null
                    },
                    onDrop = dropWidget,
                    onDismiss = viewModel::closeWidgetPage,
                )
            }
        }

        // The dim that says a card is up, or that the home screen is being
        // rearranged. It is drawn over the pages so a tap that misses the card
        // puts it away rather than reaching the icon behind it.
        if (actionTarget != null || widgetTarget != null || state.editing) {
            OneUiScrim(
                onDismiss = {
                    actionTarget = null
                    widgetTarget = null

                    when {
                        state.selectedWidget != NO_WIDGET -> viewModel.selectWidget(NO_WIDGET)
                        state.editing -> viewModel.setEditing(false)
                    }
                },
            )
        }

        // The edit mode's own furniture goes above that dim: a tap on the page
        // strip or on one of the buttons is meant for them, and it is only the
        // home screen behind that puts the edit mode away again.
        if (state.editing) {
            HomeEditMode(
                pages = state.settings.homePages,
                currentPage = state.homePage,
                rows = state.settings.grid.rows,
                canAddPage = state.settings.homePages < LauncherSettings.HOME_PAGE_LIMIT,
                cellsOnPage = { page ->
                    val cells = state.settings.grid.cellsOfPage(page)
                    (state.homeApps.keys + state.settings.widgetCells())
                        .filter { it in cells }
                        .toSet()
                },
                onSelectPage = { page ->
                    // Tapping the page already showing leaves things as they
                    // are: the strip is for moving about, not for leaving.
                    if (page != state.homePage) viewModel.setHomePage(page)
                },
                onAddPage = viewModel::addHomePageAtEnd,
                onAddPageLeft = viewModel::addHomePageAtStart,
                onRemovePage = viewModel::removeHomePage,
                onMovePage = viewModel::moveHomePage,
                onWallpaper = viewModel::openWallpaper,
                onWidgets = viewModel::openWidgetPage,
                onSettings = viewModel::openSettings,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // The handles a selected widget wears. They are drawn above the dim and
        // above the pages both: a corner handle on the edge of the screen would
        // otherwise be clipped by the page it sits in and hidden behind the dim,
        // and the finger on it has to be read in a space that does not move - a
        // handle that resized its own container would be chasing itself.
        state.settings.widgets.values
            .firstOrNull { it.hostId == state.selectedWidget }
            ?.let { selected ->
                WidgetResizeOverlay(
                    placement = selected,
                    controller = dragController,
                    area = dropArea,
                    preview = dragController.widgetPreview,
                    fits = { cell, spanX, spanY ->
                        state.settings.widgetFits(
                            cell = cell,
                            spanX = spanX,
                            spanY = spanY,
                            ignoring = selected.hostId,
                        )
                    },
                    onPreview = { dragController.widgetPreview = it },
                    onResize = { anchor, spanX, spanY ->
                        viewModel.resizeWidget(selected.hostId, anchor, spanX, spanY)
                    },
                    onMoveStart = {
                        actionTarget = null
                        widgetTarget = null
                    },
                    onDrop = dropWidget,
                    onDismiss = {
                        actionTarget = null
                        widgetTarget = null
                        viewModel.selectWidget(NO_WIDGET)
                    },
                )
            }

        // What is on offer along the top while something is in the air: the two
        // ways of getting rid of an icon that is already on the screen, or the
        // way onto the screen for one that is not.
        val carried = drag?.position ?: widgetDrag?.position
        if (carried != null && dragController.moved && !state.editing) {
            // Only something already pinned somewhere has anything to be taken
            // off the screen or uninstalled. An icon out of the drawer and a
            // widget out of the widget page are both on their way *to* the home
            // screen and have neither, so no targets are offered for them: they
            // could not be used, and drawing them over the grid the thing is
            // about to land on invites a drop that does nothing.
            val removable = when {
                drag != null -> drag.area != DragArea.DRAWER
                else -> widgetDrag?.from != null
            }

            when {
                // Something being brought in from another page is offered the
                // way onto the screen until the screen has arrived for it.
                incoming && !homeArrived -> DrawerPullTarget(
                    hovered = pullingHome,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                removable -> DragTargets(
                    position = carried,
                    showUninstall = drag?.let {
                        it.app.componentName.packageName != context.packageName
                    } == true,
                    onZones = { remove, uninstall ->
                        removeZone = remove
                        uninstallZone = uninstall
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        // Drawn last and above every page, so the icon stays under the finger
        // wherever it came from.
        dragController.drag?.let { current ->
            DragOverlay(
                drag = current,
                icon = state.icons[current.app.key],
                iconSize = state.settings.iconSize.sizeDp.dp,
                origin = dragLayerOrigin,
            )
        }

        // The cell size is only known once the home screen has been laid out,
        // which it has by the time anything can be dragged - and the ghost is
        // drawn on the screen, so it takes the size the grid is drawn at.
        dragController.widgetDrag?.let { current ->
            val grid = dropArea.gridSize
            val shape = dropArea.shape
            WidgetGhost(
                drag = current,
                cellSize = Size(
                    width = if (grid.width > 0f) {
                        grid.width / shape.columns
                    } else {
                        0f
                    },
                    height = if (grid.height > 0f) {
                        grid.height / shape.rows
                    } else {
                        0f
                    },
                ),
                origin = dragLayerOrigin,
            )
        }
    }

    actionTarget?.let { target ->
        val isOurs = target.app.componentName.packageName == context.packageName
        val settings = state.settings
        val pinned = target.area != null

        AppActionCard(
            app = target.app,
            icon = state.icons[target.app.key],
            anchor = target.anchor,
            onDismiss = { actionTarget = null },
            onAppInfo = {
                viewModel.openAppInfo(target.app)
                actionTarget = null
            },
            onUninstall = {
                viewModel.uninstall(target.app)
                actionTarget = null
            },
            // The launcher must not offer to hide or uninstall itself.
            showUninstall = !isOurs,
            onHide = if (isOurs) {
                null
            } else {
                {
                    viewModel.hide(target.app)
                    actionTarget = null
                }
            },
            onAddToHome = if (pinned || settings.homeItems.containsValue(target.app.key)) {
                null
            } else {
                {
                    viewModel.addToHome(target.app)
                    actionTarget = null
                }
            },
            onAddToDock = if (settings.dockItems.containsValue(target.app.key)) {
                null
            } else {
                {
                    viewModel.addToDock(target.app)
                    actionTarget = null
                }
            },
            // Only offered where the icon was actually pressed: on the screen it
            // is pinned to.
            onRemove = target.area?.let { area ->
                val home = if (area == DragArea.HOME) HomeArea.HOME else HomeArea.DOCK

                {
                    viewModel.removeFromHome(home, target.index)
                    actionTarget = null
                }
            },
            removeLabel = stringResource(
                if (target.area == DragArea.DOCK) {
                    R.string.remove_from_dock
                } else {
                    R.string.remove_short
                },
            ),
        )
    }

    widgetTarget?.let { target ->
        val placement = state.settings.widgets[target.cell]

        if (placement == null) {
            // Taken away underneath us: nothing left to ask about.
            widgetTarget = null
        } else {
            WidgetActionCard(
                label = remember(placement.provider) {
                    providerLabel(context, placement)
                },
                anchor = target.anchor,
                onRemove = {
                    viewModel.removeWidget(target.cell)
                    widgetTarget = null
                },
                onDismiss = { widgetTarget = null },
            )
        }
    }
}

/**
 * The target that carries an icon out of the drawer and onto the home screen.
 *
 * Holding the icon up here brings the home screen out from under the drawer; the
 * icon is then dropped on it like anything else. The whole band along the top is
 * live - the pill is what says the band is there - and the band is only up while
 * the home screen is still on its way.
 *
 * It reports nothing about where it is: the band is a place along the top of the
 * page, and the pull and the drop both test the same place. A composable that
 * said where it was would be a second answer to that question, and a stale one
 * as soon as it went away.
 */
@Composable
private fun DrawerPullTarget(
    hovered: Boolean,
    modifier: Modifier = Modifier,
) {
    val colours = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DRAWER_PULL_ZONE),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier.padding(top = 22.dp),
            shape = RoundedCornerShape(26.dp),
            color = if (hovered) {
                colours.primary.copy(alpha = 0.28f)
            } else {
                colours.surfaceContainerHigh.copy(alpha = 0.92f)
            },
            shadowElevation = if (hovered) 10.dp else 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(glyph = Glyph.HOME, tint = colours.onSurface, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.drag_to_home),
                    style = MaterialTheme.typography.labelLarge,
                    color = colours.onSurface,
                )
            }
        }
    }
}

/**
 * The two places an icon or a widget can be dropped to get rid of it: taking it
 * off the home screen on the left, and uninstalling it on the right. Both are
 * only on the screen while something is in the air.
 *
 * [onZones] reports where they ended up, so that letting go can be told whether
 * it happened over one of them. It is told nothing once they go away, so a drop
 * somewhere else cannot be mistaken for a drop on a target that was not there.
 */
@Composable
private fun DragTargets(
    position: Offset,
    showUninstall: Boolean,
    onZones: (Rect?, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var remove by remember { mutableStateOf<Rect?>(null) }
    var uninstall by remember { mutableStateOf<Rect?>(null) }

    DisposableEffect(Unit) {
        onDispose { onZones(null, null) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DropZone(
            glyph = Glyph.REMOVE,
            label = stringResource(R.string.remove_short),
            hovered = remove?.contains(position) == true,
            onBounds = { bounds ->
                remove = bounds
                onZones(remove, uninstall)
            },
        )

        if (showUninstall) {
            DropZone(
                glyph = Glyph.UNINSTALL,
                label = stringResource(R.string.uninstall),
                hovered = uninstall?.contains(position) == true,
                onBounds = { bounds ->
                    uninstall = bounds
                    onZones(remove, uninstall)
                },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** One of the two drop targets: a glyph, a name, and a lift while it is over. */
@Composable
private fun DropZone(
    glyph: Glyph,
    label: String,
    hovered: Boolean,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) },
        shape = RoundedCornerShape(26.dp),
        color = if (hovered) {
            tint.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        },
        shadowElevation = if (hovered) 10.dp else 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(glyph = glyph, tint = tint, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
        }
    }
}

/** The app whose actions are showing, and where its icon is. */
private data class ActionTarget(
    val app: AppInfo,
    /** The area it is pinned to, or null when it only lives in the drawer. */
    val area: DragArea?,
    val index: Int,
    val anchor: Rect,
)

/** The widget whose card is showing, and where it is. */
private data class WidgetTarget(val cell: Int, val anchor: Rect)

/** What to call a widget in its own actions card: its app's name. */
private fun providerLabel(context: Context, placement: WidgetPlacement): String {
    val packageName = placement.providerPackage
    return runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
