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

/**
 * Opening a pack on an account, through the **whole app**.
 *
 * ### Why this exists beside `AccountBagUiTest`
 *
 * That one composes `InventoryBody` with a gate built by hand, which proves the screen and the
 * client agree with the server. It cannot prove what the *rest of the app* does while that happens
 * — and on a real device the bag is one tab inside a navigation bar, inside a shell that recomposes
 * on every profile change and holds writers of its own. A player reported losing items to exactly
 * this flow on a build where both halves above were already green, so the untested seam is the
 * shell, and this is that seam.
 *
 * ### Every write the app makes is watched
 *
 * The stand-in records `PUT /me/save` as well as serving it. That is the assertion that matters:
 * the bag is server-owned, so a stale profile written by some other screen cannot damage it *on a
 * current server* — but it would on an older one, and knowing whether the app makes such a write at
 * all is the difference between "safe" and "safe by accident".
 */
@OptIn(ExperimentalTestApi::class)
class AccountBagAppTest {

    /** The pack is opened, the rest of the bag survives, and the dealt cards arrive. */
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

    /**
     * And leaving the reveal draws every row the bag holds.
     *
     * The player's own claim, as an assertion about the **screen** rather than the profile.
     */
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

    /**
     * Nothing the app does afterwards writes the profile back.
     *
     * The one this was written to catch. A `PUT /me/save` carrying a profile from *before* the pack
     * is the only shape that loses items permanently, and it is invisible to every other test here
     * because the current server would refuse to apply the bag from it anyway. If the app ever
     * starts making that write, this fails — on the build where it is still harmless, rather than
     * on the deployment where it is not.
     */
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

    /** A stored session, so Play is Continue and the form never appears. */
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

    /** `AccountRoutes`, minus the socket — and a ledger of every profile written back. */
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

    /** Every body `PUT /me/save` received, so a test can assert there were none. */
    private val saves = mutableListOf<String>()

    private val home = ServerEntry(id = "home", label = "Home", baseUrl = "https://example.invalid")

    /** Healthy and of exactly this version, so no update notice replaces anything. */
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
