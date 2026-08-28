package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **A deal the opponent won the toss for still opens with an empty board.**
 *
 * `POST /pve/matches` answers with the position after the toss has been honoured, so half of all
 * deals come back with the opponent's card already on the board — `PveMatchView.plays` announces
 * it. `PveMatchScreen` adopted that answer whole, and the result was a board whose *first frame*
 * had a card on it, its placement sound played, and the announcements that explain that card —
 * the rules in force, the hand turning face up under Open, and the coin flip deciding who moves
 * first — playing afterwards over a decision already taken. The flip in particular was reduced to
 * decoration: it reported a winner the board had acted on three seconds earlier.
 *
 * So the opening is walked like any other answer, from the position the referee dealt
 * (`MatchView.asDealt`), and lands once the intro has finished.
 *
 * ### Why this one runs at the shipped pace
 *
 * Because what it is checking **is** the pacing. [TEST_PACING] would compress the intro to a few
 * hundred milliseconds, which is the same order as the harness's own latency — the window the
 * first assertion has to land in would be racing a `waitUntil` poll rather than measuring
 * anything. `Pacing.Default` gives it the three seconds the player actually gets.
 */
@OptIn(ExperimentalTestApi::class)
class PveOpeningUiTest {

    @Test
    fun theOpponentsOpeningWaitsForTheAnnouncementsThatExplainIt() = runComposeUiTest {
        // The toss is forced rather than seeded: it is drawn after the rules and both hands, so a
        // seed that happens to hand the opening to red today stops doing so the moment a
        // catalogue gains a card.
        val stub = PveStubServer().apply { toss = CardColor.RED }
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                server = stub.connection,
                pacing = Pacing.Default,
            )
        }

        openDashboard()
        openOpponents()
        // Stops at the board rather than at the player's turn, which is the whole point: the
        // frames under test are the ones before the opponent has moved.
        challenge(awaitTurn = false)

        assertEquals(
            HAND_SIZE,
            handSize(CardColor.RED),
            "the opponent still holds five cards while the toss is being announced",
        )
        assertEquals(0, placementsMade(), "nothing is on the board until the intro has played")

        awaitPlayer()

        assertEquals(
            HAND_SIZE - 1,
            handSize(CardColor.RED),
            "and then the card the toss won them lands",
        )
        assertEquals(1, placementsMade())
    }

    /**
     * The other half of the toss, which was never broken and is what says so.
     *
     * A deal the *player* won carries no placement at all, so there is nothing to hold back — and
     * nothing here may hold back, either. A guard that waited out the intro before showing the
     * board would be just as wrong in the opposite direction.
     */
    @Test
    fun aDealThePlayerWonPutsNothingOnTheBoardEither() = runComposeUiTest {
        val stub = PveStubServer().apply { toss = CardColor.BLUE }
        setContent {
            TestApp(
                store = settingsFor(AppLocale.EN_US),
                server = stub.connection,
                pacing = Pacing.Default,
            )
        }

        openDashboard()
        openOpponents()
        challenge(awaitTurn = false)

        assertEquals(0, placementsMade())

        awaitPlayer()

        assertEquals(0, placementsMade(), "the player is on move and has not played yet")
        assertEquals(HAND_SIZE, handSize(CardColor.BLUE))
    }
}
