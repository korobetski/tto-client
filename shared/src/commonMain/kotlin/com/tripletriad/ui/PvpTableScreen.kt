package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.FormatCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.TradeRule
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.protocol.PvpStake
import com.tripletriad.protocol.PvpStakePolicy
import com.tripletriad.protocol.PvpTableRequest
import kotlinx.coroutines.launch

const val PVP_TABLE_OPEN_TEST_TAG: String = "pvp-table-open"
const val PVP_TABLE_MGP_TEST_TAG: String = "pvp-table-mgp"
const val PVP_TABLE_ROULETTE_TEST_TAG: String = "pvp-table-roulette"

fun stakeChipTestTag(mgp: Int): String = "pvp-stake-$mgp"

fun ruleToggleTestTag(key: String): String = "pvp-rule-$key"

fun formatToggleTestTag(id: String): String = "pvp-format-$id"

fun tradeToggleTestTag(trade: TradeRule): String = "pvp-trade-${trade.name.lowercase()}"

@Composable
internal fun PvpTableScreen(
    profile: GameSave,
    catalog: CardCatalog,
    formats: FormatCatalog,
    session: PvpSession,
    invitee: String? = null,
    onOpened: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val stakes = LocalStakes.current
    val scope = rememberCoroutineScope()
    var format by remember(formats) { mutableStateOf(formats.default ?: formats.formats.first()) }
    var rules by remember { mutableStateOf(GameRules()) }
    var roulette by remember { mutableStateOf(false) }
    var trade by remember { mutableStateOf(TradeRule.NONE) }
    // A string and not an `Int`, because a half-typed number is not a number — see [AmountField].
    var wager by remember { mutableStateOf("") }
    // The terms as filled in, waiting only on which deck the host brings. See [HostDeck].
    var proposed by remember { mutableStateOf<PvpTableRequest?>(null) }

    val propose: (PvpTableRequest) -> Unit = { terms ->
        scope.launch {
            if (invitee == null) {
                session.host(terms)
            } else {
                session.challenge(invitee, terms)
            }
            // Back to the lobby either way, refused or not. The lobby is where the table
            // would have appeared and where its refusal is shown — see the note host
            // there — so it is the one screen that can report either outcome.
            onOpened()
        }
    }

    // Below nothing and above the form, because the deck question replaces this screen rather
    // than sitting inside it — the same branch `PvpScreen` takes for a seat, and for the same
    // reason: the terms above are `remember`ed state, and a destination of its own would have
    // thrown them away on the way there and back.
    val pending = proposed
    if (pending != null) {
        HostDeck(
            profile = profile,
            catalog = catalog,
            format = format,
            invitee = invitee,
            terms = pending,
            onChoose = { deck ->
                session.deck = deck
                propose(pending)
                proposed = null
            },
            onBack = { proposed = null },
        )
        return
    }

    CharacterScaffold(
        profile = profile,
        title = if (invitee == null) {
            strings[StringKeys.PVP_HOST]
        } else {
            strings.format(StringKeys.PVP_INVITE_TO, invitee)
        },
        onBack = onBack,
    ) {
        // Only worth asking when there is a choice. The shipped data has three, but a build with
        // one authored format should not put a single-option picker in front of a player.
        if (formats.formats.size > 1) {
            SectionLabel(strings[StringKeys.PVP_FORMAT])
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpaceSm),
                verticalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                for (option in formats.formats) {
                    TtoFilterChip(
                        label = strings[option.nameKey],
                        tag = formatToggleTestTag(option.id),
                        selected = format.id == option.id,
                        onClick = {
                            format = option
                            // The rules do not survive the move: each format allows its own pool,
                            // and carrying a tick across to a format that forbids it would send a
                            // request the server refuses for a reason the screen never showed.
                            rules = option.confine(rules)
                        },
                    )
                }
            }
        }

        SectionLabel(strings[StringKeys.PVP_RULES_PICK])
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            // Straight from the format's pool, so a rule the format does not allow is not offered
            // rather than offered and then refused by the server.
            for (key in format.choosableRuleKeys()) {
                val on = key in rules.activeRuleKeys()
                TtoFilterChip(
                    label = strings[key],
                    tag = ruleToggleTestTag(key),
                    selected = on,
                    // `toggling` rather than a local when: the key-to-rule table lives in `:core`
                    // and is `internal` there, precisely so a second copy cannot drift from it.
                    onClick = { rules = rules.toggling(key, !on) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = roulette,
                onCheckedChange = { roulette = it },
                modifier = Modifier.testTag(PVP_TABLE_ROULETTE_TEST_TAG),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings[StringKeys.PVP_ROULETTE],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = strings[StringKeys.PVP_ROULETTE_HINT],
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        SectionLabel(strings[StringKeys.PVP_STAKE])
        StakeField(
            value = wager,
            onValueChange = { wager = it },
            profile = profile,
        )

        SectionLabel(strings[StringKeys.PVP_TRADE])
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            // Exclusive: a match is played under one trade rule or none, so these are radio
            // buttons wearing a chip's clothes rather than a set of independent flags.
            for (option in TradeRule.entries) {
                TtoFilterChip(
                    label = strings[tradeKey(option)],
                    tag = tradeToggleTestTag(option),
                    selected = trade == option,
                    onClick = { trade = option },
                )
            }
        }

        WideButton(
            label = strings[
                if (invitee == null) StringKeys.PVP_HOST_OPEN else StringKeys.PVP_INVITE,
            ],
            tag = PVP_TABLE_OPEN_TEST_TAG,
            // A wager the server will refuse is not one this screen offers. Both halves of the
            // limit are checked, because they are two different refusals — see [StakeField].
            enabled = !session.isBusy && wager.digits <= stakeLimit(profile, stakes),
            onClick = {
                val terms = PvpTableRequest(
                    formatId = format.id,
                    rules = rules,
                    roulette = roulette,
                    stake = PvpStake(mgp = wager.digits, trade = trade),
                )
                // Nothing to ask under **Random**: the referee splices the hand from the whole
                // collection and the deck the host would choose is ignored. The same decision
                // `PvpScreen.sit` makes for a seat, on the same rule and for the same reason —
                // the *declared* one, not the one a roulette may add on top, which the server
                // draws after this answer is given.
                if (terms.rules.random) {
                    session.deck = ANY_DECK
                    propose(terms)
                } else {
                    proposed = terms
                }
            },
        )
    }
}

