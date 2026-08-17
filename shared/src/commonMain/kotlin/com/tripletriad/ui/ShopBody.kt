package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.BoosterPricing
import com.tripletriad.data.ShopOffer
import com.tripletriad.data.StarterCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import kotlin.math.roundToInt

const val SHOP_LIST_TEST_TAG: String = "shop-list"
const val SHOP_BUY_TEST_TAG: String = "shop-buy"

const val SHOP_STARTER_TEST_TAG: String = "shop-starter"
const val SHOP_STARTER_CLAIM_TEST_TAG: String = "shop-starter-claim"

const val SHOP_NOTE_TEST_TAG: String = "shop-note"

fun shopOfferTestTag(offer: ShopOffer): String = "shop-offer-${itemSlug(offer.item)}"

fun shopOddsTestTag(offer: ShopOffer): String = "shop-odds-${itemSlug(offer.item)}"

@Composable
private fun packTerms(strings: Strings, item: Item, cards: Map<Int, Card>): String? {
    val pack = (item as? BoosterItem)?.boosterType ?: return null
    val odds = (BoosterPricing.fiveStarChance(pack, cards) * PERCENT).roundToInt()
    val floor = strings.format(
        StringKeys.PACK_GUARANTEE,
        BoosterPricing.guaranteedFloor(pack, cards).toString(),
    )
    return if (odds == 0) {
        floor
    } else {
        "$floor$DOT_SEPARATOR" +
            strings.format(StringKeys.PACK_ODDS, odds.toString())
    }
}

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
    if (onClaimStarter != null) {
        StarterPackPanel(
            starters = starters,
            cards = cards,
            onClaim = onClaimStarter,
        )
    }

    LazyColumn(
        modifier = Modifier.testTag(SHOP_LIST_TEST_TAG).fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        items(offers, key = ::shopOfferTestTag) { offer ->
            OfferRow(
                offer = offer,
                cards = cards,
                isAffordable = offer.isAffordableBy(profile),
                isSelected = shopOfferTestTag(offer) == selectedTag,
                onClick = { onSelect(shopOfferTestTag(offer).takeIf { it != selectedTag }) },
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

@Composable
private fun OfferRow(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    isAffordable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    // Dimmed rather than disabled: an offer out of reach is still worth reading, and it is still
    // selectable so the Buy button can say what it would cost.
    val alpha = if (isAffordable) 1f else UNAFFORDABLE_ALPHA

    Row(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            // Exclusive: the shelf has one selection at a time and the Buy button acts on it, so
            // these are radio buttons wearing a row's clothes.
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        // The card if the offer is a card, its icon otherwise — a booster pack has artwork of
        // its own, and a shelf of nothing but text is the thing this screen was worst at.
        val card = itemCard(offer.item, cards)
        if (card != null) {
            CardThumb(card = card)
        } else {
            ItemGlyph(
                item = offer.item,
                description = itemName(strings, offer.item, cards),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = itemName(strings, offer.item, cards),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings[offer.item.descriptionKey],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * FAINT),
                style = MaterialTheme.typography.labelSmall,
                // Two lines, not one. At the old type scale a pack's description fitted; at
                // Material's it does not, and `Six cartes communes pour démarrer une collec…` is a
                // shelf that will not say what it is selling. A row growing by one line is a
                // cheaper price than a description nobody can finish reading.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // What a pack actually promises. A row that says "five cards" and nothing else asks
            // the player to guess its odds, and they guess wrong in whichever direction
            // disappoints them. Both numbers come from the pool — see `BoosterPricing`.
            packTerms(strings, offer.item, cards)?.let { terms ->
                Text(
                    text = terms,
                    modifier = Modifier.testTag(shopOddsTestTag(offer)),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                    style = MaterialTheme.typography.labelSmall,
                    // Same reasoning as the description above, and it matters more here: this
                    // line is the pack's *odds*, and `· Chance de …` is the half that got cut.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = "${offer.price} ${strings[StringKeys.MGP]}",
            color = if (isAffordable) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val UNAFFORDABLE_ALPHA = DISABLED

private const val PERCENT = 100
