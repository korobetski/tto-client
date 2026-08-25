package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
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

    private companion object {
        val FIRST_RULE = HELP_RULES.first()
        val SECOND_RULE = HELP_RULES[1]

        const val COMBO = "RULE_COMBO"

        const val RULES_LISTED = 17

        const val FAMILIES = 4
    }
}
