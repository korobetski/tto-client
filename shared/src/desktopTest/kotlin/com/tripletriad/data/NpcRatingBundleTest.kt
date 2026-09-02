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
 * The shipped roster against [NpcRating] — a test about **content**, which is why it is here.
 *
 * ### One number, checked; three, derived
 *
 * `npcs.json` used to carry `difficulty`, `level`, `matchFee` and `MGPReward`, and this file
 * checked all four. It carries only the difficulty now: the other three are computed from it in
 * `:core` (`model/NpcBalance.kt`) and pinned there against literal curves, so asserting them here
 * would be asserting that a function returns what it returns. What is left is the assertion that
 * cannot be made anywhere else — that the number in the file is the number four hundred simulated
 * matches produce.
 *
 * [writeRatings] still emits the whole table, derived columns included, because that file is how
 * the shipped one is regenerated when the rating moves and reading it is how one sees what moved.
 */
class NpcRatingBundleTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }
    private val formats = runBlocking { loadFormatCatalog() }

    private val format: Format = requireNotNull(formats.default) { "no format is authored" }

    private val reference: GameSave = NpcRating.referenceProfile(cards, format)

    @Test
    fun everyOpponentCarriesTheDifficultyTheModelMeasures() {
        val rated = npcs.npcs.map { npc -> npc to NpcRating.rated(npc, winRateOf(npc)) }
        writeRatings(rated)

        for ((shipped, expected) in rated) {
            assertEquals(expected.difficulty, shipped.difficulty, "${shipped.iconId}: difficulty")
        }
    }

    @Test
    fun everyDifficultyIsOnTheScale() {
        for (npc in npcs.npcs) {
            assertTrue(
                npc.difficulty in NpcRating.RANGE,
                "${npc.iconId} has difficulty ${npc.difficulty}, outside ${NpcRating.RANGE}",
            )
        }
    }

    @Test
    fun theRosterUsesMoreThanOneBand() {
        val bands = npcs.npcs.map { it.difficulty }.toSet()

        assertTrue(bands.size >= MIN_BANDS, "the whole roster sits in $bands")
    }

    private fun winRateOf(npc: Npc): Double = NpcRating.referenceWinRate(
        npc = npc,
        reference = reference,
        catalog = cards,
        format = format,
        random = Random(SEED + npc.iconId.hashCode()),
    )

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
        const val SEED = 20260812

        const val MIN_BANDS = 3
    }
}
