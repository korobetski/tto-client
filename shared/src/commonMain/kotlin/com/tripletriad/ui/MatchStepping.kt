package com.tripletriad.ui

import com.tripletriad.model.Card
import com.tripletriad.model.MatchView
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
 *
 * ### There used to be an inverse, and deleting it is the point
 *
 * `asDealt` lived here: a deal whose toss gave the opponent the opening came back with the card
 * already on the board, so this file took it *back off* — undid the placement, worked out from the
 * Open rules whether the card had been face up, and put it back in the slot it left — to give the
 * coin flip something left to announce. It was a second implementation of the deal, and it
 * disagreed with the first in exactly the way that predicts: the card went back carrying the
 * catalogue's default owner, so it sat in the opponent's hand in the player's own colour until it
 * was played again.
 *
 * The server no longer sends a position the client has to unwind. `POST /pve/matches` answers with
 * the board as dealt, and the opening is asked for once the announcements are done — see
 * `PveSession.begin`. What is left here steps *forwards* only, which is the only direction a
 * painter should know about.
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
