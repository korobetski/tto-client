package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_FORMAT
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.Npc
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The chips and the menu that replaced the three shelves.
 *
 * What these hold is the reason the shelves went: the roster answers one question at a time now,
 * and every opponent it answers with is drawn once. The old screen opened by drawing "never
 * played" and "has a card you're missing" above the roster, and on a new character those two held
 * 22 and 19 of the same people in the same order — so the first faces appeared three times before
 * a single filter had been pressed.
 *
 * The roster is read from the catalogue at a fresh character's own level rather than typed, so
 * these say what the *filter* does and not what `npcs.json` happens to hold this week. The one
 * exception is "All Open", which is asserted by its English wording because a tile that carries a
 * rules line nobody can read would satisfy any test written against the key.
 */
@OptIn(ExperimentalTestApi::class)
class OpponentFilterUiTest {
    private val catalog = runBlocking { loadNpcCatalog() }

    /** What a character made through the UI sees: level 1, no achievement earned, at noon. */
    private val roster: List<Npc> =
        catalog.available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, STARTER_LEVEL)

    @Test
    fun theTimedChipLeavesOnlyTheOnesWithAnHour() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        // Present before the chip is pressed — otherwise the assertion below would pass against
        // a roster this character never had.
        scrollToOpponent(TEST_OPPONENT)
        assertTrue(exists(opponentRowTestTag(TEST_OPPONENT)), "$TEST_OPPONENT is listed")

        onNodeWithTag(opponentReasonTestTag("timed")).performClick()

        assertFalse(
            exists(opponentRowTestTag(TEST_OPPONENT)),
            "$TEST_OPPONENT is open at every hour and should be filtered out",
        )
    }

    @Test
    fun theRuleMenuFindsWhoPlaysThatRule() = runComposeUiTest {
        val rule = ruleOf(TEST_OPPONENT)
        val other = roster.first { rule !in it.ruleKeys }

        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        pickRule(rule)

        scrollToOpponent(TEST_OPPONENT)
        assertTrue(
            exists(opponentRowTestTag(TEST_OPPONENT)),
            "$TEST_OPPONENT plays $rule and should survive the filter",
        )
        assertFalse(
            exists(opponentRowTestTag(other.iconId)),
            "${other.iconId} does not play $rule and should not be listed",
        )
    }

    @Test
    fun theRuleFilterCanBeGivenBack() = runComposeUiTest {
        val rule = ruleOf(TEST_OPPONENT)
        val other = roster.first { rule !in it.ruleKeys }

        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        pickRule(rule)
        assertFalse(exists(opponentRowTestTag(other.iconId)), "the filter took hold")

        pickRule(null)

        scrollToOpponent(other.iconId)
        assertTrue(
            exists(opponentRowTestTag(other.iconId)),
            "clearing the rule should bring back everybody it hid",
        )
    }

    /**
     * The one thing proposition A gave up and this screen did not: a grid tile is not a row, but
     * it still says what a match costs and what it is played under. Both are read *before* a tap,
     * and the sheet those used to live in is after.
     */
    @Test
    fun aTileSaysWhatTheMatchCostsAndWhichRulesItIsPlayedUnder() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        scrollToOpponent(TEST_OPPONENT)

        val npc = roster.first { it.iconId == TEST_OPPONENT }
        assertTrue(tileSays(TEST_OPPONENT, "All Open"), "the tile should name the rule imposed")
        assertTrue(
            tileSays(TEST_OPPONENT, "${npc.matchFee}"),
            "the tile should name the ${npc.matchFee} fee",
        )
    }

    private fun ruleOf(iconId: String): String {
        val rules = roster.first { it.iconId == iconId }.ruleKeys
        check(rules.isNotEmpty()) { "$iconId should impose at least one rule" }
        return rules.first()
    }

    /** Opens the rule menu and takes one entry — null being "any rule", its own entry. */
    private fun ComposeUiTest.pickRule(ruleKey: String?) {
        onNodeWithTag(OPPONENT_RULE_FILTER_TEST_TAG).performClick()
        onNodeWithTag(opponentRuleChoiceTestTag(ruleKey)).performClick()
    }

    /** What one tile carries, as opposed to what the screen carries. */
    private fun ComposeUiTest.tileSays(iconId: String, text: String): Boolean =
        onAllNodes(hasTestTag(opponentRowTestTag(iconId)).and(hasText(text, substring = true)))
            .fetchSemanticsNodes()
            .isNotEmpty()

    private companion object {
        /** What `newCharacter()` produces, and what the roster above is read at. */
        const val STARTER_LEVEL = 1
    }
}
