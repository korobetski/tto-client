package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.model.AscensionTally
import com.tripletriad.model.Board
import com.tripletriad.model.CaptureKind
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.GameRules
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.MatchView
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.PlayResult
import com.tripletriad.model.Side
import com.tripletriad.model.TypeRule
import com.tripletriad.model.elementalModifier
import com.tripletriad.model.powerModifier
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The board and the two hands, drawn from **a view rather than a state**.
 *
 * ### Why a [MatchView] and not a `MatchState`
 *
 * It used to take the whole match, and could: a solo match was resolved here, so this process held
 * both hands and hid one of them from itself. `HandVisibility` existed to stop the *screen* drawing
 * a card, never to stop the player knowing it.
 *
 * Every match this draws is refereed now — see `PveMatchScreen` and `PvpMatchScreen` — and the
 * opponent's hidden cards are not on this side of the wire at all. A [MatchView] is exactly what
 * arrives: the cards this side may see, the hidden ones as nulls, and `playableHandIndices` decided
 * by whoever holds the state rather than rolled again here. So the parameters this lost —
 * `visibility` and `playable` — were not simplified away; they moved to the end that is entitled to
 * compute them.
 *
 * The tutorial still runs locally and still calls this, with a view it builds from its own state
 * (`MatchView.of`). That is the one legitimate case of a client deriving its own view, and it is
 * legitimate because a lesson is not credited and settles nothing.
 */
