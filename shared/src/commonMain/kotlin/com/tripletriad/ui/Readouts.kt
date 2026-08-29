package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tripletriad.CLIENT_VERSION

/*
 * Two things several screens show and nobody can touch.
 *
 * They would sit as happily in [Controls], and that is where they were: it is the file for what
 * more than one screen draws. It ran into detekt's ceiling on functions per file, and of everything
 * in there these two are the ones that are not controls — a bar that reports and a line that
 * states. Splitting on that line rather than on an arbitrary one is why this file has a name.
 */

/**
 * The build's release number, small enough to ignore and present enough to read out.
 *
 * Two screens want it — the sign-in form and the title screen — and for one reason: the first thing
 * a bug report has to carry is which build it came from, and asking a player to find that anywhere
 * else is asking them to guess. The tag is a parameter because the two are asserted on separately.
 */
@Composable
internal fun VersionLine(tag: String, modifier: Modifier = Modifier) {
    Text(
        text = "v$CLIENT_VERSION",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.testTag(tag),
    )
}

/**
 * A progress bar as wide as whatever holds it.
 *
 * Three screens draw one — the level bar, a quest's own row, and the lobby's summary of both — and
 * they were three copies of the same twelve lines until the third arrived.
 */
@Composable
internal fun Meter(
    fraction: Float,
    modifier: Modifier = Modifier,
    colour: Color = MaterialTheme.colorScheme.tertiary,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = METER_TRACK)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(MeterHeight)
                .clip(MaterialTheme.shapes.small)
                .background(colour),
        )
    }
}

private const val METER_TRACK = 0.5f

internal val MeterHeight = 4.dp
