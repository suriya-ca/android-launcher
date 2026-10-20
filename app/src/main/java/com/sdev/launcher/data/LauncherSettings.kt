package com.sdev.launcher.data

/** How the launcher picks its colours. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Size of the icons in the app drawer. */
enum class IconSize(val sizeDp: Float) {
    SMALL(44f),
    MEDIUM(52f),
    LARGE(60f),
}

/** Order of the app drawer. */
enum class SortOrder { ALPHABETICAL, REVERSE_ALPHABETICAL }

/** How the app drawer arranges the apps it holds. */
enum class DrawerStyle {
    /** Fixed pages of icons, swiped through horizontally. */
    PAGES,

    /** One long grid, scrolled vertically. */
    LIST,
}

/** The outline the launcher draws icons into. */
enum class IconShape {
    /** Leave icons alone: adaptive icons arrive already shaped by the system. */
    SYSTEM,
    CIRCLE,
    SQUIRCLE,
    ROUNDED_SQUARE,
    SQUARE,
    TEARDROP,
}

/** The two places the home screen keeps icons. */
enum class HomeArea { HOME, DOCK }

/**
 * The shape of the home screen: how many rows a page has, and everything that
 * follows from that.
 *
 * Columns, and the number of pages that may exist, are fixed; the rows are the
 * user's to choose, which is why a page's cell count and the sum of all the
 * cells are worked out here rather than being written down as constants. Every
 * piece of cell arithmetic takes a grid, so nothing can quietly assume the
 * height it was first written against.
 */
data class HomeGrid(
    val rows: Int,
    val columns: Int = LauncherSettings.DEFAULT_HOME_COLUMNS,
) {
    /** How many cells one page holds. */
    val cellCount: Int get() = columns * rows

    /** Every cell there is, across every page there can be. */
    val capacity: Int get() = LauncherSettings.HOME_PAGE_LIMIT * cellCount

    /** Which page a cell index sits on. */
    fun pageOf(index: Int): Int = index / cellCount

    /** Where a cell index sits within its own page. */
    fun cellOf(index: Int): Int = index % cellCount

    /** The first cell of [page]. */
    fun firstCellOfPage(page: Int): Int = page * cellCount

    /** Every cell of [page], in reading order. */
    fun cellsOfPage(page: Int): IntRange =
        firstCellOfPage(page) until (firstCellOfPage(page) + cellCount)
}

/**
 * Everything the user can configure about the launcher.
 *
 * The defaults describe a wallpapered page with a five-ish row drawer of
 * labelled apps on four columns, a search bar, wallpaper colours and the
 * system theme.
 */
