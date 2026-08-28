package com.tripletriad.ui

import com.tripletriad.model.Capture
import com.tripletriad.model.CaptureKind
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import com.tripletriad.model.OpenRule
import com.tripletriad.protocol.Placement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Walking a refereed answer onto the board, one placement at a time.
 *
 * **The claim that justifies the file** is [theSteppedBoardEqualsTheOneTheRefereeSent]: one request
 * carries two placements, the screen shows them one after the other, and what it arrives at has to
 * be the position the server actually has. If it were not, this would be a second implementation of
 * the rules quietly disagreeing with the first — which is the failure the whole refereed design was
 * built to remove.
 *
 * Everything else here is about what stepping must *not* do.
 */
class MatchSteppingTest {

    // ---- One placement ----------------------------------------------------

    @Test
    fun theCardLandsOnTheCellTheRefereeNamed() {
        val played = blue.first()
        val stepped = view.after(placement(CardColor.BLUE, played, position = 4), played)

        assertEquals(played.id, stepped.board.cells[4]?.card?.id)
        assertEquals(CardColor.BLUE, stepped.board.cells[4]?.owner)
        assertEquals(1, stepped.placement)
    }

    /**
     * **The captures are taken, not worked out.**
     *
     * The list comes from the referee. A card facing a stronger one is flipped here because the
     * server said so, and one facing a weaker one stays put for exactly the same reason: this
     * function does not know which is which, and must not.
     */
    @Test
    fun theCapturesAreTheOnesTheRefereeListedAndNotOnesWorkedOutHere() {
        val theirs = red.first()
        val opened = view.after(placement(CardColor.RED, theirs, position = 0), theirs)
        val mine = blue.first()

        val stepped = opened.after(
            placement(
                CardColor.BLUE,
                mine,
                position = 1,
                captures = listOf(Capture(0, CaptureKind.BASIC, wave = 0)),
            ),
            mine,
        )

        assertEquals(CardColor.BLUE, stepped.board.cells[0]?.owner, "the named cell flipped")
        assertEquals(theirs.id, stepped.board.cells[0]?.card?.id, "and it is still the same card")
    }

    /** A capture the referee did not list does not happen, however the digits look. */
    @Test
    fun anUnlistedNeighbourIsLeftAlone() {
        val theirs = red.first()
        val opened = view.after(placement(CardColor.RED, theirs, position = 0), theirs)
        val mine = blue.first()

        val stepped = opened.after(placement(CardColor.BLUE, mine, position = 1), mine)

        assertEquals(CardColor.RED, stepped.board.cells[0]?.owner, "nothing said this should flip")
    }

    @Test
    fun theCardIsStampedWithWhoPlayedIt() {
        val theirs = red.first()
        val stepped = view.after(placement(CardColor.RED, theirs, position = 8), theirs)

        assertEquals(CardColor.RED, stepped.board.cells[8]?.card?.owner)
        assertEquals(CardColor.RED, assertNotNull(stepped.lastPlay).card.owner)
    }

    // ---- The hands --------------------------------------------------------

    @Test
    fun myOwnHandClosesUpOverTheSlotThatWasPlayed() {
        val played = blue[2]
        val stepped = view.after(placement(CardColor.BLUE, played, position = 0, slot = 2), played)

        assertEquals(HAND_SIZE - 1, stepped.ownHand.size)
        assertTrue(stepped.ownHand.none { it.id == played.id })
        assertEquals(HAND_SIZE, stepped.opponentHand.size, "the other hand is untouched")
    }

    /**
     * A hidden opponent card leaves its slot the same way a visible one does.
     *
     * The count is public even when the cards are not, so the hand has to shrink — a player can see
     * how many are left whether or not they can see what they are.
     */
    @Test
    fun theOpponentsHandShrinksEvenWhenItsCardsAreHidden() {
        val theirs = red[1]
        val stepped = view.after(placement(CardColor.RED, theirs, position = 0, slot = 1), theirs)

        assertEquals(HAND_SIZE - 1, stepped.opponentHand.size)
        assertTrue(stepped.opponentHand.all { it == null }, "and they are still hidden")
        assertEquals(HAND_SIZE, stepped.ownHand.size)
    }

    /**
     * A slot this side does not have costs the animation and nothing else.
     *
     * A response naming one is a version disagreement, and the frame it would have drawn is about
     * to be replaced by the server's view anyway. Crashing a match in progress is the worse trade.
     */
    @Test
    fun aSlotThatIsNotThereLeavesTheHandAloneRatherThanThrowing() {
        val played = blue.first()
        val stepped = view.after(
            placement(CardColor.BLUE, played, position = 0, slot = HAND_SIZE + 3),
            played,
        )

        assertEquals(HAND_SIZE, stepped.ownHand.size)
        assertEquals(played.id, stepped.board.cells[0]?.card?.id, "the card still landed")
    }

