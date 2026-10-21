package com.sdev.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One DataStore for the whole process, so the launcher and the settings screen
 * always see the same values and a change made in one shows up in the other
 * immediately.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings",
)

/** Reads and writes [LauncherSettings] to disk. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore
    private val packageManager = context.applicationContext.packageManager

    val settings: Flow<LauncherSettings> = dataStore.data.map { prefs ->
        val dockCount = dockSlotsOf(prefs)
        val pages = homePages(prefs)
        val layout = homeLayoutOf(prefs)

        LauncherSettings(
            drawerColumns = (prefs[KEY_DRAWER_COLUMNS] ?: LauncherSettings.DEFAULT_DRAWER_COLUMNS)
                .coerceIn(3, 6),
            drawerStyle = prefs.enumOr(KEY_DRAWER_STYLE, DrawerStyle.PAGES),
            iconSize = prefs.enumOr(KEY_ICON_SIZE, IconSize.MEDIUM),
            showLabels = prefs[KEY_SHOW_LABELS] ?: true,
            showSearchBar = prefs[KEY_SHOW_SEARCH_BAR] ?: true,
            sortOrder = prefs.enumOr(KEY_SORT_ORDER, SortOrder.ALPHABETICAL),
            themeMode = prefs.enumOr(KEY_THEME_MODE, ThemeMode.SYSTEM),
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            iconShape = prefs.enumOr(KEY_ICON_SHAPE, IconShape.SYSTEM),
            iconPack = prefs[KEY_ICON_PACK]?.takeIf { it.isNotEmpty() },
            homeColumns = homeColumnsOf(prefs),
            homeRows = homeRowsOf(prefs),
            dockSlots = dockCount,
            showDock = prefs[KEY_SHOW_DOCK] ?: true,
            showNotificationBadges = prefs[KEY_SHOW_NOTIFICATION_BADGES] ?: true,
            enableT9Search = prefs[KEY_ENABLE_T9_SEARCH] ?: false,
            swipeDownNotifications = prefs[KEY_SWIPE_NOTIFICATIONS] ?: true,
            hiddenApps = prefs[KEY_HIDDEN_APPS].orEmpty(),
            homeItems = layout.cells,
            folders = layout.folders,
            homePages = pages,
            mainHomePage = mainHomePage(prefs),
            widgets = widgetsOf(prefs),
            dockItems = decodeSlots(prefs[KEY_DOCK_ITEMS], dockCount),
        )
    }

    suspend fun setDrawerColumns(columns: Int) = write { it[KEY_DRAWER_COLUMNS] = columns }

    suspend fun setDrawerStyle(style: DrawerStyle) = write { it[KEY_DRAWER_STYLE] = style.name }

    suspend fun setIconSize(size: IconSize) = write { it[KEY_ICON_SIZE] = size.name }

    suspend fun setShowLabels(show: Boolean) = write { it[KEY_SHOW_LABELS] = show }

    suspend fun setShowSearchBar(show: Boolean) = write { it[KEY_SHOW_SEARCH_BAR] = show }

    suspend fun setSortOrder(order: SortOrder) = write { it[KEY_SORT_ORDER] = order.name }

    suspend fun setThemeMode(mode: ThemeMode) = write { it[KEY_THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = write { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setIconShape(shape: IconShape) = write { it[KEY_ICON_SHAPE] = shape.name }

    suspend fun setIconPack(packageName: String?) =
        write { it[KEY_ICON_PACK] = packageName.orEmpty() }

    suspend fun setShowDock(enabled: Boolean) = write { it[KEY_SHOW_DOCK] = enabled }

    suspend fun setDockSlots(slots: Int) = write { prefs ->
        val validSlots = slots.coerceIn(
            LauncherSettings.DOCK_SLOT_CHOICES.first(),
            LauncherSettings.DOCK_SLOT_CHOICES.last(),
        )
        prefs[KEY_DOCK_SLOTS] = validSlots
        // Trim any dock items that exceed the new capacity
        val current = decodeSlots(prefs[KEY_DOCK_ITEMS], LauncherSettings.DOCK_SLOT_CHOICES.last())
        val trimmed = current.filterKeys { it in 0 until validSlots }
        prefs[KEY_DOCK_ITEMS] = encodeSlots(trimmed)
    }

    suspend fun setShowNotificationBadges(enabled: Boolean) =
        write { it[KEY_SHOW_NOTIFICATION_BADGES] = enabled }

    suspend fun setEnableT9Search(enabled: Boolean) =
        write { it[KEY_ENABLE_T9_SEARCH] = enabled }

    suspend fun setSwipeDownNotifications(enabled: Boolean) =
        write { it[KEY_SWIPE_NOTIFICATIONS] = enabled }

    suspend fun setAppHidden(key: String, hidden: Boolean) = write { prefs ->
        val current = prefs[KEY_HIDDEN_APPS].orEmpty()
        prefs[KEY_HIDDEN_APPS] = if (hidden) current + key else current - key
    }

    suspend fun setHomeGrid(columns: Int, rows: Int): List<Int> {
        val refitDropped = mutableListOf<Int>()

        val released = writeAndRelease { prefs ->
            val newColumns = columns.coerceIn(4, 5)
            val newRows = rows.coerceIn(
                LauncherSettings.HOME_MIN_ROWS,
                LauncherSettings.HOME_MAX_ROWS,
            )
            val oldColumns = homeColumnsOf(prefs)
            val oldRows = homeRowsOf(prefs)

            if (newRows != oldRows || newColumns != oldColumns) {
                // The cells are numbered on the grid in use, so they have to be
                // renumbered before the new grid is written down. Writing it
                // first would make the file look as if it had always been on
                // the new grid, and the icons would jump across the pages.
                val items = remapHomeCells(
                    items = homeItemsOf(prefs),
                    fromRows = oldRows,
                    toRows = newRows,
                    fromColumns = oldColumns,
                    toColumns = newColumns,
                )
                prefs[KEY_HOME_ITEMS] = encodeHomeItems(items)
                // A folder whose cell did not survive the change is not on the
                // screen any more, and is not written down.
                prefs[KEY_FOLDERS] = encodeFolders(liveFolders(foldersOf(prefs), items))

                val refit = refitWidgets(
                    widgets = widgetsOf(prefs),
                    icons = items.keys,
                    fromRows = oldRows,
                    toRows = newRows,
                    fromColumns = oldColumns,
                    toColumns = newColumns,
                )
                prefs[KEY_WIDGETS] = encodeWidgets(refit.widgets)
                refitDropped += refit.dropped
            }

            prefs[KEY_HOME_COLUMNS] = newColumns
            prefs[KEY_HOME_ROWS] = newRows
        }

        return released + refitDropped
    }

    /** Says which page the launcher should open on and return to. */
    suspend fun setMainHomePage(page: Int) = write { prefs ->
        prefs[KEY_MAIN_HOME_PAGE] = page.coerceIn(0, homePages(prefs) - 1)
    }

    /**
     * Puts the app [key] on the home screen cell [cell], taking it from
     * wherever it was.
     *
     * What the cell was holding decides what happens: an empty cell takes the
     * app, an app makes a folder of the two, and a folder takes the app in.
     *
     * [from] says where the app was, so that it can be taken off there: the
     * drawer, a home cell, a dock slot, or a folder.
     *
     * Returns the cell the app ended up on, or null when nothing happened.
     */
    suspend fun dropAppOnCell(key: String, cell: Int, from: AppPlace): Int? {
        var landed: Int? = null

        mutateSlots { _, _, home, folders, dock, _ ->
            val layout = HomeLayout(home, folders)
            val target = layout.cells[cell]

            // Dropping an app on the cell it is already in does nothing: it
            // must not be made into a folder of itself.
            if (target is HomeItem.App && target.key == key) return@mutateSlots

            // The app is on the home screen now, so it is not in the dock as
            // well: an app is in one place at a time.
            if (from is AppPlace.Dock) {
                dock.remove(from.slot)
            } else {
                dock.entries.firstOrNull { it.value == key }?.let { dock.remove(it.key) }
            }

            val updated = layout
                .withoutAppAt(from, key)
                .withAppDroppedOnCell(key, cell, newFolderId())

            applyHomeLayout(home, folders, updated)
            landed = cell
        }

        return landed
    }

    /**
     * Puts the app [key] in the dock, taking it from wherever it was.
     *
     * The dock holds apps only. An app that is already in the dock trades
     * places with the one under the finger, which is how the dock has always
     * been rearranged; an app from anywhere else goes into the nearest free
     * slot, so that nothing already there is thrown out.
     *
     * Returns the dock slot the app landed in, or null when the dock is full
     * and there was nowhere for it to go.
     */
    suspend fun dropAppInDock(key: String, slot: Int?, from: AppPlace): Int? {
        var landed: Int? = null

        mutateSlots { _, dockCapacity, home, folders, dock, _ ->
            if (from is AppPlace.Dock) {
                // Two apps in the dock trade places.
                val wanted = slot ?: return@mutateSlots
                if (from.slot == wanted) return@mutateSlots

                val moving = dock.remove(from.slot) ?: return@mutateSlots
                val displaced = dock[wanted]
                if (displaced == null) dock.remove(wanted) else dock[wanted] = displaced
                dock[from.slot] = moving

                landed = wanted
                return@mutateSlots
            }

            val target = findFreeCell(dock.keys.toSet(), dockCapacity, slot) ?: return@mutateSlots

            val layout = HomeLayout(home, folders)
            val cleared = layout.withoutAppAt(from, key)

            applyHomeLayout(home, folders, cleared)
            dock[target] = key
            landed = target
        }

        return landed
    }

    /**
     * Moves the folder [folderId] to the home screen cell [cell].
     *
     * What the cell was holding decides what happens: an empty cell takes the
     * folder, an app joins the folder, and another folder is merged into it.
     *
     * Returns the cell the folder ended up on.
     */
    suspend fun dropFolderOnHomeCell(folderId: String, cell: Int): Int? {
        var landed: Int? = null

        mutateSlots { _, _, home, folders, _, _ ->
            val layout = HomeLayout(home, folders)
            if (layout.cellOfFolder(folderId) == null) return@mutateSlots

            val updated = layout.withFolderDroppedOnCell(folderId, cell)
            applyHomeLayout(home, folders, updated)
            landed = cell
        }

        return landed
    }

    /**
     * Moves the app at [from] inside the folder [folderId] to [to], so that
     * the apps in a folder can be put in a different order.
     */
    suspend fun moveFolderApp(folderId: String, from: Int, to: Int) =
        mutateSlots { _, _, home, folders, _, _ ->
            val updated = HomeLayout(home, folders).withFolderMemberMoved(folderId, from, to)
            applyHomeLayout(home, folders, updated)
        }

    /** Calls the folder [folderId] [name]. */
    suspend fun renameFolder(folderId: String, name: String) =
        mutateSlots { _, _, home, folders, _, _ ->
            val updated = HomeLayout(home, folders).withFolderNamed(folderId, name)
            applyHomeLayout(home, folders, updated)
        }

    /**
     * Takes the folder [folderId] off the home screen. The apps in it are left
     * installed and go back to being apps in the drawer.
     */
    suspend fun removeFolder(folderId: String) =
        mutateSlots { _, _, home, folders, _, _ ->
            val updated = HomeLayout(home, folders).withoutFolder(folderId)
            applyHomeLayout(home, folders, updated)
        }

    /**
     * Puts the app [key] into the folder [folderId], taking it from wherever
     * it was.
     *
     * The app is in one place at a time, so it leaves the cell, the dock slot
     * or the folder it was in.
     */
    suspend fun addAppToFolder(key: String, folderId: String) =
        mutateSlots { _, _, home, folders, dock, _ ->
            val layout = HomeLayout(home, folders)
            if (layout.folders[folderId] == null) return@mutateSlots

            // The app is in the folder now, so it is not in the dock as well.
            dock.entries.firstOrNull { it.value == key }?.let { dock.remove(it.key) }

            val moved = layout.withAppMovedIntoFolder(folderId, key)

            applyHomeLayout(home, folders, moved)
        }

    /**
     * Takes the app [key] out of the folder [folderId] and puts it on the home
     * screen near the folder, so that it is still on the screen and is simply
     * not in the folder any more.
     *
     * Returns the cell it landed on, or null when the home screen had no room
     * and the app went back to being an app in the drawer.
     */
    suspend fun removeAppFromFolder(folderId: String, key: String): Int? {
        var landed: Int? = null

        mutateSlots { grid, _, home, folders, _, widgets ->
            val layout = HomeLayout(home, folders)
            val folder = layout.folders[folderId] ?: return@mutateSlots
            if (key !in folder.appKeys) return@mutateSlots

            val cleared = layout.withoutAppInFolder(folderId, key)
            val near = cleared.cellOfFolder(folderId) ?: cleared.cellOfApp(key)
            val occupied = cleared.cells.keys + widgets.values.flatMap { it.cellsIn(grid) }
            val cell = findFreeCell(occupied.toSet(), grid.capacity, near)

            val updated = if (cell == null) cleared else cleared.withAppAt(key, cell)
            applyHomeLayout(home, folders, updated)
            landed = cell
        }

        return landed
    }

    /**
     * Takes the app [key] out of the folder [folderId] and off the home screen
     * altogether: it goes back to being an app in the drawer.
     */
    suspend fun unpinFolderApp(folderId: String, key: String) =
        mutateSlots { _, _, home, folders, _, _ ->
            val layout = HomeLayout(home, folders)
            val folder = layout.folders[folderId] ?: return@mutateSlots
            if (key !in folder.appKeys) return@mutateSlots

            val cleared = layout.withoutAppInFolder(folderId, key)
            applyHomeLayout(home, folders, cleared)
        }

    /** Empties one home screen cell. */
    suspend fun clearHomeItem(area: HomeArea, index: Int) = mutateSlots { _, _, home, folders, dock, _ ->
        when (area) {
            HomeArea.HOME -> {
                val folder = (home[index] as? HomeItem.Folder)?.id
                home.remove(index)
                // A folder that is taken off the screen goes with it: the apps
                // in it are left installed, in the drawer.
                if (folder != null) folders.remove(folder)
            }

            HomeArea.DOCK -> dock.remove(index)
        }
    }

    /**
     * Puts [key] into [area] and returns the cell it landed in, or null when
     * there is no room left or the app is already there.
     *
     * [preferred] is the cell the user aimed at. It is used when free,
     * otherwise the nearest free cell from there, so that dropping an icon on a
     * full part of the grid still lands it close to the finger. [pages] is how
     * much of the home screen exists so far: nothing is placed beyond it.
     */
    suspend fun addHomeItem(
        key: String,
        area: HomeArea,
        preferred: Int? = null,
        pages: Int = LauncherSettings.HOME_PAGE_LIMIT,
    ): Int? {
        var placed: Int? = null

        mutateSlots { grid, dockCapacity, home, folders, dock, widgets ->
            val already = if (area == HomeArea.HOME) {
                home.values.any { (it as? HomeItem.App)?.key == key } ||
                    folders.values.any { key in it.appKeys }
            } else {
                dock.containsValue(key)
            }
            if (already) return@mutateSlots

            val capacity = if (area == HomeArea.HOME) {
                (pages * grid.cellCount).coerceIn(grid.cellCount, grid.capacity)
            } else {
                dockCapacity
            }

            // Cells a widget covers are not places an icon can go.
            val occupied = if (area == HomeArea.HOME) {
                home.keys + widgets.values.flatMap { it.cellsIn(grid) }
            } else {
                dock.keys.toSet()
            }

            val slot = findFreeCell(occupied, capacity, preferred)
            if (slot != null) {
                if (area == HomeArea.HOME) {
                    home[slot] = HomeItem.App(key)
                } else {
                    dock[slot] = key
                }
                placed = slot
            }
        }

        return placed
    }

    /**
     * Puts an empty page at [at], moving the pages after it along. Nothing is
     * lost: the cells are renumbered, for icons and widgets alike.
     */
    suspend fun insertHomePage(at: Int) = write { prefs ->
        val pages = homePages(prefs)
        if (pages >= LauncherSettings.HOME_PAGE_LIMIT) return@write

        val insert = at.coerceIn(0, pages)
        val main = mainHomePage(prefs)
        val grid = gridOf(prefs)
        val first = insert * grid.cellCount
        val home = homeItemsOf(prefs)
        val widgets = widgetsOf(prefs)

        prefs[KEY_HOME_ITEMS] = encodeHomeItems(shiftCellsFrom(home, first, grid.cellCount))
        prefs[KEY_WIDGETS] = encodeWidgets(
            shiftCellsFrom(widgets, first, grid.cellCount).mapValues { (cell, widget) ->
                widget.at(cell)
            },
        )
        prefs[KEY_HOME_PAGES] = pages + 1
        prefs[KEY_MAIN_HOME_PAGE] = mainPageAfterInsert(main, insert)
    }

    /**
     * Takes page [page] away, everything on it with it, and closes the gap it
     * leaves behind. Returns the widgets that went, so that the system can be
     * told to let go of them.
     *
     * The last page is never taken away: a home screen with no pages is not a
     * home screen.
     */
    suspend fun removeHomePage(page: Int): List<Int> {
        var released = emptyList<Int>()

        val dropped = writeAndRelease { prefs ->
            val pages = homePages(prefs)
            if (pages <= 1 || page !in 0 until pages) return@writeAndRelease

            val main = mainHomePage(prefs)
            val grid = gridOf(prefs)
            val cells = grid.cellsOfPage(page)
            val home = homeItemsOf(prefs)
            val widgets = widgetsOf(prefs)

            released = widgets.values.filter { it.pageIn(grid) == page }.map { it.hostId }

            val before = home.filterKeys { it !in cells }
            val after = grid.firstCellOfPage(page + 1)
            val gone = shiftCellsFrom(widgets.filterKeys { it !in cells }, after, -grid.cellCount)

            val shifted = shiftCellsFrom(before, after, -grid.cellCount)
            prefs[KEY_HOME_ITEMS] = encodeHomeItems(shifted)
            // A folder that was on the page that went goes with it.
            prefs[KEY_FOLDERS] = encodeFolders(liveFolders(foldersOf(prefs), shifted))
            prefs[KEY_WIDGETS] = encodeWidgets(
                gone.mapValues { (cell, widget) -> widget.at(cell) },
            )
            prefs[KEY_HOME_PAGES] = pages - 1
            prefs[KEY_MAIN_HOME_PAGE] = mainPageAfterRemove(
                mainPage = main,
                page = page,
                pages = pages,
            )
        }

        return dropped + released
    }

    /**
     * Puts a widget on the home screen: at [preferred] when it fits there,
     * otherwise at the first place it fits that is clear of apps and of other
     * widgets. Returns the cell it landed on, or null when there is no room.
     */
    suspend fun addWidget(
        hostId: Int,
        provider: String,
        spanX: Int,
        spanY: Int,
        preferred: Int? = null,
        pages: Int = LauncherSettings.HOME_PAGE_LIMIT,
    ): Int? {
        var placed: Int? = null

        mutateWidgets { grid, home, widgets ->
            val limit = (pages * grid.cellCount).coerceAtMost(grid.capacity)

            val occupied = home.keys + widgets.values.flatMap { it.cellsIn(grid) }
            val cell = findFreeWidgetCell(grid, occupied, spanX, spanY, preferred)
                ?.takeIf { it < limit }

            if (cell != null) {
                widgets[cell] = WidgetPlacement(hostId, provider, cell, spanX, spanY)
                placed = cell
            }
        }

        return placed
    }

    /**
     * Moves the widget anchored at [from] to [to], when it fits there. Returns
     * whether it moved.
     */
    suspend fun moveWidget(from: Int, to: Int): Boolean {
        var moved = false

        mutateWidgets { grid, home, widgets ->
            val widget = widgets[from] ?: return@mutateWidgets
            if (from == to) return@mutateWidgets

            val candidate = widget.at(to)
            if (!candidate.inside(grid)) return@mutateWidgets

            val cells = candidate.cellsIn(grid)
            if (cells.any { it in home }) return@mutateWidgets
            if (widgets.values.any { it.hostId != widget.hostId && it.cellsIn(grid).any { cell -> cell in cells } }) {
                return@mutateWidgets
            }

            widgets.remove(from)
            widgets[to] = candidate
            moved = true
        }

        return moved
    }

    /** Takes the widget anchored at [cell] off the home screen. */
    suspend fun removeWidget(cell: Int) = mutateWidgets { _, _, widgets ->
        widgets.remove(cell)
    }

    /**
     * Moves and resizes a widget in one step: [anchor] is where its top left
     * corner goes and the spans are how much room it takes. Returns whether it
     * fitted there.
     */
    suspend fun resizeWidget(hostId: Int, anchor: Int, spanX: Int, spanY: Int): Boolean {
        var applied = false

        mutateWidgets { grid, home, widgets ->
            val entry = widgets.entries.firstOrNull { it.value.hostId == hostId }
                ?: return@mutateWidgets

            val candidate = entry.value.copy(cell = anchor, spanX = spanX, spanY = spanY)
            if (!candidate.inside(grid)) return@mutateWidgets

            val cells = candidate.cellsIn(grid)
            if (cells.any { it in home }) return@mutateWidgets
            if (widgets.values.any { it.hostId != hostId && it.cellsIn(grid).any { placed -> placed in cells } }) {
                return@mutateWidgets
            }

            // Widgets are keyed by the cell they hang from, so resizing one from
            // its top left corner is a re-key.
            widgets.remove(entry.key)
            widgets[anchor] = candidate
            applied = true
        }

        return applied
    }

    /** Forgets widgets whose app is no longer installed. */
    suspend fun pruneWidgets() = mutateWidgets { _, _, widgets ->
        val present = HashMap<String, Boolean>()

        fun installed(packageName: String): Boolean = present.getOrPut(packageName) {
            runCatching { packageManager.getPackageInfo(packageName, 0) }.isSuccess
        }

        widgets.entries
            .filter { !installed(it.value.providerPackage) }
            .forEach { widgets.remove(it.key) }
    }

    /**
     * Forgets home screen cells whose app is no longer installed, and the apps
     * in folders that have gone the same way. A folder left with nothing in it
     * goes too.
     */
    suspend fun pruneHomeItems(installed: Set<String>) = mutateSlots { _, _, home, folders, dock, _ ->
        dock.entries
            .filter { it.value.isNotEmpty() && it.value !in installed }
            .forEach { dock.remove(it.key) }

        home.entries
            .filter { (_, item) -> item is HomeItem.App && item.key !in installed }
            .forEach { home.remove(it.key) }

        folders.values.toList().forEach { folder ->
            val remaining = folder.appKeys.filter { it in installed }
            if (remaining.size != folder.appKeys.size) {
                if (remaining.isEmpty()) {
                    folders.remove(folder.id)
                    home.entries
                        .filter { (_, item) -> (item as? HomeItem.Folder)?.id == folder.id }
                        .forEach { home.remove(it.key) }
                } else {
                    folders[folder.id] = folder.copy(appKeys = remaining)
                }
            }
        }
    }

    /**
     * Reads both slot maps and the widgets, hands them to [block] and writes
     * back whatever changed. Empty strings count as empty cells, so moving an
     * icon around is always a plain swap. Widgets are read only: they are what
     * an icon has to keep off.
     */
    private suspend fun mutateSlots(
        block: (
            grid: HomeGrid,
            dockCapacity: Int,
            home: MutableMap<Int, HomeItem>,
            folders: MutableMap<String, Folder>,
            dock: MutableMap<Int, String>,
            widgets: Map<Int, WidgetPlacement>,
        ) -> Unit,
    ) = write { prefs ->
        val layout = homeLayoutOf(prefs)
        val home = layout.cells.toMutableMap()
        val folders = layout.folders.toMutableMap()
        val dockCapacity = dockSlotsOf(prefs)
        val dock = decodeSlots(prefs[KEY_DOCK_ITEMS], dockCapacity).toMutableMap()
        val widgets = widgetsOf(prefs)
        val homeBefore = home.toString()
        val foldersBefore = folders.toString()
        val dockBefore = dock.toString()

        block(gridOf(prefs), dockCapacity, home, folders, dock, widgets)

        if (home.toString() != homeBefore) prefs[KEY_HOME_ITEMS] = encodeHomeItems(home)
        if (folders.toString() != foldersBefore) prefs[KEY_FOLDERS] = encodeFolders(folders)
        if (dock.toString() != dockBefore) prefs[KEY_DOCK_ITEMS] = encodeSlots(dock)
    }

    /**
     * Runs [block] over the store. Cells are read and written in the numbering
     * of the grid in use, whatever the file itself is numbered on, so the file is
     * brought up to date both before [block] runs and again afterwards in case
     * [block] is the thing that changed the grid.
     */
    private suspend fun write(block: (MutablePreferences) -> Unit) {
        writeAndRelease(block)
    }

    /**
     * As [write], but handing back the host ids of any widgets the grid in use
     * left no room for, so that the system can be told to let go of them.
     */
    private suspend fun writeAndRelease(block: (MutablePreferences) -> Unit): List<Int> {
        val released = mutableListOf<Int>()

        dataStore.edit { prefs ->
            released += renumberCells(prefs)
            block(prefs)
            released += renumberCells(prefs)
        }

        return released
    }

    /**
     * Reads the home screen cells - which widgets have to avoid, but never
     * change - and the widgets, hands both to [block], and writes the widgets
     * back if they changed.
     */
    private suspend fun mutateWidgets(
        block: (grid: HomeGrid, home: Map<Int, HomeItem>, widgets: MutableMap<Int, WidgetPlacement>) -> Unit,
    ) = write { prefs ->
        val home = homeItemsOf(prefs)
        val widgets = widgetsOf(prefs).toMutableMap()
        val before = widgets.toString()

        block(gridOf(prefs), home, widgets)

        if (widgets.toString() != before) prefs[KEY_WIDGETS] = encodeWidgets(widgets)
    }

    private fun dockSlotsOf(prefs: Preferences): Int =
        (prefs[KEY_DOCK_SLOTS] ?: LauncherSettings.DEFAULT_DOCK_SLOTS).coerceIn(
            LauncherSettings.DOCK_SLOT_CHOICES.first(),
            LauncherSettings.DOCK_SLOT_CHOICES.last(),
        )

    /** How many home screen pages exist, within the launcher's limits. */
    private fun homePages(prefs: Preferences): Int =
        (prefs[KEY_HOME_PAGES] ?: 1).coerceIn(1, LauncherSettings.HOME_PAGE_LIMIT)

    /** The main page, kept inside the pages that actually exist. */
    private fun mainHomePage(prefs: Preferences): Int =
        (prefs[KEY_MAIN_HOME_PAGE] ?: 0).coerceIn(0, homePages(prefs) - 1)

    /** How many rows the grid in use has. */
    private fun homeRowsOf(prefs: Preferences): Int =
        (prefs[KEY_HOME_ROWS] ?: LauncherSettings.HOME_ROWS).coerceIn(
            LauncherSettings.HOME_MIN_ROWS,
            LauncherSettings.HOME_MAX_ROWS,
        )

    /** How many columns the grid in use has. */
    private fun homeColumnsOf(prefs: Preferences): Int =
        (prefs[KEY_HOME_COLUMNS] ?: LauncherSettings.DEFAULT_HOME_COLUMNS).coerceIn(4, 5)

    /** The grid the launcher is working on. */
    private fun gridOf(prefs: Preferences): HomeGrid =
        HomeGrid(rows = homeRowsOf(prefs), columns = homeColumnsOf(prefs))

    /**
     * The home screen as it is: the cells, and the folders they point at.
     *
     * A folder whose cell is no longer there - because the grid changed and
     * the cell did not fit any more - is not a folder any more, and is left
     * out.
     */
    private fun homeLayoutOf(prefs: Preferences): HomeLayout {
        val items = homeItemsOf(prefs)
        return HomeLayout(items, liveFolders(foldersOf(prefs), items))
    }

    /**
     * The home screen cells in the file, numbered as the grid in use numbers
     * them.
     */
    private fun homeItemsOf(prefs: Preferences): Map<Int, HomeItem> {
        val storedR = storedRows(prefs)
        val storedC = storedColumns(prefs)

        return remapHomeCells(
            items = decodeHomeItems(
                prefs[KEY_HOME_ITEMS],
                HomeGrid(rows = storedR, columns = storedC).capacity,
            ),
            fromRows = storedR,
            toRows = homeRowsOf(prefs),
            fromColumns = storedC,
            toColumns = homeColumnsOf(prefs),
        )
    }

    /** The folders in the file. */
    private fun foldersOf(prefs: Preferences): Map<String, Folder> =
        decodeFolders(prefs[KEY_FOLDERS])

    /** The folders that are still on the screen: one whose cell has gone is not. */
    private fun liveFolders(
        folders: Map<String, Folder>,
        items: Map<Int, HomeItem>,
    ): Map<String, Folder> {
        val onScreen = items.values.filterIsInstance<HomeItem.Folder>().map { it.id }.toSet()
        return folders.filterKeys { it in onScreen }
    }

    /** The widgets in the file, numbered as the grid in use numbers them. */
    private fun widgetsOf(prefs: Preferences): Map<Int, WidgetPlacement> {
        val storedR = storedRows(prefs)
        val storedC = storedColumns(prefs)

        return refitWidgets(
            widgets = decodeWidgets(HomeGrid(rows = storedR, columns = storedC), prefs[KEY_WIDGETS]),
            icons = homeItemsOf(prefs).keys,
            fromRows = storedR,
            toRows = homeRowsOf(prefs),
            fromColumns = storedC,
            toColumns = homeColumnsOf(prefs),
        ).widgets
    }

    /** The height of the grid the cells in the file were numbered on. */
    private fun storedRows(prefs: Preferences): Int =
        prefs[KEY_HOME_ROWS] ?: LauncherSettings.LEGACY_HOME_ROWS

    /** The width of the grid the cells in the file were numbered on. */
    private fun storedColumns(prefs: Preferences): Int =
        prefs[KEY_HOME_COLUMNS] ?: LauncherSettings.LEGACY_HOME_COLUMNS

    /**
     * Writes the cells in the file in the numbering of the grid in use, with
     * every widget pulled back onto it. Does nothing once the two agree.
     *
     * Returns the host ids of widgets the grid had no room for.
     */
    private fun renumberCells(prefs: MutablePreferences): List<Int> {
        val rows = homeRowsOf(prefs)
        val columns = homeColumnsOf(prefs)
        if (prefs[KEY_HOME_ROWS] == rows && prefs[KEY_HOME_COLUMNS] == columns) return emptyList()

        val storedR = storedRows(prefs)
        val storedC = storedColumns(prefs)
        val refit = refitWidgets(
            widgets = decodeWidgets(HomeGrid(rows = storedR, columns = storedC), prefs[KEY_WIDGETS]),
            icons = homeItemsOf(prefs).keys,
            fromRows = storedR,
            toRows = rows,
            fromColumns = storedC,
            toColumns = columns,
        )

        val items = homeItemsOf(prefs)
        prefs[KEY_HOME_ITEMS] = encodeHomeItems(items)
        // A folder whose cell did not survive the change is not on the screen
        // any more, and is not written down.
        prefs[KEY_FOLDERS] = encodeFolders(liveFolders(foldersOf(prefs), items))
        prefs[KEY_WIDGETS] = encodeWidgets(refit.widgets)
        prefs[KEY_HOME_ROWS] = rows
        prefs[KEY_HOME_COLUMNS] = columns

        return refit.dropped
    }

    private inline fun <reified T : Enum<T>> Preferences.enumOr(
        key: Preferences.Key<String>,
        fallback: T,
    ): T = this[key]?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: fallback

    /** A name for a folder that has not been given one. */
    private fun newFolderId(): String = UUID.randomUUID().toString()

    private companion object {
        val KEY_DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val KEY_DRAWER_STYLE = stringPreferencesKey("drawer_style")
        val KEY_ICON_SIZE = stringPreferencesKey("icon_size")
        val KEY_SHOW_LABELS = booleanPreferencesKey("show_labels")
        val KEY_SHOW_SEARCH_BAR = booleanPreferencesKey("show_search_bar")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_ICON_SHAPE = stringPreferencesKey("icon_shape")
        val KEY_ICON_PACK = stringPreferencesKey("icon_pack")
        val KEY_SWIPE_NOTIFICATIONS = booleanPreferencesKey("swipe_notifications")
        val KEY_HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val KEY_HOME_ITEMS = stringPreferencesKey("home_items")
        val KEY_FOLDERS = stringPreferencesKey("home_folders")
        val KEY_HOME_PAGES = intPreferencesKey("home_pages")
        val KEY_MAIN_HOME_PAGE = intPreferencesKey("main_home_page")

        /**
         * How many rows the grid in use has, and how many columns.
         *
         * The file is written down in the numbering of the grid in use, and
         * this is where that numbering is kept.
         */
        val KEY_HOME_ROWS = intPreferencesKey("grid_rows")
        val KEY_HOME_COLUMNS = intPreferencesKey("grid_columns")

        val KEY_WIDGETS = stringPreferencesKey("widget_items")
        val KEY_DOCK_ITEMS = stringPreferencesKey("dock_items")
        val KEY_DOCK_SLOTS = intPreferencesKey("dock_slots")
        val KEY_SHOW_DOCK = booleanPreferencesKey("show_dock")
        val KEY_SHOW_NOTIFICATION_BADGES = booleanPreferencesKey("show_notification_badges")
        val KEY_ENABLE_T9_SEARCH = booleanPreferencesKey("enable_t9_search")
    }
}
