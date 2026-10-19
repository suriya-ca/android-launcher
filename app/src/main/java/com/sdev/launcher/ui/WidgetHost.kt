package com.sdev.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import com.sdev.launcher.data.HomeGrid
import com.sdev.launcher.data.WidgetPlacement
import com.sdev.launcher.data.widgetSpanFor
import kotlin.math.roundToInt

/**
 * The launcher's side of the widget system: the host that owns the widgets on
 * the home screen, and the calls the rest of the launcher makes on them.
 *
 * There is one of these for the whole process - [com.sdev.launcher.LauncherApp]
 * holds it - because a host is how the system knows where to send a widget's
 * updates. A host that is built and thrown away as the home screen comes and
 * goes is a host the system stops sending updates to, which leaves the widgets
 * on the screen frozen on whatever they last drew. Living for as long as the
 * launcher does is what keeps them moving.
 *
 * Binding a widget is normally the home app's privilege. When the system will
 * not simply allow it, [bindIntent] is offered instead: it is the system's own
 * "let this app show widgets" screen, and the user's answer comes back to
 * whoever launched it.
 */
class WidgetHost(private val context: Context) {

    val host = AppWidgetHost(context, HOST_ID)

    /**
     * The views the host is showing, one per widget, kept for as long as the
     * widget is on the home screen.
     *
     * A widget's view is where its updates land. Throwing one away and making a
     * new one every time the page it is on is drawn again leaves the updates
     * that arrive in between with nowhere to go, and a widget that is swiped
     * away from and back again should show what it looks like now. One view per
     * widget, kept, also means a widget's own state - a list it has scrolled, a
     * picture it has loaded - survives the page coming and going.
     */
    private val views = HashMap<Int, AppWidgetHostView>()

    /**
     * The ids reserved for widgets being drawn on the widget page, by the key of
     * the entry they belong to.
     *
     * A preview is a real widget, bound to a real id, so what the page shows is
     * the widget itself rather than a picture of it. These are not the home
     * screen's ids: they last as long as the page is showing, and all but the one
     * being taken are handed back when it goes.
     */
    private val previews = HashMap<String, Int>()

    /**
     * How big one cell of the home screen is, in dp, as it is really drawn.
     *
     * The home screen reports this because a widget's room is measured in cells
     * and the user can change how many rows there are, so the system's own idea
     * of a cell is not the launcher's. Until the home screen has been laid out,
     * the system's size is used.
     */
    var cellWidthDp: Float = CELL_DP.toFloat()
    var cellHeightDp: Float = CELL_DP.toFloat()

    private var listening = false

    private val manager: AppWidgetManager
        get() = AppWidgetManager.getInstance(context)

    /**
     * Starts the host listening for widget updates, once.
     *
     * It has to happen before any widget is asked for, and it is never given up:
     * a host that stops listening does not start again on its own, and the
     * widgets it owns go quiet. Safe to call from anywhere, as often as liked.
     */
    fun keepListening() {
        if (listening) return

        listening = runCatching {
            host.startListening()
            true
        }.getOrDefault(false)
    }

    /** Reserves an id for a widget that is about to be added. */
    fun allocateId(): Int = host.allocateAppWidgetId()

    /**
     * Hands an id back when the widget is no longer on the home screen, and lets
     * go of the view that was showing it.
     */
    fun releaseId(hostId: Int) {
        views.remove(hostId)
        runCatching { host.deleteAppWidgetId(hostId) }
    }

    /** Hands several widgets back at once. */
    fun releaseIds(hostIds: List<Int>) {
        hostIds.forEach { releaseId(it) }
    }

    /**
     * The view showing the widget [hostId], made once and kept.
     *
     * The same view is handed back every time, so the widget is only ever drawn
     * by one thing and its updates always have somewhere to land. A widget that
     * has moved from one page to another can be asked for again while the page
     * it came from is still being taken down, and a view can only be in one
     * place, so it is taken out of wherever it was first.
     */
    fun viewOf(hostId: Int, info: AppWidgetProviderInfo): AppWidgetHostView? {
        views[hostId]?.let { cached ->
            (cached.parent as? ViewGroup)?.removeView(cached)
            return cached
        }

        val made = runCatching { host.createView(context, hostId, info) }.getOrNull()
        if (made != null) views[hostId] = made
        return made
    }

    /** The provider behind [hostId], if the system still has it. */
    fun infoOf(hostId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(hostId) }.getOrNull()

