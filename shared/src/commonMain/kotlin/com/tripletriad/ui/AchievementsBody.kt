package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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

/** The tiers of one family — the pane beside the grid, or the sheet over it. */
const val ACHIEVEMENT_DETAIL_TEST_TAG: String = "stats-achievement-detail"

/** The narrow layout's sheet. There is no such node in the wide one — see the pane. */
const val ACHIEVEMENT_SHEET_TEST_TAG: String = "stats-achievement-sheet"
const val ACHIEVEMENT_NO_MATCH_TEST_TAG: String = "stats-achievement-no-match"
const val ACHIEVEMENT_CATEGORY_MENU_TEST_TAG: String = "stats-category-menu"
const val ACHIEVEMENT_TOTAL_TIERS_TEST_TAG: String = "stats-total-tiers"
const val ACHIEVEMENT_TOTAL_MGP_TEST_TAG: String = "stats-total-mgp"
const val ACHIEVEMENT_TOTAL_CARDS_TEST_TAG: String = "stats-total-cards"

const val ACHIEVEMENT_HIDDEN_TEST_TAG: String = "stats-hidden"

/** `all` is the entry that lifts the category — in the rail and in the menu alike. */
fun achievementCategoryTestTag(category: String): String = "stats-category-$category"

/** `all` is the chip that lifts the standing. */
fun achievementStandingTestTag(standing: String): String = "stats-standing-$standing"

fun achievementTierTestTag(id: String): String = "stats-tier-$id"

fun achievementBadgeTestTag(family: String): String = "stats-badge-$family"

/** What lifts a filter, as a tag suffix — shared by the rail, the menu and the chips. */
private const val ALL_TAG = "all"

/**
 * Twelve families, narrowed by what they are about and by how far along they are.
 *
 * ### Why the filters, with twelve families
 *
 * Twelve is few, but they are not alike: five of them are collections and three are single
 * tournaments, and the only question a player brings to the tab — *what am I close to?* — was
 * answered by scrolling all of them. [Standing.ALMOST] answers it in a tap.
 *
 * ### The wide window
 *
 * The categories stay open down the left, the way the card list's filters do, and the tiers of the
 * family in focus stay open down the right — so a ladder is read beside the grid instead of over
 * it. The pane always shows something: with nothing picked it shows the first family the filters
 * leave, because an empty column a third of the window wide reads as a screen that failed to load.
 * Narrower, the categories become a menu at the head of the chips and the tiers a sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.AchievementsBody(profile: GameSave, cards: Map<Int, Card>) {
    val strings = LocalStrings.current
    val families = remember(profile) { rankedFamilies(profile) }

    if (families.isEmpty()) {
        // Unreachable while [AchievementCatalog] has members, and asserted anyway: an empty
        // catalogue should say so rather than render as a tab that lost its content.
        EmptyNote(strings[StringKeys.NO_ACHIEVEMENT], STATS_NO_ACHIEVEMENT_TEST_TAG)
        return
    }

    var category by remember { mutableStateOf<AchievementCategory?>(null) }
    var standing by remember { mutableStateOf<Standing?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    val totals = remember(profile) { achievementTotals(profile) }
    val inCategory = families.filter { category == null || it.category == category }
    val shown = inCategory.filter { standing?.admits(it) ?: true }

    val totalsRow: @Composable () -> Unit = {
        TotalsRow(totals)
        // Under the totals rather than in the grid: there is nothing to draw for it but a number.
        if (totals.hiddenLeft > 0) {
            Text(
                text = strings.format(StringKeys.ACHIEVEMENTS_HIDDEN, "${totals.hiddenLeft}"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag(ACHIEVEMENT_HIDDEN_TEST_TAG),
            )
        }
    }
    val grid: @Composable (Modifier, String?) -> Unit = { modifier, lit ->
        if (shown.isEmpty()) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                EmptyNote(strings[StringKeys.ACHIEVEMENT_NO_MATCH], ACHIEVEMENT_NO_MATCH_TEST_TAG)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(MedallionMinWidth),
                modifier = modifier.testTag(STATS_ACHIEVEMENTS_TEST_TAG),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                items(shown, key = { it.key }) { family ->
                    Medallion(
                        family = family,
                        profile = profile,
                        cards = cards,
                        selected = family.key == lit,
                        onClick = { picked = family.key },
                    )
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (LocalWideLayout.current && maxWidth >= AchievementPanesMinWidth) {
            val focus = shown.firstOrNull { it.key == picked } ?: shown.firstOrNull()
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(SpaceMd),
            ) {
                CategoryRail(
                    families = families,
                    category = category,
                    onPick = { category = it },
                    modifier = Modifier.width(RailWidth).fillMaxHeight(),
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(SpaceSm),
                ) {
                    totalsRow()
                    ChipRow { StandingChips(inCategory, standing) { standing = it } }
                    grid(Modifier.fillMaxWidth().weight(1f), focus?.key)
                }
                Box(
                    modifier = Modifier
                        .width(DetailWidth)
                        .fillMaxHeight()
                        .rowSurface()
                        .padding(SpaceMd),
                ) {
                    focus?.let { FamilyDetail(it, profile, cards, Modifier.fillMaxSize()) }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(SpaceSm),
            ) {
                totalsRow()
                ChipRow {
                    CategoryMenu(category) { category = it }
                    StandingChips(inCategory, standing) { standing = it }
                }
                grid(Modifier.fillMaxWidth().weight(1f), picked)
            }

            shown.firstOrNull { it.key == picked }?.let { family ->
                ModalBottomSheet(
                    onDismissRequest = { picked = null },
                    sheetState = rememberModalBottomSheetState(),
                    modifier = Modifier.testTag(ACHIEVEMENT_SHEET_TEST_TAG),
                ) {
                    FamilyDetail(
                        family = family,
                        profile = profile,
                        cards = cards,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpaceMd, vertical = SpaceSm),
                    )
                }
            }
        }
    }
}

/**
 * Tiers earned, Gil they paid, cards they gave — the three sums the grid is the detail of.
 *
 * Counted over the whole catalogue whatever the filters say: a total that shrank when a chip was
 * lit would be a second, quieter way of saying what the grid already shows.
 */
