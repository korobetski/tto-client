package com.tripletriad.ui

import com.tripletriad.data.CardCatalog
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Board
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.TurnOrder

/**
 * The lessons after the first one — a rule each, one move each.
 *
 * ### Why a puzzle rather than a match
 *
 * The lesson that ships today is a whole nine-placement match, and that is the right shape for
 * *the board, the digits and capture*: there is nothing to demonstrate until some cards are down.
 * It is the wrong shape for a named rule. A match under Same takes four minutes, and Same fires
 * only if the player happens to place a card where it can — so the lesson either says nothing or
 * says it about a board the player did not build. A position one move from the rule firing takes
 * fifteen seconds and cannot fail to teach it.
 *
 * ### The rule has to be the only explanation
 *
 * Every position here captures **nothing** with the special rules switched off. That is not a
 * nicety: a placement that would have won on raw power teaches the player that their 2 beat a 4
 * for some reason they have not been told, and the lesson's own sentence is then false. The
 * positions were found by `tools/find_lesson_positions.py`, which searches for exactly that
 * property, and each is pinned by `TutorialPuzzleTest` replaying it through the real engine —
 * because that tool is a *second* implementation of rules that live in `tto-core` and will drift
 * from them.
 *
 * ### Nothing here is a card the player owns
 *
 * As with the first lesson's hand ([tutorialDeck]), these are fixed cards from block 1 rather than
 * anything in the collection: the sentences name the numbers on them.
 */
internal data class TutorialPuzzle(
    /** The rule this lesson exists to teach, in force for it and nothing else. */
    val rules: GameRules,
    /** The eight cards already down, by cell. */
    val board: List<PuzzlePiece>,
    /** What the player holds — one card, the one the lesson is about. */
    val hand: List<Int>,
    /** Said before the move, in order. */
    val lines: List<String>,
    /** Said once it has been made, over the outcome panel. */
    val closing: String,
)

/** One card already on a lesson's board. */
internal data class PuzzlePiece(val position: Int, val cardId: Int, val owner: CardColor)

/**
 * The position, as a [MatchSetup] the ordinary match screen can play.
 *
 * Null when a card id does not resolve in [catalog], which is a data fault rather than a state a
 * player reaches — `TutorialPuzzleTest` resolves every id in the shipped catalogue. The caller
 * skips the lesson rather than crashing, on the same footing as the rest of [App]'s `?.let` chain.
 *
 * ### The two invariants a hand-built position has to keep
 *
 * [MatchState] derives everything from its counters, so a position that disagrees with itself does
 * not fail loudly — it plays wrongly:
 *
 * - **`placement` must equal the number of cards down.** `currentPlayer` is
 *   `order.colorAt(placement)`, so a position that under-counts hands the turn to the wrong side.
 * - **the hands must fill the cells that are left.** Fewer, and the match reaches a placement with
 *   an empty hand and `play` throws; more, and it ends with cards still held.
 *
 * Both are checked here rather than trusted, because the failure is silent at the point where it
 * is introduced and loud somewhere else entirely.
 *
 * The opponent's hand is empty — a one-move puzzle ends on the player's move — so
 * [HandVisibility.HIDDEN] describes it exactly and the Open rules have nothing to reveal.
 */
internal fun puzzleSetup(puzzle: TutorialPuzzle, catalog: CardCatalog): MatchSetup? {
    val pieces = puzzle.board.associateBy { it.position }
    val cells = List(Board.SIZE) { position ->
        pieces[position]?.let { piece ->
            catalog[piece.cardId]?.let { card ->
                PlacedCard(card = card.copy(owner = piece.owner), owner = piece.owner)
            }
        }
    }
    if (cells.count { it != null } != puzzle.board.size) return null

    val hand = puzzle.hand.mapNotNull { catalog[it] }
    if (hand.size != puzzle.hand.size) return null
    if (puzzle.board.size + hand.size != Board.SIZE) return null

    return MatchSetup(
        state = MatchState(
            rules = puzzle.rules,
            board = Board(cells = cells),
            hands = mapOf(
                CardColor.BLUE to hand.map { it.copy(owner = CardColor.BLUE) },
                CardColor.RED to emptyList(),
            ),
            // Blue holds the even placements, and a full board leaves an even one — so the player
            // moves, which is the whole of the lesson.
            order = TurnOrder(CardColor.BLUE),
            placement = puzzle.board.size,
        ),
        opponentVisibility = HandVisibility.HIDDEN,
        // No toss to show: nobody won the right to move first, the position simply starts here.
        coinFlip = null,
        // The rule's own caption still plays, which is worth keeping — it is the one announcement
        // that names what the lesson is about before a word is said.
        intro = MatchPreparation.introSteps(puzzle.rules),
    )
}

