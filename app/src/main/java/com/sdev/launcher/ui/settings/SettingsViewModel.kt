package com.sdev.launcher.ui.settings

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdev.launcher.LauncherApp
import com.sdev.launcher.data.AppInfo
import com.sdev.launcher.data.AppRepository
import com.sdev.launcher.data.DrawerStyle
import com.sdev.launcher.data.IconPack
import com.sdev.launcher.data.IconShape
import com.sdev.launcher.data.IconSize
import com.sdev.launcher.data.LauncherSettings
import com.sdev.launcher.data.SettingsRepository
import com.sdev.launcher.data.SortOrder
import com.sdev.launcher.data.ThemeMode
import com.sdev.launcher.service.NotificationShadeService
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: LauncherSettings = LauncherSettings(),
    /** Every launchable app, so hidden ones can be brought back. */
    val apps: List<AppInfo> = emptyList(),
    /** One sample icon rendered in each shape, for the shape picker. */
    val shapePreviews: Map<IconShape, ImageBitmap> = emptyMap(),
    val iconPacks: List<IconPack> = emptyList(),
    /** Each icon pack's own icon, so the list is recognisable. */
    val packIcons: Map<String, ImageBitmap> = emptyMap(),
    /** Whether the notifications gesture has been allowed in the system settings. */
    val shadeEnabled: Boolean = false,
    /** Whether notification listener access is granted for badges. */
    val notificationPermissionGranted: Boolean = false,
)

/**
 * Deliberately separate from the launcher's ViewModel: the settings screen
 * needs the app *list* but not a hundred decoded icon bitmaps.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val settingsRepository = SettingsRepository(application)

    /**
     * The launcher's widget host, which belongs to the application. It is here
     * so that a widget the shorter grid has no room for can be handed back to
     * the system rather than left bound and invisible.
     */
    private val widgetHost = (application as LauncherApp).widgetHost

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            val apps = appRepository.loadApps()
            _state.update { it.copy(apps = apps) }

            loadShapePreviews(apps.firstOrNull())
            loadIconPacks()
            refreshShadeState()
        }
    }

    fun setDrawerColumns(columns: Int) {
        viewModelScope.launch { settingsRepository.setDrawerColumns(columns) }
    }

    fun setHomeColumns(columns: Int) {
        viewModelScope.launch {
            val rows = _state.value.settings.homeRows
            widgetHost.releaseIds(settingsRepository.setHomeGrid(columns, rows))
        }
    }

    /**
     * Gives the home screen a grid of [rows] rows.
     *
     * Everything on the home screen keeps its place where the new grid has that
     * place to give; a widget the shorter grid cannot hold is moved, and one
     * there is nowhere left for is handed back to the system.
     */
    fun setHomeRows(rows: Int) {
        viewModelScope.launch {
            val cols = _state.value.settings.homeColumns
            widgetHost.releaseIds(settingsRepository.setHomeGrid(cols, rows))
        }
    }

    fun setShowDock(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowDock(show) }
    }

    fun setDockSlots(slots: Int) {
        viewModelScope.launch { settingsRepository.setDockSlots(slots) }
    }

    fun setShowNotificationBadges(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowNotificationBadges(show) }
        refreshNotificationPermission()
    }

    fun setEnableT9Search(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEnableT9Search(enabled) }
    }

    fun setDrawerStyle(style: DrawerStyle) {
        viewModelScope.launch { settingsRepository.setDrawerStyle(style) }
    }

    fun setIconSize(size: IconSize) {
        viewModelScope.launch { settingsRepository.setIconSize(size) }
    }

    fun setIconShape(shape: IconShape) {
        viewModelScope.launch { settingsRepository.setIconShape(shape) }
    }

    fun setIconPack(packageName: String?) {
        viewModelScope.launch { settingsRepository.setIconPack(packageName) }
    }

    fun setShowLabels(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowLabels(show) }
    }

    fun setShowSearchBar(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowSearchBar(show) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settingsRepository.setSortOrder(order) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setSwipeDownNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSwipeDownNotifications(enabled) }
        refreshShadeState()
    }

    fun setAppHidden(key: String, hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setAppHidden(key, hidden) }
    }

    /** Called when the screen resumes: the user may have just allowed the gesture. */
    fun refreshShadeState() {
        val enabled = NotificationShadeService.isEnabled(getApplication())
        _state.update { it.copy(shadeEnabled = enabled) }
        refreshNotificationPermission()
    }

    fun refreshNotificationPermission() {
        val granted = com.sdev.launcher.service.LauncherNotificationListenerService.isEnabled(getApplication())
        _state.update { it.copy(notificationPermissionGranted = granted) }
    }

    /**
     * Renders one sample icon in every shape, so the picker shows what it is
     * choosing between rather than just naming the outlines.
     */
    private suspend fun loadShapePreviews(sample: AppInfo?) {
        val context = getApplication<Application>()
        val sizePx = with(context.resources.displayMetrics) { (PREVIEW_DP * density).roundToInt() }
        val ownIcon = runCatching {
            context.packageManager.getApplicationIcon(context.packageName)
        }.getOrNull()

        val previews = IconShape.entries.mapNotNull { shape ->
            val bitmap = sample?.let { appRepository.icon(it, sizePx, shape, pack = null) }
                ?: ownIcon?.let { appRepository.render(it, sizePx, shape) }

            bitmap?.let { shape to it }
        }.toMap()

        _state.update { it.copy(shapePreviews = previews) }
    }

    private suspend fun loadIconPacks() {
        val context = getApplication<Application>()
        val packs = runCatching { appRepository.iconPacks() }.getOrDefault(emptyList())
        val sizePx = with(context.resources.displayMetrics) { (PREVIEW_DP * density).roundToInt() }

        val icons = packs.mapNotNull { pack ->
            val drawable = runCatching {
                context.packageManager.getApplicationIcon(pack.packageName)
            }.getOrNull() ?: return@mapNotNull null

            pack.packageName to appRepository.render(drawable, sizePx, IconShape.SYSTEM)
        }.toMap()

        _state.update { it.copy(iconPacks = packs, packIcons = icons) }
    }

    private companion object {
        const val PREVIEW_DP = 44f
    }
}
