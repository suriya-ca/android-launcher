package com.sdev.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {

    @Test
    fun `direct substring matching works`() {
        assertTrue(SearchMatcher.matches("YouTube", "you"))
        assertTrue(SearchMatcher.matches("YouTube", "Tube"))
        assertFalse(SearchMatcher.matches("YouTube", "Vimeo"))
    }

    @Test
    fun `acronym matching works`() {
        assertTrue(SearchMatcher.matches("Play Store", "ps"))
        assertTrue(SearchMatcher.matches("Play Store", "PS"))
        assertTrue(SearchMatcher.matches("YouTube Music", "ym"))
    }

    @Test
    fun `fuzzy subsequence matching works`() {
        assertTrue(SearchMatcher.matches("Telegram", "tlg"))
        assertTrue(SearchMatcher.matches("Telegram", "trm"))
        assertFalse(SearchMatcher.matches("Telegram", "xyz"))
    }

    @Test
    fun `T9 dial pad matching works`() {
        // G-O-O-G-L-E -> 4-6-6-4-5-3
        assertTrue(SearchMatcher.matches("Google", "466453", enableT9 = true))
        assertTrue(SearchMatcher.matches("Google", "466", enableT9 = true))
        assertFalse(SearchMatcher.matches("Google", "466", enableT9 = false))

        // P-H-O-N-E -> 7-4-6-6-3
        assertTrue(SearchMatcher.matches("Phone", "7466", enableT9 = true))
    }

    @Test
    fun `an empty query matches every app`() {
        assertTrue(SearchMatcher.matches("Anything", ""))
        assertTrue(SearchMatcher.matches("Anything", "   "))
    }
}
