package com.sdev.launcher.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdev.launcher.R
import com.sdev.launcher.data.DrawerStyle
import com.sdev.launcher.data.IconSize
import com.sdev.launcher.data.LauncherSettings
import com.sdev.launcher.data.SortOrder
import com.sdev.launcher.data.ThemeMode
import com.sdev.launcher.service.NotificationShadeService

private val THEME_MODES = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
private val ICON_SIZES = listOf(IconSize.SMALL, IconSize.MEDIUM, IconSize.LARGE)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showHiddenApps by rememberSaveable { mutableStateOf(false) }
    var showIconShape by rememberSaveable { mutableStateOf(false) }
    var showIconPacks by rememberSaveable { mutableStateOf(false) }

    val anySubScreen = showHiddenApps || showIconShape || showIconPacks
    BackHandler(enabled = anySubScreen) {
        showHiddenApps = false
        showIconShape = false
        showIconPacks = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            showHiddenApps -> HiddenAppsScreen(
                apps = state.apps,
                hiddenApps = state.settings.hiddenApps,
                onToggle = viewModel::setAppHidden,
                onBack = { showHiddenApps = false },
            )

            showIconShape -> IconShapeScreen(
                previews = state.shapePreviews,
                selected = state.settings.iconShape,
                onSelect = viewModel::setIconShape,
                onBack = { showIconShape = false },
            )

            showIconPacks -> IconPackScreen(
                packs = state.iconPacks,
                previews = state.packIcons,
                systemPreview = state.shapePreviews[state.settings.iconShape],
                selected = state.settings.iconPack,
                onSelect = viewModel::setIconPack,
                onBack = { showIconPacks = false },
            )

            else -> SettingsHome(
                state = state,
                viewModel = viewModel,
                onOpenHiddenApps = { showHiddenApps = true },
                onOpenIconShape = { showIconShape = true },
                onOpenIconPacks = { showIconPacks = true },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun SettingsHome(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenHiddenApps: () -> Unit,
    onOpenIconShape: () -> Unit,
    onOpenIconPacks: () -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    val context = LocalContext.current
    val hiddenAppCount = settings.hiddenApps.size
    var isDefaultHome by remember { mutableStateOf(isDefaultHome(context)) }
    var choice by remember { mutableStateOf<SettingsChoice?>(null) }

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

    // The lists the popup cards offer. They are named here rather than at each
    // row so that the row shows the same words as the card it opens.
    val themeLabels = listOf(
        stringResource(R.string.theme_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark),
    )
    val layoutLabels = listOf(
        stringResource(R.string.drawer_style_pages),
        stringResource(R.string.drawer_style_list),
    )
    val columnLabels = LauncherSettings.DRAWER_COLUMN_CHOICES.map { it.toString() }
    val iconSizeLabels = listOf(
        stringResource(R.string.icon_size_small),
        stringResource(R.string.icon_size_medium),
        stringResource(R.string.icon_size_large),
    )
    val sortLabels = listOf(
        stringResource(R.string.sort_az),
        stringResource(R.string.sort_za),
    )
    val rowLabels = LauncherSettings.HOME_ROW_CHOICES.map { it.toString() }
    val homeColumnLabels = LauncherSettings.HOME_COLUMN_CHOICES.map { it.toString() }
    val dockSlotLabels = LauncherSettings.DOCK_SLOT_CHOICES.map { it.toString() }

    // Opens the card of choices for a row. The row hands over its own bounds,
    // which is what the card hangs off.
    fun ask(options: List<String>, selected: Int, onPick: (Int) -> Unit) = { anchor: Rect ->
        choice = SettingsChoice(
            anchor = anchor,
            options = options,
            selected = selected.coerceAtLeast(0),
            onPick = onPick,
        )
    }

    BackHandler(enabled = choice != null) { choice = null }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsPage(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
        ) {
            SettingsSection(stringResource(R.string.settings_home)) {
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_home_columns),
                    subtitle = stringResource(R.string.settings_home_columns_hint),
                    value = pluralStringResource(
                        R.plurals.settings_columns_count,
                        settings.homeColumns,
                        settings.homeColumns,
                    ),
                    onOpen = ask(
                        options = homeColumnLabels,
                        selected = LauncherSettings.HOME_COLUMN_CHOICES.indexOf(settings.homeColumns),
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
                        options = rowLabels,
                        selected = LauncherSettings.HOME_ROW_CHOICES.indexOf(settings.homeRows),
                    ) { index ->
                        viewModel.setHomeRows(LauncherSettings.HOME_ROW_CHOICES[index])
                    },
                )

                SettingsDivider()

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
                            options = dockSlotLabels,
                            selected = LauncherSettings.DOCK_SLOT_CHOICES.indexOf(settings.dockSlots),
                        ) { index ->
                            viewModel.setDockSlots(LauncherSettings.DOCK_SLOT_CHOICES[index])
                        },
                    )
                }
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
                                    com.sdev.launcher.service.LauncherNotificationListenerService.settingsIntent(),
                                )
                            }
                        },
                    )
                }
            }

            SettingsSection(stringResource(R.string.settings_app_screen)) {
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_drawer_style),
                    value = layoutLabels[if (settings.drawerStyle == DrawerStyle.PAGES) 0 else 1],
                    onOpen = ask(
                        options = layoutLabels,
                        selected = if (settings.drawerStyle == DrawerStyle.PAGES) 0 else 1,
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
                        options = columnLabels,
                        selected = LauncherSettings.DRAWER_COLUMN_CHOICES
                            .indexOf(settings.drawerColumns),
                    ) { index ->
                        viewModel.setDrawerColumns(LauncherSettings.DRAWER_COLUMN_CHOICES[index])
                    },
                )

                SettingsDivider()

                SettingsChoiceRow(
                    title = stringResource(R.string.settings_icon_size),
                    value = iconSizeLabels[ICON_SIZES.indexOf(settings.iconSize).coerceAtLeast(0)],
                    onOpen = ask(
                        options = iconSizeLabels,
                        selected = ICON_SIZES.indexOf(settings.iconSize),
                    ) { index ->
                        viewModel.setIconSize(ICON_SIZES[index])
                    },
                )

                SettingsDivider()

                SettingsChoiceRow(
                    title = stringResource(R.string.settings_sort),
                    value = sortLabels[
                        if (settings.sortOrder == SortOrder.ALPHABETICAL) 0 else 1,
                    ],
                    onOpen = ask(
                        options = sortLabels,
                        selected = if (settings.sortOrder == SortOrder.ALPHABETICAL) 0 else 1,
                    ) { index ->
                        viewModel.setSortOrder(
                            if (index == 0) SortOrder.ALPHABETICAL else SortOrder.REVERSE_ALPHABETICAL,
                        )
                    },
                )

                SettingsDivider()

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

            SettingsSection(stringResource(R.string.settings_appearance)) {
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_theme),
                    value = themeLabels[THEME_MODES.indexOf(settings.themeMode).coerceAtLeast(0)],
                    onOpen = ask(
                        options = themeLabels,
                        selected = THEME_MODES.indexOf(settings.themeMode),
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

                SettingsDivider()

                SettingsRow(
                    title = stringResource(R.string.settings_icon_shape),
                    value = stringResource(iconShapeLabel(settings.iconShape)),
                    onClick = onOpenIconShape,
                )

                SettingsDivider()

                SettingsRow(
                    title = stringResource(R.string.settings_icon_pack),
                    value = state.iconPacks
                        .firstOrNull { it.packageName == settings.iconPack }
                        ?.label
                        ?: stringResource(R.string.icon_pack_system),
                    onClick = onOpenIconPacks,
                )
            }

            SettingsSection(stringResource(R.string.settings_apps)) {
                SettingsRow(
                    title = stringResource(R.string.settings_hidden_apps),
                    value = if (hiddenAppCount == 0) {
                        stringResource(R.string.hidden_apps_none)
                    } else {
                        pluralStringResource(
                            R.plurals.hidden_apps_count,
                            hiddenAppCount,
                            hiddenAppCount,
                        )
                    },
                    onClick = onOpenHiddenApps,
                )
            }

            SettingsSection(stringResource(R.string.settings_system)) {
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

        choice?.let { open ->
            SettingsChoiceCard(choice = open, onDismiss = { choice = null })
        }
    }
}

private fun isDefaultHome(context: Context): Boolean {
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return false
    return roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
        roleManager.isRoleHeld(RoleManager.ROLE_HOME)
}

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager
        .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        .versionName
        .orEmpty()
}.getOrDefault("")
