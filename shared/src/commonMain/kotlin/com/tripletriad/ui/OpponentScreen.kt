package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardSet
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Availability
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Npc
import com.tripletriad.model.NpcLevel
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.random.Random

const val OPPONENT_LIST_TEST_TAG: String = "opponent-list"
const val OPPONENT_EMPTY_TEST_TAG: String = "opponent-empty"

const val OPPONENT_LOCKED_TEST_TAG: String = "opponent-locked"

const val OPPONENT_UNEARNED_TEST_TAG: String = "opponent-unearned"

const val RANDOM_OPPONENT_TEST_TAG: String = "opponent-random"

const val OPPONENT_SHEET_TEST_TAG: String = "opponent-sheet"

const val OPPONENT_CHALLENGE_TEST_TAG: String = "opponent-challenge"

const val OPPONENT_RESUME_TEST_TAG: String = "opponent-resume"

const val OPPONENT_RULE_FILTER_TEST_TAG: String = "opponent-filter-rule"

fun opponentBlockFilterTestTag(block: Int?): String = "opponent-filter-block-${block ?: "all"}"

fun opponentReasonTestTag(reason: String): String = "opponent-filter-$reason"

fun opponentRuleChoiceTestTag(ruleKey: String?): String = "opponent-rule-${ruleKey ?: "any"}"

fun opponentRowTestTag(iconId: String): String = "opponent-row-$iconId"

fun opponentRewardsTestTag(iconId: String): String = "opponent-rewards-$iconId"

/**
 * Why one opponent rather than another, as a question the player asks instead of three lists.
 *
 * These were the three shelves above the roster, and their two biggest were true of nearly the
 * same people: on a new character "never played" held 22 and "has a card you're missing" held 19,
 * in the same order, so the screen opened by drawing the same five faces twice before the roster
 * itself. As chips they are one predicate each over one grid, and nobody is named twice.
 */
internal enum class OpponentReason(val slug: String, val labelKey: String) {
    ALL("all", StringKeys.ALL),
    FRESH("new", StringKeys.OPPONENTS_NEW),
    WANTED("wanted", StringKeys.OPPONENTS_WANTED),
    TIMED("timed", StringKeys.OPPONENTS_TIMED),
}

