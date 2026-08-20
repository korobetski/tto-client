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
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import com.tripletriad.net.AccountClient
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AccountBagUiTest {

    private val catalog = runBlocking { loadCardCatalog() }
    private val formats = runBlocking { loadFormatCatalog() }
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    @Test
    fun openingAPackKeepsTheRestOfTheBag() = runComposeUiTest {
        val session = signedIn()
        setContent { Fixture(session) }

        onNodeWithTag(inventoryRowTestTag(BoosterItem(PACK))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PACK_REVEAL_TEST_TAG) }

        val bag = session.save?.bag.orEmpty()
        assertTrue(
            bag.any { it is PotionItem && it.potionType == PotionType.MGP && it.stack == POTIONS },
            "the potions did not survive the pack: $bag",
        )
        assertTrue(
            bag.filterIsInstance<CardItem>().any { it.cardId == KEPT_CARD && it.stack == CARDS },
            "the card stack did not survive the pack: $bag",
        )
        assertTrue(
            bag.none { it is BoosterItem },
            "the pack was opened and stayed in the bag: $bag",
        )
    }

    @Test
    fun openingAPackAddsWhatItDealt() = runComposeUiTest {
        val session = signedIn()
        setContent { Fixture(session) }

        onNodeWithTag(inventoryRowTestTag(BoosterItem(PACK))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PACK_REVEAL_TEST_TAG) }

        val cardStacks = session.save?.bag.orEmpty().filterIsInstance<CardItem>().sumOf { it.stack }
        assertTrue(cardStacks > CARDS, "a pack was opened and dealt nothing: $cardStacks")
    }

    @Test
    fun theBagComesBackAfterTheReveal() = runComposeUiTest {
        val session = signedIn()
        setContent { Fixture(session) }

        onNodeWithTag(inventoryRowTestTag(BoosterItem(PACK))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PACK_REVEAL_TEST_TAG) }

        var taps = 0
        while (exists(PACK_REVEAL_TEST_TAG) && taps < REVEAL_TAP_LIMIT) {
            onNodeWithTag(PACK_REVEAL_ACTION_TEST_TAG).performClick()
            waitForIdle()
            taps++
        }

        val bag = session.save?.bag.orEmpty()
        assertEquals(
            bag.size,
            bag.count { exists(inventoryRowTestTag(it)) },
            "the bag holds ${bag.size} entries and the screen drew fewer: $bag",
        )
    }

    // ---- Harness -----------------------------------------------------------

    @Composable
    private fun Fixture(session: AccountSession) {
        val gate = rememberAccountGate(session)
        val profile = gate.profile ?: return

        CompositionLocalProvider(LocalStrings provides strings) {
            TripleTriadTheme {
                Box(modifier = Modifier.size(WIDTH, HEIGHT)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        InventoryBody(
                            profile = profile,
                            catalog = catalog,
                            format = formats.default!!,
                            onUse = gate.useItem,
                            onIntent = gate.perform,
                            onUnlocked = {},
                        )
                    }
                }
            }
        }
    }

    private fun signedIn(): AccountSession {
        stored = PlayerState(
            save = GameSave.new(createdAt = 0L).copy(
                username = NAME,
                bag = Inventory.addAll(
                    GameSave.new(createdAt = 0L),
                    listOf(
                        BoosterItem(PACK),
                        CardItem(cardId = KEPT_CARD, stack = CARDS),
                        PotionItem(PotionType.MGP, stack = POTIONS),
                    ),
                ).bag,
            ),
        )

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
            }
        }
    }

    private fun server() = MockEngine { request ->
        when (request.url.encodedPath) {
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
        const val NAME = "bagger"
        const val KEPT_CARD = 300
        const val CARDS = 2
        const val POTIONS = 3
        const val SEED = 7
        val PACK = BoosterType.BRONZE

        const val REVEAL_TAP_LIMIT = 20

        val WIDTH = 360.dp
        val HEIGHT = 640.dp
    }
}
