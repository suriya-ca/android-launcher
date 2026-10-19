package com.sdev.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdev.launcher.data.AppInfo

/**
 * The icon and label. Taps, long presses and drags are not handled here: they
 * are recognised by [iconGesture], which the home screen and the drawer share.
 */
@Composable
fun AppIconTileContent(
    app: AppInfo,
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelColor: Color? = null,
    labelShadow: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(icon = icon, label = app.label, size = iconSize)

        if (showLabel) {
            Spacer(Modifier.height(6.dp))

            Text(
                text = app.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    shadow = if (labelShadow) LABEL_SHADOW else null,
                ),
                color = labelColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Home screen labels sit on the wallpaper, so they need a little help. */
private val LABEL_SHADOW = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 6f)


/**
 * The icon itself, falling back to a coloured letter while the real bitmap is
 * still being decoded.
 */
@Composable
fun AppIconImage(
    icon: ImageBitmap?,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LetterAvatar(label = label, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun LetterAvatar(label: String, modifier: Modifier = Modifier) {
    val color = remember(label) {
        val hue = ((label.hashCode() % 360) + 360) % 360
        Color.hsl(hue.toFloat(), 0.4f, 0.45f)
    }
    val letter = remember(label) {
        label.trim().firstOrNull()?.uppercase() ?: "?"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
