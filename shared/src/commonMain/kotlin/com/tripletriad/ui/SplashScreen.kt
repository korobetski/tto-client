package com.tripletriad.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import androidx.compose.foundation.Image as ComposeImage

const val SPLASH_PHASE_TEST_TAG: String = "splash-phase"

const val SPLASH_LOGO_TEST_TAG: String = "splash-logo"

@Composable
internal fun SplashScreen(state: StartupState) {
    val strings = LocalStrings.current
    // Its own load rather than a `StartupPhase`: 15 KB of chrome that the first frame wants,
    // before the phase it would otherwise sit in. Null renders as reserved space, not a gap.
    val logo by produceState<ImageBitmap?>(initialValue = null) { value = loadLogo() }

    // Animated so the bar slides between phases instead of jumping. Phases are coarse — four of
    // them — and an unanimated bar at 33% then 66% reads as a stall followed by a lurch.
    val progress by animateFloatAsState(
        targetValue = state.phase.progress,
        label = "startup-progress",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Fixed height whether or not the bitmap has arrived, so nothing below it moves when it
        // does. `MenuScreen.as:43-46` centres the same wordmark above its stack.
        Box(
            modifier = Modifier.height(LogoHeight).widthIn(max = LogoMaxWidth),
            contentAlignment = Alignment.Center,
        ) {
            logo?.let {
                ComposeImage(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().testTag(SPLASH_LOGO_TEST_TAG),
                )
            }
        }

        Text(
            text = strings[state.phase.labelKey],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = SpaceXxl, bottom = SpaceMd)
                .testTag(SPLASH_PHASE_TEST_TAG),
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().widthIn(max = LogoMaxWidth).height(3.dp),
            color = MaterialTheme.colorScheme.tertiary,
            // `surfaceContainerHighest` is Material's own track role. It was an alpha over
            // `onSurface`, which is a way of asking for a surface without having one to ask for.
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private val LogoMaxWidth = 512.dp
private val LogoHeight = 128.dp
