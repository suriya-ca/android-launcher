package com.sdev.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sdev.launcher.R
import com.sdev.launcher.service.LauncherNotificationListenerService

/** How icons look, and whether they carry the badges of their notifications. */
@Composable
internal fun IconsSettingsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    ask: ChoiceOpener,
    onOpenShape: () -> Unit,
    onOpenPacks: () -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    val context = LocalContext.current
    val sizeLabels = ICON_SIZES.map { iconSizeLabel(it) }
    val packLabel = state.iconPacks
        .firstOrNull { it.packageName == settings.iconPack }
        ?.label
        ?: stringResource(R.string.icon_pack_system)

    SettingsPage(
        title = stringResource(R.string.settings_icons),
        onBack = onBack,
    ) {
        SettingsSection(stringResource(R.string.settings_icon_style)) {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_icon_size),
                value = iconSizeLabel(settings.iconSize),
                onOpen = ask(
                    sizeLabels,
                    ICON_SIZES.indexOf(settings.iconSize),
                ) { index ->
                    viewModel.setIconSize(ICON_SIZES[index])
                },
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_icon_shape),
                value = stringResource(iconShapeLabel(settings.iconShape)),
                onClick = onOpenShape,
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_icon_pack),
                value = packLabel,
                onClick = onOpenPacks,
            )
        }

        SettingsSection(stringResource(R.string.settings_badges)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_badges),
                subtitle = stringResource(R.string.settings_show_badges_hint),
                checked = settings.showNotificationBadges,
                onCheckedChange = viewModel::setShowNotificationBadges,
            )

            if (settings.showNotificationBadges && !state.notificationPermissionGranted) {
                SettingsDivider()

                SettingsRow(
                    title = stringResource(R.string.settings_badges_permission),
                    subtitle = stringResource(R.string.settings_badges_permission_hint),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                LauncherNotificationListenerService.settingsIntent(),
                            )
                        }
                    },
                )
            }
        }
    }
}
