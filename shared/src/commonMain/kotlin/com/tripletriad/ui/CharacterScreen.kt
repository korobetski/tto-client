package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchRecord
import com.tripletriad.model.XpTable
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

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.PROFILE],
        onBack = onBack,
        // Only the achievements: the rail, the grid and the ladder want the room, and a summary
        // or a history stretched to a desktop window is lines too long to read.
        wide = tab == CharacterTab.ACHIEVEMENTS,
        wideMaxWidth = CollectionMaxWidth,
    ) {
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
            if (profile.boons.luck > 0) add("${strings[StringKeys.LUCK]} ×${profile.boons.luck}")
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

private const val PERCENT = 100

private const val TRACK = 0.25f

private val BarHeight = 10.dp
private val DotSize = 8.dp
