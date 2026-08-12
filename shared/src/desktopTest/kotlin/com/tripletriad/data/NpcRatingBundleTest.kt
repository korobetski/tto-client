package com.tripletriad.data

import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every shipped opponent's difficulty, skill band, payout and fee, held to [NpcRating].
 *
 * ### What this replaces
 *
 * Four hand-authored columns that had stopped meaning anything together. `difficulty` ran 1..19 in
 * the FFXIV table and was **0 for all twenty-five FFVIII entries**; a win paid between 0 and 182
 * MGP with no relation to either. The two tables were authored separately and never had to agree,
 * which was fine until `MODE` went and put them on one screen sorted by difficulty. At that point
 * the twenty-five zeroes sorted ahead of the whole FFXIV ladder.
 *
 * [NpcRating] replaces all four with one measurement: how often a neutral reference profile beats
 * this opponent, over four hundred simulated matches. See that object for why it is measured rather
 * than computed from a formula over card power and rule weights.
 *
 * ### This test is also the generator
 *
 * It always writes `build/npc-ratings.json` — the four values the model gives each opponent —
 * before asserting. That file is what `tools/apply_npc_ratings.py` copies into the two `npcs.json`,
 * so the *arithmetic* exists once, here, in Kotlin. The script does no maths; it moves numbers. A
 * second implementation in Python would be a second source of truth for the thing this whole file
 * exists to make single.
 *
 * So the loop when the model changes is: run this, run the script, run this again. It is green when
 * the shipped data says what the model says.
 *
 * ### It is slow, and that is the price
 *
 * 85 opponents × 400 matches ≈ 34 000 matches. Seconds, not milliseconds. The alternative is not
 * measuring, and it is worth the seconds because this catches the failure that matters: somebody
 * edits a card's power or an opponent's rules, and the difficulty deciding where they sit in the
 * list quietly stops being true.
 */
class NpcRatingBundleTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }
    private val formats = runBlocking { loadFormatCatalog() }

    /** The widest format, so no opponent is rated in one the reference could not bring cards to. */
    private val format: Format = requireNotNull(formats.default) { "no format is authored" }

    /**
     * The yardstick — see [NpcRating.referenceProfile] for why it is the middle of the card table
     * and not an authored starter.
     *
     * One reference for both sets, which is the whole point. An FFVIII opponent has never been
     * comparable to an FFXIV one because nothing had ever played both; with `MODE` gone a character
     * can hold either set's cards, so a single yardstick is not only possible but correct.
     */
    private val reference: GameSave = NpcRating.referenceProfile(cards, format)

    @Test
    fun everyOpponentCarriesTheRatingTheModelGives() {
        val rated = npcs.npcs.map { npc -> npc to NpcRating.rated(npc, winRateOf(npc)) }
        writeRatings(rated)

        for ((shipped, expected) in rated) {
            val where = shipped.iconId
            assertEquals(expected.difficulty, shipped.difficulty, "$where: difficulty")
            assertEquals(expected.level, shipped.level, "$where: level")
            assertEquals(expected.mgpReward, shipped.mgpReward, "$where: MGPReward")
            assertEquals(expected.matchFee, shipped.matchFee, "$where: matchFee")
        }
    }

    /**
     * No opponent sorts on a difficulty the scale does not define.
     *
     * Separate from the assertion above because it is about the **list**, not about any one row:
     * `NpcCatalog.available` sorts on this field and the level gate reads it, so a 0 or a 19 is a
     * row in the wrong place whether or not the model would have produced it.
     */
    @Test
    fun everyDifficultyIsOnTheScale() {
        for (npc in npcs.npcs) {
            assertTrue(
                npc.difficulty in NpcRating.RANGE,
                "${npc.iconId} has difficulty ${npc.difficulty}, outside ${NpcRating.RANGE}",
            )
        }
    }

    /**
     * Losing always pays something, and the fee never exceeds what a win returns.
     *
     * Two properties of the *economy* rather than of the model, asserted against the shipped file
     * so they survive a hand edit. The old data broke both: one opponent paid `l: 0`, and several
     * charged a fee larger than their own draw payout.
     */
    @Test
    fun noOpponentIsABadDeal() {
        for (npc in npcs.npcs) {
            assertTrue(npc.mgpReward.lose > 0, "${npc.iconId} pays nothing for a loss")
            assertTrue(
                npc.matchFee < npc.mgpReward.win,
                "${npc.iconId} charges ${npc.matchFee} and pays ${npc.mgpReward.win} for a win",
            )
        }
    }

    /**
     * The reference profile beats somebody and loses to somebody.
     *
     * The guard against a rating that is technically stable and completely useless. If the AI drew
     * every match, or the reference deck beat all eighty-five, every row would land in one band and
     * the sort would be as meaningless as the column of zeroes this replaced — and every other
     * assertion here would still pass.
     */
    @Test
    fun theRosterUsesMoreThanOneBand() {
        val bands = npcs.npcs.map { it.difficulty }.toSet()

        assertTrue(bands.size >= MIN_BANDS, "the whole roster sits in $bands")
    }

    /** Seeded per opponent, so no rating depends on the order the others were computed in. */
    private fun winRateOf(npc: Npc): Double = NpcRating.referenceWinRate(
        npc = npc,
        reference = reference,
        catalog = cards,
        format = format,
        random = Random(SEED + npc.iconId.hashCode()),
    )

    /**
     * `build/npc-ratings.json`, the generator's output. See this class's KDoc.
     *
     * Keyed by icon **and format**, not by icon alone: `queen-of-cards` is authored twice, once per
     * table, and has been since the AS3. Her two entries have different hands and different rules,
     * so they rate differently and the file has to be able to say so.
     */
    private fun writeRatings(rated: List<Pair<Npc, Npc>>) {
        val rows = rated.joinToString(",\n") { (shipped, expected) ->
            """
            |    {
            |      "iconID": "${shipped.iconId}",
            |      "format": "${shipped.formats.first()}",
            |      "difficulty": ${expected.difficulty},
            |      "level": "${expected.level.storageKey}",
            |      "matchFee": ${expected.matchFee},
            |      "MGPReward": {
            |        "w": ${expected.mgpReward.win},
            |        "d": ${expected.mgpReward.draw},
            |        "l": ${expected.mgpReward.lose}
            |      }
            |    }
            """.trimMargin().trimEnd()
        }
        val file = File("build/npc-ratings.json")
        file.parentFile.mkdirs()
        file.writeText("{\n  \"ratings\": [\n$rows\n  ]\n}\n")
    }

    private companion object {
        /**
         * Fixed, and mixed with the opponent's icon so each rating stands alone.
         *
         * An unseeded rating is a number that changes when nothing changed, and a *shared*
         * generator would make every opponent's rating depend on how many were rated before it —
         * so adding one opponent would renumber the rest.
         */
        const val SEED = 20260812

        /** Three of ten bands used is the least that can be called a scale. */
        const val MIN_BANDS = 3
    }
}
