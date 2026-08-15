package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The bubble while it is on screen. */
const val TALK_BUBBLE_TEST_TAG: String = "talk-bubble"

/** The frame artwork alone, so a test can ask whether the line is inside it. */
const val TALK_FRAME_TEST_TAG: String = "talk-frame"

/**
 * `TalkAnim` — an NPC saying one line.
 *
 * The bubble scales down from 1.5x as it fades in, the speaker's name and their line appear once
 * it has arrived, both are up for five seconds, and then it shrinks away to half size. Its three
 * callers in the original are the two group ladders and `TutorialScreen`, which is what this
 * unblocks: the tutorial *is* a sequence of these over a scripted match.
 *
 * ### Why the text is text and not a picture
 *
 * Unlike the twenty rule captions, which are images of words in four languages, this one is a
 * frame with live text drawn into it — `TextField(450, 75, _message, 'Raleway', 16, …)`. So the
 * asset is one file for every language, and the line arrives as a string rather than as a texture
 * id. That is the original's design and it is the better one; the captions are pictures only
 * because they are stylised wordmarks.
 *
 * ### Why the text appears after the bubble
 *
 * `predispose()` — the entry tween's `onComplete` — is what constructs both `TextField`s. A line
 * readable while its bubble is still flying in would be readable at 1.5x and moving, which is
 * where the original's structure is making an argument rather than an accident.
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
    val art = LocalCardArt.current
    val scale = remember(message) { Animatable(ENTER_SCALE) }
    val alpha = remember(message) { Animatable(0f) }
    var textUp by remember(message) { mutableStateOf(false) }

    LaunchedEffect(message) {
        val entering = tween<Float>(ENTER_MILLIS, easing = LinearEasing)
        alpha.animateTo(1f, entering)
        scale.animateTo(1f, entering)

        textUp = true // predispose(): the two TextFields are built here, not before.
        delay(HOLD_MILLIS.toLong())
        textUp = false // setTimeout(… visible = false, 5000)

        val leaving = tween<Float>(EXIT_MILLIS, easing = LinearEasing)
        alpha.animateTo(0f, leaving)
        scale.animateTo(EXIT_SCALE, leaving)

        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().testTag(TALK_BUBBLE_TEST_TAG),
        // `gfx.y = stage.height / 6` — high on the screen, over whatever is behind it.
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = BubbleTop)
                .width(BubbleWidth)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
            contentAlignment = Alignment.Center,
        ) {
            // `matchParentSize` keeps the frame out of the measuring, so the box is exactly as
            // tall as the line and the frame then fills that.
            Frame(art?.talk, modifier = Modifier.matchParentSize().testTag(TALK_FRAME_TEST_TAG))
            // The line is **laid out** from the first frame and only revealed on `textUp`, so the
            // bubble flies in at the size it will keep; composing it late would have the frame
            // snap taller the moment the text arrived. Hidden by alpha *and* by clearing its
            // semantics, so it is no more readable to a screen reader than to the eye —
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

/**
 * The bubble itself, stretched to whatever height its line needs.
 *
 * ### Why this is sliced and not simply drawn
 *
 * The AS3 draws `talk_basic.tex` at 1:1 into a 544x144 frame and lets a long line overflow it,
 * which on a phone means sentences hanging outside the bubble: the nine tutorial lines run to 189
 * characters in French, and at this width that is seven wrapped lines against a frame that holds
 * three.
 *
 * The artwork makes the fix cheap. It is a capsule with **no tail and no asymmetry** — rows 62 to
 * 82 of the texture are pixel-for-pixel identical, the widest part of the lens — so the top and
 * bottom caps can be drawn at their authored size and that seam band stretched to cover the rest.
 * A horizontal slice would need the same treatment for the two side notches; nothing asks for it,
 * since the width is fixed and only the height varies.
 *
 * @param modifier sized by the caller, which sizes it from the text — see [TalkBubble].
 */
