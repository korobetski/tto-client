package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.tripletriad.model.GameSave
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.PvpChallenge
import com.tripletriad.protocol.PvpTable
import com.tripletriad.protocol.PvpTableRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val PVP_HOST_TEST_TAG: String = "pvp-host"
const val PVP_CANCEL_TABLE_TEST_TAG: String = "pvp-cancel-table"
const val PVP_NAME_TEST_TAG: String = "pvp-name"
const val PVP_CHALLENGE_TEST_TAG: String = "pvp-challenge"
const val PVP_NO_TABLE_TEST_TAG: String = "pvp-no-table"

// The one list. `PVP_TABLES_TEST_TAG` is now the open-tables heading rather than a list of its
// own — it still means "there are tables", which is all anything ever asked it.
const val PVP_LOBBY_TEST_TAG: String = "pvp-lobby"
const val PVP_TABLES_TEST_TAG: String = "pvp-tables"

/** The line that says whether there is anybody to play. See [PresenceLine]. */
const val PVP_PRESENCE_TEST_TAG: String = "pvp-presence"
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

fun tableCautionTestTag(id: String): String = "pvp-caution-$id"

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

/**
 * The multiplayer room, read from the top down in the order things are owed.
 *
 * ### Why the two tabs went
 *
 * They were `Tables` and `Invitations`, and the screen opened on the first. An invitation is an
 * *event* — somebody named this player and is waiting on an answer — and it was being filed
 * behind a tab that had to be remembered and pressed. The data was never the problem: one loop
 * refreshed both lists whichever tab was showing, so the invitation was in memory and out of
 * sight at the same time.
 *
 * So there is one column, ordered by what it costs to ignore each thing:
 *
 * 1. **a prize on a timer** — settled *against* the player if they never come;
 * 2. **invitations received** — somebody is waiting;
 * 3. **your own table** — open, and taking up the one table you are allowed;
 * 4. **the other tables** — the only part that is a choice rather than an answer;
 * 5. **invitations you sent**, then the two doors that start something new.
 *
 * Nothing here is a tab, and nothing is hidden behind one.
 *
 * ### The empty room is a page, not a sentence
 *
 * Three players on a server is the ordinary state of a game this size, not a fault. When there is
 * nothing at all — see [LobbyEmpty] — the screen offers the three things that can still be done
 * instead of reporting that nobody is here.
 */
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
    onTab: (PlayTab) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
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
        title = strings[StringKeys.PLAY],
        onBack = onBack,
        snackbar = note,
    ) {
        // The play root's own header, so the three ways to start a match stay in one place and the
        // tab row does not move when one is chosen. It is the only tab row on this screen now.
        PlayTabs(
            current = PlayTab.MULTIPLAYER,
            waiting = session.claims.size + session.challenges.size,
            onSelect = onTab,
        )

        // Above the room and not in place of it: the server is what refuses a table, and a player
        // below the line is better served by reading what is on offer and what it will cost them
        // to join it than by a shut door. See [PvpLocked].
        PvpLocked(profile)

        LobbyBody(
            profile = profile,
            session = session,
            now = now,
            scope = scope,
            onHost = onHost,
            onInvite = onInvite,
            onClaim = onClaim,
            onSit = sit,
        )
    }
}

