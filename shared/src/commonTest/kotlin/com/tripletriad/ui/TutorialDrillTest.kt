package com.tripletriad.ui

import com.tripletriad.model.Board
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.MatchState
import com.tripletriad.model.TurnOrder
import com.tripletriad.model.powerModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TutorialDrillTest {

    @Test
    fun theCourseEndsInThreeMatches() {
        assertEquals(EXPECTED_DRILLS, TUTORIAL_DRILLS.size, "Bonus, Order and the exam")
        assertEquals(
            EXPECTED_PUZZLES,
            TUTORIAL_PUZZLES.size,
            "the positions are unchanged — a drill inserted among them renumbers every one",
        )
    }

    @Test
    fun everyDrillPlaysTheRulesItsRowNames() {
        for (lesson in TUTORIAL_COURSE) {
            val drill = lesson.drill ?: continue
            val playing = drill.rules.activeRuleKeys()

            assertTrue(
                playing.isNotEmpty(),
                "${lesson.titleKey} is played under no rule at all, so it teaches nothing",
            )
            assertTrue(
                lesson.ruleKeys.containsAll(playing),
                "${lesson.titleKey} plays $playing but its row names ${lesson.ruleKeys}",
            )
        }
    }

    @Test
    fun theOrderDrillNarrowsTheHandToOneCard() {
        val drill = TUTORIAL_DRILLS[ORDER_DRILL]
        val hand = drill.deck.map { card(it) }
        val state = MatchState(
            rules = drill.rules,
            hands = mapOf(CardColor.BLUE to hand, CardColor.RED to hand),
            order = TurnOrder(CardColor.BLUE),
        )

        assertEquals(
            listOf(hand.first()),
            state.playableCards(),
            "under Order only the first card dealt may be picked up, and that is the lesson",
        )
    }

    @Test
    fun everyDrillDealsAHandThatExists() {
        for (lesson in TUTORIAL_COURSE) {
            val drill = lesson.drill ?: continue

            assertEquals(HAND_SIZE, drill.deck.size, "${lesson.titleKey} deals the wrong hand size")
            for (id in drill.deck) {
                assertNotNull(LESSON_CATALOG[id], "${lesson.titleKey} deals $id, not a card")
            }
        }
    }

    @Test
    fun theBonusDrillDealsOneTribe() {
        val deck = TUTORIAL_DRILLS[BONUS_DRILL].deck
        val types = deck.map { card(it).type }

        assertEquals(HAND_SIZE, deck.toSet().size, "five cards, not one card five times")
        assertEquals(1, types.toSet().size, "the hand is meant to be one tribe, and is $types")
        assertNotNull(types.first(), "an untyped hand gains nothing under Bonus, which is the rule")
    }

    @Test
    fun eachCardOfTheTribeStrengthensTheOnesAlreadyDown() {
        val drill = TUTORIAL_DRILLS[BONUS_DRILL]
        val beast = card(drill.deck.first())
        var state = tribeAgainstTypeless(drill)

        assertEquals(0, state.modifier(beast), "nothing is down, so nothing is owed")

        state = state.playNext(FIRST_CELL)
        assertEquals(
            1,
            state.modifier(beast),
            "one beast down and the tribe is not one stronger — a card counts itself from the " +
                "moment it lands, which is this port's own deviation and is versioned",
        )

        state = state.playNext(SECOND_CELL)
        assertEquals(
            1,
            state.modifier(beast),
            "a card of no tribe moved the count, so the rule is counting placements",
        )

        state = state.playNext(THIRD_CELL)
        assertEquals(
            2,
            state.modifier(beast),
            "the second beast did not add, so the tally does not climb and the lesson is false",
        )
    }

    @Test
    fun everyLineIsSpokenOnATurnThePlayerHolds() {
        for (lesson in TUTORIAL_COURSE) {
            val drill = lesson.drill
            if (drill == null || !drill.tutoring) continue

            for (placement in drill.lines.keys) {
                assertEquals(
                    0,
                    placement % 2,
                    "${lesson.titleKey} speaks at $placement, which is the opponent's turn",
                )
            }
        }
    }

    @Test
    fun onlyTheExamIsUntutored() {
        assertEquals(
            listOf(TUTORIAL_COURSE.last()),
            TUTORIAL_COURSE.filter { it.drill?.tutoring == false },
            "the exam is the last row and the only one played against an opponent trying to win",
        )
    }

    private fun tribeAgainstTypeless(drill: TutorialDrill): MatchState = MatchState(
        rules = drill.rules,
        board = Board(),
        hands = mapOf(
            CardColor.BLUE to drill.deck.map { card(it).copy(owner = CardColor.BLUE) },
            CardColor.RED to TYPELESS_HAND.map { card(it).copy(owner = CardColor.RED) },
        ),
        order = TurnOrder(CardColor.BLUE),
    )

    private fun card(id: Int): Card = assertNotNull(LESSON_CATALOG[id], "card $id")

    private fun MatchState.playNext(cell: Int): MatchState = play(currentHand.first(), cell)

    private fun MatchState.modifier(of: Card): Int = powerModifier(of, rules, null, tally)

    private companion object {
        const val BONUS_DRILL = 0
        const val ORDER_DRILL = 1

        const val EXPECTED_DRILLS = 3
        const val EXPECTED_PUZZLES = 8

        const val FIRST_CELL = 0
        const val SECOND_CELL = 1
        const val THIRD_CELL = 2

        val TYPELESS_HAND = listOf(258, 261, 263, 269, 257)
    }
}
