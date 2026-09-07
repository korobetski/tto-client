package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.launch

const val DECK_LIST_TEST_TAG: String = "deck-list"

/** The one row that stands for every slot still free. */
const val DECK_NEW_TEST_TAG: String = "deck-new"

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

/**
 * The decks a profile has, and one line standing for the slots it has not filled.
 *
 * ### Decks, not slots
 *
 * This drew all eight slots whether or not they held anything: seven empty rows, each with a
 * duplicate button and two arrows that could do nothing, on a profile with one deck. Twenty-four
 * dead controls answering a question — "how many slots are there" — nobody asks, in front of the
 * one they do: *which of these can I play*.
 *
 * So an empty slot is not a row. The slots still exist — [GameSave.MAX_DECKS] of them, addressed
 * by index everywhere — and the last line says how many are left, which is the only fact about an
 * empty slot worth a line.
 *
 * ### Reordering is a swap between neighbours *in this list*
 *
 * Not between neighbouring slots. Slot 2 can be empty while slots 1 and 5 hold decks, and a swap
 * with the empty one is a move that looks like nothing happened. So a row moves to the slot of the
 * row drawn next to it, which is what the player is pointing at.
 */
@Composable
private fun ColumnScope.DeckSlots(
    profile: GameSave,
    cards: Map<Int, Card>,
    onEdit: (Int) -> Unit,
    onPersist: suspend (GameSave) -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    // The slot a duplicate would land in, or null when all eight are spoken for. Computed once for
    // the whole list rather than per row: every row's duplicate is about the same free slot, and
    // eight rows each scanning the list would be eight answers to one question.
    val free = remember(profile.decks) { firstEmptySlot(profile) }
    val filled = remember(profile.decks) {
        profile.decks.withIndex().filter { it.value.cards.isNotEmpty() }.map { it.index }
    }

    // Hoisted out of the rows, because a drag outlives the row it started on: the moment two decks
    // swap, the deck under the finger is drawn one row further down while the pointer stream keeps
    // arriving at the node it went down on. [dragged] follows the *deck*, by slot.
    var dragged by remember { mutableStateOf<Int?>(null) }
    var travel by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0) }
    val gap = with(LocalDensity.current) { SpaceSm.toPx() }

    /**
     * Takes a frame of the drag, and swaps two decks when the finger has carried one a whole row.
     *
     * What is left of the travel is carried across the swap — a whole step subtracted rather than
     * a reset to zero — because otherwise the row would jump back under the finger every time it
     * crossed one.
     */
    fun carry(delta: Float) {
        val moved = travel + delta
        val step = if (rowHeight > 0) rowHeight + gap else 0f
        val here = dragged
        val to = here?.let { draggedOnto(filled, it, moved, step) }
        if (to == null || here == null) {
            travel = moved
            return
        }
        travel = moved - (if (moved > 0) step else -step)
        dragged = to
        scope.launch { onPersist(profile.withDecksSwapped(here, to)) }
    }

    // The list scrolls itself. The scaffold hands its content a column with a bounded height and
    // no scrolling of its own, so eight rows that do not fit are not clipped — Column hands the
    // rows past the fold what space is left, which is none, and the last deck is drawn flattened.
    Column(
        modifier = Modifier
            .testTag(DECK_LIST_TEST_TAG)
            .fillMaxWidth()
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        for ((row, index) in filled.withIndex()) {
            val deck = profile.decks[index]
            val move: (Int) -> Unit = { step ->
                filled.getOrNull(row + step)?.let { to ->
                    scope.launch { onPersist(profile.withDecksSwapped(index, to)) }
                }
            }
            DeckCard(
                index = index,
                deck = deck,
                cards = cards,
                owned = profile.cards,
                lifted = dragged == index,
                travel = if (dragged == index) travel else 0f,
                onMeasured = { rowHeight = it },
                onClick = { onEdit(index) },
                // A swap writes at once, with no Save to press: the list has no draft to hold it
                // in, and a reordering the player has to confirm is one they can lose by walking
                // away from the screen. The editor is the place with a draft; this is not it.
                onMoveUp = if (row > 0) ({ move(-1) }) else null,
                onMoveDown = if (row < filled.lastIndex) ({ move(1) }) else null,
                onCopy = free?.let { slot ->
                    { scope.launch { onPersist(profile.withDeck(slot, deck)) } }
                },
                onGrab = {
                    dragged = index
                    travel = 0f
                },
                onDrag = { delta -> carry(delta) },
                onDrop = {
                    dragged = null
                    travel = 0f
                },
            )
        }

        // The slots that are left, as one line rather than as one row each. It opens the editor on
        // the first of them, which is exactly what tapping an empty slot used to do.
        if (free != null) {
            NewDeckRow(
                slots = (free until GameSave.MAX_DECKS).count {
                    profile.decks.getOrNull(it)?.cards.isNullOrEmpty()
                },
                onClick = { onEdit(free) },
            )
        }

        // A profile can hold no deck at all — a starter profile always has one, but a player who
        // empties theirs would otherwise be looking at a screen with one dashed line on it.
        if (filled.isEmpty()) {
            Text(
                text = strings[StringKeys.DECK_NONE],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = SpaceMd),
            )
        }
    }
}

/** The dashed line that stands for every slot still free. */
@Composable
private fun NewDeckRow(slots: Int, onClick: () -> Unit) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DECK_NEW_TEST_TAG)
            .rowSurface()
            .ttoClickable(onClick = onClick)
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceXs, Alignment.CenterHorizontally),
    ) {
        Text(
            text = "+ ${strings[StringKeys.DECK_NEW]}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Text(
            text = strings.format(StringKeys.DECK_FREE_SLOTS, "$slots"),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
/**
 * The slot a drag of [travel] pixels has carried the deck in slot [from] onto, or null while it is
 * still over its own row.
 *
 * [step] is the row's own *measured* height plus the gap under it, so a deck swaps when it has
 * been dragged over the deck beside it and not a moment before — the row grows a line when a deck
 * is short of a card, and a hard-coded height would swap early on those. A step of zero is a list
 * that has not been measured yet, which is not a list anything can have been dragged across.
 *
 * [filled] is the slots that hold a deck, in the order they are drawn. Neighbours in *that* list
 * rather than neighbouring slots: slot 2 can be empty while 1 and 5 hold decks, and swapping with
 * the empty one is a move that looks like nothing happened.
 */
internal fun draggedOnto(filled: List<Int>, from: Int, travel: Float, step: Float): Int? {
    if (step <= 0f) return null
    val towards = if (travel > 0) 1 else -1
    if (travel * towards < step) return null
    return filled.getOrNull(filled.indexOf(from) + towards)
}

internal fun firstEmptySlot(profile: GameSave): Int? =
    (0 until GameSave.MAX_DECKS).firstOrNull { profile.decks.getOrNull(it)?.cards.isNullOrEmpty() }

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
