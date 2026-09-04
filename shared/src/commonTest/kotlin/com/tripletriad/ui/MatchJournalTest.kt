package com.tripletriad.ui

import com.tripletriad.data.MatchHistoryRepository
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchResult
import com.tripletriad.model.OpponentKind
import com.tripletriad.protocol.PveMatchView
import com.tripletriad.protocol.PveOutcome
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpOutcome
import com.tripletriad.protocol.RewardSummary
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning a board the server settled into a row, and keeping the rows.
 *
 * The composable that calls these is not tested here — `HistoryUiTest` drives the whole path from a
 * played match to a row on screen. What is here is the two conversions, which is where the facts
 * are decided: which side the player was, what the purse actually did, and what a match that is
 * still being played answers.
 */
class MatchJournalTest {
    @Test
    fun aBoardStillBeingPlayedIsNotARow() {
        assertNull(pve(outcome = null).asRecord(AT), "an unsettled PvE board produced a row")
        assertNull(pvp(outcome = null).asRecord(AT), "an unsettled PvP board produced a row")
    }

    @Test
    fun aSoloRowNamesTheOpponentByIconAndCarriesWhatItPaid() {
        val record = pve().asRecord(AT)

        assertEquals(MATCH_ID, record?.id)
        assertEquals(OpponentKind.NPC, record?.opponentKind)
        // The icon id, never the translated name — the screen resolves it back through the
        // catalogue, which is what lets a row written in English read in French.
        assertEquals(OPPONENT, record?.opponentName)
        assertEquals(MatchResult.WIN, record?.result)
        assertEquals(6, record?.ownScore)
        assertEquals(4, record?.opponentScore)
        assertEquals(120, record?.mgpDelta)
        assertEquals(40L, record?.xpGained)
    }

    @Test
    fun aSoloBoardTheServerPaidNothingForStillRecordsTheResult() {
        // A draw pays no reward summary at all on some rungs. The row is still the match.
        val record = pve(outcome = PveOutcome(MatchResult.DRAW, blue = 5, red = 5)).asRecord(AT)

        assertEquals(MatchResult.DRAW, record?.result)
        assertEquals(0, record?.mgpDelta)
        assertEquals(0L, record?.xpGained)
    }

    @Test
    fun aRowForARedPlayerNamesTheSideRatherThanTheMirror() {
        // `PvpSession.view` mirrors a red player's board so they see themselves in blue. A row that
        // recorded the mirror would report the opponent's half of the score as the player's.
        val record = pvp(
            side = CardColor.RED,
            // The server resolves the result from the player's side before it sends it, so a red
            // player who scored four is told LOSE — the row must not re-derive it from the board.
            outcome = PvpOutcome(result = MatchResult.LOSE, blue = 6, red = 4),
        ).asRecord(AT)

        assertEquals(CardColor.RED, record?.self)
        assertEquals(4, record?.ownScore, "the red player scored four")
        assertEquals(6, record?.opponentScore)
        assertEquals(MatchResult.LOSE, record?.result)
    }

    @Test
    fun aWageredRowReportsWhatThePurseDidRatherThanTheFlatPayout() {
        val record = pvp(
            outcome = PvpOutcome(
                result = MatchResult.WIN,
                blue = 6,
                red = 4,
                mgp = 100,
                xp = 60,
                stakeMgp = 500,
            ),
        ).asRecord(AT)

        assertEquals(600, record?.mgpDelta, "the stake came back and the row must say so")
    }

    @Test
    fun aLostStakeIsARowThatTookMoney() {
        val record = pvp(
            outcome = PvpOutcome(
                result = MatchResult.LOSE,
                blue = 4,
                red = 6,
                mgp = 15,
                stakeMgp = -500,
            ),
        ).asRecord(AT)

        assertEquals(-485, record?.mgpDelta)
    }

    @Test
    fun theSameMatchIsKeptOnceHoweverOftenItIsOffered() = runTest {
        // The writer's effects re-run whenever the session recomposes with the same settled match,
        // and a history that grew a row each time would be a history of recompositions.
        val journal = MatchJournal(MatchHistoryRepository(InMemoryDocumentStore()))
        val row = pve().asRecord(AT)!!

        repeat(3) { journal.record(KEY, row) }

        assertEquals(
            1,
            journal.records.size,
            "the match id did not de-duplicate: ${journal.records}",
        )
    }

    @Test
    fun aCharacterlessDeviceRecordsNothingRatherThanThrowing() = runTest {
        val store = InMemoryDocumentStore()
        val journal = MatchJournal(MatchHistoryRepository(store))

        journal.record(null, pve().asRecord(AT)!!)
        journal.refresh(null)

        assertTrue(journal.records.isEmpty())
        assertEquals(0, store.writes, "a signed-out device wrote a history document")
    }

    @Test
    fun refreshingSaysTheReadHasHappenedEvenWhenItFoundNothing() = runTest {
        // The screen tells an unread history from an empty one, so that a first frame is a spinner
        // rather than "no match on this device yet" on a device that has hundreds.
        val journal = MatchJournal(MatchHistoryRepository(InMemoryDocumentStore()))
        assertTrue(journal.isLoading, "a journal starts unread")

        journal.refresh(KEY)

        assertTrue(journal.records.isEmpty())
        assertTrue(!journal.isLoading, "the read answered and the screen was not told")
    }

    private fun pve(
        outcome: PveOutcome? = PveOutcome(
            result = MatchResult.WIN,
            blue = 6,
            red = 4,
            reward = RewardSummary(result = MatchResult.WIN, mgp = 120, xp = 40),
        ),
    ) = PveMatchView(
        matchId = MATCH_ID,
        opponentIconId = OPPONENT,
        rules = GameRules(same = true),
        formatId = FORMAT,
        cells = List(CELLS) { null },
        elements = List(CELLS) { null },
        hand = emptyList(),
        opponentHand = emptyList(),
        first = CardColor.BLUE,
        placement = CELLS,
        outcome = outcome,
    )

    private fun pvp(
        side: CardColor = CardColor.BLUE,
        outcome: PvpOutcome? = PvpOutcome(result = MatchResult.WIN, blue = 6, red = 4),
    ) = PvpMatchView(
        matchId = MATCH_ID,
        side = side,
        opponentName = "Somebody",
        rules = GameRules(plus = true),
        formatId = FORMAT,
        cells = List(CELLS) { null },
        elements = List(CELLS) { null },
        hand = emptyList(),
        opponentHand = emptyList(),
        first = CardColor.BLUE,
        placement = CELLS,
        outcome = outcome,
    )

    private companion object {
        const val MATCH_ID = "match-1"
        const val OPPONENT = "tt-master"
        const val FORMAT = "ff14-standard"
        const val KEY = "player - 1"
        const val AT = 1_700_000_000_000L
        const val CELLS = 9
    }
}