@Composable
private fun Frame(bitmap: ImageBitmap?, modifier: Modifier) {
    if (bitmap == null) {
        // No artwork, no frame — the line is still readable, which is the part that matters.
        Box(modifier = modifier)
        return
    }
    Column(modifier = modifier) {
        Slice(bitmap, top = 0, height = CAP_PIXELS, modifier = Modifier.height(CapHeight))
        // The one band that may be stretched, and the only one that takes the slack.
        Slice(bitmap, top = CAP_PIXELS, height = SEAM_PIXELS, modifier = Modifier.weight(1f))
        Slice(
            bitmap,
            top = bitmap.height - CAP_PIXELS,
            height = CAP_PIXELS,
            modifier = Modifier.height(CapHeight),
        )
    }
}

/** One horizontal band of the texture, drawn across the full width of the bubble. */
@Composable
private fun Slice(bitmap: ImageBitmap, top: Int, height: Int, modifier: Modifier) {
    Image(
        painter = BitmapPainter(
            image = bitmap,
            srcOffset = IntOffset(0, top),
            srcSize = IntSize(bitmap.width, height),
        ),
        contentDescription = null,
        // FillBounds and not Fit: the point of the middle band is that it may be the wrong shape.
        contentScale = ContentScale.FillBounds,
        modifier = modifier.fillMaxWidth(),
    )
}

/** The speaker above their line: white over the frame's dark lip, dark grey inside it. */
@Composable
private fun Line(message: String, speaker: String, modifier: Modifier) {
    Column(
        modifier = modifier
            // The insets clear the frame's border and its two side notches, and the minimum is
            // the authored 72 dp: a three-word line should still be drawn in the bubble the
            // original had, not in a lozenge shrunk around it.
            .heightIn(min = BubbleHeight)
            .width(TextWidth)
            .padding(horizontal = TextInset, vertical = TextInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = speaker,
            color = SpeakerText,
            fontSize = SpeakerSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Text(
            text = message,
            color = MessageText,
            fontSize = MessageSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** `Starling.juggler.tween(gfx, 0.4, …)` — in, and out again. */
private const val ENTER_MILLIS = 400
private const val EXIT_MILLIS = 400

/** `delay: 5` on the exit tween, and the `setTimeout` that hides the text at the same moment. */
private const val HOLD_MILLIS = 5_000

/** `gfx.scaleX = gfx.scaleY = 1.5` before the entry tween, and `0.5` after the exit one. */
private const val ENTER_SCALE = 1.5f
private const val EXIT_SCALE = 0.5f

/** The `talk_basic.tex` texture is 544x144, drawn at half that — the AS3 art is 2x. */
private val BubbleWidth = 272.dp
private val BubbleHeight = 72.dp

/**
 * Where the texture may be cut, in its own pixels.
 *
 * Rows 62 to 82 are identical — the widest span of the lens, `x` 9 to 533 on every one of them —
 * so that band is what stretches and the 62-row caps above and below it are drawn as they are.
 */
private const val CAP_PIXELS = 62
private const val SEAM_PIXELS = 20
private val CapHeight = 31.dp

/** `TextField(450, …)` against a 544-wide frame: the text stops short of the border. */
private val TextWidth = 272.dp

/** Enough to clear the border and the two side notches at the frame's narrowest. */
private val TextInset = 24.dp

/** `stage.height / 6`, as a distance from the top rather than a fraction of an unknown stage. */
private val BubbleTop = 48.dp

/**
 * `0xffffff`, `14` in the AS3 — where the name sat on the frame's dark lip.
 *
 * Dark here, because it no longer does: a bubble that grows with its line puts the name inside the
 * light fill on every line long enough to matter, and white on parchment is not readable. Same ink
 * as the line, a size smaller and left-aligned, which is what separates a label from its sentence.
 */
private val SpeakerText = Color(0xFF202020)
private val SpeakerSize = 12.sp

/** `0x202020`, `16` — the line is inside the frame, which is light. */
private val MessageText = Color(0xFF202020)
private val MessageSize = 13.sp
