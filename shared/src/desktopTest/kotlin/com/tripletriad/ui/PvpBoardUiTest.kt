package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchResult
import com.tripletriad.net.PvpClient
import com.tripletriad.protocol.PvpCell
import com.tripletriad.protocol.PvpMatchStatus
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.protocol.PvpMove
import com.tripletriad.protocol.PvpOutcome
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PvpBoardUiTest {

    @Test
    fun theOpponentsHandIsDrawnAsBacks() = board {
        for (slot in 0 until HAND) {
            onNodeWithTag(pvpBackTestTag(slot)).assertExists()
        }
    }

    @Test
    fun aRevealedOpponentCardIsDrawnFaceUp() = board(
        view = playing().copy(opponentHand = listOf(RED_CARDS[0], null, RED_CARDS[2], null, null)),
    ) {
        onNodeWithTag(pvpBackTestTag(HIDDEN_SLOT)).assertExists()
        onNodeWithTag(pvpBackTestTag(OTHER_HIDDEN_SLOT)).assertExists()
        // Slots 0 and 2 are faces, so they are *not* backs — the positional claim, which a filtered
        // list would lose: the hand would render as two cards instead of five with two shown.
        onNodeWithTag(pvpBackTestTag(0)).assertDoesNotExist()
        onNodeWithTag(pvpBackTestTag(SHOWN_SLOT)).assertDoesNotExist()
    }

    /**
     * The **shared** score tag, not one of this board's own.
     *
     * That is the assertion, as much as the number is: the two boards draw the same `StatusBar`
     * now, so "where is the score" has one answer in both modes. `PVP_SCORE_TEST_TAG` and
     * `PVP_TURN_TEST_TAG` went with the header that carried them.
     */
    @Test
    fun theScoreIsShown() = board {
        onNodeWithTag(SCORE_TEST_TAG).assertTextEquals("5 — 5")
    }

    @Test
    fun tappingACardThenACellPostsThatMove() {
        val posted = mutableListOf<PvpMove>()

        board(record = posted::add) {
            onNodeWithTag(pvpHandTestTag(TAPPED_SLOT)).performClick()
            onNodeWithTag(tileTestTag(CENTRE)).performClick()
            waitForIdle()
        }

        assertEquals(listOf(PvpMove(handIndex = TAPPED_SLOT, position = CENTRE)), posted)
    }

    @Test
    fun anUnplayableCardIgnoresTaps() {
        val posted = mutableListOf<PvpMove>()

        board(view = playing().copy(playable = listOf(0)), record = posted::add) {
            onNodeWithTag(pvpHandTestTag(OTHER_HIDDEN_SLOT)).performClick()
            onNodeWithTag(tileTestTag(0)).performClick()
            waitForIdle()
        }

        assertTrue(posted.isEmpty(), "an unplayable card posted $posted")
    }

    @Test
    fun theChosenCardIsTheOnlyOneRinged() = board(view = playing().copy(playable = listOf(0))) {
        assertEquals(
            1,
            onAllNodesWithTag(CHOSEN_CARD_TEST_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
            "one playable slot should leave exactly one ring",
        )
    }

    @Test
    fun anOrdinaryHandIsNotMarkedAtAll() = board {
        assertTrue(
            onAllNodesWithTag(CHOSEN_CARD_TEST_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
            "an unconstrained hand was marked",
        )
    }

    @Test
    fun theWaitingSideIsNotMarkedEither() = board(view = playing().copy(playable = emptyList())) {
        assertTrue(
            onAllNodesWithTag(CHOSEN_CARD_TEST_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
            "the waiting side's hand was marked",
        )
    }

    @Test
    fun theWaitingSideCannotPlay() {
        val posted = mutableListOf<PvpMove>()

        board(view = playing().copy(playable = emptyList()), record = posted::add) {
            onNodeWithTag(pvpHandTestTag(0)).performClick()
            onNodeWithTag(tileTestTag(0)).performClick()
            waitForIdle()
        }

        assertTrue(posted.isEmpty(), "the waiting side posted $posted")
    }

    @Test
    fun aPlayedCardIsOnTheBoard() = board(
        view = playing().copy(
            cells = List(BOARD) { position ->
                PvpCell(BLUE_CARDS[0], CardColor.BLUE).takeIf { position == CENTRE }
            },
        ),
    ) {
        onNodeWithTag(tileTestTag(CENTRE)).assertExists()
    }

    @Test
    fun aForfeitExplainsItself() = board(
        view = playing().copy(
            status = PvpMatchStatus.FORFEITED,
            outcome = PvpOutcome(
                result = MatchResult.WIN,
                blue = 5,
                red = 5,
                forfeitedBy = CardColor.RED,
            ),
        ),
    ) {
        onNodeWithTag(PVP_RESULT_TEST_TAG).assertExists()
        // The concede button is gone: there is nothing left to concede.
        onNodeWithTag(PVP_FORFEIT_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun aLiveMatchOffersConcedingAndNoResult() = board {
        onNodeWithTag(PVP_FORFEIT_TEST_TAG).assertExists()
        onNodeWithTag(PVP_RESULT_TEST_TAG).assertDoesNotExist()
    }

/**
     * **A turn that runs out plays a card. It no longer loses the match.**
     *
     * The two modes disagreed on the one thing a clock is for: a PvE board played a card at random
     * on the player's behalf, while here the server forfeited the match to whoever was on move.
     * Same thirty seconds, opposite punishment. The server's forfeit is still there and untouched —
     * it answers a client that has *gone away*, which is what its two minutes of grace are for —
     * but a player who is present and slow now gets the move a solo board would have given them.
     *
     * The move itself is `autoPlay`, which draws a playable card and a free cell and evaluates
     * nothing: a forced move must never be a good one by accident.
     */
    @Test
    fun aTurnThatRunsOutPlaysACardInsteadOfForfeiting() {
        val posted = mutableListOf<PvpMove>()

        board(record = posted::add) {
            waitUntil(timeoutMillis = AUTOPLAY_TIMEOUT_MS) { posted.isNotEmpty() }
        }

        val move = posted.single()
        assertTrue(move.handIndex in BLUE_CARDS.indices, "played a slot it did not hold: $move")
        assertTrue(move.position in 0 until BOARD, "played off the board: $move")
    }

    /** And it does not fire on a turn that is not the player's. */
    @Test
    fun theClockDoesNotPlayForTheOpponent() {
        val posted = mutableListOf<PvpMove>()

        board(view = playing().copy(playable = emptyList(), first = CardColor.RED)) {
            posted += emptyList()
            waitForIdle()
        }

        assertTrue(posted.isEmpty(), "a card was played on the opponent's turn: $posted")
    }

/**
     * **The opponent has a face, and it is the avatar they chose.**
     *
     * A solo board draws the NPC portrait the roster showed; this one drew nothing, because
     * `StatusBar` took an `Npc` and a person is not one. `OpponentFace` is the seam, and
     * `PvpMatchView.opponentAvatarId` is what fills it — added to the wire beside `opponentName`
     * because it is the same fact, who you are looking at.
     */
    @Test
    fun theOpponentIsDrawnFromTheAvatarTheyChose() = board(
        view = playing().copy(opponentAvatarId = AVATAR),
    ) {
        onNodeWithTag(portraitTestTag(AVATAR)).assertExists()
    }

    /** An account with no character yet is a name and no face, not a missing opponent. */
    @Test
    fun anOpponentWithNoAvatarIsStillDrawn() = board {
        onNodeWithTag(portraitTestTag("")).assertExists()
        onNodeWithTag(MATCH_OPPONENT_TEST_TAG).assertTextEquals("Kuplu")
    }

/**
     * **A multiplayer win announces what it unlocked, as a solo one does.**
     *
     * `MatchRewards.creditPvp` has credited achievements and daily quests since PvP was refereed —
     * `RULES_W` counts a rule win whoever was on the other side of it — and said nothing about
     * either: `PvpOutcome` had no field for them, so a player earned an achievement at the end of
     * a match and found out by going to look at their profile. Both panels draw `UnlockRows` now.
     */
    @Test
    fun aSettledMatchAnnouncesWhatItUnlocked() = board(
        view = playing().copy(
            status = PvpMatchStatus.FINISHED,
            outcome = PvpOutcome(
                result = MatchResult.WIN,
                blue = 6,
                red = 4,
                achievementIds = listOf(WHEEL_OF_FORTUNE),
            ),
        ),
    ) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_DONE_TEST_TAG) }

        onNodeWithTag(matchAchievementTestTag(WHEEL_OF_FORTUNE)).assertExists()
    }

    /** An id this build's catalogue does not know is dropped, not drawn as a hole. */
    @Test
    fun anUnknownUnlockIsSkippedRatherThanShown() = board(
        view = playing().copy(
            status = PvpMatchStatus.FINISHED,
            outcome = PvpOutcome(
                result = MatchResult.WIN,
                blue = 6,
                red = 4,
                achievementIds = listOf("ac-from-a-newer-server"),
            ),
        ),
    ) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_DONE_TEST_TAG) }

        onNodeWithTag(PVP_PAYOUT_TEST_TAG).assertExists()
    }

