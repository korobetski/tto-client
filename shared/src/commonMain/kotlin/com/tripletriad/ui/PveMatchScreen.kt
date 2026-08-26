package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.MatchReward
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.Card
import com.tripletriad.model.DailyQuestCatalog
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchView
import com.tripletriad.model.Npc
import com.tripletriad.protocol.PveMove
import com.tripletriad.protocol.RewardSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val PVE_RECONNECT_TEST_TAG: String = "pve-reconnect"

const val PVE_DEALING_TEST_TAG: String = "pve-dealing"

/**
 * A match against an opponent, **refereed**.
 *
 * ### What this screen no longer does
 *
 * It does not deal, does not roll the roulette, does not toss for the opening move, does not run
 * `MatchAi`, does not score and does not pay. `MatchScreen` did all six, and the reason it must not
 * is narrower than "cheating": to let the server replay a solo match, this process had to be
 * running the same AI from the same seed — so it held the opponent's five cards and knew every move
 * they were going to make from the first placement. Nothing in the transcript showed it, because
 * the match really did happen as claimed.
 *
 * What is left here is a screen: draw a [MatchView], post a [PveMove], draw the next one.
 *
 * ### Why the answer is walked rather than adopted
 *
 * One request applies the player's card **and** the opponent's reply, because asking separately
 * would put a round trip in front of every turn. Adopting the response directly would land two
 * cards on the same frame, and the reply is the half worth watching — so the exchange is stepped
 * through with [MatchView.after], one placement at a time, and the server's view taken at the end.
 *
 * ### Losing the connection
 *
 * Shows a way back rather than a defeat. There is nothing to save locally: the match is a row on
 * the server and [PveSession.resume] is the whole of the recovery. That is the difference between a
 * tunnel and an abandon, and it is why `pve_matches` has no deadline column.
 */
