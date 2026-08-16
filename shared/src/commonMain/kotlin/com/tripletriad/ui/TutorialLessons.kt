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
    /**
     * The board's elements, by cell — empty unless the lesson is about Elemental.
     *
     * Sparse rather than a nine-slot list because a lesson gives an element to the *one* cell it is
     * talking about. A real Elemental board has one on roughly half of them, which is the right
     * amount of noise for a match and the wrong amount for a sentence naming one tile.
     */
    val elements: Map<Int, CardType> = emptyMap(),
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

    val hand = puzzle.hand.mapNotNull { catalog[it] }
    // Validate all preconditions before returning
    val valid = cells.count { it != null } == puzzle.board.size &&
        hand.size == puzzle.hand.size &&
        puzzle.board.size + hand.size == Board.SIZE &&
        cells[puzzle.cell] == null

    if (!valid) return null

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

/**
 * A whole match played under a rule, for the rules a single placement cannot show.
 *
 * ### Why two lessons needed a shape the other eight did not
 *
 * A [TutorialPuzzle] teaches by making one move be the rule. That works for every rule that decides
 * **a capture** — the position is composed so the capture happens for one reason and no other, and
 * the whole lesson is over in fifteen seconds.
 *
 * Two of the rules left decide something else entirely:
 *
 * - **Bonus and Malus** read a *running tally*. Their modifier is not a property of the position;
 *   it is a count of what has been played, and a count of one is the count a puzzle could show.
 *   The rule only becomes visible over several turns, as the number climbs.
 * - **Order and Chaos** do not touch capture at all. They decide *which card you may pick up*, and
 *   on a board with one card in hand that is not a constraint, it is a description.
 *
 * So these are matches — and the concession is real: a drill can be **lost**, and it takes as long
 * as a game does. It is still the honest shape. A one-move position claiming to teach Bonus would
 * be a position with a tally of one, which is the case where the rule does nothing.
 *
 * The exam is the same shape with [tutoring] off, and expressing it here rather than as its own
 * function is what removed the last thing the course identified by *index*: it used to be "the
 * lesson at [LAST_LESSON] with no puzzle", which would silently have become a broken lesson the
 * moment a row was added after it. Adding these two rows is exactly that moment.
 *
 * @property rules what it is played under — the whole of what makes it a drill rather than an
 *   ordinary match against a tutor who declares nothing.
 * @property deck the five cards the player is dealt. Fixed, because both drills are about the hand:
 *   one deals five cards of one tribe, the other deals five in an order it then tells you to read.
 * @property lines what is said before each placement, by placement index. The player opens while
 *   [tutoring], so the even keys are their own turns.
 * @property outcomes said over the outcome panel, per result. A drill can be **lost** — that is the
 *   price of it being a match — so the three are asked for rather than assumed. A lesson whose
 *   sentence is about the rule and not about the score says the same thing three times, which is
 *   what [whateverHappens] is for; the exam, which is a test, says three different ones.
 * @property tutoring whether this is being **taught** or **examined**, which is one idea and
 *   therefore one flag: taught means the opponent plays to lose (`MatchAiOptions.TUTOR`), the
 *   player opens, the digits that decided a capture are ringed, and the clock is the doubled one
 *   a lesson's own sentences need. Examined means none of those — a real match, a real toss, and
 *   thirty seconds a turn.
 */
internal data class TutorialDrill(
    val rules: GameRules,
    val deck: List<Int>,
    val lines: Map<Int, List<String>> = emptyMap(),
    val outcomes: Map<MatchResult, String>,
    val tutoring: Boolean = true,
)

/** One sentence for all three results: a lesson's closing line is about the rule, not the score. */
internal fun whateverHappens(key: String): Map<MatchResult, String> =
    MatchResult.entries.associateWith { key }

/**
 * One lesson of the course, as the list screen and the player meet it.
 *
 * @property titleKey what it is called.
 * @property ruleKeys the rules it teaches, as AS3 rule constants — which are also i18n keys, so the
 *   list row's subtitle needs no table of its own and reads in all four languages rather than the
 *   two this port authors.
 * @property puzzle the position it teaches from, for a rule one move can show.
 * @property drill the match it teaches from, for a rule one move cannot — see [TutorialDrill].
 *
 * **At most one of the two**, and the opening match has neither: it is the ported `TutorialScreen`,
 * whose nine lines and rigged flip are written into `openingScript` rather than described as data.
 * A row carrying both would be a row where the dispatch in [scriptFor] silently picks one, so it is
 * rejected here instead.
 */
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

