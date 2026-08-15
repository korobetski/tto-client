package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.CardValue
import com.tripletriad.data.Format
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.model.GameSave
import com.tripletriad.model.powerLabel
import kotlinx.coroutines.launch

const val CARD_GRID_TEST_TAG: String = "card-grid"
const val CARD_TOTAL_TEST_TAG: String = "card-total"
const val CARD_DETAIL_TEST_TAG: String = "card-detail"
const val CARD_DETAIL_EMPTY_TEST_TAG: String = "card-detail-empty"

/** The two filter rows, and the Sell button on a card the profile can spare. */
const val CARD_FILTERS_TEST_TAG: String = "card-filters"
const val CARD_SELL_TEST_TAG: String = "card-sell"

/** `card-filter-set-<block>`, and `card-filter-set-all`. */
fun setFilterTestTag(block: Int?): String = "card-filter-set-${block ?: "all"}"

/** `card-filter-type-FIRE`, and `card-filter-type-all`. */
fun typeFilterTestTag(type: CardType?): String = "card-filter-type-${type?.name ?: "all"}"

/** `card-cell-<id>`. Ids are per-collection, and only one collection is ever on screen. */
fun cardCellTestTag(cardId: Int): String = "card-cell-$cardId"

/** `card-copies-<id>` — the copy badge, present only above one copy. */
fun cardCopiesTestTag(cardId: Int): String = "card-copies-$cardId"

/**
 * The whole collection, owned and not — the original's `cardListScreen`.
 *
 * Every card in the profile's table is drawn; the ones it does not own are dimmed. That is the
 * original's arrangement (`:101-106` walks the whole card table and sets `enabled` from membership
 * of `CARDS`), and it is the point of the screen: a collection browser that showed only what you
 * have would not tell you what there is to get.
 *
 * ### One visual departure
 *
 * The grid draws the original's thumbnails, sliced out of the three `card_thumbs` atlases — see
 * [UiArt] for why those stayed packed when the card faces were unpacked. What differs is how a card
 * you do not own is marked: **dimmed, not desaturated.**
 *
 * `CardThumb.enabled = false` applies a Starling `ColorMatrixFilter` at −1 saturation, with a `TODO
 * : make a grey card thumbs atlas` next to it. Compose Multiplatform has no portable colour-matrix
 * filter — `RenderEffect` is platform-specific — so alpha carries the same one bit of information.
 *
 * @param catalog both card tables. Only the profile's own is read: card ids index whichever table
 * `MODE` names, so showing the other collection's card for an id would be showing a different card.
 * @param note where a refused sale is reported. Owned by [CollectionScreen] because the snackbar
 * belongs to the scaffold, and this is one tab inside it.
 */
@Composable
internal fun ColumnScope.CardListBody(
    profile: GameSave,
    catalog: CardCatalog,
    format: Format,
    onIntent: suspend (Intent) -> IntentOutcome,
    note: NoteHost,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val admitted = remember(catalog, format) { catalog.admittedBy(format) }
    val owned = profile.cards
    var selected by remember(format) { mutableStateOf<Card?>(null) }
    var set by remember(format) { mutableStateOf<Int?>(null) }
    var type by remember(format) { mutableStateOf<CardType?>(null) }

    val cards = remember(admitted, set, type) {
        admitted.filter { (set == null || it.block == set) && (type == null || it.type == type) }
    }

    Text(
        // Counted over what is **on screen**, so the line answers the question the grid is
        // currently asking. Filtered to fire, "Owned · 3 / 21" is a fact about fire cards; the
        // unfiltered total is the same sentence with no filter applied.
        text = "${strings[StringKeys.OWNED]}$DOT_SEPARATOR" +
            "${cards.count { owned.containsKey(it.id) }} / ${cards.size}",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(CARD_TOTAL_TEST_TAG).padding(bottom = SpaceSm),
    )

    CardFilters(
        sets = remember(admitted) { admitted.map { it.block }.distinct().sorted() },
        types = remember(
            admitted,
        ) { CardType.entries.filter { t -> admitted.any { it.type == t } } },
        set = set,
        type = type,
        onSet = { set = it },
        onType = { type = it },
    )

    // Selling takes the copy out of the collection and pays for it. Asked rather than computed:
    // a card's worth is its **rarity**, and on an account it is the server's card table that says
    // so — a client that worked the price out itself could work out a better one.
    //
    // One at a time, and answered. Both for the reasons the bag's buttons are — see the `busy` flag
    // in [InventoryBody]: two taps were two sales of two copies, and `perform` answered `Unit`, so
    // a sale the server declined took the tap and said nothing at all.
    var selling by remember(format) { mutableStateOf(false) }
    val sell: (Card) -> Unit = { card ->
        if (!selling) {
            selling = true
            scope.launch {
                // The flag is dropped before the note is shown, not after: `NoteHost.show` suspends
                // for as long as the line is on screen, and a button held disabled for those four
                // seconds would look like the refusal had also broken it.
                val outcome = try {
                    onIntent(Intent.SellCard(card.id))
                } finally {
                    selling = false
                }
                sellCardNote(strings, outcome)?.let { note.show(it) }
            }
        }
    }

    val grid: @Composable (Modifier) -> Unit = { modifier ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ThumbWidth + 4.dp),
            modifier = modifier.testTag(CARD_GRID_TEST_TAG),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                CardCell(
                    card = card,
                    copies = owned[card.id] ?: 0,
                    isSelected = selected?.id == card.id,
                    onClick = { selected = if (selected?.id == card.id) null else card },
                )
            }
        }
    }

    if (LocalWideLayout.current) {
        // The grid and the card side by side — `card_list.jpg`'s own arrangement, which the
        // original could take for granted on a 1024-wide stage. The detail is fixed-width and the
        // grid takes the rest, so widening the window adds columns rather than stretching a card.
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            grid(Modifier.weight(1f).fillMaxHeight())
            CardDetail(selected, profile, sell, Modifier.width(DetailPaneWidth).fillMaxHeight())
        }
    } else {
        // Above the grid rather than beside it, and a fixed height whether or not anything is
        // selected, so the grid does not jump under the finger that just tapped it.
        CardDetail(selected, profile, sell)
        grid(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp))
    }
}

