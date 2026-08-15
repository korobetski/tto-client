package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Achievement
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.GameSave
import com.tripletriad.model.XpTable
import com.tripletriad.time.isoDate
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.roundToInt

const val STATS_TABLE_TEST_TAG: String = "stats-table"
const val STATS_LEVEL_TEST_TAG: String = "stats-level"
const val STATS_ACHIEVEMENTS_TEST_TAG: String = "stats-achievements"
const val STATS_NO_ACHIEVEMENT_TEST_TAG: String = "stats-no-achievement"

/**
 * `stats-<label>` on a counter's **value**, by the key its row is labelled with.
 *
 * On the value and not on the row, so `assertTextEquals` reads the number: a `Row` carrying only a
 * `testTag` is not a merging semantics node and would report no text at all. The label is the
 * translation, and that is `StringsBundleTest`'s to check.
 */
fun statsRowTestTag(labelKey: String): String = "stats-$labelKey"

/** `stats-ac-<id>`, by the achievement's own id — `ac-tt1`, `ac-fob`. */
fun achievementRowTestTag(id: String): String = "stats-$id"

/** `stats-family-ac-tt` — the row a whole tier family collapses into. See [AchievementFamily]. */
fun achievementFamilyTestTag(family: String): String = "stats-family-$family"

/**
 * The character's record and its achievements — the original's `profileScreen`.
 *
 * Two panels side by side on a 1024-wide stage there; one scrolling column here, statistics then
 * achievements, because the second panel is the long one and a phone has no second column to put it
 * in.
 *
 * ### The pie chart is a number
 *
 * `RoundChart` drew wins, defeats and draws as three arcs with the **total** in the middle and no
 * percentage anywhere (`:181-194`). A three-segment pie of numbers the list beside it already
 * prints is decoration; what it was standing in for is the win rate, which the original never wrote
 * down. So [Stats.winRate] is a row like the others. The arcs are Phase 6's business if they are
 * anyone's.
 *
 * Its `_total` also divides by zero on a fresh profile — `0 * 360 / 0` is `NaN` in AS3 and the
 * three ratios come out `NaN` — which draws nothing rather than crashing. [Stats.winRate] returns
 * 0f.
 *
 * ### Every achievement is listed, not only the earned ones
 *
 * `:210-220` walks `PROFILE_DATAS.ACHIEVEMENTS`, so an unearned achievement is invisible and the
 * screen cannot say what there is to aim at — the same defect the collection browser had before
 * `cardListScreen` walked the whole card table. [Requirement.progress] exists precisely so this
 * screen can ask how close the profile is, which a pre-computed Boolean could not answer; see
 * [com.tripletriad.model.Requirement].
 *
 * ### Listed by family, and by tier only inside one
 *
 * Twenty-two entries are five ladders and one standalone. See [AchievementFamily] for why they
 * collapse, and [AchievementRow] for what a row says once they have.
 *
 * Families with something unlocked come first, most recently unlocked first — the original's
 * `sortOn(['unlockDate', 'label'], DESCENDING)`. The date is now **shown** and not merely sorted
 * by: `commonMain` still has no calendar, but rendering one turned out to need arithmetic rather
 * than a dependency. See [isoDate].
 */
@Composable
internal fun StatsScreen(profile: GameSave, onAvatar: () -> Unit, onBack: () -> Unit) {
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
                    AchievementRow(family = family, profile = profile)
                }
            }
        }
    }
}

/**
 * The avatar, the level and how far into it the profile is — the original's `profile.jpg` header.
 *
 * `ProgressBar` between `Level.steps[level - 1]` and `steps[level]` (`:127-134`), with the portrait
 * `GameSave.AVATAR_ID` names beside it.
 *
 * @param onAvatar opens [AvatarScreen], where the portrait is chosen. Null on the dashboard, which
 *   draws the same bar as a summary: the record is where a character is edited, and two ways in
 *   would be two places to keep in step.
 */
@Composable
internal fun LevelBar(profile: GameSave, onAvatar: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        AvatarBadge(
            profile = profile,
            modifier = onAvatar?.let { Modifier.ttoClickable(onClick = it) } ?: Modifier,
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
    // by a zero span — the AS3 read `steps[22]` as `undefined` here and drew nothing.
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

/** One counter: its name on the left, its value on the right. */
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

/**
 * One family: what has been unlocked and when, then what is left and how far off it is.
 *
 * ### The date replaces the counter, it does not join it
 *
 * An unlocked achievement has no progress left to state — `1 / 1`, `30 / 30` — and the row used to
 * print that anyway, above a bar pinned at full. What an unlocked tier has to say is *when*, which
 * is the one thing the save records and the screen never showed. See [isoDate] for why a date can
 * be rendered here at all.
 *
 * The tier still to earn keeps the counter and the bar, on its own line, so the two readings never
 * compete for the same slot.
 */
@Composable
private fun AchievementRow(family: AchievementFamily, profile: GameSave) {
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
            text = strings["${family.face.labelKey}_DESC"],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Absent once every tier is earned: there is nothing left to aim at, and a bar at 100%
        // under a completed family says less than the dates above it already do.
        val next = family.next
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

/** A filled bar — `feathers.controls.ProgressBar`, with no Material 3 counterpart worth the API. */
@Composable
private fun Meter(fraction: Float, colour: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(MeterHeight)
                .clip(MaterialTheme.shapes.small)
                .background(colour),
        )
    }
}

/**
 * One achievement family — the five Triple Team tiers, the six Wheel of Fortune ones — as one row.
 *
 * ### Why the tiers are not twenty-two entries any more
 *
 * Because they are five things, not twenty-two. `Triple Team I` through `V` are the same
 * achievement at five thresholds, and listing them separately spent most of the screen restating a
 * requirement the player had already read, with four dead progress bars pinned at 100% above the
 * one that was moving. What a player wants from this list is *where am I, and what is next*, and
 * that is one line per family.
 *
 * `ac-fob` is a family of one, and needs no special case: the grouping is by the id with its tier
 * number removed, and an id that carries no number is its own family.
 *
 * @property earned the highest tier already unlocked, or null. Highest by catalogue order, which is
 *   threshold order — tiers cannot be earned out of sequence, but a save is a file and this makes
 *   no assumption about which build wrote it.
 * @property next the first tier not yet unlocked, or null once the family is complete. This is what
 *   the progress bar measures; [earned] is what carries a date.
 */
private data class AchievementFamily(
    val key: String,
    val tiers: List<Achievement>,
    val earned: Achievement?,
    val next: Achievement?,
) {
    /** The tier whose name and icon the row wears: what was last done, or what is left to do. */
    val face: Achievement get() = earned ?: next ?: tiers.first()

    val earnedCount: Int get() = tiers.indexOf(earned) + 1
}

/**
 * The catalogue as families, earned first and newest earned first within that.
 *
 * The earned half is the original's `sortOn(['unlockDate'], DESCENDING)`, now keyed on the family's
 * most recent unlock. The unearned half is ordered by how close the profile is, which puts the next
 * thing to aim at at the top of it rather than whichever family the catalogue declares first.
 */
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

private val MeterHeight = 4.dp
private const val PERCENT = 100
