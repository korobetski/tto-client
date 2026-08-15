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

/**
 * That the things a player taps announce **what they are** and **what state they are in**.
 *
 * ### The gap this was written against
 *
 * Before `Modifier.ttoClickable` existed there were twenty-two bare `Modifier.clickable` call sites
 * in `ui/` and not one `Role`, `selected`, `toggleable` or `stateDescription` in the whole package.
 * A screen reader met every list row in this app as an unlabelled node: it could not say a row was
 * a button, and on the screens where one row is the current choice — a deck, a card, a locale, a
 * server — it could not say which. A card game that cannot be played without sight is not a rough
 * edge, and neither is one where the *cards* are labelled but nothing says they can be pressed.
 *
 * ### Why this is a spot check and not a sweep
 *
 * Because the property worth guarding is the **shared modifier**, not each of its call sites. Every
 * row in the app that is built the ordinary way now goes through one composable, so a handful of
 * rows drawn from different screens is enough to say whether that composable still does its job —
 * and a per-screen assertion would mostly be asserting that somebody remembered to call it, which
 * is the thing having one modifier removed the need for.
 *
 * The four exceptions that keep a plain `clickable` are deliberate and documented where they are:
 * the board and the two hands (a 48 dp growth would make nine tiled cells overlap each other's hit
 * areas) and the pack reveal's full-screen tap (a focus ring the size of the window). Each states
 * its role by hand instead, which is what the last test here checks.
 */
@OptIn(ExperimentalTestApi::class)
class SemanticsTest {

    /** A card in the collection: pressable, and a toggle rather than a permanent choice. */
    @Test
    fun aCardCellIsAButtonThatReportsWhetherItIsShowing() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

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

    /**
     * A deck slot: pressable, and **not** selectable.
     *
     * The distinction is the reason `ttoClickable` takes a nullable `selected`. A deck slot opens
     * an editor; it is never "the current one". Announcing it as unselected would invite the
     * question of what selecting it would mean, and there is no answer.
     */
    @Test
    fun aDeckSlotIsAButtonWithNoSelectionToReport() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

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

    /** A locale chip: one of a set, and the set says which. */
    @Test
    fun theLanguageChipsReportWhichIsChosen() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(optionsLanguageTestTag(AppLocale.EN_US)).assertIsSelected()
        onNodeWithTag(optionsLanguageTestTag(AppLocale.DE_DE)).assertIsNotSelected()
    }

    /**
     * Something on the settings screen is a heading.
     *
     * Counted rather than named, because what is being guarded is that `SectionHeader` still
     * carries `semantics { heading() }` — the thing that lets a screen reader offer "jump to next
     * heading" instead of reading forty rows. Asserting on the *wording* would be asserting on the
     * locale bundle, which is data.
     */
    @Test
    fun theSettingsGroupsAreHeadings() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        val headings = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .fetchSemanticsNodes()
        check(headings.size >= EXPECTED_SETTINGS_HEADINGS) {
            "expected at least $EXPECTED_SETTINGS_HEADINGS headings, found ${headings.size}"
        }
    }

    /**
     * A board cell says it is pressable, without going through the shared modifier.
     *
     * The match layer opts out of `ttoClickable` for a reason of geometry — see `MatchBoard` — and
     * states its role by hand instead. Hand-written is exactly the kind that gets dropped, so it is
     * the one exception worth asserting directly.
     */
    @Test
    fun aBoardCellIsAButtonEvenThoughItSkipsTheSharedModifier() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

        startMatch()

        onNodeWithTag(tileTestTag(0)).assert(hasRole(Role.Button))
    }

    private fun hasRole(role: Role) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private companion object {
        /** General settings, audio settings — the two an unsigned-in player sees. */
        const val EXPECTED_SETTINGS_HEADINGS = 2
    }
}
