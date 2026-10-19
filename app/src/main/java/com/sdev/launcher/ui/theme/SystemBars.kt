package com.sdev.launcher.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sdev.launcher.data.ThemeMode

/**
 * Keeps the status and navigation bar icons readable for the chosen theme.
 *
 * Edge-to-edge by itself only follows the *system* dark mode, so a forced
 * Light or Dark theme would end up with icons that match the system rather
 * than the screen behind them.
 */
@Composable
fun SystemBarsEffect(themeMode: ThemeMode) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val darkTheme = resolveDarkTheme(themeMode)

    if (activity == null) return

    LaunchedEffect(activity, themeMode, darkTheme) {
        val style = when {
            themeMode == ThemeMode.SYSTEM -> SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            )

            darkTheme -> SystemBarStyle.dark(scrim = Color.TRANSPARENT)

            else -> SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            )
        }

        activity.enableEdgeToEdge(
            statusBarStyle = style,
            navigationBarStyle = style,
        )
    }
}

/**
 * The compose context is usually the activity itself, but it can be wrapped
 * when a theme is applied, so unwrap until we find it.
 */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
