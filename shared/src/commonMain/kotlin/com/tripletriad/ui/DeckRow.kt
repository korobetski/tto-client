package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.DeckLimits
import com.tripletriad.model.HAND_SIZE

/*
 * One deck, as the list draws it: the grip it is dragged by, what it is called, whether it can be
 * played, what is in it, and the menu holding what else can be done to it.
 *
 * Split from `DecksBody` at the line between "the list" and "a row of it" — the file held both,
 * plus the editor's shared pieces, and had run past the number of functions detekt allows in one.
 * Nothing here knows how many decks there are; nothing in `DecksBody` knows what a row looks like.
 */

fun deckMissingTestTag(index: Int): String = "deck-missing-$index"

fun deckSlotTestTag(index: Int): String = "deck-slot-$index"

fun deckOverLimitTestTag(index: Int): String = "deck-over-limit-$index"

fun deckMoveUpTestTag(index: Int): String = "deck-move-up-$index"

fun deckMoveDownTestTag(index: Int): String = "deck-move-down-$index"

fun deckCopyTestTag(index: Int): String = "deck-copy-$index"

/** The grip a deck is dragged by. */
fun deckDragTestTag(index: Int): String = "deck-drag-$index"

/** The ⋮ the reordering and the duplicate moved into. */
fun deckMenuTestTag(index: Int): String = "deck-menu-$index"

/** The pill that says whether a deck can be played — see [DeckState]. */
fun deckStateTestTag(index: Int): String = "deck-state-$index"

/**
 * What this list says about a deck in one word.
 *
 * The list used to say it in three places or not at all: a red line when a card was missing,
 * another when a cap was broken, and *nothing whatsoever* on the deck that was fine — so the one
 * question the screen exists to answer, "which of these can I take into a match", was answered by
 * the absence of a warning. A pill says it on every row, including the good one.
 *
 * The order below is the order a player has to fix things in, and [of] takes the first that
 * applies: a deck can be over a cap *and* short of a card, and the cap is the one the editor
 * cannot fix by itself.
 */
internal enum class DeckState(val labelKey: String) {
    OUT_OF_LIMITS(StringKeys.DECK_OUT_OF_LIMITS),
    INCOMPLETE(StringKeys.DECK_INCOMPLETE),
    PLAYABLE(StringKeys.DECK_PLAYABLE),
    ;

    internal companion object {
        /**
         * The state of one deck, judged against the same four conditions the deck selector
         * filters on — `PveMatches.playableDecks` admits a deck that is complete, admitted by the
         * format, affordable and legal. [cards] is already the *admitted* catalogue, so a card
         * absent from it is a card this format does not take.
         *
         * Saying anything else here would be worse than saying nothing: a row marked playable
         * that the selector then refuses is a screen calling the game a liar.
         */
        fun of(
            deck: Deck,
            cards: Map<Int, Card>,
            unowned: Set<Int>,
            overLimit: Map<Int, Int>,
        ): DeckState = when {
            overLimit.isNotEmpty() -> OUT_OF_LIMITS
            deck.cards.size < HAND_SIZE -> INCOMPLETE
            unowned.isNotEmpty() -> INCOMPLETE
            deck.cards.any { it !in cards } -> INCOMPLETE
            else -> PLAYABLE
        }
    }
}

/**
 * One deck, as a card: what it is called, whether it can be played, what is in it.
 *
 * ### The grip and the menu, instead of sixteen arrows
 *
 * Every row carried two arrows and a duplicate button, drawn whether or not they could do
 * anything. Reordering is now a *drag* — the thing a list of eight is actually reordered by — and
 * the arrows moved into the ⋮ rather than being deleted: a drag is not available to a screen
 * reader or to a keyboard, and the arrows are how those reach the same result. That is why they
 * keep their tags and their disabled states instead of becoming a gesture-only feature.
 *
 * @param travel how far the finger has carried this row since the last swap, in pixels. Applied
 *   as a layer offset rather than as padding so the drag costs no relayout of the list under it.
 * @param onMeasured the row's height, which is what the list needs to know a swap has been
 *   dragged past. Measured rather than assumed: the row grows a line when a deck is short of a
 *   card, and a hard-coded height would swap early on those.
 */
