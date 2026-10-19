package com.sdev.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Turns app icons into bitmaps and keeps them around, so scrolling the app
 * drawer never re-rasterises a drawable.
 */
class IconCache(maxEntries: Int = 256) {

    private val cache = LruCache<String, ImageBitmap>(maxEntries)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }

    /**
     * Renders [drawable] into a square bitmap of [sizePx], cut to [shape].
     *
     * With [IconShape.SYSTEM] adaptive icons fill the square and legacy icons
     * are inset slightly so they don't look oversized next to adaptive ones.
     * Any other shape masks the icon, so the artwork has to be drawn to fill
     * the square first: an adaptive icon's two layers are drawn by hand, since
     * the drawable would otherwise apply the system's own mask on top of ours.
     */
    fun render(
        drawable: Drawable,
        sizePx: Int,
        shape: IconShape = IconShape.SYSTEM,
    ): ImageBitmap {
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            if (shape == IconShape.SYSTEM &&
                bitmap != null &&
                bitmap.width == sizePx &&
                bitmap.height == sizePx
            ) {
                return bitmap.asImageBitmap()
            }
        }

        val target = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)

        if (shape == IconShape.SYSTEM) {
            drawPlain(drawable, canvas, sizePx)
        } else {
            canvas.save()
            canvas.clipPath(maskPath(shape, sizePx.toFloat()))
            drawFill(drawable, canvas, sizePx)
            canvas.restore()
        }

        return target.asImageBitmap()
    }

    private fun drawPlain(drawable: Drawable, canvas: Canvas, sizePx: Int) {
        val inset = if (drawable is AdaptiveIconDrawable) 0 else (sizePx * LEGACY_INSET).toInt()
        drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
        drawable.draw(canvas)
    }

    /**
     * Fills the whole square: legacy icons are only nudged in a little, because
     * the shape itself is what stops them from looking oversized.
     */
    private fun drawFill(drawable: Drawable, canvas: Canvas, sizePx: Int) {
        if (drawable !is AdaptiveIconDrawable) {
            val inset = (sizePx * LEGACY_SHAPE_INSET).toInt()
            drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            drawable.draw(canvas)
            return
        }

        val background = drawable.background
        val foreground = drawable.foreground
        if (background == null || foreground == null) {
            drawPlain(drawable, canvas, sizePx)
            return
        }

        background.setBounds(0, 0, sizePx, sizePx)
        background.draw(canvas)

        // The foreground layer is designed on a canvas a third larger than the
        // visible area, so expanding its bounds by the icon's extra inset
        // fraction makes the artwork reach the edges of the square.
        val inset = (sizePx * EXTRA_INSET_FRACTION).toInt()
        foreground.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
        foreground.draw(canvas)
    }

    private fun maskPath(shape: IconShape, size: Float): Path {
        val bounds = RectF(0f, 0f, size, size)

        return Path().apply {
            when (shape) {
                IconShape.SYSTEM -> addRect(bounds, Path.Direction.CW)

                IconShape.CIRCLE -> addOval(bounds, Path.Direction.CW)

                IconShape.SQUIRCLE -> addRoundRect(
                    bounds,
                    FloatArray(8) { size * SQUIRCLE_RADIUS },
                    Path.Direction.CW,
                )

                IconShape.ROUNDED_SQUARE -> addRoundRect(
                    bounds,
                    FloatArray(8) { size * ROUNDED_RADIUS },
                    Path.Direction.CW,
                )

                IconShape.SQUARE -> addRect(bounds, Path.Direction.CW)

                // Three fully rounded corners and one sharp one.
                IconShape.TEARDROP -> addRoundRect(
                    bounds,
                    floatArrayOf(size / 2, size / 2, size / 2, size / 2, size / 2, size / 2, 0f, 0f),
                    Path.Direction.CW,
                )
            }
        }
    }

    private companion object {
        /** How far legacy icons are held back from the edges without a shape. */
        const val LEGACY_INSET = 0.1f

        /** The same, for icons that are being masked. */
        const val LEGACY_SHAPE_INSET = 0.06f

        /**
         * How much bigger an adaptive icon's foreground layer is than the area
         * it is visible in. This is `AdaptiveIconDrawable`'s own extra inset
         * fraction, which the platform keeps out of the public API.
         */
        const val EXTRA_INSET_FRACTION = 0.25f

        const val SQUIRCLE_RADIUS = 0.33f
        const val ROUNDED_RADIUS = 0.2f
    }
}
