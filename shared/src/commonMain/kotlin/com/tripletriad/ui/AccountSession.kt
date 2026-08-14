package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.log.Log
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.net.AccountResult
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.StoredSession
import com.tripletriad.net.accountQueueKey
import com.tripletriad.net.isUnauthenticated
import com.tripletriad.protocol.AccountError
import com.tripletriad.protocol.Credentials
import com.tripletriad.protocol.ItemEffect
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.PvpRefusal
import com.tripletriad.protocol.SeedTickets
import com.tripletriad.protocol.Session
import com.tripletriad.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The signed-in player: the account, the profile the server holds for it, and the stats.
 *
 * ### Why this and not [ProfileSession]
 *
 * They are the same idea against two different sources of truth, and the difference is not
 * cosmetic. [ProfileSession] owns its profiles — it creates them, writes them and deletes them,
 * and what is on disk is whatever it last wrote. This one owns nothing: the profile is the
 * server's, and every field here is a *copy* of something that was true when it was fetched.
 *
 * That is why there is no `create`. An account is registered, not created locally, and the
 * character comes back with it.
 *
 * ### Failures are state, not exceptions
 *
 * A sign-in form has to be able to say "that name is taken" and "the server is unreachable" in the
 * same place, so both arrive as [failure] and the screen renders them. Nothing here throws.
 */
