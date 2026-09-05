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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tripletriad.data.Campaign
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

const val CAMPAIGNS_LIST_TEST_TAG: String = "campaigns-list"
const val CAMPAIGNS_EMPTY_TEST_TAG: String = "campaigns-empty"

/**
 * The tournaments: the play root's third tab.
 *
 * They were a horizontally-scrolling rack of 148 dp tiles wedged between the roster's shelves and
 * the roster, which cut the third one off mid-word and told a five-round entry-fee ladder from a
 * one-off match by nothing at all. A tournament is the richest thing this application links to —
 * several matches, a fee to enter and a card at the end — and it now gets the width of the screen
 * and a tab of its own.
 */
@Composable
internal fun CampaignsScreen(
    profile: GameSave,
    campaigns: List<Campaign>,
    waiting: Int,
    onCampaign: (Campaign) -> Unit,
    onTab: (PlayTab) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.PLAY],
        onBack = onBack,
    ) {
        PlayTabs(current = PlayTab.TOURNAMENTS, waiting = waiting, onSelect = onTab)

        if (campaigns.isEmpty()) {
            EmptyNote(text = strings[StringKeys.NO_OPPONENT], tag = CAMPAIGNS_EMPTY_TEST_TAG)
            return@CharacterScaffold
        }

        LazyColumn(
            modifier = Modifier.testTag(CAMPAIGNS_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            items(campaigns, key = { it.key }) { campaign ->
                // A ladder still to be earned is dimmed rather than hidden — the same reasoning
                // the entry fee's disabled button follows. A tournament nobody can see is a
                // tournament nobody knows to work towards, and being told what to beat first is
                // the point of gating it.
                CampaignRow(
                    campaign = campaign,
                    locked = !campaign.isUnlockedFor(profile),
                    onClick = { onCampaign(campaign) },
                )
            }
        }
    }
}

@Composable
private fun CampaignRow(campaign: Campaign, locked: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val alpha = if (locked) DISABLED else 1f

    Row(
        modifier = Modifier
            .testTag(campaignRowTestTag(campaign.key))
            .fillMaxWidth()
            .rowSurface()
            .ttoClickable(onClick = onClick)
            .padding(horizontal = SpaceMd, vertical = SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = campaignTitle(strings, campaign),
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // A locked row says what stands in the way rather than only that something does —
                // the same distinction the roster's own locked footnotes draw. An achievement gate
                // is the only one a campaign carries; see `Campaign.requiresAchievement`.
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
        }

        if (campaign.fee > 0) {
            PriceTag(
                price = campaign.fee,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * MUTED),
                coin = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
            )
        }
    }
}
