package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchIntroStep
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import kotlinx.coroutines.delay

/*
 * When each animation plays, as opposed to what it looks like.
 *
 * `MatchBannerOverlay` draws one thing at a time and `MatchBanner` says how long each takes; this
 * is the layer between them and the match — which moment owes which animation, how a repeat is
 * told from a replay, and how long the rest of the screen has to wait for the intro. It sat inside
 * `MatchScreen` until that file passed detekt's limit on how many functions one file should hold,
 * and the split is the honest one: nothing here draws, and nothing here is about a match either.
 */

@Composable
internal fun bannerQueue(key: Any, state: MatchState, setup: MatchSetup): BannerEvent? {
    var event by remember(key) { mutableStateOf<BannerEvent?>(null) }
    var epoch by remember(key) { mutableStateOf(0) }

    LaunchedEffect(key, state.placement, state.lastPlay) {
        animationsFor(state, setup)
            .takeIf { it.isNotEmpty() }
            ?.let {
                event = BannerEvent(epoch, it)
                epoch++
            }
    }

    return event
}

internal fun animationsFor(state: MatchState, setup: MatchSetup): List<MatchAnimation> =
    if (state.lastPlay == null) {
        introAnimations(setup)
    } else {
        MatchBanner.afterPlacement(state).asAnimations()
    }

internal fun introAnimations(setup: MatchSetup): List<MatchAnimation> =
    opening(setup.intro.flatMap { step -> animationsForIntroStep(step) { setup.coinFlip } })

internal fun serverIntroAnimations(rules: GameRules, first: CardColor): List<MatchAnimation> =
    opening(
        MatchPreparation.introSteps(rules).flatMap { step ->
            animationsForIntroStep(step) { CoinFlip.forced(first) }
        },
    )

/**
 * The beat before the first caption — see [MatchAnimation.Opening].
 *
 * Only on an intro that has something to say. An empty list means there is no board to open, and a
 * pause in front of nothing is a pause the player waits out for no reason.
 */
private fun opening(intro: List<MatchAnimation>): List<MatchAnimation> =
    if (intro.isEmpty()) intro else listOf(MatchAnimation.Opening) + intro

/**
 * One step's animations, in the order they play.
 *
 * Every step but [MatchIntroStep.SWAP] is exactly its caption, or — for [MatchIntroStep.COIN_FLIP],
 * which has none — the toss [flip] supplies. Swap gets its caption *and* [MatchAnimation.SwapCards]
 * right behind it, so the rule is both named and shown.
 */
private fun animationsForIntroStep(
    step: MatchIntroStep,
    flip: () -> CoinFlip?,
): List<MatchAnimation> {
    val banner = MatchBanner.forIntroStep(step)
        ?: return listOfNotNull(flip()?.let(MatchAnimation::Toss))
    val caption = MatchAnimation.Caption(banner)
    return if (banner == MatchBanner.SWAP) {
        listOf(
            caption,
            MatchAnimation.SwapCards,
        )
    } else {
        listOf(caption)
    }
}

/**
 * The same queue for a match this client is not refereeing.
 *
 * [intro] is passed rather than derived from a `MatchSetup`, because a refereed client has none —
 * the deal happened on the server. `serverIntroAnimations` builds the equivalent from the two facts
 * that do travel: the rules in force and who won the toss.
 */
@Composable
internal fun pveBannerQueue(
    key: Any,
    view: MatchView,
    intro: List<MatchAnimation>,
): BannerEvent? {
    var event by remember(key) { mutableStateOf<BannerEvent?>(null) }
    var epoch by remember(key) { mutableStateOf(0) }

    LaunchedEffect(key, view.placement, view.lastPlay) {
        animationsFor(view, intro)
            .takeIf { it.isNotEmpty() }
            ?.let {
                event = BannerEvent(epoch, it)
                epoch++
            }
    }

    return event
}

internal fun animationsFor(view: MatchView, intro: List<MatchAnimation>): List<MatchAnimation> =
    if (view.lastPlay == null) intro else MatchBanner.afterPlacement(view).asAnimations()

/**
 * When the Open rule has finished announcing itself, or null if no Open rule is in force.
 *
 * The moment the opponent's revealed cards turn face up. Measured to the *end* of the caption
 * rather than its start, so the cards turn as the words leave rather than underneath them — which
 * is the difference between the rule explaining what is about to happen and the two competing.
 *
 * Null and "0" are different answers and the caller has to tell them apart: no Open rule means
 * there is nothing to turn, not that everything turns immediately.
 */
internal fun openRevealMillis(intro: List<MatchAnimation>): Int? {
    val at = intro.indexOfFirst {
        it is MatchAnimation.Caption &&
            (it.banner == MatchBanner.ALL_OPEN || it.banner == MatchBanner.THREE_OPEN)
    }
    return if (at < 0) null else intro.take(at + 1).sumOf { it.totalMillis }
}

/**
 * Whether the opponent's revealed cards should be face up yet.
 *
 * True from the first frame when no Open rule is in force: there is nothing revealed to turn, and a
 * hand of backs that waits for a caption nobody played would simply be a hand of backs.
 */
@Composable
internal fun openRevealed(key: Any, intro: List<MatchAnimation>): Boolean {
    val at = remember(key, intro) { openRevealMillis(intro) }
    var revealed by remember(key, intro) { mutableStateOf(at == null) }
    val pacing = LocalPacing.current

    LaunchedEffect(key, intro, pacing) {
        if (at == null) {
            revealed = true
            return@LaunchedEffect
        }
        revealed = false
        delay(pacing * at.toLong())
        revealed = true
    }

    return revealed
}

@Composable
internal fun pveIntroFinished(key: Any, intro: List<MatchAnimation>): Boolean {
    var done by remember(key) { mutableStateOf(false) }
    val pacing = LocalPacing.current

    LaunchedEffect(key, pacing) {
        done = false
        delay(pacing * intro.sumOf { it.totalMillis }.toLong())
        done = true
    }

    return done
}

@Composable
internal fun introFinished(key: Any, setup: MatchSetup): Boolean {
    // Keyed on the setup as well: a sudden-death rematch replaces it in place without changing
    // the match, and plays a second intro that has to be waited through in turn.
    var done by remember(key, setup) { mutableStateOf(false) }
    val pacing = LocalPacing.current

    LaunchedEffect(key, setup, pacing) {
        done = false
        delay(pacing * introAnimations(setup).sumOf { it.totalMillis }.toLong())
        done = true
    }

    return done
}
