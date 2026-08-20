package com.tripletriad.ui

import com.tripletriad.FF14_BLOCK
import com.tripletriad.data.Format
import com.tripletriad.data.MatchRewards
import com.tripletriad.data.PveMatches
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.model.Board
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchAi
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchState
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchView
import com.tripletriad.model.Npc
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
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.Placement
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.PveFailure
import com.tripletriad.protocol.PveMatchRequest
import com.tripletriad.protocol.PveMatchStatus
import com.tripletriad.protocol.PveMatchView
import com.tripletriad.protocol.PveMove
import com.tripletriad.protocol.PveOutcome
import com.tripletriad.protocol.PveRefusal
import com.tripletriad.protocol.RewardSummary
import com.tripletriad.protocol.SeedTickets
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * A referee, in a fixture.
 *
 * ### Why a user-interface test now needs a server at all
 *
 * Because a match against an opponent is refereed. `PveMatchScreen` holds no engine: it renders the
 * view the server sent and posts placements back, so an `App` given no server draws no board and
 * every assertion about one waits ten seconds for a thing that cannot appear. That is the intended
 * consequence of moving the resolution across the wire, not a regression — but it leaves the
 * fixtures needing something to answer them.
 *
 * ### It really referees, rather than replaying a script
 *
 * The alternative was a recorded exchange: canned `PveMatchView`s handed back in order. It was
 * rejected because the tests here drive a *match* — they click a card, then a cell, and assert on
 * what the board became — and a script cannot answer a click it did not anticipate. So this deals
 * with `MatchPreparation.prepareVersus`, replies with `MatchAi`, credits with `MatchRewards`, and
 * projects with `PveMatchView.of`: the same `:core` entry points the real server calls, in the same
 * order.
 *
 * What it deliberately does **not** reproduce is everything the real referee does *around* those
 * calls — persistence, the settle-once race, the opponent's own view being drawn separately from
 * the player's. This is a stub: it holds one live match in a field and mutates it. The server's own
 * suite is where a row surviving a replay is proved.
 *
 * ### One generator, seeded once
 *
 * [seed] fixes the deal, the toss and every reply, so a fixture that plays the same clicks twice
 * gets the same match twice. Tests that care which cards are dealt pass their own.
 */