class AccountSession internal constructor(
    private val server: ServerConnection,
    private val clock: Clock,
    /**
     * Where the seed stock is written back from [nextSeed], which is not a suspending function.
     *
     * Null in a test that never plays a match. A scope rather than a coroutine started per call so
     * that the writes are cancelled with whatever owns this session, instead of outliving it.
     */
    private val scope: CoroutineScope? = null,
) {
    /** Unspent seeds, oldest first. See [nextSeed]. */
    private var tickets: List<Int> = emptyList()

    /** The player the server holds, or null when nobody is signed in. */
    var player: PlayerState? by mutableStateOf(null)
        private set

    /** True while a request is in flight, so a form can disable its button. */
    var isBusy: Boolean by mutableStateOf(false)
        private set

    /** What the last request refused with, or null. Cleared when the next one starts. */
    var failure: AccountResult<*>? by mutableStateOf(null)
        private set

    /**
     * True once [restore] has run, whatever it found.
     *
     * The difference between "nobody is signed in" and "we have not looked yet", which is what
     * stops the sign-in form from flashing up in front of a player who has a perfectly good stored
     * session.
     */
    var isRestored: Boolean by mutableStateOf(false)
        private set

    /**
     * The name last signed in with on this server, or null.
     *
     * Offered back by the sign-in form, so a returning player whose token has lapsed types a
     * password and not both. Read on [restore] rather than on demand because the form is composed
     * synchronously and a field that fills in a frame later is a field the player has already
     * started typing into.
     *
     * Survives expiry, and does **not** survive [signOut] — that is a deliberate asymmetry. An
     * expired token is the app forgetting; signing out is the player asking to be forgotten, and on
     * a device that gets handed around, honouring that is the whole reason the button exists.
     */
    var lastUsername: String? by mutableStateOf(null)
        private set

    /** The profile, for the screens that only want that. */
    val save: GameSave? get() = player?.save

    /** Which server this session is against — the other half of every key this class derives. */
    val serverId: String get() = server.server.id

    /**
     * Where this account's unsubmitted matches wait, or null when nobody is signed in.
     *
     * Derived from the account name rather than from the profile, unlike the local
     * `SaveRepository.keyFor`: a server-held profile has no creation-date-and-name identity of its
     * own, and the account name is the thing that is stable and unique.
     *
     * And from the **server** as well, because the same name on two hosts is two players — see
     * [accountQueueKey]. This is why the key is read through this property rather than cached: it
     * changes under a switch, and a stale one would drain one server's matches into another.
     */
    val queueKey: String? get() = save?.username?.let { accountQueueKey(server.server.id, it) }

    /**
     * Signs in with a stored token, if there is one that has not expired.
     *
     * Called once on launch. A token the server no longer honours is **cleared** rather than kept
     * to be tried again: it will not start working, and the player is going to have to sign in
     * either way — so the alternative is showing them the form after a needless round trip on every
     * subsequent launch too.
     */
    suspend fun restore() {
        val entry = server.server
        // Before the expiry check below, and read even when there is no usable token, because the
        // expired case is the one where the form is about to be shown and the name is worth most.
        lastUsername = server.session.lastUsername(entry.id)

        val stored = server.session.load(entry.id, clock.nowMillis())
        if (stored == null) {
            isRestored = true
            return
        }

        isBusy = true
        when (val result = server.accounts.me(stored.token)) {
            is AccountResult.Ok -> {
                player = result.value
                Log.i(TAG) { "restored the session for '${stored.username}'" }
            }

            else -> {
                if (result.isUnauthenticated()) server.session.clear(entry.id)
                // Not published as a failure: nobody asked for this, and an "offline" banner in
                // front of a player who has simply not signed in yet would be a message about a
                // request they did not make.
                Log.i(TAG) { "could not restore a stored session: $result" }
            }
        }
        isBusy = false
        isRestored = true
    }

    /** Creates an account and signs into it. */
    suspend fun register(username: String, password: String) =
        authenticate { server.accounts.register(Credentials(username, password)) }

    /** Signs in to an existing account. */
    suspend fun signIn(username: String, password: String) =
        authenticate { server.accounts.signIn(Credentials(username, password)) }

    /**
     * Signs out, locally first.
     *
     * The stored token is cleared and the player is signed out of the app **before** the server is
     * told, and the server's answer is ignored. A sign-out that a dead network could refuse would
     * be a button that sometimes does nothing, and the session expiring server-side on its own is a
     * far smaller problem than that.
     */
    suspend fun signOut() {
        val entry = server.server
        val stored = server.session.load(entry.id, clock.nowMillis())
        player = null
        failure = null
        // Cleared with the token: signing out is the player asking to be forgotten, and leaving
        // their name in the form for whoever picks the device up next answers the wrong question.
        lastUsername = null
        server.session.clear(entry.id)
        stored?.let { server.accounts.signOut(it.token) }
    }

    /**
     * Moves to another server, if it is not the one already in play.
     *
     * ### Why this signs the player out
     *
     * A bearer token means nothing to a host that did not issue it, and the character it names does
     * not exist there. Carrying either across would produce a dashboard showing one server's
     * profile while every request went to another — which is worse than being signed out, because
     * it looks like it is working.
     *
     * What is **not** thrown away is the session left behind: sessions are stored per server, so
     * coming back finds the old token still there and still valid if it has not expired. That is
     * what makes switching cheap enough to do, and it is why [restore] follows.
     *
     * @return whether anything changed.
     */
    suspend fun useServer(entry: ServerEntry): Boolean {
        if (!server.directory.select(entry)) return false

        player = null
        failure = null
        isRestored = false
        // Dropped rather than left for `restore` to overwrite: it suspends, so a recomposition in
        // between would offer the previous host's name on this one's form — the same cross-server
        // bleed the token storage is keyed to prevent, in the one place a player would believe it.
        lastUsername = null
        // The new server may already know us — sessions outlive a switch — so this is a restore and
        // not a sign-out. It sets `isRestored` whatever it finds.
        restore()
        return true
    }

    /**
     * Adopts a profile the server wrote — what a credited match sends back.
     *
     * Deliberately not a merge. The server's copy already includes everything the client knew about
     * plus what the match paid, and reconciling two profiles field by field is how a duplicated
     * reward or a vanished card gets introduced.
     */
    fun adopt(credited: PlayerState) {
        player = credited
    }

    /**
     * Re-reads the profile from the server.
     *
     * For the one case where this client is **not** the author of the change: a refereed match
     * settles server-side, so a PvP win moves MGP and cards somewhere nothing here can see, and the
     * local `GameSave` is stale the moment the board ends. Everywhere else the client writes first
     * and sends afterwards — see [persist] — which is why this direction did not exist until PvP
     * had something to pay out.
     *
     * A failure is logged and left. The next successful read carries the same state, and showing a
     * player an error about a refresh they did not ask for helps nobody.
     */
    suspend fun refresh() {
        val stored = server.session.load(server.server.id, clock.nowMillis()) ?: return
        when (val result = server.accounts.me(stored.token)) {
            is AccountResult.Ok -> adopt(result.value)
            else -> Log.i(TAG) { "could not refresh the profile: $result" }
        }
    }

    /**
     * Sends a profile the player changed outside a match, and adopts it locally either way.
     *
     * Local first, on purpose: the shop screen has already told the player they bought the card,
     * and a network round trip is not something to make them watch. A failed write is logged and
     * left — the next successful save carries the same state, because this profile is the one the
     * client keeps working from.
     */
    suspend fun persist(save: GameSave) {
        val current = player ?: return
        player = current.copy(save = save)

        val stored = server.session.load(server.server.id, clock.nowMillis())
        if (stored == null) {
            Log.w(TAG) { "not signed in; a profile change was not stored" }
            return
        }
        val result = server.accounts.saveProfile(stored.token, save)
        if (result !is AccountResult.Ok) {
            Log.w(TAG) { "a profile change was not stored: $result" }
        }
    }

    /**
     * Uses something from the bag, letting the server decide what came out.
     *
     * ### The one write that does not go local-first
     *
     * [persist] writes to the screen and sends afterwards, because the player has already been told
     * the purchase happened and a round trip is not something to make them watch. This cannot work
     * that way: **the client does not know the answer**. A booster's contents are the server's roll
     * now, so there is nothing to show optimistically — showing a pack and then replacing it with a
     * different one would be worse than a moment's wait.
     *
     * ### Where the operation id comes from
     *
     * Minted here, per call, and that is the right granularity today: nothing in this client
     * retries a request on its own, so one tap is one intent. If an automatic retry is ever added
     * it must reuse the id its first attempt carried — which is what the parameter on
     * `AccountClient.useItem` is for, and why the id is not generated down there.
     *
     * @return what the server did, or null when nobody is signed in or the request failed. A
     *   failure is reported through [failure] as every other request is.
     */
    suspend fun useItem(item: Item): ItemEffect? {
        val stored = server.session.load(server.server.id, clock.nowMillis())
        if (stored == null) {
            Log.w(TAG) { "not signed in; an item was not used" }
            return null
        }

        isBusy = true
        failure = null
        return try {
            when (val result = server.accounts.useItem(stored.token, item, newOperationId())) {
                is AccountResult.Ok -> {
                    player = result.value.player
                    result.value.effect
                }

                else -> {
                    failure = result
                    Log.w(TAG) { "an item was not used: $result" }
                    null
                }
            }
        } finally {
            isBusy = false
        }
    }

    /**
     * The next seed to play a match on, or null when there is none left.
     *
     * ### Why this is synchronous, and what that costs
     *
     * Because the screen that needs it is not: a match is assembled from its seed in one pass, and
     * making that suspend would restructure the whole of `MatchScreen` around a value that is
     * almost always already in hand. So the stock is loaded ahead of time — see [loadTickets] — and
     * this pops from it.
     *
     * What it costs is that **null is a real answer**. A player who has been offline for fifty
     * matches has spent the stock and cannot start another until they reconnect. That is the price
     * of the seed not being theirs to choose, and it is a price worth naming rather than hiding
     * behind a locally invented number that the server would refuse after the match was played.
     *
     * The remainder is written back without waiting, because a seed handed out twice is worse than
     * a seed lost: the second match on it is refused, and the player watches a real match count for
     * nothing.
     */
    fun nextSeed(): Int? {
        val seed = tickets.firstOrNull() ?: return null
        tickets = tickets.drop(1)
        scope?.launch { queueKey?.let { server.tickets.save(it, tickets) } }
        return seed
    }

    /** How many matches can still be started with no network. Rendered by nothing yet. */
    val ticketsHeld: Int get() = tickets.size

    /**
     * Reads the stock from disk, then tops it up from the server if it can.
     *
     * Disk first and unconditionally, so that a launch with no network still has whatever the last
     * online launch left behind — which is the entire reason the stock is written down.
     */
    suspend fun loadTickets() {
        val key = queueKey ?: return
        tickets = server.tickets.load(key)
        if (tickets.size < SeedTickets.TOP_UP_AT) topUpTickets()
    }

    /**
     * Asks the server to top the stock up, and keeps what it answers with.
     *
     * The response is everything the account holds unspent, not just the new ones, so this replaces
     * rather than appends — which is also what repairs a stock that has drifted from the server's
     * idea of it, whatever the reason.
     */
    suspend fun topUpTickets() {
        val key = queueKey ?: return
        val stored = server.session.load(server.server.id, clock.nowMillis()) ?: return

        when (val result = server.accounts.tickets(stored.token)) {
            is AccountResult.Ok -> {
                tickets = result.value.seeds
                server.tickets.save(key, tickets)
            }

            // Offline is the ordinary case here and not a failure to report: the player keeps
            // whatever they already hold and plays from it. `failure` is left alone deliberately,
            // so a top-up that could not happen does not put a note over an unrelated screen.
            else -> Log.i(TAG) { "the seed stock was not topped up: $result" }
        }
    }

    /**
     * Carries out an [Intent] on the account, and adopts the profile the server wrote.
     *
     * The account reading of the list `ProfileGate` reads locally. Each case is one call, and the
     * `when` is exhaustive — which is the point of the sealed type: a sixth intent does not compile
     * until it is handled here as well as there.
     *
     * Nothing is applied optimistically. The server is the one that knows what a thing costs, and a
     * screen that deducted a guessed price and then corrected it would flicker the purse on every
     * purchase.
     */
    suspend fun perform(intent: Intent) {
        val stored = server.session.load(server.server.id, clock.nowMillis())
        if (stored == null) {
            Log.w(TAG) { "not signed in; an intent was dropped" }
            return
        }

        isBusy = true
        failure = null
        val operationId = newOperationId()
        val result = try {
            when (intent) {
                is Intent.Buy -> server.accounts.buy(
                    stored.token,
                    intent.offer.item,
                    intent.formatId,
                    operationId,
                )

                is Intent.SellItem ->
                    server.accounts.sellItem(stored.token, intent.item, operationId)

                is Intent.DiscardItem ->
                    server.accounts.discardItem(stored.token, intent.item, operationId)

                is Intent.SellCard ->
                    server.accounts.sellCard(stored.token, intent.cardId, operationId)

                is Intent.EnterCampaign ->
                    server.accounts.enterCampaign(stored.token, intent.campaignKey, operationId)

                // The catalogue the intent carries is the *local* path's; this one sends nothing,
                // because which box is owed is the server's own catalogue to say.
                is Intent.ClaimStarter -> server.accounts.claimStarter(stored.token, operationId)
            }
        } finally {
            isBusy = false
        }

        when (result) {
            is AccountResult.Ok -> player = result.value
            else -> {
                failure = result
                Log.w(TAG) { "an intent was refused: $result" }
            }
        }
    }

    /**
     * An id for one intent, unique enough that two of them never collide on one account.
     *
     * Not a UUID: `kotlin.uuid` is still opt-in, and what this needs is weaker than uniqueness in
     * the universe — the server scopes ids to the account, so a collision has to happen twice for
     * the *same player* to matter. A clock reading and 64 random bits is well past that.
     */
    private fun newOperationId(): String =
        "${clock.nowMillis().toString(RADIX)}-${Random.nextLong().toULong().toString(RADIX)}"

    /**
     * Runs a sign-in or a registration, stores what came back, and drains what was waiting.
     *
     * The drain is the part worth pointing at. A player who finished matches offline has them
     * queued and unsubmitted; signing in is precisely the moment they become creditable, and doing
     * it here means the dashboard they land on already shows what those matches paid.
     */
    private suspend fun authenticate(request: suspend () -> AccountResult<Session>) {
        isBusy = true
        failure = null

        when (val result = request()) {
            is AccountResult.Ok -> {
                val session = result.value
                val entry = server.server
                server.session.save(
                    entry.id,
                    StoredSession(
                        token = session.token,
                        expiresAt = session.expiresAt,
                        username = session.player.save.username,
                    ),
                )
                player = session.player
                lastUsername = session.player.save.username
                isBusy = false
                // After `isBusy = false` and after the profile is showing: a queue of twenty
                // transcripts is twenty round trips, and holding the sign-in button down for them
                // would make signing in look slow in proportion to how long the player was offline.
                server.reporter.drain(accountQueueKey(entry.id, session.player.save.username))
            }

            else -> {
                failure = result
                isBusy = false
            }
        }
    }

    private companion object {
        const val TAG = "Account"

        /** Base 36 — the shortest an operation id gets without leaving ASCII. */
        const val RADIX = 36
    }
}