@Composable
private fun TotalsRow(totals: AchievementTotals) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        TotalTile(
            label = strings[StringKeys.ACHIEVEMENT_TIERS_EARNED],
            value = "${totals.tiersEarned} / ${totals.tiersTotal}",
            tag = ACHIEVEMENT_TOTAL_TIERS_TEST_TAG,
        )
        TotalTile(
            label = strings.format(
                StringKeys.ACHIEVEMENT_CURRENCY_EARNED,
                strings[StringKeys.MGP],
            ),
            value = "${totals.mgpEarned}",
            tag = ACHIEVEMENT_TOTAL_MGP_TEST_TAG,
        )
        TotalTile(
            label = strings[StringKeys.ACHIEVEMENT_CARDS_EARNED],
            value = "${totals.cardsEarned} / ${totals.cardsTotal}",
            tag = ACHIEVEMENT_TOTAL_CARDS_TEST_TAG,
        )
    }
}

@Composable
private fun RowScope.TotalTile(label: String, value: String, tag: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .rowSurface()
            .padding(horizontal = SpaceMd, vertical = SpaceSm),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Its own node, so it asserts as the number it is — see the tally's legend.
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** One line of chips that scrolls sideways rather than wrapping onto a second band of controls. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * All, then the four standings, each with how many families of the current category it holds.
 *
 * Counted inside the category and not across the catalogue, so a lit chip never promises a number
 * the grid under it does not show. Tapping a lit chip lifts it, as the card list's menus do.
 */
@Composable
private fun StandingChips(
    families: List<AchievementFamily>,
    standing: Standing?,
    onPick: (Standing?) -> Unit,
) {
    val strings = LocalStrings.current

    TtoFilterChip(
        label = counted(strings[StringKeys.ACHIEVEMENT_ALL], families.size),
        tag = achievementStandingTestTag(ALL_TAG),
        selected = standing == null,
    ) { onPick(null) }
    for (candidate in Standing.entries) {
        TtoFilterChip(
            label = counted(strings[candidate.labelKey], families.count(candidate::admits)),
            tag = achievementStandingTestTag(candidate.tag),
            selected = standing == candidate,
        ) { onPick(if (standing == candidate) null else candidate) }
    }
}

private fun counted(label: String, count: Int): String = "$label · $count"

/** The rail's question as a menu, for a window without room for the rail. */
@Composable
private fun CategoryMenu(
    category: AchievementCategory?,
    onPick: (AchievementCategory?) -> Unit,
) {
    val strings = LocalStrings.current

    FilterMenu(
        tag = ACHIEVEMENT_CATEGORY_MENU_TEST_TAG,
        label = category?.let { strings[it.labelKey] }
            ?: strings[StringKeys.ACHIEVEMENT_CATEGORIES],
        on = category != null,
    ) { close ->
        MenuChoice(
            label = strings[StringKeys.ACHIEVEMENT_ALL],
            tag = achievementCategoryTestTag(ALL_TAG),
            chosen = category == null,
        ) {
            onPick(null)
            close()
        }
        for (candidate in AchievementCategory.entries) {
            MenuChoice(
                label = strings[candidate.labelKey],
                tag = achievementCategoryTestTag(candidate.tag),
                chosen = category == candidate,
            ) {
                onPick(candidate)
                close()
            }
        }
    }
}

/**
 * The categories, each with how many families it holds and how much of its ladders is climbed.
 *
 * The bar counts **tiers**, not families: a category whose five ladders are each one rung short of
 * the top is nearly done, and a family count would call it untouched.
 */
@Composable
private fun CategoryRail(
    families: List<AchievementFamily>,
    category: AchievementCategory?,
    onPick: (AchievementCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        SectionHeader(text = strings[StringKeys.ACHIEVEMENT_CATEGORIES])
        RailEntry(
            label = strings[StringKeys.ACHIEVEMENT_ALL],
            families = families,
            tag = achievementCategoryTestTag(ALL_TAG),
            selected = category == null,
        ) { onPick(null) }
        for (candidate in AchievementCategory.entries) {
            val members = families.filter { it.category == candidate }
            // A category the catalogue has nothing in is a rail entry that filters to an empty
            // grid.
            if (members.isNotEmpty()) {
                RailEntry(
                    label = strings[candidate.labelKey],
                    families = members,
                    tag = achievementCategoryTestTag(candidate.tag),
                    selected = category == candidate,
                ) { onPick(candidate) }
            }
        }
    }
}

@Composable
private fun RailEntry(
    label: String,
    families: List<AchievementFamily>,
    tag: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val earned = families.sumOf { it.earnedCount }
    val total = families.sumOf { it.tiers.size }

    Column(
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .rowSurface(selected = selected)
            .ttoClickable(
                role = Role.Tab,
                selected = selected,
                shape = MaterialTheme.shapes.small,
                onClick = onClick,
            )
            .padding(SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${families.size}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
        Meter(fraction = if (total <= 0) 0f else earned.toFloat() / total)
    }
}

/**
 * One family's card — a **fixed height**, whatever it has to say.
 *
 * What a card holds varies: a date only once the family is started, a reward line only where the
 * next rung pays one, a bar only while there is a rung left. Left to wrap, the cards came out at
 * half a dozen different heights and the grid read as a wall of misaligned boxes. So the height is
 * [MedallionHeight] for all of them and the slack goes between the head and the footer, which
 * keeps the bars of a row on one line.
 */
@Composable
private fun Medallion(
    family: AchievementFamily,
    profile: GameSave,
    cards: Map<Int, Card>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val earned = family.earned
    val next = family.next
    val progress = family.progress

    Column(
        modifier = Modifier
            .testTag(achievementFamilyTestTag(family.key))
            .fillMaxWidth()
            .height(MedallionHeight)
            .rowSurface(selected = selected)
            .ttoClickable(
                selected = selected,
                shape = MaterialTheme.shapes.small,
                onClick = onClick,
            )
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            AchievementIcon(
                iconId = family.face.iconId,
                description = strings[family.face.labelKey],
                size = MedallionIconSize,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PipGap),
            ) {
                Text(
                    text = strings[family.face.labelKey],
                    color = MaterialTheme.colorScheme.onSurface
                        .copy(alpha = if (earned != null) 1f else DISABLED_TEXT),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Only where there is something to count. `ac-foc` is one achievement, and pips
                // beside its name would be a tier ladder it does not have.
                if (family.tiers.size > 1) TierPips(family)
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
            StandingBadge(family)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Absent once every tier is earned: there is nothing left to aim at, and the badge and the
        // date above already say so.
        if (next != null && progress != null) {
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(achievementRowTestTag(next.id)),
                )
            }
            Meter(fraction = progress.fraction, colour = MaterialTheme.colorScheme.tertiary)
        }

        // The reward of whatever the player can still reach — the *next* rung, not the face.
        // Showing the face's would tell someone who has just earned tier I what they have already
        // been paid, and leave the 5 000 Gil at the top of the ladder invisible until they are all
        // but standing on it. Once the family is finished there is no next rung and the face's own
        // reward is the right thing to show, as a record of what it paid.
        val paying = next ?: family.face
        RewardNote(achievement = paying, cards = cards, tag = achievementRewardTestTag(paying.id))
    }
}

/** One pip per tier, lit up to the tiers earned, then the count they spell. */
@Composable
private fun TierPips(family: AchievementFamily) {
    val lit = MaterialTheme.colorScheme.tertiary
    val unlit = MaterialTheme.colorScheme.outline.copy(alpha = TRACK)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipGap),
    ) {
        repeat(family.tiers.size) { index ->
            Box(
                modifier = Modifier
                    .size(PipSize)
                    .clip(CircleShape)
                    .background(if (index < family.earnedCount) lit else unlit),
            )
        }
        Text(
            text = "${family.earnedCount} / ${family.tiers.size}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = SpaceXs),
        )
    }
}

