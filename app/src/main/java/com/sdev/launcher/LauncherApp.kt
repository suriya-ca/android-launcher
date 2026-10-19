package com.sdev.launcher

import android.app.Application
import com.sdev.launcher.ui.WidgetHost

/**
 * The launcher's application.
 *
 * It holds the widget host, which belongs to the process rather than to any one
 * screen. The system sends a widget's updates to the host that created its view,
 * and only while that host is listening: a host that is built, torn down and
 * built again as the home screen activity comes and goes stops being sent
 * updates altogether, which is how widgets come to sit frozen on whatever they
 * last drew. Living for as long as the process does is what avoids that, and it
 * also means the settings screen can hand a widget back to the system without
 * starting a second host of its own.
 */
class LauncherApp : Application() {

    /**
     * The one widget host. Made on first use and listening from then on, for as
     * long as the launcher is running.
     */
    val widgetHost: WidgetHost by lazy {
        WidgetHost(this).also { it.keepListening() }
    }

    override fun onCreate() {
        super.onCreate()

        // Bind as early as possible: any widget the user already has is one the
        // system is holding for us until we ask for it again.
        widgetHost
    }
}
