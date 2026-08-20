package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_FORMAT
import com.tripletriad.data.NpcRating
import com.tripletriad.data.SaveRepository
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.model.XpTable
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OpponentUiTest {
    private val stub = PveStubServer()

    private val catalog = runBlocking { loadNpcCatalog() }

    private fun stored(documents: InMemoryDocumentStore): GameSave =
        runBlocking { SaveRepository(documents).list().single().save }

    @Test
    fun opponentsAreListedEasiestFirst() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
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

    @Test
    fun aRowShowsTheCardsAnOpponentCanGiveUp() = runComposeUiTest {
        // `itemRewards`, not `cards`: the first is the drop table, the second is the pool the
        // opponent's own hand is dealt from. They are different lists and only one is a reward.
        val drops = catalog
            .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
            .first { npc -> npc.itemRewards.any { it.cardId != null } }

        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(drops.iconId)))
        // Unmerged: the row is a clickable card, so it merges its descendants' semantics and the
        // inner tag is invisible to the default finder. The same reason the quest badge is read
        // this way in `QuestsUiTest`.
        onNodeWithTag(opponentRewardsTestTag(drops.iconId), useUnmergedTree = true).assertExists()
    }

    @Test
    fun anOpponentWithNoDropsShowsNoCards() = runComposeUiTest {
        val barren = catalog
            .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
            .firstOrNull { npc -> npc.itemRewards.none { it.cardId != null } }

        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        if (barren == null) {
            // Every shipped opponent drops something, so there is nothing to assert — said out
            // loud rather than passing silently, which would hide the day one stops dropping.
            return@runComposeUiTest
        }
        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(barren.iconId)))
        onNodeWithTag(opponentRewardsTestTag(barren.iconId), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun aRowNamesTheRulesTheOpponentImposes() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        assertTrue(isVisible("All Open"), "tt-master imposes All Open and the row should say so")
        assertTrue(isVisible("Difficulty"), "the row should state the difficulty")
        assertTrue(isVisible("Match Fee"), "the row should state the fee")
    }

    @Test
    fun anEveningOpponentIsAbsentAtNoon() = runComposeUiTest {
        // Seeded above the level gate, because the fixture is a difficulty-4 opponent and a
        // character made through the UI starts at level 1. What is under test here is the *hour*.
        val documents = seeded(veteran())
        setContent {
            App(
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
            App(
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
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
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
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()
        val before = stub.player.save

        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        awaitOpponents()

        val save = stub.player.save
        assertEquals(0, save.endedMatches, "nothing was finished")
        assertEquals(0, save.stats.played, "and so nothing was recorded")
        assertEquals(before.mgp, save.mgp, "walking out of a board must not pay")
    }

    @Test
    fun aSecondMatchAddsToTheProfile() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        startMatch()

        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stub.player.save.endedMatches == 1 }
        val afterFirst = stub.player.save

        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
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

    @Test
    fun theOpponentListIsHeldBackByTheCharactersLevel() = runComposeUiTest {
        setContent {
            App(store = settingsFor(AppLocale.EN_US), clock = FixedClock(hour = EVENING))
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
            App(
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

    private companion object {
        const val NOON = 12
        const val EVENING = 18

        const val EVENING_OPPONENT = "linu-vali"
    }
}
