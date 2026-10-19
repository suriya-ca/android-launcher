package com.sdev.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdev.launcher.ui.LauncherScreen
import com.sdev.launcher.ui.LauncherViewModel
import com.sdev.launcher.ui.theme.AppTheme
import com.sdev.launcher.ui.theme.SystemBarsEffect

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

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

                LauncherScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up apps installed or uninstalled while we were in the background,
        // and tells the widgets how much room they have in case the grid has
        // changed since they last drew.
        viewModel.onResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home action while the drawer is open should return to the home screen.
        viewModel.onHomeRequested()
    }
}
