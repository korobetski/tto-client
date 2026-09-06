package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.ShopOffer
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
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
        facts?.let { PackFactLines(offer = offer, facts = it, strings = strings) }
        OfferPrice(offer = offer, profile = profile)
    }
}

/** [PackFacts] in words: what comes out of the pack, then what the collection still wants. */
@Composable
private fun PackFactLines(offer: ShopOffer, facts: PackFacts, strings: Strings) {
    val contents = listOfNotNull(
        strings.format(StringKeys.PACK_CARDS, facts.draws.toString()),
        facts.stars?.let(::starRange),
    ).joinToString(DOT_SEPARATOR)

    Text(
        text = contents,
        modifier = Modifier.testTag(shopPackFactsTestTag(offer)),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        modifier = Modifier.testTag(shopPackMissingTestTag(offer)),
        text = if (facts.missing == 0) {
            strings[StringKeys.PACK_COMPLETE]
        } else {
            strings.format(StringKeys.PACK_MISSING, facts.missing.toString())
        },
        // Lit when there is something to gain and quiet when there is not: a pack whose pool the
        // collection already holds is still for sale, it is just no longer an argument.
        color = if (facts.missing == 0) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
        } else {
            MaterialTheme.colorScheme.tertiary
        },
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
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
        OfferPrice(offer = offer, profile = profile)
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
 * A price on the shelf, and — when the purse cannot reach it — **by how much**.
 *
 * Out of reach used to be said by turning the number red and disabling the buy button, which
 * tells a player that they cannot have this and nothing else. The gap is the actionable half:
 * "you need 400 more" is a match away and "you need 2 760 more" is not, and only the shelf knows
 * which of the two this is.
 *
 * Dimming the whole offer, which this did before the red, hid the name and the description of
 * everything expensive — and three cards on this shelf cost more than a character will hold for a
 * very long time.
 *
 * The coin and the grouped number themselves are [PriceTag], which is how every price in the app
 * is written; what belongs to the shop is the colour and the line underneath.
 */
@Composable
internal fun OfferPrice(offer: ShopOffer, profile: GameSave) {
    val strings = LocalStrings.current
    val short = offer.price - profile.mgp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PriceTag(
            price = offer.price,
            color = if (short <= 0) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.error
            },
            coin = if (short <= 0) {
                LocalTtoColors.current.currency
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (short > 0) {
            Text(
                text = strings.format(StringKeys.PRICE_SHORT, grouped(short)),
                modifier = Modifier.testTag(shopShortTestTag(offer)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
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

/** Tighter than [SpaceXs]: a card cell is 86 dp wide and every dp of it is the card. */
private val CardCellPadding = 2.dp

private const val STAR_RANGE = "–"
