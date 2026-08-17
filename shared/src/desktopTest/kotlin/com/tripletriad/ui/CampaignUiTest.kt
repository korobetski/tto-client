package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_BLOCK
import com.tripletriad.FF8_BLOCK
import com.tripletriad.data.loadCampaignCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CampaignUiTest {
    private val campaigns = runBlocking { loadCampaignCatalog() }
    private val goldSaucer = campaigns.byKey(GOLD_SAUCER) ?: error("no $GOLD_SAUCER campaign")
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    private fun withFee(): InMemoryDocumentStore =
        seeded(GameSave.new(createdAt = 0L).copy(mgp = goldSaucer.fee + POCKET_CHANGE))

    @Test
    fun bothLaddersAreOffered() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter(FF14_BLOCK)
        openOpponents()

        assertTrue(exists(campaignRowTestTag(GOLD_SAUCER)), "the Gold Saucer is the ff14 ladder")
        assertTrue(exists(campaignRowTestTag(CARD_CLUB)), "the Card Club is the ff8 one")
    }

    @Test
    fun anFf8CharacterSeesBothToo() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter(FF8_BLOCK)
        openOpponents()

        assertTrue(exists(campaignRowTestTag(CARD_CLUB)))
        assertTrue(exists(campaignRowTestTag(GOLD_SAUCER)))
    }

    @Test
    fun aFreshPurseCannotEnter() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()
        onNodeWithTag(campaignRowTestTag(GOLD_SAUCER)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CAMPAIGN_LIST_TEST_TAG) }

        onNodeWithTag(CAMPAIGN_START_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun theLadderShowsItsRungsBeforeTheFee() = runComposeUiTest {
        val documents = withFee()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)

        onNodeWithTag(CAMPAIGN_START_TEST_TAG).assertIsEnabled()
        assertTrue(isVisible("${goldSaucer.fee}"), "the price should be next to the button")
        // The first rung by name, resolved the way the screen resolves it. Only the first: six rows
        // do not all fit, and a test that scrolled would be asserting the scaffold, not the ladder.
        assertVisible(
            english[goldSaucer.steps.first().npc.nameKey],
            "the ladder should open with its first opponent",
        )
    }

    @Test
    fun startingALadderChargesTheFee() = runComposeUiTest {
        val documents = withFee()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)

        onNodeWithTag(CAMPAIGN_START_TEST_TAG).performClick()
        settleDeck()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).mgp == POCKET_CHANGE }
        assertEquals(POCKET_CHANGE, storedSave(documents).mgp, "the fee should have been taken")
        assertTrue(existsUnmerged(CAMPAIGN_STEP_TEST_TAG), "the ladder should say which rung it is")
    }

    @Test
    fun aRungOffersTheNextRungRatherThanARematch() = runComposeUiTest {
        val documents = withFee()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)
        onNodeWithTag(CAMPAIGN_START_TEST_TAG).performClick()
        settleDeck()

        playOut()

        assertFalse(isVisible("Rematch"), "a scripted match is never replayed in place")
        assertTrue(exists(MATCH_DONE_TEST_TAG), "the way out is always offered")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.openLadder(documents: InMemoryDocumentStore) {
        loadCharacter(documents)
        openOpponents()
        onNodeWithTag(campaignRowTestTag(GOLD_SAUCER)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CAMPAIGN_LIST_TEST_TAG) }
    }

    private companion object {
        const val GOLD_SAUCER = "gs"

        const val CARD_CLUB = "cc"

        const val POCKET_CHANGE = 7
    }
}
