package com.sdev.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure arithmetic behind a folder's icon and its panel. */
class FolderIconTest {

    @Test
    fun `a folder shows up to four apps on its icon`() {
        assertEquals(0, folderPreviewCount(0))
        assertEquals(1, folderPreviewCount(1))
        assertEquals(4, folderPreviewCount(4))
        assertEquals(4, folderPreviewCount(9))
    }

    @Test
    fun `the count chip shows the apps the icon cannot hold`() {
        assertEquals(0, folderExtraCount(0))
        assertEquals(0, folderExtraCount(4))
        assertEquals(1, folderExtraCount(5))
        assertEquals(5, folderExtraCount(9))
    }

    @Test
    fun `a folder grows a row for every four apps`() {
        assertEquals(1, folderRowCount(1))
        assertEquals(1, folderRowCount(4))
        assertEquals(2, folderRowCount(5))
        assertEquals(5, folderRowCount(20))
        assertEquals(5, folderRowCount(100))
    }
}