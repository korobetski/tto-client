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
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.TypeRule
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Bonus and Malus, on screen — `RULE_ASCENSION` and `RULE_DESCENSION`, as the French bundle names
 * them.
 *
 * ### Why this had to exist before the rule was worth playing
 *
 * These two leave **no mark on the board**. Elemental at least colours a cell, so a player can see
 * where the modifier comes from; a Bonus tally is a number nothing on screen holds. Following it
 * meant counting the cards of each type already placed, applying the total to every card of that
 * type, on every side, and redoing it after each turn — for the opponent's cards as well as one's
 * own, since the tally is board-wide and not per player.
 *
 * So the rule shipped as arithmetic homework, and what turns it back into a game is a **badge**
 * over the card saying what the board is doing to it.
 *
 * ### The digits do **not** move, and that is the sharp half
 *
 * A first version folded the modifier into the four numbers, so a 5 on a `+3` board was drawn as an
 * 8. That is wrong, and not by taste: the **printed** values are what Same and Plus compare —
 * `RulesEngineOptions.specialPowerBasis` — so a card whose digits had the modifier baked in would
 * be showing numbers a player cannot use to spot a Same. Every test below therefore asserts the
 * digits are the card's own *and* that the badge is present; either alone would allow the version
 * that was wrong.
 *
 * The digits are read through the accessibility label because they are 18x18 bitmaps out of an
 * atlas and a test cannot read a texture. `CardFace` builds the label from the same card the glyphs
 * come from, so asserting it pins what was drawn.
 *
 * ### Why it composes [PlayArea] rather than playing a match
 *
 * Same reason as `ElementalBoardTest`: reaching a Bonus board through the app means finding an NPC
 * that declares the rule and beating whatever roulette it has. A [MatchState] built here states the
 * board it means, and the arithmetic on it belongs to `RulesEngineTest` in `:core`.
 */
@OptIn(ExperimentalTestApi::class)
class BonusMalusBoardTest {

    /** A card on a `+3` board keeps its printed 5s and wears a `+3`. */
    @Test
    fun aCardUnderBonusKeepsItsPrintedPowersAndWearsTheBadge() = runComposeUiTest {
        setContent {
            Fixture(tallied(TypeRule.ASCENSION, CardType.BEAST to BONUS).withCard(CELL, beast()))
        }

        onNodeWithTag(tileModifierTestTag(CELL), useUnmergedTree = true).assertTextEquals("+3")
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
    }

    /** And under Malus the sign is written rather than merely coloured — digits still untouched. */
    @Test
    fun aCardUnderMalusWearsANegativeBadge() = runComposeUiTest {
        setContent {
            Fixture(tallied(TypeRule.DESCENSION, CardType.BEAST to MALUS).withCard(CELL, beast()))
        }

        onNodeWithTag(tileModifierTestTag(CELL), useUnmergedTree = true).assertTextEquals("−2")
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
    }

    /**
     * A runaway Malus still reports the whole tally, and still leaves the digits alone.
     *
     * `−9` against a 5 floors the card at 1 when it *fights* — [MIN_MODIFIED_POWER] — but the badge
     * reports what the board is doing, not where the arithmetic landed, and the printed 5s are what
     * Same and Plus will read whatever the tally says.
     */
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

    /** A card of another type is untouched, so the badge belongs to the card and not the board. */
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
     * Picking a card up annotates nothing under Bonus, and the card keeps its printed digits.
     *
     * The counterpart of `ElementalBoardTest.holdingACardAnnotatesEveryFreeElementalCellForIt`, and
     * the opposite answer for a reason. Under Elemental the nine cells differ, so the badge tells a
     * player which one suits the card; under Bonus every cell would show the same number, which is
     * nine copies of one fact about a card that is not on the board yet. In hand it does not count
     * — see [AscensionTally].
     */
    @Test
    fun holdingACardUnderBonusAnnotatesNothing() = runComposeUiTest {
        setContent {
            Fixture(tallied(TypeRule.ASCENSION, CardType.BEAST to BONUS, hand = beast()))
        }

        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()

        for (cell in 0 until Board.SIZE) {
            assertFalse(
                existsUnmerged(tileModifierTestTag(cell)),
                "cell $cell annotated a card that is still in hand",
            )
        }
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
    }

    /** With no type rule up, a board draws exactly what the cards say and adds no badge. */
    @Test
    fun withNoRuleTheBoardIsUnannotated() = runComposeUiTest {
        setContent { Fixture(tallied(TypeRule.NONE).withCard(CELL, beast())) }

        assertFalse(existsUnmerged(tileModifierTestTag(CELL)), "a badge appeared with no rule up")
        onNodeWithContentDescription("$BEAST_NAME, 5 5 5 5").assertExists()
    }

    // ---- Fixtures ----------------------------------------------------------

    /**
     * [PlayArea] in a box big enough to lay a board out in.
     *
     * The selection is hoisted here because it is hoisted above `PlayArea` in the real screen; a
     * fixture passing a constant null would compose a state a match is never in, and the test that
     * taps a card would assert nothing.
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
                    waves = emptyMap(),
                    onSelect = { selected = it },
                    onPlace = {},
                    onDrop = { _, _ -> },
                )
            }
        }
    }

    /**
     * A board under [rule] with [counts] already tallied, and no elements anywhere.
     *
     * The tally is set directly rather than reached by playing cards, so the fixture states the
     * board it means in one line. What it must not do is state a tally the rule could not produce
     * — a `+3` under `NONE` — which is why the rule and the counts arrive in one call.
     */
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

    /** Every side is 5, so one assertion covers all four and a transposed edge cannot hide. */
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
        /**
         * The three tallies these tests are about, and why each is a different number.
         *
         * [BONUS] and [MALUS] are ordinary boards — a few cards of one type down. [RUNAWAY_MALUS]
         * is the one that has to be **larger than any card's power**, because the floor is only
         * observable when the arithmetic would have gone past it: `-2` against a 5 proves nothing
         * about a clamp that never fired.
         */
        const val BONUS = 3
        const val MALUS = -2
        const val RUNAWAY_MALUS = -9

        const val CELL = 0
        const val OTHER_CELL = 4
        const val BEAST_ID = 300
        const val OTHER_ID = 301

        /**
         * The labels these cards carry.
         *
         * No bundle is loaded here, so `Strings[key]` returns the key — see `Strings.get`, whose
         * last fallback is the key itself. That is what makes a missing translation visible rather
         * than blank, and it is what these assertions read.
         */
        const val BEAST_NAME = "STR_TEST_$BEAST_ID"
        const val OTHER_NAME = "STR_TEST_$OTHER_ID"

        val FIXTURE_SIDE = 900.dp
    }
}
