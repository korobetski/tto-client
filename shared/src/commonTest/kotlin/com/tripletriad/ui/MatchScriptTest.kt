package com.tripletriad.ui

import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.MatchAiOptions
import com.tripletriad.model.MatchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * What a script is allowed to decide, and what it falls back to when it decides nothing.
 *
 * Every function here is an extension on a **nullable** `MatchScript`, and that is the whole
 * design: an ordinary match passes `null` and gets the default, a lesson passes a script and
 * overrides one thing. So each has two answers worth having, and the pair is the test.
 *
 * `startingMatch` and `creditFor` are not here — [LessonRecordTest] owns the record-keeping half.
 */
class MatchScriptTest {

    // ---- The opening line -------------------------------------------------

    @Test
    fun anOpeningLineIsSaidBeforeTheFirstPlacementAndNotAgain() {
        val lesson = Lesson.opening("STR_HELLO")

        assertEquals(listOf("STR_HELLO"), lesson.linesBefore(placement = 0, blueScore = 5))
        assertEquals(emptyList(), lesson.linesBefore(placement = 1, blueScore = 5))
    }

    /** A lesson with nothing to say opens in silence rather than with an empty bubble. */
    @Test
    fun anOpeningWithNoLineSaysNothingAtAll() {
        val lesson = Lesson.opening(null)

        assertEquals(emptyList(), lesson.linesBefore(placement = 0, blueScore = 5))
    }

    @Test
    fun aSilentLessonSaysNothingAtAnyPlacement() {
        for (placement in 0..9) {
            assertEquals(emptyList(), Lesson.Silent.linesBefore(placement, blueScore = 5))
        }
    }

    @Test
    fun theLinesForAStateComeFromTheScriptsOwnLesson() {
        val script = script(lesson = Lesson.opening("STR_HELLO"))

        assertEquals(listOf("STR_HELLO"), script.linesBefore(MatchState()))
    }

    /** No script is no lesson, and a board with nobody to teach it says nothing. */
    @Test
    fun anUnscriptedMatchHasNoLines() {
        assertEquals(emptyList(), (null as MatchScript?).linesBefore(MatchState()))
    }

    // ---- The deal ---------------------------------------------------------

    @Test
    fun aScriptsOwnDeckWinsOverTheProfiles() {
        val fixed = listOf(1, 2, 3, 4, 5)

        val dealt = script(deck = fixed).deckFor(GameRules(random = true), profile)

        assertEquals(fixed, dealt, "a lesson deals the hand it was written for")
    }

    /**
     * Under Random the profile's deck is named, and otherwise nothing is.
     *
     * Null is not "no cards" — it is "this side is not choosing", and the referee deals. Only the
     * Random rule needs the collection named up front, because that is what it draws from.
     */
    @Test
    fun withoutAScriptOnlyRandomNamesTheProfilesDeck() {
        assertEquals(
            PveMatches.playerDeck(profile),
            (null as MatchScript?).deckFor(GameRules(random = true), profile),
        )
        assertNull((null as MatchScript?).deckFor(GameRules(), profile))
    }

    @Test
    fun aScriptedFirstPlayerForcesTheCoin() {
        val opensRed = MatchScript(speakerKey = SPEAKER, firstPlayer = CardColor.RED)

        assertEquals(CardColor.RED, opensRed.flip()?.winner, "the toss was decided by the script")
    }

    @Test
    fun anUnscriptedMatchTossesForItself() {
        assertNull((null as MatchScript?).flip(), "there is no forced coin to hand over")
    }

    // ---- The fallbacks ----------------------------------------------------

    @Test
    fun aScriptsRulesWinAndOtherwiseTheCallersAreAsked() {
        var asked = 0
        val fallback = {
            asked++
            GameRules(reverse = true)
        }

        assertEquals(
            GameRules(swap = true),
            script(rules = GameRules(swap = true)).rulesOr(fallback),
        )
        assertEquals(0, asked, "a script's rules are not a fallback away")

        assertEquals(GameRules(reverse = true), (null as MatchScript?).rulesOr(fallback))
        assertEquals(1, asked)
    }

