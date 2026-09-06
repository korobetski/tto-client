package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameRules
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HelpUiTest {
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    private fun ComposeUiTest.openHelp() {
        newCharacter()
        openFromDashboard(DASHBOARD_HELP_TEST_TAG, HELP_LIST_TEST_TAG)
    }

    @Test
    fun tappingARuleOpensItsTextAndTappingAgainClosesIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        assertFalse(existsUnmerged(helpTextTestTag(FIRST_RULE)), "nothing is open on arrival")

        onNodeWithTag(helpRuleTestTag(FIRST_RULE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(FIRST_RULE)) }

        onNodeWithTag(helpRuleTestTag(FIRST_RULE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !existsUnmerged(helpTextTestTag(FIRST_RULE)) }
    }

    @Test
    fun openingASecondRuleClosesTheFirst() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        onNodeWithTag(helpRuleTestTag(FIRST_RULE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(FIRST_RULE)) }
        onNodeWithTag(helpRuleTestTag(SECOND_RULE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(SECOND_RULE)) }

        assertFalse(existsUnmerged(helpTextTestTag(FIRST_RULE)), "two rules were open at once")
    }

    @Test
    fun everyRuleHasARowOnTheScreen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        for (ruleKey in HELP_RULES) {
            onNodeWithTag(HELP_LIST_TEST_TAG)
                .performScrollToNode(hasTestTag(helpRuleTestTag(ruleKey)))
        }
    }

    @Test
    fun everyRuleResolvesToBothALabelAndAText() {
        val unresolved = HELP_RULES.flatMap { ruleKey ->
            listOf(ruleKey, "${ruleKey}_HELP").filter { english[it] == it }
        }
        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    @Test
    fun theListHasNoDuplicates() {
        assertEquals(HELP_RULES.size, HELP_RULES.toSet().size, HELP_RULES.toString())
        assertEquals(RULES_LISTED, HELP_RULES.size)
    }

    @Test
    fun comboIsExplainedHereAndIsNotARuleTheEngineToggles() {
        assertTrue(COMBO in HELP_RULES, "the one place combo is legitimately named")
        assertFalse(
            COMBO in GameRules().activeRuleKeys(),
            "combo is not a flag; it fires whenever Same or Plus captures",
        )
    }

    @Test
    fun theRulesAreGroupedUnderFourHeadings() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        assertEquals(FAMILIES, HELP_FAMILIES.size)
        for (family in HELP_FAMILIES) {
            onNodeWithTag(HELP_LIST_TEST_TAG)
                .performScrollToNode(hasTestTag(helpFamilyTestTag(family.labelKey)))
        }
        assertEquals(
            HELP_RULES.size,
            HELP_FAMILIES.sumOf { it.rules.size },
            "a rule is in exactly one family",
        )
    }

    @Test
    fun everyHeadingResolves() {
        val unresolved = HELP_FAMILIES.map { it.labelKey }.filter { english[it] == it }
        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    /** The one control a book of seventeen entries needs, and the one it did not have. */
    @Test
    fun theBookIsSearchedByTheNameOfARule() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        onNodeWithTag(HELP_SEARCH_TEST_TAG).performTextReplacement(english[FALLEN_ACE])

        assertTrue(exists(helpRuleTestTag(FALLEN_ACE)), "the rule searched for is not shown")
        assertFalse(exists(helpRuleTestTag(FIRST_RULE)), "the rest of the book is still there")
    }

    /**
     * And by what the rule *says*, which is how somebody who has forgotten its name arrives.
     *
     * The needle is taken from the bundle rather than written here: it must be a word the English
     * paragraph really contains, and pinning one in the test would make this a test of the bundle.
     */
    @Test
    fun theBookIsSearchedByWhatARuleSaysAndNotOnlyByItsName() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        val word = english["${FALLEN_ACE}_HELP"].split(" ").first { it.length > SHORT_WORD }
        onNodeWithTag(HELP_SEARCH_TEST_TAG).performTextReplacement(word)

        assertTrue(exists(helpRuleTestTag(FALLEN_ACE)), "a paragraph should be searchable")
    }

    @Test
    fun aBookNarrowedToNothingSaysSoWithoutHidingTheWayBack() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        onNodeWithTag(HELP_SEARCH_TEST_TAG).performTextReplacement("zzzz")

        assertTrue(exists(HELP_NO_MATCH_TEST_TAG), "the book went blank instead of saying so")
        // The tag alone would pass with any sentence under it, and the one sentence that must not
        // be there is the empty-book one: the book is full, the query is not.
        onNodeWithTag(HELP_NO_MATCH_TEST_TAG)
            .assertTextEquals(english[StringKeys.HELP_NO_MATCH])
        onNodeWithTag(HELP_SEARCH_TEST_TAG).assertExists()
    }

    /** The picture that settles which way Fallen Ace runs. See [RULE_DIAGRAMS]. */
    @Test
    fun aRuleAPairOfCardsCanStateIsDrawnAsWellAsWritten() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        onNodeWithTag(HELP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(helpRuleTestTag(FALLEN_ACE)))
        onNodeWithTag(helpRuleTestTag(FALLEN_ACE)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(FALLEN_ACE)) }
        assertTrue(
            existsUnmerged(ruleDiagramTestTag(FALLEN_ACE)),
            "the rule the diagrams exist for is not drawn",
        )
    }

    /**
     * And the picture says which of the two placements captured.
     *
     * Asserted on the frame's own sentence rather than on the arrow glyph: the arrow is what a
     * sighted player reads, the sentence is what everybody else gets, and a diagram that drew both
     * frames the same way would still have two frames.
     */
    @Test
    fun theTwoFramesOfARuleSayOppositeThings() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        onNodeWithTag(HELP_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(helpRuleTestTag(FALLEN_ACE)))
        onNodeWithTag(helpRuleTestTag(FALLEN_ACE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(ruleDiagramTestTag(FALLEN_ACE)) }

        val frames = RULE_DIAGRAMS.getValue(FALLEN_ACE)
        assertEquals(
            listOf(true, false),
            frames.map { it.captured },
            "one frame captures and the other does not, or there is nothing to tell apart",
        )
        for (frame in frames) {
            val outcome = english[
                if (frame.captured) StringKeys.HELP_CAPTURES else StringKeys.HELP_FAILS,
            ]
            onNodeWithContentDescription(
                "${frame.attacker} $outcome ${frame.defender}",
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    /** And a rule a pair of cards cannot state keeps its paragraph and gets no picture. */
    @Test
    fun aRuleAPairOfCardsCannotStateIsLeftAsProse() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        onNodeWithTag(HELP_LIST_TEST_TAG).performScrollToNode(hasTestTag(helpRuleTestTag(SAME)))
        onNodeWithTag(helpRuleTestTag(SAME)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(SAME)) }
        assertFalse(
            existsUnmerged(ruleDiagramTestTag(SAME)),
            "Same is about two sides at once and cannot be drawn with one pair",
        )
    }

    private companion object {
        val FIRST_RULE = HELP_RULES.first()
        val SECOND_RULE = HELP_RULES[1]

        const val COMBO = "RULE_COMBO"

        const val FALLEN_ACE = "RULE_FALLEN_ACE"
        const val SAME = "RULE_SAME"

        /** Long enough that the word is not "a" or "the", which every paragraph contains. */
        const val SHORT_WORD = 4

        const val RULES_LISTED = 17

        const val FAMILIES = 4
    }
}
