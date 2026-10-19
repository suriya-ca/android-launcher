package com.sdev.launcher.data

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** One installed icon pack, as far as the launcher cares. */
data class IconPack(val packageName: String, val label: String)

/**
 * Finds and reads third-party icon packs.
 *
 * Icon packs have no official API. In practice almost all of them advertise one
 * of a handful of legacy intent actions and ship a resource called
 * `appfilter.xml` listing `ComponentInfo{package/class}` to drawable name
 * mappings. That is the format read here; anything that doesn't fit is treated
 * as an empty pack rather than an error, so a broken pack can only fail to
 * change icons.
 */
class IconPackRepository(private val context: Context) {

    private val mutex = Mutex()
    private val loaded = mutableMapOf<String, LoadedPack?>()

    /** Every installed pack, sorted by name. Empty when there are none. */
    suspend fun availablePacks(): List<IconPack> = withContext(Dispatchers.IO) {
        val manager = context.packageManager
        val found = LinkedHashMap<String, IconPack>()

        THEME_ACTIONS.forEach { action ->
            val activities = runCatching {
                manager.queryIntentActivities(Intent(action), 0)
            }.getOrNull().orEmpty()

            activities.forEach { resolved ->
                val packageName = resolved.activityInfo?.packageName ?: return@forEach
                if (packageName == context.packageName || packageName in found) return@forEach

                val label = runCatching { resolved.loadLabel(manager).toString() }
                    .getOrNull()
                    .orEmpty()
                    .ifBlank { packageName }

                found[packageName] = IconPack(packageName, label)
            }
        }

        found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** Reads (and remembers) one pack's icon mappings. */
    suspend fun load(packageName: String): LoadedPack? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (packageName in loaded) return@withLock loaded[packageName]

            val pack = readPack(packageName)
            loaded[packageName] = pack
            pack
        }
    }

    private fun readPack(packageName: String): LoadedPack? = runCatching {
        val resources = context.packageManager.getResourcesForApplication(packageName)

        val fromResource = resources.getIdentifier(APP_FILTER, "xml", packageName)
        val icons = if (fromResource != 0) {
            parseAppFilter(resources.getXml(fromResource))
        } else {
            readAppFilterAsset(packageName)
        }

        if (icons.isEmpty()) null else LoadedPack(packageName, resources, icons)
    }.getOrNull()

    /** A few old packs keep `appfilter.xml` in assets rather than resources. */
    private fun readAppFilterAsset(packageName: String): Map<String, String> = runCatching {
        val assets = context.createPackageContext(packageName, 0).assets
        assets.open("$APP_FILTER.xml").use { stream ->
            parseAppFilter(Xml.newPullParser().apply { setInput(stream, null) })
        }
    }.getOrDefault(emptyMap())

    private companion object {
        const val APP_FILTER = "appfilter"

        /**
         * The intent actions icon packs use to advertise themselves. ADW's is by
         * far the most common and is also what Nova, Apex and friends read.
         */
        val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME",
            "org.adw.launcher.icons",
        )
    }
}

/**
 * An icon pack that has been read successfully: its resources, plus the
 * component-to-drawable mapping from its `appfilter.xml`.
 */
class LoadedPack(
    val packageName: String,
    private val resources: Resources,
    private val icons: Map<String, String>,
) {
    val iconCount: Int get() = icons.size

    /** The icon [app] has in this pack, or null when the pack doesn't cover it. */
    fun iconFor(app: AppInfo): Drawable? {
        val component = app.componentName
        val packageName = component.packageName

        val name = icons[component.flattenToShortString()]
            ?: icons[component.flattenToString()]
            ?: icons["$packageName/${component.className}"]
            ?: icons["$packageName/"]
            ?: return null

        val id = resources.getIdentifier(name, "drawable", this.packageName)
        if (id == 0) return null

        return runCatching { resources.getDrawable(id, null) }.getOrNull()
    }
}

/**
 * Reads an `appfilter.xml`: a flat list of
 * `<item component="ComponentInfo{package/class}" drawable="name"/>`.
 */
internal fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
    val icons = HashMap<String, String>()

    var event = runCatching { parser.eventType }.getOrDefault(XmlPullParser.START_DOCUMENT)
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "item") {
            val component = parser.getAttributeValue(null, "component")
            val drawable = parser.getAttributeValue(null, "drawable")

            if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                normalizeComponent(component)?.let { icons[it] = drawable }
            }
        }

        event = runCatching { parser.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
    }

    return icons
}

/**
 * Turns the component spellings icon packs use into the one the launcher looks
 * up: `package/class`. A bare package name becomes `package/`, which is how a
 * pack says "every activity of this app".
 */
internal fun normalizeComponent(raw: String): String? {
    val trimmed = raw.trim()
    val withoutPrefix = trimmed.removePrefix("ComponentInfo{").trimEnd('}').trim()
    if (withoutPrefix.isEmpty()) return null

    return if (withoutPrefix.contains('/')) withoutPrefix else "$withoutPrefix/"
}
