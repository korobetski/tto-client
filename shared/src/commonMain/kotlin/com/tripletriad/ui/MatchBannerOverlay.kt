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