/**
 * The solo roster: the play root's first tab.
 *
 * The eighty-five hand-drawn portraits are this screen's one real asset, and a 56 dp row spent
 * them four at a time. A grid shows twelve to sixteen above the fold without giving up what the
 * row said that a portrait cannot: each tile still carries the rules the match is played under
 * and what it costs to sit down, because those are what a player reads *before* committing a fee,
 * not after. Finding somebody who plays a particular rule is the [OPPONENT_RULE_FILTER_TEST_TAG]
 * menu rather than a scroll through everyone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun OpponentScreen(
    profile: GameSave,
    catalog: NpcCatalog,
    cards: Map<Int, Card>,
    sets: List<CardSet>,
    hour: Int,
    formatId: String,
    waiting: Int,
    onChallenge: (Npc) -> Unit,
    onTab: (PlayTab) -> Unit,
    onBack: () -> Unit,
    /**
     * The opponent of a match the server still has open, or null when there is nothing to go back
     * to.
     *
     * An `Npc` rather than an id because the button names who is waiting, and this screen already
     * holds the roster to resolve it — see the caller, which cannot: a match may be against an
     * opponent the current filter is hiding, and the answer has to survive that.
     */
    resumable: Npc? = null,
    onResume: (Npc) -> Unit = {},
) {
    val strings = LocalStrings.current
    // Keyed on the format, not on the character: who a player may challenge is a property of the
    // match they are looking for. `MODE` used to answer this and the answer was the same only
    // because a character could play one set.
    val earned = profile.achievements.keys
    val opponents = remember(catalog, formatId, hour, profile.level, earned) {
        catalog.available(formatId, hour, profile.level, earned)
    }
    val locked = remember(catalog, formatId, hour, profile.level) {
        catalog.lockedByLevel(formatId, hour, profile.level)
    }
    // Counted apart from the level-locked, because the two footnotes promise different things:
    // one says keep playing, this one says there is a tournament to win first.
    val unearned = remember(catalog, formatId, earned) {
        catalog.lockedByAchievement(formatId, earned)
    }

    val filters = rememberOpponentFilters(opponents, sets)
    val shown = filters.apply(opponents, cards, profile)
    // Grouped rather than flattened, so a header can say which skill band a run of tiles belongs
    // to without repeating it on every tile. `shown` is already sorted by difficulty
    // (`available()`), and `Npc.level` is computed from difficulty by `npcLevelFor` —
    // non-decreasing in it — so the groups come out in ascending order for free, the same way
    // `groupBy` preserves the order it first sees a key in.
    val tiers = remember(shown) { shown.groupBy { it.level } }

    // The opponent a tap opened the detail sheet for, or null. Kept as an id rather than the `Npc`
    // itself so a filter change that removes it from `shown` closes the sheet by simply finding
    // nothing, instead of holding a stale reference to an opponent no longer on screen.
    var detailIcon by remember(shown) { mutableStateOf<String?>(null) }
    val detail = shown.firstOrNull { it.iconId == detailIcon }
    val sheetState = rememberModalBottomSheetState()

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.PLAY],
        onBack = onBack,
        // The die is an action, not a chapter. It used to be a full-width filled button halfway
        // down the screen, between the shelves and the roster, where it read as a heading and cut
        // the page in two. Hidden rather than disabled when there is nobody to pick, since a
        // filter that matches nobody is the player's own doing and the grid already says so.
        actions = {
            if (shown.isNotEmpty()) {
                IconButton(
                    onClick = { onChallenge(shown.random(Random)) },
                    modifier = Modifier.testTag(RANDOM_OPPONENT_TEST_TAG),
                ) {
                    Icon(
                        imageVector = TtoIcons.Die,
                        contentDescription = strings[StringKeys.RANDOM_OPPONENT],
                    )
                }
            }
        },
    ) {
        PlayTabs(current = PlayTab.SOLO, waiting = waiting, onSelect = onTab)

        // One scrolling container for the whole screen rather than a static header above a
        // separately-scrolling list. Everything above the tiles is a full-width span in the same
        // grid, so nothing above the fold can starve what is below it on a short window — the
        // same fix `ShopBody` makes for the same reason.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TileMinWidth),
            modifier = Modifier.testTag(OPPONENT_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            // **First, and filled.** A match already under way is the one thing on this screen
            // that is not a choice: everything below it starts something new, and starting
            // something new is what abandons the match — the server closes the live one when the
            // next is opened (`PveStore.open`). So it goes above the chips rather than among the
            // opponents, where a player who had scrolled would never see it, and it is not
            // filtered by them for the same reason.
            resumable?.let { npc ->
                fullWidth(RESUME_KEY) {
                    WideButton(
                        label = strings.format(StringKeys.MATCH_RESUME, strings[npc.nameKey]),
                        tag = OPPONENT_RESUME_TEST_TAG,
                        onClick = { onResume(npc) },
                    )
                }
            }

            fullWidth(FILTERS_KEY) {
                OpponentFilterRows(
                    filters = filters,
                    opponents = opponents,
                    cards = cards,
                    profile = profile,
                )
            }

            if (shown.isEmpty()) {
                fullWidth(EMPTY_KEY) {
                    EmptyNote(
                        text = strings[StringKeys.NO_OPPONENT],
                        tag = OPPONENT_EMPTY_TEST_TAG,
                    )
                }
            } else {
                for ((level, npcs) in tiers) {
                    fullWidth("tier-${level.name}") { TierHeader(level, npcs.size) }
                    items(npcs, key = { it.iconId }) { npc ->
                        OpponentTile(
                            npc = npc,
                            cards = cards,
                            owned = profile.cards,
                            onClick = { detailIcon = npc.iconId },
                        )
                    }
                }

                // Under the grid rather than over it: it is a footnote about what is *not* here,
                // and a player who has not scrolled to the bottom has not run out of opponents
                // yet.
                if (locked > 0) {
                    fullWidth(LOCKED_KEY) {
                        Footnote(
                            text = strings.format(StringKeys.OPPONENTS_LOCKED, locked.toString()),
                            tag = OPPONENT_LOCKED_TEST_TAG,
                        )
                    }
                }

                if (unearned > 0) {
                    fullWidth(UNEARNED_KEY) {
                        Footnote(
                            text = strings.format(
                                StringKeys.OPPONENTS_UNEARNED,
                                unearned.toString(),
                            ),
                            tag = OPPONENT_UNEARNED_TEST_TAG,
                        )
                    }
                }
            }
        }
    }

    // Outside the scaffold for the same reason `StoreScreen` puts its own sheet there: it covers
    // the screen rather than sitting in the column, and `ModalBottomSheet` hoists itself to its own
    // surface regardless of where it is called from.
    detail?.let { npc ->
        ModalBottomSheet(
            onDismissRequest = { detailIcon = null },
            sheetState = sheetState,
            modifier = Modifier.testTag(OPPONENT_SHEET_TEST_TAG),
        ) {
            OpponentDetailSheet(
                npc = npc,
                cards = cards,
                owned = profile.cards,
                onChallenge = {
                    detailIcon = null
                    onChallenge(npc)
                },
            )
        }
    }
}

