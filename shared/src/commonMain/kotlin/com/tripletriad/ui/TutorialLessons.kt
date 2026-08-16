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
    /** The one cell left free, which every line names. */
    val cell: Int,
    /** Said before the move, in order. */
    val lines: List<String>,
    /** Said once it has been made, over the outcome panel. */
    val closing: String,
    /**
     * The rule sets under which this placement must capture **nothing**.
     *
     * Usually the empty one — raw power has to take nothing, or the rule being taught is not what
     * the player just watched. A lesson about *two* rules cannot ask that: an ace captures plenty
     * on raw power, and the claim there is the interaction, so its baselines are the two rules one
     * at a time. Carried as data because it is the property `TutorialPuzzleTest` checks, and it
     * differs per lesson.
     */
    val baselines: List<GameRules> = listOf(GameRules()),
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
    // The cell the lines name has to be the one that is free. Nothing downstream would notice:
    // the position would simply be a different one from the sentences describing it.
    if (cells[puzzle.cell] != null) return null

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
 * One lesson of the course, as the list screen and the player meet it.
 *
 * @property titleKey what it is called.
 * @property ruleKeys the rules it teaches, as AS3 rule constants — which are also i18n keys, so the
 *   list row's subtitle needs no table of its own and reads in all four languages rather than the
 *   two this port authors.
 * @property puzzle the position, or **null for the opening match**, which is the ported
 *   `TutorialScreen` and is a whole nine-placement game rather than one move.
 */
internal data class TutorialLesson(
    val titleKey: String,
    val ruleKeys: List<String>,
    val puzzle: TutorialPuzzle?,
)

/**
 * The course, in the order it is taught.
 *
 * Ordered so no lesson uses anything an earlier one has not shown. Same before Plus because Same is
 * the simpler claim — two numbers equal, against two sums equal. Combo after both, because
 * **combo is not a rule**: `GameRules.comboEnabled` is always true and `RULE_COMBO` is a dead
 * constant everywhere but the help screen (see [HelpScreen]), so its lesson is played under Same
 * and the third card falls to the chain. Same Wall after Same, for the reason it is named after
 * it.
 * Reverse and Fallen Ace last, and then together, because the pair only means anything once each
 * has been met alone.
 *
 * Every number below is transcribed from a position `tools/find_lesson_positions.py` found and
 * `TutorialPuzzleTest` replays through the real engine. The comments state the arithmetic each
 * lesson's own sentences state, which is the thing a reader can check against the card values.
 */
