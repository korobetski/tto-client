package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.Immutable
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
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.FormatCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.GameSave
import com.tripletriad.model.TradeRule
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.PvpChallenge
import com.tripletriad.protocol.PvpStake
import com.tripletriad.protocol.PvpTable
import com.tripletriad.protocol.PvpTableRequest
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

const val PVP_TABLES_LOADING_TEST_TAG: String = "pvp-tables-loading"
const val PVP_TABLES_FAILED_TEST_TAG: String = "pvp-tables-failed"
const val PVP_CHALLENGES_LOADING_TEST_TAG: String = "pvp-challenges-loading"
const val PVP_CHALLENGES_FAILED_TEST_TAG: String = "pvp-challenges-failed"

fun challengeRowTestTag(id: String): String = "pvp-invite-$id"

fun challengeAcceptTestTag(id: String): String = "pvp-accept-$id"

fun challengeDropTestTag(id: String): String = "pvp-drop-$id"

fun tableRowTestTag(id: String): String = "pvp-table-$id"

fun tableJoinTestTag(id: String): String = "pvp-join-$id"

internal enum class LobbyTab { TABLES, CHALLENGES }

/**
 * A table or an invitation the player has said yes to, waiting only on which deck they bring.
 *
 * ### Why sitting down is two steps now
 *
 * It was one: the lobby carried a row of deck chips above the tabs, and Join sent the answer that
 * row was holding. That put the deck question **before** both of the things it depends on — which
 * table, and so which rules and what stake — and asked it in a shape multiplayer had invented for
 * itself while the rest of the game used [DeckSelectorScreen]. A player choosing a deck against a
 * list of tables is choosing against nothing.
 *
 * So Join and Accept no longer send anything. They name a seat, and the deck screen the whole game
 * shares takes it from there — the same screen, in the same place in the sequence, as a match
 * against a program.
 *
 * Hosting is deliberately **not** routed through here: see `PvpTableScreen`.
 *
 * @property terms what is being sat down to. A [PvpTableRequest] because that is the shape both
 *   sources already have — a challenge carries one, and a table is one plus a host and a clock.
 */
@Immutable
internal data class PvpSeat(
    val kind: Kind,
    val id: String,
    val opponent: String,
    val terms: PvpTableRequest,
) {
    /** Which of the two lists this seat came from, and so which call takes it. */
    enum class Kind { TABLE, CHALLENGE }

    companion object {
        fun at(table: PvpTable): PvpSeat = PvpSeat(
            kind = Kind.TABLE,
            id = table.id,
            opponent = table.hostName,
            terms = PvpTableRequest(
                formatId = table.formatId,
                rules = table.rules,
                roulette = table.roulette,
                stake = table.stake,
            ),
        )

        fun at(challenge: PvpChallenge): PvpSeat = PvpSeat(
            kind = Kind.CHALLENGE,
            id = challenge.id,
            opponent = challenge.fromName,
            terms = challenge.terms,
        )
    }
}

/**
 * Sits down at a seat the player has chosen and brought a deck to.
 *
 * One entry point for the two lists, because from here they are the same act: the difference
 * between joining a table and accepting an invitation is which endpoint takes it, and that is the
 * only thing this decides. `PvpSession.deck` is read by both, and holds whatever the deck screen
 * just wrote.
 *
 * An extension rather than a method, because it decides nothing about the session's own state —
 * it picks one of two calls the session already offers, from a type the session has no reason to
 * know about.
 */
internal suspend fun PvpSession.take(seat: PvpSeat) = when (seat.kind) {
    PvpSeat.Kind.TABLE -> join(seat.id)
    PvpSeat.Kind.CHALLENGE -> accept(seat.id)
}

