package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.tripletriad.model.Board
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import com.tripletriad.model.TOTAL_CARDS

/**
 * Reading and driving a match in progress.
 *
 * Split from [ComposeTestSupport] because it is a different job: that file gets a test to a screen,
 * this one plays the game once it is there. They were one file until it crossed twenty functions,
 * and the split is the one the contents already suggested — nothing here is used by a test that
 * does not reach a board.
 */

/**
 * The score as (blue, red), read off the status bar.
 *
 * Lets a test work out what a placement actually *did*: the side that played gains one for its own
 * card plus one per capture, and the other side loses one per capture. So the opponent's score
 * falling is proof a capture happened — which is how `MatchAudioTest` can assert *which* placement
 * sound is right rather than only that one of the two played.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.score(): Pair<Int, Int> {
    val node = onNodeWithTag(SCORE_TEST_TAG).fetchSemanticsNode()
    val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
    val halves = text.split("—").map { it.trim() }
    check(halves.size == 2) { "the score node does not read like a score: \"$text\"" }
    return halves[0].toInt() to halves[1].toInt()
}

/**
 * How many cards are left in [owner]'s hand, counted off the screen.
 *
 * Slots close up as cards are played, so the number of `hand-<owner>-<n>` nodes *is* the hand size.
 * This is what tells a test that a placement landed: with an autonomous opponent choosing its own
 * cells, "the board changed" cannot be inferred from clicking a particular tile any more.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.handSize(owner: CardColor): Int =
    (0 until HAND_SIZE).count { exists(handCardTestTag(owner, it)) }

/**
 * How many cards are on the board, derived from what has left the two hands.
 *
 * There is no tag for "this cell is occupied" — a placed card is drawn, not written — so the hands
 * are the readable side of the same fact.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.placementsMade(): Int =
    CardColor.entries.sumOf { HAND_SIZE - handSize(it) }

/**
 * True while the player can move.
 *
 * Read off `turn-blue` rather than off the wording of the turn line, which is what this used to do:
 * "blue to play" only exists in English, so every match test was pinned to `en_US` and the two that
 * deliberately run in French and German could not ask whose turn it was at all.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isPlayerTurn(): Boolean = exists(turnTestTag(CardColor.BLUE))

/** True once the end-of-match panel is up, which is also when the profile has been credited. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isFinished(): Boolean = exists(MATCH_RESULT_TEST_TAG)

/**
 * Waits until it is the player's turn, or the match is over.
 *
 * This is the new shape of every match test: the opponent takes its turn on its own after a pause,
 * so a test cannot assume the turn has passed back by the time its next line runs.
 *
 * @param timeoutMillis [TUTORIAL_TIMEOUT_MS] for a lesson, where a turn also waits on the speech
 *   bubbles and their tweens and can take several times what an ordinary opponent's pause does.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitPlayer(timeoutMillis: Long = UI_TIMEOUT_MS) {
    waitUntil(timeoutMillis = timeoutMillis) { isPlayerTurn() || isFinished() }
}

/**
 * Plays one of the player's cards onto the first cell that accepts it.
 *
 * Tries cells in order and stops when the hand shrinks. Clicking an occupied cell is a no-op rather
 * than an error — `MatchScreen` guards on `board.isEmpty(position)` — which is what makes probing
 * safe, and is itself asserted by `placingOnATakenCellIsIgnored`.
 *
 * @return the cell played on.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.playOneCard(timeoutMillis: Long = UI_TIMEOUT_MS): Int {
    awaitPlayer(timeoutMillis)
    check(!isFinished()) { "the match is already over" }
    val before = handSize(CardColor.BLUE)
    onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()
    for (position in 0 until Board.SIZE) {
        onNodeWithTag(tileTestTag(position)).performClick()
        waitForIdle()
        if (handSize(CardColor.BLUE) < before) return position
    }
    error("no cell accepted a card; the board looks full but the match is not over")
}

/**
 * Plays the match to the end, letting the opponent take its own turns.
 *
 * The player places five cards or four depending on the coin flip, so this loops on "is it over"
 * rather than on a count.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.playOut(timeoutMillis: Long = UI_TIMEOUT_MS) {
    var moves = 0
    while (!isFinished()) {
        check(moves <= PLACEMENTS_PER_MATCH) { "played $moves times and the match has not ended" }
        playOneCard(timeoutMillis)
        moves++
        awaitPlayer(timeoutMillis)
    }
}

/**
 * True when the score line sums to [TOTAL_CARDS].
 *
 * Matched on the score node with an **exact** text comparison, not a substring anywhere on screen:
 * the line reads `5 — 5`, and `"0 — 0"` is a substring of `"10 — 0"`.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.totalIsTen(): Boolean =
    (0..TOTAL_CARDS).any { blue ->
        onAllNodes(hasTestTag(SCORE_TEST_TAG) and hasText("$blue — ${TOTAL_CARDS - blue}"))
            .fetchSemanticsNodes().isNotEmpty()
    }

/**
 * Brings [iconId]'s row into the composition, because the roster is lazy and now long.
 *
 * Was unnecessary while `MODE` split the cast in two and every test's opponent sat near the top of
 * its own half. One format means one list of eighty-five, sorted by difficulty — and the FFVIII
 * table declares `difficulty` **0** for all twenty-five of its entries (see `NpcCatalog.available`,
 * which records that gap), so all twenty-five sort ahead of `tt-master`. The row is there; it is
 * simply not composed until something scrolls to it.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.scrollToOpponent(iconId: String) {
    onNodeWithTag(OPPONENT_LIST_TEST_TAG)
        .performScrollToNode(hasTestTag(opponentRowTestTag(iconId)))
}
