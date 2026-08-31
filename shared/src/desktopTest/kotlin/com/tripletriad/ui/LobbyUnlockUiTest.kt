package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.model.XpTable
import com.tripletriad.protocol.Unlocks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The level gate on the two things a second player can be cheated through.
 *
 * Multiplayer and the auction house are the places where one person holding two accounts stops
 * being their own business: a rigged PvP match moves rating, and a sale to yourself moves cards.
 * The gate is not a fix for that — see the note on [LocalUnlocks] — it is a cost, and the cost
 * is only worth anything if it is charged on both doors rather than on the one that was easier
 * to shut.
 *
 * These run with **no server**, so the thresholds are `:core`'s own defaults — which is what
 * [LocalUnlocks] falls back to and what a deployment that states nothing sends. A deployment
 * that states its own is `PvpUnlockTest`'s subject on the server side.
 */
@OptIn(ExperimentalTestApi::class)
class LobbyUnlockUiTest {
    @Test
    fun aNewCharacterIsToldWhenMultiplayerOpensRatherThanJustRefused() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        onNodeWithTag(DASHBOARD_PVP_TEST_TAG).performScrollTo().assertIsNotEnabled()
        // The badge, and not merely a dimmed card: "not yet, and here is when" is a different
        // sentence from "not here", and a card that only greys out says the second one.
        assertTrue(
            isVisible("Unlocks at level ${Unlocks.DEFAULT_MULTIPLAYER}"),
            "the lobby refused multiplayer without saying when it opens",
        )
    }

    @Test
    fun theSameLineIsOnTheAuctionBannerAndTapsThroughToTheReason() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        // On the card itself, and not merely on the screen behind it. The card is what a player
        // reads before deciding whether to tap, and it is the only thing under it: the line that
        // used to be there whatever the level said "Coming soon", which was true of the house
        // before it was built and false afterwards.
        assertTrue(
            auctionCardSays("Unlocks at level ${Unlocks.DEFAULT_AUCTION}"),
            "the auction card did not say when it opens",
        )

        openFromDashboard(DASHBOARD_AUCTION_TEST_TAG, AUCTION_SCREEN_TEST_TAG)

        // The banner is not disabled, which is the point of it: a player below the line can read
        // what the place *is* before being told they cannot go in yet.
        assertTrue(isVisible("Auction house"), "the screen did not name what it is")
        assertTrue(exists(AUCTION_LOCK_TEST_TAG), "the requirement was not stated on the page")
    }

    @Test
    fun atTheThresholdBothStopSayingIt() = runComposeUiTest {
        // XP, not `level`: `GameSave.sane()` derives the level from it on every load and every
        // write, so a save that names a level it has not earned is a save the repository undoes.
        val documents = seeded(
            GameSave(username = "kuplu", xp = XpTable.thresholdFor(Unlocks.DEFAULT_AUCTION)),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)

        assertFalse(
            isVisible("Unlocks at level ${Unlocks.DEFAULT_MULTIPLAYER}"),
            "a character who has cleared the gate was still being told about it",
        )

        // Nothing under the name once the door is open — no reason it is shut, and no promise
        // that it is coming. Asserted on the card rather than on the page, which would pass on
        // the multiplayer badge alone: both gates are at the same level and print the same line.
        assertFalse(
            auctionCardSays("Unlocks at level"),
            "an open auction house was still explaining itself",
        )

        onNodeWithTag(DASHBOARD_AUCTION_TEST_TAG).performScrollTo().performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_SCREEN_TEST_TAG) }
        assertFalse(exists(AUCTION_LOCK_TEST_TAG), "the cleared requirement was still on the page")
    }

    /** What the lobby's auction card itself carries, as opposed to what the lobby carries. */
    private fun ComposeUiTest.auctionCardSays(text: String): Boolean =
        onAllNodes(hasTestTag(DASHBOARD_AUCTION_TEST_TAG).and(hasText(text, substring = true)))
            .fetchSemanticsNodes()
            .isNotEmpty()
}
