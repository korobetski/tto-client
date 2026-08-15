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
import com.tripletriad.data.FormatCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.TradeRule
import com.tripletriad.protocol.PvpStake
import com.tripletriad.protocol.PvpTableRequest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val PVP_TABLE_OPEN_TEST_TAG: String = "pvp-table-open"
const val PVP_TABLE_MGP_TEST_TAG: String = "pvp-table-mgp"
const val PVP_TABLE_ROULETTE_TEST_TAG: String = "pvp-table-roulette"

/** `pvp-rule-<key>` — one rule the host may tick. */
fun ruleToggleTestTag(key: String): String = "pvp-rule-$key"

/** `pvp-format-<id>` — one format the host may play in. */
fun formatToggleTestTag(id: String): String = "pvp-format-$id"

/** `pvp-trade-<rule>` — one trade rule. */
fun tradeToggleTestTag(trade: TradeRule): String = "pvp-trade-${trade.name.lowercase()}"

/**
 * Stating the terms of a match: the format, the rules, and what it is played for.
 *
 * ### One screen, both ways into a match
 *
 * A table offers these terms to the lobby; an invitation offers the same ones to one named person.
 * They were not the same for most of this feature's life — an invitation carried a wager and
 * nothing else, so accepting one always played the default format under whatever the roulette drew.
 * That made naming your rules something you could do for strangers and not for a friend.
 *
 * They are now the same request, checked by the same function on the server — see
 * `PvpReferee.checkTerms` — and stated on the same screen. [invitee] is the whole difference:
 * null offers the terms to everybody, a name offers them to one person.
 *
 * ### The host chooses, and until now nobody did
 *
 * Every PvP match this game has ever played used `Roulette.augment(GameRules(), …)` — one to three
 * rules drawn at random, with no way to ask for any of them or to refuse any of them. That was
 * defensible while PvP was a queue, because two strangers have no way to agree on anything. It
 * stops being defensible the moment they can read each other's terms first.
 *
 * ### Why Roulette is a checkbox and not the thirteenth chip
 *
 * `RULE_ROULETTE` really is one of the sixteen keys, and it is deliberately not offered as one.
 * `GameRules.roulette` is what `RULES_W['RULE_ROULETTE']` counts, and what the Wheel of Fortune
 * achievements read — so it means *a draw happened*, which is a claim only the server may make.
 * Ticking it here would credit a roulette win for a match that never drew. The checkbox therefore
 * sets a separate flag on the request, and the server performs the draw and sets the field.
 */
@Composable
internal fun PvpTableScreen(
    profile: GameSave,
    formats: FormatCatalog,
    session: PvpSession,
    /** Who this is aimed at, or null to offer it to the lobby. See the KDoc. */
    invitee: String? = null,
    onOpened: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var format by remember(formats) { mutableStateOf(formats.default ?: formats.formats.first()) }
    var rules by remember { mutableStateOf(GameRules()) }
    var roulette by remember { mutableStateOf(false) }
    var trade by remember { mutableStateOf(TradeRule.NONE) }
    var mgp by remember { mutableStateOf(0) }

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
        Text(
            text = strings.format(StringKeys.PVP_STAKE_MGP, "$mgp"),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
        )
        // Bounded by the purse, because the server refuses a wager the host cannot cover — and a
        // slider that can be dragged into a refusal is a slider that lies about what it offers.
        TtoSlider(
            value = mgp.toFloat(),
            onValueChange = { mgp = (it / STEP).roundToInt() * STEP },
            tag = PVP_TABLE_MGP_TEST_TAG,
            modifier = Modifier.fillMaxWidth(),
            valueRange = 0f..profile.mgp.toFloat().coerceAtLeast(0f),
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
            enabled = !session.isBusy,
            onClick = {
                scope.launch {
                    val terms = PvpTableRequest(
                        formatId = format.id,
                        rules = rules,
                        roulette = roulette,
                        stake = PvpStake(mgp = mgp, trade = trade),
                    )
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
            },
        )
    }
}

/** A heading inside a screen's column. The same weight the lobby's own headings use. */
@Composable
private fun SectionLabel(text: String) {
    SectionHeader(text = text, modifier = Modifier.padding(top = SpaceMd))
}

/** Wagers move in tens: a slider that can land on 37 MGP is precision nobody wanted. */
private const val STEP = 10
