package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Format
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.DeckLimits
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import kotlinx.coroutines.launch

const val DECK_LIST_TEST_TAG: String = "deck-list"
const val DECK_EDITOR_TEST_TAG: String = "deck-editor"
const val DECK_NAME_TEST_TAG: String = "deck-name"
const val DECK_SAVE_TEST_TAG: String = "deck-save"
const val DECK_RESET_TEST_TAG: String = "deck-reset"
const val DECK_POWER_TEST_TAG: String = "deck-power"
const val DECK_PICK_GRID_TEST_TAG: String = "deck-pick-grid"

const val DECK_MISSING_TEST_TAG: String = "deck-missing"

/** The editor's live per-rank counters — `★5 1 / 1  ·  ★4 0 / 2`. */
const val DECK_LIMITS_TEST_TAG: String = "deck-limits"

const val DECK_OVER_LIMIT_TEST_TAG: String = "deck-over-limit"

fun deckMissingTestTag(index: Int): String = "deck-missing-$index"

fun deckSlotTestTag(index: Int): String = "deck-slot-$index"

fun deckPositionTestTag(index: Int): String = "deck-position-$index"

fun deckPickTestTag(cardId: Int): String = "deck-pick-$cardId"

fun deckRemainingTestTag(cardId: Int): String = "deck-remaining-$cardId"

fun deckOverLimitTestTag(index: Int): String = "deck-over-limit-$index"

@Composable
internal fun ColumnScope.DecksBody(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    editing: Int?,
    onEdit: (Int?) -> Unit,
    onPersist: suspend (GameSave) -> Unit,
) {
    val cards = remember(catalog, format) {
        catalog.admittedBy(format).associateBy { it.id }
    }

    if (editing == null) {
        DeckSlots(profile = profile, cards = cards, onEdit = { onEdit(it) })
    } else {
        DeckEditor(
            profile = profile,
            slot = editing,
            cards = cards,
            onPersist = onPersist,
            onDone = { onEdit(null) },
        )
    }
}

