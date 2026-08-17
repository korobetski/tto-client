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

/** The bubble while it is on screen. */
const val TALK_BUBBLE_TEST_TAG: String = "talk-bubble"

/** The panel alone, so a test can ask whether the line is inside it. */
const val TALK_FRAME_TEST_TAG: String = "talk-frame"

/**
 * `TalkAnim` — an NPC saying one line.
 *
 * The bubble scales up as it fades in, the speaker's name and their line appear once it has
 * arrived, both are up for five seconds, and then it shrinks away to half size. Its three callers
 * in the original are the two group ladders and `TutorialScreen`, which is what this unblocks: the
 * tutorial *is* a sequence of these over a scripted match.
 *
 * ### Why the frame is drawn and no longer `talk_basic.tex`
 *
 * The AS3 frame is a parchment lens authored at 544x144 for a 1024-wide desktop stage, and it was
 * always the wrong shape for this port: the tutorial's sixty lines run past three rendered lines in
 * French, so the texture had to be sliced into caps and a stretched seam band to grow with its
 * sentence (`CAP_PIXELS` and its neighbours, now gone). A drawn panel takes the theme's own
 * surface, border and elevation, grows to any height for free, needs no atlas at boot, and removes
 * one more piece of Square Enix artwork from the build — see the legal note in `CLAUDE.md`.
 *
 * The **text stays text**, as it always was: `TextField(450, 75, _message, 'Raleway', 16, …)`, one
 * frame for every language, the line arriving as a string rather than as a texture id. That is the
 * original's design and it is the better one; the twenty rule captions are pictures only because
 * they are stylised wordmarks.
 *
 * ### Why the text appears after the bubble
 *
 * `predispose()` — the entry tween's `onComplete` — is what constructs both `TextField`s. A line
 * readable while its bubble is still flying in would be readable while it is moving, which is where
 * the original's structure is making an argument rather than an accident.
 *
 * ### Tapping it moves it on
 *
 * The original has no such control: every bubble is up for its five seconds and the next is
 * scheduled behind it. That is fine for the nine lines it had and wrong for a curriculum — a fast
 * reader waits out four seconds of nothing, on every line, and there are sixty of them. The timer
 * stays as the fallback, so a bubble nobody touches behaves exactly as it always did.
 *
 * The tap target is the **bubble**, not the screen. The composable fills its parent so the bubble
 * can be placed against the top of it, and making that box clickable would swallow every tap meant
 * for the board underneath — a player who wanted to place a card while a line was up could not.
 *
 * @param message the line, already translated. Wrapped rather than clipped: the AS3 sizes its
 *   field 450x75 with `autoSize = VERTICAL`, so a long line grows downward instead of vanishing.
 * @param speaker who is talking. `RED_PLAYER_NAME` in a ladder match, `STR_NPC_TT_Master` in the
 *   tutorial.
 * @param onFinished the line is done. Called once, from this composable's own coroutine, so a
 *   caller can advance a script on it.
 */
@Composable
internal fun TalkBubble(message: String, speaker: String, onFinished: () -> Unit) {
    val strings = LocalStrings.current
    val scale = remember(message) { Animatable(ENTER_SCALE) }
    val alpha = remember(message) { Animatable(0f) }
    var textUp by remember(message) { mutableStateOf(false) }
    var dismissed by remember(message) { mutableStateOf(false) }

    LaunchedEffect(message) {
        // **Together, not one after the other.** These were two suspending `animateTo` calls in a
        // row, so a "0.4s" entry took 0.8s and the exit another 0.8 — a second and a half of every
        // line spent on its own animation. `coroutineScope` returns when both have landed.
        coroutineScope {
            launch { alpha.animateTo(1f, tween(ENTER_MILLIS, easing = LinearEasing)) }
            scale.animateTo(1f, tween(ENTER_MILLIS, easing = FastOutSlowInEasing))
        }

        textUp = true // predispose(): the two TextFields are built here, not before.
        // Whichever comes first: the five seconds, or a tap. `withTimeoutOrNull` returning null is
        // the ordinary case and carries no meaning beyond "nobody touched it".
        withTimeoutOrNull(HOLD_MILLIS.toLong()) { snapshotFlow { dismissed }.first { it } }
        textUp = false // setTimeout(… visible = false, 5000)

        coroutineScope {
            launch { alpha.animateTo(0f, tween(EXIT_MILLIS, easing = LinearEasing)) }
            scale.animateTo(EXIT_SCALE, tween(EXIT_MILLIS, easing = FastOutSlowInEasing))
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

/**
 * The panel the line sits in — the same clothes as the outcome panel, one step smaller.
 *
 * `surfaceContainerHigh` at `OutcomeElevation` is what `MatchChrome` dresses the result panel in,
 * and a bubble drawn over the board wants exactly that separation: the board is the app's darkest
 * surface, so anything lighter reads as sitting above it. The hairline outline is what stops the
 * corners dissolving into the backdrop on the screens whose background is nearly the same tone.
 */
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

/**
 * The speaker above their line, as a nameplate rather than as a first word.
 *
 * The AS3 draws the name in white 14 on the frame's dark lip, where the shape of the artwork is
 * what separates it from the sentence. There is no lip on a drawn panel, so the separation has to
 * come from the type: `primary` — the theme's amber, the colour nothing else in a match uses for
 * text — bold, tracked out, and a rule under it. Left-aligned against a centred sentence, which is
 * the other half of what tells a label from what it labels.
 */
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

/**
 * `Starling.juggler.tween(gfx, 0.4, …)` — in, and out again.
 *
 * Shorter than the original's 0.4s, and much shorter than the 0.8s this actually ran at: sixty
 * lines is sixty entries, and the entry is time in which there is nothing to read. The hold is
 * untouched — that is the part a reader spends.
 */
private const val ENTER_MILLIS = 240
private const val EXIT_MILLIS = 280

/** `delay: 5` on the exit tween, and the `setTimeout` that hides the text at the same moment. */
private const val HOLD_MILLIS = 5_000

/**
 * `gfx.scaleX = gfx.scaleY = 1.5` before the entry tween, and `0.5` after the exit one.
 *
 * 1.15 rather than 1.5 on the way in. A bubble that starts half again as wide as it ends covers the
 * board it is explaining, and at this speed that much travel reads as a jolt; the exit keeps its
 * 0.5, where nothing is being read any more.
 */
private const val ENTER_SCALE = 1.15f
private const val EXIT_SCALE = 0.5f

/** Wider than the AS3's 272 dp half-scale, because nothing is tied to a texture's pixels now. */
private val BubbleWidth = 300.dp

/** The floor the AS3's 544x144 frame gave a three-word line. Kept: a lozenge is not a bubble. */
private val BubbleHeight = 72.dp

/** Round enough to read as speech, square enough to be the same family as the outcome panel. */
private val BubbleCorner = 20.dp

/**
 * How far down the screen it sits — `stage.height / 6` restated as a fraction that scales.
 *
 * The floor is the AS3's own 48 dp and the ceiling is the board's first row — see the call site,
 * where the reason a bigger number is not simply a nicer one is written down.
 */
private const val BUBBLE_TOP_FRACTION = 0.14f
private val BubbleTopMin = 48.dp
private val BubbleTopMax = 72.dp

/** Bold alone does not carry at `labelLarge`; tracked out, the name reads as a plate. */
private val SpeakerTracking = 0.6.sp
