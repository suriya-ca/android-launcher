package com.sdev.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * Reads the list of launchable apps and their icons.
 *
 * `LauncherApps` is the API intended for launchers: it needs no permissions and
 * keeps working for the current user. `PackageManager` is used only as a
 * fallback for the rare device where the service returns nothing.
 */
class AppRepository(private val context: Context) {

    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    private val iconCache = IconCache()
    private val iconPacks = IconPackRepository(context)
    private val activityInfos = ConcurrentHashMap<String, LauncherActivityInfo>()

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        activityInfos.clear()

        val fromLauncherApps = runCatching { launcherActivityInfos() }.getOrNull().orEmpty()
        val apps = if (fromLauncherApps.isNotEmpty()) {
            fromLauncherApps.map { info ->
                val key = identity(info.componentName, info.user)
                activityInfos[key] = info
                AppInfo(
                    key = key,
                    componentName = info.componentName,
                    label = info.label?.toString().orEmpty()
                        .ifBlank { info.componentName.packageName },
                    user = info.user,
                )
            }
        } else {
            fallbackApps()
        }

        apps.asSequence()
            .filter { it.componentName.packageName != context.packageName }
            .distinctBy { it.key }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    /**
     * Decodes one icon, using the cache when possible. Safe to call from the
     * main thread; the work happens on [Dispatchers.IO].
     *
     * [pack] is the package name of the icon pack to take the icon from, or
     * null to use the app's own icon. An icon pack that doesn't cover the app
     * falls back to the app's own icon, which is what users expect.
     */
    suspend fun icon(
        app: AppInfo,
        sizePx: Int,
        shape: IconShape = IconShape.SYSTEM,
        pack: String? = null,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "$sizePx|${shape.name}|${pack.orEmpty()}|${app.key}"
        iconCache.get(cacheKey)?.let { return@withContext it }

        val drawable = packDrawable(pack, app) ?: resolveDrawable(app) ?: return@withContext null
        val bitmap = iconCache.render(drawable, sizePx, shape)
        iconCache.put(cacheKey, bitmap)
        bitmap
    }

    /** Renders an arbitrary drawable, used for the settings previews. */
    fun render(
        drawable: Drawable,
        sizePx: Int,
        shape: IconShape,
    ): ImageBitmap = iconCache.render(drawable, sizePx, shape)

    /** Every installed icon pack. */
    suspend fun iconPacks(): List<IconPack> = iconPacks.availablePacks()

    private suspend fun packDrawable(pack: String?, app: AppInfo): Drawable? {
        if (pack == null) return null
        return iconPacks.load(pack)?.iconFor(app)
    }

    /**
     * Emits whenever an app is installed, removed, enabled, disabled or
     * renamed. Package broadcasts are not delivered for apps that are invisible
     * to us, which is why this goes through `LauncherApps`.
     */
    fun packageEvents(): Flow<Unit> = callbackFlow {
        val service = launcherApps
        if (service == null) {
            close()
            return@callbackFlow
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                trySend(Unit)
            }

            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                trySend(Unit)
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) {
                trySend(Unit)
            }
        }

        service.registerCallback(callback)
        awaitClose { service.unregisterCallback(callback) }
    }.conflate()

    private fun launcherActivityInfos(): List<LauncherActivityInfo> {
        val service = launcherApps ?: return emptyList()
        return service.profiles.flatMap { user -> service.getActivityList(null, user) }
    }

    @Suppress("DEPRECATION")
    private fun fallbackApps(): List<AppInfo> {
        val user = Process.myUserHandle()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
                AppInfo(
                    key = identity(componentName, user),
                    componentName = componentName,
                    label = resolveInfo.loadLabel(context.packageManager).toString(),
                    user = user,
                )
            }
    }

    private fun resolveDrawable(app: AppInfo): Drawable? {
        activityInfos[app.key]?.let { info ->
            runCatching { info.getIcon(0) }.getOrNull()?.let { return it }
        }
        return runCatching { context.packageManager.getActivityIcon(app.componentName) }.getOrNull()
    }

    private fun identity(componentName: ComponentName, user: UserHandle): String =
        componentName.flattenToShortString() + "@" + user.hashCode()
}
