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
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.TypeRule
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Elemental rule, on screen.
 *
 * The engine has applied `elementalModifier` since it was ported and the board has carried its
 * elements just as long; what was missing was any way for a player to see either. These assert the
 * three things now drawn — the element, what it did to the card on it, and what it would do to the
 * card in hand.
 *
 * ### Why this composes [PlayArea] rather than playing a match
 *
 * Because the rule is opponent data. Reaching an elemental board through the app means finding an
 * NPC in `npcs.json` that happens to declare `RULE_ELEMENTAL`, beating the roulette if it has one,
 * and then hoping the cell the test wants got an element — `Board.elements()` rolls each of the
 * nine independently at about one in two. A `MatchState` built here states the board it means, and
 * the arithmetic on it is `PowerTest`'s in `:core`.
 */
@OptIn(ExperimentalTestApi::class)
class ElementalBoardTest {

    /**
     * A cell with an element shows it, and a cell without one shows nothing.
     *
     * The second half is the one that keeps the feature honest: eight of nine cells carrying a
     * glyph nobody chose would be noise on every match played without the rule.
     */
    @Test
    fun aCellShowsItsElementAndAPlainCellShowsNone() = runComposeUiTest {
        setContent { Fixture(state = elementalBoard()) }

        assertTrue(existsUnmerged(tileElementTestTag(FIRE_CELL)), "the fire cell should say so")
        assertFalse(existsUnmerged(tileElementTestTag(PLAIN_CELL)), "a plain cell has no element")
    }

    /** A card sitting on its own element is worth one more, and the cell says by how much. */
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

    /**
     * And a card on the wrong element is worth one less — **including an untyped one**.
     *
     * That is the case a player is most likely to get wrong, and it is intended rather than a
     * quirk of the AS3: see `elementalModifier`, where it is documented against the ruleset.
     */
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

    /**
     * Picking a card up shows what each free elemental cell would do to **it**.
     *
     * This is the half that changes how the rule plays. A hand card has one element and the board
     * has up to nine; working out which cell suits it means reading nine glyphs and comparing each
     * against the card in hand, which is arithmetic the screen already has the data to do.
     */
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

    /**
     * [PlayArea] in a box big enough to lay a board out in.
     *
     * The selection is held here because it is hoisted above `PlayArea` in the real screen — the
     * status bar reads it too. A fixture that passed a constant `null` would compose the board in
     * a state a match can never be in, and the one test that taps a card would silently assert
     * nothing.
     *
     * The theme is real because the modifier badge reads `tertiary` and `error` off it, and a
     * composable that resolved neither would still draw — in whatever Material's defaults are.
     */
    @Composable
    private fun Fixture(state: MatchState) {
        var selected by remember { mutableStateOf<Card?>(null) }

        TripleTriadTheme {
            Box(modifier = Modifier.size(FIXTURE_SIDE)) {
                PlayArea(
                    state = state,
                    selected = selected,
                    visibility = HandVisibility.HIDDEN,
                    layout = matchLayout(FIXTURE_SIDE, FIXTURE_SIDE),
                    playable = state.hands[CardColor.BLUE].orEmpty(),
                    // These fixtures are about what a *cell* draws — the element, the modifier
                    // badge, the digits under it. The capture rings are a lesson's, and lighting
                    // them here would put a second mark on the same cards.
                    highlights = emptyMap(),
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

    /** Powers and rarity are irrelevant here; only [Card.type] is read. */
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

        /** A cell the board rolled no element for, which is about half of them in a real match. */
        const val PLAIN_CELL = 4

        val FIXTURE_SIDE = 900.dp
    }
}
