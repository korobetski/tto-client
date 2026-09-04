package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.Inventory
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.net.AccountClient
import com.tripletriad.net.AuctionClient
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.PveClient
import com.tripletriad.net.PvpClient
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerProbe
import com.tripletriad.net.SessionStore
import com.tripletriad.net.StoredSession
import com.tripletriad.net.TicketStore
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.protocol.BagItemRequest
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ItemUsed
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.SeedTickets
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.effect
import com.tripletriad.storage.InMemoryDocumentStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AccountSettlementTest {

    @Test
    fun aCardTheServerCreditedCanBeUsed() = runComposeUiTest {
        val sessions = signedInStore()
        // The order the app meets these in, and the whole point of the fixture: `GET /me` on
        // launch answers the profile as it was *before* the queued match was judged — no drop in
        // the bag — and the drop only exists once the drain has submitted the transcript and the
        // server has replayed it. A client that ignores what the drain credited never sees it.
        val reporter = CreditingReporter {
            stored = stored.copy(save = Inventory.add(stored.save, CardItem(WON_CARD)))
            stored
        }

        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), server = connection(sessions, reporter))
        }
        openTheBag()

        onNodeWithTag(inventoryRowTestTag(CardItem(WON_CARD))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { stored.save.ownsCard(WON_CARD) }

        assertEquals(listOf(accountKey), reporter.drained, "the queue was not drained at launch")
        assertTrue(
            stored.save.bag.none { it is CardItem && it.cardId == WON_CARD },
            "the card was added but not spent: ${stored.save.bag}",
        )
    }

    /**
     * The queue is drained at launch and again on the way off a board, never from one.
     *
     * Driven against [PveStubServer] rather than this file's own engine, because reaching a board
     * now means reaching a referee: a match is a row on the server, and an `App` answering no
     * `/pve/matches` cannot open one. The claim is unchanged — `MatchSettlement` keys on whether a
     * board is up, so it fires at launch and on each exit and not in between.
     */
    @Test
    fun leavingABoardDrainsWhatItLeftBehind() = runComposeUiTest {
        val reporter = CreditingReporter { null }
        val server = PveStubServer(reporter = reporter)

        setContent {
            TestApp(store = settingsFor(AppLocale.EN_US), server = server.connection)
        }
        openDashboard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { reporter.drained.size == 1 }

        openOpponents()
        challenge()
        assertEquals(1, reporter.drained.size, "a board is not the place to drain from")

        leaveMatch()
        awaitOpponents()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { reporter.drained.size == 2 }
        assertEquals(2, reporter.drained.size)
        assertTrue(
            reporter.drained.all { it.contains(PveStubServer.NAME) },
            "both drains should be this account's: ${reporter.drained}",
        )
    }

    // ---- Harness -----------------------------------------------------------

    private fun ComposeUiTest.openTheBag() {
        openDashboard()
        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_LIST_TEST_TAG)
    }

    private class CreditingReporter(private val credit: () -> PlayerState?) : MatchReporter {
        val drained = mutableListOf<String>()

        override suspend fun report(profileKey: String, transcript: MatchTranscript) = Unit
        override suspend fun drain(profileKey: String): PlayerState? {
            drained += profileKey
            return credit()
        }

        override suspend fun forget(profileKey: String) = Unit
    }

    private fun signedInStore(): InMemoryDocumentStore {
        val documents = InMemoryDocumentStore()
        runBlocking {
            SessionStore(documents).save(
                home.id,
                StoredSession(token = "t", expiresAt = Long.MAX_VALUE, username = NAME),
            )
        }
        stored = PlayerState(save = GameSave.new(username = NAME, createdAt = 0L))
        return documents
    }

    private fun connection(
        sessions: InMemoryDocumentStore,
        reporter: MatchReporter,
    ): ServerConnection {
        val http = HttpClient(server()) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), listOf(home))
        return ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            pvp = PvpClient(http, baseUrl = { directory.selected.baseUrl }),
            pve = PveClient(http, baseUrl = { directory.selected.baseUrl }),
            auctions = AuctionClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(sessions),
            tickets = TicketStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = reporter,
        )
    }

    private fun server() = MockEngine { request ->
        when (request.url.encodedPath) {
            "/server" -> respondJson(matchProtocolJson.encodeToString(serverInfo))

            // An empty stock, answered explicitly so the two tests below are not at the mercy of
            // what an unrelated route happens to decode as. No seed means no board — see
            // `NoSeedNotice`, which `leavingABoardDrainsWhatItLeftBehind` navigates through.
            "/me/tickets" -> respondJson(matchProtocolJson.encodeToString(SeedTickets()))

            "/me/save" -> {
                val sent = matchProtocolJson.decodeFromString<GameSave>(
                    request.body.toByteArray().decodeToString(),
                )
                stored = stored.copy(save = sent.withServerOwnedFrom(stored.save))
                respond("", HttpStatusCode.NoContent)
            }

            "/me/bag/use" -> {
                val body = matchProtocolJson.decodeFromString<BagItemRequest>(
                    request.body.toByteArray().decodeToString(),
                )
                val used = Inventory.use(stored.save, body.item, Random(SEED))
                stored = stored.copy(save = used.save)
                respondJson(matchProtocolJson.encodeToString(ItemUsed(stored, used.effect())))
            }

            else -> respondJson(matchProtocolJson.encodeToString(stored))
        }
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private var stored: PlayerState = PlayerState(save = GameSave.new(createdAt = 0L))

    private val home = ServerEntry(id = "home", label = "Home", baseUrl = "https://example.invalid")

    private val accountKey: String get() = com.tripletriad.net.accountQueueKey(home.id, NAME)

    private val serverInfo = ServerInfo(
        name = "test",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private companion object {
        const val NAME = "winner"

        const val WON_CARD = 300
        const val SEED = 7
    }
}
