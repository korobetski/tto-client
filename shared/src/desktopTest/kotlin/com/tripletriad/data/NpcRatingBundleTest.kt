package com.tripletriad.data

import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcRatingBundleTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }
    private val formats = runBlocking { loadFormatCatalog() }

    private val format: Format = requireNotNull(formats.default) { "no format is authored" }

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
    fun noOpponentIsABadDeal() {
        for (npc in npcs.npcs) {
            assertTrue(npc.mgpReward.lose > 0, "${npc.iconId} pays nothing for a loss")
            assertTrue(
                npc.matchFee < npc.mgpReward.win,
                "${npc.iconId} charges ${npc.matchFee} and pays ${npc.mgpReward.win} for a win",
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
