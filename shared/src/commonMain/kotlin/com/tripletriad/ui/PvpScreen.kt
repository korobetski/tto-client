package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.GameSave
import com.tripletriad.model.TradeRule
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.PvpChallenge
import com.tripletriad.protocol.PvpStake
import com.tripletriad.protocol.PvpTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val PVP_HOST_TEST_TAG: String = "pvp-host"
const val PVP_CANCEL_TABLE_TEST_TAG: String = "pvp-cancel-table"
const val PVP_NAME_TEST_TAG: String = "pvp-name"
const val PVP_CHALLENGE_TEST_TAG: String = "pvp-challenge"
const val PVP_NO_CHALLENGE_TEST_TAG: String = "pvp-no-challenge"
const val PVP_NO_TABLE_TEST_TAG: String = "pvp-no-table"
const val PVP_LIST_TEST_TAG: String = "pvp-challenges"
const val PVP_TABLES_TEST_TAG: String = "pvp-tables"
const val PVP_LOBBY_TABS_TEST_TAG: String = "pvp-lobby-tabs"
const val PVP_CLAIM_BANNER_TEST_TAG: String = "pvp-claim-banner"
const val PVP_CLAIM_BANNER_ACTION_TEST_TAG: String = "pvp-claim-banner-go"
const val PVP_NOTE_TEST_TAG: String = "pvp-note"

/**
 * The two states an empty lobby list can be in other than empty — see [ListState].
 *
 * Three tags for one region on purpose: `assertDoesNotExist` on a list says nothing about *which*
 * of the three the screen settled on, and telling a player the wrong one is the bug these exist to
 * catch.
 */
const val PVP_TABLES_LOADING_TEST_TAG: String = "pvp-tables-loading"
const val PVP_TABLES_FAILED_TEST_TAG: String = "pvp-tables-failed"
const val PVP_CHALLENGES_LOADING_TEST_TAG: String = "pvp-challenges-loading"
const val PVP_CHALLENGES_FAILED_TEST_TAG: String = "pvp-challenges-failed"

/** `pvp-invite-<id>` — one invitation row. */
fun challengeRowTestTag(id: String): String = "pvp-invite-$id"

/** `pvp-accept-<id>` — the button that turns an invitation into a match. */
fun challengeAcceptTestTag(id: String): String = "pvp-accept-$id"

/** `pvp-drop-<id>` — declining one, or withdrawing one you sent. */
fun challengeDropTestTag(id: String): String = "pvp-drop-$id"

/** `pvp-table-<id>` — one open table. */
fun tableRowTestTag(id: String): String = "pvp-table-$id"

/** `pvp-join-<id>` — the button that turns a table into a match. */
fun tableJoinTestTag(id: String): String = "pvp-join-$id"

/** `pvp-deck-<slot>` — one deck to bring. `pvp-deck-any` leaves it to the server. */
fun pvpDeckTestTag(slot: Int): String =
    if (slot == ANY_DECK) "pvp-deck-any" else "pvp-deck-$slot"

/** Which half of the lobby is showing. */
internal enum class LobbyTab { TABLES, CHALLENGES }

/**
 * Finding somebody to play — the original's `PVPScreen`, which never worked.
 *
 * ### There is no original to be faithful to
 *
 * `PVPScreen.as` is 363 lines around a socket protocol where **27 of its 29 handlers are dead
 * code**; the user list it drew is assigned and only `trace`d, and the call that would have
 * refreshed it is commented out. So this screen is designed rather than ported.
 *
 * ### Why this is a list of tables and not a queue any more
 *
 * It *was* a queue, and the argument for it is worth keeping because it was a good one: a lobby
 * listing everybody available is the wrong shape for a game with few players connected at once,
 * since an empty list says "nobody is here" and ends the session where a queue says "waiting" and
 * pairs the moment somebody else taps the same button.
 *
 * What broke it is that a match now has **terms**. A queue can only pair people who have agreed to
 * nothing, and a player dropped into a wager they never saw has not agreed to it — no amount of
 * "waiting" beats being shown what you are about to risk. So the host states the rules and the
 * stake, everybody can read them, and joining is a decision instead of a coin toss.
 *
 * The empty list is the price, and it is honest: nobody *is* there, and a player who can see that
 * can go and do something else rather than watch a spinner that was never going to resolve.
 *
 * ### It polls only while it is on screen
 *
 * The [LaunchedEffect] below is the whole subscription. Leaving the screen cancels it, which is
 * what stops a request a second running behind the shop. See [PvpSession].
 */
