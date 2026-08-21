package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Campaign
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Npc
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.roundToInt
import kotlin.random.Random

const val OPPONENT_LIST_TEST_TAG: String = "opponent-list"
const val OPPONENT_EMPTY_TEST_TAG: String = "opponent-empty"

const val OPPONENT_LOCKED_TEST_TAG: String = "opponent-locked"

const val RANDOM_OPPONENT_TEST_TAG: String = "opponent-random"

/*
 * `TUTORIAL_ROW_TEST_TAG` used to be here, over a row this panel drew above the ladders —
 * `PVEScreen.as:79` draws the tutorial as a bare `tt_tuto` texture in exactly that place.
 *
 * It is gone because the tutorial is no longer one thing: it is a course of twelve lessons with an
 * order and a place you are up to, none of which a row on a list of *opponents* can say. It has a
 * screen and a dashboard entry of its own — see `LessonsScreen`, which explains the move and why
 * there is only one way in.
 */

fun opponentRowTestTag(iconId: String): String = "opponent-row-$iconId"

fun opponentRewardsTestTag(iconId: String): String = "opponent-rewards-$iconId"

@Composable
@Suppress("LongParameterList")
internal fun OpponentScreen(
    profile: GameSave,
    catalog: NpcCatalog,
    cards: Map<Int, Card>,
    hour: Int,
    formatId: String,
    onChallenge: (Npc) -> Unit,
    campaigns: List<Campaign>,
    onCampaign: (Campaign) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    // Keyed on the format, not on the character: who a player may challenge is a property of the
    // match they are looking for. `MODE` used to answer this and the answer was the same only
    // because a character could play one set.
    val opponents = remember(catalog, formatId, hour, profile.level) {
        catalog.available(formatId, hour, profile.level)
    }
    val locked = remember(catalog, formatId, hour, profile.level) {
        catalog.lockedByLevel(formatId, hour, profile.level)
    }

    ScreenScaffold(
        title = strings[StringKeys.OPPONENTS],
        onBack = onBack,
    ) {
        CampaignPanel(
            campaigns = campaigns,
            onCampaign = onCampaign,
        )

        if (opponents.isEmpty()) {
            Text(
                text = strings[StringKeys.NO_OPPONENT],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(OPPONENT_EMPTY_TEST_TAG).padding(vertical = SpaceXl),
            )
        } else {
            // One tap into a match against whoever the roster hands back, rather than scrolling
            // the list to pick. `opponents` is already the unlocked roster — the same list a row
            // is drawn from — so this can never land on somebody still gated by level or hour.
            WideButton(
                label = strings[StringKeys.RANDOM_OPPONENT],
                tag = RANDOM_OPPONENT_TEST_TAG,
                filled = false,
                onClick = { onChallenge(opponents.random(Random)) },
            )

            LazyColumn(
                modifier = Modifier
                    .testTag(OPPONENT_LIST_TEST_TAG)
                    .fillMaxWidth()
                    .padding(top = SpaceSm),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                items(opponents, key = { it.iconId }) { npc ->
                    OpponentRow(
                        npc = npc,
                        cards = cards,
                        owned = profile.cards,
                        onClick = { onChallenge(npc) },
                    )
                }

                // Under the list rather than over it: it is a footnote about what is *not* here,
                // and a player who has not scrolled to the bottom has not run out of opponents yet.
                if (locked > 0) {
                    item(key = LOCKED_KEY) {
                        Text(
                            text = strings.format(StringKeys.OPPONENTS_LOCKED, locked.toString()),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .testTag(OPPONENT_LOCKED_TEST_TAG)
                                .fillMaxWidth()
                                .padding(vertical = SpaceSm),
                        )
                    }
                }
            }
        }
    }
}

private const val LOCKED_KEY = "locked-note"

@Composable
private fun CampaignPanel(
    campaigns: List<Campaign>,
    onCampaign: (Campaign) -> Unit,
) {
    val strings = LocalStrings.current

    SectionHeader(text = strings[StringKeys.CAMPAIGNS], modifier = Modifier.fillMaxWidth())
    for (campaign in campaigns) {
        CampaignRow(
            label = campaignTitle(strings, campaign),
            tag = campaignRowTestTag(campaign.key),
            onClick = { onCampaign(campaign) },
        )
    }
}

