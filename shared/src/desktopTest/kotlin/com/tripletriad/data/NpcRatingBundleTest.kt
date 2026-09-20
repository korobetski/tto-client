package com.tripletriad.data

import com.tripletriad.model.DIFFICULTY_RANGE
import com.tripletriad.model.Deck
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
 * ### What that number is, since 2026-09-20
 *
 * It is measured, for every opponent, against four collections a player actually held. Until that
 * date 124 of the 158 were not measured at all: they carried the `Level` column of
 * arrtripletriad.com, read on 2026-09-15, and the other 34 were calibrated onto them
 * ([NpcRating.calibratedDifficulty]). Those levels describe the live FFXIV game — another card
 * pool, another opponent AI, another spread of rules — and against play here they rank the roster
 * with a Spearman correlation of **0.44**, non-monotonically: the opponents labelled 2 were on
 * average *easier* than the ones labelled 1, and the ones labelled 8 sat with the 4s. Thirteen of
 * them were labelled 1 or 2 and are in fact top-band, which matters because the opponent list
 * offers exactly those to a character that cannot beat them.
 *
 * So the anchoring is gone, and every difficulty is the band its measured score falls in.
 * [NpcRating.calibratedDifficulty] stays in `:core`, unused by this file.
 *
 * ### The yardstick is four real collections, not one synthetic profile
 *
 * [NpcRating.referenceProfile] — ten cards from the middle of the card table — is a profile nobody
 * ever has, and it ranks the roster differently from collections people do have: Spearman 0.74
 * against the four below, disagreeing by up to eight bands. [PROFILES] are lifted instead from a
 * simulated account at days 0, 10, 30 and 60, one entry per *copy* because `RULE_RANDOM` deals
 * from the collection rather than from the deck. An opponent's score is the mean of the four, so
 * the scale answers "how hard is this over a player's first two months" rather than "how hard is
 * it for one profile".
 *
 * ### The scale is ten shares of the roster, frozen as score thresholds
 *
 * [NpcRating.difficultyFor]'s equal bands over the win-rate axis leave the bottom of the scale
 * empty — no opponent at all rates 1, two rate 2 — and `difficulty` is what
 * [NpcCatalog.available] gates on (`difficulty <= level + 1`), so a level-1 character would have
 * nobody to play. The bands were cut at the roster's own deciles on 2026-09-20 and then **frozen**
 * as [CUT_POINTS]: an opponent added later lands in the band its score falls in and moves nobody.
 *
 * [writeRatings] still emits the whole table, measured score and derived columns included, because
 * that file is how the shipped one is regenerated when a rating moves.
 */
class NpcRatingBundleTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }
    private val formats = runBlocking { loadFormatCatalog() }

    private val format: Format = requireNotNull(formats.default) { "no format is authored" }

    private val references: List<GameSave> = PROFILES.map { (held, deck) ->
        GameSave.new(username = "Reference", createdAt = 0L).copy(
            cards = held,
            decks = listOf(Deck("Reference", deck)),
        )
    }

    private val scores: Map<String, Double> by lazy {
        npcs.npcs.associate { it.iconId to scoreOf(it) }
    }

    @Test
    fun everyOpponentCarriesTheDifficultyItsScoreLandsIn() {
        val rated = npcs.npcs.map { npc ->
            npc to npc.copy(difficulty = bandFor(scores.getValue(npc.iconId)))
        }
        writeRatings(rated)

        val wrong = rated
            .filter { (shipped, expected) -> shipped.difficulty != expected.difficulty }
            .map { (shipped, expected) ->
                "${shipped.iconId}: carries ${shipped.difficulty}, scores " +
                    "%.5f".format(scores.getValue(shipped.iconId)) +
                    " which is ${expected.difficulty}"
            }

        assertEquals(
            emptyList(),
            wrong,
            "build/npc-ratings.json holds what the roster should carry",
        )
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

    /**
     * The scale is a gate as much as a label, so its bottom has to stay populated — see the class
     * KDoc. The deciles put sixteen opponents in each band; this fails long before one empties.
     */
    @Test
    fun everyBandCarriesOpponents() {
        val counted = npcs.npcs.groupingBy { it.difficulty }.eachCount()

        for (band in DIFFICULTY_RANGE) {
            assertTrue(
                (counted[band] ?: 0) >= MIN_PER_BAND,
                "difficulty $band has ${counted[band] ?: 0} opponents, fewer than $MIN_PER_BAND",
            )
        }
    }

    /** The first opponent the game offers is the easiest one it has. */
    @Test
    fun theTripleTriadMasterIsTheBottomOfTheScale() {
        assertEquals(
            NpcRating.RANGE.first,
            npcs.npcs.single { it.iconId == FIRST_OPPONENT }.difficulty,
        )
    }

    /**
     * What the four reference collections score against [npc] on average, draws counted half.
     *
     * One seed for all of them rather than one per opponent: every opponent then meets the same
     * sequence of rule draws and coin flips, so two of them differ by what they are rather than by
     * what they were dealt.
     */
    private fun scoreOf(npc: Npc): Double = references.map { reference ->
        NpcRating.referenceWinRate(
            npc = npc,
            reference = reference,
            catalog = cards,
            format = format,
            random = Random(SEED),
        )
    }.average()

    /** The band [score] falls in, [CUT_POINTS] being read from the easy end down. */
    private fun bandFor(score: Double): Int =
        CUT_POINTS.indexOfFirst { score >= it }.takeIf { it >= 0 }?.plus(1)
            ?: DIFFICULTY_RANGE.last

    private fun writeRatings(rated: List<Pair<Npc, Npc>>) {
        val rows = rated.joinToString(",\n") { (shipped, expected) ->
            """
            |    {
            |      "iconID": "${shipped.iconId}",
            |      "format": "${shipped.formats.first()}",
            |      "score": ${"%.5f".format(scores.getValue(shipped.iconId))},
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
        const val SEED = 20260920

        const val FIRST_OPPONENT = "tt-master"

        /** Sixteen to a band when they were cut; this leaves room for a band to lose a third. */
        const val MIN_PER_BAND = 10

        /**
         * The lowest score each band admits, easiest band first: at or above `[0]` is a 1, below
         * the last entry is a 10. The roster's deciles on 2026-09-20, frozen there.
         */
        val CUT_POINTS: List<Double> = listOf(
            0.63469, 0.54251, 0.47859, 0.42407, 0.35735,
            0.31376, 0.28265, 0.23594, 0.16735,
        )

        /** 9 cards, 9 copies: the starter pack, before a single match. */
        val STARTER: Map<Int, Int> = mapOf(
            257 to 1, 259 to 1, 262 to 1, 263 to 1, 264 to 1, 265 to 1, 281 to 1, 439 to 1,
            509 to 1,
        )

        val STARTER_DECK: List<Int> = listOf(257, 259, 262, 263, 281)

        /** 24 cards, 33 copies: day ten. */
        val EARLY: Map<Int, Int> = mapOf(
            257 to 1, 259 to 1, 261 to 1, 262 to 1, 263 to 1, 264 to 1, 265 to 1, 271 to 5,
            273 to 1, 276 to 1, 281 to 1, 287 to 1, 439 to 1, 509 to 1, 2049 to 3, 2054 to 1,
            2058 to 2, 2061 to 1, 2063 to 1, 2067 to 1, 2074 to 2, 2075 to 2, 2077 to 1,
            2080 to 1,
        )

        val EARLY_DECK: List<Int> = listOf(281, 287, 2077, 2080, 2074)

        /** 33 cards, 88 copies: day thirty. */
        val MID: Map<Int, Int> = mapOf(
            257 to 1, 259 to 1, 261 to 3, 262 to 1, 263 to 1, 264 to 1, 265 to 1, 271 to 5,
            273 to 4, 276 to 1, 281 to 1, 287 to 1, 439 to 1, 509 to 1, 2049 to 6, 2051 to 6,
            2052 to 4, 2054 to 4, 2058 to 4, 2061 to 1, 2063 to 2, 2067 to 2, 2071 to 7,
            2074 to 10, 2075 to 7, 2077 to 1, 2080 to 1, 2082 to 2, 2084 to 2, 2085 to 2,
            2088 to 2, 2099 to 1, 2134 to 1,
        )

        val MID_DECK: List<Int> = listOf(2134, 2099, 2084, 2088, 281)

        /** 56 cards, 290 copies: day sixty. */
        val LATE: Map<Int, Int> = mapOf(
            257 to 2, 259 to 1, 260 to 1, 261 to 7, 262 to 1, 263 to 1, 264 to 2, 265 to 1,
            268 to 2, 270 to 3, 271 to 7, 272 to 3, 273 to 7, 274 to 1, 275 to 2, 276 to 1,
            277 to 1, 281 to 2, 283 to 3, 285 to 2, 287 to 1, 293 to 1, 297 to 1, 299 to 2,
            300 to 1, 302 to 1, 306 to 1, 312 to 1, 439 to 1, 509 to 1, 615 to 1, 2049 to 16,
            2051 to 15, 2052 to 27, 2054 to 9, 2058 to 7, 2059 to 1, 2061 to 1, 2063 to 7,
            2064 to 1, 2067 to 4, 2070 to 1, 2071 to 28, 2074 to 28, 2075 to 35, 2077 to 1,
            2080 to 1, 2081 to 1, 2082 to 4, 2084 to 3, 2085 to 13, 2088 to 6, 2099 to 14,
            2100 to 3, 2126 to 1, 2134 to 1,
        )

        val LATE_DECK: List<Int> = listOf(312, 2126, 300, 297, 299)

        /** The four collections, each with the deck the account fielded at the time. */
        val PROFILES: List<Pair<Map<Int, Int>, List<Int>>> = listOf(
            STARTER to STARTER_DECK,
            EARLY to EARLY_DECK,
            MID to MID_DECK,
            LATE to LATE_DECK,
        )
    }
}
