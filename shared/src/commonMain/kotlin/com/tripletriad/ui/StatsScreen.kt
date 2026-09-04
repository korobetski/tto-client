package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Achievement
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.XpTable
import com.tripletriad.time.isoDate
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.roundToInt

const val STATS_TABLE_TEST_TAG: String = "stats-table"
const val STATS_LEVEL_TEST_TAG: String = "stats-level"
const val STATS_ACHIEVEMENTS_TEST_TAG: String = "stats-achievements"

/** The way into what the counters above it are a summary of. */
const val STATS_HISTORY_TEST_TAG: String = "stats-history"
const val STATS_NO_ACHIEVEMENT_TEST_TAG: String = "stats-no-achievement"

fun statsRowTestTag(labelKey: String): String = "stats-$labelKey"

fun achievementRowTestTag(id: String): String = "stats-$id"

fun achievementFamilyTestTag(family: String): String = "stats-family-$family"

fun achievementRewardTestTag(id: String): String = "stats-reward-$id"

@Composable
internal fun StatsScreen(
    profile: GameSave,
    cards: Map<Int, Card>,
    onAvatar: () -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val families = remember(profile) { rankedFamilies(profile) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.PROFILE], onBack = onBack) {
        LevelBar(profile, onAvatar = onAvatar)

        Column(
            modifier = Modifier
                .testTag(STATS_TABLE_TEST_TAG)
                .fillMaxWidth()
                .padding(top = SpaceMd),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StatRow(StringKeys.WINS, "${profile.stats.wins}")
            StatRow(StringKeys.DEFEATS, "${profile.stats.defeats}")
            StatRow(StringKeys.DRAWS, "${profile.stats.draws}")
            // Derived, not stored: `STARTED_MATCHES - ENDED_MATCHES`. See [GameSave.forfeits].
            StatRow(StringKeys.FORFEITS, "${profile.forfeits}")
            StatRow(StringKeys.MATCHES, "${profile.stats.played}")
            StatRow(StringKeys.WIN_RATE, "${(profile.stats.winRate * PERCENT).roundToInt()}%")
            StatRow(StringKeys.MGP, "${profile.mgp}")
            StatRow(
                StringKeys.BOONS,
                "${strings[StringKeys.MGP]} ×${profile.boons.mgp}$DOT_SEPARATOR" +
                    "${strings[StringKeys.XP]} ×${profile.boons.xp}",
            )
        }

        // Under the counters and above the achievements, which is where it belongs in the
        // sentence this screen is: here is the tally, here is what it is made of, here is what it
        // has earned. The counters are the summary of a list that until now had nowhere to live.
        RowButton(
            label = strings[StringKeys.HISTORY],
            tag = STATS_HISTORY_TEST_TAG,
            onClick = onHistory,
        )

        SectionHeader(
            text = strings[StringKeys.ACHIEVEMENTS_LIST],
            modifier = Modifier.padding(top = SpaceLg),
        )

        if (families.isEmpty()) {
            // Unreachable while [AchievementCatalog] has 22 members, and asserted anyway: an empty
            // catalogue should say so rather than render as a screen that lost its second half.
            EmptyNote(strings[StringKeys.NO_ACHIEVEMENT], STATS_NO_ACHIEVEMENT_TEST_TAG)
        } else {
            LazyColumn(
                modifier = Modifier
                    .testTag(STATS_ACHIEVEMENTS_TEST_TAG)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                items(families, key = { it.key }) { family ->
                    AchievementRow(family = family, profile = profile, cards = cards)
                }
            }
        }
    }
}

@Composable
internal fun LevelBar(
    profile: GameSave,
    onAvatar: (() -> Unit)? = null,
    avatarTag: String = AVATAR_TEST_TAG,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        AvatarBadge(
            profile = profile,
            modifier = onAvatar?.let { Modifier.ttoClickable(onClick = it) } ?: Modifier,
            tag = avatarTag,
        )
        Box(modifier = Modifier.weight(1f)) { LevelMeter(profile) }
    }
}

@Composable
private fun LevelMeter(profile: GameSave) {
    val strings = LocalStrings.current
    val floor = XpTable.thresholdFor(profile.level)
    val ceiling = XpTable.thresholdFor(profile.level + 1)
    // At the top of the table there is no next threshold, so the bar is full rather than dividing
    // by a zero span.
    val span = ceiling - floor
    val fraction = if (span <= 0L) 1f else ((profile.xp - floor).toFloat() / span).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.testTag(STATS_LEVEL_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = "${strings[StringKeys.LEVEL]} ${profile.level}$DOT_SEPARATOR" +
                "${profile.xp} ${strings[StringKeys.XP]}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Meter(fraction = fraction, colour = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun StatRow(labelKey: String, value: String) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rowSurface()
            .padding(horizontal = SpaceMd, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            text = strings[labelKey],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(statsRowTestTag(labelKey)),
        )
    }
}

