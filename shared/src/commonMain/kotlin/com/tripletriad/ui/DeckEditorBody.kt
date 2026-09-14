package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tripletriad.data.CardSet
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

/** Tops the draft up from the collection — see [Deck.completedFrom]. */
const val DECK_FILL_TEST_TAG: String = "deck-fill"

fun deckPositionTestTag(index: Int): String = "deck-position-$index"

fun deckPickTestTag(cardId: Int): String = "deck-pick-$cardId"

fun deckRemainingTestTag(cardId: Int): String = "deck-remaining-$cardId"

/** The handle a position is dragged by — absent where there is no card to move. */
fun deckPositionDragTestTag(position: Int): String = "deck-position-drag-$position"

/**
 * ### Two layouts over one draft
 *
 * Past [DeckEditorPanesMinWidth] the filters come out down the left as the collection's do, the
 * hand grows into room a phone never had, and every fact *about* the draft — its averages, its rule
 * counters, Fill, Reset and Save — moves to a column on the right. Everything stateful is hoisted
 * above that choice, so a window resized across the line keeps the draft, the name and the filters.
 */
@Composable
internal fun ColumnScope.DeckEditor(
    profile: GameSave,
    slot: Int,
    cards: Map<Int, Card>,
    sets: List<CardSet>,
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
    // still free on it, rather than one cell per copy. See [DeckPickGrid].
    val owned = remember(profile.cards, cards) {
        profile.cards.keys.sorted().mapNotNull(cards::get)
    }

    // The collection's filters, over the cards this profile could put in a deck. They narrow what
    // the grid shows and nothing else: Fill still chooses from [owned], because a search left
    // typed in would otherwise make Fill quietly worse for a reason nothing near it states.
    val filters = rememberCardFilters(owned, sets)
    val shown = remember(
        owned,
        filters.pickedSets,
        filters.pickedTypes,
        filters.pickedRarities,
        filters.minimums,
        filters.query,
        filters.sort,
        filters.reversed,
    ) {
        filters.sorted(owned.filter { filters.matches(it) })
    }

    // Recomputed against the **draft**, so removing the greyed card clears the warning as the
    // player watches — which is the whole reason the editor greys them too rather than leaving
    // it to the list. This is the screen where the deck can actually be repaired.
    val unowned = unownedPositions(draft, profile.cards)
    val overLimit = DeckLimits.overLimit(draft.cards, cards)
    val completed = remember(draft, owned, cards, profile.cards) {
        draft.completedFrom(owned, cards, profile::copiesOf)
    }

    val status: @Composable (Boolean) -> Unit = { stacked ->
        DeckStatus(draft = draft, cards = cards, unowned = unowned, overLimit = overLimit)
        DeckPowerRow(
            draft = draft,
            cards = cards,
            stacked = stacked,
            // Affordable as well as complete. `5 / 5` in the affirmative tone on a deck that
            // cannot be dealt is the screen agreeing the deck is finished while every other
            // place refuses it.
            playable = draft.isComplete && unowned.isEmpty() && overLimit.isEmpty(),
            fillable = completed != draft,
            onFill = { draft = completed },
        )
    }
    val actions: @Composable (Boolean) -> Unit = { stacked ->
        DeckActions(
            stacked = stacked,
            onReset = { draft = draft.emptied() },
            onSave = {
                scope.launch {
                    onPersist(profile.withDeck(slot, draft.copy(name = name.trim())))
                    onDone()
                }
            },
        )
    }
    val hand: @Composable (Float) -> Unit = { scale ->
        DeckHand(draft = draft, cards = cards, unowned = unowned, scale = scale) { draft = it }
    }
    val nameField: @Composable () -> Unit = {
        DeckNameField(name) { name = it.take(MAX_DECK_NAME) }
    }
    // Counted over what the profile owns, not the catalogue: those are the only cards this grid
    // could ever show, so "12 / 40" is how much of the player's own collection the filters hide.
    val search: @Composable () -> Unit = {
        CardSearchRow(filters = filters, count = "${shown.size} / ${owned.size}")
    }
    val grid: @Composable (Modifier) -> Unit = { modifier ->
        DeckPickGrid(
            shown = shown,
            draft = draft,
            profile = profile,
            cards = cards,
            modifier = modifier,
        ) { draft = it }
    }

    BoxWithConstraints(
        modifier = Modifier.testTag(DECK_EDITOR_TEST_TAG).fillMaxWidth().weight(1f),
    ) {
        if (LocalWideLayout.current && maxWidth >= DeckEditorPanesMinWidth) {
            val middle = maxWidth - DeckFilterPanelWidth - DeckAnalysisWidth - SpaceMd * 2
            val scale = wideHandScale(width = middle, height = maxHeight)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(SpaceMd),
            ) {
                CardFilterPanel(filters, Modifier.width(DeckFilterPanelWidth).fillMaxHeight())
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    nameField()
                    hand(scale)
                    search()
                    grid(Modifier.fillMaxWidth().weight(1f))
                }
                DeckAnalysisPanel(
                    draft = draft,
                    cards = cards,
                    modifier = Modifier.width(DeckAnalysisWidth).fillMaxHeight(),
                ) {
                    status(true)
                    actions(true)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                nameField()
                hand(HAND_CARD_SCALE)
                status(false)
                actions(false)
                search()
                CardFilterMenus(filters)
                grid(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun DeckNameField(name: String, onName: (String) -> Unit) {
    val strings = LocalStrings.current
    OutlinedTextField(
        value = name,
        onValueChange = onName,
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
}

@Composable
private fun DeckStatus(
    draft: Deck,
    cards: Map<Int, Card>,
    unowned: Set<Int>,
    overLimit: Map<Int, Int>,
) {
    val strings = LocalStrings.current

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
}

/**
 * The power line and the one control that changes it without a trip through the grid.
 *
 * On this row rather than in the button pair below, because three 56 dp buttons across a phone is
 * one ellipsis each in French — "Réinitialiser", "Compléter", "Sauvegarder" — and because this
 * button is *about* the number beside it.
 *
 * @param stacked the button under the line, for the analysis column: beside it in 300 dp, French
 *   breaks the line inside `4 / 5`.
 * @param fillable false once Fill would change nothing — a full deck, or an empty collection. A
 *   control that is lit and inert is worse than one that is plainly not available.
 */
@Composable
private fun DeckPowerRow(
    draft: Deck,
    cards: Map<Int, Card>,
    stacked: Boolean,
    playable: Boolean,
    fillable: Boolean,
    onFill: () -> Unit,
) {
    val strings = LocalStrings.current
    val power: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            text = "${strings[StringKeys.DECK_POWER]} ${deckPower(draft, cards)}" +
                "$DOT_SEPARATOR${draft.cards.size} / $HAND_SIZE",
            color = if (playable) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = modifier.testTag(DECK_POWER_TEST_TAG),
        )
    }
    val fill: @Composable (Modifier) -> Unit = { modifier ->
        FilledTonalButton(
            onClick = onFill,
            enabled = fillable,
            modifier = modifier.testTag(DECK_FILL_TEST_TAG),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = SpaceLg, vertical = SpaceXs),
        ) {
            Text(
                text = strings[StringKeys.DECK_FILL],
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(SpaceSm)) {
            power(Modifier)
            fill(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            power(Modifier.weight(1f))
            fill(Modifier)
        }
    }
}

/**
 * Reset and Save.
 *
 * @param stacked one above the other, for the analysis column: side by side in its 300 dp they are
 *   two ellipses in French.
 */
@Composable
private fun DeckActions(stacked: Boolean, onReset: () -> Unit, onSave: () -> Unit) {
    val strings = LocalStrings.current
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WideButton(strings[StringKeys.SAVE], DECK_SAVE_TEST_TAG, onClick = onSave)
            WideButton(
                strings[StringKeys.RESET_DECK],
                DECK_RESET_TEST_TAG,
                filled = false,
                onClick = onReset,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.RESET_DECK], DECK_RESET_TEST_TAG, onClick = onReset)
            }
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.SAVE], DECK_SAVE_TEST_TAG, onClick = onSave)
            }
        }
    }
}

