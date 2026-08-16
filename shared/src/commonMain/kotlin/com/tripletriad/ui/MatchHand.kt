package com.tripletriad.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.ui.theme.LocalTtoColors

/**
 * How a hand reads when a rule has chosen the card for the player — Order and Chaos.
 *
 * ### The rule is invisible otherwise, and Chaos is the sharp case
 *
 * Both narrow `playableCards` to a single card, and until now the only thing on screen that said so
 * was what *failed*: a tap on any other card did nothing, and a drag could not be started. Under
 * Order that is survivable — the card left is always the leftmost, and a player works it out in two
 * turns. Under Chaos it is not: the card is drawn afresh each turn, so what a player learns is that
 * cards sometimes do not respond, which is indistinguishable from a broken screen.
 *
 * So the hand says it in two ways at once, and both are wanted rather than one being belt and
 * braces. Dimming answers "why will this card not move" — the question a player asks while
 * reaching. The ring answers "which one, then" — the question they ask next, and the one that
 * matters under Chaos, where the answer moves. Four dimmed cards alone leave the fifth looking
 * ordinary rather than chosen.
 *
 * ### The two boards share this and always did
 *
 * `MatchScreen` rolls Chaos itself; `PvpMatchScreen` is told the answer, because a client and a
 * server rolling separately would refuse moves for a reason nobody could see
 * (`MatchView.playableHandIndices`). They arrive at the same fact by different routes, and it
 * should not look different — which is exactly the argument `MatchSounds` makes for the sounds.
 */
@Composable
internal fun PlayableRing(scale: Float) {
    Box(
        modifier = Modifier
            .testTag(CHOSEN_CARD_TEST_TAG)
            .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
            .border(
                SelectionRingWidth,
                LocalTtoColors.current.selectionRing.copy(alpha = CHOSEN_CARD_ALPHA),
                TileShape,
            ),
    )
}

/**
 * The ring, for a test.
 *
 * Not indexed by slot: what the tests need to know is **how many** cards are marked and, through
 * the hand's own tags, which — and a count is the assertion that catches the failure worth
 * catching, which is a hand marking everything or nothing.
 */
internal const val CHOSEN_CARD_TEST_TAG: String = "hand-chosen"

/**
 * Whether a rule has left the player fewer cards than they hold.
 *
 * @param held how many cards are in hand.
 * @param playable how many of them may be played this turn.
 * @param isMyTurn whether it is this player's move at all. **Load-bearing**: `playableCards` is
 *   empty on the other side's turn and `MatchView.playableHandIndices` is documented as empty then
 *   too, so without this every hand would read as narrowed to nothing while the opponent thought.
 *
 * `1 until held` rather than `< held`: zero playable cards out of five is not a narrowing, it is a
 * state the rules do not produce, and dimming a whole hand on the strength of it would turn a bug
 * somewhere else into a board that looks deliberately unusable.
 */
internal fun handIsNarrowed(held: Int, playable: Int, isMyTurn: Boolean): Boolean =
    isMyTurn && playable in 1 until held

/**
 * The ring on the card the rules leave.
 *
 * The **selection ring at the weight a free cell wears it** — see `TileCell`, where the same colour
 * at a fraction of its alpha already means "this one could take the card you are holding". One
 * vocabulary: full strength is what you have chosen, faint is what you may choose. A new colour
 * would have been a third thing for a player to learn on the one screen that can least afford it.
 */
private const val CHOSEN_CARD_ALPHA = 0.55f
