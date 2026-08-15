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
import com.tripletriad.net.MatchReporter
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

/**
 * What a submitted match credits, reaching the screen the player is looking at.
 *
 * ### The bug this is the fixed half of
 *
 * A match against a program is credited by the **client** — see `MatchScreen` — and on an account
 * that write goes out as a `PUT /me/save`, which the server applies through
 * `GameSave.withServerOwnedFrom`: it keeps its own `bag`, `cards` and `mgp` and discards the
 * client's. So a card the opponent dropped exists only in the copy on screen until the match's
 * transcript has been submitted and replayed.
 *
 * Nothing closed that window. The drain ran once, when a character first came into play, and the
 * profile it credited was handed to a constructor callback **no host ever passed** — so the client
 * and the server disagreed about the bag for the rest of the session. Tapping Use on the drop asked
 * the server to spend an item it did not hold; the row vanished, the collection gained nothing, and
 * the screen said nothing at all.
 *
 * `AccountBagSyncTest` covers the screen's half — that the refusal is now spoken aloud. This covers
 * the half that matters more: that the disagreement is settled before the player can reach the bag.
 */
@OptIn(ExperimentalTestApi::class)
class AccountSettlementTest {

    /**
     * The reported story, end to end: a card won from an opponent can be used.
     *
     * The queue is drained at launch and again on the way off any board, and what it credits is
     * adopted — so the bag on screen is the bag the server holds, and Use spends something that
     * really is there.
     */
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
            App(store = settingsFor(AppLocale.EN_US), server = connection(sessions, reporter))
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
     * Leaving a board drains again, which is the case a launch-only drain could not serve.
     *
     * The board here never deals a card: this account holds no seed stock, so `MatchScreen` shows
     * `NoSeedNotice` instead. That is deliberate rather than a shortcut — what is under test is the
     * **navigation**, not the match. `Screen.MATCH` is a board as far as `PLAYING_SCREENS` is
     * concerned, so walking into one and back out is exactly the transition a finished match makes,
     * without nine placements in front of it.
     */
    @Test
    fun leavingABoardDrainsWhatItLeftBehind() = runComposeUiTest {
        val sessions = signedInStore()
        val reporter = CreditingReporter { null }

        setContent {
            App(store = settingsFor(AppLocale.EN_US), server = connection(sessions, reporter))
        }
        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        awaitDashboard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { reporter.drained.size == 1 }

        openOpponents()
        scrollToOpponent(TEST_OPPONENT)
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(NO_SEEDS_TEST_TAG) }
        assertEquals(1, reporter.drained.size, "a board is not the place to drain from")

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitOpponents()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { reporter.drained.size == 2 }
        assertEquals(listOf(accountKey, accountKey), reporter.drained)
    }

    // ---- Harness -----------------------------------------------------------

    private fun ComposeUiTest.openTheBag() {
        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        awaitDashboard()
        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_LIST_TEST_TAG)
    }

    /**
     * A reporter that records every drain and answers with whatever [credit] says the server wrote.
     *
     * A lambda rather than a fixed profile, so a test can make the drain **be** the moment the
     * account gains something — which is what a real one is.
     */
    private class CreditingReporter(private val credit: () -> PlayerState?) : MatchReporter {
        val drained = mutableListOf<String>()

        override suspend fun report(profileKey: String, transcript: MatchTranscript) = Unit
        override suspend fun drain(profileKey: String): PlayerState? {
            drained += profileKey
            return credit()
        }

        override suspend fun forget(profileKey: String) = Unit
    }

    /** A stored session, so Play is Continue and the sign-in form never appears. */
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
            session = SessionStore(sessions),
            tickets = TicketStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = reporter,
        )
    }

    /** `AccountRoutes`, minus the socket. */
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

    /** The key this account's queue is drained under — server id and account name. */
    private val accountKey: String get() = com.tripletriad.net.accountQueueKey(home.id, NAME)

    /** Healthy and of exactly this version, so no update notice replaces anything. */
    private val serverInfo = ServerInfo(
        name = "test",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private companion object {
        const val NAME = "winner"

        /** An ff14 card no starter collection holds, so owning it can only come from the use. */
        const val WON_CARD = 300
        const val SEED = 7
    }
}
