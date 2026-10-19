package com.sdev.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sdev.launcher.R
import com.sdev.launcher.data.IconPack
import com.sdev.launcher.data.IconShape
import com.sdev.launcher.ui.AppIconImage

/** Picks the outline every icon is drawn into. */
@Composable
fun IconShapeScreen(
    previews: Map<IconShape, ImageBitmap>,
    selected: IconShape,
    onSelect: (IconShape) -> Unit,
    onBack: () -> Unit,
) {
    val shapes = IconShape.entries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        SettingsHeader(
            title = stringResource(R.string.settings_icon_shape),
            onBack = onBack,
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(items = shapes, key = { _, shape -> shape.name }) { index, shape ->
                PickRow(
                    preview = previews[shape],
                    label = stringResource(iconShapeLabel(shape)),
                    selected = shape == selected,
                    showDivider = index != shapes.lastIndex,
                    onClick = { onSelect(shape) },
                )
            }
        }
    }
}

/** Picks the icon pack icons are taken from. */
@Composable
fun IconPackScreen(
    packs: List<IconPack>,
    previews: Map<String, ImageBitmap>,
    systemPreview: ImageBitmap?,
    selected: String?,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        SettingsHeader(
            title = stringResource(R.string.settings_icon_pack),
            onBack = onBack,
        )

        Text(
            text = stringResource(R.string.icon_pack_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        )

        if (packs.isEmpty()) {
            // The system icons are still a choice, so the picker always has one
            // row in it; there is just nothing else to list.
            PickRow(
                preview = systemPreview,
                label = stringResource(R.string.icon_pack_system),
                selected = selected == null,
                showDivider = false,
                onClick = { onSelect(null) },
            )

            Box(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.icon_pack_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item(key = "system") {
                    PickRow(
                        preview = systemPreview,
                        label = stringResource(R.string.icon_pack_system),
                        selected = selected == null,
                        showDivider = true,
                        onClick = { onSelect(null) },
                    )
                }

                itemsIndexed(items = packs, key = { _, pack -> pack.packageName }) { index, pack ->
                    PickRow(
                        preview = previews[pack.packageName],
                        label = pack.label,
                        selected = pack.packageName == selected,
                        showDivider = index != packs.lastIndex,
                        onClick = { onSelect(pack.packageName) },
                    )
                }
            }
        }
    }
}

/** A preview, a name and a tick: one option of either picker. */
@Composable
private fun PickRow(
    preview: ImageBitmap?,
    label: String,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconImage(icon = preview, label = label, size = 40.dp)

            Spacer(Modifier.width(16.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (selected) {
                CheckGlyph(color = MaterialTheme.colorScheme.primary)
            }
        }

        if (showDivider) {
            SettingsDivider()
        }
    }
}

internal fun iconShapeLabel(shape: IconShape): Int = when (shape) {
    IconShape.SYSTEM -> R.string.icon_shape_system
    IconShape.CIRCLE -> R.string.icon_shape_circle
    IconShape.SQUIRCLE -> R.string.icon_shape_squircle
    IconShape.ROUNDED_SQUARE -> R.string.icon_shape_rounded_square
    IconShape.SQUARE -> R.string.icon_shape_square
    IconShape.TEARDROP -> R.string.icon_shape_teardrop
}