    /** The id the widget page is drawing [key] under, if it has been asked for yet. */
    private fun previewIdOf(key: String): Int? = previews[key]
    /**
     * Reserves and binds an id so that the widget behind [info] can be drawn on
     * the widget page, and tells the system how much room it is being drawn in.
     *
     * Binding is the part worth not doing twice, so the same id comes back every
     * time [key] is asked for.
     *
     * Null when the system will not hand this widget over, which leaves the page
     * to offer it by name and to put the system's question to the user on the way
     * in.
     */
    fun previewId(key: String, info: AppWidgetProviderInfo, spanX: Int, spanY: Int): Int? {
        previewIdOf(key)?.let { return it }

        val id = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return null
        if (!bind(id, info.provider)) {
            releaseId(id)
            return null
        }

        val (width, height) = previewSizeDp(info, spanX, spanY)
        updateOptions(hostId = id, widthDp = width, heightDp = height)

        previews[key] = id
        return id
    }

    /**
     * The room a preview of [info] is drawn in, in dp: the size the provider asks
     * for, or about what it would take up on the home screen when it does not
     * say. Both the size the system is told and the size the view is drawn at
     * come from here, so a widget cannot be shown at one size and told another.
     */
    fun previewSizeDp(info: AppWidgetProviderInfo, spanX: Int, spanY: Int): Pair<Int, Int> =
        (info.minWidth.takeIf { it > 0 } ?: sizeDp(spanX, cellWidthDp))
            .coerceAtLeast(MIN_PREVIEW_DP) to
            (info.minHeight.takeIf { it > 0 } ?: sizeDp(spanY, cellHeightDp))
                .coerceAtLeast(MIN_PREVIEW_DP)

    /**
     * Hands back every id reserved for a preview.
     *
     * Called when the widget page goes away, and whenever a widget is chosen:
     * whatever is left of the previews is nothing but the ids they were drawn
     * under.
     */
    fun releasePreviews() {
        previews.values.forEach { releaseId(it) }
        previews.clear()
    }

    /** True when the system let us bind straight away. */
    fun bind(hostId: Int, provider: ComponentName): Boolean = runCatching {
        manager.bindAppWidgetIdIfAllowed(hostId, provider)
    }.getOrDefault(false)

    /** The system's own request for permission to show widgets. */
    fun bindIntent(hostId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, hostId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

    /** Every widget that can be put on the screen. */
    fun providers(): List<AppWidgetProviderInfo> = runCatching {
        manager.getInstalledProviders().filter { it.provider != null }
    }.getOrDefault(emptyList())

    /** The label shown on the widget page. */
    fun labelOf(provider: AppWidgetProviderInfo): CharSequence =
        runCatching { provider.loadLabel(context.packageManager) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: provider.provider.packageName

    /**
     * How many cells a widget wants on [grid].
     *
     * A provider states its size in dp, and the system's own model of a cell is
     * what those numbers were chosen against, so [widgetSpanFor] is what reads
     * them. The launcher's grid is narrower than the widest of them, which is
     * what the limit is for.
     */
    fun spanOf(provider: AppWidgetProviderInfo, grid: HomeGrid): Pair<Int, Int> =
        widgetSpanFor(provider.minWidth, grid.columns) to
            widgetSpanFor(provider.minHeight, grid.rows)

    /**
     * Tells the system how much room a widget has now, so that a provider which
     * draws itself differently at different sizes is asked to draw again rather
     * than being stretched.
     */
    fun resize(hostId: Int, spanX: Int, spanY: Int) {
        updateOptions(
            hostId = hostId,
            widthDp = sizeDp(spanX, cellWidthDp),
            heightDp = sizeDp(spanY, cellHeightDp),
        )
    }

    /**
     * Tells the system how much room a widget has, in dp, and nothing else.
     *
     * This is the one thing that asks a provider which sizes its layout off the
     * room it has been given to lay itself out again: a widget cannot be made to
     * redraw, it can only be told that its room has changed.
     */
    fun updateOptions(hostId: Int, widthDp: Int, heightDp: Int) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }

        runCatching { manager.updateAppWidgetOptions(hostId, options) }
    }

    /**
     * Says again how much room every one of [widgets] has.
     *
     * Called when the home screen comes back to the front. The host keeps what
     * the system last told it, but the grid may have been changed while the
     * launcher was away - or by another launcher being the home app - and a
     * widget is only ever asked to draw itself again when the room it has
     * changes.
     */
    fun refreshSizes(widgets: Collection<WidgetPlacement>) {
        widgets.forEach { resize(it.hostId, it.spanX, it.spanY) }
    }

    /** The size in dp of [span] cells of [cellDp]. */
    private fun sizeDp(span: Int, cellDp: Float): Int =
        (span * cellDp).roundToInt() - PADDING_DP

    companion object {
        /**
         * Identifies this launcher to the system as the owner of the widgets it
         * shows. Only has to stay the same across restarts.
         */
        private const val HOST_ID = 0x1a5c

        /** The system's model of a cell, in dp, as [widgetSpanFor] reads it. */
        private const val CELL_DP = 70

        /** How much of a widget's room is the gap around it rather than it. */
        private const val PADDING_DP = 30

        /** The smallest a preview is drawn, however little the widget asks for. */
        private const val MIN_PREVIEW_DP = 32
    }
}
