package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.model.XpTable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The level gate on the two things a second player can be cheated through.
 *
 * Multiplayer and the auction house are the places where one person holding two accounts stops
 * being their own business: a rigged PvP match moves rating, and a sale to yourself moves cards.
 * The gate is not a fix for that — see the note on [Unlocks] — it is a cost, and the cost is only
 * worth anything if it is charged on both doors rather than on the one that was easier to shut.
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
            isVisible("Unlocks at level ${Unlocks.MULTIPLAYER_LEVEL}"),
            "the lobby refused multiplayer without saying when it opens",
        )
    }

    @Test
    fun theSameLineIsOnTheAuctionBannerAndTapsThroughToTheReason() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

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
            GameSave(username = "kuplu", xp = XpTable.thresholdFor(Unlocks.AUCTION_LEVEL)),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)

        assertFalse(
            isVisible("Unlocks at level ${Unlocks.MULTIPLAYER_LEVEL}"),
            "a character who has cleared the gate was still being told about it",
        )

        onNodeWithTag(DASHBOARD_AUCTION_TEST_TAG).performScrollTo().performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(AUCTION_SCREEN_TEST_TAG) }
        assertFalse(exists(AUCTION_LOCK_TEST_TAG), "the cleared requirement was still on the page")
    }
}