@Composable
@Suppress("LongParameterList")
internal fun PveMatchScreen(
    session: PveSession,
    catalog: CardCatalog,
    npc: Npc,
    onExit: () -> Unit,
    turnLimit: Duration = PVE_TURN_LIMIT,
    // How long the opponent appears to think, on top of waiting for the board to go quiet. A
    // parameter so a fixture can take it to zero: a test that drives nine placements should not
    // spend ten seconds watching somebody pretend to deliberate.
    thinking: Duration = PVE_THINKING,
    // The campaign's lines, and nothing else a script can normally do. `MatchScript` also fixes the
    // deal, forces the coin flip and weakens the opponent, none of which survives here: those are
    // the referee's decisions now, and a client that could ask for a rigged deal is exactly what
    // moving the match to the server was for. The tutorial still uses all of them — see
    // [MatchScreen], which is local and settles nothing.
    script: MatchScript? = null,
    scriptExit: ScriptExit? = null,
    onResult: (MatchResult) -> Unit = {},
) {
    val strings = LocalStrings.current
    val audio = LocalAudio.current
    val pacing = LocalPacing.current
    val scope = rememberCoroutineScope()
    val cards = catalog.byId

    /*
     * The board the server last sent, resolved **once** per answer.
     *
     * Remembered rather than recomputed, and not to save the arithmetic: `shown === served` is how
     * this screen asks "has the board caught up with the server", and a view rebuilt on every
     * recomposition is never identical to the one that was adopted a frame ago. Derived that way it
     * read false forever — the turn clock never started and the result panel never opened.
     */
    val served = remember(session.match, cards) { session.view(cards) }
    val matchId = session.match?.matchId

    // The board a rematch replaces, so the intro and the banners fire again for it. A sudden-death
    // rematch keeps the match id — it is the same match — and only this changes.
    val boardKey = matchId to (session.match?.rematch ?: 0)

    /*
     * What is on screen, which lags the server by however long the exchange takes to tell.
     *
     * Held as state rather than derived, because the two genuinely differ for about a second per
     * turn: `served` is the position after both placements and this is the position after however
     * many of them the player has been shown.
     *
     * **Null on a new board, not the board itself.** A fresh deal already contains whatever the
     * opening toss let the opponent play, and `plays` announces it: starting from the served
     * position would apply that placement to a board it had already been applied to, putting a
     * second card on a taken cell and leaving the match unplayable from the first turn. Null is
     * how [PveExchange] is told there is nothing to walk from, and it adopts the answer whole.
     *
     * **Referential, not structural.** The walk ends by adopting the referee's own view, and on the
     * last placement of a match that view is `equals` to the one the walk just stepped to — same
     * board, same empty hands, no playable slots. Under the default policy that write is dropped,
     * so `shown === served` never came true, and the result panel — which waits on exactly that —
     * never opened. The two are the same *position* and different *facts*: one is what this screen
     * worked out, the other is what the server said.
     */
    var shown by remember(boardKey) {
        mutableStateOf<MatchView?>(null, referentialEqualityPolicy())
    }

    PveExchange(
        session = session,
        served = served,
        cards = cards,
        shown = shown,
        thinking = thinking,
    ) { next, told ->
        shown = next
        // Only a placement being *told* sounds. The walk ends by adopting the referee's own view,
        // which is the position the last step already reached — sounding it again played every
        // capture twice and announced the winner twice.
        if (told) playMatchSounds(audio, scope, next, pacing)
    }

    val view = shown
    if (view == null) {
        PveWaiting(session = session, against = npc.iconId, onExit = onExit)
        return
    }

    var selected by remember(boardKey) { mutableStateOf<Card?>(null) }
    var reward by remember(matchId) { mutableStateOf<MatchReward?>(null) }

    // The intro the server's deal implies. `serverIntroAnimations` derives the captions from the
    // rules and the toss from `first` — both facts the referee decided and sent — so an announced
    // Reverse is the rule the match is actually being played under.
    val intro = remember(boardKey) {
        session.match?.let { serverIntroAnimations(it.rules, it.first) }.orEmpty()
    }
    val banners = pveBannerQueue(boardKey, view, intro)
    val underway = pveIntroFinished(boardKey, intro)
    val revealed = openRevealed(boardKey, intro)

    // The campaign's opening line. Read once per placement rather than per recomposition, for the
    // reason the local screen gives: the bubbles are played from this list and nothing else may
    // disagree with it about how many lines there are.
    val lesson = remember(boardKey, view.placement) {
        script?.lesson?.linesBefore(view.placement, view.score.blue).orEmpty()
    }
    val speech = rememberLessonSpeech(view.placement, lesson)

    LaunchedEffect(matchId) { audio.play(Sound.MATCH_OPEN) }

    // Announced once the last placement has been seen, and only for a match the server settled —
    // `outcome` is absent on a sudden-death draw, which is a rematch rather than an ending.
    // Keyed on **whether the board has caught up**, not on how far it has got. The last placement
    // of a match is stepped to and then adopted, and both are placement nine: a key that counted
    // placements never changed across that pair, so the panel waited for a frame that never came.
    LaunchedEffect(matchId, session.match?.outcome, shown === served) {
        val outcome = session.match?.outcome ?: return@LaunchedEffect
        if (reward != null || shown !== served) return@LaunchedEffect
        onResult(outcome.result)
        delay(pacing * (PVE_OUTCOME_PAUSE_MS + settleMillis(view.lastPlay)))
        reward = outcome.reward?.asMatchReward(outcome.result)
    }

    val place: (Card, Int) -> Unit = { card, position ->
        val slot = view.ownHand.indexOfFirst { it.id == card.id }
        if (slot >= 0 && canPlay(view, card, position)) {
            selected = null
            scope.launch { session.play(PveMove(handIndex = slot, position = position)) }
        }
    }

    val autoRandom = remember(matchId) { Random(matchId.hashCode()) }
    // Held while a line is up as well as behind the intro, and while the exchange is still being
    // told: `shown === served` is "the board has caught up with the server", and counting a turn
    // the player cannot yet take is how a clock ends up playing for them.
    val ticking = underway && !speech.isSpeaking && shown === served
    val turnFraction = turnClock(boardKey, view, turnLimit, ticking) {
        autoPlay(view, autoRandom)?.let { (card, position) -> place(card, position) }
    }

    val wide = LocalWideLayout.current
    val log = rememberViewMoveLog(boardKey, view)

    MatchFrame(
        wide = wide,
        side = {
            MatchSidePanel(
                npc = npc,
                opponentName = strings[npc.nameKey],
                rules = view.rules,
                log = log,
            )
        },
    ) { panelShown ->
        StatusBar(
            view = view,
            selected = selected,
            npc = npc,
            opponentName = strings[npc.nameKey],
            turnFraction = turnFraction,
            showOpponent = !panelShown,
            outcomeTitle = script?.outcomeTitle,
            onExit = onExit,
        )
        BoardRules(view.rules, panelShown)

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayArea(
                view = view,
                selected = selected,
                layout = matchLayout(maxWidth - PlayAreaInset * 2, maxHeight - PlayAreaInset * 2),
                // Nothing ringed. `captureHighlights` explains *why* a capture happened by naming
                // the two facing digits, which is a lesson's job; an ordinary match shows the rule
                // working rather than annotating it.
                highlights = emptyMap(),
                // The stagger is passed, and matters more here than in a local match: the captures
                // are the referee's own, so a combo resolved on the server turns at exactly the
                // pace one resolved here used to.
                waves = captureWaves(view.lastPlay),
                onSelect = { card ->
                    if (card in view.playableCards) selected = card
                },
                onPlace = { position -> selected?.let { place(it, position) } },
                onDrop = place,
                revealed = revealed,
            )
            reward?.let {
                OutcomePanel(
                    reward = it,
                    opponentName = strings[npc.nameKey],
                    cards = cards,
                    // A rematch is a *new* match on the server, so the panel's second control opens
                    // one rather than resetting a board. Outside a campaign there is no next rung
                    // and the panel offers only the way out.
                    next = scriptExit ?: rematchExit(session, npc, scope, audio),
                    onDone = onExit,
                    title = script?.outcomeTitle,
                )
            }
            MatchBannerOverlay(banners)
            LessonBubbles(speech = speech, script = script, enabled = underway)
            OutcomeBubble(script = script, result = reward?.result)
            // Over everything, because it is the only thing on screen the player can act on.
            session.trouble?.let { PveReconnect(session, against = npc.iconId) }
        }
    }
}

