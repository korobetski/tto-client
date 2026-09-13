package com.tripletriad.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Achievement
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.time.isoDate
import com.tripletriad.ui.theme.LocalTtoColors

/**
 * Every tier of one family, earned or not, with what each asks and pays.
 *
 * The card names only the next rung; this is the rest of the ladder — the dates of the rungs
 * behind, and the targets and rewards of the ones ahead, which until now a player learned by
 * reaching them.
 */
@Composable
internal fun FamilyDetail(
    family: AchievementFamily,
    profile: GameSave,
    cards: Map<Int, Card>,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val subtitle = buildList {
        add(strings[family.category.labelKey])
        if (family.tiers.size > 1) add("${family.earnedCount} / ${family.tiers.size}")
    }

    Column(
        modifier = modifier
            .testTag(ACHIEVEMENT_DETAIL_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            AchievementIcon(
                iconId = family.face.iconId,
                description = strings[family.face.labelKey],
                size = DetailIconSize,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings[family.face.labelKey],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle.joinToString(DOT_SEPARATOR),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        SectionHeader(
            text = strings[StringKeys.ACHIEVEMENT_TIERS],
            modifier = Modifier.padding(top = SpaceSm),
        )
        for (tier in family.tiers) {
            TierRow(tier = tier, isNext = tier == family.next, profile = profile, cards = cards)
        }
    }
}

/**
 * One rung: a tick and its date once earned, a bar while it is the next, a lock beyond that.
 *
 * Merged into one node, so that a screen reader reads a rung as the sentence it is and a test
 * reads its progress without knowing which of its lines carries it.
 */
@Composable
private fun TierRow(
    tier: Achievement,
    isNext: Boolean,
    profile: GameSave,
    cards: Map<Int, Card>,
) {
    val strings = LocalStrings.current
    val earnedAt = profile.achievements[tier.id]

    Row(
        modifier = Modifier
            .testTag(achievementTierTestTag(tier.id))
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        TierMark(earned = earnedAt != null, isNext = isNext)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TierLineGap),
        ) {
            Text(
                text = strings[tier.labelKey],
                color = MaterialTheme.colorScheme.onSurface
                    .copy(alpha = if (earnedAt != null || isNext) 1f else DISABLED_TEXT),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (earnedAt != null) {
                Text(
                    text = strings.format(StringKeys.ACHIEVEMENT_EARNED_ON, isoDate(earnedAt)),
                    color = LocalTtoColors.current.transient,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                val progress = tier.progressFor(profile)
                Text(
                    text = "${progress.current} / ${progress.target}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
                if (isNext) Meter(fraction = progress.fraction)
            }
            RewardNote(achievement = tier, cards = cards, tag = null)
        }
    }
}

@Composable
private fun TierMark(earned: Boolean, isNext: Boolean) {
    Box(modifier = Modifier.size(TierMarkSize), contentAlignment = Alignment.Center) {
        when {
            earned -> Icon(
                imageVector = TtoIcons.Done,
                contentDescription = null,
                tint = LocalTtoColors.current.positive,
                modifier = Modifier.size(TierMarkSize),
            )

            isNext -> Box(
                modifier = Modifier
                    .size(TierMarkSize)
                    .border(RingWidth, MaterialTheme.colorScheme.tertiary, CircleShape),
            )

            else -> Icon(
                imageVector = TtoIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                modifier = Modifier.size(IconSm),
            )
        }
    }
}

private val DetailIconSize = 56.dp
private val TierMarkSize = 20.dp
private val RingWidth = 2.dp
private val TierLineGap = 2.dp
