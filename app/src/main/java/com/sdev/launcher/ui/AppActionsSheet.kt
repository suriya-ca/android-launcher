package com.sdev.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sdev.launcher.R
import com.sdev.launcher.data.AppInfo

/**
 * The actions for one app, in the card One UI hangs off the icon that was long
 * pressed.
 *
 * Which rows appear depends on where the icon was: an icon on the home screen
 * is offered removal, one in the drawer is offered a place on the home screen,
 * and the launcher never offers to hide or uninstall itself.
 */
@Composable
fun AppActionCard(
    app: AppInfo,
    icon: ImageBitmap?,
    anchor: Rect,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onHide: (() -> Unit)? = null,
    onAddToHome: (() -> Unit)? = null,
    onAddToDock: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = null,
    showUninstall: Boolean = true,
) {
    OneUiPopup(anchor = anchor, onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconImage(icon = icon, label = app.label, size = 34.dp)

            Spacer(Modifier.width(14.dp))

            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(2.dp))

        if (onRemove != null) {
            OneUiAction(
                glyph = Glyph.REMOVE,
                label = removeLabel ?: stringResource(R.string.remove_from_home),
                onClick = onRemove,
            )
        }
        if (onAddToHome != null) {
            OneUiAction(
                glyph = Glyph.ADD,
                label = stringResource(R.string.add_to_home),
                onClick = onAddToHome,
            )
        }
        if (onAddToDock != null) {
            OneUiAction(
                glyph = Glyph.ADD,
                label = stringResource(R.string.add_to_dock),
                onClick = onAddToDock,
            )
        }

        OneUiAction(
            glyph = Glyph.INFO,
            label = stringResource(R.string.app_info),
            onClick = onAppInfo,
        )

        if (onHide != null) {
            OneUiAction(
                glyph = Glyph.HIDE,
                label = stringResource(R.string.hide_app),
                onClick = onHide,
            )
        }

        if (showUninstall) {
            OneUiAction(
                glyph = Glyph.UNINSTALL,
                label = stringResource(R.string.uninstall),
                onClick = onUninstall,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
