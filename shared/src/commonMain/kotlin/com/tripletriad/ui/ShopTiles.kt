package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.data.ShopOffer
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.ui.theme.LocalTtoColors

/** What one card of the shelf is already worth to this collection. */
fun shopOwnedTestTag(offer: ShopOffer): String = "shop-owned-${itemSlug(offer.item)}"

/** What the purse is short of an offer. Absent when the offer is affordable. */
fun shopShortTestTag(offer: ShopOffer): String = "shop-short-${itemSlug(offer.item)}"

/** What a pack holds, on the tile that sells it. Absent for anything that is not a pack. */
fun shopPackFactsTestTag(offer: ShopOffer): String = "shop-pack-${itemSlug(offer.item)}"

/** The rank a base pack is named after, over the corner of its art. Absent on themed packs. */
fun shopRarityTestTag(offer: ShopOffer): String = "shop-rarity-${itemSlug(offer.item)}"

/** What the purse is short of an offer, under its price on the shelf. Absent when affordable. */
fun shopTileShortTestTag(offer: ShopOffer): String = "shop-tile-short-${itemSlug(offer.item)}"

/** How much of a pack's pool the collection is still missing, on the tile that sells it. */
fun shopPackMissingTestTag(offer: ShopOffer): String = "shop-missing-${itemSlug(offer.item)}"

/**
 * What a pack contains, as the three numbers that decide whether to buy it.
 *
 * @param draws how many cards one pack hands over.
 * @param stars the rarities its pool spans, `null` when the format admits none of them — a pool
 *   may name cards this format leaves out, and a range over an empty list is not a range.
 * @param missing how many of the pool's cards the collection does **not** hold. The number the
 *   purchase actually turns on, and the one the shelf never showed: two packs at the same price
 *   are not the same offer when one of them can only hand back duplicates.
 */
internal data class PackFacts(val draws: Int, val stars: IntRange?, val missing: Int)

/**
 * [PackFacts] for [type], read off the pool the pack draws from.
 *
 * No request and no new state: [BoosterType.pool] is the pack's own list of card ids, [cards] is
 * the table the shop is already priced from, and [owned] is the collection the profile is already
 * carrying. Counted over the **whole pool** rather than over the format's share of it, because
 * the pool is what the pack draws from whatever the format admits.
 */
internal fun packFacts(
    type: BoosterType,
    cards: Map<Int, Card>,
    owned: Map<Int, Int>,
): PackFacts {
    val rarities = type.pool.mapNotNull { cards[it]?.rarity }
    return PackFacts(
        draws = type.cardCount,
        stars = if (rarities.isEmpty()) null else rarities.min()..rarities.max(),
        missing = type.pool.count { (owned[it] ?: 0) == 0 },
    )
}

/**
 * A rarity range as stars: `★★` alone when a pool is of one rarity, `★–★★★★` when it spans.
 *
 * The dash rather than a word, so the line reads the same in the four bundles and needs none of
 * them: a range of stars is punctuation, not a sentence.
 */
internal fun starRange(stars: IntRange): String = if (stars.first == stars.last) {
    starsOf(stars.first)
} else {
    "${starsOf(stars.first)}$STAR_RANGE${starsOf(stars.last)}"
}

/**
 * One pack, as what it holds rather than as a name and a price.
 *
 * The tile used to carry the pack's picture, its name and its cost, which is everything except
 * the question being asked — *is there anything in it I do not already have*. That number is
 * [PackFacts.missing], and it costs a `count` over a list the client already has in memory.
 *
 * "1 card" is not on it: every pack hands over one, so a line that never differs from one tile to
 * the next compared nothing. The ranks the pool spans are drawn with the game's rarity icons, where
 * "★–★★★★" in a 152 dp column used to lose its tail.
 *
 * The odds line stays off the shelf, by the same decision as before: [packTerms] computes it and
 * the purchase sheet is where a player weighing a bet reads it, once rather than eleven times.
 */
@Composable
internal fun BoosterTile(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    profile: GameSave,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val name = itemName(strings, offer.item, cards)
    val type = (offer.item as? BoosterItem)?.boosterType
    val facts = remember(type, cards, profile.cards) {
        type?.let { packFacts(it, cards, profile.cards) }
    }

    Column(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        PackArt(offer = offer, profile = profile, type = type, name = name)
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
        facts?.let { PackFactLines(offer = offer, facts = it) }
        OfferPrice(offer = offer, profile = profile, gapLine = true)
    }
}

/**
 * A pack's own artwork: the four tribe packs carry their emblem, the other eleven share the plain
 * wrapper, and `ItemGlyph` falls back to the vector only if neither loaded.
 *
 * Five of those eleven are one series told apart by nothing but the name, so the wrapper is marked
 * with the rank the name stands for.
 */
@Composable
internal fun PackArt(offer: ShopOffer, profile: GameSave, type: BoosterType?, name: String) {
    OfferArt(offer = offer, profile = profile) {
        ItemGlyph(item = offer.item, description = name, size = BoosterArtSize)
        type?.let(::baseRarity)?.let { rarity ->
            RarityIcon(
                rarity = rarity,
                size = RarityBadgeSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = SpaceXs, y = SpaceXs)
                    .testTag(shopRarityTestTag(offer))
                    .semantics { contentDescription = starsOf(rarity) },
            )
        }
    }
}

/**
 * An offer's picture, dimmed when the purse cannot reach it.
 *
 * Only the picture. Dimming the whole tile, which the shop did once, hid the name and the facts of
 * everything expensive — and what is out of reach is still worth reading about.
 */
@Composable
internal fun OfferArt(
    offer: ShopOffer,
    profile: GameSave,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.alpha(if (offer.isAffordableBy(profile)) 1f else FAINT),
        content = content,
    )
}

