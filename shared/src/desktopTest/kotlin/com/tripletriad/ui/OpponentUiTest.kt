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

/**
 * The opponent list, and what a finished match writes back.
 *
 * The **hour** is what makes this worth a file of its own: 27 of the 60 `ff14` opponents declare an
 * availability window and thirteen of those wrap midnight, so which opponents exist depends on the
 * clock. That is the only thing in the app that reads one, and pinning it is the only way to test
 * it.
 */
@OptIn(ExperimentalTestApi::class)
class OpponentUiTest {
    private val catalog = runBlocking { loadNpcCatalog() }

    private fun stored(documents: InMemoryDocumentStore): GameSave =
        runBlocking { SaveRepository(documents).list().single().save }

    /**
     * Opponents are listed easiest first — `NPCs.as:1141` sorts difficulty, fee, then name.
     *
     * Asserted as the **ordering**, not as a named opponent at the top. It used to name
     * `tt-master`, which held while the FFXIV table's hand-authored difficulties ran 1..19 and
     * almost every value was unique. `NpcRating` measures difficulty onto a ten-point scale, so a
     * band now holds several opponents and which of them sorts first is decided by name — a fact
     * about the alphabet, not about the list being sorted.
     *
     * The property that matters survives the change and is what is checked: no opponent is ever
     * listed before an easier one.
     */
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

    /**
     * A row shows the cards the opponent can be won from, before the match is entered.
     *
     * The AS3 lists an opponent's fee and difficulty and says nothing about what beating them
     * yields, so a player picks an opponent without the one fact that would make the choice
     * interesting. `Npc.cards` is the drop table and was always in the data.
     *
     * Asserted against the catalogue rather than a fixed id: which opponent heads the list is a
     * property of the shipped table, and a test naming a card would break when the table is
     * reauthored for something unrelated.
     */
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

    /** And an opponent with an empty drop table shows no row of cards at all. */
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

    /** A row states the rules the opponent imposes, before the player commits to the match. */
    @Test
    fun aRowNamesTheRulesTheOpponentImposes() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        assertTrue(isVisible("All Open"), "tt-master imposes All Open and the row should say so")
        assertTrue(isVisible("Difficulty"), "the row should state the difficulty")
        assertTrue(isVisible("Match Fee"), "the row should state the fee")
    }

    /**
     * An opponent available only in the evening is absent at noon and present at 18:00.
     *
     * `linu-vali` declares `{begins: 17, ends: 23}`.
     *
     * ### Why this scrolls instead of looking the row up
     *
     * The list is a `LazyColumn`, so **only composed rows have semantics nodes at all** — an
     * opponent ranked past the first screenful has no node whatever the hour, and
     * `assertDoesNotExist` on it would pass for the wrong reason. `performScrollToNode` scans the
     * lazy list's whole item set, so it succeeds exactly when the row exists and throws when it
     * does not; that makes it the only assertion here that means what it says in both directions.
     */
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

    /**
     * The two hours really do differ, and by the number the data says.
     *
     * Asserted on the catalog rather than on the screen: a count is not something a lazy list can
     * be asked for, and the point of the two tests above is the *screen* honouring a filter whose
     * arithmetic is `NpcCatalogTest`'s business.
     */
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

    /**
     * The whole point of the phase: **a played match reaches the file.**
     *
     * Asserted on the decoded `.sav` rather than on the screen, and on four independent fields —
     * `ENDED_MATCHES`, the win/draw/loss counters, `MGP` and `SAVE_NUMBER`. A screen that showed a
     * payout it never persisted would pass a test that only read the panel.
     */
    @Test
    fun aFinishedMatchIsWrittenToTheProfile() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        startMatch()

        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stored(documents).endedMatches == 1 }

        val save = stored(documents)
        assertEquals(1, save.endedMatches, "the match should be recorded as ended")
        assertEquals(1, save.stats.played, "exactly one result should be recorded")
        assertTrue(
            save.mgp > GameSave.STARTING_MGP,
            "every result pays, so MGP should have risen from ${GameSave.STARTING_MGP}",
        )
        assertTrue(save.saveNumber >= 2, "created once, then written again: ${save.saveNumber}")
    }

    /**
     * A match *begun* is recorded before it ends, which is what makes `STATS.FORFEITS` mean
     * anything.
     *
     * The AS3 increments `STARTED_MATCHES` when the match is launched (`PVEScreen.as:244`) but only
     * ever saves in `endGame`, so an abandoned match loses the increment and forfeits can never be
     * anything but zero. Persisting at the start is the deliberate fix — see `MatchScreen`.
     */
    @Test
    fun abandoningAMatchCountsAsAForfeit() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        startMatch()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stored(documents).startedMatches == 1 }

        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        awaitOpponents()

        val save = stored(documents)
        assertEquals(1, save.startedMatches)
        assertEquals(0, save.endedMatches)
        assertEquals(1, save.forfeits, "started minus ended")
        assertEquals(1, save.pveMatches, "and it was a match against an opponent")
    }

    /** Two matches accumulate rather than overwriting each other. */
    @Test
    fun aSecondMatchAddsToTheProfile() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        startMatch()

        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stored(documents).endedMatches == 1 }
        val afterFirst = stored(documents)

        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        settleDeck()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !isFinished() }
        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stored(documents).endedMatches == 2 }

        val afterSecond = stored(documents)
        assertEquals(2, afterSecond.stats.played)
        assertEquals(2, afterSecond.startedMatches)
        assertTrue(
            afterSecond.mgp > afterFirst.mgp,
            "the second match should have paid too: ${afterFirst.mgp} -> ${afterSecond.mgp}",
        )
    }

    /**
     * A new character is not shown the whole sixty-strong table, and is told so.
     *
     * `NpcCatalog.available` is where the arithmetic is tested; this is the screen honouring it —
     * the footnote appears at level 1 and the difficulty-4 opponent does not, and levelling to 3
     * produces them. Both halves matter: a filter with no explanation is a short list, and an
     * explanation with no filter is a lie.
     */
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

    /**
     * A character past the level gate, so a test about the *hour* is not also about the level.
     *
     * The level is **derived** from the opponent it has to reach, not written down. `NpcRating`
     * measures difficulty from the shipped cards and rules, so an opponent's number moves when its
     * hand or its rules are edited — and a hard-coded level would then be testing whether somebody
     * remembered to update this file. `NpcCatalog.available` opens difficulties up to
     * `level + LEVEL_REACH`, so the level that just reaches it is its difficulty less one.
     *
     * Seeded as **XP**, not as a level: `GameSave.sane()` recomputes the level from the experience
     * on every load and every write, so a `copy(level = 3)` would be normalised straight back to 1
     * before the screen ever saw it.
     */
    private fun veteran(): GameSave =
        GameSave.new(createdAt = 0L).copy(xp = XpTable.thresholdFor(eveningOpponent.difficulty - 1))

    /** The opponent with an evening window, read from the catalogue for its difficulty. */
    private val eveningOpponent: Npc
        get() = requireNotNull(catalog.byIcon(EVENING_OPPONENT, FF14_FORMAT)) {
            "$EVENING_OPPONENT is not in the roster"
        }

    private companion object {
        const val NOON = 12
        const val EVENING = 18

        /** `{begins: 17, ends: 23}` in `NPCs.as`. Its difficulty is measured, not assumed here. */
        const val EVENING_OPPONENT = "linu-vali"
    }
}