@Composable
internal fun DeckCard(
    index: Int,
    deck: Deck,
    cards: Map<Int, Card>,
    owned: Map<Int, Int>,
    lifted: Boolean,
    travel: Float,
    onMeasured: (Int) -> Unit,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onGrab: () -> Unit,
    onDrag: (Float) -> Unit,
    onDrop: () -> Unit,
) {
    val unowned = remember(deck, owned) { unownedPositions(deck, owned) }
    val overLimit = remember(deck, cards) { DeckLimits.overLimit(deck.cards, cards) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Above its neighbours only while it is being dragged: a row that is permanently on
            // its own layer draws over the one it is swapping with at the wrong moment.
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer { translationY = travel }
            .onSizeChanged { onMeasured(it.height) }
            .rowSurface(armed = lifted)
            .padding(end = SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckGrip(index = index, onGrab = onGrab, onDrag = onDrag, onDrop = onDrop)

        // The tag stays on what *opens the deck* rather than on the surface around it: every
        // caller taps it to reach the editor, and `SemanticsTest` reads a `Role.Button` off it.
        DeckCardFacts(
            index = index,
            deck = deck,
            cards = cards,
            unowned = unowned,
            overLimit = overLimit,
            modifier = Modifier
                .weight(1f)
                .testTag(deckSlotTestTag(index))
                .ttoClickable(onClick = onClick)
                .padding(vertical = SpaceMd, horizontal = SpaceSm),
        )

        DeckMenu(
            index = index,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onCopy = onCopy,
        )
    }
}

/**
 * The grip a row is dragged by.
 *
 * A drag anywhere on the row would fight the tap that opens the editor and the list's own
 * scrolling; a grip is the one place where a downward gesture can only mean "move this". Its
 * height is a full touch target while its width is not — a `pointerInput` node is not a
 * `clickable`, so nothing grows its bounds for it, and the row is tall enough to give the height
 * away for free where 48 dp of width would cost the deck's name a word.
 */
@Composable
private fun DeckGrip(index: Int, onGrab: () -> Unit, onDrag: (Float) -> Unit, onDrop: () -> Unit) {
    val strings = LocalStrings.current
    val description = strings[StringKeys.DECK_REORDER]

    Box(
        modifier = Modifier
            .size(width = GripWidth, height = MinTouchTarget)
            .testTag(deckDragTestTag(index))
            .semantics { contentDescription = description }
            .pointerInput(index) {
                detectDragGestures(
                    onDragStart = { onGrab() },
                    onDragEnd = onDrop,
                    onDragCancel = onDrop,
                ) { change, delta ->
                    change.consume()
                    onDrag(delta.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TtoIcons.Grip,
            contentDescription = null,
            // The same tone the ⋮ beside it wears: a grip drawn at the disabled alpha reads as a
            // control that cannot be used, which is the one thing it must not say.
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            modifier = Modifier.size(IconSm),
        )
    }
}

/**
 * Move up, move down, duplicate — the three things a row does that are not "open it".
 *
 * A disabled item is left in the menu rather than dropped from it, so the menu is the same three
 * lines on every row and the one that is greyed says *why* nothing happened when it is pressed.
 */
@Composable
private fun DeckMenu(
    index: Int,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onCopy: (() -> Unit)?,
) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }

    Box {
        StripButton(
            icon = TtoIcons.More,
            description = strings[StringKeys.DECK_ACTIONS],
            tag = deckMenuTestTag(index),
            enabled = true,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DeckMenuItem(StringKeys.MOVE_UP, deckMoveUpTestTag(index), onMoveUp) { open = false }
            DeckMenuItem(
                key = StringKeys.MOVE_DOWN,
                tag = deckMoveDownTestTag(index),
                onClick = onMoveDown,
            ) { open = false }
            DeckMenuItem(StringKeys.DECK_COPY, deckCopyTestTag(index), onCopy) { open = false }
        }
    }
}

@Composable
private fun DeckMenuItem(key: String, tag: String, onClick: (() -> Unit)?, close: () -> Unit) {
    val strings = LocalStrings.current

    DropdownMenuItem(
        text = { Text(strings[key]) },
        enabled = onClick != null,
        onClick = {
            close()
            onClick?.invoke()
        },
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun DeckCardFacts(
    index: Int,
    deck: Deck,
    cards: Map<Int, Card>,
    unowned: Set<Int>,
    overLimit: Map<Int, Int>,
    modifier: Modifier,
) {
    val strings = LocalStrings.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SpaceXs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            Text(
                text = deckLabel(strings, deck, index),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            DeckStatePill(
                state = DeckState.of(deck, cards, unowned, overLimit),
                tag = deckStateTestTag(index),
            )
        }
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
        // The pill says *that* the deck cannot be played; these say *why*, which is the half a
        // player can act on. `error` and not the faint tone the line above uses — every other line
        // in this row is a fact about the deck, and this is what stands between it and a match.
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

/**
 * The state in a word, on a ground that carries the same fact for anyone reading the shape rather
 * than the word — the error container is the one thing on this screen that is not the surface.
 */
@Composable
private fun DeckStatePill(state: DeckState, tag: String) {
    val strings = LocalStrings.current
    val bad = state != DeckState.PLAYABLE

    Text(
        text = strings[state.labelKey],
        color = if (bad) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier
            .testTag(tag)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (bad) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            )
            .padding(horizontal = SpaceSm, vertical = PillPadding),
    )
}

/**
 * The grip's width. Narrower than a touch target on purpose — [DeckGrip] says why.
 */
private val GripWidth = 28.dp

/** A pill is a word with air around it, not a box: two pixels is the whole of the air above. */
private val PillPadding = 2.dp
