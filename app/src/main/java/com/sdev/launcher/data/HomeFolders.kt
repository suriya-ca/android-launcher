package com.sdev.launcher.data

/**
 * The home screen's contents: what each cell holds, and the folders those
 * cells point at.
 *
 * A folder is a cell like any other - it takes up a place on the grid and
 * holds a name and a list of apps - but it holds them without taking up a
 * cell each. Everything here is plain arithmetic over two maps, so it can be
 * worked out and checked without a device.
 */
data class HomeLayout(
    /** Cell index to what the cell holds. */
    val cells: Map<Int, HomeItem>,
    /** Folder id to the folder itself. */
    val folders: Map<String, Folder>,
) {
    /** The app in [cell], if that is what the cell holds. */
    fun appAt(cell: Int): String? = (cells[cell] as? HomeItem.App)?.key

    /** The folder in [cell], if that is what the cell holds. */
    fun folderAt(cell: Int): Folder? =
        (cells[cell] as? HomeItem.Folder)?.let { folders[it.id] }

    /** The cell holding the folder with [id]. */
    fun cellOfFolder(id: String): Int? = cells.entries
        .firstOrNull { (it.value as? HomeItem.Folder)?.id == id }
        ?.key

    /** The folder holding the app with [key]. */
    fun folderOfApp(key: String): Folder? = folders.values.firstOrNull { key in it.appKeys }

    /** The cell holding the app with [key]. */
    fun cellOfApp(key: String): Int? = cells.entries
        .firstOrNull { (it.value as? HomeItem.App)?.key == key }
        ?.key
}

/**
 * The home screen with the cell [cell] emptied.
 *
 * A cell holding a folder takes the folder with it: a folder that is taken off
 * the screen is not a folder any more, and the apps in it go back to being
 * apps in the drawer.
 */
fun HomeLayout.withoutCell(cell: Int): HomeLayout {
    val folder = (cells[cell] as? HomeItem.Folder)?.id

    return copy(
        cells = cells - cell,
        folders = if (folder == null) folders else folders - folder,
    )
}

/**
 * The home screen with the app [key] taken off it: out of its cell, or out of
 * the folder that was holding it.
 *
 * A folder left with nothing in it goes with it, and its cell is free again.
 */
fun HomeLayout.withoutApp(key: String): HomeLayout {
    val cell = cellOfApp(key)
    val folder = folderOfApp(key)

    return when {
        cell != null -> copy(cells = cells - cell)
        folder != null -> withoutAppInFolder(folder.id, key)
        else -> this
    }
}

/**
 * The home screen with the app [key] taken off the place [place] says it was
 * picked up from.
 *
 * The cell an app was on is freed only if it is still holding that app:
 * something may have moved it in the meantime, and freeing a cell that now
 * holds something else would take that away with it.
 */
fun HomeLayout.withoutAppAt(place: AppPlace, key: String): HomeLayout = when (place) {
    is AppPlace.Home -> withoutAppAtCell(key, place.cell)
    is AppPlace.InFolder -> withoutAppInFolder(place.folderId, key)
    is AppPlace.Dock -> this
    AppPlace.Drawer -> this
}

/**
 * The home screen with the app [key] taken off the cell [cell].
 *
 * The cell is only freed if it is still holding that app: something may have
 * moved it in the meantime, and freeing a cell that now holds something else
 * would take that away with it.
 */
fun HomeLayout.withoutAppAtCell(key: String, cell: Int): HomeLayout =
    if (appAt(cell) == key) withoutCell(cell) else this

/**
 * The home screen with the app [key] put into the cell [cell].
 *
 * The cell is expected to be empty: this is the move, not the drop that
 * decides what the cell was holding.
 */
fun HomeLayout.withAppAt(key: String, cell: Int): HomeLayout =
    copy(cells = cells + (cell to HomeItem.App(key)))

/**
 * The home screen with a new folder at [cell], holding the apps in [keys] in
 * the order they are given.
 */
fun HomeLayout.withFolderAt(id: String, cell: Int, keys: List<String>): HomeLayout =
    copy(
        cells = cells + (cell to HomeItem.Folder(id)),
        folders = folders + (id to Folder(id = id, name = "", appKeys = keys)),
    )

/**
 * The home screen with the app [key] added to the folder [folderId], at the
 * end of it.
 *
 * An app that is already in the folder is left where it is rather than
 * appearing twice.
 */