/** The two footnotes under the roster, which differ only in what they say. */
@Composable
private fun Footnote(text: String, tag: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.testTag(tag).fillMaxWidth().padding(vertical = SpaceSm),
    )
}

/**
 * One item spanning every column, for everything on this screen that is not an opponent.
 *
 * Keyed, unlike `ShopBody`'s: the chips and the footnotes come and go as the filters change, and
 * an unkeyed item that appears above the grid would take the identity of whatever was there.
 */
private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private const val RESUME_KEY = "resume"

private const val FILTERS_KEY = "filters"

private const val EMPTY_KEY = "empty"

private const val LOCKED_KEY = "locked-note"

private const val UNEARNED_KEY = "unearned-note"

private val TileMinWidth = 104.dp

private val WantDotSize = 9.dp

private val FeeCoinSize = 13.dp

/**
 * The skill-band header a run of tiles sits under.
 *
 * Painted over an explicit background rather than left transparent for the same reason the sticky
 * header it replaces was: a grid header spans a row of its own and a transparent one would show
 * the tiles scrolling under it.
 */
@Composable
private fun TierHeader(level: NpcLevel, count: Int) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = SpaceXs, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = strings[level.labelKey].uppercase(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/**
 * One opponent, as a portrait rather than a line of text with a portrait on it.
 *
 * What the 56 dp row said is all still here — the rules imposed and the match fee — but stacked
 * under the face instead of beside it, which is what buys three or four opponents per row instead
 * of one. The cyan dot keeps the meaning it had on the row: this opponent can hand over a card the
 * collection does not hold.
 */
@Composable
private fun OpponentTile(
    npc: Npc,
    cards: Map<Int, Card>,
    owned: Map<Int, Int>,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val name = strings[npc.nameKey]
    val wants = remember(npc, cards, owned) { npc.wants(cards, owned) }

    Column(
        modifier = Modifier
            .testTag(opponentRowTestTag(npc.iconId))
            .fillMaxWidth()
            .rowSurface()
            .ttoClickable(onClick = onClick)
            .padding(vertical = SpaceSm, horizontal = SpaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box {
            NpcPortrait(npc = npc, name = name)
            if (wants) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(WantDotSize)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
            }
        }

        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // The rules a match is played under, kept on the tile rather than left to the sheet a tap
        // opens: which rules an opponent imposes is exactly the thing worth reading *before*
        // committing to a match fee. A tile with none draws a blank line rather than closing up,
        // so a row of tiles keeps one height and the fees stay on one baseline.
        Text(
            text = npc.ruleKeys.joinToString(DOT_SEPARATOR) { strings[it] },
            color = LocalTtoColors.current.transient,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // What a match costs, out of the same purse a card is bought with. See [PriceTag].
        PriceTag(
            price = npc.matchFee,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            coin = MaterialTheme.colorScheme.tertiary,
            coinSize = FeeCoinSize,
        )
    }
}

/**
 * The detail a tile's tap opens: portrait at full size, the payout the tile has no room for, the
 * drop table — still without its odds, see [RewardCards] — and the [StringKeys.CHALLENGE] button
 * that actually starts the match.
 */
@Composable
private fun OpponentDetailSheet(
    npc: Npc,
    cards: Map<Int, Card>,
    owned: Map<Int, Int>,
    onChallenge: () -> Unit,
) {
    val strings = LocalStrings.current
    val rewards = remember(npc, cards) { npcCardRewards(npc, cards) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SpaceLg, vertical = SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            NpcPortrait(npc = npc, name = strings[npc.nameKey])
            Column {
                Text(
                    text = strings[npc.nameKey],
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = strings[npc.level.labelKey],
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Text(
            text = rewardLine(strings, npc),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.bodyMedium,
        )

        // Omitted rather than shown empty: "no special rules" is what an absent line already
        // says. The tile keeps a blank one to hold its height; a sheet has no row to line up with.
        val rules = npc.ruleKeys
        if (rules.isNotEmpty()) {
            Text(
                text = rules.joinToString(DOT_SEPARATOR) { strings[it] },
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        // Omitted rather than shown empty, for the same reason. One of the 85 opponents drops no
        // card at all — `STR_NPC_MARTINE` — and a caption over nothing would read as a missing
        // image.
        if (rewards.isNotEmpty()) {
            RewardCards(iconId = npc.iconId, rewards = rewards, owned = owned)
        }

        WideButton(
            label = strings[StringKeys.CHALLENGE],
            tag = OPPONENT_CHALLENGE_TEST_TAG,
            onClick = onChallenge,
        )
    }
}

/**
 * The cards an opponent can hand over, one already owned told apart from one that is not.
 *
 * A copy already in the bag is drawn at full strength and one that is not is dimmed — the same
 * [UNOWNED_ALPHA] the card list dims an unowned thumb by, see `CardListBody.kt`, so "not yet mine"
 * reads the same wherever a card is shown next to others the collection may or may not hold.
 *
 * **No odds are shown**, on the same instruction the shop's booster tiles follow: knowing which
 * cards are in play is worth keeping, knowing exactly how likely each one is is worth losing —
 * see `ShopBody.BoosterTile`.
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
        for ((card, _) in rewards) {
            val dim = (owned[card.id] ?: 0) <= 0
            CardThumb(
                card = card,
                modifier = if (dim) Modifier.alpha(UNOWNED_ALPHA) else Modifier,
            )
        }
    }
}

/**
 * The cards an opponent can give up, resolved against the profile's own table.
 *
 * An id the collection does not hold is dropped rather than drawn as a hole: `NPCs.as` is
 * per-collection data and this is a belt-and-braces read of it, not a claim that every listed id
 * ships. Shared by [OpponentTile] and [CampaignScreen]'s final-reward line so both name the same
 * cards for the same opponent.
 */
internal fun npcCardRewards(npc: Npc, cards: Map<Int, Card>): List<Pair<Card, Double>> =
    npc.itemRewards.mapNotNull { reward ->
        reward.cardId?.let { id -> cards[id]?.let { it to reward.rate } }
    }

/** At least one card this opponent can hand over is not in the collection yet. */
internal fun Npc.wants(cards: Map<Int, Card>, owned: Map<Int, Int>): Boolean =
    npcCardRewards(this, cards).any { (card, _) -> (owned[card.id] ?: 0) <= 0 }

private const val UNOWNED_ALPHA = 0.28f

private fun rewardLine(strings: Strings, npc: Npc): String = buildList {
    add("${strings[StringKeys.DIFFICULTY]} ${npc.difficulty}")
    if (npc.matchFee > 0) add("${strings[StringKeys.MATCH_FEE]} ${npc.matchFee}")
    add("${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}")
    val xp = npc.xpFor(MatchResult.WIN)
    if (xp > 0) add("$xp ${strings[StringKeys.XP]}")
}.joinToString(DOT_SEPARATOR)

/** Open at some hours and not others — the "Horaire" chip's own predicate. */
internal fun Npc.isTimed(): Boolean = availability != Availability.Always

/** The menu of rules on offer, and the filters read against them. */
@Composable
private fun OpponentFilterRows(
    filters: OpponentFilters,
    opponents: List<Npc>,
    cards: Map<Int, Card>,
    profile: GameSave,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = SpaceXs),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        // Counted over the *unfiltered* roster, so a chip says how many it would show rather than
        // how many survive the chip already pressed. A count that changed when you pressed a
        // different chip would be a count of nothing anybody asked about.
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceXs)) {
            for (reason in OpponentReason.entries) {
                val count = opponents.count { reason.holds(it, cards, profile) }
                TtoFilterChip(
                    label = "${strings[reason.labelKey]}  $count",
                    tag = opponentReasonTestTag(reason.slug),
                    selected = reason == filters.reason,
                    onClick = { filters.reason = reason },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuleMenu(filters = filters, opponents = opponents)

            // Only when there is a second collection to tell apart from the first — one block
            // admitted is not a filter, it is a row of one chip that does nothing. Same rule the
            // card list's own set row follows.
            if (filters.blocks.size > 1) {
                TtoFilterChip(
                    label = strings[StringKeys.ALL],
                    tag = opponentBlockFilterTestTag(null),
                    selected = filters.block == null,
                ) { filters.block = null }
                for (block in filters.blocks) {
                    TtoFilterChip(
                        label = setLabel(strings, block),
                        tag = opponentBlockFilterTestTag(block),
                        selected = filters.block == block,
                        onClick = { filters.block = block.takeIf { it != filters.block } },
                    )
                }
            }
        }
    }
}

/**
 * "Who plays Plus?", as a menu rather than a scroll.
 *
 * A chip row was not an option: the roster imposes upwards of a dozen distinct rules and a row of
 * a dozen chips is a second horizontal scroll over a screen that just lost one. The menu lists
 * only the rules somebody on the current roster actually plays, each with the number who do, so a
 * choice can never come back empty.
 */
@Composable
private fun RuleMenu(filters: OpponentFilters, opponents: List<Npc>) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    val counts = remember(opponents) {
        opponents.flatMap { it.ruleKeys }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { (key, _) -> strings[key] }
    }
    if (counts.isEmpty()) return

    Box {
        TtoFilterChip(
            label = filters.rule?.let { strings[it] } ?: strings[StringKeys.ANY_RULE],
            tag = OPPONENT_RULE_FILTER_TEST_TAG,
            selected = filters.rule != null,
        ) { open = true }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(strings[StringKeys.ANY_RULE]) },
                onClick = {
                    filters.rule = null
                    open = false
                },
                modifier = Modifier.testTag(opponentRuleChoiceTestTag(null)),
            )
            for ((key, count) in counts) {
                DropdownMenuItem(
                    text = { Text("${strings[key]}  $count") },
                    onClick = {
                        filters.rule = key
                        open = false
                    },
                    modifier = Modifier.testTag(opponentRuleChoiceTestTag(key)),
                )
            }
        }
    }
}
