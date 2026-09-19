package com.tripletriad.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.GameSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AchievementsUiTest {
    /** A window the rail and the pane fit in — see `AchievementPanesMinWidth`. */
    private fun wideWindow(block: suspend SkikoComposeUiTest.() -> Unit) = runSkikoComposeUiTest(
        size = Size(WIDE_WINDOW_WIDTH, WIDE_WINDOW_HEIGHT),
        density = Density(1f),
        block = block,
    )

    private fun ComposeUiTest.openWith(save: GameSave) {
        val documents = seeded(save)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openAchievements()
    }

    /** Scrolled to first: the chips scroll sideways, and one past the edge takes no tap. */
    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    /** A menu's line, which lives in a popup with nothing to scroll. */
    private fun ComposeUiTest.pick(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    /**
     * The families the grid has composed. A lazy grid composes only what is on screen, so this
     * is asserted only where the filters leave few enough to fit — which is what makes a wrong
     * answer show: an unfiltered grid fills the window with families that are not expected.
     */
    private fun ComposeUiTest.familiesShown(): Set<String> =
        onAllNodes(
            SemanticsMatcher("a family's card") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(FAMILY) == true
            },
        ).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag].removePrefix(FAMILY) }
            .toSet()

    @Test
    fun theAlmostChipKeepsOnlyTheFamiliesNearTheirNextRung() = runComposeUiTest {
        // `ac-tt` is started too, at one win of the thirty its next rung wants: in progress, and
        // far from almost.
        openWith(earnedFirstWin().copy(mgp = NEARLY_THE_FIRST_POT))

        tap(achievementStandingTestTag(Standing.ALMOST.tag))

        assertEquals(setOf(MGP_POT), familiesShown())
        onNodeWithTag(achievementStandingTestTag(Standing.ALMOST.tag))
            .assertTextEquals("Almost there · 1")
        assertTrue(existsUnmerged(achievementBadgeTestTag(MGP_POT)), "the card says so too")
    }

    @Test
    fun aCategoryFromTheMenuNarrowsTheGridAndTheChipsCountInsideIt() = runComposeUiTest {
        openWith(GameSave.new(createdAt = 0L))

        pick(ACHIEVEMENT_CATEGORY_MENU_TEST_TAG)
        // MGP rather than Campaigns: one family, where the tournaments are twenty now and a
        // lazy grid would not compose them all to be counted.
        pick(achievementCategoryTestTag(AchievementCategory.MGP.tag))

        assertEquals(setOf(MGP_POT), familiesShown())
        onNodeWithTag(achievementStandingTestTag("all")).assertTextEquals("All · 1")
        // Nought: a new character's purse has already started the one family here, where the
        // whole catalogue has dozens not started — so the chip counts inside the category.
        onNodeWithTag(achievementStandingTestTag(Standing.NOT_STARTED.tag))
            .assertTextEquals("Not started · 0")
        onNodeWithTag(ACHIEVEMENT_CATEGORY_MENU_TEST_TAG).assertTextEquals("MGP ▾")
    }

    @Test
    fun aStandingNothingIsInSaysSoRatherThanDrawingAnEmptyGrid() = runComposeUiTest {
        openWith(GameSave.new(createdAt = 0L))

        tap(achievementStandingTestTag(Standing.COMPLETED.tag))

        assertTrue(exists(ACHIEVEMENT_NO_MATCH_TEST_TAG), "an empty grid is explained")
        assertEquals(emptySet(), familiesShown())
    }

    @Test
    fun theTotalsCountTheWholeCatalogueWhateverTheFiltersSay() = runComposeUiTest {
        openWith(earnedFirstWin())
        val expected = "1 / ${AchievementCatalog.all.size}"

        onNodeWithTag(ACHIEVEMENT_TOTAL_TIERS_TEST_TAG).assertTextEquals(expected)
        pick(ACHIEVEMENT_CATEGORY_MENU_TEST_TAG)
        pick(achievementCategoryTestTag(AchievementCategory.MGP.tag))

        onNodeWithTag(ACHIEVEMENT_TOTAL_TIERS_TEST_TAG).assertTextEquals(expected)
    }

    @Test
    fun onANarrowWindowAFamilyOpensItsWholeLadderInASheet() = runComposeUiTest {
        openWith(earnedFirstWin())
        assertFalse(exists(ACHIEVEMENT_DETAIL_TEST_TAG), "no ladder until a family is picked")

        tap(achievementFamilyTestTag(TRIPLE_TEAM))

        assertTrue(exists(ACHIEVEMENT_SHEET_TEST_TAG), "the ladder arrives over the grid")
        onNodeWithTag(achievementTierTestTag("ac-tt1")).assertTextContains("Earned $UNLOCKED_ON")
        onNodeWithTag(achievementTierTestTag("ac-tt2")).assertTextContains("1 / 30")
        assertTrue(exists(achievementTierTestTag("ac-tt5")), "the top rung is listed as well")
    }

    @Test
    fun aWideWindowKeepsTheCategoriesAndTheLadderOpenBesideTheGrid() = wideWindow {
        openWith(earnedFirstWin())

        assertFalse(exists(ACHIEVEMENT_CATEGORY_MENU_TEST_TAG), "the rail replaces the menu")
        assertTrue(exists(achievementCategoryTestTag(AchievementCategory.CAMPAIGNS.tag)))
        // Nothing picked, and the pane still shows a family: the first the grid lists.
        assertTrue(exists(achievementTierTestTag("ac-tt1")), "the earned family leads")

        tap(achievementFamilyTestTag(MGP_POT))

        assertTrue(exists(achievementTierTestTag("ac-mp1")), "the pane follows the pick")
        assertFalse(exists(achievementTierTestTag("ac-tt1")), "one ladder at a time")
        assertFalse(exists(ACHIEVEMENT_SHEET_TEST_TAG), "and never in a sheet")
    }

    @Test
    fun theLadderNeverShowsAFamilyTheFiltersHaveTakenOffTheGrid() = wideWindow {
        openWith(earnedFirstWin())
        tap(achievementFamilyTestTag(TRIPLE_TEAM))

        tap(achievementCategoryTestTag(AchievementCategory.MGP.tag))

        assertEquals(setOf(MGP_POT), familiesShown())
        assertFalse(exists(achievementTierTestTag("ac-tt1")), "the pick is off the grid")
        assertTrue(exists(achievementTierTestTag("ac-mp1")), "so the pane takes the first left")
    }

    private fun earnedFirstWin(): GameSave = GameSave.new(createdAt = 0L)
        .copy(npcWins = mapOf(TEST_OPPONENT to 1))
        .withAchievement("ac-tt1", instant = UNLOCKED_AT)

    private companion object {
        val FAMILY = achievementFamilyTestTag("")

        const val TRIPLE_TEAM = "ac-tt"
        const val MGP_POT = "ac-mp"

        /** Nine tenths of the thousand `ac-mp1` asks to be held. */
        const val NEARLY_THE_FIRST_POT = 900

        const val UNLOCKED_AT = 1_614_816_000_000L
        const val UNLOCKED_ON = "2021-03-04"

        const val WIDE_WINDOW_WIDTH = 1600f
        const val WIDE_WINDOW_HEIGHT = 1000f
    }
}
