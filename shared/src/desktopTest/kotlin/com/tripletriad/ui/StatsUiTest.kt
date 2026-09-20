package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.height
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.GameSave
import com.tripletriad.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A family's card is a button now — it opens the family's ladder — so it merges what it holds: the
 * date and the progress it carries are found in the unmerged tree, and scrolled to by the card's
 * own tag.
 */
@OptIn(ExperimentalTestApi::class)
class StatsUiTest {
    private fun ComposeUiTest.openStats() {
        openProfile()
    }

    private fun ComposeUiTest.height(family: String) =
        onNodeWithTag(achievementFamilyTestTag(family)).getUnclippedBoundsInRoot().height

    @Test
    fun aFreshCharacterReadsZeroWithoutDividingByZero() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openStats()

        onNodeWithTag(statsRowTestTag(StringKeys.MATCHES)).assertTextEquals("0")
        onNodeWithTag(statsRowTestTag(StringKeys.WIN_RATE)).assertTextEquals("0%")
        onNodeWithTag(statsRowTestTag(StringKeys.WINS)).assertTextEquals("0")
        onNodeWithTag(statsRowTestTag(StringKeys.DRAWS)).assertTextEquals("0")
    }

    @Test
    fun theCountersComeFromTheProfile() = runComposeUiTest {
        val played = GameSave.new(createdAt = 0L).copy(
            stats = Stats(wins = 3, defeats = 1, draws = 0),
            startedMatches = 6,
            endedMatches = 4,
        )
        val documents = seeded(played)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openAchievements()

        onNodeWithTag(STATS_ACHIEVEMENTS_TEST_TAG)
            .performScrollToNode(hasTestTag(achievementFamilyTestTag(MGP_POT)))
        assertTrue(
            isVisible("${GameSave.STARTING_MGP} / $MGP_POT_I"),
            "a hundred of the thousand it wants",
        )
    }

    /**
     * The collector's first tier is **one card short** on arrival, and used to be met by it.
     *
     * A starter box is nine cards and `ac-td1` wants ten. It was ten until the box's four
     * unauthored cards replaced its five authored spares — see `StarterCatalog` — so this row went
     * from "collected" to one card away. The threshold is the AS3's own (`STR_Triple_decker_I`) and
     * is not ours to move; the tier is a first goal rather than a welcome gift, which is the better
     * of the two anyway.
     */
    @Test
    fun theHiddenAchievementsAreCountedButNotNamed() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openAchievements()

        onNodeWithTag(ACHIEVEMENT_HIDDEN_TEST_TAG).assertTextContains("1", substring = true)
        assertFalse(isVisible("Zantetsuken"), "a hidden achievement is named before it is earned")
    }

    @Test
    fun theCollectorsFirstTierIsOneCardShortOnArrival() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openAchievements()

        onNodeWithTag(STATS_ACHIEVEMENTS_TEST_TAG)
            .performScrollToNode(hasTestTag(achievementFamilyTestTag(COLLECTOR)))
        onNodeWithTag(achievementRowTestTag(COLLECTOR_I), useUnmergedTree = true)
            .assertTextEquals("${STARTER_CARDS.size} / $COLLECTOR_I_TARGET")
    }

    @Test
    fun anEarnedAchievementIsListedFirst() = runComposeUiTest {
        val decorated = GameSave.new(createdAt = 0L)
            .copy(npcWins = mapOf("tt-master" to 1))
            .withAchievement(FIRST_WIN, instant = 1_000L)
        val documents = seeded(decorated)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openAchievements()

        assertTrue(
            existsUnmerged(achievementRowTestTag(FIRST_WIN)),
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openAchievements()

        onNodeWithTag(achievementRowTestTag(FIRST_WIN), useUnmergedTree = true)
            .assertTextEquals(UNLOCKED_ON)
    }

    @Test
    fun aFamilyIsOneRowNamingTheTierStillToEarn() = runComposeUiTest {
        val documents = seeded(
            GameSave.new(createdAt = 0L)
                .copy(npcWins = mapOf("tt-master" to 1))
                .withAchievement(FIRST_WIN, instant = UNLOCKED_AT),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openAchievements()

        assertTrue(exists(achievementFamilyTestTag(TRIPLE_TEAM)), "the family has a row")
        // The four tiers above the one earned are not rows of their own any more; only the next
        // one is named, and only as the line under the family.
        assertFalse(exists(achievementFamilyTestTag(FIRST_WIN)), "a tier is not a family")
        assertTrue(isVisible("Next:"), "the row says what is being worked towards")
        onNodeWithTag(achievementRowTestTag(SECOND_TIER), useUnmergedTree = true)
            .assertTextEquals("1 / 30")
    }

    /**
     * **Every medallion is the same height, whatever it has to say.**
     *
     * A family carries a date only once it is started, a reward line only where the next rung pays
     * one, and a bar only while a rung is left — so left to wrap, the cards came out at half a
     * dozen heights and the grid read as a wall of misaligned boxes. Asserted across three
     * families chosen for saying *different* amounts: one earned and dated, one that names a card
     * reward, one that names neither.
     */
    @Test
    fun everyAchievementCardIsTheSameHeight() = runComposeUiTest {
        val documents = seeded(
            GameSave.new(createdAt = 0L)
                .copy(npcWins = mapOf("tt-master" to 1))
                .withAchievement(FIRST_WIN, instant = UNLOCKED_AT),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openAchievements()

        val dated = height(TRIPLE_TEAM)
        assertEquals(dated, height(BEAST_TRIBE), "a card with a reward line is drawn taller")
        assertEquals(dated, height(MGP_POT), "a card with neither is drawn shorter")
    }

    @Test
    fun theCatalogueIsTheOneTheAs3Declares() {
        assertEquals(ACHIEVEMENTS, AchievementCatalog.all.size)
    }

    @Test
    fun theCatalogueCollapsesIntoItsFamilies() {
        val families = AchievementCatalog.all
            .map { it.id.trimEnd { character -> character.isDigit() } }
            .distinct()
        assertEquals(FAMILIES, families.size, "families: $families")
    }

    private companion object {
        const val COLLECTOR = "ac-td"
        const val COLLECTOR_I = "ac-td1"

        /** What `ac-td1` asks for — `Achievement.collector("ac-td1", …, 10, …)`. */
        const val COLLECTOR_I_TARGET = 10

        const val MGP_POT_I = 1_000

        const val FIRST_WIN = "ac-tt1"

        const val SECOND_TIER = "ac-tt2"

        const val TRIPLE_TEAM = "ac-tt"

        /** A family whose next rung pays a card, so its medallion carries a reward line. */
        const val BEAST_TRIBE = "ac-fob"

        /** And one that pays nothing and has not been started. */
        const val MGP_POT = "ac-mp"

        const val UNLOCKED_AT = 1_614_816_000_000L
        const val UNLOCKED_ON = "2021-03-04"

        // The 22 ported from `Achievements.as`, plus one per tournament — the originals recorded
        // no ladder result at all, so the three campaign achievements could only be authored here
        // — plus the collections: the three rungs added above `ac-fob`, three four-rung tribe
        // ladders beside it, FFVIII's single-rung companion badge, and the hidden Zantetsuken.
        // 25 + 3 + 12 + 1 + 1. Then the map: one clearing per place (26) and one win per
        // tournament new with it (23) — see `PlaceAchievements` in `tto-core`. The six places the
        // FFVIII map gained bring one of each.
        const val ACHIEVEMENTS = 91

        // `ac-tt`, `ac-wof`, `ac-td`, `ac-mp`, the four tribe ladders (`ac-fob`, `ac-fop`,
        // `ac-fog`, `ac-foh`) and `ac-foc` — and the three tournaments, which are families of one
        // apiece: a ladder is won or it is not, so there is no tier to climb. The tribe ladders
        // count as one family each because grouping trims the trailing digits, which is also why
        // `ac-fob` could keep its digitless id and still sit with `ac-fob2`. And `ac-zantetsuken`,
        // a family of one like a tournament. The map's 49 are single-tier families too.
        const val FAMILIES = 62
    }
}
