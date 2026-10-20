package com.sdev.launcher.ui

import android.content.ComponentName
import android.os.UserHandle
import com.sdev.launcher.data.AppInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class SearchMatcherTest {

    /** UserHandle has no accessible constructor in JVM test stubs; mock it instead. */
    private val fakeUser: UserHandle = mock(UserHandle::class.java)

    private fun testApp(label: String, pkg: String = "com.test.app"): AppInfo =
        AppInfo(
            key = "$pkg/MainActivity@0",
            componentName = ComponentName(pkg, "MainActivity"),
            label = label,
            user = fakeUser,
        )

    @Test
    fun `direct substring matching works`() {
        val app = testApp("YouTube")
        assertTrue(SearchMatcher.matches(app, "you"))
        assertTrue(SearchMatcher.matches(app, "Tube"))
        assertFalse(SearchMatcher.matches(app, "Vimeo"))
    }

    @Test
    fun `acronym matching works`() {
        val app = testApp("Play Store")
        assertTrue(SearchMatcher.matches(app, "ps"))
        assertTrue(SearchMatcher.matches(app, "PS"))

        val youtubeMusic = testApp("YouTube Music")
        assertTrue(SearchMatcher.matches(youtubeMusic, "ym"))
    }

    @Test
    fun `fuzzy subsequence matching works`() {
        val app = testApp("Telegram")
        assertTrue(SearchMatcher.matches(app, "tlg"))
        assertTrue(SearchMatcher.matches(app, "trm"))
        assertFalse(SearchMatcher.matches(app, "xyz"))
    }

    @Test
    fun `T9 dial pad matching works`() {
        val app = testApp("Google")
        // G-O-O-G-L-E -> 4-6-6-4-5-3
        assertTrue(SearchMatcher.matches(app, "466453", enableT9 = true))
        assertTrue(SearchMatcher.matches(app, "466", enableT9 = true))
        assertFalse(SearchMatcher.matches(app, "466", enableT9 = false))

        val phone = testApp("Phone")
        // P-H-O-N-E -> 7-4-6-6-3
        assertTrue(SearchMatcher.matches(phone, "7466", enableT9 = true))
    }
}
