package com.sdev.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sdev.launcher.R
import com.sdev.launcher.data.LauncherSettings
import com.sdev.launcher.service.NotificationShadeService

/** Everything that shapes the home screen: its grid, its dock and the swipe. */
@Composable
internal fun HomeSettingsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    ask: ChoiceOpener,
    onBack: () -> Unit,
) {
    val settings = state.settings
    val context = LocalContext.current

    val rowLabels = LauncherSettings.HOME_ROW_CHOICES.map { it.toString() }
    val columnLabels = LauncherSettings.HOME_COLUMN_CHOICES.map { it.toString() }
    val dockSlotLabels = LauncherSettings.DOCK_SLOT_CHOICES.map { it.toString() }

    SettingsPage(
        title = stringResource(R.string.settings_home),
        onBack = onBack,
    ) {
        SettingsSection(stringResource(R.string.settings_grid)) {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_home_columns),
                subtitle = stringResource(R.string.settings_home_columns_hint),
                value = pluralStringResource(
                    R.plurals.settings_columns_count,
                    settings.homeColumns,
                    settings.homeColumns,
                ),
                onOpen = ask(
                    columnLabels,
                    LauncherSettings.HOME_COLUMN_CHOICES.indexOf(settings.homeColumns),
                ) { index ->
                    viewModel.setHomeColumns(LauncherSettings.HOME_COLUMN_CHOICES[index])
                },
            )

            SettingsDivider()

            SettingsChoiceRow(
                title = stringResource(R.string.settings_grid_rows),
                subtitle = stringResource(R.string.settings_grid_rows_hint),
                value = pluralStringResource(
                    R.plurals.settings_rows_count,
                    settings.homeRows,
                    settings.homeRows,
                ),
                onOpen = ask(
                    rowLabels,
                    LauncherSettings.HOME_ROW_CHOICES.indexOf(settings.homeRows),
                ) { index ->
                    viewModel.setHomeRows(LauncherSettings.HOME_ROW_CHOICES[index])
                },
            )
        }

        SettingsSection(stringResource(R.string.settings_home_lock)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_home_lock_title),
                subtitle = stringResource(R.string.settings_home_lock_hint),
                checked = settings.homeLocked,
                onCheckedChange = viewModel::setHomeLocked,
            )
        }

        SettingsSection(stringResource(R.string.settings_dock)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_dock),
                subtitle = stringResource(R.string.settings_show_dock_hint),
                checked = settings.showDock,
                onCheckedChange = viewModel::setShowDock,
            )

            if (settings.showDock) {
                SettingsDivider()

                SettingsChoiceRow(
                    title = stringResource(R.string.settings_dock_slots),
                    subtitle = stringResource(R.string.settings_dock_slots_hint),
                    value = pluralStringResource(
                        R.plurals.settings_slots_count,
                        settings.dockSlots,
                        settings.dockSlots,
                    ),
                    onOpen = ask(
                        dockSlotLabels,
                        LauncherSettings.DOCK_SLOT_CHOICES.indexOf(settings.dockSlots),
                    ) { index ->
                        viewModel.setDockSlots(LauncherSettings.DOCK_SLOT_CHOICES[index])
                    },
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_notifications)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_swipe_notifications),
                subtitle = stringResource(R.string.settings_swipe_notifications_hint),
                checked = settings.swipeDownNotifications,
                onCheckedChange = viewModel::setSwipeDownNotifications,
            )

            if (settings.swipeDownNotifications && !state.shadeEnabled) {
                SettingsDivider()

                SettingsRow(
                    title = stringResource(R.string.settings_swipe_notifications_enable),
                    subtitle = stringResource(R.string.settings_swipe_notifications_enable_hint),
                    onClick = {
                        runCatching {
                            context.startActivity(NotificationShadeService.settingsIntent())
                        }
                    },
                )
            }
        }
    }
}