@Composable
@Suppress("LongParameterList")
internal fun PvpScreen(
    profile: GameSave,
    session: PvpSession,
    now: Long,
    onMatch: () -> Unit,
    onHost: () -> Unit,
    onInvite: (String) -> Unit,
    onClaim: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(LobbyTab.TABLES) }
    val note = rememberNoteHost(PVP_NOTE_TEST_TAG)

    // Every refusal this screen can provoke — a stake nobody can cover, a table already open, an
    // invitation to somebody who is not there — used to be recorded on `session.failure` and read
    // by nothing at all. A player tapped Host, the server said no, and the screen did nothing.
    //
    // Keyed on the failure so each new one shows: `NoteHost.show` dismisses whatever is on screen
    // first, so a second refusal replaces the first rather than queueing behind it.
    LaunchedEffect(session.failure) {
        session.failure?.let { note.show(it.message(strings)) }
    }

    // Three things are being waited for and one loop covers them: a table being joined, an
    // invitation arriving, and somebody else opening a table. None has a notification to arrive on.
    LaunchedEffect(session) {
        session.refreshChallenges()
        session.refreshClaims()
        session.watchLobby()
    }

    // The instant a match exists — however it arrived — the board takes over. Written as an effect
    // rather than checked in the loop above so that a match resumed at launch lands here too.
    LaunchedEffect(session.match) {
        if (session.match != null) onMatch()
    }

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.MULTIPLAYER],
        onBack = onBack,
        snackbar = note,
    ) {
        if (session.claims.isNotEmpty()) {
            ClaimBanner(count = session.claims.size, onClaim = onClaim)
        }

        // Above the tabs, because it governs both of them: the deck is brought to a table this
        // player hosts, a table they join and an invitation they accept alike.
        DeckPicker(
            profile = profile,
            selected = session.deck,
            onSelect = { session.deck = it },
        )

        ScreenTabs(
            tabs = listOf(
                strings[StringKeys.PVP_TABLES] to screenTabTestTag("tables"),
                strings[StringKeys.PVP_CHALLENGE] to screenTabTestTag("invites"),
            ),
            selected = tab.ordinal,
            onSelect = { index -> tab = LobbyTab.entries[index] },
            modifier = Modifier.testTag(PVP_LOBBY_TABS_TEST_TAG),
        )

        when (tab) {
            LobbyTab.TABLES ->
                TablesBody(session = session, now = now, scope = scope, onHost = onHost)
            LobbyTab.CHALLENGES -> ChallengesBody(
                profile = profile,
                session = session,
                scope = scope,
                onInvite = onInvite,
            )
        }
    }
}

/**
 * Which deck to bring, asked once for the whole lobby.
 *
 * ### Why it is here and not on the way into each match
 *
 * PvE asks inside the match, and `DeckSelectorScreen` explains at length why it has to: under the
 * Random rule the hand is dealt from the whole collection and the question is not worth asking, and
 * whether Random is in force is not known until the roulette has been drawn. None of that reasoning
 * survives the crossing to PvP. **The server deals**, before either client has been told anything,
 * so there is no moment between the roulette and the deal for a client to be asked in. The choice
 * has to be made in advance or not at all.
 *
 * Made once rather than at each button, then. A player brings the same five cards to whatever they
 * end up playing, and a picker on the Join button — and another on the Accept button, and another
 * on the host screen — would be the same question asked three times with three chances to disagree.
 *
 * ### Only complete decks, and Automatic is not one of them
 *
 * The filter is exactly the server's: `PveMatches.playerDeck` reads a slot only `if (isComplete)`
 * and otherwise falls back, so offering a half-built deck would be offering something that silently
 * would not be played. Deliberately **not** filtered by format, unlike the PvE selector — the lobby
 * holds tables in several formats at once and there is no one format to filter against. That
 * matches what the server does with the slot, which also does not consult the format.
 *
 * Absent entirely for a profile with no complete deck, because then there is nothing to choose:
 * `playerDeck` falls back to five owned cards, which is the one thing that profile can play.
 */
@Composable
private fun DeckPicker(profile: GameSave, selected: Int, onSelect: (Int) -> Unit) {
    val strings = LocalStrings.current
    val decks = remember(profile.decks) {
        profile.decks.withIndex().filter { it.value.isComplete }
    }
    if (decks.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            text = strings[StringKeys.PVP_DECK],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            TtoFilterChip(
                label = strings[StringKeys.PVP_DECK_ANY],
                tag = pvpDeckTestTag(ANY_DECK),
                selected = selected == ANY_DECK,
                onClick = { onSelect(ANY_DECK) },
            )
            for ((slot, deck) in decks) {
                TtoFilterChip(
                    label = deckLabel(strings, deck, slot),
                    tag = pvpDeckTestTag(slot),
                    selected = selected == slot,
                    onClick = { onSelect(slot) },
                )
            }
        }
    }
}

/**
 * A prize waiting to be collected, at the top of the lobby.
 *
 * Worth a banner rather than a row in a list, because the deadline is real: a claim nobody makes is
 * settled by the server, which picks the strongest card and not necessarily the one this player
 * wanted. Somebody who cannot see that they are owed something will find out by being given
 * something else.
 */
