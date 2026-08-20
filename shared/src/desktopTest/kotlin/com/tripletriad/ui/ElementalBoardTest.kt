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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.model.Board
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.GameRules
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.TypeRule
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ElementalBoardTest {

    @Test
    fun aCellShowsItsElementAndAPlainCellShowsNone() = runComposeUiTest {
        setContent { Fixture(state = elementalBoard()) }

        assertTrue(existsUnmerged(tileElementTestTag(FIRE_CELL)), "the fire cell should say so")
        assertFalse(existsUnmerged(tileElementTestTag(PLAIN_CELL)), "a plain cell has no element")
    }

    @Test
    fun aCardOnAMatchingElementShowsItsBonus() = runComposeUiTest {
        setContent {
            Fixture(
                state = elementalBoard().withCard(FIRE_CELL, card(id = 300, type = CardType.FIRE)),
            )
        }

        onNodeWithTag(tileModifierTestTag(FIRE_CELL), useUnmergedTree = true)
            .assertTextEquals("+1")
    }

    @Test
    fun aCardOnTheWrongElementShowsItsPenaltyAndSoDoesAnUntypedOne() = runComposeUiTest {
        setContent {
            Fixture(
                state = elementalBoard()
                    .withCard(FIRE_CELL, card(id = 300, type = CardType.ICE))
                    .withCard(ICE_CELL, card(id = 301, type = null)),
            )
        }

        onNodeWithTag(tileModifierTestTag(FIRE_CELL), useUnmergedTree = true)
            .assertTextEquals("−1")
        onNodeWithTag(tileModifierTestTag(ICE_CELL), useUnmergedTree = true)
            .assertTextEquals("−1")
    }

    @Test
    fun holdingACardAnnotatesEveryFreeElementalCellForIt() = runComposeUiTest {
        val fire = card(id = 300, type = CardType.FIRE)
        setContent { Fixture(state = elementalBoard(hand = listOf(fire))) }

        assertFalse(
            existsUnmerged(tileModifierTestTag(FIRE_CELL)),
            "nothing is annotated before a card is picked up",
        )

        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()

        onNodeWithTag(tileModifierTestTag(FIRE_CELL), useUnmergedTree = true)
            .assertTextEquals("+1")
        onNodeWithTag(tileModifierTestTag(ICE_CELL), useUnmergedTree = true)
            .assertTextEquals("−1")
        assertFalse(
            existsUnmerged(tileModifierTestTag(PLAIN_CELL)),
            "a cell with no element does nothing to the card, so it says nothing",
        )
    }

    // ---- Fixtures ----------------------------------------------------------

    @Composable
    private fun Fixture(state: MatchState) {
        var selected by remember { mutableStateOf<Card?>(null) }

        TripleTriadTheme {
            Box(modifier = Modifier.size(FIXTURE_SIDE)) {
                PlayArea(
                    // The board draws from a view now, so a fixture builds one — the same
                    // projection `MatchScreen` makes of its own state, with the opponent's hand
                    // hidden as an ordinary match would have it.
                    view = MatchView.of(state, CardColor.BLUE, HandVisibility.HIDDEN),
                    selected = selected,
                    layout = matchLayout(FIXTURE_SIDE, FIXTURE_SIDE),
                    // These fixtures are about what a *cell* draws — the element, the modifier
                    // badge, the digits under it. The capture rings are a lesson's, and lighting
                    // them here would put a second mark on the same cards.
                    highlights = emptyMap(),
                    waves = emptyMap(),
                    onSelect = { selected = it },
                    onPlace = {},
                    onDrop = { _, _ -> },
                )
            }
        }
    }

    private fun elementalBoard(hand: List<Card> = emptyList()): MatchState = MatchState(
        rules = GameRules(typeRule = TypeRule.ELEMENTAL),
        board = Board(
            elements = List(Board.SIZE) { position ->
                when (position) {
                    FIRE_CELL -> CardType.FIRE
                    ICE_CELL -> CardType.ICE
                    else -> null
                }
            },
        ),
        hands = mapOf(CardColor.BLUE to hand, CardColor.RED to emptyList()),
    )

    private fun MatchState.withCard(position: Int, card: Card): MatchState = copy(
        board = board.copy(
            cells = board.cells.mapIndexed { at, cell ->
                if (at == position) PlacedCard(card, CardColor.BLUE) else cell
            },
        ),
    )

    private fun card(id: Int, type: CardType?) = Card(
        id = id,
        nameKey = "STR_TEST_$id",
        name = "Test $id",
        top = 5,
        right = 5,
        bottom = 5,
        left = 5,
        rarity = 1,
        type = type,
    )

    private companion object {
        const val FIRE_CELL = 0
        const val ICE_CELL = 1

        const val PLAIN_CELL = 4

        val FIXTURE_SIDE = 900.dp
    }
}
