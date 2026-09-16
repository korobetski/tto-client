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
import androidx.compose.ui.text.style.TextOverflow
import com.tripletriad.data.ShopOffer
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave

/**
 * The same pack as [BoosterTile], as one line of a list: a phone's column fits six of these where
 * it fitted three and a half tiles, and the tile's centred stack left most of each row empty.
 *
 * Every tag the tile carries is carried here too, on the same facts, so a test about what a pack
 * says does not care which of the two drew it. The missing count is the short form: the long one
 * is a sentence, and this row has one line for two facts.
 */
@Composable
internal fun BoosterRow(
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

    Row(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        PackArt(offer = offer, profile = profile, type = type, name = name)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            facts?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpaceSm),
                ) {
                    RarityRange(stars = it.stars, tag = shopPackFactsTestTag(offer))
                    MissingLine(offer = offer, missing = it.missing, short = true)
                }
            }
        }
        OfferPrice(offer = offer, profile = profile, gapLine = true)
    }
}