/**
 * "Play again", which against a refereed opponent is a new match rather than a reset board.
 *
 * The local screen re-dealt in place by bumping a counter, and could: it owned the deal. Here the
 * server owns it, and asking for a second match is the same request as asking for the first —
 * including the deck, the roulette and the toss, none of which this end is entitled to reuse.
 */
private fun rematchExit(
    session: PveSession,
    npc: Npc,
    scope: CoroutineScope,
    audio: AudioPlayer,
): ScriptExit = ScriptExit(StringKeys.REMATCH) {
    val formatId = session.match?.formatId ?: return@ScriptExit
    // Answered here rather than by the board arriving, because the board arrives when the server
    // says so: the tap has to be acknowledged on the frame it happens on, as it was when the deal
    // was local.
    scope.launch { audio.play(Sound.NEW_MATCH) }
    scope.launch { session.open(npc.iconId, formatId) }
}

/**
 * Walks the placements the server just announced onto the board, one at a time.
 *
 * The pause between them is the same beat the local opponent used to take before moving. It was a
 * lie then — the AI had decided instantly — and it is a lie now, for the same good reason: a reply
 * that appears on the frame the player's own card lands on is a reply nobody sees.
 */
@Composable
private fun PveExchange(
    session: PveSession,
    served: MatchView?,
    cards: Map<Int, Card>,
    shown: MatchView?,
    thinking: Duration,
    onStep: (MatchView, told: Boolean) -> Unit,
) {
    val plays = session.match?.plays.orEmpty()
    val pacing = LocalPacing.current

    LaunchedEffect(session.match, pacing) {
        val target = served ?: return@LaunchedEffect
        // Nothing to tell: a plain read, a resumed match, or the opening deal. The board is the
        // truth and it is adopted whole. It still *announces* when the answer carried placements —
        // the opening toss can have given the opponent the first move, and a card that appears in
        // silence appears from nowhere.
        if (plays.isEmpty() || shown == null) {
            onStep(target, plays.isNotEmpty())
            return@LaunchedEffect
        }

        var at: MatchView = shown
        for ((index, play) in plays.withIndex()) {
            val card = cards[play.cardId] ?: break
            // The player's own card lands on the frame they asked for it — index 0 is the move
            // they just made, and making somebody wait to see their own tap answered is the one
            // delay that reads as lag rather than as an opponent.
            //
            // Everything after it waits twice: once for the screen to go quiet, and once more
            // because a program that answers the instant the flip ends does not read as an
            // opponent taking a turn.
            if (index > 0) delay(pacing * (quietMillis(at) + thinking.inWholeMilliseconds))
            at = at.after(play, card)
            onStep(at, true)
        }
        // The referee's view, always, and after the walk rather than instead of it. A board stepped
        // to a different position than the one that was sent is a rendering bug here; the player
        // still gets the position the server has.
        // The last placement's own animation, and no thinking pause: nobody is about to move.
        delay(pacing * settleMillis(at.lastPlay))
        onStep(target, false)
    }
}

