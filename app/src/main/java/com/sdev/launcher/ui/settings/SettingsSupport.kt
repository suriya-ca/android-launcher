package com.sdev.launcher.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sdev.launcher.R
import com.sdev.launcher.data.IconSize
import com.sdev.launcher.data.ThemeMode

/** The theme choices, in the order the settings cards list them. */
internal val THEME_MODES = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

/** The icon sizes, in the order the settings cards list them. */
internal val ICON_SIZES = listOf(IconSize.SMALL, IconSize.MEDIUM, IconSize.LARGE)

@Composable
internal fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
internal fun iconSizeLabel(size: IconSize): String = stringResource(
    when (size) {
        IconSize.SMALL -> R.string.icon_size_small
        IconSize.MEDIUM -> R.string.icon_size_medium
        IconSize.LARGE -> R.string.icon_size_large
    },
)

internal fun isDefaultHome(context: Context): Boolean {
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return false
    return roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
        roleManager.isRoleHeld(RoleManager.ROLE_HOME)
}

internal fun appVersionName(context: Context): String = runCatching {
    context.packageManager
        .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        .versionName
        .orEmpty()
}.getOrDefault("")
