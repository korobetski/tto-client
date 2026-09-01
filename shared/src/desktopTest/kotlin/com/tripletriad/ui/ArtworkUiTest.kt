package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.XpTable
import com.tripletriad.protocol.Unlocks
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ArtworkUiTest {
    private val art = runBlocking { loadUiArt() }

    @Test
    fun theRecordShowsTheCharactersAvatar() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_STATS_TEST_TAG, STATS_TABLE_TEST_TAG)

        assertTrue(existsUnmerged(AVATAR_TEST_TAG), "the record should show the avatar")
    }

    @Test
    fun theCollectionGridDrawsThumbnails() = runComposeUiTest {
        val card = runBlocking { loadCardCatalog() }.all
            .first { it.id == STARTER_CARDS.first() }
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        assertTrue(
            existsUnmerged(thumbTestTag(card.textureId)),
            "the grid should draw ${card.name} as its thumbnail",
        )
    }

    @Test
    fun theOpponentListShowsPortraits() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()
        scrollToOpponent(TEST_OPPONENT)

        assertTrue(
            existsUnmerged(portraitTestTag(TEST_OPPONENT)),
            "the row for $TEST_OPPONENT should carry its portrait",
        )
    }

    @Test
    fun aDeckSlotFillsWithAThumbnailWhenACardIsPicked() = runComposeUiTest {
        val first = STARTER_CARDS.first()
        val card = runBlocking { loadCardCatalog() }.all.first { it.id == first }
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        onNodeWithTag(deckPickTestTag(first)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(thumbTestTag(card.textureId)) }
    }

    /**
     * The auction house is given the card art, like every other room that draws a card.
     *
     * `LocalCardArt` used to be provided screen by screen and the house was left off the list, so
     * a lectern drew a colour quad where the card should be and the consignment picker offered
     * element chips with no elements on them — `CardTypeBadge` and `CardFace`'s layers both draw
     * *nothing* when the sheet is missing, which is why this asserts on a badge that only exists
     * when the art arrived.
     *
     * It has to walk in through the lobby: the bodies' own tests provide the art themselves, so
     * nothing below the app can see a provider that was never written. The clock is stopped on
     * the way in because `AuctionSession.watch` is a poll that never finishes — see
     * `AuctionUiTest`, which stops it for the same reason.
     */
    @Test
    fun theAuctionHouseIsGivenTheCardArt() = runComposeUiTest {
        val elemental = runBlocking { loadCardCatalog() }.all.first { it.type != null }
        // Seeded on the server, because that is where a profile lives once there is one — and
        // there has to be one here: the house is level-gated, and it is a room with nobody in it
        // without a counterparty. Two copies and no decks, so one is spare and the consignment
        // tab is a form rather than its empty note.
        val server = PveStubServer(
            save = freshSave().copy(
                xp = XpTable.thresholdFor(Unlocks.DEFAULT_AUCTION),
                level = Unlocks.DEFAULT_AUCTION,
                cards = mapOf(elemental.id to 2),
                decks = emptyList(),
            ),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = server.connection) }
        openDashboard()

        mainClock.autoAdvance = false
        onNodeWithTag(DASHBOARD_AUCTION_TEST_TAG).performScrollTo().performClick()
        mainClock.advanceTimeBy(A_SECOND)
        onNodeWithTag(screenTabTestTag("auction-sell")).performClick()
        mainClock.advanceTimeByFrame()

        assertTrue(
            existsUnmerged(cardTypeTestTag(elemental.id)),
            "the consignment desk drew ${elemental.name} with no element on it",
        )
    }

    @Test
    fun anOpponentWithNoPortraitIsDrawnAsAMonogram() = runComposeUiTest {
        val npc = runBlocking { loadNpcCatalog() }.all.first { it.iconId == "jack" }

        setContent {
            CompositionLocalProvider(LocalUiArt provides art) {
                TripleTriadTheme {
                    NpcPortrait(npc = npc, name = "Jack")
                }
            }
        }

        waitForIdle()
        assertTrue(existsUnmerged(portraitTestTag("jack")), "the plate is drawn either way")
        assertVisible("J", "a portrait-less opponent shows their initial")
    }

    private companion object {
        const val A_SECOND = 1_000L
    }
}