// Card ids and cell indices; naming each one would say nothing the comments do not.
@Suppress("MagicNumber")
internal val TUTORIAL_COURSE: List<TutorialLesson> = listOf(
    // The ported `TutorialScreen`: nine lines over a whole match, under All Open.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_BASICS,
        ruleKeys = listOf("RULE_ALL_OPEN"),
        puzzle = null,
    ),
    // Dodo's right 2 meets Tonberry's left 2, and its bottom 3 meets Bomb's top 3. Neither would
    // fall to raw power — both are ties — so Same is the only thing that captures them.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_SAME,
        ruleKeys = listOf("RULE_SAME"),
        puzzle = TutorialPuzzle(
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
            cell = CENTRE,
            lines = listOf(StringKeys.LESSON_SAME_1, StringKeys.LESSON_SAME_2),
            closing = StringKeys.LESSON_SAME_DONE,
        ),
    ),
    // Dodo's top 4 and Tonberry's bottom 7 make eleven; its left 4 and Chocobo's right 7 make
    // eleven too. Dodo loses both sides on power — 4 against 7, twice — which is what makes this
    // the position that shows what Plus is *for*.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_PLUS,
        ruleKeys = listOf("RULE_PLUS"),
        puzzle = TutorialPuzzle(
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
            cell = CENTRE,
            lines = listOf(StringKeys.LESSON_PLUS_1, StringKeys.LESSON_PLUS_2),
            closing = StringKeys.LESSON_PLUS_DONE,
        ),
    ),
    // The Same above with Coblyn in Bomb's place: once Coblyn turns blue its own left 4 beats
    // Sabotender's right 3, and that third card falls to the chain rather than to the placement.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_COMBO,
        ruleKeys = listOf("RULE_COMBO", "RULE_SAME"),
        puzzle = TutorialPuzzle(
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
            cell = CENTRE,
            lines = listOf(StringKeys.LESSON_COMBO_1, StringKeys.LESSON_COMBO_2),
            closing = StringKeys.LESSON_COMBO_DONE,
        ),
    ),
    // **Not taught from the centre**, and it cannot be: Same Wall needs a side facing a wall, and
    // the centre is the one cell that has none. Nanamo's 10 faces the wall above cell 1, and its
    // bottom 4 meets Dodo's top 4 — one match plus a wall counting as an ace is two "sames".
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_SAME_WALL,
        ruleKeys = listOf("RULE_SAME_WALL"),
        puzzle = TutorialPuzzle(
            rules = GameRules(sameWall = true),
            board = listOf(
                PuzzlePiece(4, DODO, CardColor.RED),
                PuzzlePiece(0, MORBOL, CardColor.BLUE),
                PuzzlePiece(2, AHRIMAN, CardColor.BLUE),
                PuzzlePiece(3, GOOBBUE, CardColor.BLUE),
                PuzzlePiece(5, COEURL, CardColor.BLUE),
                PuzzlePiece(6, MANDRAGORA, CardColor.BLUE),
                PuzzlePiece(7, COBLYN, CardColor.BLUE),
                PuzzlePiece(8, PUDDING, CardColor.BLUE),
            ),
            hand = listOf(NANAMO),
            cell = TOP_CENTRE,
            lines = listOf(StringKeys.LESSON_SAME_WALL_1, StringKeys.LESSON_SAME_WALL_2),
            closing = StringKeys.LESSON_SAME_WALL_DONE,
        ),
    ),
    // Dodo's 4 against Tonberry's 7. Under Reverse the lower number is the stronger one, so the
    // side that loses every other match wins this one — and nothing here captures without it.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_REVERSE,
        ruleKeys = listOf("RULE_REVERSE"),
        puzzle = TutorialPuzzle(
            rules = GameRules(reverse = true),
            board = listOf(
                PuzzlePiece(1, TONBERRY, CardColor.RED),
                PuzzlePiece(0, MORBOL, CardColor.BLUE),
                PuzzlePiece(2, AHRIMAN, CardColor.BLUE),
                PuzzlePiece(3, GOOBBUE, CardColor.BLUE),
                PuzzlePiece(5, COEURL, CardColor.BLUE),
                PuzzlePiece(6, MANDRAGORA, CardColor.BLUE),
                PuzzlePiece(7, COBLYN, CardColor.BLUE),
                PuzzlePiece(8, PUDDING, CardColor.BLUE),
            ),
            hand = listOf(DODO),
            cell = CENTRE,
            lines = listOf(StringKeys.LESSON_REVERSE_1, StringKeys.LESSON_REVERSE_2),
            closing = StringKeys.LESSON_REVERSE_DONE,
        ),
    ),
    // A 1 against an A. Fallen Ace drops the 10 to 0 *before* anything else, so Amalj'aa's 1 takes
    // Hildibrand's ace — and the same placement takes nothing at all without the rule.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_FALLEN_ACE,
        ruleKeys = listOf("RULE_FALLEN_ACE"),
        puzzle = TutorialPuzzle(
            rules = GameRules(fallenAce = true),
            board = listOf(
                PuzzlePiece(1, HILDIBRAND, CardColor.RED),
                PuzzlePiece(0, MORBOL, CardColor.BLUE),
                PuzzlePiece(2, AHRIMAN, CardColor.BLUE),
                PuzzlePiece(3, GOOBBUE, CardColor.BLUE),
                PuzzlePiece(5, COEURL, CardColor.BLUE),
                PuzzlePiece(6, MANDRAGORA, CardColor.BLUE),
                PuzzlePiece(7, COBLYN, CardColor.BLUE),
                PuzzlePiece(8, PUDDING, CardColor.BLUE),
            ),
            hand = listOf(AMALJAA),
            cell = CENTRE,
            lines = listOf(StringKeys.LESSON_FALLEN_ACE_1, StringKeys.LESSON_FALLEN_ACE_2),
            closing = StringKeys.LESSON_FALLEN_ACE_DONE,
        ),
    ),
    // The interaction, and the one lesson whose baselines are not "no rules at all": Hildibrand's
    // bottom 10 beats Dodo's top 4 on raw power, so that question says nothing here. Under Reverse
    // alone the ace is worthless — 4 is not greater than 10. Under Fallen Ace alone it is worth 0
    // and 4 is not lower than 0. Under both, 0 is the lowest number on the board and unbeatable.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_REVERSE_FALLEN_ACE,
        ruleKeys = listOf("RULE_REVERSE", "RULE_FALLEN_ACE"),
        puzzle = TutorialPuzzle(
            rules = GameRules(reverse = true, fallenAce = true),
            board = listOf(
                PuzzlePiece(7, DODO, CardColor.RED),
                PuzzlePiece(0, MORBOL, CardColor.BLUE),
                PuzzlePiece(1, COEURL, CardColor.BLUE),
                PuzzlePiece(2, AHRIMAN, CardColor.BLUE),
                PuzzlePiece(3, GOOBBUE, CardColor.BLUE),
                PuzzlePiece(5, SABOTENDER, CardColor.BLUE),
                PuzzlePiece(6, MANDRAGORA, CardColor.BLUE),
                PuzzlePiece(8, PUDDING, CardColor.BLUE),
            ),
            hand = listOf(HILDIBRAND),
            cell = CENTRE,
            lines = listOf(
                StringKeys.LESSON_REVERSE_FALLEN_ACE_1,
                StringKeys.LESSON_REVERSE_FALLEN_ACE_2,
            ),
            closing = StringKeys.LESSON_REVERSE_FALLEN_ACE_DONE,
            baselines = listOf(
                GameRules(reverse = true),
                GameRules(fallenAce = true),
            ),
        ),
    ),
)

/**
 * The positions alone, in course order.
 *
 * Derived rather than kept beside [TUTORIAL_COURSE], so a lesson cannot be in one list and missing
 * from the other — which is exactly what a second hand-maintained table would eventually do.
 */
internal val TUTORIAL_PUZZLES: List<TutorialPuzzle> = TUTORIAL_COURSE.mapNotNull { it.puzzle }

/** The cell with four neighbours, where a rule has the most room to fire. */
internal const val CENTRE: Int = 4

/** Cell 1 — a top-edge cell, and therefore one that has a wall. See the Same Wall lesson. */
internal const val TOP_CENTRE: Int = 1

/*
 * The block-1 cards the positions are built from, by id (`cards.json`).
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
private const val AMALJAA = 270
private const val HILDIBRAND = 318
private const val NANAMO = 319
