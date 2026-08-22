package com.tripletriad.ui

import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpTableRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What a **signed-out** or **failing** player-versus-player session does.
 *
 * [PvpSessionTest] owns the happy paths; this owns the two edges every one of its methods has and
 * that a lobby polling once a second hits constantly.
 *
 * Two rules are being asserted, and they are deliberately different from each other:
 *
 * * **Signed out, nothing is sent.** Every method reads the token first and gives up quietly
 *   without one. A request with no session behind it is one the server can only answer 401, and a
 *   screen that fired a dozen a second while the token was being restored would be a screen that
 *   signs the player out for a moment on every launch.
 * * **A failed *list* read is shown on the list; a failed *action* is published.** The lobby, the
 *   invitations and the claims are polled once a second, so a note per failed poll would bury the
 *   screen — their `ListState` carries it instead. Hosting, joining, forfeiting and claiming are
 *   things the player did on purpose, and those set `failure` so the screen can say so.
 */
class PvpSessionGuardsTest {

    // ---- Nothing to be in the middle of -----------------------------------

    @Test
    fun aSessionWithNoMatchHasNoSideAndIsNotInAnyStateOfBeingOver() = runTest {
        val session = session()

        assertNull(session.side, "there is no board, so the player is neither colour")
        assertFalse(session.isOver, "nothing has begun, so nothing has ended")
        assertFalse(session.isAwaitingClaim, "and nothing is owed")
        assertFalse(session.isSettled)
    }

    @Test
    fun clearingASessionThatHasNoMatchIsHarmless() = runTest {
        val session = session()

        session.clear()

        assertNull(session.match)
    }

    // ---- Signed out -------------------------------------------------------

    /**
     * Every method, in one test, because the claim is about all of them at once.
     *
     * Listing them individually would be a dozen near-identical tests asserting the same sentence;
     * what matters is that **no** entry point is the one that forgot to check.
     */
    @Test
    fun aSignedOutSessionTalksToNobody() = runTest {
        var asked = 0
        val session = session(token = null) { asked++ }

        session.refreshTables()
        session.refreshChallenges()
        session.refreshClaims()
        session.host(PvpTableRequest(formatId = FORMAT))
        session.cancelTable(TABLE)
        session.join(TABLE)
        session.challenge("somebody", PvpTableRequest(formatId = FORMAT))
        session.accept(CHALLENGE)
        session.dropChallenge(CHALLENGE)
        session.play(PvpMove(handIndex = 0, position = 0))
        session.forfeit()
        session.claim(MATCH, cardIds = emptyList())

        assertEquals(0, asked, "a request went out with no session behind it")
        assertNull(session.failure, "and giving up quietly is not a failure to report")
    }

    // ---- Nothing to play in -----------------------------------------------

    /**
     * A move and a forfeit both need a match id, and neither invents one.
     *
     * The screen cannot normally reach either without a board, but a tap queued behind [clear] can,
     * and a request naming no match is one the server can only refuse.
     */
    @Test
    fun playingAndForfeitingWithNoMatchAskNothing() = runTest {
        var asked = 0
        val session = session { asked++ }

        session.play(PvpMove(handIndex = 0, position = 0))
        session.forfeit()

        assertEquals(0, asked, "there is no match to play in or give up")
    }

    // ---- A failed list read stays on the list ------------------------------

    @Test
    fun aFailedLobbyReadIsShownOnTheListAndNotAsANote() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.refreshTables()

        assertEquals(ListState.FAILED, session.tablesState)
        assertNull(session.failure, "a poll that fails once a second must not raise a note")
    }

    @Test
    fun aFailedInvitationsReadIsShownOnItsOwnList() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.refreshChallenges()

        assertEquals(ListState.FAILED, session.challengesState)
        assertNull(session.failure)
    }

    @Test
    fun aFailedClaimsReadIsShownOnItsOwnList() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.refreshClaims()

        assertEquals(ListState.FAILED, session.claimsState)
        assertNull(session.failure)
    }

    /** And the state each list starts in, before anything has answered. */
    @Test
    fun everyListStartsLoadingRatherThanEmpty() = runTest {
        val session = session()

        assertEquals(ListState.LOADING, session.tablesState)
        assertEquals(ListState.LOADING, session.challengesState)
        assertEquals(ListState.LOADING, session.claimsState)
    }

    // ---- A failed action is published --------------------------------------

    @Test
    fun aRefusedTableIsReportedSoTheScreenCanSaySo() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.host(PvpTableRequest(formatId = FORMAT))

        assertNotNull(session.failure, "the player asked for this, so they are told")
        assertFalse(session.isBusy, "and the session is free again either way")
    }

    @Test
    fun aRefusedJoinIsReported() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.join(TABLE)

        assertNotNull(session.failure)
    }

    @Test
    fun aRefusedInvitationIsReported() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.challenge("somebody", PvpTableRequest(formatId = FORMAT))

        assertNotNull(session.failure)
    }

    @Test
    fun aRefusedAcceptanceIsReported() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.accept(CHALLENGE)

        assertNotNull(session.failure)
    }

    @Test
    fun aRefusedClaimIsReported() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.claim(MATCH, cardIds = listOf(257))

        assertNotNull(session.failure)
    }

    /**
     * Dropping an invitation is the one action whose refusal is **not** reported.
     *
     * The invitation is off this player's screen either way, and an expiry that already removed it
     * server-side reached the same outcome they asked for. Reporting it would be telling somebody
     * their tidying-up failed when it did not.
     */
    @Test
    fun aRefusedDropIsSwallowedBecauseTheInvitationIsGoneEitherWay() = runTest {
        val session = session(status = HttpStatusCode.InternalServerError)

        session.dropChallenge(CHALLENGE)

        assertNull(session.failure, "the invitation is gone from this screen regardless")
    }

    // ---- Fixtures ----------------------------------------------------------

    /**
     * A session whose server answers [status] with an empty JSON list, and which counts requests.
     *
     * `[]` decodes as an empty lobby, an empty set of invitations and an empty set of claims, so
     * one body serves every read here; the tests that care about a *failure* never decode it.
     */
    private fun session(
        token: String? = "token",
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: () -> Unit = {},
    ): PvpSession {
        val engine = MockEngine {
            onRequest()
            respond(
                content = "[]",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return PvpSession(
            client = PvpClient(http, { "http://server" }),
            tokenOf = { token },
            hostName = "",
        )
    }

    private companion object {
        const val FORMAT = "free-play"
        const val TABLE = "t-1"
        const val CHALLENGE = "c-1"
        const val MATCH = "m-1"
    }
}
