package com.tripletriad.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SemanticsTest {
    private val stub = PveStubServer()

    @Test
    fun aCardCellIsAButtonThatReportsWhetherItIsShowing() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }

        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        val cell = onNodeWithTag(cardCellTestTag(STARTER_CARDS.first()))
        cell.assert(hasRole(Role.Button))
        cell.assertIsNotSelected()

        // Tapping opens the detail pane beside the grid, and the cell is what says it is open.
        cell.performClick()
        waitForIdle()
        cell.assertIsSelected()
    }

    @Test
    fun aDeckSlotIsAButtonWithNoSelectionToReport() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }

        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)
        onNodeWithTag(screenTabTestTag("decks")).performClick()
        waitForIdle()

        val slot = onNodeWithTag(deckSlotTestTag(0))
        slot.assert(hasRole(Role.Button))
        slot.assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected),
            { "a deck slot is not one of a set of choices" },
        )
    }

    @Test
    fun theLanguageChipsReportWhichIsChosen() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }

        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(optionsLanguageTestTag(AppLocale.EN_US)).assertIsSelected()
        onNodeWithTag(optionsLanguageTestTag(AppLocale.DE_DE)).assertIsNotSelected()
    }

    @Test
    fun theSettingsGroupsAreHeadings() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }

        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        val headings = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .fetchSemanticsNodes()
        check(headings.size >= EXPECTED_SETTINGS_HEADINGS) {
            "expected at least $EXPECTED_SETTINGS_HEADINGS headings, found ${headings.size}"
        }
    }

    @Test
    fun aBoardCellIsAButtonEvenThoughItSkipsTheSharedModifier() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }

        startMatch()

        onNodeWithTag(tileTestTag(0)).assert(hasRole(Role.Button))
    }

    private fun hasRole(role: Role) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private companion object {
        const val EXPECTED_SETTINGS_HEADINGS = 2
    }
}
