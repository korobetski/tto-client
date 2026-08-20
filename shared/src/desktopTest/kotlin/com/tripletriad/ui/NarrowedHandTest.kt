package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.model.Board
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import com.tripletriad.model.OrderRule
import com.tripletriad.model.TurnOrder
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class NarrowedHandTest {

    @Test
    fun theChosenCardIsTheOnlyOneRinged() = runComposeUiTest {
        setContent { Fixture(state = ordered(), playable = listOf(HAND[CHOSEN])) }

        assertEquals(
            1,
            onAllNodesWithTag(CHOSEN_CARD_TEST_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
            "exactly one card should be marked as the one that may be played",
        )
    }

    @Test
    fun theForbiddenCardsCannotBeTapped() = runComposeUiTest {
        setContent { Fixture(state = ordered(), playable = listOf(HAND[CHOSEN])) }

        onNodeWithTag(handCardTestTag(CardColor.BLUE, CHOSEN)).assertIsEnabled()
        for (slot in 0 until HAND_SIZE) {
            if (slot != CHOSEN) {
                onNodeWithTag(handCardTestTag(CardColor.BLUE, slot)).assertIsNotEnabled()
            }
        }
    }

    @Test
    fun theChosenCardStillSelects() = runComposeUiTest {
        val picked = mutableListOf<Card>()

        setContent {
            Fixture(state = ordered(), playable = listOf(HAND[CHOSEN]), onPick = picked::add)
        }
        onNodeWithTag(handCardTestTag(CardColor.BLUE, CHOSEN)).performClick()

        assertEquals(listOf(HAND[CHOSEN]), picked, "the one playable card should still respond")
    }

    @Test
    fun anOrdinaryHandIsNotMarkedAtAll() = runComposeUiTest {
        setContent { Fixture(state = plain(), playable = HAND) }

        assertFalse(
            existsUnmerged(CHOSEN_CARD_TEST_TAG),
            "an unconstrained hand marked a card, which says nothing and would say it every match",
        )
        for (slot in 0 until HAND_SIZE) {
            onNodeWithTag(handCardTestTag(CardColor.BLUE, slot)).assertIsEnabled()
        }
    }

    @Test
    fun theOpponentsHandIsNeverMarked() = runComposeUiTest {
        setContent { Fixture(state = ordered(toMove = CardColor.RED), playable = emptyList()) }

        assertFalse(existsUnmerged(CHOSEN_CARD_TEST_TAG), "the opponent's hand was marked")
    }

    // ---- Fixtures ----------------------------------------------------------

    @Composable
    private fun Fixture(state: MatchState, playable: List<Card>, onPick: (Card) -> Unit = {}) {
        var selected by remember { mutableStateOf<Card?>(null) }

        TripleTriadTheme {
            Box(modifier = Modifier.size(FIXTURE_SIDE)) {
                PlayArea(
                    // The narrowing is stated rather than composed. `MatchView.of` would ask the
                    // rules what may be played, and this file is about what a narrowed hand
                    // *looks* like — a state that narrowed to exactly these cards would make it a
                    // test of `playableCards` instead.
                    view = MatchView.of(state, CardColor.BLUE, HandVisibility.HIDDEN).copy(
                        playableHandIndices = state.hands[CardColor.BLUE].orEmpty()
                            .withIndex()
                            .filter { (_, card) -> card in playable }
                            .map { (slot, _) -> slot },
                    ),
                    selected = selected,
                    layout = matchLayout(FIXTURE_SIDE, FIXTURE_SIDE),
                    // The rings a lesson draws on the *board*; this fixture is about the hand.
                    highlights = emptyMap(),
                    waves = emptyMap(),
                    onSelect = {
                        selected = it
                        onPick(it)
                    },
                    onPlace = {},
                    onDrop = { _, _ -> },
                )
            }
        }
    }

    private fun ordered(toMove: CardColor = CardColor.BLUE): MatchState = MatchState(
        rules = GameRules(order = OrderRule.ORDER),
        board = Board(),
        hands = mapOf(CardColor.BLUE to HAND, CardColor.RED to HAND),
        order = TurnOrder(toMove),
    )

    private fun plain(): MatchState = ordered().copy(rules = GameRules())

    private companion object {
        const val CHOSEN = 3

        val FIXTURE_SIDE = 900.dp

        val HAND: List<Card> = List(HAND_SIZE) { slot ->
            Card(
                id = 300 + slot,
                nameKey = "STR_TEST_${300 + slot}",
                name = "Test $slot",
                top = 5,
                right = 5,
                bottom = 5,
                left = 5,
                rarity = 1,
                owner = CardColor.BLUE,
            )
        }
    }
}
