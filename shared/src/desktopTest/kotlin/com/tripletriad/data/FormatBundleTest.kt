package com.tripletriad.data

import com.tripletriad.FF14_BLOCK
import com.tripletriad.FF14_FORMAT
import com.tripletriad.FF8_BLOCK
import com.tripletriad.FF8_FORMAT
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormatBundleTest {
    private val formats = runBlocking { loadFormatCatalog() }
    private val cards = runBlocking { loadCardCatalog() }

    @Test
    fun eachSetHasASingleSetFormatThatDrawsSomething() {
        // FF14 admits both of its blocks — it outgrew 255 cards and split across two, and a
        // format that took only the first would deal a collection nobody can play out of. FF8
        // still fits in one.
        val expected = listOf(
            FF14_FORMAT to listOf(FF14_BLOCK, 2),
            FF8_FORMAT to listOf(FF8_BLOCK),
        )
        for ((id, blocks) in expected) {
            val format = assertNotNull(formats[id], "$id is not authored")

            assertEquals(blocks, format.blocks, "$id should admit exactly $blocks")
            assertTrue(format.rules.isNotEmpty(), "$id has nothing to draw")
        }
    }

    @Test
    fun theTwoPoolsAreNotTheSame() {
        val ff14 = assertNotNull(formats[FF14_FORMAT]).rules
        val ff8 = assertNotNull(formats[FF8_FORMAT]).rules

        assertTrue("RULE_ELEMENTAL" in ff8 && "RULE_ELEMENTAL" !in ff14, "Elemental is FF8-only")
        assertTrue("RULE_SAME_WALL" in ff8 && "RULE_SAME_WALL" !in ff14, "Same Wall is FF8-only")
        assertTrue("RULE_ASCENSION" in ff14 && "RULE_ASCENSION" !in ff8, "Ascension is FF14-only")
        assertTrue("RULE_SWAP" in ff14 && "RULE_SWAP" !in ff8, "Swap is FF14-only")
    }

    @Test
    fun theShippedFormatsBreakNoneOfTheAuthoringRules() {
        val problems = formats.violations(cards.sets)

        assertTrue(problems.isEmpty(), "formats.json: ${problems.joinToString("; ")}")
    }

    @Test
    fun everyReleasedSetIsAdmittedBySomeFormat() {
        for (set in cards.releasedSets) {
            assertTrue(
                // Every block of it, not just the first: a set spanning two blocks whose second
                // one no format admits is half a collection nobody can play.
                set.blocks.all { formats.admitting(it).isNotEmpty() },
                "${set.slug} is released and some block of it is admitted by no format",
            )
        }
    }

    @Test
    fun aCardIsAdmittedByItsOwnSetsFormat() {
        val ff14 = assertNotNull(formats[FF14_FORMAT])
        val ff8 = assertNotNull(formats[FF8_FORMAT])
        val dodo = cards.block(FF14_BLOCK).first().id
        val geezard = cards.block(FF8_BLOCK).first().id

        assertTrue(ff14.admitsCard(dodo))
        assertTrue(!ff14.admitsCard(geezard))
        assertTrue(ff8.admitsCard(geezard))
        assertTrue(!ff8.admitsCard(dodo))
    }

    @Test
    fun everyFormatNameResolves() {
        val english = runBlocking { loadStrings(AppLocale.EN_US) }
        val unresolved = formats.formats.map { it.nameKey }.filter { english[it] == it }

        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    @Test
    fun aBrokenCatalogueIsRefusedOnEveryCount() {
        val broken = FormatCatalog(
            listOf(
                Format(
                    id = "broken",
                    nameKey = "APP_NOWHERE",
                    blocks = listOf(GHOST_BLOCK),
                    // `RULE_COMBO` is in the help screen's list and is a dead constant: nothing
                    // turns it on. A format naming it would be promising a rule it cannot deliver.
                    rules = listOf("RULE_SAME", "RULE_SAME", "RULE_COMBO", "NOT_A_RULE"),
                ),
                Format(
                    id = "broken",
                    nameKey = "APP_NOWHERE",
                    blocks = emptyList(),
                    rules = emptyList(),
                ),
            ),
        )

        val problems = broken.violations(cards.sets)

        assertTrue(problems.any { "not unique" in it }, problems.toString())
        assertTrue(problems.any { "admits no set" in it }, problems.toString())
        assertTrue(problems.any { "nothing ships" in it }, problems.toString())
        assertTrue(problems.any { "empty rule pool" in it }, problems.toString())
        assertTrue(problems.any { "not rules" in it }, problems.toString())
        assertTrue(problems.any { "lists a rule twice" in it }, problems.toString())
        assertTrue(problems.any { "released and no format admits it" in it }, problems.toString())
    }

    @Test
    fun idsAreUniqueInTheShippedFile() {
        val ids = formats.formats.map { it.id }

        assertEquals(ids.size, ids.toSet().size, ids.toString())
    }

    private companion object {
        const val GHOST_BLOCK = 99
    }
}
