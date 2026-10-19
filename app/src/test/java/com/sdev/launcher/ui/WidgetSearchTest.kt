package com.sdev.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the widget page's search leaves on the page.
 *
 * The case worth pinning down is a search that matches no widget but does match
 * the app providing them, and one that matches a single widget under an app
 * whose own name has nothing to do with it: a widget is nearly always listed
 * under a name its app does not share, so matching on the app's name alone would
 * make half the widgets on the phone unfindable.
 */
class WidgetSearchTest {

    private fun choice(label: String) = WidgetChoice(
        key = label,
        provider = AppWidgetProviderInfo(),
        label = label,
        spanX = 1,
        spanY = 1,
    )

    private fun group(app: String, vararg widgets: String) = WidgetGroup(
        packageName = app.lowercase().replace(" ", "."),
        label = app,
        choices = widgets.map { choice(it) },
    )

    private val groups = listOf(
        group("Clock", "Analog clock", "Digital clock"),
        group("Weather", "Today's forecast"),
        group("Calendar", "Month", "Week"),
    )

    @Test
    fun `an empty search leaves everything where it was`() {
        assertEquals(groups, filterWidgetGroups(groups, ""))
        assertEquals(groups, filterWidgetGroups(groups, "   "))
    }

    @Test
    fun `a widget is found by its own name`() {
        val shown = filterWidgetGroups(groups, "week")

        assertEquals(1, shown.size)
        assertEquals("Calendar", shown.single().label)
        assertEquals(listOf("Week"), shown.single().choices.map { it.label })
    }

    @Test
    fun `an app's widgets are found by the app's name`() {
        val shown = filterWidgetGroups(groups, "clock")

        assertEquals(1, shown.size)
        // Both of them, and not the clock widget that is not called one.
        assertEquals(listOf("Analog clock", "Digital clock"), shown.single().choices.map { it.label })
    }

    @Test
    fun `a group left with nothing in it is dropped`() {
        assertTrue(filterWidgetGroups(groups, "calendar").single().label == "Calendar")

        val shown = filterWidgetGroups(groups, "forecast")
        assertEquals(1, shown.size)
        assertEquals("Weather", shown.single().label)
    }

    @Test
    fun `a search that matches nothing leaves nothing`() {
        assertTrue(filterWidgetGroups(groups, "nothing like this").isEmpty())
    }
}