@Composable
private fun DeckSlots(profile: GameSave, cards: Map<Int, Card>, onEdit: (Int) -> Unit) {
    Column(
        modifier = Modifier.testTag(DECK_LIST_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (index in 0 until GameSave.MAX_DECKS) {
            val deck = profile.decks.getOrNull(index) ?: Deck(name = "", cards = emptyList())
            DeckSlotRow(
                index = index,
                deck = deck,
                cards = cards,
                unowned = unownedPositions(deck, profile.cards),
                overLimit = DeckLimits.overLimit(deck.cards, cards),
                onClick = { onEdit(index) },
            )
        }
    }
}

@Composable
private fun DeckSlotRow(
    index: Int,
    deck: Deck,
    cards: Map<Int, Card>,
    unowned: Set<Int>,
    overLimit: Map<Int, Int>,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(deckSlotTestTag(index))
            .fillMaxWidth()
            .rowSurface()
            .ttoClickable(onClick = onClick)
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deckLabel(strings, deck, index),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${deck.cards.size} / $HAND_SIZE$DOT_SEPARATOR" +
                    "${strings[StringKeys.DECK_POWER]} ${deckPower(deck, cards)}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                // Two lines: this is `0 / 5 · Deck power 34`, the row also carries five
                // thumbnails, and at one line the **number** is what falls off the end — so the
                // line was clipping to `0 / 5 · Puissance du` and reporting no power at all.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Said in words as well as in grey, because grey alone is a hint and this is a
            // *reason*: a deck of five cards that never appears in the selector is otherwise a
            // screen refusing to explain itself. `error` and not the faint tone the line above
            // uses — every other line in this row is a fact about the deck, and this one is the
            // only thing standing between the player and playing it.
            if (unowned.isNotEmpty()) {
                Text(
                    text = strings.format(StringKeys.DECK_MISSING_CARDS, "${unowned.size}"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(deckMissingTestTag(index)),
                )
            }
            // Said for the same reason the line above is, and in the same tone: a deck that never
            // appears in the selector because it holds two five-stars is otherwise a screen
            // refusing to explain itself, and this one is repairable in two taps.
            if (overLimit.isNotEmpty()) {
                Text(
                    text = overLimitText(strings, overLimit),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(deckOverLimitTestTag(index)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HairlineWidth)) {
            for (position in 0 until HAND_SIZE) {
                DeckPosition(
                    card = deck.cards.getOrNull(position)?.let(cards::get),
                    owned = position !in unowned,
                )
            }
        }
    }
}

@Composable
private fun DeckEditor(
    profile: GameSave,
    slot: Int,
    cards: Map<Int, Card>,
    onPersist: suspend (GameSave) -> Unit,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val stored = profile.decks.getOrNull(slot) ?: Deck(name = "", cards = emptyList())
    // The editor's own copy, keyed on the slot so switching slots restarts it rather than carrying
    // the previous deck's cards across. Nothing here reaches the profile until Save.
    var draft by remember(slot) { mutableStateOf(stored) }
    var name by remember(slot) { mutableStateOf(deckLabel(strings, stored, slot)) }
    // Distinct cards, ascending — the grid draws one cell per card and says how many copies are
    // still free on it, rather than one cell per copy. See [remaining].
    val owned = remember(profile.cards, cards) {
        profile.cards.keys.sorted().mapNotNull(cards::get)
    }

    Column(
        modifier = Modifier.testTag(DECK_EDITOR_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_DECK_NAME) },
            label = { Text(strings[StringKeys.DECK]) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.testTag(DECK_NAME_TEST_TAG).fillMaxWidth(),
        )

        // Recomputed against the **draft**, so removing the greyed card clears the warning as the
        // player watches — which is the whole reason the editor greys them too rather than leaving
        // it to the list. This is the screen where the deck can actually be repaired.
        val unowned = unownedPositions(draft, profile.cards)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (position in 0 until HAND_SIZE) {
                val card = draft.cards.getOrNull(position)?.let(cards::get)
                // The tag sits on the *clickable* box and not on the frame inside it: a
                // `clickable` merges its descendants' semantics, so a tag one level down is
                // absorbed and unreachable from the merged tree a test drives.
                Box(
                    modifier = Modifier
                        .testTag(deckPositionTestTag(position))
                        .ttoClickable(enabled = card != null) {
                            draft = draft.minusCardAt(position)
                        },
                ) {
                    DeckPosition(card = card, owned = position !in unowned)
                }
            }
        }

        if (unowned.isNotEmpty()) {
            Text(
                text = strings.format(StringKeys.DECK_MISSING_CARDS, "${unowned.size}"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag(DECK_MISSING_TEST_TAG),
            )
        }

        // The rule, stated as a live count rather than as a sentence to read once. It is drawn
        // whatever the draft holds — `0 / 1` before a five-star is picked as much as `1 / 1`
        // after — because a cap the player only meets when they hit it is a cap they meet as a
        // refusal. The numbers come from `DeckLimits`, so the screen and the server cannot drift.
        val overLimit = DeckLimits.overLimit(draft.cards, cards)

        Text(
            text = "${strings[StringKeys.DECK_LIMITS]} ${limitsText(draft, cards)}",
            color = if (overLimit.isEmpty()) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag(DECK_LIMITS_TEST_TAG),
        )

        // Only for a deck that is *already* over a cap — one built before the caps existed, or one
        // a set's re-rank moved. The picker cannot produce one, so this is a repair prompt and not
        // a running validation message.
        if (overLimit.isNotEmpty()) {
            Text(
                text = overLimitText(strings, overLimit),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag(DECK_OVER_LIMIT_TEST_TAG),
            )
        }

        Text(
            text = "${strings[StringKeys.DECK_POWER]} ${deckPower(draft, cards)}" +
                "$DOT_SEPARATOR${draft.cards.size} / $HAND_SIZE",
            // Affordable as well as complete. `5 / 5` in the affirmative tone on a deck that
            // cannot be dealt is the screen agreeing the deck is finished while every other place
            // refuses it.
            color = if (draft.isComplete && unowned.isEmpty() && overLimit.isEmpty()) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(DECK_POWER_TEST_TAG),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.RESET_DECK], DECK_RESET_TEST_TAG) {
                    draft = draft.emptied()
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.SAVE], DECK_SAVE_TEST_TAG) {
                    scope.launch {
                        onPersist(profile.withDeck(slot, draft.copy(name = name.trim())))
                        onDone()
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = DeckThumbSize + 4.dp),
            modifier = Modifier
                .testTag(DECK_PICK_GRID_TEST_TAG)
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(owned, key = { it.id }) { card ->
                // How many copies this deck has not spent yet. A card whose copies are all in the
                // deck is dimmed and refuses the tap, because `Deck.isAffordable` would refuse the
                // deck and the server would refuse the match — a rule the player meets as a
                // rejection they cannot act on is worse than one they can see coming.
                val remaining = profile.copiesOf(card.id) - draft.copiesUsed(card.id)

                // And whether the deck may hold another of this rank. Dimmed and inert exactly as
                // a spent copy is, because to the player the two are the same fact — this card
                // cannot go in — and a rule met as a rejection the server issues later is worse
                // than one met as a tile that will not depress. See `DeckLimits.admits`.
                val admitted = DeckLimits.admits(draft.cards, cards, card)

                // Centred in its cell, and the frame sized to the card rather than to the
                // cell. `GridCells.Adaptive` hands an item a **fixed** cross-axis width — whatever
                // is left over once the columns divide the row — so a border taken straight off
                // the cell was 51 px of frame around a 44 px thumbnail, and the seven that did
                // not fit stuck out to the right of every card on the screen. The grid was never
                // wider than its column; the frame was wider than what it framed. The frame now
                // belongs to `CardThumb`, which is sized by the art rather than by the cell.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(deckPickTestTag(card.id))
                            // A pick goes in and comes out of the draft, so it toggles rather
                            // than chooses: `Checkbox` is what a screen reader should hear.
                            .ttoClickable(
                                role = Role.Checkbox,
                                selected = card.id in draft.cards,
                                enabled = !draft.isComplete && remaining > 0 && admitted,
                            ) {
                                draft = draft.plusCard(card.id)
                            },
                    ) {
                        // The same tile the collection and the shop draw. What the badge counts
                        // here is what the *draft* has left, not what the profile owns — see
                        // `remaining`.
                        CardTile(
                            card = card,
                            dim = remaining <= 0 || !admitted,
                            selected = card.id in draft.cards,
                            count = remaining.takeIf { profile.copiesOf(card.id) > 1 },
                            countTag = deckRemainingTestTag(card.id),
                        )
                    }

                    // Under the thumbnail rather than on it. A 44dp thumbnail already renders the
                    // powers, at a size nobody reads — which is why building a deck meant tapping
                    // each card to find out what it was. See [CardStatsLine]. Without the element:
                    // it is on the thumbnail now, twice its old size.
                    CardStatsLine(
                        card = card,
                        showType = false,
                        modifier = Modifier.alpha(if (remaining > 0) 1f else SPENT_ALPHA),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeckPosition(card: Card?, owned: Boolean = true) {
    if (card == null) {
        EmptyCardSlot(size = DeckThumbSize)
    } else {
        CardThumb(
            card = card,
            size = DeckThumbSize,
            modifier = if (owned) Modifier else Modifier.alpha(SPENT_ALPHA),
        )
    }
}

internal fun unownedPositions(deck: Deck, owned: Map<Int, Int>): Set<Int> {
    val seen = mutableMapOf<Int, Int>()
    return deck.cards.withIndex().mapNotNullTo(mutableSetOf()) { (position, id) ->
        val used = (seen[id] ?: 0) + 1
        seen[id] = used
        position.takeIf { used > (owned[id] ?: 0) }
    }
}

internal fun deckLabel(strings: Strings, deck: Deck, index: Int): String =
    deck.name.ifBlank { "${strings[StringKeys.DECK]} ${index + 1}" }

/**
 * The caps as counters, highest rank first — `★5 1 / 1  ·  ★4 0 / 2`.
 *
 * Built out of [DeckLimits.MAX_BY_RARITY] rather than written out, so a cap that changes changes
 * here too. Stars rather than the word "rank" because the tiles the player is choosing between are
 * already labelled with stars, and a screen that names the same thing two ways is a screen that has
 * to be read twice.
 */
internal fun limitsText(deck: Deck, cards: Map<Int, Card>): String {
    val tally = DeckLimits.tally(deck.cards, cards)
    return DeckLimits.MAX_BY_RARITY.entries
        .sortedByDescending { it.key }
        .joinToString(DOT_SEPARATOR) { (rarity, limit) ->
            "★$rarity ${tally[rarity] ?: 0} / $limit"
        }
}

/** Every broken cap, in the tone of a repair: what the deck holds, and what it may. */
internal fun overLimitText(strings: Strings, overLimit: Map<Int, Int>): String =
    overLimit.entries.sortedByDescending { it.key }.joinToString(DOT_SEPARATOR) { (rarity, used) ->
        strings.format(
            StringKeys.DECK_OVER_LIMIT,
            "$used",
            "$rarity",
            "${DeckLimits.limitOf(rarity)}",
        )
    }

internal fun deckPower(deck: Deck, cards: Map<Int, Card>): Int =
    deck.cards.sumOf { cards[it]?.rarity ?: 0 }

private const val MAX_DECK_NAME = 24

internal val DeckThumbSize = 40.dp

private const val SPENT_ALPHA = 0.3f