/**
 * The star rank a base pack is named after — Bronze one, Platinum five — or null for a themed pack.
 *
 * The pack's place in the series rather than a reading of its pool: the pool's own range is the
 * line under the name, and a badge that repeated it would say one fact twice on one tile.
 */
internal fun baseRarity(type: BoosterType): Int? =
    BASE_PACKS.indexOf(type).takeIf { it >= 0 }?.plus(1)

private val BASE_PACKS = listOf(
    BoosterType.BRONZE,
    BoosterType.SILVER,
    BoosterType.GOLD,
    BoosterType.MITHRIL,
    BoosterType.PLATINUM,
)

/** The game's icon for [rarity], or its stars as text when the icons did not load. */
@Composable
internal fun RarityIcon(rarity: Int, size: Dp, modifier: Modifier = Modifier) {
    val icon = LocalUiArt.current?.icon("card_r${rarity}_icon")
    if (icon == null) {
        Text(
            text = starsOf(rarity),
            modifier = modifier,
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    } else {
        Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size))
    }
}

/** [PackFacts] drawn: the ranks the pool spans, then what the collection still wants. */
@Composable
private fun PackFactLines(offer: ShopOffer, facts: PackFacts) {
    RarityRange(stars = facts.stars, tag = shopPackFactsTestTag(offer))
    MissingLine(offer = offer, missing = facts.missing, short = false)
}

/**
 * The ranks a pool spans, as the game's rarity icons.
 *
 * Laid out with or without a range, so a pool the format admits none of does not leave its tile
 * one line shorter than its neighbours.
 */
@Composable
internal fun RarityRange(
    stars: IntRange?,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    Row(
        modifier = modifier
            .height(RarityRangeSize)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .semantics(mergeDescendants = true) {
                if (stars != null) contentDescription = starRange(stars)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        if (stars != null) {
            RarityIcon(rarity = stars.first, size = RarityRangeSize)
            if (stars.last != stars.first) {
                Text(
                    text = STAR_RANGE,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                )
                RarityIcon(rarity = stars.last, size = RarityRangeSize)
            }
        }
    }
}

/**
 * How much of a pool the collection lacks: lit when there is something to gain, quiet when there
 * is not. A pack whose pool the collection already holds is still for sale; it is just no longer
 * an argument.
 */
@Composable
internal fun MissingLine(offer: ShopOffer, missing: Int, short: Boolean) {
    val strings = LocalStrings.current
    Text(
        modifier = Modifier.testTag(shopPackMissingTestTag(offer)),
        text = when {
            missing == 0 -> strings[StringKeys.PACK_COMPLETE]
            short -> strings.format(StringKeys.PACK_MISSING_SHORT, missing.toString())
            else -> strings.format(StringKeys.PACK_MISSING, missing.toString())
        },
        color = if (missing == 0) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
        } else {
            MaterialTheme.colorScheme.tertiary
        },
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        maxLines = if (short) 1 else 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One boon: what it does and for how long, which is the part a price alone does not say. */
@Composable
internal fun BoonTile(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    profile: GameSave,
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
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        OfferArt(offer = offer, profile = profile) {
            ItemGlyph(item = offer.item, description = name, size = IconMd)
        }
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
        OfferPrice(offer = offer, profile = profile, gapLine = true)
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
internal fun CardOffer(
    offer: ShopOffer,
    card: Card?,
    profile: GameSave,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val copies = (offer.item as? CardItem)?.let { profile.copiesOf(it.cardId) } ?: 0

    Column(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(vertical = SpaceXs, horizontal = CardCellPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CardCellPadding),
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

        OfferPrice(offer = offer, profile = profile)
    }
}

/**
 * A price on the shelf, red when the purse cannot reach it — and, where there is room, by how much.
 *
 * @param gapLine whether "you need 510 more" goes under the price. When it does, the line is laid
 *   out on every tile, blank on the ones in reach: drawn only where it had something to say, it
 *   once left a shelf of eleven packs at two heights. The 86 dp card cells have no room for it —
 *   their red is the whole message, and the purchase sheet names the number.
 *
 * The coin and the grouped number themselves are [PriceTag], which is how every price in the app
 * is written; what belongs to the shop is the colour.
 */
@Composable
internal fun OfferPrice(offer: ShopOffer, profile: GameSave, gapLine: Boolean = false) {
    val affordable = offer.isAffordableBy(profile)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PriceTag(
            price = offer.price,
            color = if (affordable) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.error
            },
            coin = if (affordable) {
                LocalTtoColors.current.currency
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (gapLine) {
            Text(
                text = if (affordable) {
                    ""
                } else {
                    LocalStrings.current.format(
                        StringKeys.PRICE_SHORT,
                        grouped(offer.price - profile.mgp),
                    )
                },
                modifier = if (affordable) {
                    Modifier
                } else {
                    Modifier.testTag(
                        shopTileShortTestTag(offer),
                    )
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Wide enough for a pack's name, its contents line and its price without any of them wrapping. */
internal val BoosterTileWidth = 152.dp

/** A card cell holds a 44 dp thumbnail, its four powers and a price. */
internal val CardOfferWidth = 86.dp

/** A boon is a row, and two of them fit side by side on anything wider than a phone. */
internal val BoonTileWidth = 240.dp

private val BoosterArtSize = 44.dp

/** Over the corner of a 44 dp pack: large enough to count its stars, small enough to leave it. */
private val RarityBadgeSize = 22.dp

/** A `labelSmall` line tall, so the range takes the room the text it replaced took. */
private val RarityRangeSize = 18.dp

/** Tighter than [SpaceXs]: a card cell is 86 dp wide and every dp of it is the card. */
private val CardCellPadding = 2.dp

private const val STAR_RANGE = "–"
