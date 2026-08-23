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
import com.tripletriad.protocol.PveRefusal
import com.tripletriad.protocol.PvpRefusal
import com.tripletriad.protocol.SeedTickets
import com.tripletriad.protocol.Session
import com.tripletriad.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

class AccountSession internal constructor(
    private val server: ServerConnection,
    private val clock: Clock,
    private val scope: CoroutineScope? = null,
) {
    private var tickets: List<Int> = emptyList()

    var player: PlayerState? by mutableStateOf(null)
        private set

    var isBusy: Boolean by mutableStateOf(false)
        private set

    var failure: AccountResult<*>? by mutableStateOf(null)
        private set

    var isRestored: Boolean by mutableStateOf(false)
        private set

    var lastUsername: String? by mutableStateOf(null)
        private set

    val save: GameSave? get() = player?.save

    val serverId: String get() = server.server.id

    val queueKey: String? get() = save?.username?.let { accountQueueKey(server.server.id, it) }

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

    suspend fun register(username: String, password: String) =
        authenticate { server.accounts.register(Credentials(username, password)) }

    suspend fun signIn(username: String, password: String) =
        authenticate { server.accounts.signIn(Credentials(username, password)) }

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

    suspend fun deleteAccount(password: String): Boolean {
        val entry = server.server
        val stored = server.session.load(entry.id, clock.nowMillis()) ?: return false
        val name = save?.username ?: stored.username

        isBusy = true
        failure = null
        val result = server.accounts.deleteAccount(stored.token, Credentials(name, password))
        isBusy = false

        if (result is AccountResult.Ok) {
            player = null
            failure = null
            lastUsername = null
            tickets = emptyList()
            server.session.clear(entry.id)
            Log.i(TAG) { "the account was deleted at the player's request" }
        } else {
            failure = result
            // The account name is not in this line and the password certainly is not — `LogSecrecy`
            // in the server makes the same promise on its side. The result names the refusal.
            Log.w(TAG) { "an account deletion was refused: $result" }
        }
        return result is AccountResult.Ok
    }

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

    fun adopt(credited: PlayerState) {
        player = credited
    }

    suspend fun refresh() {
        val stored = server.session.load(server.server.id, clock.nowMillis()) ?: return
        when (val result = server.accounts.me(stored.token)) {
            is AccountResult.Ok -> adopt(result.value)
            else -> Log.i(TAG) { "could not refresh the profile: $result" }
        }
    }

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

    fun nextSeed(): Int? {
        val seed = tickets.firstOrNull() ?: return null
        tickets = tickets.drop(1)
        scope?.launch { queueKey?.let { server.tickets.save(it, tickets) } }
        return seed
    }

    val ticketsHeld: Int get() = tickets.size

    suspend fun loadTickets() {
        val key = queueKey ?: return
        tickets = server.tickets.load(key)
        if (tickets.size < SeedTickets.TOP_UP_AT) topUpTickets()
    }

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

    suspend fun perform(intent: Intent): IntentOutcome {
        val before = player?.save
        val stored = server.session.load(server.server.id, clock.nowMillis())
        if (stored == null) {
            Log.w(TAG) { "not signed in; an intent was dropped" }
            return IntentOutcome.UNREACHABLE
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

                is Intent.SellAllItems ->
                    server.accounts.sellAllItems(stored.token, intent.item, operationId)

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

        return when (result) {
            is AccountResult.Ok -> {
                player = result.value
                if (result.value.save == before) IntentOutcome.REFUSED else IntentOutcome.APPLIED
            }

            else -> {
                failure = result
                Log.w(TAG) { "an intent was refused: $result" }
                IntentOutcome.UNREACHABLE
            }
        }
    }

    private fun newOperationId(): String =
        "${clock.nowMillis().toString(RADIX)}-${Random.nextLong().toULong().toString(RADIX)}"

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
                //
                // **And what they credited is adopted.** It was not, for as long as this call has
                // existed: the sign-in landed on the profile as it was *before* the queue was
                // submitted, so a player who had played offline saw a dashboard that was already
                // out of date by the time it was drawn — and stayed that way until the next
                // launch. See `MatchReporter.drain`.
                server.reporter
                    .drain(accountQueueKey(entry.id, session.player.save.username))
                    ?.let { adopt(it) }
            }

            else -> {
                failure = result
                isBusy = false
            }
        }
    }

    private companion object {
        const val TAG = "Account"

        const val RADIX = 36
    }
}

internal fun AccountResult<*>.message(strings: Strings): String = when (this) {
    is AccountResult.Ok -> ""
    is AccountResult.Offline -> strings[StringKeys.ERROR_OFFLINE]
    is AccountResult.UpdateRequired -> strings[StringKeys.ERROR_UPDATE]
    is AccountResult.Failed -> strings.format(StringKeys.ERROR_STATUS, status.toString())
    is AccountResult.RefusedPvp -> code.message(strings)

    // One sentence for five of the six codes, unlike the player-versus-player refusals above. Each
    // of those means "this screen's idea of the match is wrong", and the answer to all of them is
    // the same: re-read it. A player who is told *which* way their client was out of date learns
    // nothing they can act on.
    //
    // `UNDEALABLE` is the exception and had to be pulled out: it is not staleness, re-reading does
    // not fix it, and the retry button under that sentence never once helped. It is a deck the
    // format does not admit, and saying so is the only thing that lets the player act.
    is AccountResult.RefusedPve -> when (code) {
        PveRefusal.UNDEALABLE -> strings[StringKeys.ERROR_UNDEALABLE]
        else -> strings[StringKeys.ERROR_STALE_MATCH]
    }

    // Deliberately not phrased as an error. The player did nothing wrong and the answer is to
    // wait, so the wording says that — and says how long whenever the server was willing to.
    is AccountResult.Throttled ->
        retryAfterSeconds
            ?.let { strings.format(StringKeys.ERROR_THROTTLED_IN, it.toString()) }
            ?: strings[StringKeys.ERROR_THROTTLED]

    is AccountResult.Refused -> failure.error.message(strings, failure.detail)
}

// Lifted out of the `when` above rather than nested inside it. Four more arms in one function put
// it over detekt's complexity gate the moment a fifth kind of refusal arrived, and the sentence
// the account errors deserve is not the sentence a match refusal deserves.
private fun AccountError.message(strings: Strings, detail: String): String = when (this) {
    AccountError.USERNAME_TAKEN -> strings[StringKeys.ERROR_NAME_TAKEN]
    AccountError.INVALID_CREDENTIALS -> strings[StringKeys.ERROR_BAD_CREDENTIALS]
    AccountError.MALFORMED_CREDENTIALS -> detail
    AccountError.UNAUTHENTICATED -> strings[StringKeys.ERROR_EXPIRED]
}

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

@Composable
internal fun rememberAccountSession(server: ServerConnection, clock: Clock): AccountSession {
    // The composition's own scope, so the seed stock's writes are cancelled with the app rather
    // than outliving it — see `AccountSession.nextSeed`, which cannot suspend.
    val scope = rememberCoroutineScope()
    return remember(server, clock, scope) { AccountSession(server, clock, scope) }
}