/**
     * **The loser watches the cards that are about to leave their collection.**
     *
     * This screen was a name and a countdown. The board behind it says who owns what *now* and
     * nothing about what was dealt, so the loser found out which cards had gone by noticing one
     * missing later. `pickFrom` travels to both sides now — the winner picks from it, the loser
     * watches it — and the same `PrizeRow` draws both.
     */
    @Test
    fun theLoserIsShownTheCardsAtStake() = board(
        view = playing().copy(
            status = PvpMatchStatus.AWAITING_CLAIM,
            outcome = PvpOutcome(
                result = MatchResult.LOSE,
                blue = 4,
                red = 6,
                // Zero owed is what makes this the loser's side of the claim.
                picksOwed = 0,
                pickFrom = RED_CARDS,
                claimDeadline = NOW + CLAIM_LEFT_MS,
            ),
        ),
    ) {
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PVP_WAIT_TEST_TAG) }

        for (id in RED_CARDS) {
            onNodeWithTag(prizeTestTag(id)).assertExists()
        }
        // Watching, not choosing: the confirm control belongs to the winner alone.
        onNodeWithTag(PVP_CLAIM_CONFIRM_TEST_TAG).assertDoesNotExist()
    }

/**
     * **A rematch changes the board key even though the match id does not.**
     *
     * This is the wiring behind `MatchBannerUiTest.aRematchIsOwedItsOwnOpening`: that one proves a
     * new key earns a new opening, and this one proves a Sudden Death rematch produces one. The
     * pair matters because everything downstream is a `remember` key, and a key that quietly stops
     * changing fails silently — the board would simply never announce itself again.
     */
    @Test
    fun aRematchIsADifferentBoardFromTheSameMatch() {
        val opening = playing()
        val second = opening.copy(rematch = 1)

        assertEquals(
            second.matchId,
            opening.matchId,
            "the fixture changed the match, not the board",
        )
        assertNotEquals(pvpBoardKey(opening), pvpBoardKey(second))
        assertEquals(pvpBoardKey(opening), pvpBoardKey(opening.copy(placement = PLACED)))
    }

    @Test
    fun theTurnLineNamesWhoseMoveItIs() = board {
        onNodeWithTag(TURN_TEST_TAG).assertExists()
        onNodeWithTag(PVP_BOARD_TEST_TAG).assertExists()
    }

    @Test
    fun theWaitingSideIsToldWhoTheyAreWaitingFor() = board(
        view = playing().copy(playable = emptyList(), first = CardColor.RED),
    ) {
        onNodeWithTag(TURN_TEST_TAG).assertTextContains("Kuplu", substring = true)
    }

    @Test
    fun aFinishedMatchOffersAWayOut() {
        var exited = false

        board(
            view = playing().copy(
                status = PvpMatchStatus.FINISHED,
                outcome = PvpOutcome(result = MatchResult.WIN, blue = 6, red = 4),
            ),
            onExit = { exited = true },
        ) {
            onNodeWithTag(PVP_DONE_TEST_TAG).performClick()
            waitForIdle()
        }

        assertTrue(exited, "the result panel led nowhere")
    }

    @Test
    fun theScoreIsToldFromThisPlayersSideWhenTheServerDealtRed() = board(
        view = dealtRed(),
    ) {
        onNodeWithTag(SCORE_TEST_TAG).assertTextEquals("6 — 4")
    }

    @Test
    fun aForfeitStillNamesWhoLeftWhenTheViewIsMirrored() = board(
        view = dealtRed().copy(
            status = PvpMatchStatus.FORFEITED,
            outcome = PvpOutcome(
                result = MatchResult.LOSE,
                blue = 4,
                red = 6,
                forfeitedBy = CardColor.RED,
            ),
        ),
    ) {
        onNodeWithText(strings[StringKeys.PVP_YOU_LEFT]).assertExists()
        onNodeWithText(strings[StringKeys.PVP_THEY_LEFT]).assertDoesNotExist()
    }

    // ---- Harness ----------------------------------------------------------

    @Suppress("LongParameterList")
    private fun board(
        view: PvpMatchView = playing(),
        record: (PvpMove) -> Unit = {},
        onExit: () -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/move")) {
                val sent = (request.body as OutgoingContent.ByteArrayContent)
                    .bytes()
                    .decodeToString()
                record(json.decodeFromString(PvpMove.serializer(), sent))
            }
            respondJson(json.encodeToString(PvpMatchView.serializer(), view))
        }
        val session = sessionOver(engine)
        runBlocking { session.resume() }

        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                TripleTriadTheme {
                    PvpMatchScreen(session = session, cards = catalogue, now = NOW, onExit = onExit)
                }
            }
        }
        block()
    }

    private fun sessionOver(engine: MockEngine): PvpSession {
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return PvpSession(PvpClient(http, { "http://server" }), tokenOf = { "token" })
    }

    private fun playing() = PvpMatchView(
        matchId = "m-1",
        side = CardColor.BLUE,
        opponentName = "Kuplu",
        rules = GameRules(),
        formatId = "ff14",
        cells = List(BOARD) { null },
        elements = List(BOARD) { null },
        hand = BLUE_CARDS,
        opponentHand = List(HAND) { null },
        first = CardColor.BLUE,
        placement = 0,
        playable = BLUE_CARDS.indices.toList(),
    )

    private fun dealtRed() = playing().copy(
        side = CardColor.RED,
        cells = List(BOARD) { position ->
            when (position) {
                CENTRE -> PvpCell(BLUE_CARDS[0], CardColor.BLUE)
                in 0 until PLACED_BY_RED -> PvpCell(RED_CARDS[position], CardColor.RED)
                else -> null
            }
        },
        hand = RED_CARDS.take(HAND_LEFT),
        opponentHand = List(HAND_LEFT) { null },
        placement = PLACED,
        // Four cards down with blue moving first means it is blue's turn again — the opponent's —
        // and an empty list is what the server sends a side that may not move.
        playable = emptyList(),
    )

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val catalogue: Map<Int, Card> =
        runBlocking { loadCardCatalog() }.all.associateBy { it.id }

    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    private companion object {
        /** Long enough for the thirty second turn to elapse on the test clock. */
        const val AUTOPLAY_TIMEOUT_MS = 60_000L

        /** A claim deadline still ahead of [NOW], so the countdown has something to count. */
        const val CLAIM_LEFT_MS = 30_000L

        /** "Win one Roulette match" — a real id from `AchievementCatalog`. */
        const val WHEEL_OF_FORTUNE = "ac-wof1"

        /** One of the shipped avatars — see `AVATAR_NAMES`. */
        const val AVATAR = "ffxiv_twi01001"

        const val BOARD = 9
        const val HAND = 5

        const val CENTRE = 4
        const val TAPPED_SLOT = 2
        const val SHOWN_SLOT = 2
        const val HIDDEN_SLOT = 1
        const val OTHER_HIDDEN_SLOT = 3
        const val NOW = 1_767_268_800_000L

        const val PLACED_BY_RED = 3
        const val PLACED = 4
        const val HAND_LEFT = 3

        val BLUE_CARDS: List<Int> = (1..5).map { Card.idFor(block = 1, number = it) }
        val RED_CARDS: List<Int> = (11..15).map { Card.idFor(block = 1, number = it) }
    }
}
