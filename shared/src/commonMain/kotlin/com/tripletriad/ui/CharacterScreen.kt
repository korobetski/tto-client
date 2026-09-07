package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Achievement
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchRecord
import com.tripletriad.model.XpTable
import com.tripletriad.time.isoDate
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.roundToInt

const val PROFILE_TABS_TEST_TAG: String = "profile-tabs"

/** The summary panel — the stacked bar and the counters read off it. */
const val STATS_TABLE_TEST_TAG: String = "stats-table"

const val STATS_LEVEL_TEST_TAG: String = "stats-level"
const val STATS_COLLECTION_TEST_TAG: String = "stats-collection"
const val STATS_ACHIEVEMENTS_TEST_TAG: String = "stats-achievements"
const val STATS_NO_ACHIEVEMENT_TEST_TAG: String = "stats-no-achievement"

fun statsRowTestTag(labelKey: String): String = "stats-$labelKey"

fun statsSetTestTag(slug: String): String = "stats-set-$slug"

fun achievementRowTestTag(id: String): String = "stats-$id"

fun achievementFamilyTestTag(family: String): String = "stats-family-$family"

fun achievementRewardTestTag(id: String): String = "stats-reward-$id"

internal enum class CharacterTab {
    SUMMARY,

    ACHIEVEMENTS,

    HISTORY,
}

/**
 * Who this character is, what they have won, and what they have.
 *
 * ### Three tabs rather than three screens
 *
 * The record, the achievements and the history were a screen, a list at the bottom of it and a
 * full-width button between the two — which put the history at `lobby → avatar → character →
 * history`, four levels deep for the material the counters above it are a *summary of*. They are
 * one place seen three ways, so they are one root with three tabs, the same shape
 * [CollectionScreen] and [StoreScreen] already have.
 *
 * ### Why the eight counters became a bar and a number
 *
 * Wins, defeats and draws are one quantity split three ways; eight rows of identical weight said
 * they were eight unrelated facts, and gave the win rate — the only number anybody opens this
 * screen for — exactly the prominence of the draw count. Forfeits are a subtraction
 * ([GameSave.forfeits]) and read as a note. The MGP row is gone: the app bar above it has said the
 * same number all along.
 */
@Composable
@Suppress("LongParameterList")
internal fun CharacterScreen(
    profile: GameSave,
    catalog: CardCatalog?,
    records: List<MatchRecord>,
    isHistoryLoading: Boolean,
    opponents: NpcCatalog?,
    initial: CharacterTab,
    onAvatar: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    var tab by remember { mutableStateOf(initial) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.PROFILE], onBack = onBack) {
        ScreenTabs(
            tabs = listOf(
                strings[StringKeys.SUMMARY] to screenTabTestTag("summary"),
                strings[StringKeys.ACHIEVEMENTS] to screenTabTestTag("achievements"),
                strings[StringKeys.HISTORY_TAB] to screenTabTestTag("history"),
            ),
            selected = tab.ordinal,
            onSelect = { index -> tab = CharacterTab.entries[index] },
            modifier = Modifier.testTag(PROFILE_TABS_TEST_TAG),
        )

        when (tab) {
            CharacterTab.SUMMARY -> SummaryBody(profile, catalog, onAvatar)
            CharacterTab.ACHIEVEMENTS -> AchievementsBody(profile, catalog?.byId.orEmpty())
            CharacterTab.HISTORY -> HistoryBody(
                records = records,
                isLoading = isHistoryLoading,
                opponents = opponents,
            )
        }
    }
}

@Composable
private fun ColumnScope.SummaryBody(
    profile: GameSave,
    catalog: CardCatalog?,
    onAvatar: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        IdentityRow(profile = profile, onAvatar = onAvatar)
        TallyPanel(profile)

        if (catalog != null) {
            SectionHeader(text = strings[StringKeys.COLLECTION])
            CollectionPanel(profile = profile, catalog = catalog)
        }
    }
}