/** The cards the filters let through, each a toggle into and out of the draft. */
@Composable
private fun DeckPickGrid(
    shown: List<Card>,
    draft: Deck,
    profile: GameSave,
    cards: Map<Int, Card>,
    modifier: Modifier,
    onDraft: (Deck) -> Unit,
) {
    // Said rather than left blank, as in the collection: the only way this empties is a filter
    // the player set, and an empty grid under a search field looks like a screen that failed.
    if (shown.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            EmptyNote(LocalStrings.current[StringKeys.NO_CARD_MATCH], CARD_NO_MATCH_TEST_TAG)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = DeckThumbSize + 4.dp),
        modifier = modifier.testTag(DECK_PICK_GRID_TEST_TAG),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(shown, key = { it.id }) { card ->
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
                // The name is the one thing neither the thumbnail nor the line under it says.
                CardHoverInfo(card = card, copies = null) {
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
                                onDraft(draft.plusCard(card.id))
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

/**
 * The draft's five positions, in the order they will be played.
 *
 * ### Why the order is worth a gesture
 *
 * A card's place in a deck decides exactly one thing — under `RULE_ORDER` it is the sequence the
 * hand is dealt in — and it used to be changed one step at a time through the two arrows below
 * each position. Dragging a card onto its neighbour is the same swap, said the way a hand of five
 * cards is actually rearranged, and it is the gesture the list of decks was already reordered by
 * (see `DeckCard`). The arrows stay for the same reason they stayed there: a drag reaches neither
 * a keyboard nor a screen reader, so deleting them would make reordering a sighted-pointer
 * feature.
 *
 * ### The drag is held here, not in the position
 *
 * The moment two cards swap, the card under the finger is drawn one position further along while
 * the pointer stream keeps arriving at the node the gesture went down on. [dragged] therefore
 * follows the *position the card is now in*, and the travel left over from a swap is carried
 * across it rather than reset, so the card does not jump back under the finger each time it
 * crosses a neighbour.
 */
@Composable
private fun DeckHand(
    draft: Deck,
    cards: Map<Int, Card>,
    unowned: Set<Int>,
    scale: Float,
    onDraft: (Deck) -> Unit,
) {
    val strings = LocalStrings.current
    val gripLabel = strings[StringKeys.DECK_REORDER]

    var dragged by remember { mutableStateOf<Int?>(null) }
    var travel by remember { mutableStateOf(0f) }
    // The column's own measured width, not the thumbnail's: the powers under a card are wider than
    // the 40 dp portrait above them, and stepping by the portrait would swap before the finger had
    // reached the next position.
    var columnWidth by remember { mutableStateOf(0) }
    val gap = with(LocalDensity.current) { DeckHandGap.toPx() }

    fun carry(delta: Float) {
        val here = dragged ?: return
        val moved = travel + delta
        val step = if (columnWidth > 0) columnWidth + gap else 0f
        // The same rule the deck list swaps by, over the filled positions instead of the filled
        // slots — here they are contiguous, so a position's neighbour is the next index.
        val to = draggedOnto(draft.cards.indices.toList(), here, moved, step)
        if (to == null) {
            travel = moved
            return
        }
        travel = moved - (if (moved > 0) step else -step)
        dragged = to
        onDraft(draft.withCardMoved(here, to))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        // Centred, for the wide editor's hand, which stops growing before its column does.
        horizontalArrangement = Arrangement.spacedBy(DeckHandGap, Alignment.CenterHorizontally),
    ) {
        for (position in 0 until HAND_SIZE) {
            val card = draft.cards.getOrNull(position)?.let(cards::get)
            val lifted = dragged == position

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    // Above its neighbours only while it is being dragged: a position permanently
                    // on its own layer draws over the one it is swapping with at the wrong moment.
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationX = if (lifted) travel else 0f }
                    .onSizeChanged { columnWidth = it.width },
            ) {
                // The tag sits on the *clickable* box and not on the frame inside it: a
                // `clickable` merges its descendants' semantics, so a tag one level down is
                // absorbed and unreachable from the merged tree a test drives.
                Box(
                    modifier = Modifier
                        .testTag(deckPositionTestTag(position))
                        .ttoClickable(enabled = card != null) {
                            onDraft(draft.minusCardAt(position))
                        }
                        // A drag reaches a finger and a mouse and nothing else. These are the
                        // same two moves the arrows made, kept where a screen reader and a
                        // keyboard can still find them once the arrows are gone.
                        .semantics {
                            customActions = listOfNotNull(
                                shift(strings[StringKeys.MOVE_LEFT], card, position > 0) {
                                    onDraft(draft.withCardMoved(position, position - 1))
                                },
                                shift(
                                    strings[StringKeys.MOVE_RIGHT],
                                    card,
                                    position < draft.cards.size - 1,
                                ) {
                                    onDraft(draft.withCardMoved(position, position + 1))
                                },
                            )
                        }
                        // The card is draggable as well as its grip: nothing here scrolls
                        // sideways, so a sideways drag on a card can only mean "move this one",
                        // and the tap that takes the card out survives because a drag past the
                        // touch slop consumes the gesture before the tap is recognised.
                        .pointerInput(position, card?.id) {
                            if (card == null) return@pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    dragged = position
                                    travel = 0f
                                },
                                onDragEnd = {
                                    dragged = null
                                    travel = 0f
                                },
                                onDragCancel = {
                                    dragged = null
                                    travel = 0f
                                },
                            ) { change, delta ->
                                change.consume()
                                carry(delta.x)
                            }
                        },
                ) {
                    HandPosition(card = card, owned = position !in unowned, scale = scale)
                }

                // The handle, in the place the two arrows used to hold: a card is easier to
                // drop where it belongs than to walk there one position at a time. What a drag
                // reaches nobody else does, so the two moves it replaces stay on the position
                // itself as accessibility actions.
                Box(
                    modifier = Modifier
                        .height(GripHeight)
                        .testTag(deckPositionDragTestTag(position))
                        .semantics { contentDescription = gripLabel }
                        .pointerInput(position, card?.id) {
                            if (card == null) return@pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    dragged = position
                                    travel = 0f
                                },
                                onDragEnd = {
                                    dragged = null
                                    travel = 0f
                                },
                                onDragCancel = {
                                    dragged = null
                                    travel = 0f
                                },
                            ) { change, delta ->
                                change.consume()
                                carry(delta.x)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (card != null) {
                        Icon(
                            imageVector = TtoIcons.Grip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                            modifier = Modifier.size(IconSm),
                        )
                    }
                }
            }
        }
    }
}