/**
 * How long [view]'s placement takes to finish being *watched* — the board and the banners.
 *
 * [settleMillis] answers only half of it. A placement draws two things at once: the card landing
 * and the chain flipping, which is what it measures, and the captions over the top of them, which
 * it knows nothing about. The refereed screen waited on the first half alone, and the opponent
 * replied through its own Same and Combo banners — a turn that took `MatchScreen` 3.1 seconds took
 * this one 0.95, because every caption was simply not counted.
 *
 * The maximum rather than the sum, for the reason [settleMillis] gives about its own two halves:
 * the banners play *over* the board rather than after it, so the screen is quiet when the slower of
 * the two has finished. `MatchScreen` adds them instead — see its opponent effect — which is the
 * one place the two screens deliberately disagree, and the sum is what a 700ms base pause needed to
 * feel right where this has 550.
 */
internal fun quietMillis(view: MatchView): Long = maxOf(
    settleMillis(view.lastPlay),
    MatchBanner.afterPlacement(view).sumOf { it.totalMillis }.toLong(),
)

/** No board yet: the deal is in flight, or the connection is. */
@Composable
private fun PveWaiting(session: PveSession, against: String, onExit: () -> Unit) {
    val strings = LocalStrings.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (session.trouble != null) {
            PveReconnect(session, against = against, onExit = onExit)
        } else {
            Text(
                text = strings[StringKeys.LOADING],
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(PVE_DEALING_TEST_TAG),
            )
        }
    }
}

/**
 * The way back from a dropped connection.
 *
 * A panel with a button, and deliberately not a defeat, a forfeit or an offer to play on offline.
 * The match is a row on the server; the only thing that went wrong is that this screen is out of
 * date, and [PveSession.resume] fixes exactly that.
 */
@Composable
private fun PveReconnect(
    session: PveSession,
    against: String,
    onExit: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .testTag(PVE_RECONNECT_TEST_TAG)
            .fillMaxWidth()
            .padding(SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            // What actually came back, not "the server is unreachable" over the top of every one
            // of them. A refusal, a 500 and a throttle each read as a dead network here, which
            // sent a player looking at their signal for a fault that was nothing of the kind.
            text = session.trouble?.message(strings) ?: strings[StringKeys.ERROR_OFFLINE],
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            // `refresh` when there is a board to re-read, because it asks by id and cannot be
            // handed a different match; `resume` only when there is nothing yet, and then narrowed
            // to this opponent for the reason its own KDoc gives.
            onClick = {
                scope.launch {
                    if (session.match != null) session.refresh() else session.resume(against)
                }
            },
            enabled = !session.isBusy,
        ) {
            Text(strings[StringKeys.RETRY])
        }
        onExit?.let {
            Button(onClick = it, enabled = !session.isBusy) { Text(strings[StringKeys.BACK]) }
        }
    }
}

/**
 * [RewardSummary] as the panel's own type.
 *
 * The ids are resolved rather than sent: an achievement carries an unlock rule and a quest carries
 * an objective, both of which are *data both ends hold*, so putting them on the wire would ship a
 * catalogue many times per match to say what one string already says. An id this build does not
 * know is dropped — a client whose catalogue is behind the server's should show the rest of the
 * payout, not fail to announce a match it just won.
 */
internal fun RewardSummary.asMatchReward(
    result: MatchResult = this.result,
): MatchReward = MatchReward(
    result = result,
    mgp = mgp,
    xp = xp,
    items = items,
    achievements = achievementIds.mapNotNull(AchievementCatalog::get),
    quests = questIds.mapNotNull(DailyQuestCatalog::get),
)

/**
 * The beat the opponent takes before answering, on top of the board falling quiet.
 *
 * Short enough not to be a wait, long enough that the reply is a *reply*. The two are added rather
 * than folded into one number so that a combo — which takes the board most of a second to draw —
 * does not eat the pause and make a long turn look like a fast one.
 */
private val PVE_THINKING = 550.milliseconds

private const val PVE_OUTCOME_PAUSE_MS = 1_400L

private val PVE_TURN_LIMIT = 30.seconds