@Composable
private fun ClaimBanner(count: Int, onClaim: () -> Unit) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(PVP_CLAIM_BANNER_TEST_TAG)
            .fillMaxWidth()
            // The one tinted thing in the lobby, which is what makes the tint mean something:
            // a prize on a timer is the only item here that will be settled *against* the player
            // if they ignore it.
            .rowSurface(selected = true)
            .ttoClickable(onClick = onClaim)
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            text = strings.format(StringKeys.PVP_CLAIM_PENDING, "$count"),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        RowButton(
            label = strings[StringKeys.PVP_CLAIM],
            tag = PVP_CLAIM_BANNER_ACTION_TEST_TAG,
            onClick = onClaim,
        )
    }
}

/** The lobby proper: what is on offer, and the button to offer something. */
@Composable
private fun ColumnScope.TablesBody(
    session: PvpSession,
    now: Long,
    scope: CoroutineScope,
    onHost: () -> Unit,
) {
    val strings = LocalStrings.current
    val mine = session.myTable

    if (mine == null) {
        WideButton(
            label = strings[StringKeys.PVP_HOST],
            tag = PVP_HOST_TEST_TAG,
            enabled = !session.isBusy,
            onClick = onHost,
        )
    } else {
        WideButton(
            label = strings[StringKeys.PVP_HOST_CANCEL],
            tag = PVP_CANCEL_TABLE_TEST_TAG,
            filled = false,
            enabled = !session.isBusy,
            onClick = { scope.launch { session.cancelTable(mine.id) } },
        )
    }

    if (session.tables.isEmpty()) {
        // "Nobody is here" only once somebody has been asked, and never when nobody could be —
        // see `PvpSession.tablesState`. An empty list is three different things and only one of
        // them is worth telling a player.
        when (session.tablesState) {
            ListState.LOADING -> LoadingNote(PVP_TABLES_LOADING_TEST_TAG)
            ListState.READY ->
                EmptyNote(strings[StringKeys.PVP_NO_TABLE], PVP_NO_TABLE_TEST_TAG)

            ListState.FAILED -> FailedNote(
                text = strings[StringKeys.ERROR_OFFLINE],
                tag = PVP_TABLES_FAILED_TEST_TAG,
                onRetry = { scope.launch { session.refreshTables() } },
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .testTag(PVP_TABLES_TEST_TAG)
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            items(session.tables, key = { it.id }) { table ->
                TableRow(
                    table = table,
                    mine = table.id == mine?.id,
                    now = now,
                    enabled = !session.isBusy,
                    onJoin = { scope.launch { session.join(table.id) } },
                )
            }
        }
    }
}

/**
 * One table: who is offering it, on what terms, and a way in.
 *
 * The terms are the row's whole reason for existing — a lobby that listed only names would be the
 * queue again with a longer path to the same blind match. `RulesStrip` draws them with the same
 * captions the board does, so what is read here and what is played there cannot drift apart.
 */
@Composable
private fun TableRow(
    table: PvpTable,
    mine: Boolean,
    now: Long,
    enabled: Boolean,
    onJoin: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .testTag(tableRowTestTag(table.id))
            .fillMaxWidth()
            // A plain row. It used to be `selected = !mine`, which tinted every table **except**
            // the player's own — so in a lobby of six, five were marked and one was not, and a
            // mark carried by the majority is not a mark. The row already says whose table it is
            // in words, on its first line, in whichever language the player reads; the tint was
            // saying the same thing again and worse. See the claim banner below, which is now the
            // only tinted thing on this screen and means something because of it.
            .rowSurface()
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            Text(
                text = if (mine) {
                    strings[StringKeys.PVP_TABLE_MINE]
                } else {
                    strings.format(StringKeys.PVP_TABLE_BY, table.hostName)
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // A host joining their own table would be a match against themselves, which the server
            // refuses — so the button is absent rather than offered and then denied.
            if (!mine) {
                RowButton(
                    label = strings[StringKeys.PVP_JOIN],
                    tag = tableJoinTestTag(table.id),
                    enabled = enabled,
                    onClick = onJoin,
                )
            }
        }

        Text(
            text = stakeLine(table.stake, strings) + DOT_SEPARATOR +
                strings.format(StringKeys.PVP_TABLE_EXPIRES, "${minutesLeft(table, now)}"),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
        RulesStrip(rules = table.rules, roulette = table.roulette)
    }
}

/**
 * Whole minutes before a table lapses, floored at zero.
 *
 * Shown because a table **expires** — `PvpMatchRow.TABLE_MILLIS`, five minutes — and until now it
 * did so silently: a host sat watching the lobby, their row disappeared, and the Host button came
 * back with nothing said. Rounded up, so a table with thirty seconds left reads "1 min" rather than
 * "0 min" for its last minute of life.
 */
internal fun minutesLeft(table: PvpTable, now: Long): Int {
    val left = (table.expiresAt - now).coerceAtLeast(0L)
    return ((left + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
}

private const val MILLIS_PER_MINUTE = 60_000L

/** What a table is played for, in one line. */
internal fun stakeLine(stake: PvpStake, strings: Strings): String {
    if (stake.isFree) return strings[StringKeys.PVP_TABLE_FREE]

    val parts = buildList {
        if (stake.mgp > 0) add(strings.format(StringKeys.PVP_STAKE_MGP, "${stake.mgp}"))
        if (stake.trade != TradeRule.NONE) add(strings[tradeKey(stake.trade)])
    }
    return parts.joinToString(" $DOT_SEPARATOR ")
}

/** The caption for a trade rule. */
internal fun tradeKey(trade: TradeRule): String = when (trade) {
    TradeRule.NONE -> StringKeys.PVP_TRADE_NONE
    TradeRule.ONE -> StringKeys.PVP_TRADE_ONE
    TradeRule.DIFF -> StringKeys.PVP_TRADE_DIFF
    TradeRule.DIRECT -> StringKeys.PVP_TRADE_DIRECT
    TradeRule.ALL -> StringKeys.PVP_TRADE_ALL
}

/** Inviting somebody by name, and the invitations standing either way. */
@Composable
private fun ColumnScope.ChallengesBody(
    profile: GameSave,
    session: PvpSession,
    scope: CoroutineScope,
    onInvite: (String) -> Unit,
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(strings[StringKeys.USERNAME]) },
            modifier = Modifier.weight(1f).testTag(PVP_NAME_TEST_TAG),
        )
        TextButton(
            // Trimmed here as well as on the server, for the reason `Credentials.looksValid`
            // gives: a round trip to be told about a trailing space is a round trip wasted.
            enabled = name.isNotBlank() && !session.isBusy,
            // Straight to the terms screen rather than sending from here: an invitation states the
            // same four things a table does, and the screen that states them already exists.
            onClick = { onInvite(name.trim()) },
            modifier = Modifier.testTag(PVP_CHALLENGE_TEST_TAG),
        ) {
            Text(strings[StringKeys.PVP_INVITE])
        }
    }

    if (session.challenges.isEmpty()) {
        when (session.challengesState) {
            ListState.LOADING -> LoadingNote(PVP_CHALLENGES_LOADING_TEST_TAG)
            ListState.READY ->
                EmptyNote(strings[StringKeys.PVP_NO_CHALLENGE], PVP_NO_CHALLENGE_TEST_TAG)

            ListState.FAILED -> FailedNote(
                text = strings[StringKeys.ERROR_OFFLINE],
                tag = PVP_CHALLENGES_FAILED_TEST_TAG,
                onRetry = { scope.launch { session.refreshChallenges() } },
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .testTag(PVP_LIST_TEST_TAG)
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            items(session.challenges, key = { it.id }) { challenge ->
                ChallengeRow(
                    challenge = challenge,
                    mine = challenge.fromName.equals(profile.username, ignoreCase = true),
                    onAccept = { scope.launch { session.accept(challenge.id) } },
                    onDrop = { scope.launch { session.dropChallenge(challenge.id) } },
                )
            }
        }
    }
}

/**
 * One invitation.
 *
 * Both directions are listed, and they are not the same row: an invitation this player *sent* has
 * nothing to accept, only to withdraw. Showing an Accept on it would be offering them a match
 * against themselves.
 */
@Composable
private fun ChallengeRow(
    challenge: PvpChallenge,
    mine: Boolean,
    onAccept: () -> Unit,
    onDrop: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(challengeRowTestTag(challenge.id))
            .fillMaxWidth()
            // Plain, for the reason `TableRow` gives: the first line already says whether this
            // invitation was sent or received.
            .rowSurface()
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (mine) {
                    strings.format(StringKeys.PVP_SENT_TO, challenge.toName)
                } else {
                    strings.format(StringKeys.PVP_FROM, challenge.fromName)
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stakeLine(challenge.stake, strings),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
            )
            RulesStrip(
                rules = challenge.terms.rules,
                roulette = challenge.terms.roulette,
                tag = null,
            )
        }
        if (!mine) {
            RowButton(
                label = strings[StringKeys.PVP_ACCEPT],
                tag = challengeAcceptTestTag(challenge.id),
                onClick = onAccept,
            )
        }
        RowButton(
            label = strings[if (mine) StringKeys.CANCEL else StringKeys.PVP_DECLINE],
            tag = challengeDropTestTag(challenge.id),
            onClick = onDrop,
        )
    }
}
