package com.sdev.launcher.data

/**
 * What a home screen cell holds.
 *
 * A cell holds one thing: an app, or a folder of apps. The two are told apart
 * by the shape of the token they are written down as, so a cell that held an
 * app before folders existed still holds one after the upgrade.
 */
sealed interface HomeItem {

    /** A cell holding one app, named by the app's key. */
    data class App(val key: String) : HomeItem

    /** A cell holding a folder, named by the folder's id. */
    data class Folder(val id: String) : HomeItem
}

/**
 * A folder on the home screen: a name, and the apps in it in the order they
 * are shown.
 *
 * The name may be empty, which means the folder is called whatever the
 * launcher calls folders by default.
 */
data class Folder(
    val id: String,
    val name: String,
    val appKeys: List<String>,
)

/**
 * Where an app was picked up from.
 *
 * It says where the app has to be taken off when it is put somewhere else:
 * the drawer (where there is nothing to take off), a home cell, a dock slot,
 * or a folder.
 */
sealed interface AppPlace {

    /** Only in the drawer: it was never pinned to the screen. */
    data object Drawer : AppPlace

    /** On a home screen cell. */
    data class Home(val cell: Int) : AppPlace

    /** In the dock. */
    data class Dock(val slot: Int) : AppPlace

    /** Inside a folder. */
    data class InFolder(val folderId: String) : AppPlace
}
