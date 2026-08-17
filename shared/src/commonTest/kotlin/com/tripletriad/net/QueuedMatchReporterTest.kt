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

class QueuedMatchReporterTest {

    @Test
    fun aReportedMatchIsQueuedForLater() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { judged(accepted) }

        reporter.report(PROFILE, transcript(seed = 1))

        assertEquals(listOf(1), TranscriptQueue(store).pending(PROFILE).map { it.seed })
    }

    @Test
    fun aQueueThatCannotBeWrittenIsLoggedRatherThanThrown() = runTest {
        val reporter = reporterOver(FailingStore()) { judged(accepted) }

        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE), "a broken store must not throw out of a drain either")
    }

    @Test
    fun aDrainReturnsTheCreditedProfile() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { judged(accepted, player = credited(mgp = 500)) }
        reporter.report(PROFILE, transcript(seed = 1))

        assertEquals(credited(mgp = 500), reporter.drain(PROFILE))
    }

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

    @Test
    fun aDuplicateReceiptCreditsNothingEvenThoughItCarriesAProfile() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) {
            judged(accepted, player = credited(mgp = 700), duplicate = true)
        }
        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE), "a duplicate is not new information")
    }

    @Test
    fun aRejectedTranscriptCreditsNothing() = runTest {
        val store = InMemoryDocumentStore()
        val rejected = MatchVerdict.Rejected(RejectionReason.ILLEGAL_MOVE, "cell was taken")
        val reporter = reporterOver(store) { judged(rejected) }
        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE))
    }

    @Test
    fun anOfflineDrainCreditsNothingAndKeepsTheQueue() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { SubmissionResult.Offline("no route") }
        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE))
        assertEquals(listOf(1), TranscriptQueue(store).pending(PROFILE).map { it.seed })
    }

    @Test
    fun anUnauthenticatedDrainCreditsNothingAndKeepsTheQueue() = runTest {
        val store = InMemoryDocumentStore()
        val reporter = reporterOver(store) { SubmissionResult.Unauthenticated }
        reporter.report(PROFILE, transcript(seed = 1))

        assertNull(reporter.drain(PROFILE))
        assertEquals(listOf(1), TranscriptQueue(store).pending(PROFILE).map { it.seed })
    }

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
