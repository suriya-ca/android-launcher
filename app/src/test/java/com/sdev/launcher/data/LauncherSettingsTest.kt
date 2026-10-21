package com.sdev.launcher.data

import org.junit.Assert.assertFalse
import org.junit.Test

class LauncherSettingsTest {
    @Test
    fun `the home screen starts unlocked`() {
        assertFalse(LauncherSettings().homeLocked)
    }
}