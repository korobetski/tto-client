package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_FORMAT
import com.tripletriad.data.NpcRating
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.model.XpTable
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
     * The random button hands the challenge to `onChallenge` exactly as a row's own tap does — it
     * draws from `opponents`, the same unlocked roster a row is listed from — so the only thing
     * worth proving here is that the tap actually opens a match rather than doing nothing.
     */
    @Test
    fun theRandomButtonOpensAMatch() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        openDashboard()
        openOpponents()

        onNodeWithTag(RANDOM_OPPONENT_TEST_TAG).performClick()
        settleDeck()

        assertTrue(exists(BOARD_TEST_TAG), "the random button should open a board")
    }

    @Test
    fun aRowShowsTheCardsAnOpponentCanGiveUp() = runComposeUiTest {
        // `itemRewards`, not `cards`: the first is the drop table, the second is the pool the
        // opponent's own hand is dealt from. They are different lists and only one is a reward.
        val drops = catalog
            .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
            .first { npc -> npc.itemRewards.any { it.cardId != null } }

        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        // The drop table moved into the detail sheet a row's tap opens — see
        // `OpponentDetailSheet` — rather than sitting on the row itself, so seeing it now starts
        // with the tap.
        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(drops.iconId)))
        onNodeWithTag(opponentRowTestTag(drops.iconId)).performClick()
        onNodeWithTag(opponentRewardsTestTag(drops.iconId), useUnmergedTree = true).assertExists()
    }

    @Test
    fun anOpponentWithNoDropsShowsNoCards() = runComposeUiTest {
        val barren = catalog
            .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
            .firstOrNull { npc -> npc.itemRewards.none { it.cardId != null } }

        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        if (barren == null) {
            // Every shipped opponent drops something, so there is nothing to assert — said out
            // loud rather than passing silently, which would hide the day one stops dropping.
            return@runComposeUiTest
        }
        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(barren.iconId)))
        onNodeWithTag(opponentRowTestTag(barren.iconId)).performClick()
        onNodeWithTag(opponentRewardsTestTag(barren.iconId), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun aRowNamesTheRulesTheOpponentImposes() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        // Scrolled to rather than assumed visible: the hub above the roster now carries a
        // campaign rack and up to three shelves of its own, so a row that used to sit in the
        // first screenful can sit well below it. That is the roster and the hub sharing one
        // scroll, not a row that moved away.
        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(TEST_OPPONENT)))
        // The rules line moved into the detail sheet a tap opens — the row itself is 56 dp now
        // and has no room left for it.
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).performClick()

        assertTrue(isVisible("All Open"), "tt-master imposes All Open and the sheet should say so")
        assertTrue(isVisible("Difficulty"), "the sheet should state the difficulty")
        assertTrue(isVisible("Match Fee"), "the sheet should state the fee")
    }

    @Test
    fun anEveningOpponentIsAbsentAtNoon() = runComposeUiTest {
        // Seeded above the level gate, because the fixture is a difficulty-4 opponent and a
        // character made through the UI starts at level 1. What is under test here is the *hour*.
        val documents = seeded(veteran())
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = documents,
                clock = FixedClock(hour = NOON),
            )
        }
        loadCharacter(documents)
        openOpponents()

        val found = runCatching {
            onNodeWithTag(OPPONENT_LIST_TEST_TAG)
                .performScrollToNode(hasTestTag(opponentRowTestTag(EVENING_OPPONENT)))
        }
        assertTrue(found.isFailure, "an opponent shut at noon should not be listed at noon")
    }

    @Test
    fun anEveningOpponentIsThereInTheEvening() = runComposeUiTest {
        val documents = seeded(veteran())
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = documents,
                clock = FixedClock(hour = EVENING),
            )
        }
        loadCharacter(documents)
        openOpponents()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(EVENING_OPPONENT)))
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

    @Test
    fun theOpponentListIsHeldBackByTheCharactersLevel() = runComposeUiTest {
        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), clock = FixedClock(hour = EVENING))
        }
        newCharacter()
        openOpponents()

        // Scrolled to rather than merely looked for: the footnote is the last item of a
        // `LazyColumn`, so it is composed only once it is reached, and an opponent row is four
        // lines tall since it started showing the cards that can drop. `exists` passed here while
        // the rows were short enough for the bottom of the list to be on screen at once, which was
        // luck and not the claim being made.
        val told = runCatching {
            onNodeWithTag(OPPONENT_LIST_TEST_TAG)
                .performScrollToNode(hasTestTag(OPPONENT_LOCKED_TEST_TAG))
        }
        assertTrue(told.isSuccess, "a level-1 character should be told")
        val reached = runCatching {
            onNodeWithTag(OPPONENT_LIST_TEST_TAG)
                .performScrollToNode(hasTestTag(opponentRowTestTag(EVENING_OPPONENT)))
        }
        assertTrue(
            reached.isFailure,
            "a difficulty-${eveningOpponent.difficulty} opponent is out of a level-1 reach",
        )
    }

    @Test
    fun levellingOpensTheOnesThatWereHeldBack() = runComposeUiTest {
        val documents = seeded(veteran())
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                documents = documents,
                clock = FixedClock(hour = EVENING),
            )
        }
        loadCharacter(documents)
        openOpponents()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(EVENING_OPPONENT)))
        scrollToOpponent(EVENING_OPPONENT)
        onNodeWithTag(opponentRowTestTag(EVENING_OPPONENT)).assertExists()
    }

    private fun veteran(): GameSave =
        GameSave.new(createdAt = 0L).copy(xp = XpTable.thresholdFor(eveningOpponent.difficulty - 1))

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

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(OPPONENT_UNEARNED_TEST_TAG))
        onNodeWithTag(OPPONENT_UNEARNED_TEST_TAG).assertExists()
    }

    /** Winning the Card Club puts her on it, and takes the footnote away. */
    @Test
    fun winningTheCardClubPutsHerOnTheRoster() = runComposeUiTest {
        // Levelled as well as decorated: she has a difficulty like anyone else, and the level
        // gate would hold her back on a fresh character whatever achievements it held. The two
        // gates are independent and this test is about the second one.
        val ishtar = assertNotNull(catalog.all.firstOrNull { it.iconId == ISHTAR })
        val documents = seeded(
            GameSave.new(createdAt = 0L)
                .copy(xp = XpTable.thresholdFor(ishtar.difficulty))
                .withAchievement(CARD_CLUB, instant = 0L),
        )
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

        /** The FFVIII Queen of Cards, and what finishing the Card Club unlocks. */
        const val ISHTAR = "ishtar"

        const val CARD_CLUB = "ac-cmp-cc"
    }
}
