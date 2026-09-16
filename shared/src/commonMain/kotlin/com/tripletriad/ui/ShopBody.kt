package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.data.BoosterPricing
import com.tripletriad.data.Inventory
import com.tripletriad.data.ShopOffer
import com.tripletriad.data.StarterCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.PotionItem
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.roundToInt

const val SHOP_LIST_TEST_TAG: String = "shop-list"
const val SHOP_BUY_TEST_TAG: String = "shop-buy"

/** The purchase sheet. Absent until an offer is picked — the buy button lives inside it. */
const val SHOP_SHEET_TEST_TAG: String = "shop-sheet"

/**
 * What an offer holds and the button that buys it, in the sheet on a phone and in the pane beside
 * the shelf on anything wider. The tag tests wait on, since only one of the two containers exists.
 */
const val SHOP_OFFER_DETAIL_TEST_TAG: String = "shop-offer-detail"

/** The wide shop's right-hand pane, which is there with or without a pick. */
const val SHOP_PANE_TEST_TAG: String = "shop-pane"

const val SHOP_STARTER_TEST_TAG: String = "shop-starter"
const val SHOP_STARTER_CLAIM_TEST_TAG: String = "shop-starter-claim"

const val SHOP_NOTE_TEST_TAG: String = "shop-note"

/** The boons brought to the head of the packs. Absent while any pack is affordable. */
const val SHOP_WITHIN_REACH_TEST_TAG: String = "shop-within-reach"

fun shopOfferTestTag(offer: ShopOffer): String = "shop-offer-${itemSlug(offer.item)}"

fun shopOddsTestTag(offer: ShopOffer): String = "shop-odds-${itemSlug(offer.item)}"

/** "5 of 8 missing", over the pool a picked pack draws from. */
fun shopPoolCountTestTag(offer: ShopOffer): String = "shop-pool-count-${itemSlug(offer.item)}"

/** One card of a picked pack's pool. */
fun shopPoolCardTestTag(cardId: Int): String = "shop-pool-card-$cardId"

/** The mark on a pool card the collection already holds. */
fun shopPoolOwnedTestTag(cardId: Int): String = "shop-pool-owned-$cardId"

/** The purse after the purchase, when it can be made. See [shopShortTestTag] for when it cannot. */
fun shopBalanceAfterTestTag(offer: ShopOffer): String = "shop-balance-${itemSlug(offer.item)}"

/** The chip that puts one shelf on show. Absent when only one shelf is stocked. */
fun shopShelfTestTag(shelf: String): String = "shop-shelf-$shelf"

@Composable
private fun packTerms(strings: Strings, item: Item, cards: Map<Int, Card>): String? {
    val pack = (item as? BoosterItem)?.boosterType ?: return null
    val odds = (BoosterPricing.fiveStarChance(pack, cards) * PERCENT).roundToInt()
    // A pack draws exactly one card now — there is no guaranteed rarity floor left to advertise,
    // only the chance of the top rarity. Silent when that chance is zero, same as before.
    return if (odds == 0) null else strings.format(StringKeys.PACK_ODDS, odds.toString())
}

/**
 * The shop, as one shelf at a time.
 *
 * ### Why one shelf at a time
 *
 * The screen sells three different things. A booster is a **bet** — you are buying odds over a
 * pool. A boon is a **consumable** that expires after five matches. A card is a **thing you
 * either have or want**. They were drawn one under another in a single scroller, which put the
 * packs in a rack that scrolled *sideways* inside a page that scrolled *down*, with a hand-drawn
 * scrollbar under it to explain the second axis. Two axes on one screen is one too many, and the
 * bar existed only to apologise for it.
 *
 * So each shelf gets the whole viewport and its own column count, and the chips above choose
 * which. The rack, its scrollbar and the sixty lines that drew them are gone: eleven packs down
 * a grid need no gesture the page does not already have.
 *
 * ### Chips rather than a second tab row
 *
 * The store already carries a `PrimaryTabRow` — Shop and Bag — and a second underlined strip
 * directly beneath the first reads as two competing levels of the same control. A chip row is
 * visibly the smaller choice, which is what picking a shelf is.
 */