@Composable
@Suppress("LongParameterList")
internal fun PlayArea(
    view: MatchView,
    selected: Card?,
    layout: MatchLayout,
    highlights: Map<Int, Set<Side>>,
    waves: Map<Int, Int>,
    onSelect: (Card) -> Unit,
    onPlace: (Int) -> Unit,
    onDrop: (Card, Int) -> Unit,
) {
    val drag = rememberBoardDragState()
    val hand: @Composable (Boolean) -> Unit = { own ->
        HandArea(
            // Positional both ways: `opponentHand` keeps a null where a card is hidden, and the own
            // hand is mapped into the same shape so one row renders both. The nulls are what make
            // Three Open "five cards, three of them face up" rather than "three cards".
            cards = if (own) view.ownHand else view.opponentHand,
            owner = if (own) view.side else view.opponent,
            own = own,
            active = view.currentPlayer == (if (own) view.side else view.opponent),
            selected = selected,
            layout = layout,
            playableSlots = if (own) view.playableHandIndices else emptyList(),
            drag = drag,
            onSelect = onSelect,
            onDrop = onDrop,
        )
    }
    val board: @Composable () -> Unit = {
        BoardGrid(
            board = view.board,
            // Both travel on the view. `MatchView.tally` has been on the wire since PvP was
            // refereed, precisely so a client renders what the referee computed rather than
            // recounting it — and an Ascension tally recounted from a partial board would be wrong.
            rules = view.rules,
            tally = view.tally,
            scale = layout.boardScale,
            drag = drag,
            // The card looking for a cell, by either gesture — a tapped one and a lifted one both
            // need somewhere to go. Carried rather than reduced to a boolean because under the
            // Elemental rule the cells are not interchangeable: which of them helps depends on
            // *this* card's own element. See [TileCell].
            held = selected ?: drag.card.takeIf { drag.isDragging },
            highlights = highlights,
            waves = waves,
            onPlace = onPlace,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Nothing in a match touches the edge of the window. It used to: in landscape the
            // outermost hand card sat flush against the glass, which on a phone is where the
            // curve of the screen begins and where a palm rests.
            .padding(PlayAreaInset)
            .onGloballyPositioned { drag.origin = it.positionInRoot() },
    ) {
        PlayAreaContents(layout = layout, hand = hand, board = board)
        DragGhost(drag = drag, scale = layout.scale)
    }
}

@Composable
private fun PlayAreaContents(
    layout: MatchLayout,
    hand: @Composable (own: Boolean) -> Unit,
    board: @Composable () -> Unit,
) {
    // `spacedBy` and not `SpaceBetween`: the gap is a design decision now rather than whatever
    // happened to be left over, and centring the group is what keeps the board in the middle of
    // the window on a viewport with room to spare.
    if (layout.landscape) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(
                HandBoardGap,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The opponent first — top in portrait, left in landscape. Named by *side* rather than
            // by colour: in a refereed match the player is not always blue, and the hand nearest
            // the player's thumb has to be their own whichever colour they were dealt.
            hand(false)
            board()
            hand(true)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(HandBoardGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The opponent first — top in portrait, left in landscape. Named by *side* rather than
            // by colour: in a refereed match the player is not always blue, and the hand nearest
            // the player's thumb has to be their own whichever colour they were dealt.
            hand(false)
            board()
            hand(true)
        }
    }
}

@Composable
internal fun DragGhost(drag: BoardDragState, scale: Float) {
    val card = drag.card ?: return
    if (!drag.pointer.isSpecified) return
    val width = CardSpriteWidth * scale
    val height = CardSpriteHeight * scale

    Box(
        modifier = Modifier
            .offset {
                val local = drag.pointer - drag.origin
                IntOffset(
                    (local.x - (width.toPx() / 2)).roundToInt(),
                    (local.y - (height.toPx() / 2)).roundToInt(),
                )
            }
            .graphicsLayer { alpha = DRAG_GHOST_ALPHA },
    ) {
        CardFace(card = card, scale = scale)
    }
}

internal fun captureHighlights(board: Board, play: PlayResult?): Map<Int, Set<Side>> {
    if (play == null) return emptyMap()
    val lit = mutableMapOf<Int, MutableSet<Side>>()

    for (capture in play.captures) {
        val from = attackers(play, capture.wave)
        // The side of the *captured* card that faces its attacker. One code path for the placement
        // and for the chain, because "which neighbour took me" is the same question either way.
        val side = Side.entries.singleOrNull { board.neighbour(capture.position, it) in from }
        val attacker = side?.let { board.neighbour(capture.position, it) }
        if (side == null || attacker == null) continue

        lit.getOrPut(capture.position) { mutableSetOf() } += side
        lit.getOrPut(attacker) { mutableSetOf() } += side.facing()
    }
    return lit
}

private fun attackers(play: PlayResult, wave: Int): Set<Int> = when (wave) {
    FIRST_WAVE -> setOf(play.position)
    // The generation before, and at the first remove only the special captures: a basic one never
    // starts a chain.
    FIRST_WAVE + 1 ->
        play.captures
            .filter { it.wave == FIRST_WAVE && it.kind != CaptureKind.BASIC }
            .mapTo(mutableSetOf()) { it.position }
    else -> play.captures.filter { it.wave == wave - 1 }.mapTo(mutableSetOf()) { it.position }
}

internal fun captureWaves(play: PlayResult?): Map<Int, Int> =
    play?.captures.orEmpty().associate { it.position to it.wave }

internal const val COMBO_WAVE_MS: Long = 450L

internal fun waveDelayMillis(play: PlayResult?): Long =
    (play?.captures.orEmpty().maxOfOrNull { it.wave } ?: FIRST_WAVE) * COMBO_WAVE_MS

private const val FIRST_WAVE = 0

@Composable
@Suppress("LongParameterList")
internal fun BoardGrid(
    board: Board,
    rules: GameRules,
    tally: AscensionTally,
    scale: Float,
    drag: BoardDragState,
    held: Card?,
    highlights: Map<Int, Set<Side>>,
    waves: Map<Int, Int>,
    onPlace: (Int) -> Unit,
) {
    val hovered = drag.hovered()

    Column(
        modifier = Modifier.testTag(BOARD_TEST_TAG).padding(TileGap * scale),
        verticalArrangement = Arrangement.spacedBy(TileGap * scale),
    ) {
        for (row in 0 until BOARD_WIDTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap * scale)) {
                for (column in 0 until BOARD_WIDTH) {
                    val position = row * BOARD_WIDTH + column
                    val free = board.isEmpty(position)

                    DisposableEffect(position) {
                        onDispose { drag.unregisterCell(position) }
                    }

                    TileCell(
                        position = position,
                        placed = board[position],
                        element = board.elements[position],
                        rules = rules,
                        tally = tally,
                        scale = scale,
                        isTarget = hovered == position && free,
                        isOpen = held != null && free,
                        held = held,
                        highlight = highlights[position].orEmpty(),
                        wave = waves[position] ?: 0,
                        modifier = Modifier
                            .testTag(tileTestTag(position))
                            .onGloballyPositioned { coordinates ->
                                // Only a free cell registers, so a drag over a taken one finds
                                // nothing and [BoardDragState.drop] returns null.
                                if (free) {
                                    drag.registerCell(position, coordinates.boundsInRoot())
                                } else {
                                    drag.unregisterCell(position)
                                }
                            }
                            // **The board is the one place `ttoClickable` is wrong**, and the
                            // reason is geometry: it grows every target to 48 dp beyond the
                            // layout, and nine cells tiling with a 4 dp gutter would then overlap
                            // each other's hit areas and steal taps from their neighbours. A cell
                            // is `CardSpriteWidth` — 88 dp at full scale — so it needs no help.
                            // What it did need is the role, which `CardFace` cannot supply: the
                            // card inside is labelled (see `cardLabel`), and the *cell* was an
                            // unlabelled box a screen reader could not tell was pressable.
                            .clickable(role = Role.Button) { onPlace(position) },
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun TileCell(
    position: Int,
    placed: PlacedCard?,
    element: CardType?,
    rules: GameRules,
    tally: AscensionTally,
    scale: Float,
    isTarget: Boolean,
    isOpen: Boolean,
    held: Card?,
    highlight: Set<Side>,
    wave: Int,
    modifier: Modifier,
) {
    val game = LocalTtoColors.current

    Box(
        modifier = modifier
            .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
            .clip(TileShape)
            .background(game.boardTile)
            .border(
                width = if (isTarget) SelectionRingWidth else 1.dp,
                color = when {
                    isTarget -> game.selectionRing
                    isOpen -> game.selectionRing.copy(alpha = OPEN_CELL_ALPHA)
                    else -> game.boardTileOutline
                },
                shape = TileShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (placed == null) {
            element?.let { ElementBadge(position = position, element = it, scale = scale) }
        } else {
            BoardCard(placed, scale, highlight, wave)
        }

        // The card's own modifier once it is down, under whichever rule is up; the held card's
        // prospective one while the cell is free, under Elemental only. Never both — a cell holds
        // a card or it does not.
        val modifierValue = when {
            placed != null -> powerModifier(placed.card, rules, element, tally)
            held != null && element != null && rules.typeRule == TypeRule.ELEMENTAL ->
                elementalModifier(held.type, element)
            else -> 0
        }
        if (modifierValue != 0) {
            PowerModifierBadge(
                position = position,
                value = modifierValue,
                scale = scale,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun ElementBadge(position: Int, element: CardType, scale: Float) {
    val icon = LocalCardArt.current?.typeIcon(element)
    val size = ElementIconSize * scale

    if (icon == null) {
        // No artwork loaded — a preview, or a test with no bundle. The name is a worse answer than
        // the glyph and a much better one than an empty cell.
        Text(
            text = element.name.take(ELEMENT_LABEL_CHARS),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = ELEMENT_ALPHA),
            fontSize = ElementFontSize * scale,
            modifier = Modifier.testTag(tileElementTestTag(position)),
        )
    } else {
        Image(
            bitmap = icon,
            contentDescription = element.name,
            contentScale = ContentScale.Fit,
            alpha = ELEMENT_ALPHA,
            modifier = Modifier.testTag(tileElementTestTag(position)).size(size),
        )
    }
}

@Composable
private fun PowerModifierBadge(position: Int, value: Int, scale: Float, modifier: Modifier) {
    val positive = value > 0

    Text(
        text = if (positive) "+$value" else "−${-value}",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = ModifierFontSize * scale,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .testTag(tileModifierTestTag(position))
            .padding(ModifierInset * scale)
            .clip(ModifierShape)
            .background(
                if (positive) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            .padding(horizontal = ModifierPadding * scale),
    )
}

@Composable
private fun BoardCard(placed: PlacedCard, scale: Float, highlight: Set<Side>, wave: Int) {
    val squashY = remember { Animatable(1f) }
    val stretchX = remember { Animatable(1f) }
    val landing = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(placed.owner) }
    var showBack by remember { mutableStateOf(false) }

    // `afterFly`'s second tween. It runs once, on the composition that first has a card in this
    // cell, which is exactly when a card is played onto it — [TileCell] composes nothing here
    // while the cell is empty, so the state above is created fresh with the card.
    LaunchedEffect(Unit) {
        landing.animateTo(1f, tween(LAND_MS, easing = EaseOut))
    }

    LaunchedEffect(placed.owner) {
        if (shown == placed.owner) return@LaunchedEffect
        // A card that fell to the chain waits for the one that took it — see [captureWaves]. Held
        // *before* the flip and not inside it, so what the player sees is the card sitting there
        // unchanged while the previous generation turns, which is what makes it read as a wave.
        delay(wave * COMBO_WAVE_MS)
        // `horizon = false` is the default and the only value the match screens pass, so the
        // squash is vertical and the widening horizontal.
        coroutineScope {
            launch { stretchX.animateTo(FLIP_STRETCH, tween(FLIP_LEG_MS, easing = EaseIn)) }
            squashY.animateTo(0f, tween(FLIP_LEG_MS, easing = EaseIn))
        }
        shown = placed.owner // yoyo(): switchColor()
        showBack = true // yoyo(): hide()
        squashY.animateTo(FLIP_STRETCH, tween(FLIP_LEG_MS, easing = EaseOut))
        squashY.animateTo(0f, tween(FLIP_LEG_MS, easing = EaseIn)) // unflip()
        showBack = false // yoyo2(): show()
        coroutineScope {
            launch { stretchX.animateTo(1f, tween(FLIP_LEG_MS, easing = EaseOut)) }
            squashY.animateTo(1f, tween(FLIP_LEG_MS, easing = EaseOut))
        }
    }

    CardFace(
        card = placed.card.copy(owner = shown),
        scale = scale,
        highlight = highlight,
        showBack = showBack,
        modifier = Modifier.graphicsLayer {
            // The landing and the flip multiply rather than override: a card captured while it
            // is still settling keeps settling. They cannot both be at rest and disagree, since
            // `landing` only ever runs once and only at the start.
            val arriving = lerp(LAND_SCALE, 1f, landing.value)
            scaleX = stretchX.value * arriving
            scaleY = squashY.value * arriving
            rotationZ = LAND_DEGREES * (1f - landing.value)
            translationX = LAND_OFFSET_X * size.width * (1f - landing.value)
            translationY = LAND_OFFSET_Y * size.height * (1f - landing.value)
            alpha = landing.value
        },
    )
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

@Composable
@Suppress("LongParameterList")
private fun HandArea(
    cards: List<Card?>,
    owner: CardColor,
    own: Boolean,
    active: Boolean,
    selected: Card?,
    layout: MatchLayout,
    playableSlots: List<Int>,
    drag: BoardDragState,
    onSelect: (Card) -> Unit,
    onDrop: (Card, Int) -> Unit,
) {
    val gap = HandGap * layout.scale
    // Only the player's own hand can be narrowed on screen: `playableSlots` is what *they* may
    // play, and it is empty while the opponent is thinking, so asking this of the other hand would
    // report every one of its cards as forbidden on its own turn. See `handIsNarrowed`.
    val narrowed = own &&
        handIsNarrowed(held = cards.size, playable = playableSlots.size, isMyTurn = active)

    Box(
        modifier = Modifier
            .size(layout.handWidth, layout.handHeight)
            .graphicsLayer { alpha = if (active) 1f else INACTIVE_HAND_ALPHA },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until layout.handRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until layout.handColumns) {
                        val slot = row * layout.handColumns + column
                        val card = cards.getOrNull(slot)
                        if (slot !in cards.indices) {
                            // No card *at all* — the hand has been played down past this slot.
                            // Distinct from `cards[slot] == null`, which is a card that is there
                            // and may not be seen. The two used to be the same expression, and
                            // could be: this side held both hands and only declined to draw one.
                            Spacer(
                                Modifier.size(
                                    CardSpriteWidth * layout.scale,
                                    CardSpriteHeight * layout.scale,
                                ),
                            )
                        } else if (card == null) {
                            // Hidden. Tagged exactly like a visible card in the same slot, so a
                            // test — or a screen reader — asks "what is in slot 2" without having
                            // to know first whether an Open rule revealed it.
                            CardBack(
                                color = owner,
                                scale = layout.scale,
                                modifier = Modifier.testTag(handCardTestTag(owner, slot)),
                            )
                        } else {
                            // Keyed by the card, not by the slot. Slots close up when a card is
                            // played, so without this every slot behind the played one is handed
                            // a different card and silently keeps the previous one's composition
                            // state. That is what made cards draw each other's artwork
                            // (`rememberCardFace`, and `CardFaceTest`); nothing else in a slot
                            // holds state today, and this is what stops the next thing that does.
                            //
                            // The copy ordinal is part of the key because a hand can now hold two
                            // of one card: `key` requires its keys to be unique within a group,
                            // and the texture id alone would repeat. Counting the identical cards
                            // *ahead* of this one keeps the key attached to the copy rather than
                            // to the slot, so it still survives the hand closing up.
                            key(card.textureId, cards.take(slot).count { it?.id == card.id }) {
                                HandCard(
                                    card = card,
                                    owner = owner,
                                    slot = slot,
                                    isSelected = active && selected?.id == card.id,
                                    active = active,
                                    // Forbidden by Order or Chaos, and said so rather than merely
                                    // enforced. The tap used to reach `onSelect`, which dropped it
                                    // — see `MatchScreen`, where the guard is — so the card simply
                                    // did not respond. That is the feedback the drag gate below
                                    // already refuses to give, and the two now agree.
                                    allowed = !narrowed || slot in playableSlots,
                                    // Ringed when the rules have left exactly this one. Not when
                                    // the whole hand is playable: five rings state nothing.
                                    chosen = narrowed && slot in playableSlots,
                                    // Only the player's own playable cards are draggable.
                                    // `Card._draggable` is the same gate (`Card.as:137`), and it
                                    // matters more here than it looks: dragging a card that
                                    // `RULE_ORDER` forbids and having the drop silently do nothing
                                    // is worse feedback than not being able to lift it.
                                    drag = drag.takeIf {
                                        own && active && slot in playableSlots
                                    },
                                    onDrop = onDrop,
                                    scale = layout.scale,
                                    onSelect = onSelect,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun HandCard(
    card: Card,
    owner: CardColor,
    slot: Int,
    isSelected: Boolean,
    active: Boolean,
    allowed: Boolean,
    chosen: Boolean,
    scale: Float,
    drag: BoardDragState?,
    onSelect: (Card) -> Unit,
    onDrop: (Card, Int) -> Unit,
) {
    // Always face up: reaching this composable means the card is one this side may see. The
    // player's own hand always is, and the opponent's only where an Open rule put it —
    // `openPhase` assigns `RULE_ALL_OPEN` to `bluePlayer` on both of its branches
    // (`BaseMatchScreen.as:172`, `:176`), so Open is only ever about the opponent. That decision
    // belongs to the referee now; a card it did not send is drawn by [HandArea] as a back, and
    // there is no `HandVisibility` left here to disagree with it.
    //
    // Captured so the pointer can be converted out of this card's own space and into root, which
    // is where the cells registered their bounds. See [BoardDragState].
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The second read needs no `?.`: reaching it means the first comparison was true, and a null
    // `drag` cannot equal a card id — Kotlin 2.4's smart cast now says so.
    val isBeingDragged = drag?.card?.id == card.id && drag.isDragging

    Box(
        modifier = Modifier
            .testTag(handCardTestTag(owner, slot))
            .onGloballyPositioned { coordinates = it }
            .then(
                if (drag == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(card.id, drag) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                coordinates?.let { drag.start(card, it.localToRoot(offset)) }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                coordinates?.let { drag.moveTo(it.localToRoot(change.position)) }
                            },
                            onDragEnd = { drag.drop()?.let { (c, at) -> onDrop(c, at) } },
                            onDragCancel = { drag.cancel() },
                        )
                    }
                },
            )
            // Role and state, no growth — see `BoardGrid` for why the match layer opts out of
            // `ttoClickable`. `selected` is the ring the card wears when it is the one in hand,
            // and without it that ring is visible and unannounced.
            .semantics { selected = isSelected }
            .clickable(enabled = active && allowed, role = Role.Button) { onSelect(card) },
    ) {
        // Dimmed rather than removed while it is in the air: taking it out of the hand would
        // re-lay-out the four cards beside it in the middle of the gesture. The same dimming
        // carries "the rules forbid this one", which is the value the whole hand already wears
        // when it is not its turn — one meaning, "you cannot play this now", at one weight.
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = when {
                    isBeingDragged -> DRAG_SOURCE_ALPHA
                    allowed -> 1f
                    else -> INACTIVE_HAND_ALPHA
                }
            },
        ) {
            CardFace(card = card, scale = scale)
        }
        // Never both: a chosen card that has been picked up is simply the selected one, and two
        // rings on one card at two weights would read as a rendering fault.
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
                    .border(SelectionRingWidth, LocalTtoColors.current.selectionRing, TileShape),
            )
        } else if (chosen) {
            PlayableRing(scale = scale)
        }
    }
}

internal data class MatchLayout(
    val landscape: Boolean,
    val handColumns: Int,
    val handRows: Int,
    val scale: Float,
    val boardScale: Float,
) {
    val handWidth: Dp
        get() = (CardSpriteWidth * handColumns + HandGap * (handColumns + 1)) * scale
    val handHeight: Dp
        get() = (CardSpriteHeight * handRows + HandGap * (handRows + 1)) * scale
}

private const val BOARD_WIDTH = 3

private const val LANDSCAPE_HAND_COLUMNS = 2
private const val MIN_CARD_SCALE = 0.22f

private const val MAX_CARD_SCALE = 1f
private const val ELEMENT_LABEL_CHARS = 3

private const val ELEMENT_ALPHA = 0.55f

internal const val INACTIVE_HAND_ALPHA = 0.45f

private const val DRAG_GHOST_ALPHA = 0.85f

internal const val DRAG_SOURCE_ALPHA = 0.3f

private const val OPEN_CELL_ALPHA = 0.38f

private const val LAND_MS = 400

private const val LAND_DEGREES = -270f

private const val LAND_SCALE = 1.2f

private const val LAND_OFFSET_X = 0.48f
private const val LAND_OFFSET_Y = -0.78f

private const val FLIP_LEG_MS = 100

private const val FLIP_STRETCH = 1.2f

/*
 * `Transitions.EASE_IN` / `EASE_OUT`, per the mapping in
 * [api-mapping.md](../../../../../../../docs/analysis/api-mapping.md). Starling's curves are
 * not identical to Compose's; a visual diff pass is still owed.
 */
private val EaseIn = FastOutLinearInEasing
private val EaseOut = LinearOutSlowInEasing
private val TileGap = 4.dp
internal val TileShape = RoundedCornerShape(6.dp)
internal val SelectionRingWidth = 2.dp
private val ElementFontSize = 9.sp

private val ElementIconSize = 26.dp
private val ModifierFontSize = 10.sp
private val ModifierShape = RoundedCornerShape(3.dp)
private val ModifierInset = 2.dp
private val ModifierPadding = 3.dp

internal val HandGap = 3.dp

internal val HandBoardGap = 16.dp

internal val PlayAreaInset = 8.dp

internal val MatchHeaderTopInset = 12.dp

internal fun matchLayout(width: Dp, height: Dp): MatchLayout {
    val landscape = width >= height
    val columns = if (landscape) LANDSCAPE_HAND_COLUMNS else HAND_SIZE
    val rows = (HAND_SIZE + columns - 1) / columns

    val handWidth = CardSpriteWidth.value * columns + HandGap.value * (columns + 1)
    val handHeight = CardSpriteHeight.value * rows + HandGap.value * (rows + 1)
    val boardWidth = CardSpriteWidth.value * BOARD_WIDTH + TileGap.value * (BOARD_WIDTH + 1)
    val boardHeight = CardSpriteHeight.value * BOARD_WIDTH + TileGap.value * (BOARD_WIDTH + 1)

    // The two breaks between the three groups, on whichever axis they are stacked along. Counted
    // here rather than left to `SpaceBetween`'s leftover, which is how they came out at two dp on
    // a phone in landscape — see [HandBoardGap].
    //
    // **Taken off the space rather than added to the need**, and the difference is not cosmetic:
    // `scale` divides the available size by the needed one, so anything inside `needed` is treated
    // as scaling with the cards. The gap does not — it is a fixed 16 dp — and counting it there
    // overstated the room by the amount the gap was notionally shrunk, which came out as a 6 dp
    // overflow at 640x360. `MatchLayoutTest.theArrangementAlwaysFitsInTheSpaceItWasGiven` is what
    // said so.
    val breaks = HandBoardGap.value * 2
    val roomWidth = if (landscape) width.value - breaks else width.value
    val roomHeight = if (landscape) height.value else height.value - breaks

    val neededWidth = if (landscape) handWidth * 2 + boardWidth else maxOf(handWidth, boardWidth)
    val neededHeight =
        if (landscape) maxOf(handHeight, boardHeight) else handHeight * 2 + boardHeight

    val scale = minOf(roomWidth / neededWidth, roomHeight / neededHeight)
        .coerceIn(MIN_CARD_SCALE, MAX_CARD_SCALE)

    // Whatever the hands did not need, out of the room the breaks left.
    val boardWidthBudget = if (landscape) roomWidth - handWidth * 2 * scale else roomWidth
    val boardHeightBudget =
        if (landscape) roomHeight else roomHeight - handHeight * 2 * scale
    val boardScale = minOf(boardWidthBudget / boardWidth, boardHeightBudget / boardHeight)
        .coerceIn(scale, MAX_CARD_SCALE)

    return MatchLayout(landscape, columns, rows, scale, boardScale)
}
