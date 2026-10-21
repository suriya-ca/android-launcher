package com.sdev.launcher.ui

import com.sdev.launcher.data.AppInfo

/**
 * Enhanced search matching including:
 * 1. Substring case-insensitive match
 * 2. Initials/acronym match (e.g. "ps" matches "Play Store")
 * 3. Fuzzy subsequence match (letters appearing in order)
 * 4. T9 numeric keypad match (e.g. "466453" matches "Google")
 */
object SearchMatcher {

    private val T9_MAP = mapOf(
        '2' to charArrayOf('a', 'b', 'c'),
        '3' to charArrayOf('d', 'e', 'f'),
        '4' to charArrayOf('g', 'h', 'i'),
        '5' to charArrayOf('j', 'k', 'l'),
        '6' to charArrayOf('m', 'n', 'o'),
        '7' to charArrayOf('p', 'q', 'r', 's'),
        '8' to charArrayOf('t', 'u', 'v'),
        '9' to charArrayOf('w', 'x', 'y', 'z'),
    )

    /** Whether [app]'s label matches [query]; the label is all that is looked at. */
    fun matches(app: AppInfo, query: String, enableT9: Boolean = false): Boolean =
        matches(app.label, query, enableT9)

    /**
     * Whether the label [label] matches [query]. This is the whole of the
     * matching, separated from [AppInfo] so it can be checked without one.
     */
    fun matches(label: String, query: String, enableT9: Boolean = false): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true

        // 1. Direct contains
        if (label.contains(trimmed, ignoreCase = true)) return true

        // 2. Acronym match (e.g. "yt" -> "YouTube", "g" -> "Gmail")
        val words = label.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.size > 1) {
            val initials = words.map { it.first().lowercaseChar() }.joinToString("")
            if (initials.contains(trimmed.lowercase())) return true
        }

        // 3. Subsequence fuzzy match (for queries with >= 2 chars)
        if (trimmed.length >= 2 && matchesSubsequence(label.lowercase(), trimmed.lowercase())) {
            return true
        }

        // 4. T9 match if query is numeric
        if (enableT9 && trimmed.all { it.isDigit() }) {
            if (matchesT9(label, trimmed)) return true
        }

        return false
    }

    private fun matchesSubsequence(text: String, query: String): Boolean {
        var textIdx = 0
        var queryIdx = 0
        while (textIdx < text.length && queryIdx < query.length) {
            if (text[textIdx] == query[queryIdx]) {
                queryIdx++
            }
            textIdx++
        }
        return queryIdx == query.length
    }

    private fun matchesT9(label: String, digits: String): Boolean {
        val lower = label.lowercase()
        // Check if any word starts with or matches the T9 digits
        val words = lower.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        for (word in words) {
            if (wordMatchesT9Prefix(word, digits)) return true
        }
        return wordMatchesT9Prefix(lower, digits)
    }

    private fun wordMatchesT9Prefix(word: String, digits: String): Boolean {
        if (word.length < digits.length) return false
        for (i in digits.indices) {
            val validChars = T9_MAP[digits[i]] ?: return false
            if (word[i] !in validChars) return false
        }
        return true
    }
}