/**
 * The message to show for a failed account request.
 *
 * A function rather than a field on [AccountResult] because the wording is the UI's business and
 * the result type lives in the network layer.
 *
 * ### The one message that is not translated
 *
 * [AccountError.MALFORMED_CREDENTIALS] shows `failure.detail`, which is a sentence the **server**
 * wrote — it is the only failure whose reason the client cannot know (which rule, and how it was
 * broken). Passing it through is honest; inventing a generic replacement would tell the player less
 * than the server already told them. It is in the server's locale rather than the player's, which
 * is a real limitation and one the protocol would have to grow a key for to fix.
 */
internal fun AccountResult<*>.message(strings: Strings): String = when (this) {
    is AccountResult.Ok -> ""
    is AccountResult.Offline -> strings[StringKeys.ERROR_OFFLINE]
    is AccountResult.UpdateRequired -> strings[StringKeys.ERROR_UPDATE]
    is AccountResult.Failed -> strings.format(StringKeys.ERROR_STATUS, status.toString())
    is AccountResult.RefusedPvp -> code.message(strings)

    // Deliberately not phrased as an error. The player did nothing wrong and the answer is to
    // wait, so the wording says that — and says how long whenever the server was willing to.
    is AccountResult.Throttled ->
        retryAfterSeconds
            ?.let { strings.format(StringKeys.ERROR_THROTTLED_IN, it.toString()) }
            ?: strings[StringKeys.ERROR_THROTTLED]

    is AccountResult.Refused -> when (failure.error) {
        AccountError.USERNAME_TAKEN -> strings[StringKeys.ERROR_NAME_TAKEN]
        AccountError.INVALID_CREDENTIALS -> strings[StringKeys.ERROR_BAD_CREDENTIALS]
        AccountError.MALFORMED_CREDENTIALS -> failure.detail
        AccountError.UNAUTHENTICATED -> strings[StringKeys.ERROR_EXPIRED]
    }
}