/**
 * This deck topped up from [pool] until it is full, legal and paid for.
 *
 * ### Completes rather than replaces
 *
 * What is already picked stays picked, and only the empty positions are filled. Emptying the slot
 * is what Reset is for, and a "fill" that silently threw away four deliberate choices to reach the
 * fifth would be the one control on this screen a player could not undo.
 *
 * ### "Best" means [Card.total], and that is a claim worth stating
 *
 * [pool] arrives in whatever order the caller sorted it, and the caller sorts by the sum of the
 * four edges. `Card.total`'s own KDoc says why that is deliberately unweighted: which edge matters
 * depends on where a card is played and on whether Reverse or Fallen Ace is up, so any weighting
 * would be a claim about a board that has not been dealt. So this produces *a good legal deck*, not
 * an optimal one, and it is a starting point the player is expected to edit.
 *
 * ### Duplicates count, which is why the inner loop is there
 *
 * [pool] holds one entry per *card*, not per copy — it is the picker grid's own list, which draws a
 * card once and badges how many are spare. So a pass that took each entry at most once would build
 * a two-card deck for a collection of two cards owned four times each, and refuse to fill positions
 * the player can fill by hand. Each card is therefore taken as many times as it is owned and
 * allowed, before the walk moves on to the next.
 *
 * ### Three refusals, in the order they cost least to check
 *
 * A full hand ends it. Copies already spent in this deck are not spent twice — the same rule the
 * picker grid greys a tile for. A rank the deck has no room for is skipped, by [DeckLimits.admits],
 * which is the rule the server would refuse the match over.
 */
