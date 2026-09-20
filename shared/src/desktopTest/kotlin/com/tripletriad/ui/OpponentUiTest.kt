package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_FORMAT
import com.tripletriad.FF8_BLOCK
import com.tripletriad.data.NpcRating
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OpponentUiTest {
    private val stub = PveStubServer()

    private val catalog = runBlocking { loadNpcCatalog() }

    @Test
    fun opponentsAreListedEasiestFirst() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        scrollToOpponent(TEST_OPPONENT)
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).assertExists()

        val listed = catalog.available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
        assertEquals(
            listed.map { it.difficulty }.sorted(),
            listed.map { it.difficulty },
            "the list must never put a harder opponent above an easier one",
        )
        assertEquals(
            NpcRating.RANGE.first,
            listed.first().difficulty,
            "the head of the list should be as easy as the scale goes",
        )
    }

    /**
     * The quick match hands the challenge to `onChallenge` exactly as a row's own tap does — which
     * opponent is `QuickMatchTest`'s question — so the only thing worth proving here is that the
     * tap actually opens a match rather than doing nothing.
     */
    @Test
    fun theQuickMatchOpensAMatch() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        openDashboard()
        openOpponents()

        onNodeWithTag(QUICK_MATCH_TEST_TAG).performClick()
        settleDeck()

        assertTrue(exists(BOARD_TEST_TAG), "the quick match should open a board")
    }

    /** The die, inside a place, draws from that place. */
    @Test
    fun theDieInAPlaceOpensAMatch() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        openDashboard()
        openOpponents()
        assertFalse(exists(RANDOM_OPPONENT_TEST_TAG), "the home has the quick match instead")

        openPlace(STARTER_PLACE)
        onNodeWithTag(RANDOM_OPPONENT_TEST_TAG).performClick()
        settleDeck()

        assertTrue(exists(BOARD_TEST_TAG), "the die should open a board")
    }

    /** The home opens on a suggestion: today's tour sits above the places. */
    @Test
    fun theHomeOffersTheDaysTour() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        assertTrue(exists(TOUR_TEST_TAG), "a new character has somebody to be pointed at")
        assertTrue(
            STARTER_PLACE_OPPONENTS.any { exists(tourPickTestTag(it)) },
            "the tour should pick from the places a new character can reach",
        )
    }

    @Test
    fun aRowShowsTheCardsAnOpponentCanGiveUp() = runComposeUiTest {
        // `itemRewards`, not `cards`: the first is the drop table, the second is the pool the
        // opponent's own hand is dealt from. They are different lists and only one is a reward.
        val drops = catalog
            .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
            .filter { it.iconId in STARTER_PLACE_OPPONENTS }
            .first { npc -> npc.itemRewards.any { it.cardId != null } }

        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        // The drop table moved into the detail sheet a row's tap opens — see
        // `OpponentDetailSheet` — rather than sitting on the row itself, so seeing it now starts
        // with the tap.
        scrollToOpponent(drops.iconId)
        onNodeWithTag(opponentRowTestTag(drops.iconId)).performClick()
        onNodeWithTag(opponentRewardsTestTag(drops.iconId), useUnmergedTree = true).assertExists()
    }

    @Test
    fun anOpponentWithNoDropsShowsNoCards() = runComposeUiTest {
        val barren = catalog
            .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
            .firstOrNull { npc -> npc.itemRewards.none { it.cardId != null } }

        // Every place open: the barren one is not in a starting place.
        val documents = seeded(explorerSave())
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openOpponents()

        if (barren == null) {
            // Every shipped opponent drops something, so there is nothing to assert — said out
            // loud rather than passing silently, which would hide the day one stops dropping.
            return@runComposeUiTest
        }
        scrollToOpponent(barren.iconId)
        onNodeWithTag(opponentRowTestTag(barren.iconId)).performClick()
        onNodeWithTag(opponentRewardsTestTag(barren.iconId), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun aRowNamesTheRulesTheOpponentImposes() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        // Scrolled to rather than assumed visible: the tab header and the filter row sit above
        // the grid, and a tile that used to be in the first screenful can sit below them.
        scrollToOpponent(TEST_OPPONENT)
        // What the *sheet* says, which is more than the tile can hold: the tile names the rules
        // and the fee (`OpponentFilterUiTest`), the sheet also names the difficulty and labels
        // the fee as one.
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).performClick()

        assertTrue(isVisible("All Open"), "tt-master imposes All Open and the sheet should say so")
        assertTrue(isVisible("Difficulty"), "the sheet should state the difficulty")
        assertTrue(isVisible("Match Fee"), "the sheet should state the fee")
    }

    @Test
    fun anEveningOpponentIsAbsentAtNoon() = runComposeUiTest {
        // Seeded with every place open, because the fixture stands in Dravania and a character
        // made through the UI starts at the Gold Saucer. What is under test here is the *hour*.
        val documents = seeded(explorerSave())
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = documents,
                clock = FixedClock(hour = NOON),
            )
        }
        loadCharacter(documents)
        openOpponents()

        val found = runCatching { scrollToOpponent(EVENING_OPPONENT) }
        assertTrue(found.isFailure, "an opponent shut at noon should not be listed at noon")
    }

    /**
     * In their own place, though, they are shown shut rather than left out — with the hours they
     * keep, which is how a player learns when to come back.
     */
    @Test
    fun anEveningOpponentSaysWhenToComeBack() = runComposeUiTest {
        val documents = seeded(explorerSave())
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = documents,
                clock = FixedClock(hour = NOON),
            )
        }
        loadCharacter(documents)
        openOpponents()
        openPlace(EVENING_PLACE)

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(EVENING_OPPONENT)))
        val hours = eveningOpponent.availability
        val said = "${hours.begins}:00–${hours.ends}:00"
        onNodeWithTag(opponentNoteTestTag(EVENING_OPPONENT), useUnmergedTree = true)
            .assertTextContains(said, substring = true)

        onNodeWithTag(opponentRowTestTag(EVENING_OPPONENT)).performClick()
        assertTrue(exists(OPPONENT_BLOCKED_TEST_TAG), "the sheet should say why not now")
        assertFalse(exists(OPPONENT_CHALLENGE_TEST_TAG), "and offer no challenge")
    }

    /** The sheet counts a rivalry down, and says the hours of an opponent who keeps them. */
    @Test
    fun theSheetSaysHowCloseARivalryIs() = runComposeUiTest {
        val documents = seeded(explorerSave())
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = documents,
                clock = FixedClock(hour = EVENING),
            )
        }
        loadCharacter(documents)
        openOpponents()

        scrollToOpponent(EVENING_OPPONENT)
        onNodeWithTag(opponentRowTestTag(EVENING_OPPONENT)).performClick()

        // One win in `explorerSave`, so two more to the next stage.
        onNodeWithTag(OPPONENT_RIVAL_TEST_TAG).assertTextContains("2 more", substring = true)
        assertTrue(exists(OPPONENT_HOURS_TEST_TAG), "a timed opponent's sheet names the hours")
        assertTrue(exists(OPPONENT_CHALLENGE_TEST_TAG), "open in the evening, so challengeable")
    }

    @Test
    fun anEveningOpponentIsThereInTheEvening() = runComposeUiTest {
        val documents = seeded(explorerSave())
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = documents,
                clock = FixedClock(hour = EVENING),
            )
        }
        loadCharacter(documents)
        openOpponents()

        scrollToOpponent(EVENING_OPPONENT)
        onNodeWithTag(opponentRowTestTag(EVENING_OPPONENT)).assertExists()
    }

    @Test
    fun theEveningListIsLongerThanTheNoonOne() {
        val atNoon = catalog.available(FF14_FORMAT, NOON, ANY_LEVEL).map { it.iconId }
        val atEvening = catalog.available(FF14_FORMAT, EVENING, ANY_LEVEL).map { it.iconId }

        assertTrue(EVENING_OPPONENT !in atNoon, "the fixture should be shut at noon")
        assertTrue(EVENING_OPPONENT in atEvening, "the fixture should be open in the evening")
        assertTrue(
            atEvening.size > atNoon.size,
            "more opponents in the evening: ${atNoon.size} vs ${atEvening.size}",
        )
    }

    // ---- What a finished match writes ------------------------------------

    @Test
    fun aFinishedMatchIsWrittenToTheProfile() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        val before = stub.player.save.mgp

        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stub.player.save.endedMatches == 1 }

        // Read off the **server's** profile, which is the only one there is. Nothing local adds a
        // match up any more: the referee settles, credits once, and sends back the profile it
        // wrote — see `PveOutcome.player`.
        val save = stub.player.save
        assertEquals(1, save.endedMatches, "the match should be recorded as ended")
        assertEquals(1, save.stats.played, "exactly one result should be recorded")
        assertTrue(save.mgp > before, "every result pays, so MGP should have risen from $before")
    }

    /**
     * Leaving a board pays nothing, and **that is all it does now.**
     *
     * It used to count: the profile was written when the board opened, so started-minus-ended made
     * a forfeit. The refereed path writes the profile once, at settlement — `PveReferee.settle` is
     * the only caller of `GameSave.startingMatch` — so a match nobody finished touches no counter
     * at all. `PveStore.abandonLive` marks the row and stops there.
     *
     * The half that matters is unchanged and is what this asserts: an abandoned match is not a
     * result and is not paid. Whether walking out should still cost a forfeit is a question for the
     * server, not for this screen.
     */
    @Test
    fun abandoningAMatchIsNotAResultAndPaysNothing() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        val before = stub.player.save

        leaveMatch()
        awaitOpponents()

        val save = stub.player.save
        assertEquals(0, save.endedMatches, "nothing was finished")
        assertEquals(0, save.stats.played, "and so nothing was recorded")
        assertEquals(before.mgp, save.mgp, "walking out of a board must not pay")
    }

    @Test
    fun aSecondMatchAddsToTheProfile() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stub.player.save.endedMatches == 1 }
        val afterFirst = stub.player.save

        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        // Playing again puts the deck question back — free play deals afresh, deck included.
        settleDeck()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !isFinished() }
        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stub.player.save.endedMatches == 2 }

        val afterSecond = stub.player.save
        assertEquals(2, afterSecond.stats.played)
        assertTrue(
            afterSecond.mgp > afterFirst.mgp,
            "the second match should have paid too: ${afterFirst.mgp} -> ${afterSecond.mgp}",
        )
    }

    /**
     * **Playing again asks which deck again.**
     *
     * A second match is a second deal, and the deck is part of a deal — a player who has just
     * watched a deck lose under Reverse is exactly the player who wants to bring another. The
     * board's rematch control used to call `PveSession.open` itself with whatever deck the session
     * still held, and the selector could not come back because `MatchDestination` remembers its
     * answer for as long as the opponent does not change. See `rematchExit` in `App.kt`.
     *
     * The tournament is deliberately the opposite — see `CampaignUiTest`.
     */
    @Test
    fun playingAgainAsksWhichDeckToBring() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(NEW_MATCH_TEST_TAG) }

        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_SELECT_CHOOSE_TEST_TAG) }
        assertFalse(exists(BOARD_TEST_TAG), "a board was dealt before the deck was chosen")
    }

    /** A place with a picture shows it on its row and again above its members. */
    @Test
    fun aPlaceWithAPictureShowsItInTheListAndInside() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter(FF8_BLOCK)
        openOpponents()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(zoneRowTestTag(PICTURED_PLACE)))
        // Unmerged: the picture is inside the row's click target, which merges what it holds.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(zoneArtTestTag(PICTURED_PLACE)) }

        openPlace(PICTURED_PLACE)
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(zoneArtTestTag(PICTURED_PLACE)) }
        assertFalse(exists(zoneRowTestTag(PICTURED_PLACE)), "still on the list")
    }

    /**
     * A new character is held to the starting places, and told there is more.
     *
     * The evening, so the fixture is not simply shut: what keeps it off is its place.
     */
    @Test
    fun aNewCharacterIsHeldToTheStartingPlaces() = runComposeUiTest {
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), clock = FixedClock(hour = EVENING))
        }
        newCharacter()
        openOpponents()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(zoneRowTestTag(FIRST_CITY)))
        onNodeWithTag(zoneStatusTestTag(FIRST_CITY), useUnmergedTree = true)
            .assertTextContains("Clear first", substring = true)

        // Scrolled to rather than merely looked for: the footnote is the last item of a lazy
        // grid, so it is composed only once it is reached.
        openEveryone()
        val told = runCatching {
            onNodeWithTag(OPPONENT_LIST_TEST_TAG)
                .performScrollToNode(hasTestTag(OPPONENT_LOCKED_TEST_TAG))
        }
        assertTrue(told.isSuccess, "a new character should be told there are more elsewhere")
        val reached = runCatching { scrollToOpponent(EVENING_OPPONENT) }
        assertTrue(reached.isFailure, "$EVENING_OPPONENT stands in a place not open yet")
    }

    /** Beating the Gold Saucer's regulars opens the three cities, and nothing further. */
    @Test
    fun clearingTheGoldSaucerOpensTheCities() = runComposeUiTest {
        val saucer = assertNotNull(shippedZones[STARTER_PLACE])
        val documents = seeded(
            GameSave.new(createdAt = 0L).copy(npcWins = saucer.npcs.associateWith { 1 }),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openOpponents()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(zoneRowTestTag(FIRST_CITY)))
        onNodeWithTag(zoneStatusTestTag(FIRST_CITY), useUnmergedTree = true)
            .assertTextContains("Open", substring = true)
        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(zoneRowTestTag(EVENING_PLACE)))
        onNodeWithTag(zoneStatusTestTag(EVENING_PLACE), useUnmergedTree = true)
            .assertTextContains("Clear first", substring = true)
    }

    /**
     * A place's faces say which of them are done with.
     *
     * The count on the place's row ("2 / 6 beaten") says how many, never which, and the roster's
     * "never played" chip lives on the full grid rather than in a place. So inside a place the
     * only way to tell an opponent already beaten from one still to meet was to open each sheet
     * in turn and read the rivalry line.
     *
     * Unmerged, because the mark sits inside the tile's own click target, which merges what it
     * holds — the same reason the zone art is looked for that way.
     */
    @Test
    fun aPlaceMarksTheOpponentsAlreadyBeaten() = runComposeUiTest {
        val saucer = assertNotNull(shippedZones[STARTER_PLACE])
        val documents = seeded(
            GameSave.new(createdAt = 0L).copy(npcWins = mapOf(TEST_OPPONENT to 1)),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openOpponents()
        openPlace(STARTER_PLACE)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            existsUnmerged(opponentBeatenTestTag(TEST_OPPONENT))
        }
        // The other five are on screen too — six tiles, no scrolling — so their want of a mark is
        // a real absence rather than a lazy grid's silence.
        for (other in saucer.npcs.filter { it != TEST_OPPONENT }) {
            onNodeWithTag(opponentRowTestTag(other)).assertExists()
            assertFalse(
                existsUnmerged(opponentBeatenTestTag(other)),
                "$other has never been beaten and should carry no mark",
            )
        }
    }

    /** A place leads with its tournament, shut until the place is cleared and named as such. */
    @Test
    fun aPlaceShowsItsTournamentShutUntilItIsCleared() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()
        openPlace(STARTER_PLACE)

        onNodeWithTag(campaignRowTestTag(STARTER_LADDER))
            .assertTextContains("Clear first: The Gold Saucer", substring = true)
    }

    /** Once the place is cleared its tournament opens, and the row leads to it. */
    @Test
    fun aClearedPlacesTournamentOpensFromThePlace() = runComposeUiTest {
        // A starter's decks, or the ladder would be shut for having none to bring.
        val documents = seeded(
            freshSave().withAchievement(AchievementCatalog.placeCleared(STARTER_PLACE), 0L),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openOpponents()
        openPlace(STARTER_PLACE)

        onNodeWithTag(campaignRowTestTag(STARTER_LADDER)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CAMPAIGN_START_TEST_TAG) }
        assertFalse(exists(CAMPAIGN_LOCKED_TEST_TAG), "a cleared place's ladder should be open")
    }

    private val eveningOpponent: Npc
        get() = requireNotNull(catalog.byIcon(EVENING_OPPONENT, FF14_FORMAT)) {
            "$EVENING_OPPONENT is not in the roster"
        }

    /**
     * The one opponent behind an achievement is off the roster until it is held.
     *
     * Absence is asserted through the **footnote** rather than through the row, because the list is
     * lazy: a row that is merely scrolled past does not exist either, and `exists` cannot tell that
     * apart from a row that was filtered out. The count under the list can.
     */
    @Test
    fun anUnearnedOpponentIsOffTheRosterAndSaidToBe() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()
        openEveryone()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(OPPONENT_UNEARNED_TEST_TAG))
        onNodeWithTag(OPPONENT_UNEARNED_TEST_TAG).assertExists()
    }

    /** Winning the Card Club puts her on it, and takes the footnote away. */
    @Test
    fun winningTheCardClubPutsHerOnTheRoster() = runComposeUiTest {
        // Every place open as well as decorated: she stands in the last FFVIII place, which a
        // fresh character has not reached whatever achievements it held. The place and the door
        // are independent and this test is about the door.
        assertNotNull(catalog.all.firstOrNull { it.iconId == ISHTAR })
        val documents = seeded(explorerSave().withAchievement(CARD_CLUB, instant = 0L))
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openOpponents()

        scrollToOpponent(ISHTAR)
        onNodeWithTag(opponentRowTestTag(ISHTAR)).assertExists()
        assertFalse(
            exists(OPPONENT_UNEARNED_TEST_TAG),
            "nothing is left to earn, so the footnote should be gone",
        )
    }

    private companion object {
        const val NOON = 12
        const val EVENING = 18

        const val EVENING_OPPONENT = "linu-vali"

        /** Where [EVENING_OPPONENT] stands: several places past the start. */
        const val EVENING_PLACE = "dravania"

        const val STARTER_PLACE = "gold-saucer"
        const val STARTER_LADDER = "gs"
        const val PICTURED_PLACE = "balamb"

        const val FIRST_CITY = "uldah"

        /** The FFVIII Queen of Cards, and what finishing the Card Club unlocks. */
        const val ISHTAR = "ishtar"

        const val CARD_CLUB = "ac-cmp-cc"
    }
}