/** The face, the level, and the one number the screen exists to answer. */
@Composable
private fun IdentityRow(profile: GameSave, onAvatar: () -> Unit) {
    val strings = LocalStrings.current
    val floor = XpTable.thresholdFor(profile.level)
    val ceiling = XpTable.thresholdFor(profile.level + 1)
    // At the top of the table there is no next threshold, so the bar is full rather than dividing
    // by a zero span.
    val span = ceiling - floor
    val fraction = if (span <= 0L) 1f else ((profile.xp - floor).toFloat() / span).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        AvatarBadge(profile = profile, modifier = Modifier.ttoClickable(onClick = onAvatar))

        Column(
            modifier = Modifier.testTag(STATS_LEVEL_TEST_TAG).weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Text(
                text = profile.username,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${strings[StringKeys.LEVEL]} ${profile.level}$DOT_SEPARATOR" +
                    "${profile.xp} ${strings[StringKeys.XP]}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Meter(fraction = fraction, colour = MaterialTheme.colorScheme.tertiary)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${(profile.stats.winRate * PERCENT).roundToInt()}%",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(statsRowTestTag(StringKeys.WIN_RATE)),
            )
            Text(
                text = strings[StringKeys.WIN_RATE],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One bar, split the way the matches were.
 *
 * Three widths rather than three rows, because a defeat is only legible against how many wins it
 * sits beside. A player with no finished match gets the empty track rather than a division by
 * zero, which is what a fresh character sees and is honest about.
 */
@Composable
private fun TallyPanel(profile: GameSave) {
    val strings = LocalStrings.current
    val stats = profile.stats
    val colours = LocalTtoColors.current

    Column(
        modifier = Modifier
            .testTag(STATS_TABLE_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = TRACK)),
        ) {
            // A zero-weight child is a crash, not an empty slice — so each is drawn only when it
            // has something to occupy.
            if (stats.wins > 0) Slice(stats.wins, MaterialTheme.colorScheme.tertiary)
            if (stats.defeats > 0) Slice(stats.defeats, MaterialTheme.colorScheme.error)
            if (stats.draws > 0) Slice(stats.draws, MaterialTheme.colorScheme.outline)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(StringKeys.WINS, stats.wins, MaterialTheme.colorScheme.tertiary)
            LegendItem(StringKeys.DEFEATS, stats.defeats, MaterialTheme.colorScheme.error)
            LegendItem(StringKeys.DRAWS, stats.draws, MaterialTheme.colorScheme.outline)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Footnote(StringKeys.MATCHES, "${stats.played}")
            // Derived, not stored: `STARTED_MATCHES - ENDED_MATCHES`. See [GameSave.forfeits].
            Footnote(StringKeys.FORFEITS, "${profile.forfeits}")
        }

        // Only when there is one. `MGP ×0 · XP ×0` was a sentence about nothing, assembled by hand
        // and printed on every fresh character.
        val boons = buildList {
            if (profile.boons.mgp > 0) add("${strings[StringKeys.MGP]} ×${profile.boons.mgp}")
            if (profile.boons.xp > 0) add("${strings[StringKeys.XP]} ×${profile.boons.xp}")
        }
        if (boons.isNotEmpty()) {
            Text(
                text = boons.joinToString(DOT_SEPARATOR),
                color = colours.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(statsRowTestTag(StringKeys.BOONS)),
            )
        }
    }
}

@Composable
private fun RowScope.Slice(share: Int, colour: Color) {
    Box(modifier = Modifier.weight(share.toFloat()).fillMaxSize().background(colour))
}

@Composable
private fun RowScope.LegendItem(
    labelKey: String,
    count: Int,
    colour: Color,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Box(
            modifier = Modifier
                .size(DotSize)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colour),
        )
        // The number is its own node so it reads — and asserts — as the count it is, rather than
        // as a sentence a translation could reorder.
        Text(
            text = "$count",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(statsRowTestTag(labelKey)),
        )
        Text(
            text = strings[labelKey],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.Footnote(labelKey: String, value: String) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(statsRowTestTag(labelKey)),
        )
        Text(
            text = strings[labelKey],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How much of each set is in hand.
 *
 * The progression a player actually follows, and until now it lived nowhere but above the card
 * grid — a search tool, not a record. Counted per **set** and not per format: a format is a rule
 * about what may be played, and a collection is a thing that is owned.
 */
@Composable
private fun CollectionPanel(profile: GameSave, catalog: CardCatalog) {
    Column(
        modifier = Modifier
            .testTag(STATS_COLLECTION_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        for (set in catalog.releasedSets) {
            val cards = remember(catalog, set) { set.blocks.flatMap(catalog::block) }
            val owned = cards.count { profile.cards.containsKey(it.id) }
            SetRow(
                label = setLabel(LocalStrings.current, set.blocks.first()),
                tag = statsSetTestTag(set.slug),
                owned = owned,
                total = cards.size,
            )
        }
    }
}

@Composable
private fun SetRow(label: String, tag: String, owned: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Meter(fraction = if (total <= 0) 0f else owned.toFloat() / total)
        }
        Text(
            text = "$owned / $total",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(tag),
        )
    }
}

/**
 * Twenty-two families as medallions, two to a row.
 *
 * The icon was already being drawn at 28 dp on the end of a line of prose; given the width the
 * prose was taking, the whole catalogue fits in an screen and a half rather than six.
 */
@Composable
private fun ColumnScope.AchievementsBody(profile: GameSave, cards: Map<Int, Card>) {
    val strings = LocalStrings.current
    val families = remember(profile) { rankedFamilies(profile) }

    if (families.isEmpty()) {
        // Unreachable while [AchievementCatalog] has 22 members, and asserted anyway: an empty
        // catalogue should say so rather than render as a tab that lost its content.
        EmptyNote(strings[StringKeys.NO_ACHIEVEMENT], STATS_NO_ACHIEVEMENT_TEST_TAG)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(MedallionMinWidth),
        modifier = Modifier.testTag(STATS_ACHIEVEMENTS_TEST_TAG).fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        items(families, key = { it.key }) { family ->
            Medallion(family = family, profile = profile, cards = cards)
        }
    }
}

/**
 * One family's medallion — a **fixed height**, whatever it has to say.
 *
 * What a medallion holds varies: a date only once the family is started, a reward line only where
 * the next rung pays one, a bar only while there is a rung left. Left to wrap, the twenty-two
 * cards came out at half a dozen different heights and the grid read as a wall of misaligned
 * boxes. So the height is [MedallionHeight] for all of them and the slack goes between the name
 * and the footer, which keeps the bars of a row on one line.
 */
@Composable
private fun Medallion(family: AchievementFamily, profile: GameSave, cards: Map<Int, Card>) {
    val strings = LocalStrings.current
    val earned = family.earned
    val next = family.next

    Column(
        modifier = Modifier
            .testTag(achievementFamilyTestTag(family.key))
            .fillMaxWidth()
            .height(MedallionHeight)
            .rowSurface(selected = earned != null)
            .padding(SpaceSm),
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
                size = MedallionIconSize,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings[family.face.labelKey],
                    color = MaterialTheme.colorScheme.onSurface
                        .copy(alpha = if (earned != null) 1f else DISABLED_TEXT),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
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
        }

        Spacer(modifier = Modifier.weight(1f))

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

        // The reward of whatever the player can still reach — the *next* rung, not the face.
        // Showing the face's would tell someone who has just earned tier I what they have already
        // been paid, and leave the 5 000 MGP at the top of the ladder invisible until they are all
        // but standing on it. Once the family is finished there is no next rung and the face's own
        // reward is the right thing to show, as a record of what it paid.
        RewardNote(achievement = next ?: family.face, cards = cards)

        // Absent once every tier is earned: there is nothing left to aim at, and a bar at 100%
        // under a completed family says less than the date above it already does.
        if (next != null) {
            val progress = next.progressFor(profile)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpaceXs),
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
        maxLines = 2,
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

/** How faded an unearned family's name is — legible, but plainly not yours yet. */
private const val DISABLED_TEXT = 0.65f

private const val TRACK = 0.25f

private val BarHeight = 10.dp
private val DotSize = 8.dp
private val MedallionIconSize = 32.dp
private val MedallionMinWidth = 150.dp

/** Room for the tallest medallion there is: a two-line name, a date, a reward and a bar. */
private val MedallionHeight = 136.dp
