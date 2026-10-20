package com.sdev.launcher.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listens for system notifications and maintains the count of unread notifications
 * per app package name, powering icon notification badges.
 */
class LauncherNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateNotificationCounts()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateNotificationCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateNotificationCounts()
    }

    private fun updateNotificationCounts() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val counts = mutableMapOf<String, Int>()

        for (sbn in active) {
            if (sbn.isOngoing) continue
            val pkg = sbn.packageName ?: continue
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }

        _notificationCounts.value = counts
    }

    companion object {
        @Volatile
        private var instance: LauncherNotificationListenerService? = null

        private val _notificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val notificationCounts: StateFlow<Map<String, Int>> = _notificationCounts.asStateFlow()

        /** True when this notification listener service is connected. */
        fun isConnected(): Boolean = instance != null

        /** Whether notification access is granted for this app in system settings. */
        fun isEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val component = ComponentName(context, LauncherNotificationListenerService::class.java).flattenToString()
            return isConnected() || enabledListeners.split(':').any { it.equals(component, ignoreCase = true) }
        }

        /** Intent to open Notification Access settings. */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
