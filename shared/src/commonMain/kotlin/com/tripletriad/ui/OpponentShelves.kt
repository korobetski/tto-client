package com.tripletriad.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Campaign
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Availability
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc

fun opponentShelfTestTag(shelf: String): String = "opponent-shelf-$shelf"

fun opponentTileTestTag(shelf: String, iconId: String): String = "opponent-tile-$shelf-$iconId"

@Composable
internal fun BlockFilterRow(blocks: List<Int>, selected: Int?, onSelect: (Int?) -> Unit) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        TtoFilterChip(
            label = strings[StringKeys.ALL],
            tag = opponentBlockFilterTestTag(null),
            selected = selected == null,
        ) { onSelect(null) }
        for (block in blocks) {
            TtoFilterChip(
                label = setLabel(strings, block),
                tag = opponentBlockFilterTestTag(block),
                selected = selected == block,
                onClick = { onSelect(block.takeIf { it != selected }) },
            )
        }
    }
}

/**
 * The rows above the roster that answer a question instead of listing everyone.
 *
 * Each is computed off data the profile and the catalog already carry — nothing here is a new
 * field. A shelf that would be empty draws nothing: three empty racks under an empty roster
 * would say less than the [OPPONENT_EMPTY_TEST_TAG] text already does.
 */
@Composable
internal fun OpponentShelves(
    opponents: List<Npc>,
    cards: Map<Int, Card>,
    profile: GameSave,
    onSelect: (Npc) -> Unit,
) {
    val strings = LocalStrings.current

    // Never in `npcWins`, which is exactly the set a win or a loss adds an opponent's `iconId`
    // to — a queue that empties itself as it is played, and needs nothing counted to build.
    val fresh = remember(opponents, profile.npcWins) {
        opponents.filter { it.iconId !in profile.npcWins }
    }
    Shelf(strings[StringKeys.OPPONENTS_NEW], SHELF_NEW, fresh, onSelect)

    // At least one card this opponent can hand over is not in the collection yet. The same
    // [npcCardRewards] a row already draws its cards from — this is a filter over it, not a
    // second source of the fact.
    val wanting = remember(opponents, cards, profile.cards) {
        opponents.filter { npc ->
            npcCardRewards(npc, cards).any { (card, _) -> (profile.cards[card.id] ?: 0) <= 0 }
        }
    }
    Shelf(strings[StringKeys.OPPONENTS_WANTED], SHELF_WANTED, wanting, onSelect)

    // Everyone in `opponents` is already open at this hour — `NpcCatalog.available` filtered on
    // exactly that — so this only has to say which of them are not open at *every* hour, which
    // is the difference between being on this list today and being on it always.
    val timed = remember(opponents) { opponents.filter { it.availability != Availability.Always } }
    Shelf(strings[StringKeys.OPPONENTS_TIMED], SHELF_TIMED, timed, onSelect)
}

@Composable
private fun Shelf(title: String, slug: String, npcs: List<Npc>, onSelect: (Npc) -> Unit) {
    if (npcs.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = SpaceXs),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${npcs.size}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }

    Row(
        modifier = Modifier
            .testTag(opponentShelfTestTag(slug))
            .horizontalScroll(rememberScrollState()).padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        for (npc in npcs) {
            OpponentTile(
                slug = slug,
                npc = npc,
                onClick = { onSelect(npc) },
            )
        }
    }
}

@Composable
private fun OpponentTile(slug: String, npc: Npc, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val name = strings[npc.nameKey]

    Column(
        modifier = Modifier
            .testTag(opponentTileTestTag(slug, npc.iconId))
            .width(ShelfTileWidth)
            .ttoClickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        NpcPortrait(npc = npc, name = name)
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val SHELF_NEW = "new"
private const val SHELF_WANTED = "wanted"
private const val SHELF_TIMED = "timed"

private val ShelfTileWidth = 68.dp

private val CampaignTileWidth = 148.dp

/**
 * The tournaments, as a rack of tiles rather than a stack of identical bars.
 *
 * Three grey bars said nothing to tell a five-round entry-fee ladder from a one-off match, which
 * is exactly backwards: this is the richest content the screen links to. A tile says the ladder's
 * length and its own [Campaign.fee] the way the shop's booster tiles say a pack's name and price.
 */
@Composable
internal fun CampaignPanel(
    campaigns: List<Campaign>,
    profile: GameSave,
    onCampaign: (Campaign) -> Unit,
) {
    if (campaigns.isEmpty()) return
    val strings = LocalStrings.current

    SectionHeader(text = strings[StringKeys.CAMPAIGNS], modifier = Modifier.fillMaxWidth())
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        for (campaign in campaigns) {
            // A ladder still to be earned is dimmed rather than hidden — the same reasoning the
            // entry fee's disabled button follows. A tournament nobody can see is a tournament
            // nobody knows to work towards, and being told what to beat first is the point of
            // gating it.
            CampaignTile(
                campaign = campaign,
                locked = !campaign.isUnlockedFor(profile),
                onClick = { onCampaign(campaign) },
            )
        }
    }
}

@Composable
private fun CampaignTile(campaign: Campaign, locked: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val alpha = if (locked) DISABLED else 1f

    Column(
        modifier = Modifier
            .testTag(campaignRowTestTag(campaign.key))
            .width(CampaignTileWidth)
            .rowSurface()
            .ttoClickable(onClick = onClick)
            .padding(SpaceSm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = campaignTitle(strings, campaign),
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // A locked tile says what stands in the way rather than only that something does —
            // the same distinction the roster's own locked footnotes draw. An achievement gate is
            // the only one a campaign carries; see `Campaign.requiresAchievement`.
            text = if (locked) {
                strings[StringKeys.CAMPAIGN_LOCKED]
            } else {
                strings.format(StringKeys.CAMPAIGN_ROUNDS, campaign.steps.size.toString())
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (campaign.fee > 0) {
            Text(
                text = "${strings[StringKeys.MATCH_FEE]} ${campaign.fee}",
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
