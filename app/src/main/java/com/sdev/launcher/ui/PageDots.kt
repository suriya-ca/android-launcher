package com.sdev.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * How many pages there are to swipe through, and which one is showing. Used by
 * the drawer's pages and by the home screen's.
 *
 * [mainPage] is the page the launcher's Home action returns to. It is drawn as
 * a ring when it is not the page being looked at, so the two kinds of "current"
 * page cannot be confused. A [currentPage] of -1 means no real page is showing,
 * which happens while an add-page outline is on screen.
 */
@Composable
fun PageDots(
    pageCount: Int,
    currentPage: Int,
    mainPage: Int = -1,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val main = index == mainPage

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (selected) 18.dp else 7.dp, height = 7.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            main -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                    .then(
                        if (main && !selected) {
                            Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
