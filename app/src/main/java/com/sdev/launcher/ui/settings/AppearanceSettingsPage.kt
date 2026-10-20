package com.sdev.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sdev.launcher.R

/** The theme and the colours the launcher takes from the wallpaper. */
@Composable
internal fun AppearanceSettingsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    ask: ChoiceOpener,
    onBack: () -> Unit,
) {
    val settings = state.settings
    val themeLabels = THEME_MODES.map { themeLabel(it) }

    SettingsPage(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
    ) {
        SettingsSection(stringResource(R.string.settings_colors)) {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_theme),
                value = themeLabels[
                    THEME_MODES.indexOf(settings.themeMode).coerceAtLeast(0),
                ],
                onOpen = ask(
                    themeLabels,
                    THEME_MODES.indexOf(settings.themeMode),
                ) { index ->
                    viewModel.setThemeMode(THEME_MODES[index])
                },
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_hint),
                checked = settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
        }
    }
}
