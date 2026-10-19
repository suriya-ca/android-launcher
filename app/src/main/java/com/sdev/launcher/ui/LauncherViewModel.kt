package com.sdev.launcher.ui

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdev.launcher.LauncherApp
import com.sdev.launcher.R
import com.sdev.launcher.data.AppInfo
import com.sdev.launcher.data.AppRepository
import com.sdev.launcher.data.HomeArea
import com.sdev.launcher.data.LauncherSettings
import com.sdev.launcher.data.SettingsRepository
import com.sdev.launcher.data.SortOrder
import com.sdev.launcher.data.findFreeCellOnPage
import com.sdev.launcher.data.findNearestWidgetCell
import com.sdev.launcher.service.NotificationShadeService
import com.sdev.launcher.ui.settings.SettingsActivity
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** No widget is selected. A cell index is never negative, so it cannot clash. */
internal const val NO_WIDGET = -1

/**
 * One entry in the widget picker: a widget that can be added, what to call it,
 * and how many cells it takes.
 */
data class WidgetChoice(
    /**
     * Identifies this entry for as long as the picker is open, so that the id its
     * preview is bound to can be found again and the same widget cannot be listed
     * under it twice.
     */
    val key: String,
    val provider: AppWidgetProviderInfo,
    val label: String,
    val spanX: Int,
    val spanY: Int,
)

/** One app's widgets: the heading the picker gives them, and the widgets under it. */
data class WidgetGroup(
    val packageName: String,
    val label: String,
    val choices: List<WidgetChoice>,
)

/**
 * A widget the user has picked that the system has not handed over yet: the id
 * already reserved for it, what it is, and the screen the system wants the user
 * to answer on.
 */
