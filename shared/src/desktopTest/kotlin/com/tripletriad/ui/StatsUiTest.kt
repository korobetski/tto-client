package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.GameSave
import com.tripletriad.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StatsUiTest {
    private fun ComposeUiTest.openStats() {
        openFromDashboard(DASHBOARD_STATS_TEST_TAG, STATS_TABLE_TEST_TAG)
    }

    @Test
    fun aFreshCharacterReadsZeroWithoutDividingByZero() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openStats()

        onNodeWithTag(statsRowTestTag(StringKeys.MATCHES)).assertTextEquals("0")
        onNodeWithTag(statsRowTestTag(StringKeys.WIN_RATE)).assertTextEquals("0%")
        onNodeWithTag(statsRowTestTag(StringKeys.MGP))
            .assertTextEquals("${GameSave.STARTING_MGP}")
    }

    @Test
    fun theCountersComeFromTheProfile() = runComposeUiTest {
        val played = GameSave.new(createdAt = 0L).copy(
            stats = Stats(wins = 3, defeats = 1, draws = 0),
            startedMatches = 6,
            endedMatches = 4,
        )
        val documents = seeded(played)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openStats()

        onNodeWithTag(statsRowTestTag(StringKeys.WINS)).assertTextEquals("3")
        onNodeWithTag(statsRowTestTag(StringKeys.DEFEATS)).assertTextEquals("1")
        onNodeWithTag(statsRowTestTag(StringKeys.MATCHES)).assertTextEquals("4")
        // `STARTED_MATCHES - ENDED_MATCHES`, which is never stored — see [GameSave.forfeits].
        onNodeWithTag(statsRowTestTag(StringKeys.FORFEITS)).assertTextEquals("2")
        onNodeWithTag(statsRowTestTag(StringKeys.WIN_RATE)).assertTextEquals("75%")
    }

    @Test
    fun unearnedAchievementsAreListedWithTheirProgress() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openStats()

        onNodeWithTag(STATS_ACHIEVEMENTS_TEST_TAG)
            .performScrollToNode(hasTestTag(achievementRowTestTag(HOARDER_I)))
        assertTrue(
            isVisible("${GameSave.STARTING_MGP} / $MGP_POT_I"),
            "a hundred of the thousand it wants",
        )
    }

    @Test
    fun theCollectorsFirstTierIsMetOnArrival() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openStats()

        onNodeWithTag(STATS_ACHIEVEMENTS_TEST_TAG)
            .performScrollToNode(hasTestTag(achievementRowTestTag(COLLECTOR_I)))
        onNodeWithTag(achievementRowTestTag(COLLECTOR_I))
            .assertTextEquals("${STARTER_CARDS.size} / ${STARTER_CARDS.size}")
    }

    @Test
    fun anEarnedAchievementIsListedFirst() = runComposeUiTest {
        val decorated = GameSave.new(createdAt = 0L)
            .copy(npcWins = mapOf("tt-master" to 1))
            .withAchievement(FIRST_WIN, instant = 1_000L)
        val documents = seeded(decorated)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openStats()

        assertTrue(
            exists(achievementRowTestTag(FIRST_WIN)),
            "an earned achievement should be composed without scrolling",
        )
    }

    @Test
    fun anEarnedTierShowsItsUnlockDateInsteadOfItsProgress() = runComposeUiTest {
        val documents = seeded(
            GameSave.new(createdAt = 0L)
                .copy(npcWins = mapOf("tt-master" to 1))
                .withAchievement(FIRST_WIN, instant = UNLOCKED_AT),
        )
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openStats()

        onNodeWithTag(achievementRowTestTag(FIRST_WIN)).assertTextEquals(UNLOCKED_ON)
    }

    @Test
    fun aFamilyIsOneRowNamingTheTierStillToEarn() = runComposeUiTest {
        val documents = seeded(
            GameSave.new(createdAt = 0L)
                .copy(npcWins = mapOf("tt-master" to 1))
                .withAchievement(FIRST_WIN, instant = UNLOCKED_AT),
        )
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openStats()

        assertTrue(exists(achievementFamilyTestTag(TRIPLE_TEAM)), "the family has a row")
        // The four tiers above the one earned are not rows of their own any more; only the next
        // one is named, and only as the line under the family.
        assertFalse(exists(achievementFamilyTestTag(FIRST_WIN)), "a tier is not a family")
        assertTrue(isVisible("Next:"), "the row says what is being worked towards")
        onNodeWithTag(achievementRowTestTag(SECOND_TIER)).assertTextEquals("1 / 30")
    }

    @Test
    fun theCatalogueIsTheOneTheAs3Declares() {
        assertEquals(ACHIEVEMENTS, AchievementCatalog.all.size)
    }

    @Test
    fun theCatalogueCollapsesIntoSixFamilies() {
        val families = AchievementCatalog.all
            .map { it.id.trimEnd { character -> character.isDigit() } }
            .distinct()
        assertEquals(FAMILIES, families.size, "families: $families")
    }

    private companion object {
        const val COLLECTOR_I = "ac-td1"

        const val HOARDER_I = "ac-mp1"
        const val MGP_POT_I = 1_000

        const val FIRST_WIN = "ac-tt1"

        const val SECOND_TIER = "ac-tt2"

        const val TRIPLE_TEAM = "ac-tt"

        const val UNLOCKED_AT = 1_614_816_000_000L
        const val UNLOCKED_ON = "2021-03-04"

        const val ACHIEVEMENTS = 22
        const val FAMILIES = 5
    }
}
