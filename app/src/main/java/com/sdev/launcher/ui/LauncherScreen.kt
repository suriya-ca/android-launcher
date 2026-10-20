package com.sdev.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.sdev.launcher.data.WidgetPlacement
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val HOME_PAGE = 0
private const val DRAWER_PAGE = 1
private const val PAGE_COUNT = 2

/**
 * How far the user has to drag before the launcher moves to the neighbouring
 * page. The pager's default is half a page, which feels sluggish for a
 * launcher: a short swipe up opens the drawer and a short swipe down closes it
 * again. A short flick is still only allowed to turn one page at a time.
 */
private const val SWIPE_THRESHOLD = 0.15f

/**
 * The launcher is two full pages stacked vertically:
 *
 *  - page 0 is the home screen: the wallpaper, plus pinned apps and a dock
 *  - page 1 is the app drawer
 *
 * Swiping up moves from home to the drawer and swiping down moves back. The
 * widget page is not one of them: it is a sheet over the launcher, opened by the
 * button in the home screen's edit mode, so the drawer cannot be swiped on past
 * into it.
 *
 * An icon can be picked up in the drawer, and a widget off the widget page, and
 * carried onto the home screen: the drag is owned here rather than by either
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
    var pageTarget by remember { mutableStateOf<PageTarget?>(null) }

    // The folder being renamed, if one is being renamed.
    var renamingFolder by remember { mutableStateOf<String?>(null) }

    // The folder that is open, and the one whose name is being asked for, are
    // put away when the home screen is rearranged: the panel is over the page,
    // and the page has to be live for the panel to be of any use.
    LaunchedEffect(state.editing) {
        if (state.editing) {
            viewModel.closeFolder()
            renamingFolder = null
        }
    }

    // Page options belong to the page they were asked about. Leaving edit mode
    // or turning to another page puts them away with it.
    LaunchedEffect(state.editing, state.homePage) {
        pageTarget = null
    }

    // Where an icon in the air would have to be let go to be taken off the
    // screen, and where it would have to be let go to be uninstalled. Both are
    // measured from the drawn targets themselves.
    var removeZone by remember { mutableStateOf<Rect?>(null) }
    var uninstallZone by remember { mutableStateOf<Rect?>(null) }

    // Where the open folder is on the screen. An app carried out of it is
    // dropped on the home screen when the finger leaves the folder, and put
    // back where it was when it does not.
    var folderBounds by remember { mutableStateOf<Rect?>(null) }
    var folderMembers by remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }

    // The one drag in flight, shared by both pages.
    val dragController = remember { IconDragController() }

    // Where the home screen is, and which cell a place on the screen belongs
    // to. The home screen keeps it up to date as it is laid out and as its
    // pages move; a drop asks it rather than doing the arithmetic itself, so
    // there is only ever one answer to "which cell is here".
    val dropArea = remember { HomeDropArea() }
    var dragLayerOrigin by remember { mutableStateOf(Offset.Zero) }

    // The place under the open folder is not a place the home screen can be
    // dropped on, so the card is told about and forgotten about with it.
    LaunchedEffect(state.openFolder) {
        if (state.openFolder == null) dropArea.folderCard = null
    }

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
            onTap = { dragged, area ->
                // A tap on the home screen while a folder is open puts the
                // folder away: the panel is over the page, and the page should
                // not do something else behind it. A tap on an app in the
                // folder itself opens the app, and the folder goes with it.
                if (area == DragArea.FOLDER) {
                    viewModel.closeFolder()
                    viewModel.launch((dragged as? Dragged.App)?.app ?: return@IconGestureHandlers)
                    return@IconGestureHandlers
                }

                if (viewModel.state.value.openFolder != null) {
                    viewModel.closeFolder()
                    return@IconGestureHandlers
                }

                // While the home screen is being rearranged a tap is how the
                // mode is left, never how an app is opened: the icons and the
                // wallpaper are one surface there, the way the dim over them
                // used to make them.
                if (viewModel.state.value.editing) {
                    viewModel.setEditing(false)
                    return@IconGestureHandlers
                }

                when (dragged) {
                    is Dragged.App -> viewModel.launch(dragged.app)
                    is Dragged.Folder -> viewModel.openFolder(dragged.folderId)
                }
            },
            onLongPress = { dragged, area, index, anchor ->
                // The card is not offered while the home screen is being
                // rearranged: the pages are live again behind the dim so they
                // can be swiped between, and a card over them would take the
                // swipe. Holding an icon there still picks it up and moves it.
                if (!viewModel.state.value.editing) {
                    // Only an icon that is pinned somewhere can be taken off
                    // again.
                    widgetTarget = null
                    actionTarget = when (dragged) {
                        is Dragged.App -> ActionTarget.App(
                            app = dragged.app,
                            area = if (area == DragArea.DRAWER) null else area,
                            index = index,
                            anchor = anchor,
                        )

                        is Dragged.Folder -> ActionTarget.Folder(
                            folderId = dragged.folderId,
                            index = index,
                            anchor = anchor,
                        )
                    }
                }
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
                            DragArea.FOLDER -> viewModel.removeFromFolder(drag)
                            DragArea.DRAWER -> Unit
                        }
                    }

                    zone == DragTarget.UNINSTALL -> drag.app?.let { viewModel.uninstall(it) }

                    page != null -> viewModel.dropOnPage(drag, page)

                    else -> {
                        val slot = if (state.settings.showDock) {
                            dropArea.dockSlotAt(
                                position = position,
                                targets = state.dockApps.keys.sorted(),
                                enabled = !editing,
                            )
                        } else {
                            null
                        }

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

                        // A widget is in the way of the cell under the finger, so
                        // nothing can be put there.
                        val ontoWidget = cell != null && cell in covered

                        // The open folder is over the page, so a drop on it is
                        // a drop on the folder and not on the cell behind it.
                        val overTheFolder = state.openFolder != null &&
                            folderBounds?.contains(position) == true

                        when (val carried = drag.dragged) {
                            // A folder is carried whole. It goes on the home
                            // screen and nowhere else: the dock holds apps.
                            is Dragged.Folder -> {
                                when {
                                    slot != null -> viewModel.refuseFolderInDock()

                                    cell != null && !ontoWidget ->
                                        viewModel.dropFolderOnHomeCell(drag, cell)
                                }
                            }

                            is Dragged.App -> when (drag.area) {
                                // Picked up in the drawer: a new pin, placed
                                // where it was let go. Landing it on an app
                                // makes a folder of the two, the same as it
                                // does on the home screen.
                                DragArea.DRAWER -> {
                                    val app = carried.app

                                    when {
                                        // Let go on the open folder: it goes
                                        // into it.
                                        overTheFolder -> viewModel.addAppToFolder(
                                            state.openFolder.orEmpty(),
                                            app,
                                        )

                                        slot != null && app != null ->
                                            viewModel.addToDock(app, slot)

                                        cell != null && cell !in covered && app != null ->
                                            viewModel.dropAppOnHomeCell(drag, cell)

                                        // Let go inside the drawer: nothing to pin.
                                    }
                                }

                                // Already on the home screen: this is a move.
                                DragArea.HOME, DragArea.DOCK -> {
                                    when {
                                        // Let go on the open folder: it goes
                                        // into it.
                                        overTheFolder -> viewModel.addAppToFolder(
                                            state.openFolder.orEmpty(),
                                            carried.app,
                                        )

                                        // A home icon let go on the dock moves into a
                                        // free slot near the finger. It does not
                                        // swap with the dock app it was dropped on:
                                        // that would replace it and put it back on
                                        // the home screen.
                                        slot != null && drag.area == DragArea.HOME ->
                                            viewModel.moveHomeItemToDock(drag.index, slot)

                                        // Dock to dock is a reorder, so a swap is what
                                        // moving one past another means.
                                        slot != null -> viewModel.dropAppInDock(drag, slot)

                                        cell != null && !ontoWidget ->
                                            viewModel.dropAppOnHomeCell(drag, cell)

                                        // Let go outside both areas: it stays where
                                        // it was.
                                    }
                                }

                                // Taken out of a folder: it either takes another
                                // app's place inside the folder, or it goes back
                                // on the home screen.
                                DragArea.FOLDER -> {
                                    val member = folderMembers.entries
                                        .firstOrNull { (index, bounds) ->
                                            index != drag.index && bounds.contains(position)
                                        }
                                        ?.key

                                    when {
                                        // Dropped on another app in the folder: it
                                        // takes that app's place.
                                        member != null -> viewModel.moveFolderApp(
                                            drag.folderId.orEmpty(),
                                            drag.index,
                                            member,
                                        )

                                        // Still over the folder: it stays in it.
                                        folderBounds?.contains(position) == true -> Unit

                                        slot != null -> viewModel.dropAppInDock(drag, slot)

                                        cell != null && !ontoWidget ->
                                            viewModel.dropAppOnHomeCell(drag, cell)
                                    }
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

    // The ViewModel says which page *should* be showing (used by the Back key
    // and the system Home action)...
    val targetPage = if (state.drawerOpen) DRAWER_PAGE else HOME_PAGE

    LaunchedEffect(targetPage) {
        if (pagerState.currentPage == targetPage) return@LaunchedEffect

        // The two pages are neighbours, so this is always a slide, which is how
        // a launcher has always felt.
        pagerState.animateScrollToPage(targetPage)
    }

    // ...and the pager reports back the page the user actually landed on.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.setDrawerOpen(page == DRAWER_PAGE) }
    }

    BackHandler(
        enabled = pageTarget != null ||
            state.editing ||
            state.drawerOpen ||
            state.pickingWidget ||
            actionTarget != null ||
            widgetTarget != null ||
            state.selectedWidget != NO_WIDGET,
    ) {
        when {
            pageTarget != null -> pageTarget = null
            state.editing -> viewModel.setEditing(false)
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
    // out of the drawer. It is carried to the Home target along the top to bring
    // the home screen out for it. A widget already on the home screen is not one
    // of these - it has a place, and is being moved rather than brought in. A
    // widget off the widget page is not one either: picking it up takes the page
    // away, so the home screen is already there under it.
    val drawerCarry = drag?.takeIf { it.area == DragArea.DRAWER && it.moved }
    val widgetCarry = widgetDrag?.takeIf { it.choice != null && it.moved }

    val carriedToHome = drawerCarry?.position

    val homeArrived = pagerState.currentPage == HOME_PAGE && !pagerState.isScrollInProgress

    // The band along the top of a page is what asks for the home screen, and it
    // is a place rather than a composable's bounds: the pill drawn in it says
    // where it is, but the test is the same one the drop makes, so the pull and
    // the landing can never mean different things.
    val pullingHome = carriedToHome != null &&
        !state.editing &&
        !homeArrived &&
        carriedToHome.y <= band

    val incoming = drawerCarry != null

    // The pull is acted on in the screen's own scope rather than by the effect
    // that noticed it. The slide takes a moment, and the finger is free to leave
    // the target while it runs: an effect that stopped then would stop the home
    // screen halfway out, leaving the two pages stuck between. Asking for the
    // same page twice is harmless - the second request takes over where the
    // first was.
    val pullScope = rememberCoroutineScope()

    LaunchedEffect(pullingHome) {
        if (!pullingHome || pagerState.currentPage == HOME_PAGE) return@LaunchedEffect

        pullScope.launch { pagerState.animateScrollToPage(HOME_PAGE) }
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
            // The home screen has to keep being told where it is while the
            // drawer slides over it - it is the home screen that owns the
            // geometry a drop is worked out from, and it cannot tell anyone
            // anything while it is not composed. Two pages means the one next
            // door is always composed; that is enough for an icon dragged out of
            // the drawer, since it is only ever one page away.
            beyondViewportPageCount = 1,
            // Dragging an icon must not also drag the pager, and neither must
            // the home screen while it is being rearranged: turning its pages is
            // a sideways swipe, and the drawer is not where that is going.
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
                    onEmptyLongPress = { position ->
                        if (state.editing) {
                            // The page thumbnails are gone, so the page itself
                            // offers its own options where it was long pressed.
                            if (state.settings.homePages > 1) {
                                pageTarget = PageTarget(
                                    page = state.homePage,
                                    anchor = Rect(
                                        position.x,
                                        position.y,
                                        position.x,
                                        position.y,
                                    ),
                                )
                            }
                        } else {
                            viewModel.setEditing(true)
                        }
                    },
                    onEmptyTap = {
                        when {
                            state.selectedWidget != NO_WIDGET -> viewModel.selectWidget(NO_WIDGET)
                            state.editing -> viewModel.setEditing(false)
                        }
                    },
                    onAddPageStart = viewModel::addHomePageAtStart,
                    onAddPageEnd = viewModel::addHomePageAtEnd,
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

                else -> AppDrawerPage(
                    state = state,
                    controller = dragController,
                    handlers = gestures,
                    isActive = pagerState.settledPage == DRAWER_PAGE,
                    onQueryChange = viewModel::onQueryChange,
                )
            }
        }

        // The widget page is a sheet over the launcher rather than a page of it,
        // so the drawer cannot be swiped on into it: it is reached by the button
        // in the home screen's edit mode, and by nothing else.
        //
        // Picking a widget up takes the sheet away - the home screen is what the
        // widget is being aimed at, and having to carry it over the sheet to get
        // there would be carrying it over the thing that is in the way. It is
        // only hidden, though, not composed away: the gesture being followed is
        // the card's, and disposing the page under the finger would tear it out
        // from under it. A drop that lands nowhere brings the sheet back.
        if (state.pickingWidget) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (widgetCarry != null) 0f else 1f }
                    // The sheet is over the pages rather than one of them, so
                    // the parts of it that are not a card - the gaps around the
                    // header, the space below the last row - have to hold the
                    // touch themselves. Without anything here a drag on one
                    // would turn the pages underneath, out of sight. What a tap
                    // on one does is nothing.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                WidgetPage(
                    groups = viewModel.widgetGroups(),
                    host = viewModel.widgetHost,
                    controller = dragController,
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

        // The folder that is open, shown over the home screen. It is drawn here
        // rather than in a popup so that an app can be carried out of it and
        // onto the grid behind: a popup is a window of its own, and what is
        // carried in it could never leave.
        //
        // While an app is on its way out of the folder the panel goes
        // transparent, so that the home screen behind it can be seen and can
        // say where the app would land. It is still drawn: taking it away
        // would take the app that is being carried away with it.
        val fromFolder = dragController.drag
        val leftTheFolder = fromFolder?.area == DragArea.FOLDER &&
            fromFolder.moved &&
            folderBounds?.contains(fromFolder.position) == false

        state.openFolder?.let { folderId ->
            val folder = state.folder(folderId)

            if (folder == null) {
                // The folder is gone: it was removed, or the app in it was.
                viewModel.closeFolder()
            } else {
                FolderPanel(
                    folder = folder,
                    icons = state.icons,
                    controller = dragController,
                    handlers = gestures,
                    onDismiss = viewModel::closeFolder,
                    onGeometry = { card, members ->
                        folderBounds = card
                        folderMembers = members
                    },
                    alpha = if (leftTheFolder) 0f else 1f,
                )
            }
        }

        // While the panel is there it covers part of the grid, and a place
        // under it is not a place the home screen can be dropped on. Once it
        // goes transparent the grid is bare again, and the app on its way out
        // can be dropped anywhere on it.
        LaunchedEffect(folderBounds, leftTheFolder) {
            dropArea.folderCard = if (leftTheFolder) null else folderBounds
        }

        // The dim that says a card is up. It is drawn over the pages so a tap
        // that misses the card puts it away rather than reaching the icon behind
        // it.
        //
        // Edit mode has a dim too, but it is not this one: [OneUiDim] has no
        // pointer input, so the pages under it stay live and can be swiped
        // between while the home screen is being rearranged. What a tap should do
        // there is the home screen's own business, and it already knows.
        if (actionTarget != null || widgetTarget != null) {
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
        } else if (state.editing) {
            OneUiDim()
        }

        // The edit mode's own furniture goes above that dim. The add-page
        // outlines live in the pager itself; these are the buttons along the
        // bottom.
        if (state.editing) {
            HomeEditMode(
                onWallpaper = viewModel::openWallpaper,
                onWidgets = viewModel::openWidgetPage,
                onSettings = viewModel::openSettings,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            if (pageTarget != null) {
                OneUiScrim(onDismiss = { pageTarget = null })
            }

            pageTarget?.let { target ->
                PageOptions(
                    page = target.page,
                    pageCount = state.settings.homePages,
                    anchor = target.anchor,
                    hasContent = state.settings.grid.cellsOfPage(target.page).any { cell ->
                        cell in state.homeCells || cell in state.settings.widgetCells()
                    },
                    isMainPage = target.page == state.settings.mainHomePage,
                    onSetMainPage = { viewModel.setMainHomePage(target.page) },
                    onRemovePage = viewModel::removeHomePage,
                    onDismiss = { pageTarget = null },
                )
            }
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
                        it.app?.componentName?.packageName != context.packageName
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
            when (val dragged = current.dragged) {
                is Dragged.App -> DragOverlay(
                    drag = current,
                    icon = state.icons[dragged.app.key],
                    iconSize = state.settings.iconSize.sizeDp.dp,
                    origin = dragLayerOrigin,
                )

                is Dragged.Folder -> {
                    val folder = state.folder(dragged.folderId)

                    if (folder != null) {
                        DragOverlay(
                            drag = current,
                            icon = null,
                            iconSize = state.settings.iconSize.sizeDp.dp,
                            origin = dragLayerOrigin,
                            content = {
                                FolderPreviewIcon(
                                    apps = folder.apps,
                                    icons = state.icons,
                                    size = state.settings.iconSize.sizeDp.dp,
                                )
                            },
                        )
                    }
                }
            }
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
        when (target) {
            is ActionTarget.App -> {
                val isOurs = target.app.componentName.packageName == context.packageName
                val settings = state.settings
                val pinned = target.area != null

                AppActionCard(
                    app = target.app,
                    icon = state.icons[target.app.key],
                    anchor = target.anchor,
                    onDismiss = {
                        actionTarget = null
                    },
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
                    onAddToHome = if (pinned || settings.holdsApp(target.app.key)) {
                        null
                    } else {                        {
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
                    // Dock apps keep their actions first; this is the way from one
                    // of them into the home screen's own edit mode.
                    onEditHome = if (target.area == DragArea.DOCK) {
                        {
                            actionTarget = null
                            viewModel.setEditing(true)
                        }
                    } else {
                        null
                    },
                    // Only offered where the icon was actually pressed: on the screen it
                    // is pinned to. An app inside a folder is not on the home
                    // screen in its own right, so it leaves the folder instead.
                    onRemove = target.area?.takeIf { it != DragArea.FOLDER }?.let { area ->
                        val home = if (area == DragArea.HOME) HomeArea.HOME else HomeArea.DOCK

                        {
                            viewModel.removeFromHome(home, target.index)
                            actionTarget = null
                        }
                    },
                    onRemoveFromFolder = if (target.area == DragArea.FOLDER) {
                        {
                            viewModel.removeAppFromFolder(
                                state.openFolder.orEmpty(),
                                target.app,
                            )
                            actionTarget = null
                        }
                    } else {
                        null
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

            is ActionTarget.Folder -> {
                FolderActionCard(
                    folder = state.folder(target.folderId),
                    icons = state.icons,
                    anchor = target.anchor,
                    onDismiss = { actionTarget = null },
                    onRename = {
                        actionTarget = null
                        renamingFolder = target.folderId
                    },
                    onRemove = {
                        viewModel.deleteFolder(target.folderId)
                        actionTarget = null
                    },
                )
            }
        }
    }

    // The question asked before a folder is given a name.
    renamingFolder?.let { folderId ->
        val folder = state.folder(folderId)

        RenameFolderDialog(
            current = folder?.name.orEmpty(),
            onConfirm = { name -> viewModel.renameFolder(folderId, name) },
            onDismiss = { renamingFolder = null },
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

/**
 * What is being acted on, and where its icon is.
 *
 * It is an app - on the home screen, in the dock, in the drawer or inside a
 * folder - or a folder of apps.
 */
private sealed interface ActionTarget {

    /** Where the icon is, so that the card can hang off it. */
    val anchor: Rect

    /** An app. */
    data class App(
        val app: AppInfo,
        /** The area it is pinned to, or null when it only lives in the drawer. */
        val area: DragArea?,
        val index: Int,
        override val anchor: Rect,
    ) : ActionTarget

    /** A folder, with the cell it is on. */
    data class Folder(
        val folderId: String,
        val index: Int,
        override val anchor: Rect,
    ) : ActionTarget
}

/** The widget whose card is showing, and where it is. */
private data class WidgetTarget(val cell: Int, val anchor: Rect)

/** The page whose options are showing, and where on the page it was asked for. */
private data class PageTarget(val page: Int, val anchor: Rect)

/** What to call a widget in its own actions card: its app's name. */
private fun providerLabel(context: Context, placement: WidgetPlacement): String {
    val packageName = placement.providerPackage
    return runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