fun HomeLayout.withAppInFolder(folderId: String, key: String): HomeLayout {
    val folder = folders[folderId] ?: return this
    if (key in folder.appKeys) return this

    return copy(
        folders = folders + (folderId to folder.copy(appKeys = folder.appKeys + key)),
    )
}

/**
 * The home screen with the app [key] taken out of the folder [folderId].
 *
 * A folder that loses its last app is taken away with it, and its cell is
 * free again.
 */
fun HomeLayout.withoutAppInFolder(folderId: String, key: String): HomeLayout {
    val folder = folders[folderId] ?: return this
    val remaining = folder.appKeys.filterNot { it == key }
    if (remaining.size == folder.appKeys.size) return this

    return if (remaining.isEmpty()) {
        withoutFolder(folderId)
    } else {
        copy(folders = folders + (folderId to folder.copy(appKeys = remaining)))
    }
}

/** The home screen without the folder [id]: its cell is free again. */
fun HomeLayout.withoutFolder(id: String): HomeLayout {
    val cell = cellOfFolder(id)
    val cells = if (cell == null) cells else cells - cell

    return copy(cells = cells, folders = folders - id)
}

/**
 * The home screen with the app [key] put into the folder [folderId], taken
 * from wherever it was.
 *
 * The app is in one place at a time, so it leaves the cell and the folder it
 * was in. A folder left with nothing in it goes with it.
 */
fun HomeLayout.withAppMovedIntoFolder(folderId: String, key: String): HomeLayout {
    val folder = folders[folderId] ?: return this

    val cell = cellOfApp(key)
    val from = folderOfApp(key)?.id

    val cleared = when {
        cell != null -> copy(cells = cells - cell)
        else -> this
    }

    val without = if (from != null && from != folderId) {
        cleared.withoutAppInFolder(from, key)
    } else {
        cleared
    }

    return without.withAppInFolder(folderId, key)
}

/**
 * The home screen with the folder [id] called [name].
 */
fun HomeLayout.withFolderNamed(id: String, name: String): HomeLayout {
    val folder = folders[id] ?: return this
    return copy(folders = folders + (id to folder.copy(name = name)))
}

/**
 * The home screen with the app at [from] inside the folder [id] moved to
 * [to], so that the apps in the folder can be put in a different order.
 */
fun HomeLayout.withFolderMemberMoved(id: String, from: Int, to: Int): HomeLayout {
    val folder = folders[id] ?: return this
    if (from !in folder.appKeys.indices) return this

    val moved = folder.appKeys.toMutableList()
    val app = moved.removeAt(from)
    moved.add(to.coerceIn(0, moved.size), app)

    return copy(folders = folders + (id to folder.copy(appKeys = moved)))
}

/**
 * The home screen with the app [key] let go on the cell [cell].
 *
 * What happens depends on what the cell was holding:
 *
 *  - nothing: the app is put there;
 *  - an app: the two of them become a folder, at that cell;
 *  - a folder: the app joins that folder.
 *
 * The app is expected to have been taken off the screen already: this is what
 * a drop does to the cell it lands on, not where it came from.
 *
 * [newFolderId] names the folder that is made when there is one to make.
 */
fun HomeLayout.withAppDroppedOnCell(key: String, cell: Int, newFolderId: String): HomeLayout =
    when (val target = cells[cell]) {
        null -> withAppAt(key, cell)

        // Dropped on the app it already is: nothing to make a folder of.
        is HomeItem.App -> when (target.key) {
            key -> this
            else -> withFolderAt(
                id = newFolderId,
                cell = cell,
                keys = listOf(target.key, key),
            )
        }

        is HomeItem.Folder -> withAppInFolder(target.id, key)
    }

/**
 * The home screen with the folder [folderId] let go on the cell [cell].
 *
 * What happens depends on what the cell was holding:
 *
 *  - nothing: the folder moves there;
 *  - an app: the app joins the folder, and the folder takes the app's place;
 *  - another folder: the two folders become one, and the one that was let go
 *    on keeps its place.
 */
