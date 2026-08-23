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
import com.tripletriad.model.MatchResult
import com.tripletriad.model.questDayOf
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CampaignUiTest {
    private val campaigns = runBlocking { loadCampaignCatalog() }
    private val goldSaucer = campaigns.byKey(GOLD_SAUCER) ?: error("no $GOLD_SAUCER campaign")
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    private fun withFee(): InMemoryDocumentStore =
        seeded(GameSave.new(createdAt = 0L).copy(mgp = goldSaucer.fee + POCKET_CHANGE))

    /**
     * A referee holding a profile that can just afford the ladder.
     *
     * The rungs are refereed matches, so a ladder needs a server at all now; and the entry fee is
     * charged against the profile the server holds, which the client pushes back through
     * `/me/save`. See [PveStubServer].
     */
    private fun payingServer(): PveStubServer = PveStubServer(
        save = freshSave().copy(mgp = goldSaucer.fee + POCKET_CHANGE),
    )

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

    /**
     * The ladder names what finishing it pays, **including its own payout**.
     *
     * Not the last opponent's figure alone, which is what this used to assert and what the screen
     * used to show. A tournament returns a multiple of its entry fee on top of whatever the final
     * rung pays — see `Campaign.payout` — and a player deciding whether to hand over the fee is
     * comparing it against the total, so the total is what the line has to say.
     *
     * The charging itself is `AccountRoutes`' `/me/campaign/enter` and cannot be asserted here:
     * MGP is server-owned, `GameSave.withServerOwnedFrom` throws a client's copy away on arrival,
     * and the stub server does not price ladders. `IntentRoutesTest.enteringALadderCostsItsFee` is
     * where that is held.
     */
    @Test
    fun theLadderNamesWhatFinishingItPays() = runComposeUiTest {
        val documents = withFee()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)

        val champion = goldSaucer.steps.last().npc
        val total = goldSaucer.payout + champion.mgpFor(MatchResult.WIN)
        assertTrue(exists(CAMPAIGN_FINAL_REWARD_TEST_TAG), "the final reward should be named")
        assertVisible("$total", "the line should add the ladder's payout to the last rung's")
        assertTrue(
            total > goldSaucer.fee,
            "a tournament won must return more than it cost, or the stake is a fine",
        )
    }

    /**
     * A ladder earned rather than bought says so, and cannot be entered at any price.
     *
     * The Card Club is the shipped case: Balamb Garden is the way in. The purse is deliberately
     * ample, so the only thing refusing is the gate.
     */
    @Test
    fun aGatedLadderNamesItsGateAndStaysShut() = runComposeUiTest {
        val cardClub = campaigns.byKey(CARD_CLUB) ?: error("no $CARD_CLUB campaign")
        val documents = seeded(GameSave.new(createdAt = 0L).copy(mgp = cardClub.fee * 2))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents, CARD_CLUB)

        assertTrue(exists(CAMPAIGN_LOCKED_TEST_TAG), "a locked ladder should say why")
        onNodeWithTag(CAMPAIGN_START_TEST_TAG).assertIsNotEnabled()
    }

    /**
     * Today's entry, once spent, shuts the ladder until 00:00 UTC — with the reason on screen.
     *
     * The profile carries no open run here, which is the case that matters: a first-round defeat
     * closes the run and leaves the day's stamp behind, and it is the stamp alone that has to keep
     * the button grey. The clock is the app's own, so the day key is the one the screen computes.
     */
    @Test
    fun aLadderEnteredTodayIsShutUntilTomorrow() = runComposeUiTest {
        val today = questDayOf(FixedClock().nowMillis())
        val documents = seeded(
            GameSave.new(createdAt = 0L).copy(
                mgp = goldSaucer.fee * 2,
                campaignEntries = mapOf(GOLD_SAUCER to today),
            ),
        )
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)

        assertTrue(exists(CAMPAIGN_LOCKED_TEST_TAG), "the day's entry being spent should be said")
        onNodeWithTag(CAMPAIGN_START_TEST_TAG).assertIsNotEnabled()
    }

    /** Yesterday's entry is not today's: the same profile, one day on, may enter again. */
    @Test
    fun anEntrySpentOnAnotherDayDoesNotShutTheLadder() = runComposeUiTest {
        val documents = seeded(
            GameSave.new(createdAt = 0L).copy(
                mgp = goldSaucer.fee * 2,
                campaignEntries = mapOf(GOLD_SAUCER to YESTERDAY),
            ),
        )
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)

        assertFalse(exists(CAMPAIGN_LOCKED_TEST_TAG), "a stale stamp should not shut the ladder")
        onNodeWithTag(CAMPAIGN_START_TEST_TAG).assertIsEnabled()
    }

    /**
     * A lost rung ends the run, and the way on is the bilan rather than another opponent.
     *
     * This is the whole of what makes a tournament a stake: `Campaign.nextStep` answers
     * `FIRST_STEP` for a loss, so before [CampaignMatchScreen] stopped consulting it, losing rung
     * one simply restarted the ladder for free and the entry fee bought unlimited retries.
     *
     * [LOSING_SEED] is a seed the stub deals a *losing* first rung from — the default one deals a
     * win, which is why the rung-to-rung test above can use it. Both are properties of
     * `PveStubServer`'s generator rather than of this screen, so a change to the dealing would
     * strand this test: it asserts the run ended, and a seed that started winning would fail here
     * loudly rather than quietly stop testing the loss.
     */
    @Test
    fun losingARungEndsTheRunAndOpensTheBilan() = runComposeUiTest {
        val stub = PveStubServer(
            save = freshSave().copy(mgp = goldSaucer.fee + POCKET_CHANGE),
            seed = LOSING_SEED,
        )
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        openRefereedLadder()
        onNodeWithTag(CAMPAIGN_START_TEST_TAG).performClick()
        settleDeck()

        playOut()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MATCH_DONE_TEST_TAG) }

        assertFalse(isVisible("Next Match"), "a lost rung must not offer the next one")
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()

        assertTrue(exists(CAMPAIGN_SUMMARY_TEST_TAG), "the run should end on its bilan")
        // Every rung is accounted for, including the ones the run never got to.
        for (step in goldSaucer.steps.indices) {
            assertTrue(exists(campaignSummaryRowTestTag(step)), "rung $step should be listed")
        }
        assertVisible("Knocked out in match 1", "the bilan should say where the run ended")
    }

    @Test
    fun startingALadderOpensItsFirstRung() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = payingServer().connection) }
        openRefereedLadder()

        onNodeWithTag(CAMPAIGN_START_TEST_TAG).performClick()
        settleDeck()

        assertTrue(existsUnmerged(CAMPAIGN_STEP_TEST_TAG), "the ladder should say which rung it is")
    }

    /**
     * The entry is paid for **before** the first rung is dealt, however slow the link is.
     *
     * `PveMatchRequest.campaignKey` is checked against the run the server holds, so a board opened
     * while the entry was still in flight is refused `NOT_ON_THAT_RUNG`. Starting the ladder used
     * to navigate without waiting, which a loopback link always got away with and a phone on a
     * real network never did — the reconnect panel came up on every attempt, saying the server was
     * unreachable when it had answered perfectly.
     *
     * The gate is what does the testing: with the entry unanswered there must be no board yet and
     * no refusal either, and only releasing it deals the first rung.
     */
    @Test
    fun theFirstRungWaitsForTheEntryToBePaid() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val stub = PveStubServer(
            save = freshSave().copy(mgp = goldSaucer.fee + POCKET_CHANGE),
            entryGate = gate,
        )
        setContent { App(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        openRefereedLadder()

        onNodeWithTag(CAMPAIGN_START_TEST_TAG).performClick()
        waitForIdle()
        assertFalse(exists(PVE_RECONNECT_TEST_TAG), "the rung must not be opened before the entry")

        gate.complete(Unit)
        settleDeck()
    }

    /**
     * A ladder played under the *other* block's format is shut, not sold.
     *
     * Balamb Garden is `ff8-standard`, and an FFXIV starter owns nothing that pool admits — so
     * there is no deck to bring to any of its four rungs. The button used to be enabled anyway:
     * the fee was taken, the run opened, and the first rung came back `UNDEALABLE` from a referee
     * that had done nothing wrong. On the board that reads as "the server is unreachable", which
     * is how this was reported and why it took a logcat to find.
     *
     * The purse is deliberately ample, so the only thing refusing is the deck.
     */
    @Test
    fun aLadderInTheOtherFormatIsShutRatherThanSold() = runComposeUiTest {
        val balamb = campaigns.byKey(BALAMB) ?: error("no $BALAMB campaign")
        val documents = seeded(freshSave(block = FF14_BLOCK).copy(mgp = balamb.fee * 2))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents, BALAMB)

        assertTrue(exists(CAMPAIGN_LOCKED_TEST_TAG), "having no deck for the format should be said")
        onNodeWithTag(CAMPAIGN_START_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun aRungOffersTheNextRungRatherThanARematch() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), server = payingServer().connection) }
        openRefereedLadder()
        onNodeWithTag(CAMPAIGN_START_TEST_TAG).performClick()
        settleDeck()

        playOut()

        assertFalse(isVisible("Rematch"), "a scripted match is never replayed in place")
        assertTrue(exists(MATCH_DONE_TEST_TAG), "the way out is always offered")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.openRefereedLadder() {
        openDashboard()
        openOpponents()
        onNodeWithTag(campaignRowTestTag(GOLD_SAUCER)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CAMPAIGN_LIST_TEST_TAG) }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.openLadder(
        documents: InMemoryDocumentStore,
        key: String = GOLD_SAUCER,
    ) {
        loadCharacter(documents)
        openOpponents()
        onNodeWithTag(campaignRowTestTag(key)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CAMPAIGN_LIST_TEST_TAG) }
    }

    private companion object {
        const val GOLD_SAUCER = "gs"

        const val CARD_CLUB = "cc"

        // The one shipped ladder in the FFVIII pool that no achievement gates, which is what makes
        // it the case where a missing deck is the *only* thing shutting the door.
        const val BALAMB = "balamb"

        const val POCKET_CHANGE = 7

        // A stub seed whose first Gold Saucer rung the player loses. See the test that uses it.
        const val LOSING_SEED = 2

        /**
         * Any day that is not the one [FixedClock] reports.
         *
         * A literal rather than arithmetic on the clock: the point is that a stamp from *some
         * other* day does not shut the ladder, and 1970-01-01 is unambiguously not today whatever
         * the fixed clock is set to.
         */
        const val YESTERDAY = "1970-01-01"
    }
}
