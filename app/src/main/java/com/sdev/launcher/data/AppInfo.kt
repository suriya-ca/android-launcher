package com.sdev.launcher.data

import android.content.ComponentName
import android.os.UserHandle

/**
 * One launchable activity, i.e. one icon in the app drawer.
 *
 * [key] is stable across list reloads and is used for icon caching and for
 * Compose's item identity.
 */
data class AppInfo(
    val key: String,
    val componentName: ComponentName,
    val label: String,
    val user: UserHandle,
)