@Suppress("TooManyFunctions")
internal class PveStubServer(
    block: Int = FF14_BLOCK,
    save: GameSave = freshSave(block = block),
    private val seed: Int = STUB_SEED,
    private val at: Long = FixedClock.DEFAULT_MILLIS,
    private val reporter: MatchReporter = SilentMatchReporter,
) {
    val cards = runBlocking { loadCardCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }
    private val formats = runBlocking { loadFormatCatalog() }

    /** The profile the server holds. `GET /me` answers it, and settlement rewrites it. */
    var player: PlayerState = PlayerState(save = save)
        private set

    private var live: Live? = null
    private var opened: Int = 0

    /**
     * The connection an `App` is handed, already signed in.
     *
     * Signed in because `PveSession` asks for a token before it asks for anything else and gives up
     * quietly without one — a fixture that skipped this step would look exactly like the timeout it
     * is meant to fix.
     */
    val connection: ServerConnection by lazy { connect() }

    private fun connect(): ServerConnection {
        val http = HttpClient(engine()) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val sessions = InMemoryDocumentStore()
        runBlocking {
            SessionStore(sessions).save(
                HOME.id,
                StoredSession(token = TOKEN, expiresAt = Long.MAX_VALUE, username = NAME),
            )
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), listOf(HOME))
        return ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            pvp = PvpClient(http, baseUrl = { directory.selected.baseUrl }),
            pve = PveClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(sessions),
            tickets = TicketStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = reporter,
        )
    }

    // ---- Routing -----------------------------------------------------------

    private fun engine() = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path == "/server" -> respondJson(matchProtocolJson.encodeToString(SERVER_INFO))
            path == "/me/tickets" -> respondJson(matchProtocolJson.encodeToString(SeedTickets()))
            path == "/me/save" -> saved(body(request))
            path.startsWith("/pve/matches") -> pve(path, request)
            else -> respondJson(matchProtocolJson.encodeToString(player))
        }
    }

    /**
     * The profile a client pushed — everything it is still allowed to decide for itself.
     *
     * Not everything is. `GameSave.withServerOwnedFrom` is what keeps a client from paying itself:
     * MGP, XP and the match record come back from the server's copy whatever was sent. What does
     * travel this way is the player's own housekeeping — decks, options, and the entry fee a
     * campaign charges before there is any match to referee.
     */
    private fun MockRequestHandleScope.saved(sent: GameSave): HttpResponseData {
        player = player.copy(save = sent.withServerOwnedFrom(player.save))
        return respond("", HttpStatusCode.NoContent)
    }

    private suspend fun MockRequestHandleScope.pve(
        path: String,
        request: HttpRequestData,
    ): HttpResponseData = when {
        path == "/pve/matches" -> opened(body<PveMatchRequest>(request))
        path == "/pve/matches/active" -> active()
        path.endsWith("/moves") -> played(id(path, suffix = "/moves"), body(request))
        else -> read(id(path, suffix = ""))
    }

    private fun MockRequestHandleScope.opened(request: PveMatchRequest): HttpResponseData {
        val format = formats[request.formatId]
            ?: return refuse(PveRefusal.NO_SUCH_FORMAT, "no such format")
        val npc = npcs.byIcon(request.opponentIconId, format.id)
            ?: return refuse(PveRefusal.NO_SUCH_OPPONENT, "no such opponent")

        val match = deal(npc, format, request.deck)
        live = match
        return respondJson(
            matchProtocolJson.encodeToString(match.wire(match.advance(null))),
            HttpStatusCode.Created,
        )
    }

    private fun MockRequestHandleScope.active(): HttpResponseData {
        val match = live ?: return respond("", HttpStatusCode.NoContent)
        return respondJson(matchProtocolJson.encodeToString(match.wire(emptyList())))
    }

    private fun MockRequestHandleScope.read(matchId: String): HttpResponseData {
        val match = live?.takeIf { it.id == matchId }
            ?: return refuse(PveRefusal.NO_SUCH_MATCH, "no such match")
        return respondJson(matchProtocolJson.encodeToString(match.wire(emptyList())))
    }

    private fun MockRequestHandleScope.played(
        matchId: String,
        move: PveMove,
    ): HttpResponseData {
        val match = live?.takeIf { it.id == matchId }
            ?: return refuse(PveRefusal.NO_SUCH_MATCH, "no such match")
        val view = match.view()

        return when {
            match.status != PveMatchStatus.PLAYING || !view.isMyTurn ->
                refuse(PveRefusal.NOT_YOUR_TURN, "it is not your turn")

            move.handIndex !in view.playableHandIndices ||
                move.position !in view.playablePositions() ->
                refuse(PveRefusal.ILLEGAL_MOVE, "that move is not allowed")

            else -> respondJson(matchProtocolJson.encodeToString(match.wire(match.advance(move))))
        }
    }

    // ---- The referee -------------------------------------------------------

    /**
     * A live match, mutated in place.
     *
     * The real row stores its inputs and derives the board on every read, which is what lets it
     * survive a restart and a deployment. Nothing here is stored, so there is nothing to survive,
     * and a mutable position says what it means in a quarter of the lines.
     */
    private class Live(
        val id: String,
        val npc: Npc,
        val formatId: String,
        val random: Random,
        var state: MatchState,
        var blueSeesRed: HandVisibility,
        var redSeesBlue: HandVisibility,
        var rematch: Int = 0,
        var status: PveMatchStatus = PveMatchStatus.PLAYING,
        var reward: RewardSummary? = null,
    )

    private fun deal(npc: Npc, format: Format, deck: Int): Live {
        // A fresh generator per match, mixed with how many have been dealt: a fixture that plays
        // two matches in a row should not be handed the same five cards twice.
        val generator = Random(seed + opened++)
        val rules = PveMatches.rulesFor(npc, format, generator)
        val legal = cards.admittedBy(format).associateBy { it.id }
        val collection = player.save.ownedCardIds().mapNotNull { legal[it] }

        val blue = if (rules.random && collection.size >= HAND_SIZE) {
            MatchPreparation.randomHand(collection, generator)
        } else {
            PveMatches.playerDeck(player.save, deck).map { legal.getValue(it) }
        }
        val red = npc.randomHand(generator).map { legal.getValue(it) }
        val first = if (generator.nextBoolean()) CardColor.BLUE else CardColor.RED
        val opening = MatchPreparation.prepareVersus(blue, red, first, rules, generator)

        return Live(
            id = "stub-$opened",
            npc = npc,
            formatId = format.id,
            random = generator,
            state = opening.state,
            blueSeesRed = opening.blueSeesRed,
            redSeesBlue = opening.redSeesBlue,
        )
    }

    /**
     * Applies [move] if there is one, then every reply the opponent owes, and settles if that was
     * the end of it.
     *
     * A loop rather than one reply, because a Sudden Death rematch keeps the turn order: a board
     * can fill, a new one begin, and the opponent be on move again immediately. Bounded by the size
     * of a board so a bug cannot spin.
     */
    private fun Live.advance(move: PveMove?): List<Placement> {
        val plays = mutableListOf<Placement>()
        move?.let { plays += apply(it) }

        repeat(Board.SIZE) {
            if (!nextBoard()) return@repeat
            val onMove = state.takeIf { it.currentPlayer == CardColor.RED } ?: return@repeat
            val chosen = MatchAi().choose(onMove, random) ?: return@repeat
            val slot = onMove.currentHand.indexOfFirst { it.id == chosen.card.id }
            if (slot >= 0) plays += apply(PveMove(slot, chosen.position))
        }
        nextBoard()
        return plays
    }

    /**
     * Moves on to the board the next placement belongs on, settling the match if there is none.
     *
     * Returns whether the match is still being played. Called before *and* after the reply loop:
     * without the trailing call a drawn board would sit finished until somebody happened to move,
     * and the player would be shown a full grid with no turn on it.
     */
    private fun Live.nextBoard(): Boolean {
        if (!state.isFinished) return true
        if (state.rules.suddenDeath && state.score.winner() == null) {
            val next = MatchPreparation.prepareRematch(state, random)
            state = next.state
            blueSeesRed = next.opponentVisibility
            redSeesBlue = next.playerVisibility
            rematch++
            return true
        }
        settle()
        return false
    }

    private fun Live.apply(move: PveMove): Placement {
        state = state.play(state.currentHand[move.handIndex], move.position)
        val play = checkNotNull(state.lastPlay) { "a placement left no trace" }
        return Placement(
            player = play.player,
            cardId = play.card.id,
            position = play.position,
            captures = play.captures,
            handIndex = play.handIndex,
        )
    }

    /** Credits the profile, once. The one write, on the server, exactly as the real one is. */
    private fun Live.settle() {
        if (status != PveMatchStatus.PLAYING) return
        val score = state.score
        val result = when {
            score.blue > score.red -> MatchResult.WIN
            score.blue < score.red -> MatchResult.LOSE
            else -> MatchResult.DRAW
        }
        val credited = MatchRewards.credit(
            save = player.save.startingMatch(againstNpc = true),
            npc = npc,
            result = result,
            rules = state.rules,
            at = at,
            random = random,
        )
        player = player.copy(save = credited.save)
        status = PveMatchStatus.FINISHED
        reward = RewardSummary(
            result = credited.reward.result,
            mgp = credited.reward.mgp,
            xp = credited.reward.xp,
            items = credited.reward.items,
            achievementIds = credited.reward.achievements.map { it.id },
            questIds = credited.reward.quests.map { it.id },
        )
    }

    // ---- Projection --------------------------------------------------------

    private fun Live.view(): MatchView =
        MatchView.of(state, CardColor.BLUE, blueSeesRed, turnRandom())

    private fun Live.wire(plays: List<Placement>): PveMatchView = PveMatchView.of(
        view = view(),
        matchId = id,
        opponentIconId = npc.iconId,
        formatId = formatId,
        status = status,
        rematch = rematch,
        outcome = outcome(),
        plays = plays,
    )

    private fun Live.outcome(): PveOutcome? {
        if (status == PveMatchStatus.PLAYING) return null
        val score = state.score
        val result = when {
            score.blue > score.red -> MatchResult.WIN
            score.blue < score.red -> MatchResult.LOSE
            else -> MatchResult.DRAW
        }
        return PveOutcome(
            result = result,
            blue = score.blue,
            red = score.red,
            reward = reward,
            player = player,
        )
    }

    /**
     * The generator for this turn's Chaos roll, seeded from the placement.
     *
     * Fixed per placement rather than drawn fresh, because a screen reads a view more than once and
     * a Chaos roll that moved between reads would offer the player a different card each frame.
     */
    private fun Live.turnRandom(): Random = Random(seed * TURN_MIX + state.placement)

    // ---- Plumbing ----------------------------------------------------------

    private suspend inline fun <reified T> body(request: HttpRequestData): T =
        matchProtocolJson.decodeFromString<T>(request.body.toByteArray().decodeToString())

    private fun id(path: String, suffix: String): String =
        path.removePrefix("/pve/matches/").removeSuffix(suffix)

    private fun MockRequestHandleScope.refuse(code: PveRefusal, detail: String) = respondJson(
        matchProtocolJson.encodeToString(PveFailure(code, detail)),
        HttpStatusCode.Conflict,
    )

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    companion object {
        const val NAME: String = "stub"

        private const val TOKEN = "stub-token"
        private const val STUB_SEED = 20260819

        /** An odd multiplier, so the seed and the placement do not cancel in the low bits. */
        private const val TURN_MIX = 31

        private val HOME =
            ServerEntry(id = "stub", label = "Stub", baseUrl = "https://stub.invalid")

        private val SERVER_INFO = ServerInfo(
            name = "stub",
            version = CURRENT_VERSION,
            minimumClient = CURRENT_VERSION,
        )
    }
}

/** Nothing is queued any more; a refereed match reports itself by being played. */
internal object SilentMatchReporter : MatchReporter {
    override suspend fun report(profileKey: String, transcript: MatchTranscript) = Unit
    override suspend fun drain(profileKey: String): PlayerState? = null
    override suspend fun forget(profileKey: String) = Unit
}
