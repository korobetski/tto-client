package com.tripletriad.ui

import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import kotlin.test.Test
import kotlin.test.assertEquals

class AchievementFamiliesTest {
    private fun standings(save: GameSave, key: String): Set<Standing> {
        val family = rankedFamilies(save).single { it.key == key }
        return Standing.entries.filter { it.admits(family) }.toSet()
    }

    @Test
    fun everyFamilyFallsInTheCategoryItsRequirementNames() {
        val byCategory = rankedFamilies(GameSave.new(createdAt = 0L))
            .groupBy({ it.category }, { it.key })
            .mapValues { (_, keys) -> keys.toSet() }

        assertEquals(
            mapOf(
                AchievementCategory.COLLECTION to
                    setOf("ac-td", "ac-fob", "ac-fop", "ac-fog", "ac-foh", "ac-foc"),
                AchievementCategory.MATCHES to setOf("ac-tt", "ac-wof"),
                AchievementCategory.PLACES to PLACES.map { "ac-zone-$it" }.toSet(),
                AchievementCategory.CAMPAIGNS to
                    setOf("ac-cmp-balamb", "ac-cmp-cc", "ac-cmp-gs") +
                    NEW_LADDERS.map { "ac-cmp-$it" },
                AchievementCategory.MGP to setOf("ac-mp"),
            ),
            byCategory,
        )
    }

    @Test
    fun almostIsAPartOfInProgressAndTheOtherTwoStandAlone() {
        val save = GameSave.new(createdAt = 0L)
            .copy(mgp = NEARLY_THE_FIRST_POT, npcWins = mapOf(TEST_OPPONENT to 1))
            .withAchievement(TRIPLE_TEAM_I, instant = 1L)
            .withAchievement(GOLD_SAUCER, instant = 2L)

        assertEquals(setOf(Standing.IN_PROGRESS, Standing.ALMOST), standings(save, MGP_POT))
        assertEquals(setOf(Standing.IN_PROGRESS), standings(save, TRIPLE_TEAM))
        assertEquals(setOf(Standing.COMPLETED), standings(save, GOLD_SAUCER))
        assertEquals(setOf(Standing.NOT_STARTED), standings(save, BALAMB))
    }

    @Test
    fun almostBeginsAtFourFifthsOfTheNextRungAndNotAfter() {
        val threshold = (FIRST_POT * ALMOST_FRACTION).toInt()
        val at = GameSave.new(createdAt = 0L).copy(mgp = threshold)
        val short = GameSave.new(createdAt = 0L).copy(mgp = threshold - 1)

        assertEquals(setOf(Standing.IN_PROGRESS, Standing.ALMOST), standings(at, MGP_POT))
        assertEquals(setOf(Standing.IN_PROGRESS), standings(short, MGP_POT))
    }

    @Test
    fun theTotalsCountTiersTheirMgpAndTheCardsAmongThem() {
        val paysACard = AchievementCatalog.all.first { it.reward is CardItem }
        val paysMgp = AchievementCatalog.all.first { it.mgpReward > 0 && it.reward !is CardItem }
        val save = GameSave.new(createdAt = 0L)
            .withAchievement(paysACard.id, instant = 1L)
            .withAchievement(paysMgp.id, instant = 2L)

        assertEquals(
            AchievementTotals(
                tiersEarned = 2,
                tiersTotal = AchievementCatalog.all.size,
                mgpEarned = paysACard.mgpReward + paysMgp.mgpReward,
                cardsEarned = 1,
                cardsTotal = AchievementCatalog.all.count { it.reward is CardItem },
                hiddenLeft = AchievementCatalog.all.count { it.hidden },
            ),
            achievementTotals(save),
        )
    }

    private companion object {
        const val TEST_OPPONENT = "tt-master"
        const val TRIPLE_TEAM = "ac-tt"
        const val TRIPLE_TEAM_I = "ac-tt1"
        const val MGP_POT = "ac-mp"
        const val GOLD_SAUCER = "ac-cmp-gs"
        const val BALAMB = "ac-cmp-balamb"

        /** The places whose ladder is new with the map; the other three predate it. */
        val NEW_LADDERS = listOf(
            "uldah", "limsa", "gridania", "mor-dhona", "battlehall", "ishgard", "dravania",
            "gyr-abania", "kugane", "othard", "norvrandt", "sharlayan", "tural", "balamb-garden",
            "dollet", "timber", "galbadia", "winhill", "fishermans-horizon", "shumi-village",
            "trabia", "centra", "esthar",
        )

        val PLACES = NEW_LADDERS + listOf("gold-saucer", "balamb", "card-club")

        /** What `ac-mp1` asks to be held. */
        const val FIRST_POT = 1_000

        /** Nine tenths of it: past [ALMOST_FRACTION], short of the rung. */
        const val NEARLY_THE_FIRST_POT = 900
    }
}
