package com.tripletriad.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.tripletriad.model.Card
import com.tripletriad.model.MatchView
import com.tripletriad.model.RulesEngine

/**
 * Whether the board answers "what would this take" before the card is committed.
 *
 * ### What it is, and what it deliberately is not
 *
 * It marks the cells a **single aimed placement** would flip — the cell under a dragged card, or
 * the cell under the mouse. It does not mark every capturing cell on the board at once. The
 * difference is the whole design: the first does arithmetic the player could do on the four digits
 * already printed in front of them, and only for the move they are already making; the second
 * would read the board *for* them and turn nine cells into a lit-up list of correct answers.
 *
 * ### Why a setting, and why it is on
 *
 * On, because the numbers are on screen either way and the rule that combines them is the one thing
 * a new player has to hold in their head while they are also learning Same, Plus and Combo. A
 * setting, because a player who has stopped needing it will find it noisy — and because reading the
 * board unaided is most of the game for the people who came back for that.
 *
 * `static` for the reason [LocalPacing] is: it changes when the settings sheet changes it, and
 * nothing gains from tracking reads of it.
 */
val LocalCaptureHints = staticCompositionLocalOf { false }

/**
 * The positions [card] would flip if it landed on [at], or empty when nothing would.
 *
 * ### Asked of the engine, not re-derived
 *
 * `RulesEngine.resolve` on a board with the card provisionally placed, which is exactly what the
 * referee will do with the real placement. Same, Plus, Same Wall and the whole combo cascade come
 * out of it for free, and — the point — so does every rule this screen has never heard of. A
 * preview that re-implemented the comparison would be a second engine, and the first thing it would
 * get wrong is whichever rule was fixed last.
 *
 * The default [com.tripletriad.model.RulesEngineOptions] are used, which is what the server plays
 * under: nothing constructs an engine directly there, it goes through `MatchState.play`, and
 * `MatchState.options` defaults. A board played under `FAITHFUL` would need this told.
 *
 * ### Null in, empty out
 *
 * A cell that is taken, a card that is not there, a board that is not this player's to move on —
 * every one of them is "nothing to preview" rather than a branch the caller has to remember. The
 * turn check matters most: the opponent's placements arrive while the board is still telling the
 * last exchange, and previewing a move that cannot be made yet would light cells under a hand that
 * is greyed out.
 */
internal fun capturePreview(view: MatchView, card: Card?, at: Int?): Set<Int> {
    if (card == null || at == null) return emptySet()
    if (!view.isMyTurn || !view.board.isEmpty(at)) return emptySet()

    return RulesEngine(view.rules)
        .resolve(view.board, at, card, view.side, view.tally)
        .capturedPositions
        .toSet()
}