internal fun Deck.completedFrom(
    pool: List<Card>,
    table: Map<Int, Card>,
    copiesOwned: (Int) -> Int,
): Deck {
    var picked = cards
    for (card in pool.sortedByDescending { it.total }) {
        if (picked.size >= HAND_SIZE) break
        while (picked.size < HAND_SIZE &&
            picked.count { it == card.id } < copiesOwned(card.id) &&
            DeckLimits.admits(picked, table, card)
        ) {
            picked = picked + card.id
        }
    }
    return if (picked == cards) this else copy(cards = picked)
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

/** One of the two moves the arrows used to make, or nothing where the move is off the end. */
private fun shift(
    label: String,
    card: Card?,
    possible: Boolean,
    move: () -> Unit,
): CustomAccessibilityAction? =
    if (card == null || !possible) {
        null
    } else {
        CustomAccessibilityAction(label) {
            move()
            true
        }
    }

/**
 * A position drawn the way the card will be *on the board*, and not as the 40 dp portrait.
 *
 * The hand is where synergy is judged, and the sprite is the only drawing of a card that carries
 * the four powers and the element in the arrangement they will be read in during the match. Five
 * of them at [HAND_CARD_SCALE] fit across the narrowest phone this screen is laid out for; a wide
 * window draws them at [wideHandScale].
 */
@Composable
private fun HandPosition(card: Card?, owned: Boolean, scale: Float) {
    if (card == null) {
        Box(
            modifier = Modifier
                .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
                .clip(RoundedCornerShape(EmptySlotCorner))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        CardFace(
            card = card,
            scale = scale,
            modifier = if (owned) Modifier else Modifier.alpha(SPENT_ALPHA),
        )
    }
}

private const val MAX_DECK_NAME = 24

/**
 * Five sprites and four gaps across the narrowest phone the app is laid out for.
 *
 * 5 × 104 dp × 0.65 + 4 × [DeckHandGap] = 354 dp, inside the 360 dp screen once its own padding is
 * taken off. Measured on a screenshot rather than trusted to this arithmetic.
 */
private const val HAND_CARD_SCALE = 0.65f

/** As tall as the arrows it replaces, and no taller: the sprite above it is the tall thing. */
private val GripHeight = DeckThumbSize / 2

/** The corner an empty slot is cut with — [EmptyCardSlot]'s, at the size of a sprite. */
private val EmptySlotCorner = 4.dp

/** Between two positions. Read by the drag as well as by the row, so it is named once. */
private val DeckHandGap = 4.dp

/**
 * As large as five sprites across [width] allow, and no taller than [HAND_HEIGHT_SHARE] of
 * [height]: the hand is read for synergy, but the grid under it is what the player is choosing
 * from.
 */
private fun wideHandScale(width: Dp, height: Dp): Float {
    val across = (width - DeckHandGap * (HAND_SIZE - 1)) / (CardSpriteWidth * HAND_SIZE)
    val down = height * HAND_HEIGHT_SHARE / CardSpriteHeight
    return minOf(across, down, WIDE_HAND_MAX_SCALE).coerceAtLeast(HAND_CARD_SCALE)
}

/**
 * The FFXIV faces are 208x256 art since 2026-09-14, so 156x192 dp is still a downscale on a 1x
 * screen. Past it a 1080p window loses a row of the grid to a hand that reads no better.
 */
private const val WIDE_HAND_MAX_SCALE = 1.5f

private const val HAND_HEIGHT_SHARE = 0.3f

/**
 * The editor body's width at which the filters and the analysis come out beside the hand. They take
 * 564 dp between them, which leaves a 1000 dp body a ~410 dp middle — a phone's width, where the
 * hand is at its phone scale. The collection's own line is the same width for the same reason.
 */
private val DeckEditorPanesMinWidth = 1000.dp

/** The collection's filter panel width, so the chips wrap the same way in both rooms. */
private val DeckFilterPanelWidth = 240.dp

private val DeckAnalysisWidth = 300.dp
