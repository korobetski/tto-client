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
 * would be asserting that a function returns what it returns.
 *
 * ### Two sources for that number
 *
 * Where the game gives a level, the file carries it: the `Level` column of
 * arrtripletriad.com/en/npcs, read on 2026-09-15, for 124 FFXIV opponents — the Triple Triad Master
 * a 1, as the game's first opponent should be. Nothing here can check those against the game, and
 * nothing tries. They are what the rest are measured against: an [isMeasured] opponent carries the
 * difficulty its win rate against the yardstick calibrates to among theirs
 * (`NpcRating.calibratedDifficulty`). So a card or an opponent added can move a measured rating,
 * and cannot move one the game gives.
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

    private val winRates: Map<String, Double> by lazy {
        npcs.npcs.associate { it.iconId to winRateOf(it) }
    }

    private val anchors: List<NpcRating.Anchor> by lazy {
        npcs.npcs.filterNot { it.isMeasured }
            .map { NpcRating.Anchor(winRates.getValue(it.iconId), it.difficulty) }
    }

    @Test
    fun everyMeasuredOpponentCarriesTheDifficultyItsWinRateCalibratesTo() {
        val rated = npcs.npcs.map { npc ->
            val calibrated = NpcRating.calibratedDifficulty(winRates.getValue(npc.iconId), anchors)
            npc to if (npc.isMeasured) npc.copy(difficulty = calibrated) else npc
        }
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

    /** The anchors, pinned: an opponent leaving the game's list is a decision, not a drift. */
    @Test
    fun theGameGivesTheLevelOfEveryFf14OpponentButNine() {
        val measured = npcs.npcs.filter { it.isMeasured }

        assertEquals(ANCHORS, npcs.npcs.size - measured.size)
        assertEquals(
            UNLEVELLED,
            measured.filterNot { FF8_FORMAT in it.formats }.map { it.iconId }.toSet(),
        )
        assertEquals(
            NpcRating.RANGE.first,
            npcs.npcs.single { it.iconId == FIRST_OPPONENT }.difficulty,
        )
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

    private val Npc.isMeasured: Boolean get() = FF8_FORMAT in formats || iconId in UNLEVELLED

    private companion object {
        const val SEED = 20260812

        const val FF8_FORMAT = "ff8-standard"

        const val FIRST_OPPONENT = "tt-master"

        /** 133 FFXIV opponents less these nine. */
        const val ANCHORS = 124

        /**
         * The FFXIV opponents arrtripletriad.com gave no level on 2026-09-15. The first five are
         * listed at 0 — patches 7.1-7.3, not yet reported; the last four are not listed at all.
         */
        val UNLEVELLED = setOf(
            "hume-black-mage", "pudeel-ja", "malevolent-weasel", "miitso", "pawkukwe",
            "momo", "tataru", "papalymo", "queen-of-cards",
        )

        const val MIN_BANDS = 3
    }
}