    @Test
    fun aScriptsTurnLimitWinsAndOtherwiseTheDefaultStands() {
        val hurried = MatchScript(speakerKey = SPEAKER, turnLimit = 5.seconds)

        assertEquals(5.seconds, hurried.turnLimitOr(30.seconds))
        assertEquals(30.seconds, (null as MatchScript?).turnLimitOr(30.seconds))
    }

    @Test
    fun aScriptsAiOptionsWinAndOtherwiseTheOpponentPlaysAtFullStrength() {
        val weakened = MatchAiOptions(blunderRate = 1.0)

        assertEquals(weakened, script(aiOptions = weakened).aiOptions())
        assertEquals(MatchAiOptions(), (null as MatchScript?).aiOptions())
    }

    // ---- The explaining rings ---------------------------------------------

    /**
     * Only a lesson annotates a capture.
     *
     * `captureHighlights` names the two facing digits that decided a flip, which is teaching. An
     * ordinary match shows the rule working rather than explaining it, so the map is empty and
     * `PveMatchScreen` passes `emptyMap()` outright.
     */
    @Test
    fun onlyAnExplainingScriptRingsTheDigitsThatDecidedACapture() {
        val played = captured()

        assertTrue(script(explains = true).highlights(played).isNotEmpty(), "a lesson explains")
        assertEquals(emptyMap(), script(explains = false).highlights(played))
        assertEquals(emptyMap(), (null as MatchScript?).highlights(played))
    }

    // ---- What the outcome panel offers next -------------------------------

    /**
     * An ordinary match offers a rematch; a scripted one offers whatever the script says.
     *
     * A lesson's second control is the next rung, not another go at this one — and a campaign that
     * offered "Rematch" would let the player replay a lesson forever instead of advancing.
     */
    @Test
    fun anUnscriptedMatchOffersARematch() {
        var asked = 0
        val exit = ScriptExit("STR_NEXT") {}

        val next = nextAction(script = null, exit = exit) { asked++ }

        assertEquals(StringKeys.REMATCH, next?.labelKey, "the ordinary second control")
        next?.onLeave?.invoke()
        assertEquals(1, asked, "and it is the rematch that was handed in")
    }

    @Test
    fun aScriptedMatchOffersTheScriptsOwnExit() {
        val exit = ScriptExit("STR_NEXT") {}

        assertSame(exit, nextAction(script = script(), exit = exit) {})
    }

    /** A script with nowhere to go next offers nothing rather than falling back on a rematch. */
    @Test
    fun aScriptedMatchWithNoExitOffersNothing() {
        assertNull(nextAction(script = script(), exit = null) {})
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun script(
        deck: List<Int>? = null,
        aiOptions: MatchAiOptions = MatchAiOptions(),
        lesson: Lesson = Lesson.Silent,
        rules: GameRules? = null,
        explains: Boolean = false,
    ) = MatchScript(
        speakerKey = SPEAKER,
        deck = deck,
        aiOptions = aiOptions,
        lesson = lesson,
        rules = rules,
        explains = explains,
    )

    /** A board where blue's card has just taken red's next door, so there is something to ring. */
    private fun captured(): MatchState {
        val blue = (1..HAND_SIZE).map { card(it, power = 8) }
        val red = (11 until 11 + HAND_SIZE).map { card(it, power = 2) }
        val opened = MatchState
            .start(blueHand = blue, redHand = red, first = CardColor.RED)
            .play(red.first(), position = 0)
        return opened.play(blue.first(), position = 1)
    }

    private fun card(number: Int, power: Int) = Card(
        id = Card.idFor(block = 1, number = number),
        nameKey = "STR_TEST_$number",
        name = "Test $number",
        top = power,
        right = power,
        bottom = power,
        left = power,
        rarity = 1,
    )

    private val profile = GameSave.new(createdAt = 0L)

    private companion object {
        const val SPEAKER = "STR_NPC_TT_Master"
    }
}