data class WidgetRequest(
    val hostId: Int,
    val provider: ComponentName,
    val spanX: Int,
    val spanY: Int,
    /**
     * The cell the user let the widget go on, when they carried it in from the
     * widget page rather than tapping for it. Kept here because the answer to
     * the system's question can take a while to come back, and by then the
     * finger is long gone: without it the widget would land wherever it first
     * fits instead of where it was aimed.
     */
    val near: Int? = null,
    val intent: Intent,
)

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val icons: Map<String, ImageBitmap> = emptyMap(),
    val settings: LauncherSettings = LauncherSettings(),
    val query: String = "",
    val drawerOpen: Boolean = false,
    /** Which home screen page the user is looking at. */
    val homePage: Int = 0,
    val loading: Boolean = true,
    /**
     * A widget that has been asked for but cannot be shown until the system, or
     * the user, says yes. The screen it is waiting on is [WidgetRequest.intent].
     */
    val widgetRequest: WidgetRequest? = null,
    /** True while the widget picker is up. */
    val pickingWidget: Boolean = false,
    /**
     * The host id of the widget whose corner handles are showing, or
     * [NO_WIDGET]. A widget is held down to get them, the way One UI offers to
     * make it bigger.
     *
     * It is the widget rather than the cell it sits on because the handles
     * outlive the widget being moved or stretched, both of which change its
     * cell: holding a cell here would mean the handles vanished the moment the
     * widget was dropped somewhere else.
     */
    val selectedWidget: Int = NO_WIDGET,
    /**
     * True while the home screen is being rearranged: the pages are pulled back,
     * the page strip is up, and tapping the empty space puts it all away again.
     */
    val editing: Boolean = false,
    /** The cell a widget has just been placed on, for its flash. */
    val revealWidget: Int? = null,
    /**
     * The cell an app was just pinned to. The home screen pulses it once it is
     * actually on screen, which is how an "add to home screen" that happened
     * while the drawer was open becomes visible.
     */
    val revealCell: Pair<HomeArea, Int>? = null,
) {
    /**
     * [apps] with hidden ones removed, narrowed by the drawer's search field
     * and ordered by the chosen sort order.
     */
    val visibleApps: List<AppInfo>
        get() {
            val trimmed = query.trim()
            val filtered = listedApps.filter { app ->
                trimmed.isEmpty() || app.label.contains(trimmed, ignoreCase = true)
            }
            return when (settings.sortOrder) {
                // The repository already delivers apps A-Z.
                SortOrder.ALPHABETICAL -> filtered
                SortOrder.REVERSE_ALPHABETICAL -> filtered.asReversed()
            }
        }

    /** Apps that are installed and not hidden, in their drawer order. */
    val listedApps: List<AppInfo>
        get() = apps.filter { it.key !in settings.hiddenApps }

    /**
     * The pinned home screen cells, with apps that are no longer installed
     * dropped.
     *
     * Hidden apps stay: hiding takes an app out of the drawer, not off the home
     * screen, so an icon the user pinned is not quietly swallowed by hiding the
     * app it launches.
     */
    val homeApps: Map<Int, AppInfo> get() = resolve(settings.homeItems)

    /** The dock slots, with apps that are no longer installed dropped. */
    val dockApps: Map<Int, AppInfo> get() = resolve(settings.dockItems)

    private fun resolve(slots: Map<Int, String>): Map<Int, AppInfo> {
        if (slots.isEmpty()) return emptyMap()

        val byKey = apps.associateBy { it.key }
        return slots.mapNotNull { (index, key) ->
            byKey[key]?.let { index to it }
        }.toMap()
    }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val settingsRepository = SettingsRepository(application)

    /**
     * The launcher's widget host. It belongs to the application, not to this
     * view model, because it has to outlive the home screen: it is how the
     * system knows where to send a widget's updates, and a host that is taken
     * down stops being sent them. The home screen draws its widgets through it.
     */
    val widgetHost = (application as LauncherApp).widgetHost

    /** The widgets the open picker is showing, grouped by the app they come from. */
    private var pickerGroups: List<WidgetGroup> = emptyList()

    private val _state = MutableStateFlow(LauncherUiState())
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    private var iconJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            repository.packageEvents().collect { refresh() }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val previous = _state.value.settings
                _state.update { it.copy(settings = settings) }

                // Icons are rendered from these settings, so any of them
                // changing means the bitmaps have to be produced again.
                if (needsIcons(previous, settings)) reloadIcons()
            }
        }
    }

    /**
     * Called when the home screen comes back to the front.
     *
     * The host is asked to keep listening - it does nothing if it already is -
     * because a host that has stopped is a host the system no longer sends
     * widget updates to. Every widget is then told its room again: the grid may
     * have been changed while the launcher was away, and a provider that draws
     * itself to fit is only asked to draw again when its room changes.
     */
    fun onResumed() {
        widgetHost.keepListening()
        widgetHost.refreshSizes(_state.value.settings.widgets.values)
        refresh()
    }

    /** Reloads the app list; cheap enough to call on every resume. */
    fun refresh() {
        viewModelScope.launch {
            // The launcher's own settings sit in the drawer like an app.
            val apps = (repository.loadApps() + launcherSettingsEntry())
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

            // Home screen cells for apps that have since gone away are dropped,
            // so those cells become usable again. Widgets whose app has gone go
            // the same way.
            settingsRepository.pruneHomeItems(apps.mapTo(HashSet(apps.size)) { it.key })
            settingsRepository.pruneWidgets()

            _state.update { current ->
                current.copy(apps = apps, loading = false)
            }
            reloadIcons()
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /**
     * Called by the pager when the user lands on a page, and by the Back key /
     * system Home action when a page should be shown.
     */
    fun setDrawerOpen(open: Boolean) {
        _state.update { current ->
            if (current.drawerOpen == open) {
                current
            } else {
                // A fresh visit to the drawer starts with an empty search.
                current.copy(drawerOpen = open, query = if (open) current.query else "")
            }
        }
    }

    /** Called when the system Home action arrives while we are already showing. */
    fun onHomeRequested() {
        setDrawerOpen(false)
        _state.update { it.copy(editing = false, selectedWidget = NO_WIDGET) }
    }

    /** Called by the home screen as the user turns from one page to the next. */
    fun setHomePage(page: Int) {
        _state.update { current ->
            if (current.homePage == page) current else current.copy(homePage = page)
        }
    }

    /**
     * Makes room beside the page being looked at. A page added on the left
     * pushes what was on screen along with it, so the user keeps looking at the
     * same icons and the new page is the one before them.
     */
    fun addHomePage(toLeft: Boolean) {
        val settings = _state.value.settings
        if (settings.homePages >= LauncherSettings.HOME_PAGE_LIMIT) return

        val current = _state.value.homePage
        viewModelScope.launch {
            settingsRepository.insertHomePage(if (toLeft) current else current + 1)
            if (toLeft) _state.update { it.copy(homePage = current + 1) }
        }
    }

    /** Takes away the page being looked at, everything on it with it. */
    fun removeHomePage() {
        val page = _state.value.homePage
        viewModelScope.launch {
            releaseWidgets(settingsRepository.removeHomePage(page))
            _state.update { it.copy(homePage = (page - 1).coerceAtLeast(0)) }
        }
    }

    /**
     * Takes away page [page], which the edit mode's strip of page thumbnails can
     * ask for wherever the user happens to be.
     */
    fun removeHomePage(page: Int) {
        if (page == _state.value.homePage) {
            removeHomePage()
            return
        }

        viewModelScope.launch {
            releaseWidgets(settingsRepository.removeHomePage(page))
            _state.update { current ->
                current.copy(
                    homePage = if (current.homePage > page) current.homePage - 1 else current.homePage,
                )
            }
        }
    }

    /** Hands widgets the store has given up on back to the system. */
    private fun releaseWidgets(hostIds: List<Int>) {
        widgetHost.releaseIds(hostIds)
    }

    /** Opens the launcher's own settings screen, from wherever it was asked for. */
    fun openSettings() {
        val context = getApplication<Application>()

        runCatching {
            context.startActivity(
                Intent(context, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        setDrawerOpen(false)
        _state.update { it.copy(editing = false) }
    }

    /** Hands over to the system's wallpaper chooser. */
    fun openWallpaper() {
        val context = getApplication<Application>()

        val sent = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SET_WALLPAPER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess

        if (!sent) {
            Toast.makeText(
                context,
                context.getString(
                    R.string.action_failed,
                    context.getString(R.string.settings_wallpaper),
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }

        _state.update { it.copy(editing = false) }
    }

    /** Adds a page after the last one, which is what the edit mode's right + does. */
    fun addHomePageAtEnd() {
        val settings = _state.value.settings
        if (settings.homePages >= LauncherSettings.HOME_PAGE_LIMIT) return

        viewModelScope.launch {
            settingsRepository.insertHomePage(settings.homePages)
            _state.update { it.copy(homePage = settings.homePages) }
        }
    }

    /**
     * Adds a page in front of the first one, which is what the edit mode's left +
     * does. Everything already on the home screen keeps its place and shifts one
     * page along, and the new page is turned to.
     */
    fun addHomePageAtStart() {
        val settings = _state.value.settings
        if (settings.homePages >= LauncherSettings.HOME_PAGE_LIMIT) return

        viewModelScope.launch {
            settingsRepository.insertHomePage(0)
            _state.update { it.copy(homePage = 0) }
        }
    }

    /** Drags one page thumbnail past its neighbours. */
    fun moveHomePage(from: Int, to: Int) {
        val pages = _state.value.settings.homePages
        if (from == to || from !in 0 until pages || to !in 0 until pages) return

        viewModelScope.launch {
            settingsRepository.moveHomePage(from, to)
            _state.update { current ->
                // The page being looked at follows the one that moved.
                current.copy(
                    homePage = when (current.homePage) {
                        from -> to
                        in from..to -> current.homePage - 1
                        in to..from -> current.homePage + 1
                        else -> current.homePage
                    },
                )
            }
        }
    }

    fun launch(app: AppInfo) {
        val context = getApplication<Application>()

        // Our own drawer entry is the launcher's settings screen.
        if (app.componentName.packageName == context.packageName) {
            openSettings()
            return
        }

        val started = runCatching {
            val service = context.getSystemService(LauncherApps::class.java)
                ?: error("LauncherApps is unavailable")
            service.startMainActivity(app.componentName, app.user, null, null)
        }.isSuccess

        if (!started) {
            startIntent(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.componentName)
                    .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                app,
            )
        }

        // Launching an app leaves the launcher on the home page, so coming
        // back (via Back or Home) lands on the wallpaper, not the drawer.
        setDrawerOpen(false)
        _state.update { it.copy(editing = false, selectedWidget = NO_WIDGET) }
    }

    fun openAppInfo(app: AppInfo) {
        startIntent(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", app.componentName.packageName, null)),
            app,
        )
    }

    fun uninstall(app: AppInfo) {
        startIntent(
            Intent(Intent.ACTION_DELETE)
                .setData(Uri.fromParts("package", app.componentName.packageName, null)),
            app,
        )
    }

    /**
     * Keeps an app installed but out of the drawer.
     *
     * An icon already pinned to the home screen or the dock stays where it is:
     * hiding is about the drawer, and swallowing an icon the user put there
     * would be a surprise.
     */
    fun hide(app: AppInfo) {
        viewModelScope.launch { settingsRepository.setAppHidden(app.key, true) }
    }

    /**
     * Pins an app to the home screen: where the user dropped it, or the first
     * free cell when it was added from the app's actions.
     */
    fun addToHome(app: AppInfo, preferred: Int? = null) =
        pin(app, HomeArea.HOME, preferred, R.string.home_full)

    /**
     * Pins an app to the dock, into [preferred] when that slot is free.
     */
    fun addToDock(app: AppInfo, preferred: Int? = null) =
        pin(app, HomeArea.DOCK, preferred, R.string.dock_full)

    fun removeFromHome(area: HomeArea, index: Int) {
        viewModelScope.launch { settingsRepository.clearHomeItem(area, index) }
    }

    fun moveHomeItem(from: HomeArea, fromIndex: Int, to: HomeArea, toIndex: Int) {
        viewModelScope.launch {
            settingsRepository.moveHomeItem(from, fromIndex, to, toIndex)
        }
    }

    /**
     * Puts what is being carried on [page], into the first cell of it that is
     * free. This is what letting go over a page thumbnail does.
     */
    fun dropOnPage(drag: DragState, page: Int) {
        val settings = _state.value.settings
        val occupied = settings.homeItems.keys + settings.widgetCells()
        val cell = findFreeCellOnPage(occupied, settings.grid, page) ?: return

        when (drag.area) {
            DragArea.DRAWER -> addToHome(drag.app, cell)

            DragArea.HOME, DragArea.DOCK -> {
                // Already there: dropping it back on its own page does nothing.
                if (drag.area == DragArea.HOME && drag.index == cell) return

                moveHomeItem(
                    from = if (drag.area == DragArea.HOME) HomeArea.HOME else HomeArea.DOCK,
                    fromIndex = drag.index,
                    to = HomeArea.HOME,
                    toIndex = cell,
                )
            }
        }
    }

    /** Swiping down on the home screen asks for the notification shade. */
    fun onHomeSwipeDown() {
        val context = getApplication<Application>()
        if (!_state.value.settings.swipeDownNotifications) return

        if (!NotificationShadeService.expandShade()) {
            Toast.makeText(
                context,
                context.getString(R.string.shade_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun pin(app: AppInfo, area: HomeArea, preferred: Int?, fullMessage: Int) {
        val context = getApplication<Application>()
        val settings = _state.value.settings
        val slots = if (area == HomeArea.HOME) settings.homeItems else settings.dockItems
        if (slots.containsValue(app.key)) return

        viewModelScope.launch {
            var pages = settings.homePages
            var slot = settingsRepository.addHomeItem(app.key, area, preferred, pages)

            // Nowhere left to put it: the home screen gets another page rather
            // than the pin being refused.
            if (slot == null && area == HomeArea.HOME && pages < LauncherSettings.HOME_PAGE_LIMIT) {
                pages += 1
                settingsRepository.insertHomePage(pages - 1)
                slot = settingsRepository.addHomeItem(
                    app.key,
                    area,
                    settings.grid.firstCellOfPage(pages - 1),
                    pages,
                )
            }

            if (slot == null) {
                Toast.makeText(context, context.getString(fullMessage), Toast.LENGTH_SHORT).show()
            } else {
                // If the icon landed on another page, the home screen turns to
                // it so that the new arrival can actually be seen.
                _state.update { current ->
                    current.copy(
                        revealCell = area to slot,
                        homePage = if (area == HomeArea.HOME) {
                            settings.grid.pageOf(slot)
                        } else {
                            current.homePage
                        },
                    )
                }
            }
        }
    }

    /** Called once the pulse over [revealCell] has been shown. */
    fun onRevealShown() {
        _state.update { it.copy(revealCell = null) }
    }

    // --- widgets -------------------------------------------------------------

    /**
     * Opens the widget page, with the widgets that can go on the home screen.
     *
     * Reached both from the button in edit mode and from the pager on settling
     * there, so a page asked for and a page swiped to come through the same
     * door. Already being open is left alone, which is what keeps the previews
     * from being bound a second time as the pager reports the page it has just
     * arrived at.
     */
    fun openWidgetPage() {
        if (_state.value.pickingWidget) return

        pickerGroups = groupWidgets(widgetHost.providers())
        _state.update { it.copy(pickingWidget = true, editing = false) }
    }

    /**
     * Lets the widget page go, handing back the ids reserved for its previews.
     *
     * Called both when the page is closed and when it is left behind, so that a
     * page swiped past does not go on holding the widgets it was drawing. The id
     * of a widget that has just been taken is not among them: it was handed back
     * when it was chosen, and the widget that landed on the home screen was bound
     * from scratch under an id of its own.
     */
    fun closeWidgetPage() {
        if (!_state.value.pickingWidget) return

        widgetHost.releasePreviews()
        _state.update { it.copy(pickingWidget = false) }
    }

    /**
     * Where the user has settled. The pager runs the pages, so the pager is what
     * says which one is showing, and the rest of the launcher follows.
     *
     * The drawer is taken first: the two are set one after the other, and an
     * instant with neither of them true is an instant with the home screen asked
     * for, which on the way out of the widget page would send the pager off to
     * the wrong end of the stack.
     */
    fun setShownPage(drawerOpen: Boolean, widgetPageOpen: Boolean) {
        setDrawerOpen(drawerOpen)

        if (widgetPageOpen) openWidgetPage() else closeWidgetPage()
    }

    /**
     * Hands back the ids reserved for previews without closing the picker.
     *
     * Preview widgets are bound to real ids, so if the screen goes away with the
     * picker still up there is nothing left to close it: the ids belong to the
     * picker rather than to the home screen, and are asked for again by whatever
     * draws the picker next.
     */
    fun releaseWidgetPreviews() {
        widgetHost.releasePreviews()
    }

    /** The widgets on offer, grouped by the app that provides them. */
    fun widgetGroups(): List<WidgetGroup> = pickerGroups

    /**
     * The widgets the picker lists: grouped by the app they come from, and
     * ordered the way everything else is, by the name the user reads.
     */
    private fun groupWidgets(providers: List<AppWidgetProviderInfo>): List<WidgetGroup> {
        val grid = _state.value.settings.grid

        return providers
            .mapIndexed { index, provider -> index to provider }
            .groupBy { (_, provider) -> provider.provider.packageName }
            .map { (packageName, entries) ->
                val choices = entries
                    .map { (index, provider) ->
                        val (spanX, spanY) = widgetHost.spanOf(provider, grid)

                        WidgetChoice(
                            // The place in the list as well as the component, so
                            // that a provider the system lists twice is still two
                            // entries rather than one drawn over the other.
                            key = "$index:${provider.provider.flattenToString()}",
                            provider = provider,
                            label = widgetHost.labelOf(provider).toString(),
                            spanX = spanX,
                            spanY = spanY,
                        )
                    }
                    .sortedBy { it.label.lowercase() }

                WidgetGroup(
                    packageName = packageName,
                    label = appLabelOf(packageName),
                    choices = choices,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** What to call the app a widget comes from. */
    private fun appLabelOf(packageName: String): String {
        val context = getApplication<Application>()
        return runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault(packageName)
    }

    /**
     * Puts the chosen widget on the home screen.
     *
     * The previews were only ever for looking at: every id reserved for one goes
     * back, and the widget that lands is bound from scratch like any other. That
     * keeps a preview's view from having to be moved out of the widget page and
     * into the home screen - a widget's view can only be in one place at a time,
     * and the page is still holding it while the home screen draws.
     *
     * [near] is the cell the user let the widget go on when they carried it in,
     * and null when they tapped for it instead. Tapping leaves the widget to take
     * the first place it fits; carrying it puts it where it was dropped.
     */
    fun chooseWidget(choice: WidgetChoice, near: Int? = null) {
        widgetHost.releasePreviews()

        _state.update { it.copy(pickingWidget = false, editing = false) }

        val provider = choice.provider.provider
        val hostId = widgetHost.allocateId()

        if (widgetHost.bind(hostId, provider)) {
            placeWidget(hostId, provider, choice.spanX, choice.spanY, near)
            return
        }

        _state.update {
            it.copy(
                widgetRequest = WidgetRequest(
                    hostId = hostId,
                    provider = provider,
                    spanX = choice.spanX,
                    spanY = choice.spanY,
                    near = near,
                    intent = widgetHost.bindIntent(hostId, provider),
                ),
            )
        }
    }

    /** Called with whatever the user answered to the system's question. */
    fun onWidgetBindResult(allowed: Boolean) {
        val request = _state.value.widgetRequest ?: return
        _state.update { it.copy(widgetRequest = null) }

        if (!allowed) {
            widgetHost.releaseId(request.hostId)
            Toast.makeText(
                getApplication<Application>(),
                getApplication<Application>().getString(R.string.widget_not_allowed),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        placeWidget(request.hostId, request.provider, request.spanX, request.spanY, request.near)
    }

    /**
     * Puts a bound widget on the home screen, as near [near] as it fits.
     *
     * The cell the user aimed at is where it goes when there is room, and the
     * nearest place on that same page that fits when there is not - a widget
     * dropped on a page with a couple of icons on it should not be refused for
     * the sake of the cell its corner happened to be over. Only when the page has
     * no room at all does it fall back to wherever it will fit, which is where a
     * tapped widget goes too.
     */
    private fun placeWidget(
        hostId: Int,
        provider: ComponentName,
        spanX: Int,
        spanY: Int,
        near: Int? = null,
    ) {
        viewModelScope.launch {
            val settings = _state.value.settings
            val grid = settings.grid

            val preferred = near?.let { anchor ->
                findNearestWidgetCell(
                    grid = grid,
                    occupied = occupiedCells(settings),
                    page = grid.pageOf(anchor),
                    spanX = spanX,
                    spanY = spanY,
                    preferred = anchor,
                )
            }

            val cell = settingsRepository.addWidget(
                hostId = hostId,
                provider = provider.flattenToString(),
                spanX = spanX,
                spanY = spanY,
                preferred = preferred,
                pages = settings.homePages,
            )

            if (cell == null) {
                widgetHost.releaseId(hostId)
                Toast.makeText(
                    getApplication<Application>(),
                    getApplication<Application>().getString(R.string.widget_no_room),
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }

            // The system is told how much room the widget has landed in, so a
            // provider that draws itself to fit draws for the right size from
            // the first frame rather than being stretched to it.
            widgetHost.resize(hostId, spanX, spanY)

            _state.update {
                it.copy(homePage = grid.pageOf(cell), revealWidget = cell)
            }
        }
    }

    /** Every cell that may not be built on: the icons, and the room widgets take. */
    private fun occupiedCells(settings: LauncherSettings): Set<Int> {
        val grid = settings.grid
        return settings.homeItems.keys +
            settings.widgets.values.flatMap { it.cellsIn(grid) }
    }

    /** Moves a widget the user dragged to another cell. */
    fun moveWidget(from: Int, to: Int) {
        viewModelScope.launch { settingsRepository.moveWidget(from, to) }
    }

    /**
     * Moves the widget on [from] to the cell nearest [near] where it fits, on
     * the page [near] sits on.
     *
     * Letting a widget go where its own corner has no room should still move it
     * when there is room a cell away, rather than doing nothing - which is what
     * happens when a four by four widget is dropped on a page with a couple of
     * icons on it. [near] is a cell on the page wanted rather than a place to
     * land: the nearest cell that fits is where it goes.
     */
    fun moveWidgetNear(from: Int, near: Int) {
        val settings = _state.value.settings
        val widget = settings.widgets[from] ?: return

        val grid = settings.grid
        val occupied = settings.homeItems.keys +
            settings.widgets.values
                .filter { it.cell != from }
                .flatMap { it.cellsIn(grid) }

        val cell = findNearestWidgetCell(
            grid = grid,
            occupied = occupied,
            page = grid.pageOf(near),
            spanX = widget.spanX,
            spanY = widget.spanY,
            preferred = near,
        ) ?: return

        if (cell != from) moveWidget(from, cell)
    }

    /** Takes a widget off the home screen for good. */
    fun removeWidget(cell: Int) {
        val placement = _state.value.settings.widgets[cell] ?: return

        // The handles cannot stay on a widget that is no longer there.
        _state.update { current ->
            if (current.selectedWidget == placement.hostId) {
                current.copy(selectedWidget = NO_WIDGET)
            } else {
                current
            }
        }

        viewModelScope.launch {
            settingsRepository.removeWidget(cell)
            widgetHost.releaseId(placement.hostId)
        }
    }

    /** Called once the flash over a newly added widget has been shown. */
    fun onWidgetRevealShown() {
        _state.update { it.copy(revealWidget = null) }
    }

    /**
     * Shows the corner handles on the widget at [cell], or takes them away again
     * when [cell] is [NO_WIDGET].
     *
     * The widget itself is remembered rather than its cell, so that the handles
     * stay on it while it is moved or stretched.
     */
    fun selectWidget(cell: Int) {
        val hostId = _state.value.settings.widgets[cell]?.hostId ?: NO_WIDGET
        _state.update { it.copy(selectedWidget = hostId) }
    }

    /**
     * Gives a widget the room the user dragged out for it. The store refuses a
     * size that would run off the grid or sit on another icon, in which case the
     * widget simply stays as it was.
     */
    fun resizeWidget(hostId: Int, anchor: Int, spanX: Int, spanY: Int) {
        viewModelScope.launch {
            if (settingsRepository.resizeWidget(hostId, anchor, spanX, spanY)) {
                widgetHost.resize(hostId, spanX, spanY)
            }
        }
    }

    /**
     * Enters or leaves the home screen's edit mode. Anything that takes the user
     * somewhere else - a settings screen, a picked widget - leaves it too.
     */
    fun setEditing(editing: Boolean) {
        _state.update { it.copy(editing = editing, selectedWidget = NO_WIDGET) }
    }

    private fun needsIcons(previous: LauncherSettings, current: LauncherSettings): Boolean =
        previous.iconSize != current.iconSize ||
            previous.iconShape != current.iconShape ||
            previous.iconPack != current.iconPack ||
            previous.hiddenApps != current.hiddenApps

    private fun reloadIcons() {
        val apps = _state.value.apps
        if (apps.isEmpty()) return

        iconJob?.cancel()
        iconJob = viewModelScope.launch { loadIcons(apps) }
    }

    /**
     * Icons are decoded one by one and published in small batches, so the
     * drawer appears immediately and fills in instead of blocking on 100+
     * drawables.
     *
     * Hiding an app takes it out of the drawer but not off the home screen, so
     * anything pinned there is decoded whether it is hidden or not: otherwise
     * the cell would keep its label and lose its icon.
     */
    private suspend fun loadIcons(apps: List<AppInfo>) {
        val sizePx = iconSizePx()
        val settings = _state.value.settings
        val pinned = settings.homeItems.values + settings.dockItems.values
        val wanted = apps.filter { it.key !in settings.hiddenApps || it.key in pinned }

        val decoded = HashMap<String, ImageBitmap>(wanted.size)
        wanted.forEachIndexed { index, app ->
            repository.icon(app, sizePx, settings.iconShape, settings.iconPack)
                ?.let { decoded[app.key] = it }

            if (index % ICON_BATCH_SIZE == ICON_BATCH_SIZE - 1) {
                _state.update { it.copy(icons = decoded.toMap()) }
            }
        }
        _state.update { it.copy(icons = decoded.toMap()) }
    }

    private fun iconSizePx(): Int {
        val dp = _state.value.settings.iconSize.sizeDp
        return with(getApplication<Application>().resources.displayMetrics) {
            (dp * density).roundToInt()
        }
    }

    /**
     * The settings screen is deliberately not a launchable activity - it is not
     * exported and has no LAUNCHER category, so it never shows up in other
     * launchers or in the system's app lists. Injecting it here is how it
     * reaches our own drawer.
     */
    private fun launcherSettingsEntry(): AppInfo {
        val app = getApplication<Application>()
        return AppInfo(
            key = SELF_SETTINGS_KEY,
            componentName = ComponentName(app, SettingsActivity::class.java),
            label = app.getString(R.string.settings_title),
            user = Process.myUserHandle(),
        )
    }

    private fun startIntent(intent: Intent, app: AppInfo) {
        val context = getApplication<Application>()
        val sent = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess

        if (!sent) {
            Toast.makeText(
                context,
                context.getString(R.string.action_failed, app.label),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private companion object {
        const val ICON_BATCH_SIZE = 8
        const val SELF_SETTINGS_KEY = "com.sdev.launcher:settings"
    }
}
