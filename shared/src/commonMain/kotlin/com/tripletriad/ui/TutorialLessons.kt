package com.tripletriad.ui

import com.tripletriad.data.CardCatalog
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Board
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.GameRules
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import com.tripletriad.model.OrderRule
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.TurnOrder
import com.tripletriad.model.TypeRule

internal data class TutorialPuzzle(
    val rules: GameRules,
    val board: List<PuzzlePiece>,
    val hand: List<Int>,
    val cell: Int,
    val elements: Map<Int, CardType> = emptyMap(),
    val lines: List<String>,
    val closing: String,
    val baselines: List<GameRules> = listOf(GameRules()),
)

internal data class PuzzlePiece(val position: Int, val cardId: Int, val owner: CardColor)

@Suppress("ReturnCount")
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
            board = Board(
                cells = cells,
                elements = List(Board.SIZE) { puzzle.elements[it] },
            ),
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

internal data class TutorialDrill(
    val rules: GameRules,
    val deck: List<Int>,
    val lines: Map<Int, List<String>> = emptyMap(),
    val outcomes: Map<MatchResult, String>,
    val tutoring: Boolean = true,
)

internal fun whateverHappens(key: String): Map<MatchResult, String> =
    MatchResult.entries.associateWith { key }

internal data class TutorialLesson(
    val titleKey: String,
    val ruleKeys: List<String>,
    val puzzle: TutorialPuzzle? = null,
    val drill: TutorialDrill? = null,
) {
    init {
        require(puzzle == null || drill == null) { "$titleKey is both a position and a match" }
    }
}

internal val EXAM_RULES: GameRules = GameRules(same = true, plus = true, reverse = true)

internal val EXAM_RULE_KEYS: List<String> = listOf("RULE_SAME", "RULE_PLUS", "RULE_REVERSE")

@Suppress("MagicNumber") // Transcribed card numbers: naming each one would say nothing it does not.
private val TUTORIAL_NUMBERS = listOf(1, 3, 6, 7, 10)

internal fun tutorialDeck(): List<Int> =
    TUTORIAL_NUMBERS.map { Card.idFor(block = TUTORIAL_BLOCK, number = it) }

