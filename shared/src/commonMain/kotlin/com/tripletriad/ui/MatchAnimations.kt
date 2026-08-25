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
    setup.intro.mapNotNull { step ->
        MatchBanner.forIntroStep(step)?.let(MatchAnimation::Caption)
            ?: setup.coinFlip?.let(MatchAnimation::Toss)
    }

internal fun serverIntroAnimations(rules: GameRules, first: CardColor): List<MatchAnimation> =
    MatchPreparation.introSteps(rules).mapNotNull { step ->
        MatchBanner.forIntroStep(step)?.let(MatchAnimation::Caption)
            ?: MatchAnimation.Toss(CoinFlip.forced(first))
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