/**
 * The column, or the page that replaces it when there is nothing in it.
 *
 * "Nothing" is a stricter test than an empty table list: an invitation, a prize or a table of your
 * own each mean the room has something in it, and a lobby still being read means nobody has been
 * asked yet — see `PvpSession.tablesState`, and [LobbyEmpty], which must never stand in for a
 * question that has not come back.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
private fun ColumnScope.LobbyBody(
    profile: GameSave,
    session: PvpSession,
    now: Long,
    scope: CoroutineScope,
    onHost: () -> Unit,
    onInvite: (String) -> Unit,
    onClaim: () -> Unit,
    onSit: (PvpSeat) -> Unit,
) {
    val strings = LocalStrings.current
    val mine = session.myTable
    // An invitation the player sent is not something waiting on them, and it is the same row type
    // either way — so it is told apart once, here, rather than inside the row.
    val received = session.challenges.filterNot { it.fromName.equals(profile.username, true) }
    val sent = session.challenges.filter { it.fromName.equals(profile.username, true) }
    val others = session.tables.filterNot { it.id == mine?.id }
    val bare = session.claims.isEmpty() && session.challenges.isEmpty() && mine == null &&
        others.isEmpty() && session.tablesState == ListState.READY

    if (bare) {
        LobbyEmpty(session = session, onHost = onHost, onInvite = onInvite)
        return
    }

    LazyColumn(
        modifier = Modifier
            .testTag(PVP_LOBBY_TEST_TAG)
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        if (session.claims.isNotEmpty()) {
            item("claims") { ClaimBanner(count = session.claims.size, onClaim = onClaim) }
        }

        // Drawn before any invitation and outside the header, because a room that could not be
        // read has no invitations to head. When the read came back and there is nothing, this is
        // silent — an empty section for an event is a heading with nothing under it.
        when (session.challengesState) {
            ListState.LOADING -> item("invites-state") {
                LoadingNote(PVP_CHALLENGES_LOADING_TEST_TAG)
            }

            ListState.FAILED -> item("invites-state") {
                FailedNote(
                    text = strings[StringKeys.ERROR_OFFLINE],
                    tag = PVP_CHALLENGES_FAILED_TEST_TAG,
                    onRetry = { scope.launch { session.refreshChallenges() } },
                )
            }

            ListState.READY -> Unit
        }

        if (received.isNotEmpty()) {
            item("waiting") { SectionHeader(strings[StringKeys.PVP_WAITING]) }
            items(received, key = { it.id }) { challenge ->
                ChallengeRow(
                    challenge = challenge,
                    mine = false,
                    onAccept = { onSit(PvpSeat.at(challenge)) },
                    onDrop = { scope.launch { session.dropChallenge(challenge.id) } },
                )
            }
        }

        if (mine != null) {
            item("mine") {
                MyTableCard(
                    table = mine,
                    now = now,
                    enabled = !session.isBusy,
                    onCancel = { scope.launch { session.cancelTable(mine.id) } },
                )
            }
        }

        item("tables-state") {
            when {
                session.tablesState == ListState.LOADING && others.isEmpty() ->
                    LoadingNote(PVP_TABLES_LOADING_TEST_TAG)

                session.tablesState == ListState.FAILED && others.isEmpty() -> FailedNote(
                    text = strings[StringKeys.ERROR_OFFLINE],
                    tag = PVP_TABLES_FAILED_TEST_TAG,
                    onRetry = { scope.launch { session.refreshTables() } },
                )

                others.isEmpty() -> EmptyNote(
                    strings[StringKeys.PVP_NO_TABLE],
                    PVP_NO_TABLE_TEST_TAG,
                )

                else -> SectionHeader(
                    text = strings[StringKeys.PVP_TABLES_OPEN] + "  ${others.size}",
                    modifier = Modifier.testTag(PVP_TABLES_TEST_TAG),
                )
            }
        }

        items(others, key = { it.id }) { table ->
            TableRow(
                table = table,
                now = now,
                enabled = !session.isBusy,
                // The reader's own profile, because what a wager *is* depends on who is looking
                // at it — see the row's own note.
                profile = profile,
                // Names the seat rather than joining. The deck question comes first now — see
                // [PvpSeat] — and it is the answer to it that sends the request.
                onJoin = { onSit(PvpSeat.at(table)) },
            )
        }

        if (sent.isNotEmpty()) {
            item("sent") {
                SectionHeader(
                    text = strings[StringKeys.PVP_SENT],
                    modifier = Modifier.padding(top = SpaceSm),
                )
            }
            items(sent, key = { it.id }) { challenge ->
                ChallengeRow(
                    challenge = challenge,
                    mine = true,
                    onAccept = {},
                    onDrop = { scope.launch { session.dropChallenge(challenge.id) } },
                )
            }
        }

        item("doors") {
            Column(
                modifier = Modifier.padding(top = SpaceMd),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                SectionHeader(strings[StringKeys.PVP_FIND])
                PresenceLine(session)
                // Absent rather than disabled when a table is already open: the card above *is*
                // that table, and the server allows one.
                if (mine == null) {
                    WideButton(
                        label = strings[StringKeys.PVP_HOST],
                        tag = PVP_HOST_TEST_TAG,
                        enabled = !session.isBusy,
                        onClick = onHost,
                    )
                }
                InviteByName(busy = session.isBusy, onInvite = onInvite)
            }
        }
    }
}

/**
 * What the room offers when it has nothing in it.
 *
 * Three doors in the order they have a chance of working — open a table and be found, name
 * somebody, or go and play a program — because "nobody is here" is a true sentence that leaves
 * the player at a dead end on the screen they came to for a match.
 *
 * The solo line is the one that is not a door: it costs nothing to say, and it is the honest
 * answer on a server with three people awake.
 */