    /**
     * And the other end of the same guard.
     *
     * A slot past the hand is the disagreement one expects from a newer server; a negative one is
     * the shape a *missing* field decodes to, and it reaches the same code by a different route.
     * Both are version disagreements about to be overwritten by the server's own view, so both cost
     * the animation and nothing else — an index used unchecked would throw here instead.
     */
    @Test
    fun aNegativeSlotIsIgnoredForTheSameReasonAnAbsentOneIs() {
        val played = blue.first()
        val stepped = view.after(
            placement(CardColor.BLUE, played, position = 0, slot = -1),
            played,
        )

        assertEquals(HAND_SIZE, stepped.ownHand.size, "no slot was named, so none was removed")
        assertEquals(played.id, stepped.board.cells[0]?.card?.id, "the card still landed")
    }

    // ---- What a stepped board may not claim -------------------------------

    /**
     * A stepped board is read-only.
     *
     * The player's next turn arrives with the server's view, which is the only thing entitled to
     * say what may be played: under Chaos the choice is a roll, and rolling it here would offer a
     * card the referee will not accept.
     */
    @Test
    fun aSteppedBoardOffersNothingToPlay() {
        val played = blue.first()
        val open = view.copy(playableHandIndices = listOf(0, 1, 2, 3, 4))

        val stepped = open.after(placement(CardColor.BLUE, played, position = 0), played)

        assertTrue(stepped.playableHandIndices.isEmpty())
        assertTrue(stepped.playableCards.isEmpty())
    }

    // ---- The check the design rests on ------------------------------------

    /**
     * **Two placements stepped equal the board the referee sent.**
     *
     * The player's card and the opponent's reply come back in one answer. `PveMatchScreen` walks
     * them for the animation and then adopts the server's own view; this is the assertion that the
     * two agree, so that the walk is a picture of the truth rather than a second opinion.
     */
    @Test
    fun theSteppedBoardEqualsTheOneTheRefereeSent() {
        val mine = blue.first()
        val theirs = red.first()
        // The referee's arithmetic, done by the engine: blue's 8s take red's 2 next door.
        val afterMine = state.play(mine, position = 0)
        val afterTheirs = afterMine.play(theirs, position = 1)
        val served = MatchView.of(afterTheirs, CardColor.BLUE, HandVisibility.HIDDEN)

        val plays = listOf(
            toPlacement(afterMine, slot = 0),
            toPlacement(afterTheirs, slot = 0),
        )
        var walked = view
        for (play in plays) {
            walked = walked.after(play, catalogue.getValue(play.cardId))
        }

        assertEquals(served.board.cells.map { it?.owner }, walked.board.cells.map { it?.owner })
        assertEquals(
            served.board.cells.map { it?.card?.id },
            walked.board.cells.map { it?.card?.id },
        )
        assertEquals(served.placement, walked.placement)
        assertContentEquals(served.ownHand.map { it.id }, walked.ownHand.map { it.id })
        assertEquals(served.opponentHand.size, walked.opponentHand.size)
        assertEquals(served.score, walked.score, "and so the score agrees too")
    }

    // ---- Taking the opening back off --------------------------------------

    /**
     * **The dealt board, played forward again, is the board the referee sent.**
     *
     * The counterpart of [theSteppedBoardEqualsTheOneTheRefereeSent], and the claim that lets an
     * opening be announced before it is acted on: a deal the opponent won the toss for arrives with
     * their card already played, [asDealt] takes it back off so the coin flip has something left to
     * decide, and [after] puts it back. If the round trip lost anything, the player would spend the
     * first turn looking at a board the server does not have.
     */
    @Test
    fun theDealtBoardPlayedForwardIsTheOneTheRefereeSent() {
        val theirs = red.first()
        val opened = redFirst.play(theirs, position = 4)
        val served = MatchView.of(opened, CardColor.BLUE, HandVisibility.HIDDEN)
        val play = toPlacement(opened, slot = 0)

        val dealt = assertNotNull(served.asDealt(play, theirs))

        assertEquals(0, dealt.placement, "the deal is before the first placement")
        assertNull(dealt.lastPlay, "and nothing has been announced on it")
        assertTrue(dealt.board.cells.all { it == null }, "the board is empty as it was dealt")
        assertEquals(HAND_SIZE, dealt.opponentHand.size, "the card is back in the hand it left")

        val walked = dealt.after(play, theirs)

        assertEquals(
            served.board.cells.map { it?.card?.id },
            walked.board.cells.map { it?.card?.id },
        )
        assertEquals(
            served.board.cells.map { it?.owner },
            walked.board.cells.map { it?.owner },
        )
        assertEquals(served.placement, walked.placement)
        assertEquals(served.opponentHand.size, walked.opponentHand.size)
        assertEquals(served.score, walked.score, "and so the score agrees too")
    }

