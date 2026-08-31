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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.model.AscensionTally
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

@OptIn(ExperimentalTestApi::class)
class BonusMalusBoardTest {

    @Test
    fun aCardUnderBonusKeepsItsPrintedPowersAndWearsTheBadge() = runComposeUiTest {
        setContent {
            Fixture(tallied(TypeRule.ASCENSION, CardType.BEAST to BONUS).withCard(CELL, beast()))
        }

        onNodeWithTag(tileModifierTestTag(CELL), useUnmergedTree = true).assertTextEquals("+3")
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
    }

    @Test
    fun aCardUnderMalusWearsANegativeBadge() = runComposeUiTest {
        setContent {
            Fixture(tallied(TypeRule.DESCENSION, CardType.BEAST to MALUS).withCard(CELL, beast()))
        }

        onNodeWithTag(tileModifierTestTag(CELL), useUnmergedTree = true).assertTextEquals("−2")
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
    }

    @Test
    fun anOverwhelmingMalusStillShowsTheWholeTally() = runComposeUiTest {
        setContent {
            Fixture(
                tallied(TypeRule.DESCENSION, CardType.BEAST to RUNAWAY_MALUS)
                    .withCard(CELL, beast()),
            )
        }

        onNodeWithTag(tileModifierTestTag(CELL), useUnmergedTree = true).assertTextEquals("−9")
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
    }

    @Test
    fun aCardOfAnotherTypeIsUntouched() = runComposeUiTest {
        setContent {
            Fixture(
                tallied(TypeRule.ASCENSION, CardType.BEAST to BONUS)
                    .withCard(CELL, beast())
                    .withCard(OTHER_CELL, card(OTHER_ID, CardType.SCIONS)),
            )
        }

        onNodeWithTag(tileModifierTestTag(CELL), useUnmergedTree = true).assertTextEquals("+3")
        assertFalse(
            existsUnmerged(tileModifierTestTag(OTHER_CELL)),
            "a card of another type gained nothing, so its cell should say nothing",
        )
        onNodeWithContentDescription("$OTHER_NAME, 5 5 5 5").assertExists()
    }

    /**
     * A card **in hand** wears the badge too, and no cell wears it on its behalf.
     *
     * Both halves matter and they used to be one assertion. The tally is a property of the board,
     * so it applies to every card of the type wherever it is standing — a player choosing between
     * five cards is choosing the number each would attack with, and printing the unmodified digits
     * made the hand tell them something the board was about to contradict. What must *not* happen
     * is the cell answering for it: an empty cell holds nothing, and a card being carried over one
     * has not been played onto it.
     */
    @Test
    fun aCardHeldUnderBonusWearsTheBadgeAndTheBoardDoesNot() = runComposeUiTest {
        setContent {
            Fixture(tallied(TypeRule.ASCENSION, CardType.BEAST to BONUS, hand = beast()))
        }

        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()

        onNodeWithTag(handModifierTestTag(CardColor.BLUE, 0), useUnmergedTree = true)
            .assertTextEquals("+3")
        for (cell in 0 until Board.SIZE) {
            assertFalse(
                existsUnmerged(tileModifierTestTag(cell)),
                "cell $cell annotated a card that is still in hand",
            )
        }
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
    }

    /** And a malus reaches the hand the same way — the badge is signed, not conditional. */
    @Test
    fun aCardHeldUnderMalusWearsANegativeBadge() = runComposeUiTest {
        setContent {
            Fixture(tallied(TypeRule.DESCENSION, CardType.BEAST to MALUS, hand = beast()))
        }

        onNodeWithTag(handModifierTestTag(CardColor.BLUE, 0), useUnmergedTree = true)
            .assertTextEquals("−2")
    }

    /**
     * Elemental never reaches a hand, and that asymmetry is the rule rather than an omission.
     *
     * Bonus and Malus are a property of the **board**, so a card carries them wherever it is.
     * Elemental is a property of a **cell**, and a card in hand is standing on none — there is no
     * true number to draw. `powerModifier` says so by itself when it is passed no element, which
     * is why the hand needs no rule test of its own.
     */
    @Test
    fun elementalLeavesTheHandAlone() = runComposeUiTest {
        setContent { Fixture(tallied(TypeRule.ELEMENTAL, hand = beast())) }

        assertFalse(
            existsUnmerged(handModifierTestTag(CardColor.BLUE, 0)),
            "a hand card claimed an element it is not standing on",
        )
    }

    /** A card of a type the tally never touched is unbadged in hand as it is on the board. */
    @Test
    fun aHeldCardOfAnotherTypeIsUntouched() = runComposeUiTest {
        setContent {
            Fixture(
                tallied(
                    TypeRule.ASCENSION,
                    CardType.BEAST to BONUS,
                    hand = card(OTHER_ID, CardType.SCIONS),
                ),
            )
        }

        assertFalse(
            existsUnmerged(handModifierTestTag(CardColor.BLUE, 0)),
            "a scion collected a beast's bonus",
        )
    }

    @Test
    fun withNoRuleTheBoardIsUnannotated() = runComposeUiTest {
        setContent { Fixture(tallied(TypeRule.NONE).withCard(CELL, beast())) }

        assertFalse(existsUnmerged(tileModifierTestTag(CELL)), "a badge appeared with no rule up")
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
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

    private fun tallied(
        rule: TypeRule,
        vararg counts: Pair<CardType, Int>,
        hand: Card? = null,
    ): MatchState = MatchState(
        rules = GameRules(typeRule = rule),
        board = Board(),
        hands = mapOf(
            CardColor.BLUE to listOfNotNull(hand),
            CardColor.RED to emptyList(),
        ),
        tally = AscensionTally(counts.toMap()),
    )

    private fun MatchState.withCard(position: Int, card: Card): MatchState = copy(
        board = board.copy(
            cells = board.cells.mapIndexed { at, cell ->
                if (at == position) PlacedCard(card, CardColor.BLUE) else cell
            },
        ),
    )

    private fun beast() = card(BEAST_ID, CardType.BEAST)

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
        const val BONUS = 3
        const val MALUS = -2
        const val RUNAWAY_MALUS = -9

        const val CELL = 0
        const val OTHER_CELL = 4
        const val BEAST_ID = 300
        const val OTHER_ID = 301

        const val BEAST_NAME = "STR_TEST_$BEAST_ID"
        const val OTHER_NAME = "STR_TEST_$OTHER_ID"

        val FIXTURE_SIDE = 900.dp
    }
}
