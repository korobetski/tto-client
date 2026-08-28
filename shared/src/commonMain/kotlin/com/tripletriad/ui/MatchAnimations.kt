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
    /*
     * Whether this board has already been opened.
     *
     * The discriminator used to be `view.lastPlay == null` — "nothing has happened yet, so this is
     * the intro". It is not the same question, and on half of all matches it gives the wrong
     * answer: the deal the referee sends back **already contains the opponent's first placement**
     * when the toss gave them the opening move (`PveRoutes.opening`). So the first view of a fresh
     * board arrives at placement one with a `lastPlay`, the intro branch was never taken, and the
     * whole opening — every rule caption, the coin flip that decided the very move on the board,
     * and START — was skipped. `pveIntroFinished` still counted its duration, so the turn clock
     * waited out three seconds of announcements nobody was shown.
     *
     * Held as state rather than derived because there is nothing on a [MatchView] to derive it
     * from: a board is opened once, and only this composition knows whether it has been.
     */
    var opened by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, view.placement, view.lastPlay) {
        animationsFor(view, intro.takeUnless { opened }.orEmpty())
            .takeIf { it.isNotEmpty() }
            ?.let {
                event = BannerEvent(epoch, it)
                epoch++
            }
        opened = true
    }

    return event
}

/**
 * The opening announcements, the placement's own, or — on a board dealt to an opponent who moves
 * first — both, in that order.
 *
 * Concatenated rather than chosen between. `MatchBanner.afterPlacement` answers empty for a board
 * nothing has been played on, so the ordinary opening still yields exactly [intro]; and a caller
 * that has already opened this board passes an empty [intro] and gets exactly the captions the
 * placement earned. There is no third case, which is why there is no branch.
 */
internal fun animationsFor(view: MatchView, intro: List<MatchAnimation>): List<MatchAnimation> =
    intro + MatchBanner.afterPlacement(view).asAnimations()

/**
 * The same queue again for a match against a **person**, where the client is one of two audiences.
 *
 * Moved here from `PvpMatchScreen` to sit beside [pveBannerQueue] and [bannerQueue]: three
 * functions answering "which moment owes which animation" belong in the file that is about exactly
 * that, and the PvP one was the odd one out.
 *
 * Where it differs from [pveBannerQueue] is what "the opening" means. There, a board arrives dealt
 * and a first placement may already be on it, so the intro is owed **once** and tracked as such.
 * Here the board arrives empty for both players and the risk runs the other way: a client that
 * *joins late* — a reconnection, a second device — missed the announcements, and replaying them
 * now would be reporting a moment that has passed. So the test is `joinedAt`, what this client had
 * in front of it the first time it looked.
 */
@Composable
internal fun pvpBannerQueue(board: Any?, view: MatchView?): BannerEvent? {
    var event by remember(board) { mutableStateOf<BannerEvent?>(null) }
    // What this client had seen when it arrived. Anything at or below it is history, not news.
    val joinedAt = remember(board) { view?.placement ?: 0 }

    LaunchedEffect(board, view?.placement) {
        if (board == null || view == null) return@LaunchedEffect

        val animations = when {
            view.placement > joinedAt -> MatchBanner.afterPlacement(view).asAnimations()
            view.placement > 0 -> emptyList()
            else -> serverIntroAnimations(view.rules, view.order.first)
        }
        animations.takeIf { it.isNotEmpty() }
            ?.let { event = BannerEvent(at = view.placement, animations = it) }
    }

    return event
}

/**
 * The opening this client owes a match against a person, which is empty unless it saw the start.
 *
 * Only a client that **arrived at the opening** has one, for the reason [pvpBannerQueue] gives: a
 * late joiner — a reconnection, a second device — missed the announcements, and playing them now
 * would be reporting a moment that has passed.
 *
 * Keyed on *whether there is a view* rather than on what is in it, which is the load-bearing part:
 * the answer is what this client saw the **first** time it looked, and a key that moved with
 * `placement` would empty the list the instant a card landed — turning cards over and starting
 * clocks that were meant to be waiting.
 *
 * [board] is the match **and which board of it** — a Sudden Death rematch keeps the match id and
 * begins a new board, and it is owed its own opening.
 */
@Composable
internal fun pvpIntro(board: Any?, view: MatchView?): List<MatchAnimation> =
    remember(board, view != null) {
        if (view == null || view.placement > 0) {
            emptyList()
        } else {
            serverIntroAnimations(view.rules, view.order.first)
        }
    }

/**
 * When the opponent's face-up cards turn over, or null when the intro shows none.
 *
 * ### Two rules put a card face up, not one
 *
 * The obvious one is Open, and this used to look for it alone. The other is **Swap**: a player
 * hands over a card out of their own deck, so they know it on sight in the hand it lands in, and
 * `MatchPreparation.swap` reports the slot for exactly that — `HandVisibility.forRule` takes it as
 * `known` and marks it visible whatever the Open rule says. Under a closed hand it is the one card
 * of five the opponent can name.
 *
 * So with Swap and no Open there was nothing here to wait for, this answered null, and
 * [openRevealed] turned that card face up **on the first frame** — before the rule that moved it
 * had been announced and before the two cards had been seen crossing. The player was shown the
 * result of a swap and then told a swap was going to happen.
 *
 * ### The later of the two, not the first
 *
 * Measured to the *end* of whichever comes last, so the cards turn as the last of the words leaves
 * rather than underneath it — the difference between a rule explaining what is about to happen and
 * the two competing. `indexOfLast` rather than a test on which rule is in force: the two are
 * independent, both can be on, and the answer is the same question either way — when has the intro
 * finished doing things to the opponent's hand.
 *
 * Null and "0" are different answers and the caller has to tell them apart: neither rule in force
 * means there is nothing to turn, not that everything turns immediately.
 */
internal fun revealMillis(intro: List<MatchAnimation>): Int? {
    val at = intro.indexOfLast { it is MatchAnimation.SwapCards || it.revealsAHand() }
    return if (at < 0) null else intro.take(at + 1).sumOf { it.totalMillis }
}

/** Whether this animation is an Open rule naming itself. */
private fun MatchAnimation.revealsAHand(): Boolean =
    this is MatchAnimation.Caption &&
        (banner == MatchBanner.ALL_OPEN || banner == MatchBanner.THREE_OPEN)

/**
 * Whether the opponent's revealed cards should be face up yet.
 *
 * True from the first frame when neither Open nor Swap is in force: there is nothing revealed to
 * turn, and a hand of backs that waits for a caption nobody played would simply be a hand of backs.
 */
@Composable
internal fun openRevealed(key: Any, intro: List<MatchAnimation>): Boolean {
    val at = remember(key, intro) { revealMillis(intro) }
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