/**
 * Whether there is anybody to play, said above the door that opens a table.
 *
 * Placed before the door and not after it because it is the one thing that changes the answer:
 * opening a table in an empty room is a wait with nothing at the end of it, and the player could
 * not tell that from the screen — an empty lobby looked the same whether the server had nobody on
 * it or simply nobody hosting.
 *
 * Draws nothing at all until the count arrives, so the line never appears as "nobody is here"
 * while the question is still in flight.
 */
@Composable
private fun PresenceLine(session: PvpSession, modifier: Modifier = Modifier) {
    val line = presenceLine(session.presence, LocalStrings.current) ?: return
    Text(
        text = line,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.testTag(PVP_PRESENCE_TEST_TAG),
    )
}

@Composable
private fun ColumnScope.LobbyEmpty(
    session: PvpSession,
    onHost: () -> Unit,
    onInvite: (String) -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(top = SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            text = strings[StringKeys.PVP_NO_TABLE],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(PVP_NO_TABLE_TEST_TAG),
        )
        Text(
            text = strings[StringKeys.PVP_EMPTY_LEAD],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.bodySmall,
        )
        PresenceLine(session, Modifier.padding(bottom = SpaceSm))

        TtoCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpaceMd),
                verticalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                Text(
                    text = strings[StringKeys.PVP_HOST],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = strings[StringKeys.PVP_HOST_HINT],
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = SpaceSm),
                )
                WideButton(
                    label = strings[StringKeys.PVP_HOST_OPEN],
                    tag = PVP_HOST_TEST_TAG,
                    enabled = !session.isBusy,
                    onClick = onHost,
                )
            }
        }

        TtoCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpaceMd),
                verticalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                Text(
                    text = strings[StringKeys.PVP_CHALLENGE],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                InviteByName(busy = session.isBusy, onInvite = onInvite)
            }
        }

        Text(
            text = strings[StringKeys.PVP_EMPTY_SOLO],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = SpaceMd),
        )
    }
}

/**
 * The one door `onInvite` never had.
 *
 * The call has existed since multiplayer did; the only way to reach it was the tab the rebuild
 * removed, and before that it was a field at the bottom of a list nobody scrolled to. It is on
 * the screen twice on purpose — under the tables when there are some, and as one of the three
 * doors when there are none — because it is the answer to two different questions: "nobody I want
 * to play is here" and "there is nobody here at all".
 */
@Composable
private fun InviteByName(busy: Boolean, onInvite: (String) -> Unit) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
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
            enabled = name.isNotBlank() && !busy,
            // Straight to the terms screen rather than sending from here: an invitation states the
            // same four things a table does, and the screen that states them already exists.
            onClick = { onInvite(name.trim()) },
            modifier = Modifier.testTag(PVP_CHALLENGE_TEST_TAG),
        ) {
            Text(strings[StringKeys.PVP_INVITE])
        }
    }
}