/**
 * "Completed" or "Almost!", or nothing.
 *
 * Two of the four standings and not all four: "in progress" and "not started" describe most of the
 * grid, and a badge on most of the grid is a badge nobody reads.
 */
@Composable
private fun StandingBadge(family: AchievementFamily) {
    val strings = LocalStrings.current
    val colours = LocalTtoColors.current
    val (key, colour) = when {
        family.next == null -> StringKeys.ACHIEVEMENT_DONE_BADGE to colours.positive
        family.isAlmost -> StringKeys.ACHIEVEMENT_ALMOST_BADGE to colours.currency
        else -> return
    }

    Text(
        text = strings[key],
        color = colour,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .testTag(achievementBadgeTestTag(family.key))
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colour.copy(alpha = BADGE_GROUND))
            .padding(horizontal = SpaceXs, vertical = PipGap),
    )
}

/**
 * "Reward: Tozol Huatotl", or nothing at all.
 *
 * A card reward is named from the card table, so it reads as the card and not as an id; Gil is
 * formatted through its own key because the currency's name is translated (Gils in French, ギル in
 * Japanese) and a bare number would say nothing.
 *
 * @param tag null in the tiers, where the card beside them already carries the same tag.
 */
@Composable
internal fun RewardNote(achievement: Achievement, cards: Map<Int, Card>, tag: String?) {
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
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
    )
}

/** How faded an unearned name is — legible, but plainly not yours yet. */
internal const val DISABLED_TEXT = 0.65f

private const val TRACK = 0.25f

/** Behind a badge's word, in the word's own colour. */
private const val BADGE_GROUND = 0.16f

/**
 * The rail, two card columns and the pane: 220 + 2 × 240 + 340, and the gaps. Below it the
 * layout is the narrow one, whatever `LocalWideLayout` says.
 */
private val AchievementPanesMinWidth = 1080.dp
private val RailWidth = 220.dp
private val DetailWidth = 340.dp

private val MedallionMinWidth = 240.dp

/**
 * Room for the tallest card and no more: a 45 dp head (name, pips, date), a 55 dp foot (the next
 * rung, a bar, two reward lines), the gap between and the padding. Measured on 2026-09-16; it was
 * 164 dp, which left the usual card — one reward line or none — with a third of itself empty.
 */
private val MedallionHeight = 132.dp
private val MedallionIconSize = 40.dp
private val PipSize = 6.dp
private val PipGap = 2.dp
