package com.sdev.launcher.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.sdev.launcher.ui.OneUiPopup
import com.sdev.launcher.ui.OneUiScrim

/**
 * The furniture of the settings screen, in the shape One UI gives it: one card
 * per group of settings, a row per setting with its current value on the right,
 * a switch, and a card of choices that hangs off a row when it is tapped.
 *
 * The colours come from the theme rather than being written down, so the screen
 * is a light one or a dark one depending on what the user has chosen.
 */

/** How far a card sits in from the sides of the screen. */
private val CARD_INSET = 16.dp

/** The corner of a settings card. */
private val CARD_CORNER = 20.dp

/** The height of a row that holds one setting. */
private val ROW_HEIGHT = 60.dp

/** Where a divider starts, so that it clears the leading text. */
private val DIVIDER_INSET = 20.dp

/** How far the page scrolls before the title has finished shrinking. */
private val TITLE_COLLAPSE = 72.dp

/** The title as it starts, and as it ends up once the page has been scrolled. */
private val BIG_TITLE = 32.sp
private val SMALL_TITLE = 20.sp

/** The header as it starts, and as it ends up once the page has been scrolled. */
private val BIG_HEADER = 104.dp
private val SMALL_HEADER = 64.dp

/**
 * A settings page with the big title One UI gives it.
 *
 * The title shrinks as the page is scrolled and a hairline is drawn under it,
 * which is the whole of what the collapsing header is for: the name of the
 * screen is large while there is room for it and out of the way once the user
 * has started reading the settings themselves.
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val collapseAt = with(LocalDensity.current) { TITLE_COLLAPSE.toPx() }
    val collapse = (scroll.value / collapseAt).coerceIn(0f, 1f)
    val colours = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(lerp(SMALL_HEADER, BIG_HEADER, 1f - collapse))
                .padding(start = 4.dp, end = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BackButton(onClick = onBack)

            Text(
                text = title,
                fontSize = lerp(SMALL_TITLE, BIG_TITLE, 1f - collapse),
                fontWeight = FontWeight.SemiBold,
                color = colours.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, bottom = 14.dp),
            )
        }

        // The hairline under the title, which the title's shrinking brings in.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colours.outlineVariant.copy(alpha = collapse)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            content()

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * The title bar of a screen that was opened from the settings screen. The big
 * title belongs to the page that is being read; these are one step in, and name
 * themselves in the bar that carries the way back.
 */
@Composable
internal fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SMALL_HEADER)
            .padding(start = 4.dp, end = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BackButton(onClick = onBack)

        Text(
            text = title,
            fontSize = SMALL_TITLE,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, bottom = 14.dp),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
internal fun BackButton(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(width = 9.dp, height = 16.dp)) {
            val stroke = size.minDimension * 0.13f
            val mid = size.height / 2f
            drawLine(color, Offset(size.width, 0f), Offset(0f, mid), stroke, StrokeCap.Round)
            drawLine(color, Offset(0f, mid), Offset(size.width, size.height), stroke, StrokeCap.Round)
        }
    }
}

/**
 * A group of settings on one card, with its name above it.
 *
 * The name is the small one One UI puts over a card; it is part of the card's
 * group rather than a heading in its own right.
 */
@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = CARD_INSET + 4.dp,
            end = CARD_INSET,
            top = 22.dp,
            bottom = 8.dp,
        ),
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CARD_INSET),
        shape = RoundedCornerShape(CARD_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(content = content)
    }
}

/**
 * One setting: its name, a line under it if it needs explaining, its current
 * value on the right, and whatever control it carries.
 *
 * A row that is tapped also gets a chevron, which is One UI's way of saying
 * that something happens rather than that the row is a value.
 */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    onBounds: ((Rect) -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colours = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                if (onBounds != null) {
                    Modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) }
                } else {
                    Modifier
                },
            )
            .heightIn(min = ROW_HEIGHT)
            .padding(start = DIVIDER_INSET, end = CARD_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colours.onSurface,
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colours.onSurfaceVariant,
                )
            }
        }

        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colours.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        when {
            trailing != null -> trailing()

            onClick != null -> {
                Spacer(Modifier.width(6.dp))
                Chevron()
            }
        }
    }
}

