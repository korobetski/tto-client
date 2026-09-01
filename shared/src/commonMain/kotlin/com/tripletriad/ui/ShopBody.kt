package com.tripletriad.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.roundToInt

const val SHOP_LIST_TEST_TAG: String = "shop-list"
const val SHOP_BUY_TEST_TAG: String = "shop-buy"

/** The purchase sheet. Absent until an offer is picked — the buy button lives inside it. */
const val SHOP_SHEET_TEST_TAG: String = "shop-sheet"

const val SHOP_STARTER_TEST_TAG: String = "shop-starter"
const val SHOP_STARTER_CLAIM_TEST_TAG: String = "shop-starter-claim"

const val SHOP_NOTE_TEST_TAG: String = "shop-note"

fun shopOfferTestTag(offer: ShopOffer): String = "shop-offer-${itemSlug(offer.item)}"

fun shopOddsTestTag(offer: ShopOffer): String = "shop-odds-${itemSlug(offer.item)}"

fun shopShelfTestTag(shelf: String): String = "shop-shelf-$shelf"

/** The booster rack's scrollbar. Absent when every pack already fits. */
const val SHOP_RACK_HINT_TEST_TAG: String = "shop-rack-hint"

@Composable
private fun packTerms(strings: Strings, item: Item, cards: Map<Int, Card>): String? {
    val pack = (item as? BoosterItem)?.boosterType ?: return null
    val odds = (BoosterPricing.fiveStarChance(pack, cards) * PERCENT).roundToInt()
    // A pack draws exactly one card now — there is no guaranteed rarity floor left to advertise,
    // only the chance of the top rarity. Silent when that chance is zero, same as before.
    return if (odds == 0) null else strings.format(StringKeys.PACK_ODDS, odds.toString())
}