/**
 * The player's own table, as a state and not as a button that changed its name.
 *
 * It used to be the same full-width control as "Host a match", relabelled "Withdraw my table" —
 * so the only way to learn what you had opened, and how long ago, was to remember choosing it.
 * A table is the one thing on this screen that belongs to the reader; it is worth a card that
 * says what it is.
 */
@Composable
private fun MyTableCard(table: PvpTable, now: Long, enabled: Boolean, onCancel: () -> Unit) {
    val strings = LocalStrings.current

    TtoCard(modifier = Modifier.testTag(tableRowTestTag(table.id)).fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpaceMd),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Text(
                text = strings[StringKeys.PVP_TABLE_MINE],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = strings.format(
                    StringKeys.PVP_TABLE_OPEN_SINCE,
                    "${minutesSince(table, now)}",
                ) + DOT_SEPARATOR + stakeLine(table.stake, strings),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                style = MaterialTheme.typography.labelMedium,
            )
            RulesStrip(rules = table.rules, roulette = table.roulette, tag = null)
            WideButton(
                label = strings[StringKeys.PVP_HOST_CANCEL],
                tag = PVP_CANCEL_TABLE_TEST_TAG,
                filled = false,
                enabled = enabled,
                onClick = onCancel,
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
private fun TableRow(
    table: PvpTable,
    now: Long,
    enabled: Boolean,
    profile: GameSave,
    onJoin: () -> Unit,
) {
    val strings = LocalStrings.current
    val stakes = LocalStakes.current
    val ceiling = stakes.ceilingFor(profile)
    // Above what this player's level allows. The server refuses it — on the *joiner's* ceiling,
    // not the host's, which is what stops a levelled account opening a wager a fresh one can be
    // talked into — so the seat is not offered rather than offered and then denied.
    val overLimit = table.stake.mgp > ceiling
    // Legal, and a large share of what this player holds. Whether a wager is large is a question
    // about the reader and not about the table, which is why the house cannot answer it once for
    // everybody and why this is the one part of the policy the server does not enforce.
    val heavy = stakes.isHeavy(table.stake.mgp, profile.mgp)
    // Two presses on a heavy table, one on any other. Keyed on the table so a row scrolled off
    // and back is not a row half-way through agreeing to something.
    var confirming by remember(table.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .testTag(tableRowTestTag(table.id))
            .fillMaxWidth()
            // A plain row. It used to be `selected = !mine`, which tinted every table **except**
            // the player's own — a mark carried by the majority is not a mark. The player's own
            // table is a card of its own now (see `MyTableCard`), so this list is other people's
            // and needs no mark at all. The claim banner is the only tinted thing here, and means
            // something because of it.
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
                text = strings.format(StringKeys.PVP_TABLE_BY, table.hostName),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RowButton(
                label = strings[
                    if (confirming) StringKeys.PVP_JOIN_CONFIRM else StringKeys.PVP_JOIN,
                ],
                tag = tableJoinTestTag(table.id),
                enabled = enabled && !overLimit,
                onClick = {
                    if (heavy && !confirming) confirming = true else onJoin()
                },
            )
        }

        Text(
            text = stakeLine(table.stake, strings) + DOT_SEPARATOR +
                strings.format(StringKeys.PVP_TABLE_EXPIRES, "${minutesLeft(table, now)}"),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
        // Under the figure it is about, in the error colour, and only when there is something to
        // say. A line every row carried would be a line nobody reads — the same argument the row
        // above makes about the tint it used to have.
        val caution = when {
            overLimit -> strings.format(StringKeys.PVP_TABLE_OVER_LIMIT, "$ceiling")
            heavy -> strings[StringKeys.PVP_TABLE_HEAVY]
            else -> null
        }
        if (caution != null) {
            Text(
                text = caution,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag(tableCautionTestTag(table.id)),
            )
        }
        RulesStrip(rules = table.rules, roulette = table.roulette)
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
