package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

const val TALK_BUBBLE_TEST_TAG: String = "talk-bubble"

const val TALK_FRAME_TEST_TAG: String = "talk-frame"

@Composable
internal fun TalkBubble(message: String, speaker: String, onFinished: () -> Unit) {
    val strings = LocalStrings.current
    val scale = remember(message) { Animatable(ENTER_SCALE) }
    val alpha = remember(message) { Animatable(0f) }
    var textUp by remember(message) { mutableStateOf(false) }
    var dismissed by remember(message) { mutableStateOf(false) }
    val pacing = LocalPacing.current

    LaunchedEffect(message, pacing) {
        // **Together, not one after the other.** These were two suspending `animateTo` calls in a
        // row, so a "0.4s" entry took 0.8s and the exit another 0.8 — a second and a half of every
        // line spent on its own animation. `coroutineScope` returns when both have landed.
        coroutineScope {
            launch { alpha.animateTo(1f, tween(pacing * ENTER_MILLIS, easing = LinearEasing)) }
            scale.animateTo(1f, tween(pacing * ENTER_MILLIS, easing = FastOutSlowInEasing))
        }

        textUp = true // predispose(): the two TextFields are built here, not before.
        // Whichever comes first: the five seconds, or a tap. `withTimeoutOrNull` returning null is
        // the ordinary case and carries no meaning beyond "nobody touched it".
        //
        // `withTimeoutOrNull(0)` would never let the tap win, so a scaled-down hold keeps a
        // millisecond: the branch a test exercises has to be the one the player exercises.
        withTimeoutOrNull((pacing * HOLD_MILLIS.toLong()).coerceAtLeast(1L)) {
            snapshotFlow { dismissed }.first { it }
        }
        textUp = false // setTimeout(… visible = false, 5000)

        coroutineScope {
            launch { alpha.animateTo(0f, tween(pacing * EXIT_MILLIS, easing = LinearEasing)) }
            scale.animateTo(EXIT_SCALE, tween(pacing * EXIT_MILLIS, easing = FastOutSlowInEasing))
        }

        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().testTag(TALK_BUBBLE_TEST_TAG),
        contentAlignment = Alignment.TopCenter,
    ) {
        // `gfx.y = stage.height / 6` was a sixth of a fixed 768-high stage — 48 dp here, and tight
        // enough against the top bar on a tall phone that the line reads as chrome rather than as
        // someone speaking. A fraction instead, so it breathes on the screens that have the room.
        //
        // **The ceiling is not a shy number.** The bubble is `clickable` — that is what advances a
        // line early — so wherever it overlaps the board it takes the tap the cell underneath
        // wanted, and a player aiming at a cell places nothing. At 96 dp it covered the first row's
        // centres on a 480 dp-tall window and `TutorialUiTest.theCourseEndsAtTheRuleBook` stopped
        // being able to finish a lesson, which is exactly the player's experience stated as a test.
        // 72 dp is what clears the board on the shortest window the app lays out for.
        val top = (maxHeight * BUBBLE_TOP_FRACTION).coerceIn(BubbleTopMin, BubbleTopMax)

        Box(
            modifier = Modifier
                .padding(top = top, start = SpaceLg, end = SpaceLg)
                .widthIn(max = BubbleWidth)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                // Not `ttoClickable`: that grows a target to 48 dp, and this one is already 300 dp
                // wide. What it does need is the role and a label, so a screen reader announces the
                // bubble as something that can be dismissed rather than as loose text.
                .clickable(role = Role.Button, onClickLabel = strings[StringKeys.CONTINUE]) {
                    dismissed = true
                },
        ) {
            Frame {
                // The line is **laid out** from the first frame and only revealed on `textUp`, so
                // the bubble flies in at the size it will keep; composing it late would have the
                // frame snap taller the moment the text arrived. Hidden by alpha *and* by clearing
                // its semantics, so it is no more readable to a screen reader than to the eye —
                // `predispose()` did not build the fields until the tween had landed.
                Line(
                    message = message,
                    speaker = speaker,
                    modifier = if (textUp) {
                        Modifier
                    } else {
                        Modifier.alpha(0f).clearAndSetSemantics {}
                    },
                )
            }
        }
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(TALK_FRAME_TEST_TAG),
        shape = RoundedCornerShape(BubbleCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = OutcomeElevation,
        shadowElevation = OutcomeElevation,
        border = BorderStroke(HairlineWidth, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
private fun Line(message: String, speaker: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = BubbleHeight)
            .padding(horizontal = SpaceLg, vertical = SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceXs, Alignment.CenterVertically),
    ) {
        Text(
            text = speaker,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = SpeakerTracking,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = HairlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SpaceXs),
        )
    }
}

private const val ENTER_MILLIS = 240
private const val EXIT_MILLIS = 280

private const val HOLD_MILLIS = 5_000

private const val ENTER_SCALE = 1.15f
private const val EXIT_SCALE = 0.5f

private val BubbleWidth = 300.dp

private val BubbleHeight = 72.dp

private val BubbleCorner = 20.dp

private const val BUBBLE_TOP_FRACTION = 0.14f
private val BubbleTopMin = 48.dp
private val BubbleTopMax = 72.dp

private val SpeakerTracking = 0.6.sp
