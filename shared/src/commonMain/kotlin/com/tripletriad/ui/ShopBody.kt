package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.PotionItem
import kotlin.math.roundToInt

const val SHOP_LIST_TEST_TAG: String = "shop-list"
const val SHOP_BUY_TEST_TAG: String = "shop-buy"

/** The purchase sheet. Absent until an offer is picked — the buy button lives inside it. */
const val SHOP_SHEET_TEST_TAG: String = "shop-sheet"

const val SHOP_STARTER_TEST_TAG: String = "shop-starter"
const val SHOP_STARTER_CLAIM_TEST_TAG: String = "shop-starter-claim"

const val SHOP_NOTE_TEST_TAG: String = "shop-note"

/** The way into the auction house, which lives under the store's tab. */
const val SHOP_AUCTION_TEST_TAG: String = "shop-auction"

fun shopOfferTestTag(offer: ShopOffer): String = "shop-offer-${itemSlug(offer.item)}"

fun shopOddsTestTag(offer: ShopOffer): String = "shop-odds-${itemSlug(offer.item)}"

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
    onClaimStarter: (() -> Unit)? = null,
    onAuction: (() -> Unit)? = null,
) {
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

    // The other shop, and the one the shelves cannot be: a price here is fixed and a price there
    // is what somebody else will pay. It sits under the store's tab because buying a card from a
    // player and buying one from a shelf are the same errand — the lobby used to carry it as a
    // card of its own, which is the thing the five tabs replaced.
    if (onAuction != null) {
        AuctionEntry(profile = profile, onClick = onAuction)
    }

    if (stocked.size > 1) {
        ShelfChips(
            stocked = stocked,
            selected = shelf,
            counts = { shelves.of(it).size },
            onSelect = { shelf = it },
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = shelf.cell),
        modifier = Modifier.testTag(SHOP_LIST_TEST_TAG).fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        when (shelf) {
            Shelf.BOOSTERS -> items(shelves.boosters, key = ::shopOfferTestTag) { offer ->
                BoosterTile(
                    offer = offer,
                    cards = cards,
                    profile = profile,
                    isSelected = shopOfferTestTag(offer) == selectedTag,
                    onClick = { pick(offer) },
                )
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

        WideButton(
            label = "${strings[StringKeys.BUY]}$DOT_SEPARATOR${grouped(offer.price)}",
            tag = SHOP_BUY_TEST_TAG,
            enabled = isAffordable,
            onClick = onBuy,
        )
    }
}
private val SheetGlyphSize = 56.dp

private const val PERCENT = 100

/**
 * The way into the auction house, on the shelf it belongs beside.
 *
 * Below the level it opens at the row says so and refuses the tap — "not yet, and here is when"
 * rather than a control that greys out and explains nothing. The screen behind it states the same
 * requirement again ([AUCTION_LOCK_TEST_TAG]) for the player who arrives another way.
 */
@Composable
private fun AuctionEntry(profile: GameSave, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val unlocks = LocalUnlocks.current
    val open = unlocks.allowsAuction(profile)

    Row(
        modifier = Modifier
            .testTag(SHOP_AUCTION_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .ttoClickable(role = Role.Button, onClick = onClick)
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings[StringKeys.AUCTION],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Nothing under the name once the house is open. The second line is where a *reason
            // it is shut* goes, and an open door has none.
            if (!open) {
                Text(
                    text = strings.format(
                        StringKeys.LOCKED_LEVEL,
                        unlocks.auction.toString(),
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = TtoIcons.Forward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            modifier = Modifier.size(IconSm),
        )
    }
}
