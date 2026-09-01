package com.tripletriad.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Campaign
import com.tripletriad.data.CardSet
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
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

fun opponentBlockFilterTestTag(block: Int?): String = "opponent-filter-block-${block ?: "all"}"

fun opponentRowTestTag(iconId: String): String = "opponent-row-$iconId"

fun opponentRewardsTestTag(iconId: String): String = "opponent-rewards-$iconId"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun OpponentScreen(
    profile: GameSave,
    catalog: NpcCatalog,
    cards: Map<Int, Card>,
    sets: List<CardSet>,
    hour: Int,
    formatId: String,
    onChallenge: (Npc) -> Unit,
    campaigns: List<Campaign>,
    onCampaign: (Campaign) -> Unit,
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

    // Which of `opponents`' own card pools is playing — FFXIV's blocks or FFVIII's. Not a format:
    // the widest format admits every block at once, on purpose (see the caller), so telling two
    // collections apart on this screen is a read of the roster the format already handed back,
    // not a second query. Reset with the roster so a stale choice from a wider format cannot hide
    // every opponent a narrower one has.
    //
    // A card's raw block folds down to the block that speaks for its whole *set* before it is
    // grouped or compared — FFXIV spans two blocks and an opponent drawing from the second should
    // still file under the same "FFXIV" chip as one drawing from the first, not a "Set 2" of its
    // own. See `representativeBlocks`.
    val blockGroups = remember(sets) { representativeBlocks(sets) }
    fun Npc.group(): Int? = block()?.let { blockGroups[it] ?: it }
    var block by remember(opponents) { mutableStateOf<Int?>(null) }
    val blocks = remember(opponents, blockGroups) {
        opponents.mapNotNull { it.group() }.distinct().sorted()
    }
    val shown = remember(opponents, block, blockGroups) {
        if (block == null) opponents else opponents.filter { it.group() == block }
    }
    // Grouped rather than flattened, so a sticky header can say which skill band a row belongs to
    // without repeating it on every row. `shown` is already sorted by difficulty (`available()`),
    // and `NpcLevel` is derived from difficulty by `NpcRating.levelFor` — non-decreasing in it — so
    // the groups come out in ascending order for free, the same way `groupBy` preserves the order
    // it first sees a key in.
    val tiers = remember(shown) { shown.groupBy { it.level } }

    // The opponent a tap opened the detail sheet for, or null. Kept as an id rather than the `Npc`
    // itself so a filter change that removes it from `shown` closes the sheet by simply finding
    // nothing, instead of holding a stale reference to an opponent no longer on screen.
    var detailIcon by remember(shown) { mutableStateOf<String?>(null) }
    val detail = shown.firstOrNull { it.iconId == detailIcon }
    val sheetState = rememberModalBottomSheetState()

    ScreenScaffold(
        title = strings[StringKeys.OPPONENTS],
        onBack = onBack,
    ) {
        // One `LazyColumn` for the whole screen rather than a static header above a second,
        // separately-scrolling one. The header used to be a handful of composables sitting above
        // the roster's own `LazyColumn`, each consuming real height in a `Column` that does not
        // scroll — and the more shelves this screen grew, the less of that height was left for the
        // roster to compose *any* row into, on a short window. A row a test could find without
        // scrolling stopped being found not because it moved, but because the space it needed had
        // been spent above it. One scrollable list is the same fix `ShopBody` makes for the same
        // reason: nothing above the fold can starve what is below it.
        LazyColumn(
            modifier = Modifier.testTag(OPPONENT_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            // **First, and filled.** A match already under way is the one thing on this screen
            // that is not a choice: everything below it starts something new, and starting
            // something new is what abandons the match — the server closes the live one when the
            // next is opened (`PveStore.open`). So it goes above the filter chips rather than
            // among the opponents, where a player who had scrolled would never see it, and it is
            // not filtered by the block chips for the same reason.
            resumable?.let { npc ->
                item(key = RESUME_KEY) {
                    WideButton(
                        label = strings.format(StringKeys.MATCH_RESUME, strings[npc.nameKey]),
                        tag = OPPONENT_RESUME_TEST_TAG,
                        onClick = { onResume(npc) },
                    )
                }
            }

            // Only when there is a second collection to tell apart from the first — one block
            // admitted is not a filter, it is a row of one chip that does nothing. Same rule the
            // card list's own set row follows.
            if (blocks.size > 1) {
                item(key = BLOCK_FILTER_KEY) {
                    BlockFilterRow(blocks = blocks, selected = block, onSelect = { block = it })
                }
            }

            item(key = CAMPAIGNS_KEY) {
                CampaignPanel(campaigns = campaigns, profile = profile, onCampaign = onCampaign)
            }

            item(key = SHELVES_KEY) {
                OpponentShelves(
                    opponents = shown,
                    cards = cards,
                    profile = profile,
                    // Opens the same confirmation sheet a roster row does rather than launching a
                    // match straight from a tap — a shelf tile is a shortcut to *find* an opponent,
                    // not a shortcut past reading their rules before playing them.
                    onSelect = { detailIcon = it.iconId },
                )
            }

            if (shown.isEmpty()) {
                item(key = EMPTY_KEY) {
                    Text(
                        text = strings[StringKeys.NO_OPPONENT],
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .testTag(OPPONENT_EMPTY_TEST_TAG)
                            .padding(vertical = SpaceXl),
                    )
                }
            } else {
                item(key = RANDOM_KEY) {
                    // One tap into a match against whoever the roster hands back, rather than
                    // scrolling the list to pick. `shown` is already the unlocked roster, one
                    // collection's worth of it — the same list a row is drawn from — so this can
                    // never land on somebody still gated by level or hour, or in the collection
                    // the player just hid.
                    WideButton(
                        label = strings[StringKeys.RANDOM_OPPONENT],
                        tag = RANDOM_OPPONENT_TEST_TAG,
                        filled = false,
                        onClick = { onChallenge(shown.random(Random)) },
                    )
                }

                for ((level, npcs) in tiers) {
                    stickyHeader(key = "tier-${level.name}") { TierHeader(level) }
                    items(npcs, key = { it.iconId }) { npc ->
                        OpponentRow(
                            npc = npc,
                            cards = cards,
                            owned = profile.cards,
                            onClick = { detailIcon = npc.iconId },
                        )
                    }
                }

                // Under the list rather than over it: it is a footnote about what is *not* here,
                // and a player who has not scrolled to the bottom has not run out of opponents yet.
                if (locked > 0) {
                    item(key = LOCKED_KEY) {
                        Footnote(
                            text = strings.format(StringKeys.OPPONENTS_LOCKED, locked.toString()),
                            tag = OPPONENT_LOCKED_TEST_TAG,
                        )
                    }
                }

                if (unearned > 0) {
                    item(key = UNEARNED_KEY) {
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

private const val RESUME_KEY = "resume"

private const val BLOCK_FILTER_KEY = "block-filter"

private const val CAMPAIGNS_KEY = "campaigns"

private const val SHELVES_KEY = "shelves"

private const val EMPTY_KEY = "empty"

private const val RANDOM_KEY = "random"

private const val LOCKED_KEY = "locked-note"

private const val UNEARNED_KEY = "unearned-note"

/**
 * The block this opponent's own card pool plays — FFXIV's or FFVIII's — or null for an opponent
 * with no cards to speak of.
 *
 * Not a field on [Npc]: it is read off the same fact [com.tripletriad.data.ShopOffer.block]
 * derives a shop offer's block from, an opponent's `cards` pool, rather than authored a second
 * time. Roulette-format opponents can only ever draw from one block regardless — nothing in
 * `npcs.json` mixes the two — so the first card is as good a witness as any.
 */
private fun Npc.block(): Int? = cards.firstOrNull()?.shr(Card.BLOCK_SHIFT)

/**
 * The skill-band header a run of rows sticks under while it scrolls.
 *
 * Painted over an explicit background rather than left transparent: a sticky header sits on top of
 * the rows still scrolling underneath it, and a transparent one would show them bleeding through.
 */
@Composable
private fun TierHeader(level: NpcLevel) {
    val strings = LocalStrings.current
    Text(
        text = strings[level.labelKey].uppercase(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = SpaceXs, horizontal = 2.dp),
    )
}

/**
 * One opponent, 56 dp tall rather than the four-line card this used to be.
 *
 * Everything that does not fit at a glance — the rules imposed, the full drop table, the win/xp
 * payout — moved into [OpponentDetailSheet], which a tap on this row opens rather than starting a
 * match outright. Eighty-five of these no longer cost twenty screens of scrolling to get through.
 */
@Composable
private fun OpponentRow(
    npc: Npc,
    cards: Map<Int, Card>,
    owned: Map<Int, Int>,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    // Only whether the drop table has something missing, not what it is — the same fact the
    // "they have a card you're missing" shelf filters on, read here as a dot because a 56 dp row
    // has no room to spell it out and the shelf above already does.
    val wants = remember(npc, cards, owned) {
        npcCardRewards(npc, cards).any { (card, _) -> (owned[card.id] ?: 0) <= 0 }
    }

    Row(
        modifier = Modifier
            .testTag(opponentRowTestTag(npc.iconId))
            .fillMaxWidth()
            .rowSurface()
            .ttoClickable(onClick = onClick)
            .padding(horizontal = SpaceMd, vertical = SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        NpcPortrait(npc = npc, name = strings[npc.nameKey])

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings[npc.nameKey],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings[npc.level.labelKey],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            // The rules a match is played under, said here rather than only in the sheet a tap
            // opens: which rules an opponent imposes is exactly the thing worth reading *before*
            // committing to a match fee, not after. Omitted when there are none, the same reasoning
            // the sheet's own rules line follows.
            if (npc.ruleKeys.isNotEmpty()) {
                Text(
                    text = npc.ruleKeys.joinToString(DOT_SEPARATOR) { strings[it] },
                    color = LocalTtoColors.current.transient,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (wants) {
            Box(
                modifier = Modifier
                    .size(WantDotSize)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
            )
        }

        // The shelf's own price tag: what a match costs is money out of the same purse a card is
        // bought with, and it was the last coin in the app still drawn by hand. See [PriceTag].
        PriceTag(
            price = npc.matchFee,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            coin = MaterialTheme.colorScheme.tertiary,
            coinSize = FeeCoinSize,
        )
    }
}

private val WantDotSize = 6.dp
private val FeeCoinSize = 13.dp

/**
 * The detail a row's tap opens: portrait at full size, the rules and payout the row has no room
 * for, the drop table — still without its odds, see [RewardCards] — and the [StringKeys.CHALLENGE]
 * button that actually starts the match.
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
        // says, the same reasoning the old row's own rules line followed.
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
 * ships. Shared by [OpponentRow] and [CampaignScreen]'s final-reward line so both name the same
 * cards for the same opponent.
 */
internal fun npcCardRewards(npc: Npc, cards: Map<Int, Card>): List<Pair<Card, Double>> =
    npc.itemRewards.mapNotNull { reward ->
        reward.cardId?.let { id -> cards[id]?.let { it to reward.rate } }
    }

private const val UNOWNED_ALPHA = 0.28f

private fun rewardLine(strings: Strings, npc: Npc): String = buildList {
    add("${strings[StringKeys.DIFFICULTY]} ${npc.difficulty}")
    if (npc.matchFee > 0) add("${strings[StringKeys.MATCH_FEE]} ${npc.matchFee}")
    add("${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}")
    val xp = npc.xpFor(MatchResult.WIN)
    if (xp > 0) add("$xp ${strings[StringKeys.XP]}")
}.joinToString(DOT_SEPARATOR)
