package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MATCH_BANNER_TEST_TAG: String = "match-banner"

fun matchBannerTestTag(banner: MatchBanner): String = "match-banner-${banner.name}"

internal sealed interface MatchAnimation {
    val totalMillis: Int

    data class Caption(val banner: MatchBanner) : MatchAnimation {
        override val totalMillis: Int get() = banner.totalMillis
    }

    data class Toss(val flip: CoinFlip) : MatchAnimation {
        override val totalMillis: Int get() = COIN_FLIP_TOTAL_MILLIS
    }

    /**
     * A blue card and a red card crossing paths — `RULE_SWAP`'s caption names the rule, this shows
     * it. The exchange it announces already happened, on the server or in `MatchPreparation.swap`,
     * before either hand was dealt, and the client is never told which two cards changed sides — so
     * this is deliberately generic rather than pretending to know.
     */
    data object SwapCards : MatchAnimation {
        override val totalMillis: Int get() = SWAP_CARDS_TOTAL_MILLIS
    }

    /**
     * Nothing on screen, for a beat, before the first caption.
     *
     * A board used to arrive and announce itself on the same frame, so the rules were being read
     * out before the player had looked at the cards they were dealt. Modelled as an animation
     * rather than a `delay` in each screen because the two intro gates — `introFinished` and
     * `pveIntroFinished` — measure the intro by summing [totalMillis], and a wait they cannot see
     * is a wait that lets the clock start early.
     */
    data object Opening : MatchAnimation {
        override val totalMillis: Int get() = MATCH_OPENING_MILLIS
    }
}

internal fun List<MatchBanner>.asAnimations(): List<MatchAnimation> =
    map(MatchAnimation::Caption)

internal data class BannerEvent(val at: Int, val animations: List<MatchAnimation>)

@Composable
internal fun MatchBannerOverlay(event: BannerEvent?) {
    val pending = remember { mutableStateListOf<MatchAnimation>() }
    var playing by remember { mutableStateOf<MatchAnimation?>(null) }

    // Keyed on the event rather than run on every composition, so a recomposition for an
    // unrelated reason — a score updating, the turn clock ticking — does not replay it.
    LaunchedEffect(event) {
        event?.animations
            ?.take(QUEUE_LIMIT - pending.size)
            ?.let(pending::addAll)
    }

    LaunchedEffect(pending.size, playing) {
        if (playing == null && pending.isNotEmpty()) {
            playing = pending.removeAt(0)
        }
    }

    when (val current = playing) {
        null -> Unit
        is MatchAnimation.Caption -> Caption(current.banner) { playing = null }
        is MatchAnimation.Toss -> CoinFlipCards(current.flip) { playing = null }
        is MatchAnimation.SwapCards -> SwapCardsCrossing { playing = null }
        is MatchAnimation.Opening -> Beat(current.totalMillis) { playing = null }
    }
}

/** A wait with nothing drawn over it. */
@Composable
private fun Beat(millis: Int, onFinished: () -> Unit) {
    val pacing = LocalPacing.current

    LaunchedEffect(millis, pacing) {
        delay(pacing * millis.toLong())
        onFinished()
    }
}

@Composable
private fun Caption(banner: MatchBanner, onFinished: () -> Unit) {
    val art = LocalBannerArt.current
    val image = rememberCaption(art, banner)

    if (art == null) {
        LaunchedEffect(banner) { onFinished() }
        return
    }
    if (image == null) {
        val pacing = LocalPacing.current
        LaunchedEffect(banner, pacing) {
            delay(pacing * banner.totalMillis.toLong())
            onFinished()
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize().testTag(MATCH_BANNER_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        BannerImage(banner = banner, image = image, onFinished = onFinished)
    }
}

@Composable
private fun BannerImage(
    banner: MatchBanner,
    image: androidx.compose.ui.graphics.ImageBitmap,
    onFinished: () -> Unit,
) {
    val scale = remember(banner) { Animatable(banner.motion.enterScale) }
    val alpha = remember(banner) { Animatable(0f) }
    val offset = remember(banner) { Animatable(banner.motion.enterOffset) }
    val rotation = remember(banner) { Animatable(0f) }
    val pacing = LocalPacing.current

    LaunchedEffect(banner, pacing) {
        if (banner.leadInMillis > 0) delay(pacing * banner.leadInMillis.toLong())

        val enter = tween<Float>(pacing * banner.enterMillis, easing = LinearEasing)
        launch { scale.animateTo(1f, enter) }
        launch { offset.animateTo(0f, enter) }
        alpha.animateTo(1f, enter)

        delay(pacing * banner.holdMillis.toLong())

        val exit = tween<Float>(pacing * banner.exitMillis, easing = LinearEasing)
        // `SuddenDeathAnim` tilts as it goes; nothing else rotates at all.
        if (banner.motion == BannerMotion.ZOOM_BOUNCE) {
            launch { rotation.animateTo(BOUNCE_DEGREES, exit) }
        }
        launch { scale.animateTo(banner.motion.exitScale, exit) }
        launch { offset.animateTo(banner.motion.exitOffset, exit) }
        alpha.animateTo(0f, exit)

        onFinished()
    }

    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .testTag(matchBannerTestTag(banner))
            // Named so a screen reader announces the rule rather than skipping it; the
            // caption is the only place some of these rules are ever spelled out.
            .semantics { contentDescription = banner.name }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = rotation.value
                if (banner.motion.isVertical) {
                    translationY = offset.value * size.height
                } else {
                    translationX = offset.value * size.width
                }
            },
    )
}