/**
 * Which deck this table is opened with, asked once the terms are settled.
 *
 * ### Why the question comes after "Open the table" and not on the form
 *
 * It was a row of chips three inches above the button, between the roulette and the wager, and read
 * as one more term of the proposal. It is not one: the rules, the format and the stake are what is
 * being **offered to somebody else**, and the deck is the only line on that screen the host answers
 * for themselves. So it leaves the form and becomes the step it is everywhere else in the game —
 * [DeckSelectorScreen], showing the rules the deck has to survive and the stake it is being wagered
 * against, both of which are now *decided* rather than half-typed. See [PvpSeat] for the joining
 * half, which arrives at the same screen from the other side.
 *
 * **Still before the request, not after.** The deck cannot wait for an opponent: `PvpJoinRequest`
 * carries the joiner's, the referee deals the moment the second player arrives, and there may be
 * nobody at this end to ask by then — which is why `pvp_tables.host_deck` is written when the table
 * is opened. What moved is where in this screen the question is put, not which side of the wire
 * answers it.
 *
 * ### Filtered by the format, which the lobby could not do
 *
 * The chips this replaces used to live above the lobby's tabs, where the only thing they could test
 * was whether a deck was complete. The format is chosen on this screen, so admissibility can be
 * tested too — and has to be: an FFXIV deck brought to an FFVIII table is five cards its pool does
 * not admit, and the referee answers `UNDEALABLE` rather than dealing them. `CampaignRung`
 * documents the same trap at length; this is the multiplayer end of it. [DeckSelectorScreen] draws
 * from `PveMatches.playableDecks` under the [format] it is handed, so that filtering is the same
 * one, done in one place.
 *
 * @param invitee the player being challenged, or null for an open table.
 */
@Composable
@Suppress("LongParameterList")
private fun HostDeck(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    invitee: String?,
    terms: PvpTableRequest,
    onChoose: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    DeckSelectorScreen(
        profile = profile,
        catalog = catalog,
        format = format,
        terms = MatchTerms(
            // An open table has nobody opposite — that is what makes it open — so the line names
            // what is being opened instead. A challenge does have somebody, and names them.
            opponent = invitee ?: strings[StringKeys.PVP_TABLE_MINE],
            rules = terms.rules,
            roulette = terms.roulette,
            stake = stakeLine(terms.stake, strings),
        ),
        onChoose = onChoose,
        onBack = onBack,
    )
}

@Composable
private fun SectionLabel(text: String) {
    SectionHeader(text = text, modifier = Modifier.padding(top = SpaceMd))
}

