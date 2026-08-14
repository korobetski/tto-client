package com.tripletriad.ui

import com.tripletriad.data.ShopOffer
import com.tripletriad.data.StarterCatalog
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.GameSave
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import com.tripletriad.net.AccountClient
import com.tripletriad.net.AccountResult
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.PvpClient
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerProbe
import com.tripletriad.net.SessionStore
import com.tripletriad.net.StoredSession
import com.tripletriad.net.TicketStore
import com.tripletriad.net.accountQueueKey
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.protocol.BagItemRequest
import com.tripletriad.protocol.ItemEffect
import com.tripletriad.protocol.ItemUsed
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.SeedTickets
import com.tripletriad.protocol.Session
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Signing in, coming back, and signing out — the state a form and a dashboard render.
 *
 * What is being pinned down here is the promise the whole account feature is for: **the character
 * is the server's, and a player who comes back finds it**. Everything else in the class exists to
 * keep that true when the network is not cooperating.
 */
class AccountSessionTest {

    // ---- Intents ----------------------------------------------------------

    /**
     * Every intent goes to its own endpoint, walked as a set rather than one at a time.
     *
     * The reason `Intent` is a sealed type: a sixth case does not compile until it is handled here
     * as well as on the server. A test per intent would be five chances to add a sixth that quietly
     * goes nowhere — this one fails the moment the `when` gains a branch nobody wired up.
     */
    @Test
    fun everyIntentAsksItsOwnEndpoint() = runTest {
        val expected = mapOf(
            Intent.Buy(ShopOffer(PotionItem(PotionType.XP), price = 50), "free-play")
                to "/me/shop/buy",
            Intent.SellItem(PotionItem(PotionType.XP)) to "/me/bag/sell",
            Intent.DiscardItem(PotionItem(PotionType.XP)) to "/me/bag/discard",
            Intent.SellCard(cardId = 257) to "/me/cards/sell",
            Intent.EnterCampaign(campaignKey = "cc", fee = 500) to "/me/campaign/enter",
            Intent.ClaimStarter(StarterCatalog(emptyList())) to "/me/starter",
        )

        for ((intent, path) in expected) {
            val paths = mutableListOf<String>()
            val session = signedInSession(
                answering(HttpStatusCode.OK, encode(player)) { paths += it.url.encodedPath },
            )

            session.perform(intent)

            assertEquals(listOf(path), paths, "$intent went to $paths")
            assertEquals(player, session.player, "$intent did not adopt the server's profile")
        }
    }

    /** A refused intent is reported and changes nothing, as a refused item use is. */
    @Test
    fun aRefusedIntentIsReportedAndChangesNothing() = runTest {
        val session = signedInSession(answering(HttpStatusCode.Conflict, """{"error":"NOPE"}"""))
        val before = session.player

        session.perform(Intent.SellCard(cardId = 257))

        assertNotNull(session.failure, "a refused intent was swallowed")
        assertEquals(before, session.player)
        assertFalse(session.isBusy)
    }

    /** With nobody signed in, an intent asks nothing at all. */
    @Test
    fun anIntentWithoutASessionAsksNothing() = runTest {
        val paths = mutableListOf<String>()
        val session = sessionOver(
            answering(HttpStatusCode.OK, "{}") { paths += it.url.encodedPath },
        )

        session.perform(Intent.SellCard(cardId = 257))

        assertTrue(paths.isEmpty(), "a signed-out client still called the server: $paths")
    }

    // ---- Seed tickets -----------------------------------------------------

    /**
     * The stock is read from disk first, so a launch with no network can still play.
     *
     * The whole reason the seeds are written down. A client that only ever fetched them would be
     * unable to start a match on a plane, which is exactly the case the stock exists for.
     */
    @Test
    fun theStockIsReadFromDiskBeforeAnythingIsAsked() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        TicketStore(documents).save(accountQueueKey(home.id, "kuplu"), listOf(11, 22, 33))
        // The server answers nothing, so what is held can only have come off the disk.
        val session = sessionOver(
            ticketEngine { respondJson(HttpStatusCode.ServiceUnavailable, "") },
            documents,
        )
        session.restore()

        session.loadTickets()