/**
 * The shop, as three shelves rather than one list.
 *
 * ### Why three
 *
 * The screen sells three different things and used to draw them as one `OfferRow` in one column,
 * sorted by price. A booster is a **bet** — you are buying odds over a pool. A boon is a
 * **consumable** that expires after five matches. A card is a **thing you either have or want**.
 * They are read differently, so they are laid out differently: a rack of packs, a pair of boon
 * tiles, and a grid of cards that can be compared against each other the way the collection's
 * grid lets them be.
 *
 * ### One list, still
 *
 * All of it is one [LazyVerticalGrid] — the headers, the pack rack and the boon pair are
 * full-width spans between the card cells. A `LazyColumn` holding a grid cannot work (the inner
 * grid has no height to measure against), and three separate scrollers on one screen is three
 * places for the player to lose their position.
 *
 * The pack rack is a plain `Row` with a scroll modifier rather than a `LazyRow`: eleven tiles is
 * not a list worth virtualising, and every tile stays composed, which is what lets a test scroll
 * to a pack by name.
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
) {
    val strings = LocalStrings.current
    // Split once per shelf rather than filtered three times per frame. `Item` is sealed and has
    // four cases, so `others` can only ever be `MiscItem` — kept as a row so an item nobody
    // planned for still appears rather than silently vanishing from the shelf.
    val shelves = remember(offers) { Shelves.of(offers) }

    val pick: (ShopOffer) -> Unit = { offer ->
        onSelect(shopOfferTestTag(offer).takeIf { it != selectedTag })
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CardOfferWidth),
        modifier = Modifier.testTag(SHOP_LIST_TEST_TAG).fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        if (onClaimStarter != null) {
            fullWidth {
                StarterPackPanel(starters = starters, cards = cards, onClaim = onClaimStarter)
            }
        }

        if (shelves.boosters.isNotEmpty()) {
            fullWidth {
                ShelfHeader(
                    "boosters",
                    strings[StringKeys.BOOSTERS],
                    shelves.boosters.size,
                )
            }
            fullWidth {
                val rack = rememberScrollState()

                Column {
                    Row(
                        modifier = Modifier.horizontalScroll(rack),
                        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
                    ) {
                        for (offer in shelves.boosters) {
                            BoosterTile(
                                offer = offer,
                                cards = cards,
                                isAffordable = offer.isAffordableBy(profile),
                                isSelected = shopOfferTestTag(offer) == selectedTag,
                                onClick = { pick(offer) },
                            )
                        }
                    }

                    ScrollHint(rack)
                }
            }
        }

        if (shelves.boons.isNotEmpty()) {
            fullWidth { ShelfHeader("boons", strings[StringKeys.BOONS], shelves.boons.size) }
            fullWidth {
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceSm)) {
                    for (offer in shelves.boons) {
                        BoonTile(
                            offer = offer,
                            cards = cards,
                            isAffordable = offer.isAffordableBy(profile),
                            isSelected = shopOfferTestTag(offer) == selectedTag,
                            onClick = { pick(offer) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (shelves.cards.isNotEmpty()) {
            fullWidth { ShelfHeader("cards", strings[StringKeys.CARDS], shelves.cards.size) }
            items(shelves.cards, key = ::shopOfferTestTag) { offer ->
                CardOffer(
                    offer = offer,
                    card = itemCard(offer.item, cards),
                    copies = (offer.item as? CardItem)?.let { profile.copiesOf(it.cardId) } ?: 0,
                    isAffordable = offer.isAffordableBy(profile),
                    isSelected = shopOfferTestTag(offer) == selectedTag,
                    onClick = { pick(offer) },
                )
            }
        }

        for (offer in shelves.others) {
            fullWidth {
                OfferRow(
                    offer = offer,
                    cards = cards,
                    isAffordable = offer.isAffordableBy(profile),
                    isSelected = shopOfferTestTag(offer) == selectedTag,
                    onClick = { pick(offer) },
                )
            }
        }
    }
}

/** The offers, sorted into the shelf each belongs on. */
private data class Shelves(
    val boosters: List<ShopOffer>,
    val boons: List<ShopOffer>,
    val cards: List<ShopOffer>,
    val others: List<ShopOffer>,
) {
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

@Composable
private fun ShelfHeader(slug: String, title: String, count: Int) {
    Row(
        modifier = Modifier
            .testTag(shopShelfTestTag(slug))
            .fillMaxWidth()
            .padding(top = SpaceXs),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Text(
            text = title.uppercase(),
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
 * The rack's scrollbar: how much of it is off-screen, where you are, and a handle to drag.
 *
 * Compose Multiplatform's real `HorizontalScrollbar` is a desktop-only artifact and this module is
 * common, so this is drawn by hand from the two numbers [ScrollState] already publishes — the thumb
 * is as wide a share of the track as the viewport is of the content.
 *
 * ### Why it had to become a control rather than stay a hint
 *
 * It was drawn as an indicator, on the claim that "the rack drags on a touch screen and answers
 * shift+wheel on a desktop". The first half is true. **The second is not**: a horizontal scroll
 * delta reaches this rack and moves nothing, so on a desktop the only way to reach the packs past
 * the fourth was to press the mouse on a *tile* — a thing whose whole job is to be clicked — and
 * haul it sideways. The wheel does what a wheel should do over a page, which is scroll the page.
 *
 * Redirecting the wheel was the other candidate and is worse: a band that swallows vertical
 * scrolling until it reaches its own end traps the page behind it, and a player who wanted the
 * cards below has to scroll nine packs sideways first.
 *
 * ### The hit area is not the bar
 *
 * The bar is [ScrollHintHeight] — three pixels of furniture — and nothing is draggable at three
 * pixels. So the drag belongs to a [ScrollHintTouchHeight] band that the bar is centred in, which
 * is invisible and is why the two heights exist separately.
 *
 * Nothing is drawn when everything fits, which is also the state before the first measurement
 * (`maxValue` is `Int.MAX_VALUE` until then).
 */
@Composable
private fun ScrollHint(state: ScrollState) {
    if (state.maxValue == 0 || state.maxValue == Int.MAX_VALUE) return

    val content = (state.viewportSize + state.maxValue).toFloat()
    val visible = if (content <= 0f) 1f else state.viewportSize / content
    val travelled = state.value.toFloat() / state.maxValue
    val thumb = visible.coerceIn(HINT_MIN_THUMB, 1f)
    // The track's own width, which only a measurement knows: a drag arrives in track pixels and
    // has to be spent in content pixels, and the two differ by however wide this happens to be.
    var track by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .testTag(SHOP_RACK_HINT_TEST_TAG)
            .fillMaxWidth()
            .padding(top = SpaceXs, start = SpaceLg, end = SpaceLg)
            .height(ScrollHintTouchHeight)
            .onSizeChanged { track = it.width.toFloat() }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // `dispatchRawDelta` rather than a `scroll` session: the drag *is* the
                    // session. The sign carries straight through — `ScrollState.value` counts how
                    // far the rack has been pushed to the left, which is the direction the thumb
                    // moves to reveal it.
                    state.dispatchRawDelta(delta * scrollPerTrackPixel(state, track, thumb))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScrollHintHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = HINT_TRACK)),
            // -1 is hard left and 1 is hard right, which is exactly how far along the track the
            // thumb has to sit for a scroll of `travelled`.
            contentAlignment = BiasAlignment(
                horizontalBias = -1f + 2f * travelled,
                verticalBias = 0f,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(thumb)
                    .height(ScrollHintHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = HINT_THUMB)),
            )
        }
    }
}

/**
 * How many pixels of rack one pixel of thumb travel is worth.
 *
 * The thumb crosses the part of the track it does not occupy — `track * (1 - thumb)` — while the
 * rack crosses all of `maxValue`, so the ratio is the second over the first. [thumb] is the
 * **drawn** width, floored at [HINT_MIN_THUMB], and it has to be: dragging a floored thumb must
 * still reach the end of the rack as it reaches the end of the track, and the true proportion would
 * leave the last packs unreachable on any rack long enough for the floor to bite.
 *
 * Zero when the thumb has nowhere to go, which is the guard the caller's `maxValue == 0` already
 * makes and is still worth making here: a track measured at zero would be divided by.
 */