/**
 * What is being wagered: typed, with the limit written under it and the usual sums one tap away.
 *
 * ### Why the slider went
 *
 * It was `TtoSlider`, running from nothing to whatever the purse held. Three things were wrong with
 * it and only the first was cosmetic. A thumb eight dp wide across a purse of forty thousand puts
 * the wager on 3,290 when the player meant three thousand, and rounding to a step of ten only
 * narrows that. It offered the *whole* purse, which is a table nobody should be able to open by
 * accident. And it could not express the limit at all: a slider whose track ends at the ceiling
 * says the ceiling is the purse, and one that runs past it lies about what will be accepted.
 *
 * A field states the number the player has in mind. [AmountField] argues the same point from the
 * auction house's side, where it landed first.
 *
 * ### Two limits, named separately
 *
 * A wager is refused for being above the level's ceiling or for being above the purse, and the two
 * are fixed by opposite things — one waits for a level, the other for money. `PvpReferee` answers
 * `STAKE_TOO_HIGH` and `CANNOT_AFFORD` for exactly that reason, and a field that said only "too
 * much" would leave the player to guess which one they were waiting on.
 *
 * ### What this is not
 *
 * Not the rule. The ceiling is `PvpStakePolicy`, the numbers come from this deployment in
 * `ServerInfo`, and the server checks its own copy on every way into a match — see [LocalStakes].
 * A bounded field is a courtesy, exactly as [LocalUnlocks] is.
 */
@Composable
private fun StakeField(value: String, onValueChange: (String) -> Unit, profile: GameSave) {
    val strings = LocalStrings.current
    val stakes = LocalStakes.current
    val ceiling = stakes.ceilingFor(profile)
    val limit = stakeLimit(profile, stakes)
    val wager = value.digits

    AmountField(
        value = value,
        onValueChange = onValueChange,
        label = strings[StringKeys.MGP],
        tag = PVP_TABLE_MGP_TEST_TAG,
        supporting = when {
            wager > ceiling ->
                strings.format(StringKeys.PVP_STAKE_OVER_LIMIT, "${profile.level}", "$ceiling")

            wager > profile.mgp ->
                strings.format(StringKeys.PVP_STAKE_OVER_PURSE, "${profile.mgp}")

            // Legal, and still worth saying out loud on the way in. The same sentence the lobby
            // puts on a table for whoever is reading it — see `PvpScreen.TableRow`.
            stakes.isHeavy(wager, profile.mgp) -> strings[StringKeys.PVP_STAKE_HEAVY]

            else -> strings.format(StringKeys.PVP_STAKE_LIMIT, "$limit")
        },
        isError = wager > limit,
        // Nothing follows it on the form but the trade chips and the button, and neither is
        // reachable from a keyboard's Next.
        imeAction = ImeAction.Done,
    )

    // The rungs. Not a replacement for the field — the point of the field is the number nobody
    // anticipated — but every wager anybody types twice is on this row, and the last one is
    // whatever this player is actually allowed, which is the one figure they cannot work out.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        val rungs = stakeRungs(limit)
        for (rung in rungs) {
            TtoFilterChip(
                label = when {
                    rung == 0 -> strings[StringKeys.PVP_TABLE_FREE]
                    rung == rungs.last() -> strings[StringKeys.PVP_STAKE_MAX]
                    else -> "$rung"
                },
                tag = stakeChipTestTag(rung),
                selected = wager == rung,
                onClick = { onValueChange(if (rung == 0) "" else "$rung") },
            )
        }
    }
}

/**
 * The most this player may put up: the lower of what their level allows and what they hold.
 *
 * Both, because either one alone is a lie in one direction. Level without purse offers a wager the
 * purse cannot cover; purse without level offers one the server refuses.
 */
internal fun stakeLimit(profile: GameSave, stakes: PvpStakePolicy): Int =
    minOf(stakes.ceilingFor(profile), profile.mgp).coerceAtLeast(0)

/**
 * The sums on the chip row, ending at [limit].
 *
 * Round numbers up to the limit and then the limit itself, so the top chip is always the largest
 * legal wager rather than the largest round one — that is the figure a player would otherwise have
 * to derive from their level. Nothing but zero when there is nothing to wager.
 */
internal fun stakeRungs(limit: Int): List<Int> =
    if (limit <= 0) listOf(0) else STAKE_RUNGS.filter { it < limit } + limit

/** A hundred is one win, `MatchRewards.PVP_WIN_MGP`; the rest are the round numbers around it. */
private val STAKE_RUNGS = listOf(0, 50, 100, 250, 500, 1_000, 2_000, 5_000)