/** A setting that is simply on or off. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
    ) {
        SettingsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A setting whose value is one of a few things. Tapping the row opens the card
 * of choices it is given; the row itself knows only where the card should hang.
 */
@Composable
internal fun SettingsChoiceRow(
    title: String,
    value: String,
    subtitle: String? = null,
    onOpen: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }

    SettingsRow(
        title = title,
        subtitle = subtitle,
        value = value,
        onClick = { onOpen(bounds) },
        onBounds = { bounds = it },
    )
}

/** The line between two settings on the same card. */
@Composable
internal fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DIVIDER_INSET)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/** The Samsung switch: a slim track with a round knob that slides along it. */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val trackWidth = 44.dp
    val trackHeight = 26.dp
    val knobWidth = 20.dp
    val margin = 3.dp

    val knob by animateDpAsState(
        targetValue = if (checked) trackWidth - knobWidth - margin else margin,
        label = "settings-switch",
    )

    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(CircleShape)
            .background(if (checked) colours.primary else colours.surfaceVariant)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .offset(x = knob)
                .padding(top = margin)
                .size(knobWidth)
                .clip(CircleShape)
                .background(if (checked) colours.onPrimary else colours.outline),
        )
    }
}

/** The small arrow on the right of a row that opens something. */
@Composable
private fun Chevron() {
    val colour = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = Modifier.size(width = 7.dp, height = 12.dp)) {
        val stroke = size.minDimension * 0.14f
        drawLine(colour, Offset(0f, 0f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Round)
        drawLine(colour, Offset(size.width, size.height / 2f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}

/** The tick that marks the picked row of a list. */
@Composable
internal fun CheckGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.13f
        val corner = Offset(size.width * 0.42f, size.height * 0.78f)

        drawLine(color, Offset(size.width * 0.16f, size.height * 0.54f), corner, stroke, StrokeCap.Round)
        drawLine(color, corner, Offset(size.width * 0.84f, size.height * 0.24f), stroke, StrokeCap.Round)
    }
}

/**
 * A choice waiting to be made: what is on offer, which one is on at the moment,
 * what to do with the answer, and where the row that asked sits on the screen.
 */
internal data class SettingsChoice(
    val anchor: Rect,
    val options: List<String>,
    val selected: Int,
    val onPick: (Int) -> Unit,
)

/**
 * The card of choices, hung off the row that was tapped: the One UI way of
 * choosing between a few things rather than a row of buttons or a screen of its
 * own. Tapping one takes it and takes the card away.
 */
@Composable
internal fun SettingsChoiceCard(choice: SettingsChoice, onDismiss: () -> Unit) {
    val colours = MaterialTheme.colorScheme

    OneUiScrim(onDismiss = onDismiss)

    OneUiPopup(anchor = choice.anchor, onDismiss = onDismiss) {
        choice.options.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        choice.onPick(index)
                    }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colours.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(12.dp))

                RadioMark(selected = index == choice.selected)
            }
        }
    }
}

/** A Samsung radio button: a ring, filled in with a tick once it is picked. */
@Composable
private fun RadioMark(selected: Boolean) {
    val colours = MaterialTheme.colorScheme

    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.09f
            val radius = size.minDimension / 2f - stroke / 2f

            if (selected) {
                drawCircle(color = colours.primary, radius = radius)
            } else {
                drawCircle(color = colours.outline, radius = radius, style = Stroke(stroke))
            }
        }

        if (selected) {
            CheckGlyph(color = colours.onPrimary, modifier = Modifier.size(13.dp))
        }
    }
}
