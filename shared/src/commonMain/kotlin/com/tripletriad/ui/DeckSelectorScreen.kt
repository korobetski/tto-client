package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.Npc
import com.tripletriad.protocol.ANY_DECK
import com.tripletriad.ui.theme.LocalTtoColors

const val DECK_SELECT_TEST_TAG: String = "deck-select"
const val DECK_SELECT_CHOOSE_TEST_TAG: String = "deck-select-choose"
const val DECK_SELECT_RANDOM_TEST_TAG: String = "deck-select-random"
const val DECK_SELECT_EMPTY_TEST_TAG: String = "deck-select-empty"

fun deckChoiceTestTag(index: Int): String = "deck-choice-$index"

@Composable
internal fun DeckSelectorScreen(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    npc: Npc,
    rules: GameRules,
    onChoose: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val cards = remember(catalog, format) {
        catalog.admittedBy(format).associateBy { it.id }
    }
    val decks = remember(profile.decks, catalog, format) {
        PveMatches.playableDecks(profile, catalog, format)
    }
    var selected by remember(decks) { mutableStateOf(if (decks.isEmpty()) null else 0) }

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.CARD_DECKS],
        onBack = onBack,
    ) {
        // Who is being played and under what. The original showed neither here — `RulesDigest` is
        // on the board, one screen later — and the rules are exactly what a deck should answer:
        // Reverse or Fallen Ace turns a deck of aces into the wrong deck.
        OpponentLine(npc = npc, rules = rules)

        if (decks.isEmpty()) {
            EmptyNote(strings[StringKeys.NO_FULL_DECK], DECK_SELECT_EMPTY_TEST_TAG)
        } else {
            LazyColumn(
                modifier = Modifier
                    .testTag(DECK_SELECT_TEST_TAG)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(decks) { row, (slot, deck) ->
                    DeckChoiceRow(
                        deck = deck,
                        slot = slot,
                        row = row,
                        cards = cards,
                        isSelected = selected == row,
                        onClick = { selected = row },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.RANDOM_DECK], DECK_SELECT_RANDOM_TEST_TAG) {
                    // **No deck**, rather than five cards drawn here. This screen used to hand the
                    // deal a hand, and could: the deal was local. It is the referee's now, and the
                    // request carries a *slot* so that a client cannot name a card it does not own
                    // — see `PveMatchRequest.deck`. So "Random" is the absence of a choice, which
                    // is what the player is saying, and the server draws.
                    onChoose(ANY_DECK)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                WideButton(
                    label = strings[StringKeys.CHOOSE_DECK],
                    tag = DECK_SELECT_CHOOSE_TEST_TAG,
                    enabled = selected != null,
                    // The **save slot**, not the row: `playableDecks` filters incomplete decks
                    // out and `IndexedValue.index` is what survives that. The server resolves it
                    // against the profile it holds.
                    onClick = { selected?.let { onChoose(decks[it].index) } },
                )
            }
        }
    }
}

@Composable
private fun OpponentLine(npc: Npc, rules: GameRules) {
    val strings = LocalStrings.current
    val keys = rules.activeRuleKeys()

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = strings[npc.nameKey],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (keys.isNotEmpty()) {
            Text(
                text = keys.joinToString(DOT_SEPARATOR) { strings[it] },
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeckChoiceRow(
    deck: Deck,
    slot: Int,
    row: Int,
    cards: Map<Int, Card>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(deckChoiceTestTag(row))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .ttoClickable(role = Role.RadioButton, selected = isSelected, onClick = onClick)
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deckLabel(strings, deck, slot),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${strings[StringKeys.DECK_POWER]} ${deckPower(deck, cards)}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                // See `DecksBody`: one line cuts the number this line exists to report.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            for (position in 0 until HAND_SIZE) {
                DeckPosition(card = deck.cards.getOrNull(position)?.let(cards::get))
            }
        }
    }
}
