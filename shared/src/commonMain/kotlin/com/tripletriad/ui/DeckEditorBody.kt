package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.DeckLimits
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import kotlinx.coroutines.launch

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

fun deckPositionTestTag(index: Int): String = "deck-position-$index"

fun deckPickTestTag(cardId: Int): String = "deck-pick-$cardId"

fun deckRemainingTestTag(cardId: Int): String = "deck-remaining-$cardId"

fun deckShiftLeftTestTag(position: Int): String = "deck-shift-left-$position"

fun deckShiftRightTestTag(position: Int): String = "deck-shift-right-$position"

@Composable
internal fun DeckEditor(
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                    // Order is not decoration: under `RULE_ORDER` it is the sequence the hand is
                    // played in, and it is the only thing a card's position in a deck decides —
                    // see `Deck.plusCard`, whose KDoc says a player who wants a different order
                    // "removes and re-adds". These two arrows are that, without emptying the slot
                    // and rebuilding it around the one card that had to move.
                    Row {
                        MoveButton(
                            icon = TtoIcons.Back,
                            description = strings[StringKeys.MOVE_LEFT],
                            tag = deckShiftLeftTestTag(position),
                            enabled = card != null && position > 0,
                            size = ShiftButtonSize,
                            onClick = { draft = draft.withCardMoved(position, position - 1) },
                        )
                        MoveButton(
                            icon = TtoIcons.Forward,
                            description = strings[StringKeys.MOVE_RIGHT],
                            tag = deckShiftRightTestTag(position),
                            enabled = card != null && position < draft.cards.size - 1,
                            size = ShiftButtonSize,
                            onClick = { draft = draft.withCardMoved(position, position + 1) },
                        )
                    }
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

/**
 * This deck with the card at [from] moved to [to], or unchanged when either is not a filled
 * position.
 *
 * Here rather than on [Deck] in `core` because nothing outside this screen asks it: the order is
 * already meaningful to the engine — `RULE_ORDER` deals the hand in it — and the engine needs no
 * help rearranging a list it only reads.
 */
internal fun Deck.withCardMoved(from: Int, to: Int): Deck {
    if (from !in cards.indices || to !in cards.indices || from == to) return this
    val moved = cards.toMutableList()
    moved.add(to, moved.removeAt(from))
    return copy(cards = moved)
}

private const val MAX_DECK_NAME = 24

/** The editor's: two side by side are exactly one [DeckThumbSize] wide. */
private val ShiftButtonSize = DeckThumbSize / 2