private const val TUTORIAL_BLOCK = 1

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
    // bottom 10 beats Amalj'aa's top 1 on raw power, so that question says nothing here.
    //
    // The pair is what each rule turns over, and turning it twice puts it back. Under Reverse alone
    // the ace beats nothing — no side of a card is higher than ten, so it can never be the lower
    // number. Under Fallen Ace alone the pair runs the other way and the 1 takes the ace, so the
    // ace still does not capture. Under both, the two inversions cancel and the ace takes the 1 —
    // which is the whole of what `RULE_FALLEN_ACE_HELP` has said all along.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_REVERSE_FALLEN_ACE,
        ruleKeys = listOf("RULE_REVERSE", "RULE_FALLEN_ACE"),
        puzzle = TutorialPuzzle(
            rules = GameRules(reverse = true, fallenAce = true),
            board = listOf(
                // A top of 1, which is the only side an ace can reach under both rules.
                PuzzlePiece(7, AMALJAA, CardColor.RED),
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
    // Gayla is a lightning card on a lightning tile, so it fights at +1: its top 2 becomes a 3 and
    // takes Thrustaevis's 2, which it only ties on the printed numbers. Verified three ways — the
    // rule off captures nothing, and the rule *on with no element under the card* captures nothing
    // either, which is what makes this a lesson about the tile rather than about the rule's name.
    //
    // Block 2, and it has to be. The FFXIV tribes — beast, garlean, primals, scions — are not
    // elements and match no tile, so every FFXIV card takes −1 on any elemental cell and a +1
    // cannot be demonstrated with one at all. See `elementalModifier`.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_ELEMENTAL,
        ruleKeys = listOf("RULE_ELEMENTAL"),
        puzzle = TutorialPuzzle(
            rules = GameRules(typeRule = TypeRule.ELEMENTAL),
            board = listOf(
                PuzzlePiece(1, THRUSTAEVIS, CardColor.RED),
                PuzzlePiece(0, FASTITOCALON_F, CardColor.BLUE),
                PuzzlePiece(2, COCKATRICE, CardColor.BLUE),
                PuzzlePiece(3, GLACIAL_EYE, CardColor.BLUE),
                PuzzlePiece(5, ANACONDAUR, CardColor.BLUE),
                PuzzlePiece(6, CREEPS, CardColor.BLUE),
                PuzzlePiece(7, GRENDEL, CardColor.BLUE),
                PuzzlePiece(8, ARMADODO, CardColor.BLUE),
            ),
            hand = listOf(GAYLA),
            cell = CENTRE,
            elements = mapOf(CENTRE to CardType.LIGHTNING),
            lines = listOf(StringKeys.LESSON_ELEMENTAL_1, StringKeys.LESSON_ELEMENTAL_2),
            closing = StringKeys.LESSON_ELEMENTAL_DONE,
        ),
    ),
    // Bonus, and Malus behind it — the first lesson that is a match rather than a position, for the
    // reason on `TutorialDrill`: the modifier is a count of what has been played, so there is
    // nothing to see until several cards are down.
    //
    // **The hand is five cards of one tribe and the tutor's is five of none.** `npcs.json` gives
    // the Triple Triad Master 258, 260, 261, 263 and 269, every one of them untyped, so the tally
    // climbs on the player's side alone and the badge appears on their cards and nowhere else.
    // Ids 270-274 are the five rarity-1 beast cards of block 1 and the only run of five in the
    // block that shares a tribe at that rarity.
    //
    // The row names Malus although the match plays Bonus. They are one mechanic with a sign, and a
    // second match to demonstrate a minus would teach nothing the closing line does not say in a
    // sentence — but a player looking for Malus in the list should find it here, not nowhere.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_BONUS,
        ruleKeys = listOf("RULE_ASCENSION", "RULE_DESCENSION"),
        drill = TutorialDrill(
            rules = GameRules(typeRule = TypeRule.ASCENSION),
            deck = listOf(AMALJAA, IXAL, SYLPH, KOBOLD, SAHUAGIN),
            lines = mapOf(
                FIRST_MOVE to listOf(StringKeys.LESSON_BONUS_1, StringKeys.LESSON_BONUS_2),
                THIRD_MOVE to listOf(StringKeys.LESSON_BONUS_3),
            ),
            outcomes = whateverHappens(StringKeys.LESSON_BONUS_DONE),
        ),
    ),
    // Order, and Chaos behind it on the same argument as Malus above: the two rules that decide
    // which card you may pick up rather than what happens when you put it down.
    //
    // Order is the `order` slot of `GameRules` and the row's `RULE_ORDER` is the key `:core`'s own
    // table maps onto it (`RuleKeys.slots`). Two spellings of one rule, and
    // `TutorialDrillTest.everyDrillPlaysTheRulesItsRowNames` is what holds them together: it reads
    // `activeRuleKeys()` back off the rules each drill plays and checks its row named them. A row
    // that advertised a rule the match did not impose is the defect it exists against.
    //
    // The hand is five ordinary block-1 cards, none of them the tutor's own, dealt weakest first so
    // that the constraint is felt rather than merely stated.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_ORDER,
        ruleKeys = listOf("RULE_ORDER", "RULE_CHAOS"),
        drill = TutorialDrill(
            rules = GameRules(order = OrderRule.ORDER),
            deck = listOf(BOMB, COBLYN, MORBOL, GOOBBUE, AHRIMAN),
            lines = mapOf(
                FIRST_MOVE to listOf(StringKeys.LESSON_ORDER_1, StringKeys.LESSON_ORDER_2),
                THIRD_MOVE to listOf(StringKeys.LESSON_ORDER_3),
            ),
            outcomes = whateverHappens(StringKeys.LESSON_ORDER_DONE),
        ),
    ),
    // The exam: everything the drills hold still, let go of. `tutoring = false` is the whole of
    // it — a real toss, a real opponent, thirty seconds a turn and no rings — and it is the
    // first match in the course that can be **lost**, which is exactly why a course of puzzles
    // needs one.
    //
    // A fixed hand all the same: the exam is about the rules, not about whether the player has
    // built a deck yet, and a course ending in the deck selector would be asking a question it
    // never taught the answer to.
    TutorialLesson(
        titleKey = StringKeys.LESSON_TITLE_EXAM,
        ruleKeys = EXAM_RULE_KEYS,
        drill = TutorialDrill(
            rules = EXAM_RULES,
            deck = tutorialDeck(),
            lines = mapOf(FIRST_MOVE to listOf(StringKeys.LESSON_EXAM_START)),
            outcomes = mapOf(
                MatchResult.WIN to StringKeys.LESSON_EXAM_WIN,
                MatchResult.LOSE to StringKeys.LESSON_EXAM_LOSE,
                MatchResult.DRAW to StringKeys.LESSON_EXAM_DRAW,
            ),
            tutoring = false,
        ),
    ),
)

internal val TUTORIAL_PUZZLES: List<TutorialPuzzle> = TUTORIAL_COURSE.mapNotNull { it.puzzle }

internal val TUTORIAL_DRILLS: List<TutorialDrill> = TUTORIAL_COURSE.mapNotNull { it.drill }

internal const val CENTRE: Int = 4

internal const val TOP_CENTRE: Int = 1

private const val FIRST_MOVE: Int = 0
private const val THIRD_MOVE: Int = 4

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

/*
 * The block-8 cards the Elemental lesson is built from — the FF8 set, which is the only one whose
 * types are elements. See that lesson's comment.
 */
private const val GAYLA = 2054
private const val FASTITOCALON_F = 2056
private const val COCKATRICE = 2059
private const val GLACIAL_EYE = 2063
private const val THRUSTAEVIS = 2065
private const val ANACONDAUR = 2066
private const val CREEPS = 2067
private const val GRENDEL = 2068
private const val ARMADODO = 2072

/*
 * The five beast-tribe cards the Bonus drill is dealt — numbers 14 to 18 of block 1, and the only
 * run of five cards in the block that share a tribe at rarity 1. `Amalj'aa` is also the Fallen Ace
 * position's card, which is why it was already named here.
 */
private const val AMALJAA = 270
private const val IXAL = 271
private const val SYLPH = 272
private const val KOBOLD = 273
private const val SAHUAGIN = 274

private const val HILDIBRAND = 318
private const val NANAMO = 319
