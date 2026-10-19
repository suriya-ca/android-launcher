package com.sdev.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which of the two targets along the top of the screen a carried icon is let go
 * over.
 *
 * The important case is the drawer, where the answer is always "neither": the
 * two targets sit in the same band as the Home target an icon is carried onto to
 * bring the home screen out, so an icon out of the drawer let go up there must
 * not be taken as a request to remove it - which, for something that is not on
 * the screen yet, means nothing happens at all - or to uninstall its app.
 */
class DragTargetTest {

    private val remove = Rect(left = 90f, top = 30f, right = 260f, bottom = 120f)
    private val uninstall = Rect(left = 820f, top = 30f, right = 990f, bottom = 120f)

    private fun onRemove() = Offset(170f, 70f)
    private fun onUninstall() = Offset(900f, 70f)
    private fun onNeither() = Offset(540f, 900f)

    @Test
    fun `an icon out of the drawer is never let go over a target`() {
        assertNull(dragTargetFor(DragArea.DRAWER, remove, uninstall, onRemove()))
        assertNull(dragTargetFor(DragArea.DRAWER, remove, uninstall, onUninstall()))
        assertNull(dragTargetFor(DragArea.DRAWER, remove, uninstall, onNeither()))
    }

    @Test
    fun `an icon already on the screen can be removed or uninstalled`() {
        assertEquals(DragTarget.REMOVE, dragTargetFor(DragArea.HOME, remove, uninstall, onRemove()))
        assertEquals(
            DragTarget.UNINSTALL,
            dragTargetFor(DragArea.HOME, remove, uninstall, onUninstall()),
        )
        assertEquals(
            DragTarget.UNINSTALL,
            dragTargetFor(DragArea.DOCK, remove, uninstall, onUninstall()),
        )
    }

    @Test
    fun `letting go between the targets is not letting go on one`() {
        assertNull(dragTargetFor(DragArea.HOME, remove, uninstall, onNeither()))
    }

    @Test
    fun `a target that is not on the screen is not a target`() {
        assertNull(dragTargetFor(DragArea.HOME, null, null, onRemove()))
        assertNull(dragTargetFor(DragArea.DOCK, remove, null, onUninstall()))
    }
}
