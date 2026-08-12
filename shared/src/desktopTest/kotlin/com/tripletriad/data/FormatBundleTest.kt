package com.tripletriad.data

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardCollection
import com.tripletriad.model.Roulette
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shipped `formats.json`, and **the one assertion this whole file exists for**.
 *
 * Document 19 moves the rule pools out of `:core` and into data. Today they are in both places:
 * `Roulette.pools` is what the engine actually draws from, and [FormatCatalog] is the transcription
 * that will replace it. Two copies of the same fact is a defect waiting to happen — so while both
 * exist, this test holds them to being identical.
 *
 * That is what makes the eventual switch a deletion rather than a rewrite. It is also what catches
 * the failure mode in between: somebody tunes a pool in one place, ships, and the roulette draws
 * something the format says it cannot.
 */
class FormatBundleTest {
    private val formats = runBlocking { loadFormatCatalog() }
    private val cards = runBlocking { loadCardCatalog() }

    /**
     * Every format's rules are exactly the pool `:core` compiles for the same set — in order.
     *
     * In order, not as a set: `Roulette.pools`' own KDoc says source order is preserved though no
     * longer load-bearing, and a transcription that quietly reordered would make the two hard to
     * diff by eye the day somebody has to.
     */
    @Test
    fun everyFormatsRulesAreThePoolTheEngineCompiles() {
        for (collection in CardCollection.entries) {
            val format = assertNotNull(
                formats.forCollection(collection),
                "no format admits exactly ${collection.slug}'s block",
            )

            assertContentEquals(
                Roulette.pool(collection),
                format.rules,
                "${format.id} has drifted from Roulette.pools[${collection.name}]",
            )
        }
    }

    /** And the transcription is not vacuous: the two pools really do differ from each other. */
    @Test
    fun theTwoPoolsAreNotTheSame() {
        val ff14 = assertNotNull(formats.forCollection(CardCollection.FF14)).rules
        val ff8 = assertNotNull(formats.forCollection(CardCollection.FF8)).rules

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
        val ff14 = assertNotNull(formats.forCollection(CardCollection.FF14))
        val ff8 = assertNotNull(formats.forCollection(CardCollection.FF8))
        val dodo = cards.block(CardCollection.FF14.block).first().id
        val geezard = cards.block(CardCollection.FF8.block).first().id

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