/**
 * The three rule lessons, in the order they are taught.
 *
 * Same before Plus because Same is the simpler claim — two numbers are equal, against two sums
 * being equal — and Combo last of the three because it needs one of the others to fire at all:
 * **combo is not a rule**. `GameRules.comboEnabled` is always true and `RULE_COMBO` is a dead
 * constant everywhere but the help screen (see [HelpScreen]), so the combo lesson is played under
 * Same and the third card falls to the chain.
 *
 * Every number below is transcribed from a verified position; see the file header.
 */
// Card ids and cell indices; naming each one would say nothing the comments do not.
@Suppress("MagicNumber")
internal val TUTORIAL_PUZZLES: List<TutorialPuzzle> = listOf(
    // Dodo's right 2 meets Tonberry's left 2, and its bottom 3 meets Bomb's top 3. Neither would
    // fall to raw power — both are ties — so Same is the only thing that captures them.
    TutorialPuzzle(
        rules = GameRules(same = true),
        board = listOf(
            PuzzlePiece(5, TONBERRY, CardColor.RED),
            PuzzlePiece(7, BOMB, CardColor.RED),
            PuzzlePiece(0, MORBOL, CardColor.BLUE),
            PuzzlePiece(1, COEURL, CardColor.BLUE),
            PuzzlePiece(2, AHRIMAN, CardColor.BLUE),
            PuzzlePiece(3, GOOBBUE, CardColor.BLUE),
            PuzzlePiece(6, MANDRAGORA, CardColor.BLUE),
            PuzzlePiece(8, PUDDING, CardColor.BLUE),
        ),
        hand = listOf(DODO),
        lines = listOf(StringKeys.LESSON_SAME_1, StringKeys.LESSON_SAME_2),
        closing = StringKeys.LESSON_SAME_DONE,
    ),
    // Dodo's top 4 and Tonberry's bottom 7 make eleven; its left 4 and Chocobo's right 7 make
    // eleven too. Dodo loses both sides on power — 4 against 7, twice — which is what makes this
    // the position that shows what Plus is *for*.
    TutorialPuzzle(
        rules = GameRules(plus = true),
        board = listOf(
            PuzzlePiece(1, TONBERRY, CardColor.RED),
            PuzzlePiece(3, CHOCOBO, CardColor.RED),
            PuzzlePiece(0, MORBOL, CardColor.BLUE),
            PuzzlePiece(2, AHRIMAN, CardColor.BLUE),
            PuzzlePiece(5, COEURL, CardColor.BLUE),
            PuzzlePiece(6, MANDRAGORA, CardColor.BLUE),
            PuzzlePiece(7, GOOBBUE, CardColor.BLUE),
            PuzzlePiece(8, PUDDING, CardColor.BLUE),
        ),
        hand = listOf(DODO),
        lines = listOf(StringKeys.LESSON_PLUS_1, StringKeys.LESSON_PLUS_2),
        closing = StringKeys.LESSON_PLUS_DONE,
    ),
    // The Same above, with Coblyn in Bomb's place: once Coblyn turns blue its own left 4 beats
    // Sabotender's right 3, and that third card falls to the chain rather than to the placement.
    TutorialPuzzle(
        rules = GameRules(same = true),
        board = listOf(
            PuzzlePiece(5, TONBERRY, CardColor.RED),
            PuzzlePiece(6, SABOTENDER, CardColor.RED),
            PuzzlePiece(7, COBLYN, CardColor.RED),
            PuzzlePiece(0, MORBOL, CardColor.BLUE),
            PuzzlePiece(1, COEURL, CardColor.BLUE),
            PuzzlePiece(2, AHRIMAN, CardColor.BLUE),
            PuzzlePiece(3, GOOBBUE, CardColor.BLUE),
            PuzzlePiece(8, PUDDING, CardColor.BLUE),
        ),
        hand = listOf(DODO),
        lines = listOf(StringKeys.LESSON_COMBO_1, StringKeys.LESSON_COMBO_2),
        closing = StringKeys.LESSON_COMBO_DONE,
    ),
)

/** Where every lesson's card goes — the centre, the one cell with four neighbours. */
internal const val PUZZLE_CELL: Int = 4

/*
 * The block-1 cards the three positions are built from, by id (`cards.json`).
 *
 * Named rather than written as numbers because the *sentences* name their numbers: a lesson that
 * says "your right side is a 2" is describing Dodo, and an id changing under it would leave the
 * line describing a card that is no longer there. `TutorialPuzzleTest` asserts the powers, so a
 * re-import that renumbers the block fails there rather than on a player's screen.
 */
private const val DODO = 257
private const val TONBERRY = 258
private const val SABOTENDER = 259
private const val PUDDING = 261
private const val BOMB = 262
private const val MANDRAGORA = 263
private const val COBLYN = 264
private const val MORBOL = 265
private const val COEURL = 266
private const val AHRIMAN = 267
private const val GOOBBUE = 268
private const val CHOCOBO = 269
