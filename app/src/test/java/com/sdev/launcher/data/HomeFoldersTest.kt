package com.sdev.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a home screen does when a folder is made, when an app joins one, and
 * when a folder is moved about.
 *
 * The rules are plain arithmetic over two maps, so they can be checked without
 * a device.
 */
class HomeFoldersTest {

    /** A home screen with two apps in it, and an empty cell. */
    private fun layout(): HomeLayout = HomeLayout(
        cells = mapOf(
            0 to HomeItem.App("app.a"),
            1 to HomeItem.App("app.b"),
        ),
        folders = emptyMap(),
    )

    @Test
    fun `an app dropped on an app makes a folder of the two`() {
        val after = layout().withAppDroppedOnCell(
            key = "app.c",
            cell = 0,
            newFolderId = "f1",
        )

        // The cell now holds the folder...
        assertEquals(HomeItem.Folder("f1"), after.cells[0])
        // ...which holds the app that was there and the one that was dropped.
        assertEquals(listOf("app.a", "app.c"), after.folders["f1"]?.appKeys)
        // The other app has not moved.
        assertEquals(HomeItem.App("app.b"), after.cells[1])
    }

    @Test
    fun `an app dropped on an empty cell is put there`() {
        val after = layout().withAppDroppedOnCell(
            key = "app.c",
            cell = 5,
            newFolderId = "f1",
        )

        assertEquals(HomeItem.App("app.c"), after.cells[5])
        assertTrue(after.folders.isEmpty())
    }

    @Test
    fun `an app dropped on a folder joins it`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a"))

        val after = withFolder.withAppDroppedOnCell(
            key = "app.c",
            cell = 4,
            newFolderId = "f2",
        )

