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

/**
 * The lessons that are matches rather than positions — [TUTORIAL_DRILLS].
 *
 * `TutorialPuzzleTest` can play a puzzle to its end and assert exactly what happened, because a
 * puzzle *is* one move. A drill is a whole game against an AI, so what is worth pinning here is not
 * the play — it is the three things a drill can get silently wrong and still look right on screen:
 *
 * 1. **The row and the match agree about the rule.** Each drill states its rules twice — once as a
 *    `GameRules` the match is played under, once as the keys its list row prints — and the two are
 *    different spellings of the same thing (`GameRules.RuleKeys`, which is `internal` in `:core`,
 *    is the table between them). A row advertising a rule the match never imposes would look
 *    entirely correct on screen: [everyDrillPlaysTheRulesItsRowNames] reads the rules back through
 *    `activeRuleKeys()` and holds them to what the row claims.
 * 2. **The hand is the one the lines describe.** Both drills talk about the hand rather than about
 *    the board: one deals five cards of a single tribe, the other deals five in an order it then
 *    tells the player to read to the end.
 * 3. **The tally really does climb.** The Bonus lesson's whole claim is that each card of a tribe
 *    strengthens the others — which no single placement can show, and which is therefore the one
 *    piece of engine behaviour these lessons rest on.
 *
 * ### Why the tally is pinned to the number and not merely to "it went up"
 *
 * Because the number has already changed once. `AscensionTally`'s own KDoc records the deviation:
 * the AS3 ran `ascensionPhase` *after* the flips, so a card resolved its own captures without its
 * own contribution, and this port counts it from the moment it lands — a change carried by
 * `protocol.CURRENT_VERSION`. A test that only asked whether the modifier rose would pass either
 * way, and the lesson's third line ("the badge has grown with them") is written for a board where
 * two beasts read `+2`.
 */
class TutorialDrillTest {

    /** Three rows are matches: Bonus, Order, and the exam behind them. */
    @Test
    fun theCourseEndsInThreeMatches() {
        assertEquals(EXPECTED_DRILLS, TUTORIAL_DRILLS.size, "Bonus, Order and the exam")
        assertEquals(
            EXPECTED_PUZZLES,
            TUTORIAL_PUZZLES.size,
            "the positions are unchanged — a drill inserted among them renumbers every one",
        )
    }

    /**
     * A drill plays under the rules its row advertises, and under nothing else.
     *
     * A **subset** rather than an equality, because two rows name more than they play on purpose:
     * Bonus's row names Malus and Order's names Chaos, each being the same mechanic with one thing
     * reversed, and a player looking for either in the list should find it somewhere. The reverse —
     * a match imposing a rule its row never mentioned — is the one that would be a defect.
     *
     * Non-empty is the other half: a drill whose `GameRules` came out as the default one would
     * play as an ordinary match with a tutor describing rules nobody switched on.
     */
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

    /**
     * Order in particular — the rule that narrows the hand rather than deciding a capture.
     *
     * Its whole visible effect is `MatchState.playableCards` returning one card, which is what
     * `MatchScreen.playable` reads and what greys out the other four. A drill played under
     * `OrderRule.FREE` would look like an ordinary match with a tutor describing a constraint the
     * player cannot feel, and every other assertion here would still pass.
     */
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

    /** Every drill deals a full hand, and every card in it resolves. */
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

    /**
     * The Bonus hand is five cards of one tribe, which is the lesson's first sentence.
     *
     * Five *different* cards too: one card repeated would satisfy the tribe check and would not be
     * a hand.
     */
    @Test
    fun theBonusDrillDealsOneTribe() {
        val deck = TUTORIAL_DRILLS[BONUS_DRILL].deck
        val types = deck.map { card(it).type }

        assertEquals(HAND_SIZE, deck.toSet().size, "five cards, not one card five times")
        assertEquals(1, types.toSet().size, "the hand is meant to be one tribe, and is $types")
        assertNotNull(types.first(), "an untyped hand gains nothing under Bonus, which is the rule")
    }

    /**
     * **Each card of the tribe strengthens the ones already down** — the drill's second line, put
     * to the engine.
     *
     * Three claims, in the order the lesson makes them: something happens when the first beast
     * lands, a card of no tribe in between adds nothing to it, and the second beast adds again. The
     * middle one is the sharp one — a modifier that merely counted *placements* would pass the
     * other two and would make the lesson's talk of tribes false.
     *
     * Played through the real engine rather than by handing it a tally, because a tally stated here
     * would be this test agreeing with itself: what the lesson claims is about what playing a card
     * does.
     */
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

    /**
     * The lines land on turns the player holds.
     *
     * A tutoring drill forces the player to open, so they hold the even placements — a line keyed
     * to an odd one would be spoken while the opponent was moving, which is where the AS3's own
     * tutorial put three of its nine and is exactly why those three never appeared. See
     * [TutorialScreen].
     */
    @Test
    fun everyLineIsSpokenOnATurnThePlayerHolds() {
        for (lesson in TUTORIAL_COURSE) {
            val drill = lesson.drill ?: continue
            if (!drill.tutoring) continue

            for (placement in drill.lines.keys) {
                assertEquals(
                    0,
                    placement % 2,
                    "${lesson.titleKey} speaks at $placement, which is the opponent's turn",
                )
            }
        }
    }

    /** The exam is the one drill examined rather than taught, and the flag is the whole of it. */
    @Test
    fun onlyTheExamIsUntutored() {
        assertEquals(
            listOf(TUTORIAL_COURSE.last()),
            TUTORIAL_COURSE.filter { it.drill?.tutoring == false },
            "the exam is the last row and the only one played against an opponent trying to win",
        )
    }

    /** Blue holds the tribe the lesson is about; red holds five cards that carry none. */
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

        /** Blue, red, blue: the three placements the tally is read across. */
        const val FIRST_CELL = 0
        const val SECOND_CELL = 1
        const val THIRD_CELL = 2

        /**
         * Five block-1 cards that carry no tribe, standing in for what the tutor brings.
         *
         * Not its hand exactly: `npcs.json` gives the Triple Triad Master 258, 260, 261, 263 and
         * 269, and 260 is not in [LESSON_CATALOG]. What matters is the property the lesson's first
         * line states out loud and these five share with the real five — **not one of them carries
         * a tribe** — so the count climbs on the player's side alone.
         */
        val TYPELESS_HAND = listOf(258, 261, 263, 269, 257)
    }
}
