package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Inventory
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
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
import com.tripletriad.protocol.ItemEffect
import com.tripletriad.protocol.ItemUsed
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.effect
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.TripleTriadTheme
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
import kotlin.test.assertTrue

/**
 * Using a bag item **the server does not agree the account holds**.
 *
 * ### The state this is about, and why it is ordinary rather than exotic
 *
 * A match against an NPC is credited by the client — `MatchScreen` calls `MatchRewards.credit` and
 * hands the result to `ProfileGate.persist` — and on an account that write is a `PUT /me/save`,
 * which the server applies through `GameSave.withServerOwnedFrom`: **`bag`, `cards` and `mgp` are
 * taken from the stored profile and the client's are discarded.** The drop the player was just
 * shown therefore exists only in the copy on screen until the match's transcript has been submitted
 * and replayed.
 *
 * So the bag on screen can legitimately list a card the server's bag does not, and the player who
 * taps Use on it is asking for something the server will refuse. The refusal is
 * [com.tripletriad.protocol.ItemEffect.NotUseable].
 *
 * ### What is asserted
 *
 * Not that the use succeeds — it cannot, and the server is right to refuse. What the screen owes
 * the player is an **answer**: either the card is in the collection, or they are told why it is
 * not. A tap that silently removes the row and adds nothing is the shape the bug was reported in.
 */
@OptIn(ExperimentalTestApi::class)
class AccountBagSyncTest {

    private val catalog = runBlocking { loadCardCatalog() }
    private val formats = runBlocking { loadFormatCatalog() }
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    /**
     * The reported bug: Use on a card the server has never heard of does nothing and says nothing.
     */
    @Test
    fun usingACardTheServerHasNotCreditedYetSaysSo() = runComposeUiTest {
        val session = signedIn()
        setContent { Fixture(session) }

        onNodeWithTag(inventoryRowTestTag(CardItem(WON_CARD))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitForIdle()

        val owned = session.save?.ownsCard(WON_CARD) == true
        assertTrue(
            owned || exists(INVENTORY_NOTE_TEST_TAG),
            "Use neither added the card nor said why not — the bag is now ${session.save?.bag}",
        )
    }

    /**
     * And a use that could not be attempted at all says a **different** thing.
     *
     * Null from the gate is not a refusal: it is no session, or a request that never came back. The
     * player's next move differs — try again rather than try something else — so the two answers
     * are two sentences and neither of them is silence. See `ProfileGate.useItem`, whose null this
     * is, and which the screen used to treat as an early return.
     */
    @Test
    fun aUseThatCouldNotBeAttemptedSaysSoToo() = runComposeUiTest {
        val profile = Inventory.add(
            GameSave.new(username = NAME, createdAt = 0L),
            CardItem(WON_CARD),
        )
        setContent { Bag(profile, onUse = { null }) }

        onNodeWithTag(inventoryRowTestTag(CardItem(WON_CARD))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(INVENTORY_NOTE_TEST_TAG) }

        assertTrue(
            isVisible(strings[StringKeys.ACTION_FAILED]),
            "a use that never reached the server said nothing",
        )
    }

    // ---- Harness -----------------------------------------------------------

    @Composable
    private fun Fixture(session: AccountSession) {
        val gate = rememberAccountGate(session)
        val profile = gate.profile ?: return
        Bag(profile, onUse = gate.useItem, onIntent = gate.perform)
    }

    /** The bag alone, over whatever answer a use is to get. */
    @Composable
    private fun Bag(
        profile: GameSave,
        onUse: suspend (Item) -> ItemEffect?,
        onIntent: suspend (Intent) -> Unit = {},
    ) {
        CompositionLocalProvider(LocalStrings provides strings) {
            TripleTriadTheme {
                Box(modifier = Modifier.size(WIDTH, HEIGHT)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        InventoryBody(
                            profile = profile,
                            catalog = catalog,
                            format = formats.default!!,
                            onUse = onUse,
                            onIntent = onIntent,
                            onUnlocked = {},
                        )
                    }
                }
            }
        }
    }

    /**
     * A signed-in account whose bag is empty on the server, and whose **client** copy holds the
     * card a match against an NPC just dropped.
     *
     * Built the way the app builds it rather than by assignment: `persist` is the call
     * `MatchScreen` makes with the credited profile, and the stand-in server discards what a real
     * one discards. So the divergence here is produced by the same two lines that produce it on a
     * device.
     */
    private fun signedIn(): AccountSession {
        stored = PlayerState(save = GameSave.new(username = NAME, createdAt = 0L))

        val http = HttpClient(server()) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), listOf(home))
        val sessions = InMemoryDocumentStore()
        val connection = ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            pvp = PvpClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(sessions),
            tickets = TicketStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = MatchReporter.None,
        )
        return AccountSession(connection, FixedClock(0L)).also {
            runBlocking {
                connection.session.save(
                    home.id,
                    StoredSession(token = "t", expiresAt = Long.MAX_VALUE, username = NAME),
                )
                it.restore()
                // What a won match leaves behind: the client's own credit, written through the
                // gate. The server keeps the `bag` it already had — see `withServerOwnedFrom`.
                it.persist(Inventory.add(it.save!!, CardItem(WON_CARD)))
            }
        }
    }

    /** `AccountRoutes`, minus the socket: the three routes this flow touches. */
    private fun server() = MockEngine { request ->
        when (request.url.encodedPath) {
            // The real route's own rule: everything a match decides is taken from the stored
            // profile, whatever the client sent. See `GameSave.withServerOwnedFrom`.
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

    private companion object {
        const val NAME = "winner"

        /** An ff14 card no starter collection holds, so owning it can only come from the use. */
        const val WON_CARD = 300
        const val SEED = 7

        val WIDTH = 360.dp
        val HEIGHT = 640.dp
    }
}
