package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AccountBagSyncTest {

    private val catalog = runBlocking { loadCardCatalog() }
    private val formats = runBlocking { loadFormatCatalog() }
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    @Test
    fun usingACardTheServerHasNotCreditedYetSaysSo() = runComposeUiTest {
        val session = signedIn()
        setContent { Fixture(session) }

        onNodeWithTag(inventoryRowTestTag(CardItem(WON_CARD))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()

        // Use is a round trip here, and `waitForIdle` stood in this line's place settling nothing:
        // the coroutine suspends inside Ktor, off the dispatcher Compose tracks, so idle is reached
        // with the request still out and the assertion raced the engine. Delaying the mock's
        // `/me/bag/use` by 300 ms failed it on every run; every other test in this class already
        // waits on its outcome rather than on idleness.
        //
        // The wait *is* the assertion's condition, so a timeout is swallowed and the failure is
        // left to the assertion, which can name the bag it found.
        fun settled() =
            session.save?.ownsCard(WON_CARD) == true || exists(INVENTORY_NOTE_TEST_TAG)
        runCatching { waitUntil(timeoutMillis = UI_TIMEOUT_MS) { settled() } }

        assertTrue(
            settled(),
            "Use neither added the card nor said why not — the bag is now ${session.save?.bag}",
        )
    }

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

    @Test
    fun aSellTheServerRefusesSaysSo() = runComposeUiTest {
        setContent {
            Bag(withWonCard(), onUse = { null }, onIntent = { IntentOutcome.REFUSED })
        }

        sell()

        assertTrue(
            isVisible(strings[StringKeys.ITEM_REFUSED]),
            "a refused sale said nothing",
        )
    }

    @Test
    fun aSellThatCouldNotBeAttemptedSaysSoToo() = runComposeUiTest {
        setContent {
            Bag(withWonCard(), onUse = { null }, onIntent = { IntentOutcome.UNREACHABLE })
        }

        sell()

        assertTrue(
            isVisible(strings[StringKeys.ACTION_FAILED]),
            "a sale that never reached the server said nothing",
        )
    }

    @Test
    fun aSecondTapWhileTheFirstIsOutDoesNothing() = runComposeUiTest {
        val answered = CompletableDeferred<Unit>()
        var asked = 0
        var done = false
        setContent {
            Bag(
                withWonCard(),
                onUse = { null },
                onIntent = {
                    asked++
                    answered.await()
                    done = true
                    IntentOutcome.APPLIED
                },
            )
        }

        onNodeWithTag(inventoryRowTestTag(CardItem(WON_CARD))).performClick()
        onNodeWithTag(INVENTORY_SELL_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { asked == 1 }

        onNodeWithTag(INVENTORY_SELL_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(INVENTORY_SELL_TEST_TAG).performClick()
        waitForIdle()
        assertEquals(1, asked, "the second tap was sent as a second sale")

        // Released, so the flag is seen to be dropped rather than merely never raised: a button
        // that stayed dead after the answer came back would be the other half of this bug.
        answered.complete(Unit)
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { done }
        waitForIdle()
        onNodeWithTag(INVENTORY_SELL_TEST_TAG).assertIsEnabled()
    }

    // ---- Harness -----------------------------------------------------------

    private fun withWonCard(): GameSave = Inventory.add(
        GameSave.new(username = NAME, createdAt = 0L),
        CardItem(WON_CARD),
    )

    private fun ComposeUiTest.sell() {
        onNodeWithTag(inventoryRowTestTag(CardItem(WON_CARD))).performClick()
        onNodeWithTag(INVENTORY_SELL_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(INVENTORY_NOTE_TEST_TAG) }
    }

    @Composable
    private fun Fixture(session: AccountSession) {
        val gate = rememberAccountGate(session)
        val profile = gate.profile ?: return
        Bag(profile, onUse = gate.useItem, onIntent = gate.perform)
    }

    @Composable
    private fun Bag(
        profile: GameSave,
        onUse: suspend (Item) -> ItemEffect?,
        onIntent: suspend (Intent) -> IntentOutcome = { IntentOutcome.APPLIED },
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
            pve = PveClient(http, baseUrl = { directory.selected.baseUrl }),
            auctions = AuctionClient(http, baseUrl = { directory.selected.baseUrl }),
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

        const val WON_CARD = 300
        const val SEED = 7

        val WIDTH = 360.dp
        val HEIGHT = 640.dp
    }
}
