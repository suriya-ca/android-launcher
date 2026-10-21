package com.sdev.launcher.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdev.launcher.ui.theme.AppTheme
import com.sdev.launcher.ui.theme.SystemBarsEffect

/**
 * The launcher's settings screen. It lives in the same APK as the launcher,
 * has no launcher icon of its own and is only reachable from the launcher's
 * home screen, so the launcher never lists itself in its own app drawer.
 */
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()

            AppTheme(
                themeMode = state.settings.themeMode,
                dynamicColor = state.settings.dynamicColor,
            ) {
                SystemBarsEffect(state.settings.themeMode)

                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    initialDestination = when (intent.getStringExtra(EXTRA_PAGE)) {
                        PAGE_HOME -> SettingsDestination.HOME
                        else -> SettingsDestination.MENU
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just allowed the notifications gesture in the
        // system settings.
        viewModel.refreshShadeState()
    }

    companion object {
        /** Names the settings page to open on, rather than always the menu. */
        const val EXTRA_PAGE = "settings_page"

        /** The home screen page, where the lock is turned off again. */
        const val PAGE_HOME = "home"
    }
}
