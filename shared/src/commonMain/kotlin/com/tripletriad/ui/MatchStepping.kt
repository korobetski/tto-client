package com.tripletriad.ui

import com.tripletriad.model.AscensionTally
import com.tripletriad.model.Card
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchView
import com.tripletriad.model.OpenRule
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.PlayResult
import com.tripletriad.protocol.Placement

/**
 * One announced placement applied to a view, so an exchange can be **watched** rather than blinked.
 *
 * ### Why this exists at all
 *
 * `POST /pve/matches/{id}/moves` answers with the board after *both* placements — the player's card
 * and the opponent's reply — because asking for them separately would put a round trip in front of
 * every turn. That is the right protocol and the wrong picture: adopting the answer directly makes
 * two cards land on the same frame, and the reply is the half the player most needs to see.
 *
 * So the response is walked. The view already on screen is stepped forward once per entry in
 * `PveMatchView.plays`, with a pause between, and the server's own view is adopted at the end.
 *
 * ### This is painting, not refereeing
 *
 * Nothing here decides anything. [Placement.captures] is the referee's list, computed by the engine
 * on the server and taken as given; this function moves cards into the cells it names. There is no
 * rules evaluation, no `RulesEngine`, and there must not be — the moment this file starts working
 * out *which* cards flip, it is a second implementation of the rules that can disagree with the
 * first, which is the failure the whole refereed design exists to remove.
 *
 * The check that it does not is cheap and worth having: after the last step the stepped board must
 * equal the board the server sent. `PveMatchScreen` adopts the server's view regardless — a
 * disagreement is a rendering bug, not a reason to show the player a board the referee does not
 * have.
 */
internal fun MatchView.after(play: Placement, card: Card): MatchView {
    val played = card.copy(owner = play.player)
    val mine = play.player == side
    val cells = board.cells.toMutableList()
    cells[play.position] = PlacedCard(played, play.player)
    // Flipped in one pass rather than wave by wave: the stagger is a rendering delay that
    // `BoardGrid` derives from `waves`, and the cells themselves change owner at once. Splitting
    // the waves here would make the board disagree with the capture list it was handed.
    for (capture in play.captures) {
        cells[capture.position] = cells[capture.position]?.copy(owner = play.player)
    }

    return copy(
        board = board.copy(cells = cells),
        // The hand closes up over the slot that was played, which is what `handIndex` is on the
        // wire for. A hidden opponent card is `null` and removed exactly the same way: the count
        // is public even when the cards are not.
        ownHand = if (mine) ownHand.withoutSlot(play.handIndex) else ownHand,
        opponentHand = if (mine) opponentHand else opponentHand.withoutSlot(play.handIndex),
        placement = placement + 1,
        tally = tally.including(played, rules),
        lastPlay = PlayResult(
            player = play.player,
            card = played,
            position = play.position,
            captures = play.captures,
            handIndex = play.handIndex,
        ),
        // Read-only until the exchange has finished being told. The player's next turn arrives with
        // the server's view, which is the only thing entitled to say what may be played — see
        // `MatchView.playableHandIndices`, and `OrderRule.CHAOS` for why it cannot be rolled here.
        playableHandIndices = emptyList(),
    )
}

/**
 * [index] removed, or the list unchanged when it names no slot.
 *
 * Unchanged rather than throwing: a response naming a slot this side does not have is a version
 * disagreement, and the frame it would have drawn is about to be replaced by the server's view
 * anyway. Losing an animation is the right cost; crashing a match in progress is not.
 */
private fun <T> List<T>.withoutSlot(index: Int): List<T> =
    if (index !in indices) this else filterIndexed { at, _ -> at != index }

/**
 * The board as the referee **dealt** it, with the opening placement taken back off.
 *
 * ### Why an opening arrives already played
 *
 * `POST /pve/matches` answers with the position after the toss has been honoured, so a deal that
 * gave the opponent the move carries their card in `PveMatchView.plays`. Adopting that answer whole
 * — which is what a fresh board did — put the card down on the first frame: under the rule
 * captions, under the hand turning over for Open, and under the coin flip that was still busy
 * announcing *who held the move*. The animation reported a decision the board had already acted on.
 *
 * So the placement is undone here, the opening plays over the board as it was dealt, and [after]
 * puts the card back when the announcements have finished.
 *
 * ### Null rather than a best effort
 *
 * Every guard asks the same question — *is this an opening this side has not seen yet* — and a no
 * is answered by leaving the caller with the server's view rather than a reconstruction. A resumed
 * match, a board already played on, and a placement that captured (which an empty board cannot
 * produce) are all positions this function has no business inventing.
 */
internal fun MatchView.asDealt(play: Placement, card: Card): MatchView? {
    if (placement != 1 || play.player == side || play.captures.isNotEmpty()) return null
    if (board.cells[play.position] == null) return null

    val cells = board.cells.toMutableList()
    cells[play.position] = null

    return copy(
        board = board.copy(cells = cells),
        opponentHand = opponentHand.withSlot(play.handIndex, card.takeIf { wasFaceUp() }),
        placement = 0,
        // The dealt board is the first placement of the match, so whatever the played card
        // contributed under Ascension or Descension is the whole of the tally.
        tally = AscensionTally.EMPTY,
        lastPlay = null,
        // Read-only for the same reason [after] is: the turn arrives with the server's view, and
        // it is not this side's turn yet in any case — `order.colorAt(0)` is the toss's winner.
        playableHandIndices = emptyList(),
    )
}

/**
 * Whether the card the opponent has just played was face up in their hand before they played it.
 *
 * Counted rather than remembered, because a [MatchView] carries no `HandVisibility` — only the
 * nulls it produced. The rules say how many of the five are showing; the hand that came back is one
 * short of that number **exactly when** the card that left was one of the ones showing.
 *
 * Under Swap and no Open the one known slot is the swapped card, and the same arithmetic answers
 * it: a hand still holding it shows one, a hand that has played it shows none.
 */
private fun MatchView.wasFaceUp(): Boolean =
    opponentHand.count { it != null } < openSlots(opponentHand.size + 1)

/** How many of an opponent hand of [size] the rules in force put face up. */
private fun MatchView.openSlots(size: Int): Int = when (rules.open) {
    OpenRule.NONE -> if (rules.swap) 1 else 0
    OpenRule.THREE_OPEN -> HandVisibility.THREE_OPEN_COUNT
    OpenRule.ALL_OPEN -> size
}

/**
 * [value] inserted at [index], or the list unchanged when it names no slot.
 *
 * The inverse of [withoutSlot] and forgiving in the same way and for the same reason: a response
 * naming a slot this side cannot place is a version disagreement, and the frame it would have drawn
 * is about to be replaced by the server's view anyway.
 */
private fun <T> List<T>.withSlot(index: Int, value: T): List<T> =
    if (index !in 0..size) this else toMutableList().apply { add(index, value) }