        assertEquals(3, session.ticketsHeld, "the stored seeds were not read")
        assertEquals(11, session.nextSeed(), "the oldest seed is not the next one")
    }

    /** Each seed is handed out once, oldest first, and then the stock is empty. */
    @Test
    fun eachSeedIsHandedOutOnce() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        TicketStore(documents).save(accountQueueKey(home.id, "kuplu"), listOf(1, 2))
        val session = sessionOver(
            ticketEngine { respondJson(HttpStatusCode.ServiceUnavailable, "") },
            documents,
        )
        session.restore()
        session.loadTickets()

        assertEquals(listOf(1, 2), listOf(session.nextSeed(), session.nextSeed()))
        assertNull(session.nextSeed(), "a seed was handed out that did not exist")
        assertEquals(0, session.ticketsHeld)
    }

    /**
     * A low stock is topped up, and what the server answers replaces what was held.
     *
     * Replaces rather than appends because the response is *everything* the account holds unspent —
     * which is also what repairs a stock that has drifted from the server's idea of it.
     */
    @Test
    fun aLowStockIsToppedUpFromTheServer() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        TicketStore(documents).save(accountQueueKey(home.id, "kuplu"), listOf(9))
        val session = sessionOver(
            ticketEngine { respondJson(HttpStatusCode.OK, encode(SeedTickets(listOf(7, 8, 9)))) },
            documents,
        )
        session.restore()

        session.loadTickets()

        assertEquals(3, session.ticketsHeld, "the stock was not topped up")
        assertEquals(
            listOf(7, 8, 9),
            TicketStore(documents).load(accountQueueKey(home.id, "kuplu")),
            "the topped-up stock was not written down",
        )
    }

    /**
     * A top-up that cannot happen leaves the stock alone and reports nothing.
     *
     * Offline is the ordinary case here, not a failure: the player keeps what they hold and plays
     * from it. Putting it on [AccountSession.failure] would raise a note over an unrelated screen.
     */
    @Test
    fun aTopUpThatFailsKeepsWhatIsHeldAndSaysNothing() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        TicketStore(documents).save(accountQueueKey(home.id, "kuplu"), listOf(5))
        val session = sessionOver(
            ticketEngine { respondJson(HttpStatusCode.ServiceUnavailable, "") },
            documents,
        )
        session.restore()

        session.loadTickets()

        assertEquals(1, session.ticketsHeld, "an unreachable server emptied the stock")
        assertEquals(5, session.nextSeed())
        assertNull(session.failure, "being offline was reported as a failure")
    }

    /** With nobody signed in there is nothing to key a stock on, and nothing is asked. */
    @Test
    fun thereIsNoStockWithoutAnAccount() = runTest {
        val session = sessionOver(answering(HttpStatusCode.NoContent, ""))

        session.loadTickets()

        assertEquals(0, session.ticketsHeld)
        assertNull(session.nextSeed())
    }

    // ---- Using an item ----------------------------------------------------

    /**
     * Using an item **asks the server** and takes the profile it answers with.
     *
     * The assertion that matters is the third one: nothing about the resulting profile was computed
     * here. A pack opened locally would produce a perfectly plausible save that the server never
     * agreed to, which is the whole vector `POST /me/bag/use` exists to close.
     */
    @Test
    fun usingAnItemAsksTheServerAndAdoptsWhatItAnswers() = runTest {
        val paths = mutableListOf<String>()
        val opened = ItemUsed(
            player = PlayerState(save = GameSave(username = "kuplu", mgp = 4200, cards = mapOf())),
            effect = ItemEffect.PackOpened(cardIds = listOf(260, 261), newCardIds = setOf(261)),
        )
        val session = signedInSession(
            answering(HttpStatusCode.OK, encode(opened)) { paths += it.url.encodedPath },
        )

        val effect = session.useItem(BoosterItem(BoosterType.BRONZE))

        assertEquals(ItemEffect.PackOpened(listOf(260, 261), setOf(261)), effect)
        assertEquals(listOf("/me/bag/use"), paths, "the item was used without asking: $paths")
        assertEquals(opened.player, session.player, "the server's profile was not adopted")
        assertFalse(session.isBusy)
    }

    /**
     * Each attempt carries an operation id, and two attempts do not share one.
     *
     * The id is what makes a retry safe on the server — see `Idempotent` in `:core`. Two *taps* are
     * two intents and must not collide, or the second one would silently replay the first and the
     * player would watch the same pack twice having spent two.
     */
    @Test
    fun everyAttemptCarriesItsOwnOperationId() = runTest {
        val bodies = mutableListOf<String>()
        val used = ItemUsed(player = player, effect = ItemEffect.NotUseable)
        val session = signedInSession(
            MockEngine { request ->
                bodies += (request.body as TextContent).text
                respondJson(HttpStatusCode.OK, encode(used))
            },
        )

        session.useItem(BoosterItem(BoosterType.BRONZE))
        session.useItem(BoosterItem(BoosterType.BRONZE))

        val ids = bodies.map { matchProtocolJson.decodeFromString<BagItemRequest>(it).operationId }
        assertEquals(2, ids.size)
        assertTrue(ids.all { it.isNotBlank() }, "an attempt carried no operation id: $ids")
        assertNotEquals(ids[0], ids[1], "two taps shared an operation id")
    }

    /** Nobody signed in means nothing is used, and no request is made to find that out. */
    @Test
    fun usingAnItemWithoutASessionAsksNothing() = runTest {
        val paths = mutableListOf<String>()
        val session = sessionOver(
            answering(HttpStatusCode.OK, "{}") { paths += it.url.encodedPath },
        )

        assertNull(session.useItem(BoosterItem(BoosterType.BRONZE)))
        assertTrue(paths.isEmpty(), "a signed-out client still called the server: $paths")
    }

    /** A refused use is reported rather than swallowed, and nothing is invented in its place. */
    @Test
    fun aRefusedUseIsReportedAndChangesNothing() = runTest {
        val session = signedInSession(answering(HttpStatusCode.Conflict, """{"error":"NOPE"}"""))
        val before = session.player

        assertNull(session.useItem(BoosterItem(BoosterType.BRONZE)))
        assertNotNull(session.failure, "a refused use was swallowed")
        assertEquals(before, session.player, "a refused use changed the profile anyway")
        assertFalse(session.isBusy)
    }

    // ---- Signing in -------------------------------------------------------

    @Test
    fun signingInPublishesTheServerHeldPlayer() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(signedIn)))

        session.signIn("kuplu", PASSWORD)

        assertEquals(player, session.player)
        assertEquals(4200, session.save?.mgp)
        assertFalse(session.isBusy)
        assertNull(session.failure)
    }

    /** And the token is stored, which is the whole point of having signed in. */
    @Test
    fun signingInStoresTheTokenForTheNextLaunch() = runTest {
        val documents = InMemoryDocumentStore()
        val session = sessionOver(answering(HttpStatusCode.OK, encode(signedIn)), documents)

        session.signIn("kuplu", PASSWORD)

        val stored = SessionStore(documents).load(home.id, NOW)
        assertEquals("kuplu", stored?.username)
        assertNotNull(stored)
    }

    @Test
    fun registeringIsTheSameThingAgainstADifferentStatus() = runTest {
        val session = sessionOver(answering(HttpStatusCode.Created, encode(signedIn)))

        session.register("kuplu", PASSWORD)

        assertEquals(player, session.player)
    }

    /**
     * A refusal is state the form renders, not an exception it has to survive.
     *
     * And nothing is stored: a failed sign-in that left a token behind would be a launch that
     * restores a session the player never got.
     */
    @Test
    fun aRefusedSignInBecomesAFailureAndStoresNothing() = runTest {
        val documents = InMemoryDocumentStore()
        val session = sessionOver(
            answering(
                HttpStatusCode.Unauthorized,
                """{"error":"INVALID_CREDENTIALS","detail":"no"}""",
            ),
            documents,
        )

        session.signIn("kuplu", PASSWORD)

        assertNull(session.player)
        assertFalse(session.isBusy)
        assertNotNull(session.failure)
        assertNull(SessionStore(documents).load(home.id, NOW))
    }

    @Test
    fun anUnreachableServerIsAFailureAndNotAThrow() = runTest {
        val session = sessionOver(MockEngine { error("connection refused") })

        session.signIn("kuplu", PASSWORD)

        assertTrue(session.failure is AccountResult.Offline, "was ${session.failure}")
    }

    /**
     * The reason signing in is where the drain happens.
     *
     * A player who played offline has transcripts queued and no token to submit them with; signing
     * in is the moment they become creditable, and the dashboard they land on should already show
     * what they paid.
     */
    @Test
    fun signingInDrainsWhatWasQueuedOffline() = runTest {
        val reporter = RecordingReporter()
        val engine = answering(HttpStatusCode.OK, encode(signedIn))
        val session = sessionOver(engine, reporter = reporter)

        session.signIn("kuplu", PASSWORD)

        assertEquals(listOf(accountQueueKey(home.id, "kuplu")), reporter.drained)
    }

    @Test
    fun aRefusedSignInDrainsNothing() = runTest {
        val reporter = RecordingReporter()
        val session = sessionOver(
            answering(
                HttpStatusCode.Unauthorized,
                """{"error":"INVALID_CREDENTIALS","detail":""}""",
            ),
            reporter = reporter,
        )

        session.signIn("kuplu", PASSWORD)

        assertTrue(reporter.drained.isEmpty())
    }

    // ---- Coming back ------------------------------------------------------

    @Test
    fun aStoredSessionRestoresTheProfileWithoutASignIn() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        val session = sessionOver(answering(HttpStatusCode.OK, encode(player)), documents)

        session.restore()

        assertEquals(player, session.player)
        assertTrue(session.isRestored)
    }

    /**
     * "We have not looked yet" and "nobody is signed in" are different, and only one of them should
     * put a sign-in form in front of a player who has a perfectly good stored session.
     */
    @Test
    fun restoringWithNothingStoredStillFinishes() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(player)))

        assertFalse(session.isRestored)
        session.restore()

        assertNull(session.player)
        assertTrue(session.isRestored)
    }

    /** An expired token is not sent at all — [SessionStore] refuses to hand it back. */
    @Test
    fun anExpiredStoredSessionIsNotUsed() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = NOW - 1))
        var asked = false
        val engine = MockEngine {
            asked = true
            respondJson(HttpStatusCode.OK, encode(player))
        }
        val session = sessionOver(engine, documents)

        session.restore()

        assertNull(session.player)
        assertFalse(asked, "an expired token was sent to the server")
    }

    /**
     * A token the server no longer honours is thrown away rather than kept to be retried.
     *
     * It will not start working, and keeping it means a needless round trip before the sign-in form
     * on every launch from here on.
     */
    @Test
    fun aRejectedTokenIsCleared() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        val session = sessionOver(
            answering(HttpStatusCode.Unauthorized, """{"error":"UNAUTHENTICATED","detail":""}"""),
            documents,
        )

        session.restore()

        assertNull(SessionStore(documents).load(home.id, NOW))
        assertNull(session.failure, "nobody asked for this request, so nobody is shown its failure")
    }

    /** But an unreachable server keeps it: the session is probably still fine. */
    @Test
    fun anOfflineRestoreKeepsTheStoredToken() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        val session = sessionOver(MockEngine { error("connection refused") }, documents)

        session.restore()

        assertNotNull(SessionStore(documents).load(home.id, NOW))
        assertTrue(session.isRestored)
    }

    // ---- The name offered back --------------------------------------------

    /**
     * The case the whole thing is for: the token is gone, and the name is not.
     *
     * A player coming back after thirty days has to type a password, and there is no reason for
     * them to have to type who they are as well — the app knows. This is the test that would fail
     * if [SessionStore.lastUsername] were ever simplified into a call to `load`, which discards the
     * document at exactly this moment.
     */
    @Test
    fun anExpiredSessionStillRemembersWhoItWas() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = NOW - 1))
        val session = sessionOver(answering(HttpStatusCode.OK, encode(player)), documents)

        session.restore()

        assertNull(session.player, "an expired token must not sign anybody in")
        assertEquals("kuplu", session.lastUsername)
    }

    @Test
    fun signingInIsWhatMakesTheNameWorthOfferingBack() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(signedIn)))

        assertNull(session.lastUsername)
        session.signIn("kuplu", PASSWORD)

        assertEquals("kuplu", session.lastUsername)
    }

    /** Nothing stored, nothing to offer — and no empty string pretending to be a name. */
    @Test
    fun aFirstRunHasNoNameToOffer() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(player)))

        session.restore()

        assertNull(session.lastUsername)
    }

    /**
     * Each server remembers its own, and a switch does not carry one across.
     *
     * The same separation the token has, in the one place a player would actually believe it: a
     * name in the field is a claim about which account this host knows.
     */
    @Test
    fun theNameDoesNotFollowThePlayerToAnotherServer() = runTest {
        val documents = InMemoryDocumentStore()
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(signedIn)),
            documents = documents,
            servers = listOf(home, away),
        )
        session.signIn("kuplu", PASSWORD)

        session.useServer(away)

        assertNull(session.lastUsername)
    }

    // ---- Signing out ------------------------------------------------------

    /**
     * Locally first, and the server's answer is ignored.
     *
     * A sign-out a dead network could refuse would be a button that sometimes does nothing.
     */
    @Test
    fun signingOutWorksEvenWhenTheServerCannotBeReached() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        val session = sessionOver(answering(HttpStatusCode.OK, encode(signedIn)), documents)
        session.restore()

        val offline = sessionOver(MockEngine { error("connection refused") }, documents)
        offline.signOut()

        assertNull(offline.player)
        assertNull(SessionStore(documents).load(home.id, NOW))
    }

    /**
     * And it forgets the name, which an expiry deliberately does not.
     *
     * The asymmetry is the point. An expired token is the app forgetting on a schedule; signing out
     * is a person asking to be forgotten, usually because somebody else is about to hold the
     * device. Leaving their name in the field would answer a question nobody asked.
     */
    @Test
    fun signingOutForgetsTheNameToo() = runTest {
        val documents = InMemoryDocumentStore()
        val session = sessionOver(answering(HttpStatusCode.OK, encode(signedIn)), documents)
        session.signIn("kuplu", PASSWORD)
        assertEquals("kuplu", session.lastUsername)

        session.signOut()

        assertNull(session.lastUsername)
        assertNull(SessionStore(documents).lastUsername(home.id))
    }

    // ---- Changes made outside a match -------------------------------------

    /**
     * A purchase shows immediately and is sent afterwards.
     *
     * The shop has already told the player the card is theirs; a round trip is not something to
     * make them watch, and the local copy is the one the client keeps working from either way.
     */
    @Test
    fun persistingShowsTheChangeBeforeTheServerHasAnswered() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        var seen: HttpRequestData? = null
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(signedIn), record = { seen = it }),
            documents,
        )
        session.signIn("kuplu", PASSWORD)

        session.persist(GameSave(username = "kuplu", mgp = 10))

        assertEquals(10, session.save?.mgp)
        assertEquals("Bearer $TOKEN", seen?.headers?.get("Authorization"))
    }

    /** A failed store is logged and left; the player keeps what they bought on screen. */
    @Test
    fun aFailedProfileStoreDoesNotUndoWhatIsOnScreen() = runTest {
        val documents = InMemoryDocumentStore()
        val session = sessionOver(answering(HttpStatusCode.OK, encode(signedIn)), documents)
        session.signIn("kuplu", PASSWORD)

        val offline = sessionOver(MockEngine { error("connection refused") }, documents)
        offline.restore()
        offline.adopt(player)
        offline.persist(GameSave(username = "kuplu", mgp = 10))

        assertEquals(10, offline.save?.mgp)
    }

    /** With nobody signed in there is nothing to change and nothing to send. */
    @Test
    fun persistingWithNoPlayerIsANoOp() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(signedIn)))

        session.persist(GameSave(username = "kuplu", mgp = 10))

        assertNull(session.player)
    }

    @Test
    fun theQueueKeyFollowsTheSignedInAccount() = runTest {
        val session = sessionOver(answering(HttpStatusCode.OK, encode(signedIn)))

        assertNull(session.queueKey)
        session.signIn("kuplu", PASSWORD)

        assertEquals(accountQueueKey(home.id, "kuplu"), session.queueKey)
    }

    // ---- Switching servers ------------------------------------------------

    /**
     * Moving to another server signs the player out of the one they left.
     *
     * The token is only valid where it was issued and the character it names does not exist
     * elsewhere, so carrying either across would show one server's profile while every request went
     * to another — a state that looks like it is working right up until a match is submitted.
     */
    @Test
    fun switchingServersDropsTheSignedInPlayer() = runTest {
        val documents = InMemoryDocumentStore()
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(signedIn)),
            documents = documents,
            servers = listOf(home, away),
        )
        session.signIn("kuplu", PASSWORD)
        assertNotNull(session.player)

        // The mock answers `/me` with a `Session`, which is not a `PlayerState`, so the restore on
        // the far side finds nothing — which is exactly the case being pinned: an account on one
        // server is not an account on the other.
        assertTrue(session.useServer(away))

        assertNull(session.player)
        assertEquals(away.id, session.serverId)
    }

    /** And the session it left behind is still there, so coming back is free. */
    @Test
    fun theSessionOnTheServerYouLeftSurvives() = runTest {
        val documents = InMemoryDocumentStore()
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(signedIn)),
            documents = documents,
            servers = listOf(home, away),
        )
        session.signIn("kuplu", PASSWORD)

        session.useServer(away)

        assertNotNull(SessionStore(documents).load(home.id, NOW))
        assertNull(SessionStore(documents).load(away.id, NOW))
    }

    /** Choosing the server you are already on does nothing at all. */
    @Test
    fun reselectingTheCurrentServerIsNotASwitch() = runTest {
        val session = sessionOver(
            answering(HttpStatusCode.OK, encode(signedIn)),
            servers = listOf(home, away),
        )
        session.signIn("kuplu", PASSWORD)

        assertFalse(session.useServer(home))
        assertNotNull(session.player)
    }

    // ---- Fixtures ---------------------------------------------------------

    private class RecordingReporter : MatchReporter {
        val drained = mutableListOf<String>()

        override suspend fun report(profileKey: String, transcript: MatchTranscript) = Unit
        override suspend fun drain(profileKey: String) {
            drained += profileKey
        }

        override suspend fun forget(profileKey: String) = Unit
    }

    private fun sessionOver(
        engine: MockEngine,
        documents: InMemoryDocumentStore = InMemoryDocumentStore(),
        reporter: MatchReporter = MatchReporter.None,
        servers: List<ServerEntry> = listOf(home),
    ): AccountSession {
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        return AccountSession(
            server = connectionOver(http, documents, reporter, servers),
            clock = FixedClock(millis = NOW),
        )
    }

    /**
     * A connection over one server, which is what every test here but [useServer] wants.
     *
     * The directory is real rather than mocked: it is the thing that decides which id the session
     * and the queue are keyed by, and a stub would let this file agree with itself about a key the
     * app would derive differently.
     */
    private fun connectionOver(
        http: HttpClient,
        documents: InMemoryDocumentStore,
        reporter: MatchReporter,
        servers: List<ServerEntry> = listOf(home),
    ): ServerConnection {
        val directory = ServerDirectory(InMemoryDocumentStore(), servers)
        return ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            pvp = PvpClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(documents),
            // The same store the test reads, so "the stock was written down" is answerable.
            tickets = TicketStore(documents),
            probe = ServerProbe(http) { NOW },
            reporter = reporter,
        )
    }

    /**
     * Signed in, with [onTickets] deciding what the seed endpoint answers.
     *
     * `/me` has to succeed for any of these to mean anything: the stock is keyed on the account,
     * and there is no account until `restore` has read one. So the engine answers the profile and
     * routes only the ticket call to the test.
     */
    private fun ticketEngine(
        onTickets: MockRequestHandleScope.() -> HttpResponseData,
    ) = MockEngine { request ->
        if (request.url.encodedPath.endsWith("/matches/tickets")) {
            onTickets()
        } else {
            respondJson(HttpStatusCode.OK, encode(player))
        }
    }

    private fun answering(
        status: HttpStatusCode,
        body: String,
        record: (HttpRequestData) -> Unit = {},
    ) = MockEngine { request ->
        record(request)
        respondJson(status, body)
    }

    /** The mock engine's `respond` with the one header Ktor's negotiation needs to decode. */
    private fun MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private inline fun <reified T> encode(value: T) = matchProtocolJson.encodeToString(value)

    /** The one server every test but the switch runs against. */
    private val home = ServerEntry.of(BASE_URL, label = "Home")

    /** Somewhere else, for the switch. */
    private val away = ServerEntry.of("http://127.0.0.1:9090", label = "Away")

    private fun stored(expiresAt: Long) =
        StoredSession(token = TOKEN, expiresAt = expiresAt, username = "kuplu")

    /**
     * A session with a live token already stored, for the calls that need one to do anything.
     *
     * Planted rather than signed in, so that the mock engine answers the call under test instead of
     * a sign-in the test does not care about.
     */
    private suspend fun signedInSession(engine: MockEngine): AccountSession {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(home.id, stored(expiresAt = LATER))
        return sessionOver(engine, documents)
    }

    private val player = PlayerState(save = GameSave(username = "kuplu", mgp = 4200))

    private val signedIn = Session(token = TOKEN, expiresAt = LATER, player = player)

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"
        const val NOW = 1_770_000_000_000L
        const val LATER = NOW + 86_400_000L

        /** Opaque to the client and meaningless to the mock — it only has to arrive. */
        const val TOKEN = "test-session"

        /** Never a real one, and never printed — as in the server's own `AccountFlowTest`. */
        const val PASSWORD = "not-a-real-password"
    }
}
