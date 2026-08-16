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
import com.tripletriad.model.OrderRule
import com.tripletriad.model.TurnOrder
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A hand under Order and Chaos — the two rules that choose the card for the player.
 *
 * ### What was wrong, and why nothing caught it
 *
 * Both rules narrow `MatchState.playableCards` to one card, and the board enforced that in two
 * places without ever *saying* it: the drag was gated, and a tap on any other card reached
 * `MatchScreen`'s `onSelect`, which dropped it. So four of five cards looked completely ordinary
 * and quietly did nothing. Under Order a player works it out — the card left is always the
 * leftmost. Under Chaos the card is drawn afresh every turn, so what they learn instead is that
 * the game sometimes ignores them.
 *
 * The board now dims the four and rings the fifth. Both are asserted here, and the fourth test is
 * the one that stops the fix becoming its own noise: with no such rule in force, **nothing** is
 * marked.
 *
 * ### Why it composes [PlayArea] rather than playing a match
 *
 * Same reason as `ElementalBoardTest` and `BonusMalusBoardTest`. Reaching a Chaos board through the
 * app means finding an NPC that declares the rule and beating whatever roulette it has, and a
 * *Chaos* board additionally means the card left is drawn at random — so the fixture would have to
 * discover which one it got before it could assert anything about it. A [MatchState] built here
 * states the hand it means. `PvpBoardUiTest.theChosenCardIsTheOnlyOneRinged` is the same claim on
 * the other board, where the answer arrives from the server instead.
 */
@OptIn(ExperimentalTestApi::class)
class NarrowedHandTest {

    /** One ring, on the one card the rules leave. */
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

    /**
     * The four the rules forbid do not take a tap.
     *
     * `assertIsNotEnabled` rather than tapping and checking nothing happened: a disabled control
     * is what a screen reader is told too, and "the tap did nothing" is the state this whole change
     * exists to stop being the only feedback. The enabled one is asserted in the same breath, or a
     * hand that had gone entirely dead would pass.
     */
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

    /** And a tap that does get through selects the card it was aimed at. */
    @Test
    fun theChosenCardStillSelects() = runComposeUiTest {
        val picked = mutableListOf<Card>()

        setContent {
            Fixture(state = ordered(), playable = listOf(HAND[CHOSEN]), onPick = picked::add)
        }
        onNodeWithTag(handCardTestTag(CardColor.BLUE, CHOSEN)).performClick()

        assertEquals(listOf(HAND[CHOSEN]), picked, "the one playable card should still respond")
    }

    /**
     * With no rule narrowing anything, nothing is dimmed and nothing is ringed.
     *
     * The half that keeps this from being a permanent decoration on every match ever played: a
     * board that ringed all five cards would be stating "you may play any of these", which is the
     * default and does not need saying.
     */
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

    /**
     * The opponent's hand is never marked, on their turn or ours.
     *
     * `playable` is the *player's* list and is empty while the opponent moves, so a narrowing test
     * that forgot whose hand it was looking at would report all five red cards as forbidden on
     * red's own turn — which is the shape the guard in `HandArea` exists against.
     */
    @Test
    fun theOpponentsHandIsNeverMarked() = runComposeUiTest {
        setContent { Fixture(state = ordered(toMove = CardColor.RED), playable = emptyList()) }

        assertFalse(existsUnmerged(CHOSEN_CARD_TEST_TAG), "the opponent's hand was marked")
    }

    // ---- Fixtures ----------------------------------------------------------

    /**
     * [PlayArea] in a box big enough to lay a board out in.
     *
     * The selection is hoisted here because it is hoisted above `PlayArea` in the real screen; a
     * fixture passing a constant null would compose a state a match is never in.
     */
    @Composable
    private fun Fixture(state: MatchState, playable: List<Card>, onPick: (Card) -> Unit = {}) {
        var selected by remember { mutableStateOf<Card?>(null) }

        TripleTriadTheme {
            Box(modifier = Modifier.size(FIXTURE_SIDE)) {
                PlayArea(
                    state = state,
                    selected = selected,
                    visibility = HandVisibility.HIDDEN,
                    layout = matchLayout(FIXTURE_SIDE, FIXTURE_SIDE),
                    playable = playable,
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

    /** An empty board under Order, with both hands full and [toMove] to play. */
    private fun ordered(toMove: CardColor = CardColor.BLUE): MatchState = MatchState(
        rules = GameRules(order = OrderRule.ORDER),
        board = Board(),
        hands = mapOf(CardColor.BLUE to HAND, CardColor.RED to HAND),
        order = TurnOrder(toMove),
    )

    /** The same board with no rule that narrows a hand. */
    private fun plain(): MatchState = ordered().copy(rules = GameRules())

    private companion object {
        /**
         * The slot the rules leave, and **not slot 0**.
         *
         * Order always leaves the first card, so a fixture built on slot 0 would pass against a
         * board that marked "the leftmost" rather than "the playable one" — which is the bug a
         * Chaos board would show and an Order board would hide. Chaos is what this number stands
         * in for; the rule on the state only has to be one that narrows.
         */
        const val CHOSEN = 3

        val FIXTURE_SIDE = 900.dp

        /** Five distinct cards, so a marked slot can be told from a marked card. */
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
