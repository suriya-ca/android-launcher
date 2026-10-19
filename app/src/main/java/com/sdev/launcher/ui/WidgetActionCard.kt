package com.sdev.launcher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sdev.launcher.R

/** Shown when a widget on the home screen is long-pressed. */
@Composable
fun WidgetActionCard(
    label: String,
    anchor: Rect,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    OneUiPopup(anchor = anchor, onDismiss = onDismiss) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        OneUiAction(
            glyph = Glyph.REMOVE,
            label = stringResource(R.string.widget_remove),
            onClick = onRemove,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