private fun scrollPerTrackPixel(state: ScrollState, track: Float, thumb: Float): Float {
    val travel = track * (1f - thumb)
    return if (travel <= 0f) 0f else state.maxValue / travel
}

/**
 * One pack on the rack.
 *
 * No odds line, by decision: the number is the argument a gambling screen makes, and this shelf
 * would rather sell the pack than the bet. [packTerms] still computes it and the purchase sheet
 * is where it belongs if it comes back.
 */
@Composable
private fun BoosterTile(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    isAffordable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val name = itemName(strings, offer.item, cards)

    Column(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .width(BoosterTileWidth)
            .rowSurface(selected = isSelected)
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        // The pack's own artwork at the size it was drawn — four of the eleven have one, and
        // `ItemGlyph` falls back to the vector for those that do not.
        ItemGlyph(item = offer.item, description = name, size = BoosterArtSize)
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        OfferPrice(price = offer.price, isAffordable = isAffordable)
    }
}

/** One boon: what it does and for how long, which is the part a price alone does not say. */
@Composable
private fun BoonTile(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    isAffordable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val name = itemName(strings, offer.item, cards)

    Row(
        modifier = modifier
            .testTag(shopOfferTestTag(offer))
            .rowSurface(selected = isSelected)
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        ItemGlyph(item = offer.item, description = name, size = IconMd)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
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
        OfferPrice(price = offer.price, isAffordable = isAffordable)
    }
}

/**
 * One card for sale, as a cell of a grid rather than a line of a list.
 *
 * What used to fill this space was `APP_CARD_ITEM_DESC` — "a single card, added straight to your
 * collection" — under every card, the same sentence as many times as there were cards. The card's
 * own numbers say more in less room, and they are what one card is compared to another by.
 */
@Composable
private fun CardOffer(
    offer: ShopOffer,
    card: Card?,
    copies: Int,
    isAffordable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(vertical = SpaceXs, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (card == null) {
            ItemGlyph(
                item = offer.item,
                description = itemName(strings, offer.item, cards = emptyMap()),
                size = FramedThumbSide,
            )
        } else {
            // The tile the deck builder and the collection draw, not a fourth arrangement of the
            // same two badges. What the count counts here is the collection: the shop was the
            // one screen that did not know what the player already owned, so a duplicate cost
            // full price with no warning.
            CardTile(
                card = card,
                selected = isSelected,
                count = copies.takeIf { it > 0 },
                countTag = shopOwnedTestTag(offer),
            )
        }

        OfferPrice(price = offer.price, isAffordable = isAffordable)
    }
}
fun shopOwnedTestTag(offer: ShopOffer): String = "shop-owned-${itemSlug(offer.item)}"

/**
 * A price on the shelf, and whether the purse can reach it.
 *
 * Out of reach is said on the **price** and nowhere else. Dimming the whole offer, which is what
 * this used to do, hides the name and the description of everything expensive — and three cards
 * on this shelf cost more than a character will hold for a very long time.
 *
 * The coin and the grouped number themselves are [PriceTag], which is how every price in the app
 * is written; what belongs to the shop is only which of two colours it wears.
 */
@Composable
private fun OfferPrice(price: Int, isAffordable: Boolean) {
    PriceTag(
        price = price,
        color = if (isAffordable) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.error
        },
        coin = if (isAffordable) {
            LocalTtoColors.current.currency
        } else {
            MaterialTheme.colorScheme.error
        },
    )
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
    isAffordable: Boolean,
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

        OfferPrice(price = offer.price, isAffordable = isAffordable)
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

private val BoosterTileWidth = 116.dp

private val ScrollHintHeight = 3.dp

/**
 * The band the bar is dragged by, which is not the bar.
 *
 * Under the 48 dp an ordinary target gets, deliberately: a scrollbar sits between two shelves the
 * player is reading, and a thumb-sized strip of dead space there would cost more than the drag it
 * buys. This is about what a desktop scrollbar is, and the rack itself still drags at any point on
 * the tiles above it.
 */
private val ScrollHintTouchHeight = 20.dp

/** Faint enough to be furniture, visible enough to say the rack goes on. */
private const val HINT_TRACK = 0.10f

private const val HINT_THUMB = 0.35f

/** A thumb narrower than this stops reading as a thumb, however long the rack gets. */
private const val HINT_MIN_THUMB = 0.12f

private val BoosterArtSize = 44.dp

/** A card cell holds a 44 dp thumbnail, its four powers and a price. */
private val CardOfferWidth = 86.dp

private val SheetGlyphSize = 56.dp

private const val PERCENT = 100