@Composable
@Suppress("LongParameterList")
internal fun ColumnScope.ShopBody(
    profile: GameSave,
    offers: List<ShopOffer>,
    cards: Map<Int, Card>,
    starters: StarterCatalog,
    selectedTag: String?,
    onSelect: (String?) -> Unit,
    onBuy: (ShopOffer) -> Unit,
    onClaimStarter: (() -> Unit)? = null,
) {
    val wide = LocalWideLayout.current
    // Split once per shelf rather than filtered three times per frame. `Item` is sealed and has
    // four cases, so `others` can only ever be `MiscItem` — kept under the boons, at the foot of
    // the shelf of things that are neither packs nor cards, so an item nobody planned for still
    // appears rather than silently vanishing.
    val shelves = remember(offers) { Shelves.of(offers) }
    val stocked = remember(shelves) { Shelf.entries.filter { shelves.of(it).isNotEmpty() } }
    var shelf by remember(stocked) { mutableStateOf(stocked.firstOrNull() ?: Shelf.BOOSTERS) }

    val pick: (ShopOffer) -> Unit = { offer ->
        onSelect(shopOfferTestTag(offer).takeIf { it != selectedTag })
    }

    // Above the shelves rather than on one of them: the pack is free, it is owed once, and it is
    // not the boosters' business. Nothing behind it is reorganised to make room — it is a banner
    // that goes away the moment it is taken.
    if (onClaimStarter != null) {
        StarterPackPanel(starters = starters, cards = cards, onClaim = onClaimStarter)
    }

    if (stocked.size > 1) {
        ShelfChips(
            stocked = stocked,
            selected = shelf,
            counts = { shelves.of(it).size },
            onSelect = { shelf = it },
        )
    }

    // A phone's packs are rows, one to a line — see [BoosterRow]. Only the packs: a card cell is
    // already a thumbnail and a price, and a boon is already a row.
    val rows = !wide && shelf == Shelf.BOOSTERS
    val grid: @Composable (Modifier) -> Unit = { modifier ->
        ShelfGrid(
            shelf = shelf,
            shelves = shelves,
            rows = rows,
            cards = cards,
            profile = profile,
            selectedTag = selectedTag,
            pick = pick,
            modifier = modifier,
        )
    }

    if (wide) {
        // The pane is there before anything is picked, so picking does not reflow the shelf, and
        // the purchase stays beside what it buys rather than over it. The sheet a phone gets
        // covered half a landscape screen and the tile the player had just tapped.
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            grid(Modifier.weight(1f).fillMaxHeight())
            ShopOfferPane(
                offer = offers.firstOrNull { shopOfferTestTag(it) == selectedTag },
                cards = cards,
                profile = profile,
                onBuy = onBuy,
                modifier = Modifier.width(ShopPaneWidth).fillMaxHeight(),
            )
        }
    } else {
        grid(Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun ShelfGrid(
    shelf: Shelf,
    shelves: Shelves,
    rows: Boolean,
    cards: Map<Int, Card>,
    profile: GameSave,
    selectedTag: String?,
    pick: (ShopOffer) -> Unit,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        columns = if (rows) GridCells.Fixed(1) else GridCells.Adaptive(minSize = shelf.cell),
        modifier = modifier.testTag(SHOP_LIST_TEST_TAG),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        when (shelf) {
            Shelf.BOOSTERS -> {
                // A purse that reaches no pack is the purse of a player who needs MGP, and the
                // boons are what makes more of it. They are a shelf away, so the ones it does
                // reach are brought here rather than left for the player to go looking for.
                val reachable = shelves.boons.filter { it.isAffordableBy(profile) }
                val noPack = shelves.boosters.none { it.isAffordableBy(profile) }
                if (reachable.isNotEmpty() && noPack) {
                    fullWidth {
                        WithinReach(profile = profile, boons = reachable) { offer ->
                            BoonTile(
                                offer = offer,
                                cards = cards,
                                profile = profile,
                                isSelected = shopOfferTestTag(offer) == selectedTag,
                                onClick = { pick(offer) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                items(shelves.boosters, key = ::shopOfferTestTag) { offer ->
                    val isSelected = shopOfferTestTag(offer) == selectedTag
                    if (rows) {
                        BoosterRow(offer, cards, profile, isSelected, onClick = { pick(offer) })
                    } else {
                        BoosterTile(offer, cards, profile, isSelected, onClick = { pick(offer) })
                    }
                }
            }

            Shelf.CARDS -> items(shelves.cards, key = ::shopOfferTestTag) { offer ->
                CardOffer(
                    offer = offer,
                    card = itemCard(offer.item, cards),
                    profile = profile,
                    isSelected = shopOfferTestTag(offer) == selectedTag,
                    onClick = { pick(offer) },
                )
            }

            Shelf.BOONS -> {
                items(shelves.boons, key = ::shopOfferTestTag) { offer ->
                    BoonTile(
                        offer = offer,
                        cards = cards,
                        profile = profile,
                        isSelected = shopOfferTestTag(offer) == selectedTag,
                        onClick = { pick(offer) },
                    )
                }
                for (offer in shelves.others) {
                    fullWidth {
                        OfferRow(
                            offer = offer,
                            cards = cards,
                            profile = profile,
                            isSelected = shopOfferTestTag(offer) == selectedTag,
                            onClick = { pick(offer) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The three shelves, in the order a player walks them: the bet, the collection, the consumable.
 *
 * @param cell how wide one thing on this shelf wants to be, which is the whole reason a shelf
 *   knows anything about layout — a pack tile carries three lines of text and a card cell carries
 *   a thumbnail, and one column count cannot serve both.
 */
internal enum class Shelf(val slug: String, val labelKey: String, val cell: Dp) {
    BOOSTERS("boosters", StringKeys.BOOSTERS, BoosterTileWidth),
    CARDS("cards", StringKeys.CARDS, CardOfferWidth),
    BOONS("boons", StringKeys.BOONS, BoonTileWidth),
}

/** The offers, sorted into the shelf each belongs on. */
private data class Shelves(
    val boosters: List<ShopOffer>,
    val boons: List<ShopOffer>,
    val cards: List<ShopOffer>,
    val others: List<ShopOffer>,
) {
    /** What [shelf] holds, including the strays the boons shelf takes in. */
    fun of(shelf: Shelf): List<ShopOffer> = when (shelf) {
        Shelf.BOOSTERS -> boosters
        Shelf.CARDS -> cards
        Shelf.BOONS -> boons + others
    }

    companion object {
        fun of(offers: List<ShopOffer>): Shelves = Shelves(
            boosters = offers.filter { it.item is BoosterItem },
            boons = offers.filter { it.item is PotionItem },
            cards = offers.filter { it.item is CardItem },
            others = offers.filter {
                it.item !is BoosterItem && it.item !is PotionItem && it.item !is CardItem
            },
        )
    }
}

/** The boons a purse too light for every pack can still buy, at the head of the packs. */
@Composable
private fun WithinReach(
    profile: GameSave,
    boons: List<ShopOffer>,
    tile: @Composable (ShopOffer) -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.testTag(SHOP_WITHIN_REACH_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = strings.format(
                StringKeys.NO_PACK_AFFORDABLE,
                grouped(profile.mgp),
                strings[StringKeys.MGP],
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelMedium,
        )
        boons.forEach { tile(it) }
    }
}

/** Puts one composable across the whole grid, whatever the column count works out to be. */
private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * Which shelf is on show, and how much is on each.
 *
 * The count travels with the label rather than sitting in a header over the shelf, which is where
 * it used to be: a header said "CARDS 74" above the cards, and the packs and the boons said their
 * own counts somewhere else down the page. Here the three numbers are next to each other, which
 * is the only place a count of one shelf can be compared with a count of another.
 */
@Composable
private fun ShelfChips(
    stocked: List<Shelf>,
    selected: Shelf,
    counts: (Shelf) -> Int,
    onSelect: (Shelf) -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        for (shelf in stocked) {
            TtoFilterChip(
                label = "${strings[shelf.labelKey]}$DOT_SEPARATOR${counts(shelf)}",
                tag = shopShelfTestTag(shelf.slug),
                selected = shelf == selected,
                onClick = { onSelect(shelf) },
            )
        }
    }
}

@Composable
private fun StarterPackPanel(
    starters: StarterCatalog,
    cards: Map<Int, Card>,
    onClaim: () -> Unit,
) {
    val strings = LocalStrings.current
    val granted = remember(starters, cards) {
        starters.starters.firstOrNull()?.deck.orEmpty().mapNotNull(cards::get)
    }

    Column(
        modifier = Modifier
            .testTag(SHOP_STARTER_TEST_TAG)
            .fillMaxWidth()
            .padding(bottom = SpaceSm)
            .rowSurface(selected = true)
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = strings[StringKeys.STARTER_PACK],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings[StringKeys.FREE],
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
            )
        }

        Text(
            text = strings[StringKeys.STARTER_PACK_DESC],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (card in granted) {
                CardThumb(card = card)
            }
        }

        WideButton(
            label = strings[StringKeys.CLAIM],
            tag = SHOP_STARTER_CLAIM_TEST_TAG,
            onClick = onClaim,
        )
    }
}

/** The row shape the whole shelf used to be, kept for anything that is not a card, pack or boon. */
@Composable
private fun OfferRow(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    profile: GameSave,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            // Exclusive: the shelf has one selection at a time and the sheet acts on it, so these
            // are radio buttons wearing a row's clothes.
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        ItemGlyph(item = offer.item, description = itemName(strings, offer.item, cards))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = itemName(strings, offer.item, cards),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings[offer.item.descriptionKey],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        OfferPrice(offer = offer, profile = profile)
    }
}

/**
 * The wide shop's right-hand column: [ShopOfferSheet] for the pick, or a line saying to make one.
 *
 * Wider than the card list's 260 dp detail pane: a pack's pool is a grid of 44 dp tiles, and at
 * 320 dp it holds six to a line rather than four.
 */
@Composable
private fun ShopOfferPane(
    offer: ShopOffer?,
    cards: Map<Int, Card>,
    profile: GameSave,
    onBuy: (ShopOffer) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .testTag(SHOP_PANE_TEST_TAG)
            .rowSurface(selected = false)
            .verticalScroll(rememberScrollState())
            .padding(vertical = SpaceMd),
    ) {
        if (offer == null) {
            Text(
                text = LocalStrings.current[StringKeys.SHOP_PICK_OFFER],
                modifier = Modifier.fillMaxWidth().padding(SpaceMd),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        } else {
            ShopOfferSheet(
                offer = offer,
                cards = cards,
                profile = profile,
                onBuy = { onBuy(offer) },
            )
        }
    }
}

/**
 * What a picked offer opens: the thing itself, what it costs, and the one button that buys it.
 *
 * The button used to be a permanent 56 dp bar at the foot of the screen — disabled for as long as
 * nothing was picked, which was most of the time, and drawn over the last row of the list because
 * nothing padded the scroller under it. Here it is beside what it buys, and it exists only when
 * there is something to buy.
 */
@Composable
internal fun ShopOfferSheet(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    profile: GameSave,
    onBuy: () -> Unit,
) {
    val strings = LocalStrings.current
    val card = itemCard(offer.item, cards)
    val name = itemName(strings, offer.item, cards)
    val isAffordable = offer.isAffordableBy(profile)

    Column(
        modifier = Modifier
            .testTag(SHOP_OFFER_DETAIL_TEST_TAG)
            .fillMaxWidth()
            .padding(horizontal = SpaceMd)
            .padding(bottom = SpaceLg),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceMd)) {
            if (card == null) {
                ItemGlyph(item = offer.item, description = name, size = SheetGlyphSize)
            } else {
                CardFace(card = card, scale = 1f)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // A card says what it is with its numbers; everything else says it in words.
                if (card != null) {
                    CardStatsLine(card = card)
                    Text(
                        text = "${strings[StringKeys.OWNED]}$DOT_SEPARATOR" +
                            "${profile.copiesOf(card.id)}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }

                Text(
                    text = strings[offer.item.descriptionKey],
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )

                // The odds are off the shelf and on the sheet, where a player deciding on a pack
                // can weigh them instead of reading them eleven times in a row.
                packTerms(strings, offer.item, cards)?.let { terms ->
                    Text(
                        text = terms,
                        modifier = Modifier.testTag(shopOddsTestTag(offer)),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // What the bag already holds of it, so a second potion is a decision rather than
                // a surprise. Cards say the same thing above, counted in the collection instead.
                if (card == null) {
                    val held = Inventory.count(profile, offer.item)
                    if (held > 0) {
                        Text(
                            text = "$COPIES_PREFIX$held",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        (offer.item as? BoosterItem)?.let { pack ->
            PackPool(type = pack.boosterType, offer = offer, cards = cards, profile = profile)
        }

        BalanceLine(offer = offer, profile = profile)

        WideButton(
            label = "${strings[StringKeys.BUY]}$DOT_SEPARATOR${grouped(offer.price)}",
            tag = SHOP_BUY_TEST_TAG,
            enabled = isAffordable,
            onClick = onBuy,
        )
    }
}

/**
 * Every card a pack can hand over, the ones already held dimmed and ticked.
 *
 * The tile's "5 missing" is a count; this is the list it counts, which is what a player weighing
 * a pack for one particular card wants to know. A card the collection lacks is the "?" the card
 * list draws for it, not its picture: the list keeps an unseen card to be found, and a shop that
 * showed the whole pool would give away every one of them a pack at a time.
 *
 * Out of [cards], the format's table — a pool card the format does not admit is left out of the
 * grid but still counted, since the pack still draws it.
 */
@Composable
private fun PackPool(
    type: BoosterType,
    offer: ShopOffer,
    cards: Map<Int, Card>,
    profile: GameSave,
) {
    val strings = LocalStrings.current
    val facts = remember(type, cards, profile.cards) { packFacts(type, cards, profile.cards) }

    Column(verticalArrangement = Arrangement.spacedBy(SpaceXs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            Text(
                text = strings[StringKeys.PACK_POOL],
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RarityRange(stars = facts.stars)
        }
        Text(
            text = strings.format(
                StringKeys.PACK_MISSING_OF,
                facts.missing.toString(),
                type.pool.size.toString(),
            ),
            modifier = Modifier.testTag(shopPoolCountTestTag(offer)),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.labelSmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            for (card in type.pool.mapNotNull(cards::get)) {
                val tag = Modifier.testTag(shopPoolCardTestTag(card.id))
                if ((profile.cards[card.id] ?: 0) > 0) {
                    Box(modifier = tag) {
                        CardTile(card = card, dim = true)
                        // Bottom corner, on a disc: the top one is the type icon's, and a green
                        // tick over a green tribe emblem was not there to read.
                        Icon(
                            imageVector = TtoIcons.Done,
                            contentDescription = null,
                            tint = LocalTtoColors.current.positiveContainer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(SpaceXs)
                                .size(OwnedMarkSize)
                                .background(LocalTtoColors.current.positive, CircleShape)
                                .padding(OwnedMarkInset)
                                .testTag(shopPoolOwnedTestTag(card.id)),
                        )
                    }
                } else {
                    UnknownCardTile(card = card, modifier = tag)
                }
            }
        }
    }
}

/**
 * The purse now and after: `1 200 → 650`, or `100 → you need 450 more`.
 *
 * The gap used to be a red line under a disabled button. Beside the purse it is arithmetic the
 * player can check, and a purse that reaches the price says what is left, which is the number the
 * next purchase starts from.
 */
@Composable
private fun BalanceLine(offer: ShopOffer, profile: GameSave) {
    val strings = LocalStrings.current
    val affordable = offer.isAffordableBy(profile)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = strings[StringKeys.SHOP_BALANCE],
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${grouped(profile.mgp)} $BALANCE_ARROW",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
        if (affordable) {
            Text(
                text = grouped(profile.mgp - offer.price),
                modifier = Modifier.testTag(shopBalanceAfterTestTag(offer)),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            Text(
                text = strings.format(StringKeys.PRICE_SHORT, grouped(offer.price - profile.mgp)),
                modifier = Modifier.testTag(shopShortTestTag(offer)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val BALANCE_ARROW = "→"

private val OwnedMarkSize = 16.dp

private val OwnedMarkInset = 2.dp

/** [ShopOfferSheet] at its phone width, near enough: a card at scale 1 and its text beside it. */
private val ShopPaneWidth = 320.dp
private val SheetGlyphSize = 56.dp

private const val PERCENT = 100
