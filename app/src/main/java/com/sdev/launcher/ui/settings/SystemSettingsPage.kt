package com.sdev.launcher.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sdev.launcher.R

/** The launcher's place in the system, and what version is running. */
@Composable
internal fun SystemSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var isDefaultHome by remember { mutableStateOf(isDefaultHome(context)) }

    val requestHomeRole = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        isDefaultHome = isDefaultHome(context)
    }

    val versionName = remember(context) { appVersionName(context) }
    val wallpaperFailed = stringResource(
        R.string.action_failed,
        stringResource(R.string.settings_wallpaper),
    )

    SettingsPage(
        title = stringResource(R.string.settings_system),
        onBack = onBack,
    ) {
        SettingsSection(stringResource(R.string.settings_home_app)) {
            SettingsRow(
                title = stringResource(R.string.settings_set_default_home),
                subtitle = stringResource(
                    if (isDefaultHome) {
                        R.string.settings_default_home_is_us
                    } else {
                        R.string.settings_default_home_other
                    },
                ),
                onClick = {
                    if (isDefaultHome) return@SettingsRow

                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                    val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    if (intent != null) requestHomeRole.launch(intent)
                },
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_wallpaper),
                onClick = {
                    val sent = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SET_WALLPAPER)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.isSuccess

                    if (!sent) {
                        Toast.makeText(context, wallpaperFailed, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        SettingsSection(stringResource(R.string.settings_about)) {
            SettingsRow(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.settings_version, versionName),
            )
        }
    }
}