data class LauncherSettings(
    val drawerColumns: Int = DEFAULT_DRAWER_COLUMNS,
    val drawerStyle: DrawerStyle = DrawerStyle.PAGES,
    val iconSize: IconSize = IconSize.MEDIUM,
    val showLabels: Boolean = true,
    val showSearchBar: Boolean = true,
    val sortOrder: SortOrder = SortOrder.ALPHABETICAL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val iconShape: IconShape = IconShape.SYSTEM,
    /** Package name of the icon pack in use, or null for the icons as shipped. */
    val iconPack: String? = null,
    /** How many columns the home screen grid has. */
    val homeColumns: Int = DEFAULT_HOME_COLUMNS,
    /** How many rows the home screen grid has. */
    val homeRows: Int = HOME_ROWS,
    /** How many slots the dock bar holds. */
    val dockSlots: Int = DEFAULT_DOCK_SLOTS,
    /** Whether the dock is shown at the bottom of the home screen. */
    val showDock: Boolean = true,
    /** Whether app icon notification dots or count badges are displayed. */
    val showNotificationBadges: Boolean = true,
    /** Whether T9 numeric dial-pad search is enabled in the app drawer. */
    val enableT9Search: Boolean = false,
    /** Whether a swipe down on the home screen opens the notification shade. */
    val swipeDownNotifications: Boolean = true,
    /** [AppInfo.key]s of apps that stay installed but hidden from the drawer. */
    val hiddenApps: Set<String> = emptySet(),
    /**
     * Home screen cells: cell index to [AppInfo.key]. Missing cells are empty.
     * Cells are numbered across every page - see [HOME_SLOT_CAPACITY].
     */
    val homeItems: Map<Int, String> = emptyMap(),
    /** How many home screen pages there are, swiped through left to right. */
    val homePages: Int = 1,
    /**
     * The page the launcher opens on, and the one the system Home action
     * returns to. It is a page in its own right; swiping to another page does
     * not move it.
     */
    val mainHomePage: Int = 0,
    /** Home screen widgets, keyed by the top left cell each one occupies. */
    val widgets: Map<Int, WidgetPlacement> = emptyMap(),
    /** Dock slots: slot index to [AppInfo.key]. Missing slots are empty. */
    val dockItems: Map<Int, String> = emptyMap(),
) {
    /** The grid the home screen is laid out on. */
    val grid: HomeGrid get() = HomeGrid(rows = homeRows, columns = homeColumns)

    /** The cell holding [key], if it is on the home screen at all. */
    fun cellOf(key: String): Pair<HomeArea, Int>? =
        homeItems.entries.firstOrNull { it.value == key }?.let { HomeArea.HOME to it.key }
            ?: dockItems.entries.firstOrNull { it.value == key }?.let { HomeArea.DOCK to it.key }

    /** True when [page] holds no icons, so it can be taken away again. */
    fun isEmptyHomePage(page: Int): Boolean {
        val shape = grid
        val cells = shape.cellsOfPage(page)
        return homeItems.keys.none { it in cells } &&
            widgets.values.none { it.pageIn(shape) == page }
    }

    /** Every cell a widget covers. Nothing else may be placed on those. */
    fun widgetCells(): Set<Int> {
        if (widgets.isEmpty()) return emptySet()

        val shape = grid
        val covered = HashSet<Int>()
        widgets.values.forEach { covered += it.cellsIn(shape) }
        return covered
    }

    /** The widget covering [index], if one does. */
    fun widgetAt(index: Int): WidgetPlacement? {
        val shape = grid
        return widgets[index] ?: widgets.values.firstOrNull { index in it.cellsIn(shape) }
    }

    /**
     * True when a [spanX] by [spanY] widget anchored at [cell] would fit: on the
     * grid, clear of icons and clear of every other widget.
     *
     * [ignoring] is the widget being resized, which must not count as being in
     * its own way.
     */
    fun widgetFits(cell: Int, spanX: Int, spanY: Int, ignoring: Int? = null): Boolean {
        val shape = grid
        if (!widgetFitsGrid(shape, cell, spanX, spanY)) return false

        val cells = widgetCells(shape, cell, spanX, spanY)
        if (homeItems.keys.any { it in cells }) return false

        return widgets.values.none {
            it.hostId != ignoring && it.cellsIn(shape).any { placed -> placed in cells }
        }
    }

    companion object {
        const val DEFAULT_DRAWER_COLUMNS = 4
        val DRAWER_COLUMN_CHOICES = listOf(4, 5)

        const val DEFAULT_HOME_COLUMNS = 4
        val HOME_COLUMN_CHOICES = listOf(4, 5)

        const val DEFAULT_DOCK_SLOTS = 5
        val DOCK_SLOT_CHOICES = listOf(3, 4, 5, 6, 7)

        /** The rows the home screen starts with. */
        const val HOME_ROWS = 8

        /** The row counts the user may choose between. */
        val HOME_ROW_CHOICES = listOf(5, 6, 7, 8, 9, 10)

        val HOME_MIN_ROWS = HOME_ROW_CHOICES.first()
        val HOME_MAX_ROWS = HOME_ROW_CHOICES.last()

        /**
         * The grid the launcher used before the rows became the user's to
         * choose.
         *
         * Cells are numbered across the pages, so a page that changed height
         * changes what every cell after the first page means. Cells written on
         * an older grid are renumbered as they are read, so that the things
         * already on the home screen stay where the user left them.
         */
        const val LEGACY_HOME_ROWS = 6
        const val LEGACY_HOME_COLUMNS = 4

        /** The most home screen pages the launcher will make room for. */
        const val HOME_PAGE_LIMIT = 6
    }
}