fun HomeLayout.withFolderDroppedOnCell(folderId: String, cell: Int): HomeLayout {
    val folder = folders[folderId] ?: return this
    val from = cellOfFolder(folderId)
    val target = cells[cell]

    // The folder is already on the cell it was let go on.
    if (from == cell) return this

    // The cell it is leaving is empty either way.
    val moved = if (from == null) this else copy(cells = cells - from)

    return when (target) {
        null -> moved.copy(cells = moved.cells + (cell to HomeItem.Folder(folderId)))

        is HomeItem.App -> {
            // The app joins the folder, and the folder takes the app's place.
            val withApp = moved.withAppInFolder(folderId, target.key)
            withApp.copy(cells = withApp.cells - cell + (cell to HomeItem.Folder(folderId)))
        }

        is HomeItem.Folder -> {
            // The folder that was let go joins the one it was let go on, and
            // the one that was there keeps its place.
            val targetFolder = moved.folders[target.id] ?: return this
            val merged = targetFolder.copy(appKeys = targetFolder.appKeys + folder.appKeys)

            moved.copy(
                cells = moved.cells - cell + (cell to HomeItem.Folder(target.id)),
                folders = moved.folders + (target.id to merged) - folderId,
            )
        }
    }
}

/**
 * Puts [updated] into the maps a caller is building its result in.
 *
 * The maps are copied out first, and that is not a detail: an operation that
 * did not change one of them hands that one back as it was given, so
 * [HomeLayout.cells] can be the very map [home] is, and [HomeLayout.folders]
 * the very map [folders] is. Clearing before reading would empty the result
 * with it - which is what used to happen when an app was dropped on a folder,
 * taking every icon on the home screen with it.
 */
fun applyHomeLayout(
    home: MutableMap<Int, HomeItem>,
    folders: MutableMap<String, Folder>,
    updated: HomeLayout,
) {
    val cells = updated.cells.toMap()
    val folderMap = updated.folders.toMap()

    home.clear()
    home.putAll(cells)
    folders.clear()
    folders.putAll(folderMap)
}

/**
 * How a home screen is written down.
 *
 * The cells are written as `index=key` lines, as they always were, and a folder
 * is written as a key of its own: `:folder:<id>`.
 *
 * The marker begins with a colon, which an app's key never does: a key is a
 * package name, a class and a user hash, and a package name is made of letters,
 * digits, underscores and dots. So the two can never be confused, whatever the
 * apps on the device are called.
 *
 * The folders themselves are written one per line, as
 * `id|name|key|key|key`: the name first, then the apps in the order they are
 * shown. A name cannot contain a line break, and an app key cannot contain a
 * bar, so a line is read back the way it was written.
 */
const val FOLDER_PREFIX = ":folder:"

fun encodeHomeItems(cells: Map<Int, HomeItem>): String = cells.entries
    .filter { item ->
        when (val value = item.value) {
            is HomeItem.App -> true
            is HomeItem.Folder -> value.id.isNotEmpty()
        }
    }
    .sortedBy { it.key }
    .joinToString("\n") { (cell, item) ->
        val key = when (item) {
            is HomeItem.App -> item.key
            is HomeItem.Folder -> FOLDER_PREFIX + item.id
        }
        "$cell=$key"
    }

fun decodeHomeItems(raw: String?, capacity: Int): Map<Int, HomeItem> =
    decodeSlots(raw, capacity).mapValues { (_, key) ->
        if (key.startsWith(FOLDER_PREFIX)) {
            HomeItem.Folder(key.removePrefix(FOLDER_PREFIX))
        } else {
            HomeItem.App(key)
        }
    }

/**
 * The folders, written one per line: the id, the name, and the apps in it.
 *
 * The name is written as it is, with a line break in it turned into a space,
 * because a name that ran over two lines would be read as two folders.
 */
fun encodeFolders(folders: Map<String, Folder>): String = folders.values
    .sortedBy { it.id }
    .joinToString("\n") { folder ->
        (listOf(folder.id, folder.name.replace("\n", " ")) + folder.appKeys)
            .joinToString("|")
    }

fun decodeFolders(raw: String?): Map<String, Folder> {
    if (raw.isNullOrEmpty()) return emptyMap()

    return raw.lineSequence().mapNotNull { line ->
        if (line.isEmpty()) return@mapNotNull null

        val parts = line.split("|")
        val id = parts[0]
        if (id.isEmpty()) return@mapNotNull null

        id to Folder(
            id = id,
            name = parts.getOrElse(1) { "" },
            appKeys = parts.drop(2).filter { it.isNotEmpty() },
        )
    }.toMap()
}
