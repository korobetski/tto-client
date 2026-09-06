package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The book's own arithmetic: what a search admits, and where a row lands once headings are counted.
 *
 * No Compose here on purpose. A wrong section order or an index off by the number of headings is
 * invisible to every test that only checks the rows exist — which is what the screen's tests did
 * before there was anything to get wrong.
 */
class HelpBookTest {
    /** A book of made-up names and paragraphs, so a bundle edit cannot make these pass or fail. */
    private val names = mapOf(
        "RULE_REVERSE" to "Reverse",
        "RULE_FALLEN_ACE" to "Fallen Ace",
        "RULE_SAME" to "Same",
        "RULE_ALL_OPEN" to "All Open",
    )

    private val texts = mapOf(
        "RULE_REVERSE" to "The lower number wins.",
        "RULE_FALLEN_ACE" to "A side of one may turn an ace.",
        "RULE_SAME" to "Two equal sides turn both cards.",
        "RULE_ALL_OPEN" to "Both hands are shown.",
    )

    private fun sections(query: String, last: List<String> = emptyList()) = helpSections(
        query = query,
        lastRules = last,
        nameOf = { names[it] ?: it },
        textOf = { texts[it] ?: "" },
    )

    private fun rulesIn(query: String, last: List<String> = emptyList()) =
        sections(query, last).flatMap { it.rules }

    @Test
    fun anEmptySearchIsTheWholeBook() {
        assertEquals(HELP_RULES, rulesIn(""))
    }

    @Test
    fun theFamiliesArriveInTheOrderTheBookIsWrittenIn() {
        assertEquals(HELP_FAMILIES.map { it.labelKey }, sections("").map { it.labelKey })
    }

    @Test
    fun aSearchFindsARuleByItsName() {
        assertEquals(listOf("RULE_FALLEN_ACE"), rulesIn("fallen"))
    }

    @Test
    fun aSearchIgnoresCaseAndTheSpaceAroundIt() {
        assertEquals(rulesIn("fallen"), rulesIn("  FALLEN "))
    }

    /** The half of the search that a title-only filter would lose, and the reason it exists. */
    @Test
    fun aSearchFindsARuleByWhatItSaysAndNotOnlyByWhatItIsCalled() {
        assertEquals(listOf("RULE_REVERSE"), rulesIn("lower number"))
        assertTrue("RULE_SAME" in rulesIn("equal sides"), "a paragraph is searchable too")
    }

    @Test
    fun aRuleWithNoParagraphIsStillFoundByItsName() {
        // `RULE_THREE_OPEN` is in neither fixture map, so it has a key for a name and no text.
        assertTrue("RULE_THREE_OPEN" in rulesIn("RULE_THREE_OPEN"))
    }

    @Test
    fun aSearchThatMatchesNothingIsAnEmptyBookRatherThanAFullOne() {
        assertEquals(emptyList(), sections("zzzz"))
    }

    @Test
    fun anEmptiedFamilyLeavesNoHeadingBehind() {
        val kept = sections("fallen")
        assertEquals(listOf(StringKeys.HELP_FAMILY_CAPTURE), kept.map { it.labelKey })
    }

    @Test
    fun theLastMatchIsPutInFrontOfTheTableOfContents() {
        val kept = sections("", listOf("RULE_SAME", "RULE_PLUS"))

        assertEquals(StringKeys.HELP_LAST_MATCH, kept.first().labelKey)
        assertEquals(listOf("RULE_SAME", "RULE_PLUS"), kept.first().rules)
    }

    @Test
    fun aRuleTheBookDoesNotListIsNotPutOnTopOfIt() {
        val kept = sections("", listOf("RULE_SAME", "RULE_NOT_IN_THE_BOOK"))

        assertEquals(listOf("RULE_SAME"), kept.first().rules)
    }

    @Test
    fun aMatchWithNoNamedRulesAddsNoHeading() {
        assertEquals(HELP_FAMILIES.size, sections("", emptyList()).size)
    }

    @Test
    fun aSearchPutsTheLastMatchAwayRatherThanPrintingItsRulesTwice() {
        val kept = sections("same", listOf("RULE_SAME"))

        assertTrue(
            kept.none { it.labelKey == StringKeys.HELP_LAST_MATCH },
            "a player who is typing has gone past the shortcut",
        )
        assertEquals(1, kept.count { "RULE_SAME" in it.rules }, "one row, not two")
    }

    @Test
    fun aRowIsFoundPastTheHeadingsAboveIt() {
        val book = sections("")
        // The first family's heading is index 0, so its first rule is 1 and its second is 2.
        assertEquals(1, rowIndexOf(book, HELP_FAMILIES.first().rules.first()))
        assertEquals(2, rowIndexOf(book, HELP_FAMILIES.first().rules[1]))
    }

    @Test
    fun everyHeadingBeforeARowIsCounted() {
        val book = sections("")
        val second = HELP_FAMILIES[1]
        // Two headings and the first family's rules stand above it.
        val expected = 2 + HELP_FAMILIES.first().rules.size
        assertEquals(expected, rowIndexOf(book, second.rules.first()))
    }

    @Test
    fun aRuleShownTwiceIsScrolledToWhereItIsAlreadyVisible() {
        val book = sections("", listOf("RULE_COMBO"))

        assertEquals(1, rowIndexOf(book, "RULE_COMBO"), "the copy on top, not the one below")
    }

    @Test
    fun aRuleThatIsNotShownHasNowhereToScrollTo() {
        assertNull(rowIndexOf(sections("fallen"), "RULE_SAME"))
    }

    @Test
    fun everyRuleWithADiagramIsARuleTheBookLists() {
        val strays = RULE_DIAGRAMS.keys.filterNot { it in HELP_RULES }
        assertTrue(strays.isEmpty(), "diagrams for rules with no entry: $strays")
    }

    /**
     * The invariant the diagrams exist for: each shows the rule **and its limit**.
     *
     * A single capturing frame would illustrate "a 1 beats an A" — which is the half of Fallen Ace
     * that players already remember, and the half that is wrong on its own.
     */
    @Test
    fun everyDiagramShowsACaptureAndAFailure() {
        for ((ruleKey, frames) in RULE_DIAGRAMS) {
            assertTrue(frames.any { it.captured }, "$ruleKey never captures")
            assertTrue(frames.any { !it.captured }, "$ruleKey never fails, so it reads as always")
        }
    }
}
