package com.sdev.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * A do-nothing accessibility service whose only job is to be allowed to pull the
 * notification shade down.
 *
 * Expanding the shade needs either a system permission or a hidden API, neither
 * of which a launcher can rely on, so the documented route is a global
 * accessibility action. There is nothing here to abuse: the service reads no
 * window content and reacts to no events, it just exposes one action.
 */
class NotificationShadeService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: NotificationShadeService? = null

        /** True when the user has switched this service on. */
        fun isConnected(): Boolean = instance != null

        /** Opens the shade, or returns false when the service isn't switched on. */
        fun expandShade(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /** Whether this launcher's service is listed in the system settings. */
        fun isEnabled(context: Context): Boolean = isConnected() || isEnabledInSettings(context)

        /** Opens the system screen where the service can be switched on. */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun isEnabledInSettings(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()

            val component = "${context.packageName}/${NotificationShadeService::class.java.name}"
            return enabled.split(':').any { it.equals(component, ignoreCase = true) }
        }
    }
}