        assertEquals(HomeItem.Folder("f1"), after.cells[4])
        assertEquals(listOf("app.a", "app.c"), after.folders["f1"]?.appKeys)
    }

    @Test
    fun `an app dropped on the cell it is already in stays where it is`() {
        val after = layout().withAppDroppedOnCell(
            key = "app.a",
            cell = 0,
            newFolderId = "f1",
        )

        assertEquals(HomeItem.App("app.a"), after.cells[0])
        assertTrue(after.folders.isEmpty())
    }

    @Test
    fun `an app taken out of a folder leaves it behind`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a", "app.b"))

        val after = withFolder.withoutAppInFolder("f1", "app.a")

        assertEquals(listOf("app.b"), after.folders["f1"]?.appKeys)
        assertEquals(HomeItem.Folder("f1"), after.cells[4])
    }

    @Test
    fun `a folder left with nothing is taken away and its cell freed`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a"))

        val after = withFolder.withoutAppInFolder("f1", "app.a")

        assertNull(after.cells[4])
        assertNull(after.folders["f1"])
    }

    @Test
    fun `a folder moved to an empty cell goes there`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a"))

        val after = withFolder.withFolderDroppedOnCell("f1", 7)

        assertEquals(HomeItem.Folder("f1"), after.cells[7])
        assertNull(after.cells[4])
    }

    @Test
    fun `a folder dropped on an app takes the app with it`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.c"))

        val after = withFolder.withFolderDroppedOnCell("f1", 0)

        // The app that was there joins the folder...
        assertEquals(listOf("app.c", "app.a"), after.folders["f1"]?.appKeys)
        // ...and the folder takes the app's place.
        assertEquals(HomeItem.Folder("f1"), after.cells[0])
        assertNull(after.cells[4])
    }

    @Test
    fun `a folder dropped on another folder joins it`() {
        val first = layout().withFolderAt("f1", 4, listOf("app.a"))
        val second = first.withFolderAt("f2", 5, listOf("app.b"))

        val after = second.withFolderDroppedOnCell("f2", 4)

        // The folder that was let go on keeps its place and takes the apps of
        // the one that was let go.
        assertEquals(HomeItem.Folder("f1"), after.cells[4])
        assertEquals(listOf("app.a", "app.b"), after.folders["f1"]?.appKeys)
        assertNull(after.folders["f2"])
        assertNull(after.cells[5])
    }

    @Test
    fun `the apps in a folder can be put in a different order`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a", "app.b", "app.c"))

        val after = withFolder.withFolderMemberMoved("f1", from = 2, to = 0)

        assertEquals(listOf("app.c", "app.a", "app.b"), after.folders["f1"]?.appKeys)
    }

    @Test
    fun `moving an app past the end of a folder puts it at the end`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a", "app.b"))

        val after = withFolder.withFolderMemberMoved("f1", from = 0, to = 99)

        assertEquals(listOf("app.b", "app.a"), after.folders["f1"]?.appKeys)
    }

    @Test
    fun `a folder can be given a name`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a"))

        val after = withFolder.withFolderNamed("f1", "Tools")

        assertEquals("Tools", after.folders["f1"]?.name)
    }

    @Test
    fun `a cell that is emptied takes its folder with it`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a"))

        val after = withFolder.withoutCell(4)

        assertNull(after.cells[4])
        assertNull(after.folders["f1"])
    }

    @Test
    fun `a cell is only freed when it is still holding the app`() {
        val after = layout().withoutAppAtCell("app.b", cell = 0)

        // The cell holds app.a, not app.b, so it is left alone.
        assertEquals(HomeItem.App("app.a"), after.cells[0])
    }

    @Test
    fun `the cell an app was on is freed when it is still holding it`() {
        val after = layout().withoutAppAtCell("app.a", cell = 0)

        assertNull(after.cells[0])
    }

    @Test
    fun `a folder is written down with its name and its apps`() {
        val folders = mapOf(
            "f1" to Folder(id = "f1", name = "Tools", appKeys = listOf("app.a", "app.b")),
        )

        assertEquals(folders, decodeFolders(encodeFolders(folders)))
    }

    @Test
    fun `a folder name with a line break in it does not become two folders`() {
        val folders = mapOf(
            "f1" to Folder(id = "f1", name = "Two\nlines", appKeys = listOf("app.a")),
        )

        val decoded = decodeFolders(encodeFolders(folders))

        assertEquals(1, decoded.size)
        assertEquals("Two lines", decoded["f1"]?.name)
    }

    @Test
    fun `a folder is written in the cell as a key of its own`() {
        val cells = mapOf(
            0 to HomeItem.App("app.a"),
            4 to HomeItem.Folder("f1"),
        )

        val written = encodeHomeItems(cells)

        // An app's key is a package name, a class and a user hash, and it never
        // starts with a colon, so the two can never be confused.
        assertEquals(
            "0=app.a\n4=:folder:f1",
            written,
        )
        assertEquals(cells, decodeHomeItems(written, capacity = 24))
    }

    @Test
    fun `an app key is never mistaken for a folder`() {
        val cells = mapOf(0 to HomeItem.App("com.folder.app/.Main@0"))

        assertEquals(cells, decodeHomeItems(encodeHomeItems(cells), capacity = 24))
    }

    @Test
    fun `an app is found where it is on the screen`() {
        val settings = LauncherSettings(
            homeItems = mapOf(
                0 to HomeItem.App("app.a"),
                4 to HomeItem.Folder("f1"),
            ),
            folders = mapOf("f1" to Folder("f1", "", listOf("app.b"))),
            dockItems = mapOf(2 to "app.c"),
        )

        assertEquals(AppPlace.Home(0), settings.placeOf("app.a"))
        assertEquals(AppPlace.InFolder("f1"), settings.placeOf("app.b"))
        assertEquals(AppPlace.Dock(2), settings.placeOf("app.c"))
        assertNull(settings.placeOf("app.d"))
    }

    @Test
    fun `an app in a folder is on the screen`() {
        val settings = LauncherSettings(
            homeItems = mapOf(4 to HomeItem.Folder("f1")),
            folders = mapOf("f1" to Folder("f1", "", listOf("app.b"))),
        )

        assertTrue(settings.holdsApp("app.b"))
        assertEquals(setOf("app.b"), settings.pinnedAppKeys)
    }

    @Test
    fun `a folder and its apps survive being written and read`() {
        val layout = HomeLayout(
            cells = mapOf(
                0 to HomeItem.App("app.a"),
                4 to HomeItem.Folder("f1"),
            ),
            folders = mapOf(
                "f1" to Folder("f1", "Tools", listOf("app.b", "app.c")),
            ),
        )

        val cells = decodeHomeItems(encodeHomeItems(layout.cells), capacity = 24)
        val folders = decodeFolders(encodeFolders(layout.folders))

        assertEquals(layout.cells, cells)
        assertEquals(layout.folders, folders)
    }

    @Test
    fun `an app taken off a home cell is gone from it`() {
        val after = layout()
            .withoutAppAt(AppPlace.Home(cell = 0), "app.a")
            .withAppDroppedOnCell("app.a", cell = 5, newFolderId = "f1")

        // The cell it was on is free, and it is on the new one.
        assertNull(after.cells[0])
        assertEquals(HomeItem.App("app.a"), after.cells[5])
    }

    @Test
    fun `an app taken out of a folder leaves it`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a", "app.b"))

        val after = withFolder
            .withoutAppAt(AppPlace.InFolder("f1"), "app.a")
            .withAppDroppedOnCell("app.a", cell = 5, newFolderId = "f2")

        assertEquals(listOf("app.b"), after.folders["f1"]?.appKeys)
        assertEquals(HomeItem.App("app.a"), after.cells[5])
    }

    @Test
    fun `an app taken out of the last folder takes the folder's place with it`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a"))

        val after = withFolder
            .withoutAppAt(AppPlace.InFolder("f1"), "app.a")
            .withAppDroppedOnCell("app.a", cell = 5, newFolderId = "f2")

        // The folder had only that app in it, so it is gone and its cell is
        // free for the app that came out of it.
        assertNull(after.folders["f1"])
        assertNull(after.cells[4])
        assertEquals(HomeItem.App("app.a"), after.cells[5])
    }

    @Test
    fun `an app from the drawer dropped on an app makes a folder`() {
        val after = layout()
            .withoutAppAt(AppPlace.Drawer, "app.c")
            .withAppDroppedOnCell("app.c", cell = 0, newFolderId = "f1")

        assertEquals(HomeItem.Folder("f1"), after.cells[0])
        assertEquals(listOf("app.a", "app.c"), after.folders["f1"]?.appKeys)
    }

    @Test
    fun `an app from the drawer dropped on a folder joins it`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.a"))

        val after = withFolder
            .withoutAppAt(AppPlace.Drawer, "app.c")
            .withAppDroppedOnCell("app.c", cell = 4, newFolderId = "f2")

        assertEquals(HomeItem.Folder("f1"), after.cells[4])
        assertEquals(listOf("app.a", "app.c"), after.folders["f1"]?.appKeys)
    }

    @Test
    fun `an app dropped on the cell it is in is left where it is`() {
        val after = layout()
            .withoutAppAt(AppPlace.Home(cell = 0), "app.a")
            .withAppDroppedOnCell("app.a", cell = 0, newFolderId = "f1")

        assertEquals(HomeItem.App("app.a"), after.cells[0])
        assertTrue(after.folders.isEmpty())
    }

    @Test
    fun `an app put into a folder leaves the cell it was on`() {
        val withFolder = layout().withFolderAt("f1", 4, listOf("app.b"))

        val after = withFolder.withAppMovedIntoFolder("f1", "app.a")

        // The cell the app was on is free...
        assertNull(after.cells[0])
        // ...and the app is in the folder with the one that was already there.
        assertEquals(listOf("app.b", "app.a"), after.folders["f1"]?.appKeys)
    }

    @Test
    fun `an app put into a folder leaves the folder it was in`() {
        // The app is in a folder and nowhere else.
        val first = HomeLayout(
            cells = mapOf(4 to HomeItem.Folder("f1")),
            folders = mapOf("f1" to Folder("f1", "", listOf("app.a", "app.b"))),
        )
        val second = first.withFolderAt("f2", 5, listOf("app.c"))

        val after = second.withAppMovedIntoFolder("f2", "app.a")

        assertEquals(listOf("app.b"), after.folders["f1"]?.appKeys)
        assertEquals(listOf("app.c", "app.a"), after.folders["f2"]?.appKeys)
    }

    /**
     * The maps a write is built in are the very maps a layout is made of, and
     * an operation that did not change one of them hands it back untouched. So
     * the result has to be copied out before the maps are cleared, or the write
     * clears what it is about to put back.
     */
    @Test
    fun `an app dropped on a folder keeps every other icon`() {
        val home = mutableMapOf<Int, HomeItem>(
            0 to HomeItem.Folder("f1"),
            1 to HomeItem.App("app.b"),
        )
        val folders = mutableMapOf("f1" to Folder("f1", "", listOf("app.a")))

        val layout = HomeLayout(home, folders)
        val updated = layout
            .withoutAppAt(AppPlace.Drawer, "app.c")
            .withAppDroppedOnCell("app.c", cell = 0, newFolderId = "f2")

        applyHomeLayout(home, folders, updated)

        assertEquals(HomeItem.Folder("f1"), home[0])
        assertEquals(HomeItem.App("app.b"), home[1])
        assertEquals(listOf("app.a", "app.c"), folders["f1"]?.appKeys)
    }

    @Test
    fun `an app dropped on an empty cell keeps the folders`() {
        val home = mutableMapOf<Int, HomeItem>(0 to HomeItem.Folder("f1"))
        val folders = mutableMapOf("f1" to Folder("f1", "", listOf("app.a")))

        val layout = HomeLayout(home, folders)
        val updated = layout
            .withoutAppAt(AppPlace.Drawer, "app.c")
            .withAppDroppedOnCell("app.c", cell = 5, newFolderId = "f2")

        applyHomeLayout(home, folders, updated)

        assertEquals(HomeItem.Folder("f1"), home[0])
        assertEquals(HomeItem.App("app.c"), home[5])
        assertEquals(setOf("f1"), folders.keys)
    }

    @Test
    fun `an app put into an open folder keeps the rest of the screen`() {
        val home = mutableMapOf<Int, HomeItem>(
            0 to HomeItem.App("app.b"),
            1 to HomeItem.Folder("f1"),
        )
        val folders = mutableMapOf("f1" to Folder("f1", "", listOf("app.a")))

        val updated = HomeLayout(home, folders)
            .withAppMovedIntoFolder("f1", "app.c")

        applyHomeLayout(home, folders, updated)

        assertEquals(HomeItem.App("app.b"), home[0])
        assertEquals(HomeItem.Folder("f1"), home[1])
        assertEquals(listOf("app.a", "app.c"), folders["f1"]?.appKeys)
    }

    @Test
    fun `a change that did nothing leaves the screen as it was`() {
        val home = mutableMapOf<Int, HomeItem>(0 to HomeItem.App("app.a"))
        val folders = mutableMapOf("f1" to Folder("f1", "", listOf("app.b")))

        applyHomeLayout(home, folders, HomeLayout(home, folders).withFolderNamed("missing", "x"))

        assertEquals(mapOf(0 to HomeItem.App("app.a")), home)
        assertEquals(setOf("f1"), folders.keys)
    }
}
