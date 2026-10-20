package com.sdev.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sdev.launcher.R
import com.sdev.launcher.data.DrawerStyle
import com.sdev.launcher.data.LauncherSettings
import com.sdev.launcher.data.SortOrder

/** Everything that shapes the app drawer: its layout and the list of apps. */
@Composable
internal fun DrawerSettingsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    ask: ChoiceOpener,
    onBack: () -> Unit,
) {
    val settings = state.settings

    val layoutLabels = listOf(
        stringResource(R.string.drawer_style_pages),
        stringResource(R.string.drawer_style_list),
    )
    val columnLabels = LauncherSettings.DRAWER_COLUMN_CHOICES.map { it.toString() }
    val sortLabels = listOf(
        stringResource(R.string.sort_az),
        stringResource(R.string.sort_za),
    )

    SettingsPage(
        title = stringResource(R.string.settings_app_drawer),
        onBack = onBack,
    ) {
        SettingsSection(stringResource(R.string.settings_layout)) {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_drawer_style),
                value = layoutLabels[if (settings.drawerStyle == DrawerStyle.PAGES) 0 else 1],
                onOpen = ask(
                    layoutLabels,
                    if (settings.drawerStyle == DrawerStyle.PAGES) 0 else 1,
                ) { index ->
                    viewModel.setDrawerStyle(
                        if (index == 0) DrawerStyle.PAGES else DrawerStyle.LIST,
                    )
                },
            )

            SettingsDivider()

            SettingsChoiceRow(
                title = stringResource(R.string.settings_columns),
                value = settings.drawerColumns.toString(),
                onOpen = ask(
                    columnLabels,
                    LauncherSettings.DRAWER_COLUMN_CHOICES.indexOf(settings.drawerColumns),
                ) { index ->
                    viewModel.setDrawerColumns(LauncherSettings.DRAWER_COLUMN_CHOICES[index])
                },
            )

            SettingsDivider()

            SettingsChoiceRow(
                title = stringResource(R.string.settings_sort),
                value = sortLabels[
                    if (settings.sortOrder == SortOrder.ALPHABETICAL) 0 else 1,
                ],
                onOpen = ask(
                    sortLabels,
                    if (settings.sortOrder == SortOrder.ALPHABETICAL) 0 else 1,
                ) { index ->
                    viewModel.setSortOrder(
                        if (index == 0) SortOrder.ALPHABETICAL else SortOrder.REVERSE_ALPHABETICAL,
                    )
                },
            )
        }

        SettingsSection(stringResource(R.string.settings_apps)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_labels),
                checked = settings.showLabels,
                onCheckedChange = viewModel::setShowLabels,
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_search),
                checked = settings.showSearchBar,
                onCheckedChange = viewModel::setShowSearchBar,
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_t9_search),
                subtitle = stringResource(R.string.settings_t9_search_hint),
                checked = settings.enableT9Search,
                onCheckedChange = viewModel::setEnableT9Search,
            )
        }
    }
}
