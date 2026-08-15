package com.tripletriad.net

import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.MatchReceipt
import com.tripletriad.protocol.MatchSubmitter
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.MatchVerdict
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.RejectionReason
import com.tripletriad.protocol.SubmissionResult
import com.tripletriad.protocol.TranscriptMove
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The glue between the durable queue and the submitter — and **what a drain credits**.
 *
 * ### Why this file did not exist, and why that mattered
 *
 * [QueuedMatchReporter] is four short methods over two collaborators that each have their own
 * tests, which is exactly the shape that looks too thin to be worth testing. It is not: it is the
 * only thing that decides what happens to the profile the server writes when it credits a match,
 * and for as long as that answer was a constructor callback, **nothing anywhere passed one**. A
 * test asking "what does a drain hand back" would have found that on the day it was written; the
 * player found it instead, as a card that could be seen in the bag and not spent.
 *
 * So the assertions here are mostly about the return value, and each one names a receipt shape the
 * server really produces.
 */
class QueuedMatchReporterTest {

    @Test
    fun aReportedMatchIsQueuedForLater() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { judged(accepted) }

        reporter.report(PROFILE, transcript(seed = 1))

        assertEquals(listOf(1), TranscriptQueue(store).pending(PROFILE).map { it.seed })
    }

    /**
     * Nothing here throws: a match is already over, and a full disk must not take the screen
     * down with it.
     */
    @Test
    fun aQueueThatCannotBeWrittenIsLoggedRatherThanThrown() = runTest {
        val reporter = reporterOver(FailingStore()) { judged(accepted) }

        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE), "a broken store must not throw out of a drain either")
    }

    /**
     * The one that matters: a drain hands back the profile the server credited.
     *
     * `AccountSession` adopts it and `MatchSettlement` is what asks — see both for why a client
     * that ignores this ends up spending items the server does not hold.
     */
    @Test
    fun aDrainReturnsTheCreditedProfile() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { judged(accepted, player = credited(mgp = 500)) }
        reporter.report(PROFILE, transcript(seed = 1))

        assertEquals(credited(mgp = 500), reporter.drain(PROFILE))
    }

    /**
     * With several credited at once, the **newest** wins and the rest are not applied in turn.
     *
     * Each receipt's profile already includes every match credited before it, so replaying them
     * would show the player their progression flickering through states the server considers
     * superseded.
     */
    @Test
    fun aDrainOfSeveralReturnsOnlyTheNewestProfile() = runTest {
        val store = InMemoryDocumentStore()
        var paid = 0
        val reporter = reporterOver(store) {
            paid += 100
            judged(accepted, player = credited(mgp = paid))
        }
        reporter.report(PROFILE, transcript(seed = 1))
        reporter.report(PROFILE, transcript(seed = 2))

        assertEquals(credited(mgp = 200), reporter.drain(PROFILE), "an older profile was adopted")
    }

    /**
     * A duplicate carries a profile and is still skipped.
     *
     * It is the state the server already had — a receipt it kept from the first time this match was
     * submitted — so adopting it would undo whatever was credited *after* it in the same drain.
     */
    @Test
    fun aDuplicateReceiptCreditsNothingEvenThoughItCarriesAProfile() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) {
            judged(accepted, player = credited(mgp = 700), duplicate = true)
        }
        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE), "a duplicate is not new information")
    }

    /** A rejected transcript credits nothing, and carries no profile to credit. */
    @Test
    fun aRejectedTranscriptCreditsNothing() = runTest {
        val store = InMemoryDocumentStore()
        val rejected = MatchVerdict.Rejected(RejectionReason.ILLEGAL_MOVE, "cell was taken")
        val reporter = reporterOver(store) { judged(rejected) }
        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE))
    }

    /** An unreachable server credits nothing and keeps the queue for the next launch. */
    @Test
    fun anOfflineDrainCreditsNothingAndKeepsTheQueue() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { SubmissionResult.Offline("no route") }
        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE))
        assertEquals(listOf(1), TranscriptQueue(store).pending(PROFILE).map { it.seed })
    }

    /**
     * No session holds the queue too — the case a player who played offline is in.
     *
     * Distinct from being offline in what it says about *why*, and identical in what it does: the
     * transcripts stay, because signing in is exactly what makes them creditable.
     */
    @Test
    fun anUnauthenticatedDrainCreditsNothingAndKeepsTheQueue() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { SubmissionResult.Unauthenticated }
        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE))
        assertEquals(listOf(1), TranscriptQueue(store).pending(PROFILE).map { it.seed })
    }

    /**
     * The two answers that are not verdicts and are still answers.
     *
     * A server on a newer protocol and a request it could not read both consume the transcript —
     * see `TranscriptQueue.drain` for why — and neither credits anything. They are here because
     * this class has to survive being handed one: they carry no receipt at all, so the mapping that
     * picks a credited profile has nothing to read on them.
     */
    @Test
    fun anAnswerWithNoReceiptCreditsNothing() = runTest {
        for (answer in listOf(
            SubmissionResult.UpdateRequired(AppVersion(2, 0, 0)),
            SubmissionResult.Failed(status = 400, detail = "malformed_request"),
        )) {
            val store = InMemoryDocumentStore()
            val reporter = reporterOver(store) { answer }
            reporter.report(PROFILE, transcript(seed = 1))

            assertNull(reporter.drain(PROFILE), "$answer credited something")
            assertTrue(TranscriptQueue(store).pending(PROFILE).isEmpty(), "$answer was retained")
        }
    }

    /**
     * Draining with nothing queued is a no-op, which is what makes it safe to call on every exit
     * from a board — see `MatchSettlement`.
     */
    @Test
    fun drainingAnEmptyQueueAsksNothingAndCreditsNothing() = runTest {
        var submissions = 0
        val reporter = reporterOver(InMemoryDocumentStore()) {
            submissions++
            judged(accepted)
        }

        assertNull(reporter.drain(PROFILE))
        assertEquals(0, submissions)
    }

    @Test
    fun forgettingAProfileEmptiesItsQueue() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { judged(accepted) }
        reporter.report(PROFILE, transcript(seed = 1))

        reporter.forget(PROFILE)

        assertTrue(TranscriptQueue(store).pending(PROFILE).isEmpty())
    }

    @Test
    fun forgettingSurvivesAStoreThatCannotDelete() = runTest {
        reporterOver(FailingStore()) { judged(accepted) }.forget(PROFILE)
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun reporterOver(store: DocumentStore, answer: () -> SubmissionResult) =
        QueuedMatchReporter(TranscriptQueue(store), RecordingSubmitter(answer))

    private fun judged(
        verdict: MatchVerdict,
        player: PlayerState? = null,
        duplicate: Boolean = false,
    ) = SubmissionResult.Judged(
        MatchReceipt(verdict = verdict, player = player, duplicate = duplicate),
    )

    private fun credited(mgp: Int) = PlayerState(save = GameSave(username = "kuplu", mgp = mgp))

    private class RecordingSubmitter(private val answer: () -> SubmissionResult) : MatchSubmitter {
        override suspend fun submit(transcript: MatchTranscript): SubmissionResult = answer()
    }

    /**
     * A store where every operation fails, which is what a full disk or a revoked permission is.
     *
     * The whole point of the `try`/`catch` in [QueuedMatchReporter]: the platform decides what a
     * failed write throws, so the reporter catches broadly and carries on. This proves it carries
     * on rather than merely that it compiles.
     */
    private class FailingStore : DocumentStore {
        override suspend fun read(key: String): String? = error("no")
        override suspend fun write(key: String, text: String): Unit = error("no")
        override suspend fun keys(): List<String> = error("no")
        override suspend fun delete(key: String): Unit = error("no")
    }

    private fun transcript(seed: Int) = MatchTranscript(
        seed = seed,
        formatId = "ff14",
        opponentIconId = "tt-master",
        deck = listOf(1, 2, 3, 4, 5),
        ownedCards = (1..5).associateWith { 1 },
        moves = listOf(TranscriptMove(cardId = 1, position = 0)),
    )

    private val accepted = MatchVerdict.Accepted(blue = 6, red = 4, winner = "BLUE")

    private companion object {
        const val PROFILE = "cid"
    }
}
