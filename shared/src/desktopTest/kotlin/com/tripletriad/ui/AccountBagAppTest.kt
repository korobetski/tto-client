package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.Inventory
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
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
import com.tripletriad.protocol.PlayerState
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
class AccountBagAppTest {

    @Test
    fun openingAPackThroughTheAppKeepsTheRestOfTheBag() = runComposeUiTest {
        val sessions = signedInStore()
        setContent { App(store = settingsFor(AppLocale.EN_US), server = connection(sessions)) }

        openTheBag()
        onNodeWithTag(inventoryRowTestTag(BoosterItem(PACK))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PACK_REVEAL_TEST_TAG) }

        val bag = stored.save.bag
        assertTrue(
            bag.any { it is PotionItem && it.stack == POTIONS },
            "the potions did not survive the pack: $bag",
        )
        assertTrue(
            bag.filterIsInstance<CardItem>().any { it.cardId == KEPT_CARD && it.stack == CARDS },
            "the card stack did not survive the pack: $bag",
        )
        assertTrue(bag.none { it is BoosterItem }, "the pack survived being opened: $bag")
    }

    @Test
    fun theBagIsWholeOnScreenAfterTheReveal() = runComposeUiTest {
        val sessions = signedInStore()
        setContent { App(store = settingsFor(AppLocale.EN_US), server = connection(sessions)) }

        openTheBag()
        onNodeWithTag(inventoryRowTestTag(BoosterItem(PACK))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PACK_REVEAL_TEST_TAG) }

        var taps = 0
        while (exists(PACK_REVEAL_TEST_TAG) && taps < REVEAL_TAP_LIMIT) {
            onNodeWithTag(PACK_REVEAL_ACTION_TEST_TAG).performClick()
            waitForIdle()
            taps++
        }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(INVENTORY_LIST_TEST_TAG) }
        val bag = stored.save.bag
        assertEquals(
            bag.size,
            bag.count { exists(inventoryRowTestTag(it)) },
            "the bag holds ${bag.size} entries and the screen drew fewer: $bag",
        )
    }

    @Test
    fun openingAPackWritesNoProfileBack() = runComposeUiTest {
        val sessions = signedInStore()
        setContent { App(store = settingsFor(AppLocale.EN_US), server = connection(sessions)) }

        openTheBag()
        onNodeWithTag(inventoryRowTestTag(BoosterItem(PACK))).performClick()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PACK_REVEAL_TEST_TAG) }

        var taps = 0
        while (exists(PACK_REVEAL_TEST_TAG) && taps < REVEAL_TAP_LIMIT) {
            onNodeWithTag(PACK_REVEAL_ACTION_TEST_TAG).performClick()
            waitForIdle()
            taps++
        }
        waitForIdle()

        assertTrue(saves.isEmpty(), "the app wrote the profile back ${saves.size} time(s)")
    }

    // ---- Harness -----------------------------------------------------------

    private fun ComposeUiTest.openTheBag() {
        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        awaitDashboard()
        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_LIST_TEST_TAG)
    }

    private fun signedInStore(): InMemoryDocumentStore {
        val documents = InMemoryDocumentStore()
        runBlocking {
            SessionStore(documents).save(
                home.id,
                StoredSession(token = "t", expiresAt = Long.MAX_VALUE, username = NAME),
            )
        }
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
        saves.clear()
        return documents
    }

    private fun connection(sessions: InMemoryDocumentStore): ServerConnection {
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
            reporter = MatchReporter.None,
        )
    }

    private fun server() = MockEngine { request ->
        when (request.url.encodedPath) {
            "/server" -> respondJson(matchProtocolJson.encodeToString(serverInfo))

            "/me/save" -> {
                saves += request.body.toByteArray().decodeToString()
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

    private val saves = mutableListOf<String>()

    private val home = ServerEntry(id = "home", label = "Home", baseUrl = "https://example.invalid")

    private val serverInfo = ServerInfo(
        name = "test",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private companion object {
        const val NAME = "bagger"
        const val KEPT_CARD = 300
        const val CARDS = 2
        const val POTIONS = 3
        const val SEED = 7
        const val REVEAL_TAP_LIMIT = 20
        val PACK = BoosterType.BRONZE
    }
}