@Composable
private fun CampaignRow(label: String, tag: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .padding(bottom = SpaceSm)
            .rowSurface()
            .ttoClickable(onClick = onClick)
            .padding(SpaceMd),
    )
}

@Composable
private fun OpponentRow(
    npc: Npc,
    cards: Map<Int, Card>,
    owned: Map<Int, Int>,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val rewards = remember(npc, cards) { npcCardRewards(npc, cards) }

    Column(
        modifier = Modifier
            .testTag(opponentRowTestTag(npc.iconId))
            .fillMaxWidth()
            .rowSurface()
            .ttoClickable(onClick = onClick)
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            NpcPortrait(npc = npc, name = strings[npc.nameKey])
            Text(
                text = strings[npc.nameKey],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings[npc.level.labelKey],
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }

        Text(
            text = rewardLine(strings, npc),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // The rules line is omitted rather than shown empty: "no special rules" is what an absent
        // line already says, and a row that is always three lines tall wastes a third of a phone
        // list on the majority of opponents that impose nothing.
        val rules = npc.ruleKeys
        if (rules.isNotEmpty()) {
            Text(
                text = rules.joinToString(DOT_SEPARATOR) { strings[it] },
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Omitted rather than shown empty, for the same reason as the rules line. One of the 85
        // opponents drops no card at all — `STR_NPC_MARTINE` — and a caption over nothing would
        // read as a missing image.
        if (rewards.isNotEmpty()) {
            RewardCards(iconId = npc.iconId, rewards = rewards, owned = owned)
        }
    }
}

/**
 * The cards an opponent can hand over, one already owned told apart from one that is not.
 *
 * A copy already in the bag is drawn at full strength and one that is not is dimmed — the same
 * [UNOWNED_ALPHA] the card list dims an unowned thumb by, see `CardListBody.kt`, so "not yet mine"
 * reads the same wherever a card is shown next to others the collection may or may not hold.
 */
@Composable
internal fun RewardCards(iconId: String, rewards: List<Pair<Card, Double>>, owned: Map<Int, Int>) {
    val strings = LocalStrings.current

    Text(
        text = strings[StringKeys.REWARD_CARDS],
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 3.dp),
    )
    Row(
        modifier = Modifier.testTag(opponentRewardsTestTag(iconId)),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        for ((card, rate) in rewards) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val dim = (owned[card.id] ?: 0) <= 0
                CardThumb(
                    card = card,
                    modifier = if (dim) Modifier.alpha(UNOWNED_ALPHA) else Modifier,
                )
                Text(
                    text = "${(rate * PERCENT).roundToInt()}%",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * The cards an opponent can give up, resolved against the profile's own table.
 *
 * An id the collection does not hold is dropped rather than drawn as a hole: `NPCs.as` is
 * per-collection data and this is a belt-and-braces read of it, not a claim that every listed id
 * ships. Shared by [OpponentRow] and [CampaignScreen]'s final-reward line so both name the same
 * cards for the same opponent.
 */
internal fun npcCardRewards(npc: Npc, cards: Map<Int, Card>): List<Pair<Card, Double>> =
    npc.itemRewards.mapNotNull { reward ->
        reward.cardId?.let { id -> cards[id]?.let { it to reward.rate } }
    }

private const val PERCENT = 100

private const val UNOWNED_ALPHA = 0.28f

private fun rewardLine(strings: Strings, npc: Npc): String = buildList {
    add("${strings[StringKeys.DIFFICULTY]} ${npc.difficulty}")
    if (npc.matchFee > 0) add("${strings[StringKeys.MATCH_FEE]} ${npc.matchFee}")
    add("${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}")
    val xp = npc.xpFor(MatchResult.WIN)
    if (xp > 0) add("$xp ${strings[StringKeys.XP]}")
}.joinToString(DOT_SEPARATOR)