@Composable
private fun AchievementRow(
    family: AchievementFamily,
    profile: GameSave,
    cards: Map<Int, Card>,
) {
    val strings = LocalStrings.current
    val earned = family.earned

    Column(
        modifier = Modifier
            .testTag(achievementFamilyTestTag(family.key))
            .fillMaxWidth()
            .rowSurface(selected = earned != null)
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            AchievementIcon(
                iconId = family.face.iconId,
                description = strings[family.face.labelKey],
                size = 28.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings[family.face.labelKey],
                    color = MaterialTheme.colorScheme.onSurface
                        .copy(alpha = if (earned != null) 1f else 0.65f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Only where there is something to count. `ac-fob` is one achievement, and
                // "1 / 1" beside its name would be a tier ladder it does not have.
                if (family.tiers.size > 1) {
                    Text(
                        text = "${family.earnedCount} / ${family.tiers.size}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            if (earned != null) {
                Text(
                    text = isoDate(profile.achievements.getValue(earned.id)),
                    color = LocalTtoColors.current.transient,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(achievementRowTestTag(earned.id)),
                )
            }
        }

        Text(
            text = markup(strings["${family.face.labelKey}_DESC"]),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // The reward of whatever the player can still reach — the *next* rung, not the face.
        // Showing the face's would tell someone who has just earned tier I what they have already
        // been paid, and leave the 5 000 MGP at the top of the ladder invisible until they are all
        // but standing on it. Once the family is finished there is no next rung and the face's own
        // reward is the right thing to show, as a record of what it paid.
        val next = family.next
        RewardNote(achievement = next ?: family.face, cards = cards)

        // Absent once every tier is earned: there is nothing left to aim at, and a bar at 100%
        // under a completed family says less than the dates above it already do.
        if (next != null) {
            val progress = next.progressFor(profile)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                Text(
                    text = strings.format(StringKeys.NEXT_TIER, strings[next.labelKey]),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${progress.current} / ${progress.target}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(achievementRowTestTag(next.id)),
                )
            }
            Meter(fraction = progress.fraction, colour = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/**
 * "Reward: Tozol Huatotl", or nothing at all.
 *
 * The rewards were invisible until this: the catalogue has always carried them, `credit` has
 * always paid them, and a player had no way to learn that finishing a tribe was worth anything.
 * A ladder nobody knows pays is a ladder nobody climbs.
 *
 * A card reward is named from the card table, so it reads as the card and not as an id; MGP is
 * formatted through its own key because the currency's name is translated (PGS in French) and a
 * bare number would say nothing.
 */
@Composable
private fun RewardNote(achievement: Achievement, cards: Map<Int, Card>) {
    if (!achievement.hasReward) return
    val strings = LocalStrings.current

    val parts = buildList {
        achievement.reward?.let { add(itemName(strings, it, cards)) }
        if (achievement.mgpReward > 0) {
            add(strings.format(StringKeys.ACHIEVEMENT_REWARD_MGP, "${achievement.mgpReward}"))
        }
    }

    Text(
        text = strings.format(StringKeys.ACHIEVEMENT_REWARD, parts.joinToString(DOT_SEPARATOR)),
        color = LocalTtoColors.current.transient,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(achievementRewardTestTag(achievement.id)),
    )
}

private data class AchievementFamily(
    val key: String,
    val tiers: List<Achievement>,
    val earned: Achievement?,
    val next: Achievement?,
) {
    val face: Achievement get() = earned ?: next ?: tiers.first()

    val earnedCount: Int get() = tiers.indexOf(earned) + 1
}

private fun rankedFamilies(profile: GameSave): List<AchievementFamily> {
    val families = AchievementCatalog.all
        .groupBy { it.id.trimEnd { character -> character.isDigit() } }
        .map { (key, tiers) ->
            AchievementFamily(
                key = key,
                tiers = tiers,
                earned = tiers.lastOrNull { profile.hasAchievement(it.id) },
                next = tiers.firstOrNull { !profile.hasAchievement(it.id) },
            )
        }
    val (started, untouched) = families.partition { it.earned != null }
    return started.sortedByDescending { profile.achievements[it.earned?.id] ?: 0L } +
        untouched.sortedByDescending { it.face.progressFor(profile).fraction }
}

private const val PERCENT = 100
