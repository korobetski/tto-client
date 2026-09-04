package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
fun deckMissingTestTag(index: Int): String = "deck-missing-$index"

fun deckSlotTestTag(index: Int): String = "deck-slot-$index"

fun deckOverLimitTestTag(index: Int): String = "deck-over-limit-$index"

fun deckMoveUpTestTag(index: Int): String = "deck-move-up-$index"

fun deckMoveDownTestTag(index: Int): String = "deck-move-down-$index"

fun deckCopyTestTag(index: Int): String = "deck-copy-$index"

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
        DeckSlots(
            profile = profile,
            cards = cards,
            onEdit = { onEdit(it) },
            onPersist = onPersist,
        )
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
private fun DeckSlots(
    profile: GameSave,
    cards: Map<Int, Card>,
    onEdit: (Int) -> Unit,
    onPersist: suspend (GameSave) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // The slot a duplicate would land in, or null when all eight are spoken for. Computed once for
    // the whole list rather than per row: every row's copy button is about the same free slot, and
    // eight rows each scanning the list would be eight answers to one question.
    val free = remember(profile.decks) { firstEmptySlot(profile) }

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
                // A swap writes at once, with no Save to press: the list has no draft to hold it
                // in, and a reordering the player has to confirm is one they can lose by walking
                // away from the screen. The editor is the place with a draft; this is not it.
                onMove = { to -> scope.launch { onPersist(profile.withDecksSwapped(index, to)) } },
                // Null on an empty slot and on every row once the eight are full, which is what
                // disables the button rather than hiding it — see [StripButton].
                onCopy = (free to deck.cards.isNotEmpty()).let { (slot, filled) ->
                    if (slot == null || !filled) {
                        null
                    } else {
                        { scope.launch { onPersist(profile.withDeck(slot, deck)) } }
                    }
                },
            )
        }
    }
}

/**
 * The first of the eight slots holding no cards, or null when none is.
 *
 * A slot the profile has never written is empty too — `decks` is as short as it has ever needed to
 * be, and the list on screen is always [GameSave.MAX_DECKS] long. So this counts past the end of
 * the stored list rather than over it, the way [GameSave.withDecksSwapped] pads past it.
 *
 * A *named* deck with no cards counts as empty: a name is not something a player would mind losing
 * on a slot they never filled, and treating it as occupied would wedge the copy button on a profile
 * that had renamed all eight.
 */
internal fun firstEmptySlot(profile: GameSave): Int? =
    (0 until GameSave.MAX_DECKS).firstOrNull { profile.decks.getOrNull(it)?.cards.isNullOrEmpty() }

@Composable
@Suppress("LongParameterList")
private fun DeckSlotRow(
    index: Int,
    deck: Deck,
    cards: Map<Int, Card>,
    unowned: Set<Int>,
    overLimit: Map<Int, Int>,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onCopy: (() -> Unit)?,
) {
    val strings = LocalStrings.current

    // The surface and the two arrows are siblings, and only what is left of them opens the
    // editor. Nesting the arrows inside the row's own `ttoClickable` would work — a merging node
    // stays addressable inside another one — but it would put two meanings on one press area, and
    // a mis-aimed tap on ↑ would open the slot instead of moving it.
    Row(
        modifier = Modifier.fillMaxWidth().rowSurface().padding(end = SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tag stays on what *opens the deck* rather than on the surface around it: every
        // caller taps it to reach the editor, and `SemanticsTest` reads a `Role.Button` off it.
        // The surface is now only a container — the arrows are its other child.
        DeckSlotFacts(
            index = index,
            deck = deck,
            cards = cards,
            unowned = unowned,
            overLimit = overLimit,
            modifier = Modifier
                .weight(1f)
                .testTag(deckSlotTestTag(index))
                .ttoClickable(onClick = onClick)
                .padding(SpaceMd),
        )

        // Its own column beside the arrows rather than a third button under them. The strip's
        // height is what sets this row's, and a third 28 dp button would make every one of the
        // eight rows taller for a control most of them are not about.
        StripButton(
            icon = TtoIcons.Copy,
            description = strings[StringKeys.DECK_COPY],
            tag = deckCopyTestTag(index),
            enabled = onCopy != null,
            onClick = { onCopy?.invoke() },
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StripButton(
                icon = TtoIcons.Collapse,
                description = strings[StringKeys.MOVE_UP],
                tag = deckMoveUpTestTag(index),
                enabled = index > 0,
                onClick = { onMove(index - 1) },
            )
            StripButton(
                icon = TtoIcons.Expand,
                description = strings[StringKeys.MOVE_DOWN],
                tag = deckMoveDownTestTag(index),
                enabled = index < GameSave.MAX_DECKS - 1,
                onClick = { onMove(index + 1) },
            )
        }
    }
}

@Composable
private fun DeckSlotFacts(
    index: Int,
    deck: Deck,
    cards: Map<Int, Card>,
    unowned: Set<Int>,
    overLimit: Map<Int, Int>,
    modifier: Modifier,
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier,
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

/**
 * One small control in a strip beside something — a reordering arrow, or the duplicate beside a
 * deck slot.
 *
 * Not `IconButton`: Material's is a fixed 48 dp, and ten of those under a row of five 40 dp
 * thumbnails is a control strip twice as wide as the thing it reorders. The size is a parameter
 * because the callers are not the same shape — a list row has the height to spare, a deck position
 * has [DeckThumbSize] and no more.
 *
 * A disabled control is drawn rather than hidden, so the strip under position 0 is the same width
 * as the one under position 3 and the thumbnails above them do not shuffle sideways as cards move.
 * The same reason keeps the duplicate drawn on a slot that has nowhere to copy to.
 */
@Composable
internal fun StripButton(
    icon: ImageVector,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = StripButtonSize,
) {
    Box(
        modifier = Modifier
            .size(size)
            .testTag(tag)
            .ttoClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface
                .copy(alpha = if (enabled) FAINT else DISABLED),
            modifier = Modifier.size(IconSm),
        )
    }
}

/**
 * This profile with deck slots [a] and [b] exchanged.
 *
 * Built out of two [GameSave.withDeck] calls rather than a list rewrite, so a swap that reaches
 * past the end of `decks` pads with empty slots exactly as saving into one does — the list on
 * screen is [GameSave.MAX_DECKS] long whatever the file holds, and moving the eighth row up must
 * mean the same thing whether the seven above it exist yet or not.
 *
 * **No slot index is stored anywhere**, so this is safe to do behind the player's back: a match
 * resolves its deck when the selector is opened, not from a remembered number.
 */
internal fun GameSave.withDecksSwapped(a: Int, b: Int): GameSave {
    if (a == b) return this
    val empty = Deck(name = "", cards = emptyList())
    val first = decks.getOrNull(a) ?: empty
    val second = decks.getOrNull(b) ?: empty
    return withDeck(a, second).withDeck(b, first)
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

internal val DeckThumbSize = 40.dp

/** The list's arrows: two of them stack inside a slot row without setting its height. */
private val StripButtonSize = 28.dp

/** The tone a card the deck cannot spend is drawn in. Shared with the editor. */
internal const val SPENT_ALPHA = 0.3f