    /**
     * A card the rules kept hidden goes back face down.
     *
     * Playing it made it public; it was not public *before*, and putting the real card back would
     * show the player one of the opponent's five under a closed hand — through the very animation
     * that is meant to be explaining which cards they are allowed to see.
     */
    @Test
    fun aHiddenCardGoesBackIntoTheHandFaceDown() {
        val theirs = red.first()
        val opened = redFirst.play(theirs, position = 0)
        val served = MatchView.of(opened, CardColor.BLUE, HandVisibility.HIDDEN)

        val dealt = assertNotNull(served.asDealt(toPlacement(opened, slot = 0), theirs))

        assertTrue(dealt.opponentHand.all { it == null }, "a closed hand stays closed")
    }

    /**
     * Under All Open it goes back the way it was: face up, in the slot it was played from.
     *
     * Which slot is not a detail. `HandVisibility.afterPlaying` closes the hand up over the card
     * that left, so a card put back anywhere else would shuffle the four the player has been
     * looking at since the deal.
     */
    @Test
    fun anOpenCardGoesBackIntoTheSlotItWasPlayedFrom() {
        val theirs = red[2]
        val opened = allOpen.play(theirs, position = 0)
        val visible = HandVisibility(red.indices.toSet())
        val served = MatchView.of(opened, CardColor.BLUE, visible.afterPlaying(2))

        val dealt = assertNotNull(served.asDealt(toPlacement(opened, slot = 2), theirs))

        assertContentEquals(red.map { it.id }, dealt.opponentHand.map { it?.id })
    }

    /**
     * A board that is not an unplayed opening is not reconstructed at all.
     *
     * Every guard in [asDealt] asks the same question, and a no is answered by leaving the caller
     * with the server's view rather than a guess. A second placement is the cheapest way to ask it:
     * the position is real, the arithmetic would even work, and it is still not an opening.
     */
    @Test
    fun aBoardPastTheOpeningIsNotTakenApart() {
        val mine = blue.first()
        val replied = redFirst.play(red.first(), position = 0).play(mine, position = 4)
        val served = MatchView.of(replied, CardColor.BLUE, HandVisibility.HIDDEN)

        assertNull(served.asDealt(toPlacement(replied, slot = 0), mine))
    }

    /** Nothing is announced before the first placement is walked. */
    @Test
    fun aBoardNobodyHasPlayedOnAnnouncesNothing() {
        assertNull(view.lastPlay)
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun card(number: Int, power: Int) = Card(
        id = Card.idFor(block = 1, number = number),
        nameKey = "STR_TEST_$number",
        name = "Test $number",
        top = power,
        right = power,
        bottom = power,
        left = power,
        rarity = 1,
    )

    private fun hand(from: Int, power: Int) =
        (from until from + HAND_SIZE).map { card(it, power) }

    private val blue = hand(from = 1, power = 8)
    private val red = hand(from = 11, power = 2)

    private val state = MatchState.start(blueHand = blue, redHand = red, first = CardColor.BLUE)

    /** The other half of the toss, which is the only one that deals a board already played on. */
    private val redFirst =
        MatchState.start(blueHand = blue, redHand = red, first = CardColor.RED)

    /** The same, under the rule that decides whether a played card goes back face up. */
    private val allOpen = MatchState.start(
        blueHand = blue,
        redHand = red,
        first = CardColor.RED,
        rules = GameRules(open = OpenRule.ALL_OPEN),
    )

    private val catalogue: Map<Int, Card> = state.hands.values.flatten().associateBy { it.id }

    private val view = MatchView.of(state, CardColor.BLUE, HandVisibility.HIDDEN)

    private fun placement(
        player: CardColor,
        card: Card,
        position: Int,
        slot: Int = 0,
        captures: List<Capture> = emptyList(),
    ) = Placement(
        player = player,
        cardId = card.id,
        position = position,
        captures = captures,
        handIndex = slot,
    )

    /** The last placement of [state], as the wire announces it. */
    private fun toPlacement(state: MatchState, slot: Int): Placement {
        val play = assertNotNull(state.lastPlay)
        return Placement(
            player = play.player,
            cardId = play.card.id,
            position = play.position,
            captures = play.captures,
            handIndex = slot,
        )
    }
}
