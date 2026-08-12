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

/**
 * The shipped `formats.json`.
 *
 * ### The assertion this file existed for is gone, and that is the good outcome
 *
 * Document 19 moves the rule pools out of `:core` and into data. For one release they were in both
 * places — `Roulette.pools` compiled into the engine, and this catalogue as the transcription — and
 * the central test here held them to being **identical, in order**. That is what made the switch a
 * deletion rather than a rewrite nobody could check, and what would have caught somebody tuning a
 * pool in one of the two places.
 *
 * `Roulette.pools` no longer exists. There is nothing left to compare against, so the assertion is
 * deleted rather than weakened: a test that outlives the duplication it was guarding is a test that
 * asserts a shipped file equals itself.
 *
 * What is kept is everything that is still a claim about the *content*: the pools differ from each
 * other in the ways the sets do, the authoring rules hold, every released set is playable, and a
 * card belongs to its own set's format.
 */
class FormatBundleTest {
    private val formats = runBlocking { loadFormatCatalog() }
    private val cards = runBlocking { loadCardCatalog() }

    /**
     * Each shipped set still has a format that admits exactly its block, and it draws something.
     *
     * The ladders are what still need these: a campaign names the format its rungs are played
     * under, and one that resolved to nothing would be an entry fee taken for a match that cannot
     * be dealt. Ordinary matches use [FormatCatalog.default] and would survive their absence.
     */
    @Test
    fun eachSetHasASingleSetFormatThatDrawsSomething() {
        for ((id, block) in listOf(FF14_FORMAT to FF14_BLOCK, FF8_FORMAT to FF8_BLOCK)) {
            val format = assertNotNull(formats[id], "$id is not authored")

            assertEquals(listOf(block), format.blocks, "$id should admit block $block alone")
            assertTrue(format.rules.isNotEmpty(), "$id has nothing to draw")
        }
    }

    /** And the transcription is not vacuous: the two pools really do differ from each other. */
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

    /** Every released set can be played, and every format names a set that ships. */
    @Test
    fun everyReleasedSetIsAdmittedBySomeFormat() {
        for (set in cards.releasedSets) {
            assertTrue(
                formats.admitting(set.block).isNotEmpty(),
                "${set.slug} is released and no format admits it",
            )
        }
    }

    /** A card belongs to the format that admits its block, and to no other. */
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

    /**
     * Each format's name resolves.
     *
     * Data-driven like `Starter.nameKey`, so `StringsBundleTest` cannot see it. Nothing shows these
     * yet — a format is not a thing a player picks while `MODE` still decides everything — and they
     * are asserted now so the day one is shown it is not `APP_FORMAT_FF14_STANDARD`.
     */
    @Test
    fun everyFormatNameResolves() {
        val english = runBlocking { loadStrings(AppLocale.EN_US) }
        val unresolved = formats.formats.map { it.nameKey }.filter { english[it] == it }

        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    /**
     * A catalogue that breaks the rules is caught on every count.
     *
     * Without this the suite above would pass just as happily against a `violations` that returned
     * an empty list unconditionally.
     */
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
        /** A block no set declares, so `violations` has something to refuse. */
        const val GHOST_BLOCK = 99
    }
}
