package com.tripletriad.net

import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AccountError
import com.tripletriad.protocol.AccountFailure
import com.tripletriad.protocol.Credentials
import com.tripletriad.protocol.PasswordReset
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.Session
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four calls that confirm an address or replace a forgotten password.
 *
 * Beside `AccountClientTest` rather than in it, because they are a different subject and that file
 * is already the length of the API it mirrors. What is worth pinning here is not that a `POST`
 * reaches a path — it is the three things the flow gets wrong when nobody is looking: the status
 * each endpoint actually answers with, the header that decides which language the mail is written
 * in, and the fact that a success carries **no body** to decode.
 */
class CredentialRecoveryClientTest {

    // ---- Confirming an address ---------------------------------------------

    @Test
    fun confirmingPostsTheCodeAndAcceptsANoContentAnswer() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.NoContent, record = { seen = it })

        assertIs<AccountResult.Ok<Unit>>(client.confirmEmail(TOKEN, "123456"))
        assertEquals(HttpMethod.Post, seen?.method)
        assertEquals("/me/email/verify", seen?.url?.encodedPath)
        assertEquals("Bearer $TOKEN", seen?.headers?.get(HttpHeaders.Authorization))
        assertTrue("123456" in bodyOf(seen), "the code was not in the body: ${bodyOf(seen)}")
    }

    /**
     * 200 is not 204, and is not treated as success.
     *
     * The same rule `AccountClientTest` pins for registration. A client that accepted any 2xx would
     * report an account confirmed on the strength of a response that meant something else — and
     * this one is easy to get wrong, because four different statuses are in play across the four
     * calls in this file.
     */
    @Test
    fun aConfirmationAnsweredWithTwoHundredIsNotSuccess() = runTest {
        val client = clientAnswering(HttpStatusCode.OK)

        assertTrue(client.confirmEmail(TOKEN, "123456") !is AccountResult.Ok)
    }

    @Test
    fun aRefusedCodeCarriesTheServersReason() = runTest {
        val client = clientAnswering(
            HttpStatusCode.BadRequest,
            body = matchProtocolJson.encodeToString(
                AccountFailure(AccountError.INVALID_CODE, "that code is not valid"),
            ),
        )

        val refused = assertIs<AccountResult.Refused>(client.confirmEmail(TOKEN, "000000"))
        assertEquals(AccountError.INVALID_CODE, refused.failure.error)
    }

    /**
     * `INVALID_CODE` did not exist when this client was written, and reaching an older build it
     * degrades to [AccountResult.Failed] rather than throwing.
     *
     * Worth a test because it is the compatibility property the whole additive change rests on: the
     * protocol version did not move for any of this, on the argument that an unknown enum value is
     * survivable. This is that argument, executed.
     */
    @Test
    fun anErrorCodeThisBuildDoesNotKnowIsNotACrash() = runTest {
        val client = clientAnswering(
            HttpStatusCode.BadRequest,
            body = """{"error":"SOMETHING_INVENTED_LATER","detail":"nope"}""",
        )

        val failed = assertIs<AccountResult.Failed>(client.confirmEmail(TOKEN, "000000"))
        assertEquals(HttpStatusCode.BadRequest.value, failed.status)
    }

    // ---- Asking for another code -------------------------------------------

    @Test
    fun resendingIsAcceptedRatherThanCreatedOrEmpty() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.Accepted, record = { seen = it })

        assertIs<AccountResult.Ok<Unit>>(client.resendCode(TOKEN))
        assertEquals("/me/email/resend", seen?.url?.encodedPath)
    }

    /**
     * The language reaches the server as `Accept-Language`, and only when there is one.
     *
     * Two assertions and both matter. The header is what decides which of the four languages the
     * mail is written in — a mail in the wrong one is the most visible bug this flow can have —
     * and its *absence* has to stay absent rather than becoming an empty header, which some servers
     * read as a request for nothing rather than as no request at all.
     */
    @Test
    fun theLanguageTravelsAsAHeaderAndIsOmittedWhenThereIsNone() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.Accepted, record = { seen = it })

        client.resendCode(TOKEN, language = "fr_FR")
        assertEquals("fr_FR", seen?.headers?.get(HttpHeaders.AcceptLanguage))

        client.resendCode(TOKEN)
        assertNull(seen?.headers?.get(HttpHeaders.AcceptLanguage))
    }

    @Test
    fun registeringCarriesTheAddressAndTheLanguage() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(
            HttpStatusCode.Created,
            body = matchProtocolJson.encodeToString(session),
            record = { seen = it },
        )

        client.register(
            Credentials("kuplu", "not-a-real-password", "kuplu@example.test"),
            language = "ja_JA",
        )

        assertEquals("ja_JA", seen?.headers?.get(HttpHeaders.AcceptLanguage))
        assertTrue("kuplu@example.test" in bodyOf(seen), bodyOf(seen))
    }

    /**
     * Signing in still sends exactly the bytes it always did.
     *
     * `Credentials` gained a nullable field, and `matchProtocolJson` sets `explicitNulls = false`
     * with `encodeDefaults` off — so an absent address is an absent *key*, not `"email":null`. That
     * is the whole reason a new client can sign in to a server deployed before any of this: the
     * server's own reader has `ignoreUnknownKeys = false` and would refuse the key outright.
     *
     * Registration is the deliberate exception. It has to send an address, so it is the one call
     * that needs the server updated first.
     */
    @Test
    fun signingInDoesNotMentionAnAddressAtAll() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(
            HttpStatusCode.OK,
            body = matchProtocolJson.encodeToString(session),
            record = { seen = it },
        )

        client.signIn(Credentials("kuplu", "not-a-real-password"))

        assertTrue("email" !in bodyOf(seen), "sign-in carried an address key: ${bodyOf(seen)}")
    }

    // ---- Forgotten passwords -----------------------------------------------

    @Test
    fun theForgottenPasswordCallNamesTheAccountAndIsAccepted() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.Accepted, record = { seen = it })

        assertIs<AccountResult.Ok<Unit>>(client.forgotPassword("kuplu"))
        assertEquals("/accounts/password/forgot", seen?.url?.encodedPath)
        // Sent as given. Trimming is `AccountSession.Recovery`'s job, where the string arrives from
        // a text field — this layer is a transport and inventing an edit here would mean two places
        // that both nearly normalise a username.
        assertTrue("\"kuplu\"" in bodyOf(seen), bodyOf(seen))
        // No bearer token, and that is the point of the endpoint: a player who has forgotten their
        // password has no session to send.
        assertNull(seen?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun resettingPostsTheCodeAndTheNewPasswordAndIsNoContent() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.NoContent, record = { seen = it })

        val result = client.resetPassword(
            PasswordReset("kuplu", "123456", "a-different-horse-entirely"),
        )

        assertIs<AccountResult.Ok<Unit>>(result)
        assertEquals("/accounts/password/reset", seen?.url?.encodedPath)
        assertTrue("123456" in bodyOf(seen), bodyOf(seen))
    }

    /** A dead server is an ordinary state of the world here as everywhere in `net/`. */
    @Test
    fun anUnreachableServerIsOfflineRatherThanAThrow() = runTest {
        val client = AccountClient(
            httpClient(MockEngine { throw kotlinx.io.IOException("no route to host") }),
            address,
        )

        assertIs<AccountResult.Offline>(client.forgotPassword("kuplu"))
    }

    // ---- Fixtures ----------------------------------------------------------

    private suspend fun bodyOf(request: HttpRequestData?): String =
        request?.body?.toByteArray()?.decodeToString().orEmpty()

    private fun clientAnswering(
        status: HttpStatusCode,
        body: String = "",
        record: (HttpRequestData) -> Unit = {},
    ): AccountClient {
        val engine = MockEngine { request ->
            record(request)
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
        return AccountClient(httpClient(engine), address)
    }

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(matchProtocolJson) }
    }

    private val player = PlayerState(
        save = GameSave(username = "kuplu"),
        email = "kuplu@example.test",
        verified = false,
    )

    private val session = Session(token = TOKEN, expiresAt = 1_770_086_400_000L, player = player)

    private val address: suspend () -> String = { "http://127.0.0.1:8080" }

    private companion object {
        const val TOKEN = "test-session"
    }
}
