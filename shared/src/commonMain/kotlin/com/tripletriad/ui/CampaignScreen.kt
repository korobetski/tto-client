package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Campaign
import com.tripletriad.data.CampaignStep
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.ui.theme.LocalTtoColors

const val CAMPAIGN_LIST_TEST_TAG: String = "campaign-list"

const val CAMPAIGN_START_TEST_TAG: String = "campaign-start"

const val CAMPAIGN_STEP_TEST_TAG: String = "campaign-step"

const val CAMPAIGN_FINAL_REWARD_TEST_TAG: String = "campaign-final-reward"

const val CAMPAIGN_LOCKED_TEST_TAG: String = "campaign-locked"

const val CAMPAIGN_SUMMARY_TEST_TAG: String = "campaign-summary"

const val CAMPAIGN_SUMMARY_DONE_TEST_TAG: String = "campaign-summary-done"

fun campaignRowTestTag(key: String): String = "campaign-row-$key"

fun campaignSummaryRowTestTag(step: Int): String = "campaign-summary-row-$step"

@Composable
@Suppress("LongParameterList")
internal fun CampaignScreen(
    campaign: Campaign,
    profile: GameSave,
    cards: Map<Int, Card> = emptyMap(),
    today: String = "",
    /** Whether any deck this ladder's format admits exists. See [CampaignRung] for the choice. */
    hasDeck: Boolean = true,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    ScreenScaffold(title = campaignTitle(strings, campaign), onBack = onBack) {
        Column(
            modifier = Modifier.testTag(CAMPAIGN_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            for ((step, entry) in campaign.steps.withIndex()) {
                RungRow(step = step, entry = entry)
            }
        }

        // The run this ladder already has under way, if any. A tournament is entered once and can
        // be come back to — see `CampaignRun` — so what this screen offers is not always a
        // purchase.
        val resuming = profile.campaignRun?.takeIf { it.campaignKey == campaign.key }

        Text(
            text = if (resuming != null) {
                strings.format(
                    StringKeys.CAMPAIGN_STEP,
                    "${resuming.step + 1}",
                    "${campaign.steps.size}",
                )
            } else {
                "${strings[StringKeys.MATCH_FEE]} ${campaign.fee} ${strings[StringKeys.MGP]}"
            },
            color = LocalTtoColors.current.transient,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = SpaceMd),
        )

        // What beating the last rung actually pays — the ladder's own steps already say this one
        // row at a time, but only once scrolled to the bottom. Named here so entering a tournament
        // is a decision made with the payoff in view rather than found at the end of it.
        FinalReward(campaign = campaign, cards = cards, owned = profile.cards)
        Spacer(modifier = Modifier.height(SpaceMd))

        val locked = !campaign.isUnlockedFor(profile)
        // Today's entry to this ladder, spent the moment it was paid for — a defeat on the first
        // rung costs the attempt, so a run that is over does not hand it back. Shown as a reason
        // rather than left to the button being mysteriously grey.
        val spent = resuming == null && profile.hasEnteredToday(campaign.key, today)
        // A ladder is played under its **own** format, so a profile whose decks are all of the
        // other block cannot field a hand on any rung of it. Said here, before the fee, because
        // the referee's answer to it is `UNDEALABLE` — a refusal arriving after the entry was
        // bought, on a board that never dealt.
        val undealable = !hasDeck
        val reason = when {
            locked -> StringKeys.CAMPAIGN_LOCKED
            spent -> StringKeys.CAMPAIGN_ENTERED_TODAY
            undealable -> StringKeys.CAMPAIGN_NO_DECK
            else -> null
        }
        if (reason != null) {
            Text(
                text = strings[reason],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .testTag(CAMPAIGN_LOCKED_TEST_TAG)
                    .padding(bottom = SpaceSm),
            )
        }

        WideButton(
            // Resuming is not entering: a run already paid for is continued, and saying "Start"
            // over it would read as a second entry fee about to be taken.
            label = strings[if (resuming != null) StringKeys.CONTINUE else StringKeys.START],
            tag = CAMPAIGN_START_TEST_TAG,
            // `isEnabled = (MGP >= 500)`. Disabled rather than hidden, so the reason a ladder
            // cannot be entered is visible next to its price. A run under way is already bought,
            // so only a *new* entry is priced; a locked ladder is not for sale at all; and today's
            // attempt at this one may already be spent.
            //
            // Every one of these is checked again by `CampaignRewards.enter` on the side that
            // holds the profile, which is what actually decides. This is the button being honest
            // about the answer, not the rule — a hidden button is not a rule, which is the lesson
            // the entry fee itself taught.
            enabled = !locked && !spent && !undealable &&
                (resuming != null || profile.mgp >= campaign.fee),
            onClick = onStart,
        )
    }
}

