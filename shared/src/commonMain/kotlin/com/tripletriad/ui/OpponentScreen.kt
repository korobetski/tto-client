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

const val OPPONENT_LIST_TEST_TAG: String = "opponent-list"
const val OPPONENT_EMPTY_TEST_TAG: String = "opponent-empty"

/** How many opponents the character's level is still holding back. Absent when none are. */
const val OPPONENT_LOCKED_TEST_TAG: String = "opponent-locked"

/** The campaign entry that opens the lesson. */
const val TUTORIAL_ROW_TEST_TAG: String = "tutorial-row"

/** `opponent-row-<iconId>` — unique across both tables, which the NPC `id` is not. */
fun opponentRowTestTag(iconId: String): String = "opponent-row-$iconId"

/** `opponent-rewards-<iconId>` — the drop table's cards. Absent when the opponent drops none. */
fun opponentRewardsTestTag(iconId: String): String = "opponent-rewards-$iconId"

/**
 * Who the profile can challenge — the original's `PVEScreen`.
 *
 * Two filters, both from the data and neither invented here:
 *
 * - **the collection**, so an `ff14_` profile never meets an `ff8_` opponent. `NPCs.LIST` returns
 *   `NPCs[MODE.toUpperCase() + 'NPCS']`, so the tables are disjoint by construction.
 * - **the hour**, because 27 of the 60 ff14 opponents declare an availability window and half of
 *   those wrap midnight. This is the only thing in the app that reads a wall clock, which is why
 *   [com.tripletriad.time.Clock] exists.
 *
 * A row shows what the player needs in order to choose: the level band that sets the XP, the
 * difficulty, the fee, the MGP on a win, **the rules the opponent imposes** — that one matters
 * most, since Reverse or Fallen Ace changes how the whole match is played and the original only
 * revealed it once the board was already up — and **the cards that can drop**.
 *
 * ### Why the drop table is on the row
 *
 * Because it is the reason to play one opponent rather than another, and neither the original nor
 * this port had anywhere to read it: `NPC._itemRewards` decided what a win paid and was visible
 * only by winning. Sixty opponents paying MGP that differs by a few dozen are interchangeable; the
 * two or three cards each of them can drop are not, and a collection is built by choosing between
 * them.
 *
 * The rate is shown with the card. A 25% drop and a 2% drop are different offers, and a thumbnail
 * without one invites the reading that beating them once is enough.
 *
 * @param cards the profile's own collection, by id — the drop tables name ids and this screen has
 *   to draw them. Only card drops are listed: a potion has an icon and no picture, and the row is
 *   already four lines tall.
 */
@Composable
@Suppress("LongParameterList")
internal fun OpponentScreen(
    profile: GameSave,
    catalog: NpcCatalog,
    cards: Map<Int, Card>,
    hour: Int,
    formatId: String,
    onChallenge: (Npc) -> Unit,
    onTutorial: () -> Unit,
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
            onTutorial = onTutorial,
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
            LazyColumn(
                modifier = Modifier.testTag(OPPONENT_LIST_TEST_TAG).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                items(opponents, key = { it.iconId }) { npc ->
                    OpponentRow(npc = npc, cards = cards, onClick = { onChallenge(npc) })
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

/** A `LazyColumn` key for the footnote, which is not an opponent and has no `iconId`. */
private const val LOCKED_KEY = "locked-note"

/**
 * `PVEScreen.as:72-96`'s Campaigns panel: the lesson, then this collection's tournament ladders.
 *
 * Above the list rather than in it, which is where the original puts it — a campaign is not an
 * opponent you pick, and putting these among sixty of them would make them ones.
 *
 * **One ladder each**, not two: the Card Club is the FF8 tournament and the Gold Saucer the FF14
 * one, and `PVEScreen` builds each button behind its own `if (MODE == …)`. Here the catalogue has
 * already filtered, so this takes whatever list it is handed — including an empty one, which is
 * what a collection with no ladder would give.
 */
@Composable
private fun CampaignPanel(
    campaigns: List<Campaign>,
    onTutorial: () -> Unit,
    onCampaign: (Campaign) -> Unit,
) {
    val strings = LocalStrings.current

    SectionHeader(text = strings[StringKeys.CAMPAIGNS], modifier = Modifier.fillMaxWidth())
    CampaignRow(
        label = strings[StringKeys.TUTORIAL],
        tag = TUTORIAL_ROW_TEST_TAG,
        onClick = onTutorial,
    )
    for (campaign in campaigns) {
        CampaignRow(
            label = campaignTitle(strings, campaign),
            tag = campaignRowTestTag(campaign.key),
            onClick = { onCampaign(campaign) },
        )
    }
}

/**
 * One way in — `PVEScreen`'s `tttBtn`, `ccBtn` and `gsBtn`, which are bare textures (`tt_tuto` and
 * two group logos) with no text at all. They get captions here because this port's asset set has no
 * such textures, and a word is a better answer than a missing image.
 */
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
private fun OpponentRow(npc: Npc, cards: Map<Int, Card>, onClick: () -> Unit) {
    val strings = LocalStrings.current
    // Resolved against the profile's own table, so an id the collection does not hold is dropped
    // rather than drawn as a hole: `NPCs.as` is per-collection data and this is a belt-and-braces
    // read of it, not a claim that every listed id ships.
    val rewards = remember(npc, cards) {
        npc.itemRewards.mapNotNull { reward ->
            reward.cardId?.let { id -> cards[id]?.let { it to reward.rate } }
        }
    }

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
            RewardCards(iconId = npc.iconId, rewards = rewards)
        }
    }
}

/**
 * The cards an opponent can drop, each under its chance of dropping.
 *
 * A `Row` and not a wrapping layout: no opponent in either table declares more than three card
 * drops, so this cannot overflow a phone at the thumbnail size the rest of the app uses.
 */
@Composable
private fun RewardCards(iconId: String, rewards: List<Pair<Card, Double>>) {
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
                CardThumb(card = card)
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

/** A drop rate is authored as a fraction — `0.25` — and read as a percentage. */
private const val PERCENT = 100

/**
 * `Difficulty 5 · Match Fee 20 · 47 MGP · 35 XP`.
 * The MGP and XP shown are the **base** payout for a win, before the random top-up
 * ([com.tripletriad.data.MatchRewards]) and before any boon. Showing a range would be more honest
 * still, but `47-67 MGP` invites the reading that the fee is subtracted somewhere in there, and it
 * is not — see [Npc.mgpFor].
 */
private fun rewardLine(strings: Strings, npc: Npc): String = buildList {
    add("${strings[StringKeys.DIFFICULTY]} ${npc.difficulty}")
    if (npc.matchFee > 0) add("${strings[StringKeys.MATCH_FEE]} ${npc.matchFee}")
    add("${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}")
    val xp = npc.xpFor(MatchResult.WIN)
    if (xp > 0) add("$xp ${strings[StringKeys.XP]}")
}.joinToString(DOT_SEPARATOR)