const val SWAP_CARDS_TEST_TAG: String = "swap-cards"

fun swapCardsTestTag(color: CardColor): String = "$SWAP_CARDS_TEST_TAG-${color.name}"

internal const val SWAP_CARDS_TOTAL_MILLIS: Int = 700

/** The beat a board takes before it starts announcing itself. See [MatchAnimation.Opening]. */
internal const val MATCH_OPENING_MILLIS: Int = 400

/**
 * The blue card leaves from the left as the red one arrives from the right, and each keeps going
 * past the other's starting side — one continuous cross rather than a meet-in-the-middle, so the
 * two never appear to pause on top of each other.
 *
 * `internal` rather than private so a test can render it on its own, the way [CoinFlipCards] is.
 * Asserting it *through* [MatchBannerOverlay] does not work: under `runComposeUiTest` the crossing
 * runs to completion before the first layout pass, so the queue has already cleared it by the time
 * a node could be found. `MatchBannerTest` covers its place in the queue instead.
 */
@Composable
internal fun SwapCardsCrossing(onFinished: () -> Unit) {
    val pacing = LocalPacing.current
    // One timeline for both cards rather than a timer per card, for the reason `CoinFlipCards`
    // gives about its own: the queue's timing must not depend on which of two parallel animations
    // happens to settle last.
    val progress = remember { Animatable(0f) }

    Box(
        modifier = Modifier.fillMaxSize().testTag(SWAP_CARDS_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        SwapCard(CardColor.BLUE, from = -SWAP_OFF_SCREEN, to = SWAP_OFF_SCREEN, progress = progress)
        SwapCard(CardColor.RED, from = SWAP_OFF_SCREEN, to = -SWAP_OFF_SCREEN, progress = progress)
    }

    LaunchedEffect(pacing) {
        progress.animateTo(
            1f,
            tween(pacing * SWAP_CARDS_TOTAL_MILLIS, easing = LinearEasing),
        )
        onFinished()
    }
}

/** One card, crossing from [from] to [to] as [progress] runs, faded at both ends of the pass. */
@Composable
private fun SwapCard(
    color: CardColor,
    from: Float,
    to: Float,
    progress: Animatable<Float, *>,
) {
    CardBack(
        color = color,
        scale = SWAP_CARD_SCALE,
        modifier = Modifier
            .testTag(swapCardsTestTag(color))
            // A card back says nothing to a screen reader on its own; the SWAP caption is what
            // names the rule, this only has to say which side is moving.
            .semantics { contentDescription = swapCardsTestTag(color) }
            .graphicsLayer {
                val fraction = progress.value
                translationX = (from + (to - from) * fraction) * size.minDimension
                // Up from nothing over the first quarter and back down over the last, so neither
                // card is ever seen standing still at the edge it came from.
                alpha = (fraction / SWAP_FADE).coerceAtMost(1f) *
                    ((1f - fraction) / SWAP_FADE).coerceAtMost(1f)
            },
    )
}

/** The share of the crossing each card spends fading in, and again fading out. */
private const val SWAP_FADE = 0.25f

private const val SWAP_CARD_SCALE = 0.8f

private const val SWAP_OFF_SCREEN = 3f

private val BannerMotion.enterOffset: Float
    get() = when (this) {
        BannerMotion.SLIDE_RIGHT -> -SLIDE_DISTANCE
        BannerMotion.SLIDE_LEFT -> SLIDE_DISTANCE
        else -> 0f
    }

private val BannerMotion.exitOffset: Float
    get() = when (this) {
        BannerMotion.SLIDE_RIGHT -> SLIDE_DISTANCE
        BannerMotion.SLIDE_LEFT -> -SLIDE_DISTANCE
        BannerMotion.ZOOM_UP -> -SLIDE_DISTANCE
        BannerMotion.ZOOM_DOWN -> SLIDE_DISTANCE
        else -> 0f
    }

private val BannerMotion.enterScale: Float
    get() = when (this) {
        BannerMotion.SLIDE_LEFT, BannerMotion.SLIDE_RIGHT -> 1f
        BannerMotion.ZOOM_BOUNCE -> 3f
        else -> 2f
    }

private val BannerMotion.exitScale: Float
    get() = if (this == BannerMotion.ZOOM) 2f else 1f

private val BannerMotion.isVertical: Boolean
    get() = this == BannerMotion.ZOOM_UP || this == BannerMotion.ZOOM_DOWN

private const val BOUNCE_DEGREES = 7.5f

private const val SLIDE_DISTANCE = 1f

private const val QUEUE_LIMIT = 4