@Composable
private fun RungRow(step: Int, entry: CampaignStep) {
    val strings = LocalStrings.current
    val npc = entry.npc

    Row(
        modifier = Modifier.fillMaxWidth().rowSurface().padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Text(
            text = "${step + 1}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        // The Card Club's seven rungs are the ones with no portrait in the asset tree, so this is
        // also where the monogram fallback earns its keep — see `NpcPortrait`.
        NpcPortrait(npc = npc, name = strings[npc.nameKey])
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = strings[npc.nameKey],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Omitted when empty, as the opponent list does: an absent line already says
            // "no special rules", and every rung here has some.
            if (npc.ruleKeys.isNotEmpty()) {
                Text(
                    text = npc.ruleKeys.joinToString(DOT_SEPARATOR) { strings[it] },
                    color = LocalTtoColors.current.transient,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun FinalReward(campaign: Campaign, cards: Map<Int, Card>, owned: Map<Int, Int>) {
    val strings = LocalStrings.current
    val champion = campaign.steps.last().npc
    // **The ladder's lot, not the last opponent's drop table.** Beating the final rung is what
    // triggers the prize, but the prize belongs to the tournament — see `CampaignRewards.finish`,
    // where it is drawn once and does not pass through that opponent's table.
    //
    // Falls back to the champion's own drops for a ladder that names no lot yet. Showing nothing
    // there would say the tournament pays nothing, when it pays what its last rung pays plus a
    // multiple of the stake back.
    val rewards = remember(campaign, cards) {
        campaign.finalReward
            .mapNotNull { entry -> entry.cardId?.let { id -> cards[id]?.let { it to entry.rate } } }
            .ifEmpty { npcCardRewards(champion, cards) }
    }

    Column(
        modifier = Modifier.testTag(CAMPAIGN_FINAL_REWARD_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = strings[StringKeys.CAMPAIGN_FINAL_REWARD],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = finalRewardLine(strings, campaign),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (rewards.isNotEmpty()) {
            RewardCards(iconId = champion.iconId, rewards = rewards, owned = owned)
        }
    }
}

/**
 * What finishing the ladder is worth, in one line.
 *
 * The **tournament's** own payout — [Campaign.payout], a multiple of the entry fee — plus what the
 * last rung pays on top of it, since both land in the same breath. A player deciding whether to
 * enter is comparing a total against a fee they are about to hand over, and reporting the halves
 * apart here would make the decision harder rather than more honest.
 *
 * The XP carries the ladder's multiplier for the same reason the MGP carries its payout: what is
 * quoted has to be what arrives.
 */
private fun finalRewardLine(strings: Strings, campaign: Campaign): String {
    val champion = campaign.steps.last().npc
    return buildList {
        add("${campaign.payout + champion.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}")
        val xp = (champion.xpFor(MatchResult.WIN) * campaign.xpMultiplier).toInt()
        if (xp > 0) add("$xp ${strings[StringKeys.XP]}")
    }.joinToString(DOT_SEPARATOR)
}

@Composable
@Suppress("LongParameterList")
internal fun CampaignMatchScreen(
    campaign: Campaign,
    catalog: CardCatalog,
    format: Format,
    pve: PveSession,
    profile: GameSave,
    onFinished: () -> Unit,
    resumedStep: Int? = null,
) {
    // Where the run stands, as the *server* holds it — a ladder is resumed on the rung it was left
    // on, and the rung is not the client's to decide. `remember` seeds from it rather than reading
    // it live: the profile is refreshed as matches settle, and a rung that moved under the board
    // would swap the opponent mid-match.
    var step by remember(campaign.key) {
        mutableStateOf(resumedStep ?: Campaign.FIRST_STEP)
    }
    var result by remember(campaign.key) { mutableStateOf<MatchResult?>(null) }

    // How each rung ended, keyed by its index — what the bilan is drawn from. A map rather than a
    // list because a drawn rung is *replayed*, and the rung's own final outcome is the one worth
    // reporting; writing the same key twice replaces it rather than appending a second row.
    val record = remember(campaign.key) { mutableStateMapOf<Int, MatchResult>() }

    // Bumped to replay a rung a draw settled nothing on. The rung index has not changed and the
    // effect that opens a match is keyed on it, so without this the "play again" exit would
    // leave the finished board on screen and never deal a new one.
    var attempt by remember(campaign.key) { mutableStateOf(0) }

    /*
     * Which deck this rung is being played with. Null until the rung has been answered.
     *
     * Held **here** rather than inside [CampaignRung], and that placement is the rule: `key(step,
     * attempt)` below rebuilds the rung on a replay, so a `remember` inside it would be discarded
     * every time a draw sent the run round again — which is what happened, and it put the deck
     * selector back in front of a player who is not entitled to change decks mid-rung. A tournament
     * entry buys one run with one hand per opponent; a draw is the same rung being finished, not a
     * new one being started.
     *
     * Keyed on [step] and deliberately not on `attempt`: the next rung is a different opponent
     * under different rules and asks again, a replay of this one does not.
     */
    var deck by remember(campaign.key, step) { mutableStateOf<Int?>(null) }

    var reviewing by remember(campaign.key) { mutableStateOf(false) }

    if (reviewing) {
        CampaignSummary(campaign = campaign, record = record, onDone = onFinished)
        return
    }

    val entry = campaign.stepAt(step) ?: return

    val script = remember(entry) {
        MatchScript(
            speakerKey = entry.npc.nameKey,
            lesson = Lesson.opening(entry.messages.start),
            outcomeLines = MatchResult.entries
                .mapNotNull { outcome -> entry.messages.forResult(outcome)?.let { outcome to it } }
                .toMap(),
        )
    }

    /*
     * Where the run goes from here. Built from the result, so it can only exist once one is known
     * — which is also the only time the panel holding it is on screen.
     *
     * **`Campaign.nextStep` is deliberately not consulted.** It answers `FIRST_STEP` for a loss,
     * which restarts the ladder from rung one at no cost — and a tournament whose entry fee buys
     * unlimited retries is not a stake. A tournament here is the hardcore thing its fee implies:
     * a win advances, a loss ends the run, and only a draw is replayed. `nextStep` still models
     * the original ladder and is left alone for whatever else reads it.
     */
    val exit = result?.let { outcome ->
        when {
            outcome == MatchResult.DRAW ->
                ScriptExit(StringKeys.NEXT_MATCH) {
                    result = null
                    attempt += 1
                }

            outcome == MatchResult.WIN && campaign.stepAt(step + 1) != null ->
                ScriptExit(StringKeys.NEXT_MATCH) {
                    result = null
                    step += 1
                }

            // Won the last rung, or lost anywhere: either way the run is over and the only thing
            // left is to read what it came to.
            else -> ScriptExit(StringKeys.CAMPAIGN_RESULTS) { reviewing = true }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = LocalStrings.current.format(
                StringKeys.CAMPAIGN_STEP,
                "${step + 1}",
                "${campaign.steps.size}",
            ),
            color = LocalTtoColors.current.transient,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .testTag(CAMPAIGN_STEP_TEST_TAG)
                .fillMaxWidth()
                .padding(horizontal = SpaceMd),
        )
        // Keyed on the rung rather than trusting the opponent to differ: two rungs of one ladder
        // sharing an icon would otherwise silently keep the board. `attempt` is in the key for the
        // same reason a rung is — a replayed draw is a different match on the same rung.
        //
        // Which is exactly why `deck` is hoisted above this: everything inside the key is rebuilt
        // on a replay, and the deck is the one answer that must survive one.
        key(step, attempt) {
            CampaignRung(
                campaign = campaign,
                entry = entry,
                catalog = catalog,
                format = format,
                pve = pve,
                profile = profile,
                script = script,
                exit = exit,
                deck = deck,
                onDeck = { deck = it },
                onFinished = onFinished,
                onResult = {
                    result = it
                    record[step] = it
                },
            )
        }
    }
}

/**
 * One rung: the deck question, then the board.
 *
 * ### The deck is asked **per rung**, and under the ladder's format
 *
 * A run is four matches, and what beat the last opponent is not what beats the next — so the
 * question is put again between every one of them, exactly as free play puts it before a match.
 *
 * **A replayed draw does not ask again**, and that is the one place this parts company with free
 * play. The entry fee buys a run, and a run is one hand per opponent: a rung a draw settled nothing
 * on is the same rung being finished, so it is played out with the five cards it was started with.
 * Free play is the opposite — see `rematchExit` in `App.kt` — because there is no fee, no ladder
 * and nothing a second deck could be unfair to. That is why [deck] arrives as a parameter: it is
 * held above the `key(step, attempt)` that rebuilds this rung, which is what makes it survive a
 * replay and reset on the next rung.
 *
 * [DeckSelectorScreen] draws from `PveMatches.playableDecks` under **[format]**, the ladder's own,
 * so only decks this tournament admits are offered. That is the half that was missing: a rung used
 * to be opened with whatever [PveSession.deck] still held from somewhere else, and the referee's
 * fallback for `ANY_DECK` — `PveMatches.playerDeck` — takes the first *complete* deck and asks
 * nothing about the format. An FFXIV deck brought to the Balamb ladder is five cards its pool does
 * not admit, so the deal threw and the request came back `UNDEALABLE` on every attempt, which the
 * board reported as a dead connection.
 *
 * ### Which is why "Random" is not `ANY_DECK` here
 *
 * The selector's own Random means "no choice, you draw", and the referee draws format-blind. In a
 * ladder that is the very trap above, so it is resolved on this side to the first deck the format
 * does admit. The player still said "pick for me"; this picks something that can be dealt.
 *
 * A match already in progress is never asked about — [PveSession.resume] runs first, and a player
 * coming back to a board they were mid-way through is not offered a re-deal.
 */
@Composable
@Suppress("LongParameterList")
private fun CampaignRung(
    campaign: Campaign,
    entry: CampaignStep,
    catalog: CardCatalog,
    format: Format,
    pve: PveSession,
    profile: GameSave,
    script: MatchScript,
    exit: ScriptExit?,
    // Null is "not answered yet"; `ANY_DECK` is an answer. The same two states free play uses.
    deck: Int?,
    onDeck: (Int) -> Unit,
    onFinished: () -> Unit,
    onResult: (MatchResult) -> Unit,
) {
    var resumed by remember { mutableStateOf(false) }

    LaunchedEffect(pve, entry.npc.iconId) {
        pve.resume(against = entry.npc.iconId)
        resumed = true
    }
    if (!resumed) return

    val playable = remember(profile.decks, profile.cards, catalog, format) {
        PveMatches.playableDecks(profile, catalog, format)
    }

    if (pve.match == null && deck == null) {
        DeckSelectorScreen(
            profile = profile,
            catalog = catalog,
            format = format,
            terms = MatchTerms(
                opponent = LocalStrings.current[entry.npc.nameKey],
                rules = entry.npc.gameRules(),
            ),
            onChoose = { chosen ->
                onDeck(
                    if (chosen == ANY_DECK) playable.firstOrNull()?.index ?: ANY_DECK else chosen,
                )
            },
            onBack = onFinished,
        )
        return
    }

    LaunchedEffect(pve, entry.npc.iconId, deck) {
        if (pve.match != null) return@LaunchedEffect
        pve.deck = deck ?: ANY_DECK
        // Named as a ladder match, so the referee can waive the opponent's own stake and pay the
        // run's rates. It is a claim the server checks against the run it holds — see
        // `PveMatchRequest.campaignKey`.
        pve.open(entry.npc.iconId, format.id, campaignKey = campaign.key)
    }

    PveMatchScreen(
        session = pve,
        catalog = catalog,
        npc = entry.npc,
        onExit = onFinished,
        script = script,
        again = exit,
        onResult = onResult,
    )
}

/**
 * What a tournament run came to, rung by rung.
 *
 * Rendered in place of the board rather than navigated to, the way `PackRevealScreen` is: a bilan
 * is a *moment at the end of a run*, not a destination. There is nothing for a back stack to
 * restore — the run it describes is over, and its rewards were credited match by match as they
 * were won — so the only way out is forward, to wherever the ladder was entered from.
 *
 * A rung with no entry in [record] is one the run never got to, which is the ordinary case for
 * every rung below the one that ended it.
 */
@Composable
private fun CampaignSummary(
    campaign: Campaign,
    record: Map<Int, MatchResult>,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current
    val lost = record.entries.firstOrNull { it.value == MatchResult.LOSE }?.key
    val won = lost == null && campaign.steps.indices.all { record[it] == MatchResult.WIN }

    ScreenScaffold(title = campaignTitle(strings, campaign), onBack = onDone) {
        Text(
            text = when {
                won -> strings[StringKeys.CAMPAIGN_COMPLETE]
                lost != null ->
                    strings.format(StringKeys.CAMPAIGN_ELIMINATED, "${lost + 1}")
                // Neither won nor lost: the run was left standing on a rung it had not settled.
                else -> strings[StringKeys.CAMPAIGNS]
            },
            color = if (won) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(bottom = SpaceMd),
        )

        Column(
            modifier = Modifier.testTag(CAMPAIGN_SUMMARY_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            for ((step, entry) in campaign.steps.withIndex()) {
                SummaryRow(step = step, entry = entry, outcome = record[step])
            }
        }

        Spacer(modifier = Modifier.height(SpaceMd))

        WideButton(
            label = strings[StringKeys.CONTINUE],
            tag = CAMPAIGN_SUMMARY_DONE_TEST_TAG,
            onClick = onDone,
        )
    }
}

@Composable
private fun SummaryRow(step: Int, entry: CampaignStep, outcome: MatchResult?) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .testTag(campaignSummaryRowTestTag(step))
            .fillMaxWidth()
            .rowSurface()
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Text(
            text = "${step + 1}",
            color = colors.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        NpcPortrait(npc = entry.npc, name = strings[entry.npc.nameKey])
        Text(
            text = strings[entry.npc.nameKey],
            color = colors.onSurface,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when (outcome) {
                MatchResult.WIN -> strings[StringKeys.YOU_WIN]
                MatchResult.LOSE -> strings[StringKeys.YOU_LOSE]
                MatchResult.DRAW -> strings[StringKeys.DRAW]
                null -> strings[StringKeys.CAMPAIGN_NOT_REACHED]
            },
            color = when (outcome) {
                MatchResult.WIN -> colors.tertiary
                MatchResult.LOSE -> colors.error
                MatchResult.DRAW -> LocalTtoColors.current.transient
                null -> colors.onSurface.copy(alpha = FAINT)
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

internal fun campaignTitle(strings: Strings, campaign: Campaign): String =
    if (strings.has(campaign.nameKey)) {
        strings[campaign.nameKey]
    } else {
        strings["APP_CAMPAIGN_${campaign.key.uppercase()}"]
    }
