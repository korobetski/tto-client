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

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.score(): Pair<Int, Int> {
    val node = onNodeWithTag(SCORE_TEST_TAG).fetchSemanticsNode()
    val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
    val halves = text.split("—").map { it.trim() }
    check(halves.size == 2) { "the score node does not read like a score: \"$text\"" }
    return halves[0].toInt() to halves[1].toInt()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.handSize(owner: CardColor): Int =
    (0 until HAND_SIZE).count { exists(handCardTestTag(owner, it)) }

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.placementsMade(): Int =
    CardColor.entries.sumOf { HAND_SIZE - handSize(it) }

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isPlayerTurn(): Boolean = exists(turnTestTag(CardColor.BLUE))

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isFinished(): Boolean = exists(MATCH_RESULT_TEST_TAG)

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitPlayer(timeoutMillis: Long = UI_TIMEOUT_MS) {
    waitUntil(timeoutMillis = timeoutMillis) { isPlayerTurn() || isFinished() }
}

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

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.totalIsTen(): Boolean =
    (0..TOTAL_CARDS).any { blue ->
        onAllNodes(hasTestTag(SCORE_TEST_TAG) and hasText("$blue — ${TOTAL_CARDS - blue}"))
            .fetchSemanticsNodes().isNotEmpty()
    }

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.scrollToOpponent(iconId: String) {
    onNodeWithTag(OPPONENT_LIST_TEST_TAG)
        .performScrollToNode(hasTestTag(opponentRowTestTag(iconId)))
}