/**
 * Why a player-versus-player request was refused, as a sentence.
 *
 * Its own function rather than an arm of [message], which the eleven branches pushed past the
 * complexity gate — and the split is the right one anyway: this is a vocabulary about *matches*,
 * where everything else there is about accounts.
 *
 * **Not every code earns a sentence.** The five in the last arm mean this client asked for
 * something impossible — a format nobody ships, a move the rules forbid, a card that was never at
 * stake — so they are reported as the failures they are. Writing a player-facing explanation for
 * them would be explaining a bug to somebody who did not cause it.
 */
internal fun PvpRefusal.message(strings: Strings): String = when (this) {
    PvpRefusal.CANNOT_AFFORD -> strings[StringKeys.PVP_ERROR_AFFORD]
    PvpRefusal.TABLE_GONE -> strings[StringKeys.PVP_ERROR_TABLE_GONE]
    PvpRefusal.RULES_NOT_ALLOWED -> strings[StringKeys.PVP_ERROR_RULES]
    PvpRefusal.ALREADY_WAITING -> strings[StringKeys.PVP_ERROR_OWN_TABLE]
    PvpRefusal.ALREADY_PLAYING -> strings[StringKeys.PVP_ERROR_IN_MATCH]
    PvpRefusal.NO_SUCH_PLAYER -> strings[StringKeys.PVP_ERROR_NO_PLAYER]
    PvpRefusal.YOURSELF -> strings[StringKeys.PVP_ERROR_YOURSELF]
    PvpRefusal.NOTHING_OWED -> strings[StringKeys.PVP_ERROR_NOTHING_OWED]
    PvpRefusal.NO_SUCH_FORMAT,
    PvpRefusal.NO_SUCH_MATCH,
    PvpRefusal.NOT_YOUR_TURN,
    PvpRefusal.ILLEGAL_MOVE,
    PvpRefusal.NOT_THEIRS,
    -> strings.format(StringKeys.ERROR_STATUS, name)
}

/**
 * One [AccountSession] for the life of the composition.
 *
 * Keyed on the connection, so it survives every recomposition and every navigation — the same
 * reasoning as [rememberProfileSession], and for a worse failure: losing the session mid-match
 * would mean the finished match had nowhere to be credited.
 */
@Composable
internal fun rememberAccountSession(server: ServerConnection, clock: Clock): AccountSession {
    // The composition's own scope, so the seed stock's writes are cancelled with the app rather
    // than outliving it — see `AccountSession.nextSeed`, which cannot suspend.
    val scope = rememberCoroutineScope()
    return remember(server, clock, scope) { AccountSession(server, clock, scope) }
}