/**
 * What a card sale did, in one line — the bag's `sellNote`, one screen along.
 *
 * ### The refusal this screen can actually provoke
 *
 * [SellButton] already hides itself for a card no deck can spare, so the obvious no is unreachable
 * from here. The one that is not: on an account the collection is the **server's**, and a card the
 * client credited itself for a match against a program is not in it until the transcript has been
 * submitted and replayed. Selling it in that window is refused, the collection is then replaced by
 * the server's own, and the copy appears to have been sold for nothing. See [MatchSettlement] for
 * what closes the window, and this for the window being open.
 *
 * [StringKeys.NOTHING_HAPPENED] and not the bag's `ITEM_REFUSED`: the bag's line says the *bag* no
 * longer holds it, which is a sentence about a place this screen is not.
 */
private fun sellCardNote(strings: Strings, outcome: IntentOutcome): String? = when (outcome) {
    // Silent on purpose, as in the bag: the copy badge and the purse in the app bar have both
    // already said it.
    IntentOutcome.APPLIED -> null
    IntentOutcome.REFUSED -> strings[StringKeys.NOTHING_HAPPENED]
    IntentOutcome.UNREACHABLE -> strings[StringKeys.ACTION_FAILED]
}

@Composable
private fun CardCell(card: Card, copies: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(cardCellTestTag(card.id))
            .rowSurface(selected = isSelected)
            // Tapping a cell opens the card beside the grid, and tapping it again closes it — so
            // it is a toggle, and the selected state is what the detail pane is showing.
            .ttoClickable(selected = isSelected, onClick = onClick)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Every card is tappable, owned or not: the original made unowned thumbs untouchable,
        // which meant the description of a card you were hunting for was the one thing you could
        // not read.
        //
        // The thumbnail and not a shrunk card face. A 104x128 face scaled to a third is a face
        // with unreadable digits on it; the thumbnail is art drawn for this size, and 263 of them
        // are three decoded sheets rather than 263 decoded images — see `UiArt`.
        CardThumb(
            card = card,
            size = ThumbWidth,
            modifier = if (copies > 0) Modifier else Modifier.alpha(UNOWNED_ALPHA),
        )

        // A badge and not a second cell. The grid answers "what is there, and what do I have",
        // and two identical thumbnails answer it worse — the second one reads as a different card
        // until you look twice. Absent at one copy, because "x1" on 200 cells is noise.
        if (copies > 1) {
            Text(
                text = "$COPIES_PREFIX$copies",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .testTag(cardCopiesTestTag(card.id))
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(CopiesBadgeCorner),
                    )
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * The selected card at full size, with what the original's right-hand panel showed.
 *
 * @param modifier its own footprint, which differs by layout: a fixed-height band above the grid on
 * a phone — fixed so the grid does not jump under the finger that just tapped it — and a fixed-
 * width column beside it on a window wide enough for both.
 */
@Composable
private fun CardDetail(
    card: Card?,
    profile: GameSave,
    onSell: (Card) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(DetailHeight),
) {
    val strings = LocalStrings.current

    Box(
        modifier = modifier.rowSurface().padding(8.dp),
        contentAlignment = if (card == null) Alignment.Center else Alignment.TopStart,
    ) {
        if (card == null) {
            EmptyNote(strings[StringKeys.PICK_CARD], CARD_DETAIL_EMPTY_TEST_TAG)
        } else {
            Row(
                modifier = Modifier.testTag(CARD_DETAIL_TEST_TAG).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(SpaceMd),
            ) {
                CardFace(card = card, scale = DETAIL_SCALE)
                // **`fillMaxHeight` is what makes the rest of this work.** Without it the column
                // was as tall as its own contents, so `weight(1f)` on the description had no
                // remainder to take, the column overflowed the panel, and what fell off the bottom
                // was the Sell button — the one control the panel exists to offer. The description
                // was cut mid-sentence with nothing to say it could be scrolled.
                Column(
                    modifier = Modifier.fillMaxHeight().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(SpaceXs),
                ) {
                    Text(
                        text = strings[card.nameKey],
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = cardFacts(strings, card),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The `ff8_` bundle has names for all 110 cards and descriptions for none, so
                    // this really is absent rather than merely untranslated — leaving the key on
                    // screen would read as a defect in the port.
                    val description = "${card.nameKey}_DESC"
                    if (strings.has(description)) {
                        Text(
                            text = strings[description],
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                            // `bodySmall` and not `labelSmall`: this is the only prose in the game
                            // and it was being drawn at the size the app uses for a stack count.
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    SellButton(card, profile, onSell)
                }
            }
        }
    }
}

/**
 * Sell one copy, when there is one to spare.
 *
 * ### Why it is here and not only in the bag
 *
 * The bag sells a `CardItem` — a card that has been *drawn* and not yet used. Once used, a card
 * joins the collection and there was no way out of it: a player opening their tenth Tonberry could
 * use it or leave it in the bag forever, and nothing turned a shelf of duplicates into anything.
 * The collection is where a player looks at what they have too much of, so it is where parting with
 * it belongs.
 *
 * ### It will not sell a card a deck is built on
 *
 * The copies a saved deck names are not offered — see [GameSave.spareCopiesOf]. Selling them would
 * leave a deck `Deck.isAffordable` refuses and a match the server rejects, and the player would
 * meet that at the point of play rather than at the point of the mistake. A card with nothing spare
 * shows no button at all rather than a disabled one: "you cannot sell this" is a question nobody
 * asked, and the answer would be a button on almost every owned card.
 */
@Composable
private fun ColumnScope.SellButton(card: Card, profile: GameSave, onSell: (Card) -> Unit) {
    val strings = LocalStrings.current
    if (profile.spareCopiesOf(card.id) < 1) return

    // Compact, and not the full-width [WideButton] this used to be. A 56 dp bar across a 184 dp
    // panel is the loudest thing on a screen whose subject is the card beside it, and every dp it
    // spans is a dp the description does not get. Selling is an occasional action on a duplicate,
    // not the reason anybody opened the collection.
    FilledTonalButton(
        onClick = { onSell(card) },
        modifier = Modifier.testTag(CARD_SELL_TEST_TAG).align(Alignment.End),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = SpaceLg, vertical = SpaceSm),
    ) {
        Text(
            text = "${strings[StringKeys.SELL]} ${CardValue.resaleOf(
                card.id,
                mapOf(card.id to card),
            )}",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The two filter rows: which set, and which element.
 *
 * ### Why filters at all
 *
 * The grid was 153 cards and is 263 since the sets were merged, on a screen whose only ordering is
 * by id. Finding the fire cards meant scrolling past two hundred that were not, and *counting* them
 * — "how many fire cards do I still need" — was not possible at all.
 *
 * The set filter is what `MODE` used to do by force. It is a **view** now rather than a
 * confinement: a player who wants to look at one set can, and nothing stops them owning or playing
 * the other.
 *
 * ### Types are drawn, not named
 *
 * Twelve of them, eight elements a player recognises by colour, and naming them would mean twelve
 * more keys in four bundles to say what the icon on the card already says. The row shows only the
 * types **present in the table** — the FFVIII set has no Beast or Garlean card, so an FFVIII-only
 * format offers no chip for them rather than a chip that always empties the grid.
 */
@Composable
private fun CardFilters(
    sets: List<Int>,
    types: List<CardType>,
    set: Int?,
    type: CardType?,
    onSet: (Int?) -> Unit,
    onType: (CardType?) -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.testTag(CARD_FILTERS_TEST_TAG).fillMaxWidth().padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A set row only when there is a choice to make. One set admitted is not a filter, it is a
        // row of one chip that does nothing.
        if (sets.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceXs)) {
                TtoFilterChip(
                    label = strings[StringKeys.ALL],
                    tag = setFilterTestTag(null),
                    selected = set == null,
                ) { onSet(null) }
                for (block in sets) {
                    TtoFilterChip(
                        label = "$BLOCK_PREFIX$block",
                        tag = setFilterTestTag(block),
                        selected = set == block,
                        onClick = { onSet(block.takeIf { it != set }) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TtoFilterChip(
                label = strings[StringKeys.ALL],
                tag = typeFilterTestTag(null),
                selected = type == null,
            ) { onType(null) }
            for (candidate in types) {
                TypeChip(candidate, isOn = type == candidate) {
                    onType(candidate.takeIf { it != type })
                }
            }
        }
    }
}

/**
 * The element filter — the same chip carrying an icon instead of a word.
 *
 * ### Why this one is still hand-built when the word chips are [TtoFilterChip]
 *
 * Because a Material chip is a *label* with an optional leading icon, and this has no label at all:
 * see [CardFilters] for why the elements are drawn rather than named. Feeding an empty label to
 * `FilterChip` would give a chip padded for text that is not there, twelve times in a row on the
 * densest strip in the app.
 *
 * What it takes from the shared control instead is everything that is not the shape: [ttoClickable]
 * gives it the 48 dp touch target — these measured about 24 dp, the smallest tap targets in the
 * game — the selected state a screen reader can read, and the focus ring. The element's own name is
 * the description, so the strip reads out as twelve named toggles rather than twelve images.
 */
@Composable
private fun TypeChip(type: CardType, isOn: Boolean, onClick: () -> Unit) {
    val icon = LocalCardArt.current?.typeIcon(type)

    Box(
        modifier = Modifier
            .testTag(typeFilterTestTag(type))
            .rowSurface(selected = isOn)
            .ttoClickable(role = Role.Checkbox, selected = isOn, onClick = onClick)
            .padding(horizontal = SpaceSm, vertical = SpaceXs),
    ) {
        if (icon == null) {
            Text(
                text = type.name.take(1),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Image(
                bitmap = icon,
                contentDescription = type.name,
                modifier = Modifier.size(TypeChipSize).alpha(if (isOn) 1f else MUTED),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/**
 * `Sides A 5 3 2 · Rarity ★★★`.
 *
 * The side order is top, right, bottom, left — `power[0..3]` as `CardDigits.display()` states it
 * and as `DecksScreen.as:296` prints it. Rarity is written as stars rather than drawn from the
 * `{n}stars` texture because the card beside it already carries that row, and because a star count
 * reads the same in all four languages.
 */
private fun cardFacts(strings: Strings, card: Card): String = listOf(
    "${strings[StringKeys.SIDES]} " + listOf(card.top, card.right, card.bottom, card.left)
        .joinToString(" ", transform = ::powerLabel),
    "${strings[StringKeys.RARITY]} ${"★".repeat(card.rarity)}",
).joinToString(DOT_SEPARATOR)

/** Small enough for eight columns on a phone, large enough for the digits to stay legible. */
private const val THUMB_SCALE = 0.46f
private val ThumbWidth = CardSpriteWidth * THUMB_SCALE

/** Two thirds, so the panel fits a card, three lines and a scrolling description on a phone. */
private const val DETAIL_SCALE = 0.66f

/**
 * How tall the detail panel is, and why it is a constant.
 *
 * Fixed rather than fitted, so the grid does not jump under the finger that just tapped it — see
 * `CardListBody`, which selects and deselects on the same tap.
 *
 * It used to be the card art plus padding, about 98 dp, which was the height of the *picture* and
 * had nothing to do with what sits beside it: a name, a facts line, a paragraph of flavour text and
 * a Sell button do not fit in 98 dp and the last two did not appear at all. This is what they
 * actually need — the button at its Material height, three or four lines of description above it,
 * and the rest scrolling.
 */
private val DetailHeight = 196.dp

/** Wide enough for the card and its facts side by side, and no wider — the grid wants the rest. */
private val DetailPaneWidth = 260.dp

/** `adjustSaturation(-1)` in the original; alpha here. See [CardListBody]. */
private const val UNOWNED_ALPHA = 0.28f

/** A set is named by its block until sets carry a translated name — see `CardSet`. */
private const val BLOCK_PREFIX = "Set "
private val TypeChipSize = 16.dp

/** The multiplication sign, not the letter x — it sits beside a numeral. */
private const val COPIES_PREFIX = "\u00d7"
private val CopiesBadgeCorner = 3.dp