@Composable
@Suppress("LongParameterList")
internal fun PvpScreen(
    profile: GameSave,
    session: PvpSession,
    catalog: CardCatalog?,
    formats: FormatCatalog?,
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
    // What the player has said yes to and not yet brought a deck to. See [PvpSeat].
    var seat by remember { mutableStateOf<PvpSeat?>(null) }

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

    /*
     * Taking a seat is two steps, or one when there is nothing to ask.
     *
     * There is nothing to ask under **Random**: the referee splices the hand from the whole
     * collection and the deck the player would choose is ignored, which is why a solo match does
     * not ask either. And nothing to ask when the catalogues have not arrived, since a deck row
     * cannot be drawn without cards to draw it from. Both answer `ANY_DECK`, which is not a deck —
     * it is the absence of a choice, and the referee draws.
     *
     * Decided here rather than inside the deck screen, because a screen that decides it has nothing
     * to show has no way to say so: it would draw nothing, the seat would sit unanswered, and Join
     * would be a tap that did nothing at all.
     */
    val sit: (PvpSeat) -> Unit = { chosen ->
        val format = formats?.get(chosen.terms.formatId)
        if (catalog == null || format == null || chosen.terms.rules.random) {
            session.deck = ANY_DECK
            scope.launch { session.take(chosen) }
        } else {
            seat = chosen
        }
    }

    // Below the effects and not above them, which is the whole of why this is a branch rather than
    // a screen: the lobby keeps polling behind the deck question — a table can lapse while it is
    // being answered — and it is `LaunchedEffect(session.match)` up there that opens the board once
    // the join goes through. A destination of its own would have cancelled both.
    val chosen = seat
    val format = chosen?.let { formats?.get(it.terms.formatId) }
    if (chosen != null && catalog != null && format != null) {
        SeatDeck(
            profile = profile,
            seat = chosen,
            catalog = catalog,
            format = format,
            onChoose = { deck ->
                session.deck = deck
                scope.launch { session.take(chosen) }
                seat = null
            },
            onBack = { seat = null },
        )
        return
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
            LobbyTab.TABLES -> TablesBody(
                session = session,
                now = now,
                scope = scope,
                onHost = onHost,
                onSit = sit,
            )

            LobbyTab.CHALLENGES -> ChallengesBody(
                profile = profile,
                session = session,
                scope = scope,
                onInvite = onInvite,
                onSit = sit,
            )
        }
    }
}

/**
 * The deck question for a seat, in the shape the whole game asks it.
 *
 * Whether it is asked at all is the caller's decision — see `sit`, which answers it before a seat
 * is ever stored.
 */
@Composable
@Suppress("LongParameterList")
private fun SeatDeck(
    profile: GameSave,
    seat: PvpSeat,
    catalog: CardCatalog,
    format: Format,
    onChoose: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    DeckSelectorScreen(
        profile = profile,
        catalog = catalog,
        format = format,
        terms = MatchTerms(
            opponent = seat.opponent,
            rules = seat.terms.rules,
            roulette = seat.terms.roulette,
            // The one line a solo match has no equivalent of, and the reason it is worth a whole
            // screen here rather than a chip: a player about to wager cards should be choosing
            // which cards knowing that they are the stake.
            stake = stakeLine(seat.terms.stake, strings),
        ),
        onChoose = onChoose,
        onBack = onBack,
    )
}

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

@Composable
private fun ColumnScope.TablesBody(
    session: PvpSession,
    now: Long,
    scope: CoroutineScope,
    onHost: () -> Unit,
    onSit: (PvpSeat) -> Unit,
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
                    // Names the seat rather than joining. The deck question comes first now —
                    // see [PvpSeat] — and it is the answer to it that sends the request.
                    onJoin = { onSit(PvpSeat.at(table)) },
                )
            }
        }
    }
}

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

internal fun minutesLeft(table: PvpTable, now: Long): Int {
    val left = (table.expiresAt - now).coerceAtLeast(0L)
    return ((left + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
}

private const val MILLIS_PER_MINUTE = 60_000L

internal fun stakeLine(stake: PvpStake, strings: Strings): String {
    if (stake.isFree) return strings[StringKeys.PVP_TABLE_FREE]

    val parts = buildList {
        if (stake.mgp > 0) add(strings.format(StringKeys.PVP_STAKE_MGP, "${stake.mgp}"))
        if (stake.trade != TradeRule.NONE) add(strings[tradeKey(stake.trade)])
    }
    return parts.joinToString(" $DOT_SEPARATOR ")
}

internal fun tradeKey(trade: TradeRule): String = when (trade) {
    TradeRule.NONE -> StringKeys.PVP_TRADE_NONE
    TradeRule.ONE -> StringKeys.PVP_TRADE_ONE
    TradeRule.DIFF -> StringKeys.PVP_TRADE_DIFF
    TradeRule.DIRECT -> StringKeys.PVP_TRADE_DIRECT
    TradeRule.ALL -> StringKeys.PVP_TRADE_ALL
}

@Composable
private fun ColumnScope.ChallengesBody(
    profile: GameSave,
    session: PvpSession,
    scope: CoroutineScope,
    onInvite: (String) -> Unit,
    onSit: (PvpSeat) -> Unit,
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
                    // As with a table: accepting names the seat, and the deck screen sends it.
                    onAccept = { onSit(PvpSeat.at(challenge)) },
                    onDrop = { scope.launch { session.dropChallenge(challenge.id) } },
                )
            }
        }
    }
}

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
