package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
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
 * ### Only one of the two doors is asserted here, and why the other cannot be
 *
 * Both used to be cards on the lobby, and the lobby no longer carries either: the auction house is
 * the store's third tab and multiplayer is the play root's. The shop's door is covered
 * below. The multiplayer one states its level again — see `PvpLocked` — but not anywhere these
 * tests can read it: they run with **no server**, and without one the multiplayer screen is not
 * drawn at all. It is asserted where it can be, against a session:
 * `PvpTablesUiTest.theRoomSaysWhichLevelOpensIt`.
 *
 * These run with **no server**, so the thresholds are `:core`'s own defaults — which is what
 * [LocalUnlocks] falls back to and what a deployment that states nothing sends. A deployment
 * that states its own is `PvpUnlockTest`'s subject on the server side.
 */
@OptIn(ExperimentalTestApi::class)
class LobbyUnlockUiTest {
    @Test
    fun theAuctionTabStatesTheLevelThatOpensIt() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        // The tab is not disabled, which is the point of it: a player below the line can read
        // what the place *is* before being told they cannot trade there yet. The banner the shelf
        // used to carry said the same thing one tap earlier; the tab is the tap.
        openAuction()

        assertTrue(isVisible("Auction house"), "the tab did not name what it is")
        assertTrue(exists(AUCTION_LOCK_TEST_TAG), "the requirement was not stated on the page")
        assertTrue(
            isVisible("Unlocks at level ${Unlocks.DEFAULT_AUCTION}"),
            "the page did not say when it opens",
        )
    }

    @Test
    fun atTheThresholdItStopsSayingIt() = runComposeUiTest {
        // XP, not `level`: `GameSave.sane()` derives the level from it on every load and every
        // write, so a save that names a level it has not earned is a save the repository undoes.
        val documents = seeded(
            GameSave(username = "kuplu", xp = XpTable.thresholdFor(Unlocks.DEFAULT_AUCTION)),
        )
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)

        openAuction()

        // Nothing about a level once the door is open — no reason it is shut, and no promise that
        // it is coming.
        assertFalse(exists(AUCTION_LOCK_TEST_TAG), "the cleared requirement was still on the page")
        assertFalse(
            isVisible("Unlocks at level"),
            "an open auction house was still explaining itself",
        )
    }
}
