package com.tripletriad.ui

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.net.PvpClient
import com.tripletriad.notify.RecordingNotifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the app says when somebody is waiting on a player who is looking at another screen.
 *
 * Read a turn at a time rather than through the loop: `PvpAlerts.watch` is a `delay` and a call to
 * `turn`, and driving it through virtual time would assert against answers that had not arrived —
 * the requests settle on the HTTP client's own dispatchers, which a `TestScope` does not own. What
 * the loop adds beyond the turn is a twenty-second wait, and a test of that is a test of `delay`.
 */
class PvpAlertsTest {

    @Test
    fun anInvitationThatArrivesWhileElsewhereRings() = runTest {
        val notifier = RecordingNotifier()
        val invitations = mutableListOf<String>()
        val alerts = alerts(session(invitations), notifier) { false }

        // The first turn is the baseline, and it must not ring: an invitation that was already
        // waiting is one the lobby will show, not one to interrupt anybody about.
        alerts.turn()
        assertEquals(emptyList(), notifier.posted, "an invitation already there rang")

        invitations += challengeJson()
        alerts.turn()

        assertEquals(1, notifier.posted.size)
        assertEquals("New invitation", notifier.posted.single().title)
        assertTrue(notifier.posted.single().body.contains(THEM), "the body does not name who")
    }

    @Test
    fun theSameInvitationDoesNotRingTwice() = runTest {
        val notifier = RecordingNotifier()
        val invitations = mutableListOf<String>()
        val alerts = alerts(session(invitations), notifier) { false }

        alerts.turn()
        invitations += challengeJson()
        alerts.turn()
        alerts.turn()
        alerts.turn()

        assertEquals(1, notifier.posted.size, "the same invitation rang on every poll")
    }

    /**
     * The invitation is *read* while the player is on the multiplayer screen and never announced —
     * neither then nor later. Without that read, leaving the lobby would ring for everything the
     * player had just finished looking at.
     */
    @Test
    fun whatWasReadOnTheLobbyScreenIsNotAnnouncedOnLeavingIt() = runTest {
        val notifier = RecordingNotifier()
        val invitations = mutableListOf<String>()
        var looking = true
        val alerts = alerts(session(invitations), notifier) { looking }

        alerts.turn()
        invitations += challengeJson()
        alerts.turn()
        looking = false
        alerts.turn()

        assertEquals(emptyList(), notifier.posted, "an invitation already read rang on leaving")
    }

    @Test
    fun aTableBeingTakenRings() = runTest {
        val notifier = RecordingNotifier()
        val alerts = alerts(session(mutableListOf(), match = { MATCH }), notifier) { false }

        alerts.turn()
        alerts.turn()

        assertEquals(1, notifier.posted.count { it.title == "Your match has started" })
    }

    /** Nothing is said about the match the player is already sitting at. */
    @Test
    fun aMatchIsNotAnnouncedToSomebodyPlayingIt() = runTest {
        val notifier = RecordingNotifier()
        val alerts = alerts(session(mutableListOf(), match = { MATCH }), notifier) { true }

        alerts.turn()
        alerts.turn()

        assertEquals(emptyList(), notifier.posted)
    }

    // ---- harness ----------------------------------------------------------

    private fun alerts(
        session: PvpSession,
        notifier: RecordingNotifier,
        looking: () -> Boolean,
    ) = PvpAlerts(session, notifier, strings, looking)

    private fun session(
        invitations: List<String>,
        match: () -> String? = { null },
    ): PvpSession {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/challenges") ->
                    respondJson("[" + invitations.joinToString(",") + "]")

                else -> match()?.let { respondJson(it) } ?: respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return PvpSession(
            client = PvpClient(http, { "http://server" }),
            tokenOf = { "token" },
            hostName = ME,
        )
    }

    private fun challengeJson() = """
        {"id":"c-1","fromName":"$THEM","toName":"$ME","formatId":"free-play",
         "rules":{},"roulette":false,"stake":{"mgp":0,"trade":"NONE"},"expiresAt":1}
    """.trimIndent()

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val strings = Strings(
        AppLocale.EN_US,
        mapOf(
            StringKeys.NOTIFY_CHALLENGE_TITLE to "New invitation",
            StringKeys.NOTIFY_CHALLENGE_BODY to "{0} is waiting for your answer.",
            StringKeys.NOTIFY_MATCH_TITLE to "Your match has started",
            StringKeys.NOTIFY_MATCH_BODY to "Somebody took your table.",
        ),
        emptyMap(),
    )

    private companion object {
        const val ME = "Sigfrid"
        const val THEM = "Kuplu"

        val MATCH = """
            {"matchId":"m-1","side":"BLUE","opponentName":"$THEM","rules":{},
             "formatId":"free-play","cells":[null,null,null,null,null,null,null,null,null],
             "elements":[null,null,null,null,null,null,null,null,null],
             "hand":[],"opponentHand":[],"first":"BLUE","placement":0,"status":"PLAYING"}
        """.trimIndent()
    }
}
