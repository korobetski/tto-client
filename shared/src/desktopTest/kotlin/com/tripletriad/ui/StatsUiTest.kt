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
import com.tripletriad.time.isoDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The character's record and its achievements — the original's `profileScreen`.
 *
 * Two of the assertions here are about things the original could not show at all: a win *rate*, and
 * an achievement the profile has not earned yet. See [StatsScreen] for why both are here.
 */
@OptIn(ExperimentalTestApi::class)
class StatsUiTest {
    private fun ComposeUiTest.openStats() {
        openFromDashboard(DASHBOARD_STATS_TEST_TAG, STATS_TABLE_TEST_TAG)
    }

    /** A fresh profile reads zero everywhere, and 0% rather than a division by zero. */
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

    /** The counters are the profile's, and forfeits are derived from the two match totals. */
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

    /**
     * Every achievement is listed, earned or not, with how far along the profile is.
     *
     * `profileScreen.as:210-220` walks `PROFILE_DATAS.ACHIEVEMENTS`, so an unearned one was
     * invisible and the screen could not say what there was to aim at.
     *
     * The MGP tier is the example rather than the collector's: a fresh profile holds
     * [GameSave.STARTING_MGP] against the thousand `ac-mp1` wants, which is the "unearned and
     * measurably far off" pair a Boolean condition could not express. It used to be `ac-td1`, and
     * that stopped being unearned — see [theCollectorsFirstTierIsMetOnArrival].
     */
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

    /**
     * **A new character already satisfies `ac-td1`.** Pinned, because nobody decided it.
     *
     * The tier wants ten distinct cards and document 19's starter pack is exactly ten, so the
     * collector's first step is met on the first frame — it is credited by the next match's
     * `AchievementCatalog.newlyEarned`, which is why the row still reads as unearned here rather
     * than carrying a date.
     *
     * Document 19 fixes the composition at ten and the thresholds come from `Achievements.as`;
     * neither says which gives. Three ways out — raise `ac-td1`, accept it as a welcome award, or
     * drop it from the family — and all three are game design rather than a defect. This test is
     * here so whichever is chosen is chosen, instead of drifting.
     */
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

    /** An earned achievement heads the list, which is the original's `unlockDate` ordering. */
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

    /**
     * An unlocked tier shows **when**, not `1 / 1`.
     *
     * The date is what the save records and the screen never showed; the counter on an earned
     * achievement was a bar pinned at full above a number that could not move. See [isoDate] for
     * why a date can be rendered from `commonMain` at all.
     */
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

    /**
     * Five tiers are one row, and the row names the next one.
     *
     * The screen used to list all twenty-two, so `Triple Team` alone spent five rows restating a
     * requirement four of them had already met. What is left is where the family stands — the tier
     * reached, and the one being worked towards.
     */
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

    /** The catalogue really is the 22 of `Achievements.as`, so the list cannot silently shrink. */
    @Test
    fun theCatalogueIsTheOneTheAs3Declares() {
        assertEquals(ACHIEVEMENTS, AchievementCatalog.all.size)
    }

    /**
     * Twenty-two tiers are five families.
     *
     * Three ladders of five — Triple Team, Triple-decker, MGP Pot — one of six, Wheel of Fortune,
     * and `ac-fob` alone: 5 + 5 + 5 + 6 + 1.
     */
    @Test
    fun theCatalogueCollapsesIntoSixFamilies() {
        val families = AchievementCatalog.all
            .map { it.id.trimEnd { character -> character.isDigit() } }
            .distinct()
        assertEquals(FAMILIES, families.size, "families: $families")
    }

    private companion object {
        /** `ac-td1` — the Triple-decker tier's first step, at ten cards. */
        const val COLLECTOR_I = "ac-td1"

        /** `ac-mp1` — hold a thousand MGP, which a fresh character is a long way from. */
        const val HOARDER_I = "ac-mp1"
        const val MGP_POT_I = 1_000

        /** `ac-tt1` — defeat one NPC. */
        const val FIRST_WIN = "ac-tt1"

        /** `ac-tt2` — defeat thirty, which is what follows [FIRST_WIN]. */
        const val SECOND_TIER = "ac-tt2"

        /** The family the Triple Team tiers collapse into. */
        const val TRIPLE_TEAM = "ac-tt"

        /** 2021-03-04T00:00:00Z, chosen so both the month and the day need padding. */
        const val UNLOCKED_AT = 1_614_816_000_000L
        const val UNLOCKED_ON = "2021-03-04"

        const val ACHIEVEMENTS = 22
        const val FAMILIES = 5
    }
}
