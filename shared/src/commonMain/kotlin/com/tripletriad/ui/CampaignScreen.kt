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
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Npc
import com.tripletriad.ui.theme.LocalTtoColors

const val CAMPAIGN_LIST_TEST_TAG: String = "campaign-list"

const val CAMPAIGN_START_TEST_TAG: String = "campaign-start"

const val CAMPAIGN_STEP_TEST_TAG: String = "campaign-step"

const val CAMPAIGN_FINAL_REWARD_TEST_TAG: String = "campaign-final-reward"

fun campaignRowTestTag(key: String): String = "campaign-row-$key"

@Composable
internal fun CampaignScreen(
    campaign: Campaign,
    profile: GameSave,
    cards: Map<Int, Card> = emptyMap(),
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

        Text(
            text = "${strings[StringKeys.MATCH_FEE]} ${campaign.fee} ${strings[StringKeys.MGP]}",
            color = LocalTtoColors.current.transient,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = SpaceMd),
        )

        // What beating the last rung actually pays — the ladder's own steps already say this one
        // row at a time, but only once scrolled to the bottom. Named here so entering a tournament
        // is a decision made with the payoff in view rather than found at the end of it.
        campaign.steps.lastOrNull()?.npc?.let { champion ->
            FinalReward(npc = champion, cards = cards, owned = profile.cards)
            Spacer(modifier = Modifier.height(SpaceMd))
        }

        WideButton(
            label = strings[StringKeys.START],
            tag = CAMPAIGN_START_TEST_TAG,
            // `isEnabled = (MGP >= 500)`. Disabled rather than hidden, so the reason a ladder
            // cannot be entered is visible next to its price.
            enabled = profile.mgp >= campaign.fee,
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
        NpcPortrait(npc = npc, name = strings[npc.nameKey], size = 36.dp)
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
private fun FinalReward(npc: Npc, cards: Map<Int, Card>, owned: Map<Int, Int>) {
    val strings = LocalStrings.current
    val rewards = remember(npc, cards) { npcCardRewards(npc, cards) }

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
            text = finalRewardLine(strings, npc),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (rewards.isNotEmpty()) {
            RewardCards(iconId = npc.iconId, rewards = rewards, owned = owned)
        }
    }
}

private fun finalRewardLine(strings: Strings, npc: Npc): String = buildList {
    add("${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}")
    val xp = npc.xpFor(MatchResult.WIN)
    if (xp > 0) add("$xp ${strings[StringKeys.XP]}")
}.joinToString(DOT_SEPARATOR)

@Composable
@Suppress("LongParameterList")
internal fun CampaignMatchScreen(
    campaign: Campaign,
    catalog: CardCatalog,
    format: Format,
    pve: PveSession,
    onFinished: () -> Unit,
) {
    var step by remember(campaign.key) { mutableStateOf(Campaign.FIRST_STEP) }
    var result by remember(campaign.key) { mutableStateOf<MatchResult?>(null) }
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

    // Built from the result, so it can only exist once one is known — which is also the only time
    // the panel holding it is on screen.
    val exit = result
        ?.let { campaign.nextStep(step, it) }
        ?.takeIf { campaign.stepAt(it) != null }
        ?.let { next ->
            ScriptExit(StringKeys.NEXT_MATCH) {
                result = null
                step = next
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
        // sharing an icon would otherwise silently keep the board.
        key(step) {
            // One match per rung, opened when the rung opens. `resume` first, so a ladder
            // interrupted mid-rung comes back on the board it was on rather than paying the
            // entry fee for a position the player had already reached.
            LaunchedEffect(pve, entry.npc.iconId, step) {
                pve.resume(against = entry.npc.iconId)
                if (pve.match == null) {
                    pve.open(entry.npc.iconId, format.id)
                }
            }
            PveMatchScreen(
                session = pve,
                catalog = catalog,
                npc = entry.npc,
                onExit = onFinished,
                script = script,
                scriptExit = exit,
                onResult = { result = it },
            )
        }
    }
}

internal fun campaignTitle(strings: Strings, campaign: Campaign): String =
    if (strings.has(campaign.nameKey)) {
        strings[campaign.nameKey]
    } else {
        strings["APP_CAMPAIGN_${campaign.key.uppercase()}"]
    }