/**
 * What the exam is played under — three rules the course has taught, named for its row.
 *
 * **Above the course rather than beside its row**, because the exam's row reads these and
 * top-level properties in one file initialise in the order they are written: declared after
 * [TUTORIAL_COURSE] they are still null when it builds, which is what
 * `Variable 'EXAM_RULE_KEYS' must be initialized` was saying.
 *
 * Same and Plus because they are the two that change how a hand is read, and Reverse because it
 * changes which hand is good. Not Combo: it is not a rule to switch on, and it comes along with
 * Same anyway ([com.tripletriad.model.GameRules.comboEnabled]).
 */
internal val EXAM_RULES: GameRules = GameRules(same = true, plus = true, reverse = true)

/** The same three, as the keys the list row prints. */
internal val EXAM_RULE_KEYS: List<String> = listOf("RULE_SAME", "RULE_PLUS", "RULE_REVERSE")

/**
 * `BLUE_CARDS = [1, 3, 6, 7, 10]` (`TutorialScreen.as:54`) — the hand the lesson is written around.
 *
 * Fixed rather than chosen, and it has to be: line 5 tells the player to pick a card with a bigger
 * number on the touching side, which is only sound advice if the hand is known to contain one.
 *
 * These are card **numbers**, resolved against the set the character plays — so an `ff8_` character
 * is dealt the first, third, sixth, seventh and tenth FF8 cards, exactly as before. That used to
 * happen for free, because an id meant nothing without `MODE` to read it through; ids are global
 * now, so the indirection the lesson depends on has to be spelled out. Left implicit, the tutorial
 * would deal five FFXIV cards to an FFVIII character and then fail to resolve them.
 *
 * The lesson holds either way, because it never names a card.
 *
 * **Here rather than beside the screen that speaks the lines**, because [TUTORIAL_COURSE]'s
 * exam row is dealt the same hand and a course's data has to be initialisable without the
 * screen: as a `private val` in `TutorialScreen.kt` this was read from that file's own
 * initialiser chain before it had been assigned, and `.map` on it threw.
 */
@Suppress("MagicNumber") // Transcribed card numbers: naming each one would say nothing it does not.
private val TUTORIAL_NUMBERS = listOf(1, 3, 6, 7, 10)

/**
 * The five cards the lesson deals the player.
 *
 * Fixed to the first block rather than to the character's collection, which no longer exists. The
 * tutorial deals its own hand — the script fixes the deal — so these are not cards the player owns
 * and never were; what matters is that the nine written lines describe them.
 */
internal fun tutorialDeck(): List<Int> =
    TUTORIAL_NUMBERS.map { Card.idFor(block = TUTORIAL_BLOCK, number = it) }

/** The block the lesson's five cards come from. See [tutorialDeck]. */
private const val TUTORIAL_BLOCK = 1

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

/**
 * The positions alone, in course order.
 *
 * Derived rather than kept beside [TUTORIAL_COURSE], so a lesson cannot be in one list and missing
 * from the other — which is exactly what a second hand-maintained table would eventually do.
 */
internal val TUTORIAL_PUZZLES: List<TutorialPuzzle> = TUTORIAL_COURSE.mapNotNull { it.puzzle }

/** The matches alone, in course order — derived for the same reason [TUTORIAL_PUZZLES] is. */
internal val TUTORIAL_DRILLS: List<TutorialDrill> = TUTORIAL_COURSE.mapNotNull { it.drill }

/** The cell with four neighbours, where a rule has the most room to fire. */
internal const val CENTRE: Int = 4

/** Cell 1 — a top-edge cell, and therefore one that has a wall. See the Same Wall lesson. */
internal const val TOP_CENTRE: Int = 1

/**
 * The two placements a drill speaks on — the player's first move and their third.
 *
 * A tutoring drill forces the player to open ([TutorialDrill.tutoring]), so they hold the even
 * placements and these are turns 1 and 3 of their own five. The third is where the lines can point
 * at something that has *accumulated*: two of the player's cards are down by then, which is the
 * whole of what a running tally needs to have become visible.
 */
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
 * The block-2 cards the Elemental lesson is built from — the FF8 set, which is the only one whose
 * types are elements. See that lesson's comment.
 */
private const val GAYLA = 518
private const val FASTITOCALON_F = 520
private const val COCKATRICE = 523
private const val GLACIAL_EYE = 527
private const val THRUSTAEVIS = 529
private const val ANACONDAUR = 530
private const val CREEPS = 531
private const val GRENDEL = 532
private const val ARMADODO = 536

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
