package com.sdev.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdev.launcher.R
import com.sdev.launcher.data.DrawerStyle
import com.sdev.launcher.ui.Glyph

/** The pages the settings screen can be showing. */
internal enum class SettingsDestination {
    MENU,
    HOME,
    DRAWER,
    ICONS,
    APPEARANCE,
    HIDDEN,
    SYSTEM,
    ICON_SHAPE,
    ICON_PACKS,
}

/**
 * Opens a card of choices on a row. The row supplies its own bounds when it is
 * tapped; what comes back is the callback that row hands the bounds to.
 */
internal typealias ChoiceOpener = (List<String>, Int, (Int) -> Unit) -> (Rect) -> Unit

/** How far a divider on the menu clears the icon tile and the text beside it. */
private val MENU_DIVIDER_INSET = 74.dp

@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    initialDestination: SettingsDestination = SettingsDestination.MENU,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(initialDestination.name) }
    val destination = SettingsDestination.valueOf(route)
    var choice by remember { mutableStateOf<SettingsChoice?>(null) }

    val parent = when (destination) {
        SettingsDestination.ICON_SHAPE, SettingsDestination.ICON_PACKS -> SettingsDestination.ICONS
        else -> SettingsDestination.MENU
    }

    BackHandler(enabled = choice != null || destination != SettingsDestination.MENU) {
        if (choice != null) {
            choice = null
        } else {
            route = parent.name
        }
    }

    val openChoice: ChoiceOpener = { options, selected, onPick ->
        { anchor ->
            choice = SettingsChoice(
                anchor = anchor,
                options = options,
                selected = selected.coerceAtLeast(0),
                onPick = onPick,
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (destination) {
                SettingsDestination.MENU -> SettingsMenu(
                    state = state,
                    onOpen = { route = it.name },
                    onBack = onBack,
                )

                SettingsDestination.HOME -> HomeSettingsPage(
                    state = state,
                    viewModel = viewModel,
                    ask = openChoice,
                    onBack = { route = SettingsDestination.MENU.name },
                )

                SettingsDestination.DRAWER -> DrawerSettingsPage(
                    state = state,
                    viewModel = viewModel,
                    ask = openChoice,
                    onBack = { route = SettingsDestination.MENU.name },
                )

                SettingsDestination.ICONS -> IconsSettingsPage(
                    state = state,
                    viewModel = viewModel,
                    ask = openChoice,
                    onOpenShape = { route = SettingsDestination.ICON_SHAPE.name },
                    onOpenPacks = { route = SettingsDestination.ICON_PACKS.name },
                    onBack = { route = SettingsDestination.MENU.name },
                )

                SettingsDestination.APPEARANCE -> AppearanceSettingsPage(
                    state = state,
                    viewModel = viewModel,
                    ask = openChoice,
                    onBack = { route = SettingsDestination.MENU.name },
                )

                SettingsDestination.HIDDEN -> HiddenAppsScreen(
                    apps = state.apps,
                    hiddenApps = state.settings.hiddenApps,
                    onToggle = viewModel::setAppHidden,
                    onBack = { route = SettingsDestination.MENU.name },
                )

                SettingsDestination.SYSTEM -> SystemSettingsPage(
                    onBack = { route = SettingsDestination.MENU.name },
                )

                SettingsDestination.ICON_SHAPE -> IconShapeScreen(
                    previews = state.shapePreviews,
                    selected = state.settings.iconShape,
                    onSelect = viewModel::setIconShape,
                    onBack = { route = SettingsDestination.ICONS.name },
                )

                SettingsDestination.ICON_PACKS -> IconPackScreen(
                    packs = state.iconPacks,
                    previews = state.packIcons,
                    systemPreview = state.shapePreviews[state.settings.iconShape],
                    selected = state.settings.iconPack,
                    onSelect = viewModel::setIconPack,
                    onBack = { route = SettingsDestination.ICONS.name },
                )
            }

            choice?.let { open ->
                SettingsChoiceCard(choice = open, onDismiss = { choice = null })
            }
        }
    }
}

/**
 * The first settings screen: what the launcher can be told about, one category
 * per row, each summarised with the value it is on at the moment.
 */
@Composable
private fun SettingsMenu(
    state: SettingsUiState,
    onOpen: (SettingsDestination) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }
    val hiddenAppCount = settings.hiddenApps.size

    val gridSummary = stringResource(
        R.string.settings_grid_summary,
        settings.homeColumns,
        settings.homeRows,
    )
    val layoutLabel = stringResource(
        if (settings.drawerStyle == DrawerStyle.PAGES) {
            R.string.drawer_style_pages
        } else {
            R.string.drawer_style_list
        },
    )
    val drawerSummary = stringResource(
        R.string.settings_menu_pair,
        layoutLabel,
        pluralStringResource(
            R.plurals.settings_columns_count,
            settings.drawerColumns,
            settings.drawerColumns,
        ),
    )
    val shapeSummary = stringResource(iconShapeLabel(settings.iconShape))
    val themeSummary = themeLabel(settings.themeMode)
    val hiddenSummary = if (hiddenAppCount == 0) {
        stringResource(R.string.hidden_apps_none)
    } else {
        pluralStringResource(
            R.plurals.hidden_apps_count,
            hiddenAppCount,
            hiddenAppCount,
        )
    }
    val systemSummary = stringResource(R.string.settings_version, versionName)

    SettingsPage(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) {
        SettingsSection(stringResource(R.string.settings_personalise)) {
            SettingsMenuRow(
                title = stringResource(R.string.settings_home),
                subtitle = gridSummary,
                glyph = Glyph.HOME,
                onClick = { onOpen(SettingsDestination.HOME) },
            )

            SettingsDivider(MENU_DIVIDER_INSET)

            SettingsMenuRow(
                title = stringResource(R.string.settings_app_drawer),
                subtitle = drawerSummary,
                glyph = Glyph.APPS,
                onClick = { onOpen(SettingsDestination.DRAWER) },
            )

            SettingsDivider(MENU_DIVIDER_INSET)

            SettingsMenuRow(
                title = stringResource(R.string.settings_icons),
                subtitle = shapeSummary,
                glyph = Glyph.ICONS,
                onClick = { onOpen(SettingsDestination.ICONS) },
            )

            SettingsDivider(MENU_DIVIDER_INSET)

            SettingsMenuRow(
                title = stringResource(R.string.settings_appearance),
                subtitle = themeSummary,
                glyph = Glyph.THEME,
                onClick = { onOpen(SettingsDestination.APPEARANCE) },
            )
        }

        SettingsSection(stringResource(R.string.settings_general)) {
            SettingsMenuRow(
                title = stringResource(R.string.settings_hidden_apps),
                subtitle = hiddenSummary,
                glyph = Glyph.HIDE,
                onClick = { onOpen(SettingsDestination.HIDDEN) },
            )

            SettingsDivider(MENU_DIVIDER_INSET)

            SettingsMenuRow(
                title = stringResource(R.string.settings_system),
                subtitle = systemSummary,
                glyph = Glyph.SETTINGS,
                onClick = { onOpen(SettingsDestination.SYSTEM) },
            )
        }
    }
}
