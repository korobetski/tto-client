package com.tripletriad.ui

import com.tripletriad.model.Board
import com.tripletriad.model.Capture
import com.tripletriad.model.CaptureKind
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.CoinFlip
import com.tripletriad.model.GameRules
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchIntroStep
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import com.tripletriad.model.OpenRule
import com.tripletriad.model.OrderRule
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.PlayResult
import com.tripletriad.model.TypeRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatchBannerTest {

    @Test
    fun aRefereedMatchAnnouncesTheRulesItIsPlayedUnder() {
        val rules = GameRules(reverse = true, fallenAce = true, open = OpenRule.ALL_OPEN)

        val played = serverIntroAnimations(rules, CardColor.BLUE)

        val captions = played.filterIsInstance<MatchAnimation.Caption>().map { it.banner }
        assertTrue(MatchBanner.ALL_OPEN in captions, "All Open was not announced: $captions")
        assertTrue(MatchBanner.REVERSE in captions, "Reverse was not announced: $captions")
        assertTrue(MatchBanner.FALLEN_ACE in captions, "Fallen Ace was not announced: $captions")
        assertTrue(MatchBanner.START in captions, "the match never said Start")
    }

    @Test
    fun theTossLandsOnWhoeverTheServerSaidMovesFirst() {
        for (first in CardColor.entries) {
            val played = serverIntroAnimations(GameRules(), first)

            val toss = played.filterIsInstance<MatchAnimation.Toss>().single()
            assertEquals(first, toss.flip.winner, "the coin disagreed with the server")
        }
    }

    @Test
    fun aMatchWithNoRulesStillOpens() {
        val played = serverIntroAnimations(GameRules(), CardColor.RED)

        val captions = played.filterIsInstance<MatchAnimation.Caption>().map { it.banner }
        assertEquals(listOf(MatchBanner.START), captions)
    }

    @Test
    fun aBasicCaptureEarnsNoCaption() {
        val play = placement(CaptureKind.BASIC)

        assertEquals(emptyList(), MatchBanner.captionsFor(play))
    }

    @Test
    fun playingIntoAnEmptyBoardEarnsNoCaption() {
        assertEquals(emptyList(), MatchBanner.captionsFor(placement()))
    }

    @Test
    fun aSameEarnsTheSameCaption() {
        assertEquals(listOf(MatchBanner.SAME), MatchBanner.captionsFor(placement(CaptureKind.SAME)))
    }

    @Test
    fun aPlusEarnsThePlusCaption() {
        assertEquals(listOf(MatchBanner.PLUS), MatchBanner.captionsFor(placement(CaptureKind.PLUS)))
    }

    @Test
    fun sameWallBorrowsTheSameCaption() {
        assertEquals(
            listOf(MatchBanner.SAME),
            MatchBanner.captionsFor(placement(CaptureKind.SAME_WALL)),
        )
    }

    @Test
    fun oneRuleFlippingThreeCardsIsStillOneCaption() {
        val play = placement(CaptureKind.SAME, CaptureKind.SAME, CaptureKind.SAME)

        assertEquals(listOf(MatchBanner.SAME), MatchBanner.captionsFor(play))
    }

    @Test
    fun aComboFollowsTheCaptionThatCausedIt() {
        val play = placement(CaptureKind.SAME, CaptureKind.COMBO)

        assertEquals(listOf(MatchBanner.SAME, MatchBanner.COMBO), MatchBanner.captionsFor(play))
    }

    @Test
    fun theComboIsSecondEvenWhenItIsCapturedFirst() {
        val play = placement(CaptureKind.COMBO, CaptureKind.PLUS)

        assertEquals(listOf(MatchBanner.PLUS, MatchBanner.COMBO), MatchBanner.captionsFor(play))
    }

    @Test
    fun aComboOnItsOwnIsStillACaption() {
        val play = placement(CaptureKind.COMBO)

        assertEquals(listOf(MatchBanner.COMBO), MatchBanner.captionsFor(play))
    }

    @Test
    fun aBasicCaptureAlongsideASpecialOneIsIgnoredRatherThanWinning() {
        val play = placement(CaptureKind.BASIC, CaptureKind.SAME)

        assertEquals(listOf(MatchBanner.SAME), MatchBanner.captionsFor(play))
    }

    // ---- The outcome and turn captions --------------------------------------

    @Test
    fun theOutcomeCaptionFollowsTheWinner() {
        assertEquals(MatchBanner.BLUE_WIN, MatchBanner.outcome(CardColor.BLUE))
        assertEquals(MatchBanner.RED_WIN, MatchBanner.outcome(CardColor.RED))
        assertEquals(MatchBanner.DRAW, MatchBanner.outcome(null))
    }

    @Test
    fun theTurnCaptionFollowsTheSideToMove() {
        assertEquals(MatchBanner.BLUE_TURN, MatchBanner.turn(CardColor.BLUE))
        assertEquals(MatchBanner.RED_TURN, MatchBanner.turn(CardColor.RED))
    }

    // ---- The pre-match chain ------------------------------------------------

    @Test
    fun everyIntroStepMapsToItsOwnCaption() {
        val mapped = MatchIntroStep.entries.associateWith(MatchBanner::forIntroStep)

        assertEquals(
            mapOf(
                MatchIntroStep.RANDOM to MatchBanner.RANDOM,
                MatchIntroStep.ALL_OPEN to MatchBanner.ALL_OPEN,
                MatchIntroStep.THREE_OPEN to MatchBanner.THREE_OPEN,
                MatchIntroStep.ORDER to MatchBanner.ORDER,
                MatchIntroStep.CHAOS to MatchBanner.CHAOS,
                MatchIntroStep.REVERSE to MatchBanner.REVERSE,
                MatchIntroStep.FALLEN_ACE to MatchBanner.FALLEN_ACE,
                MatchIntroStep.SWAP to MatchBanner.SWAP,
                MatchIntroStep.COIN_FLIP to null,
                MatchIntroStep.START to MatchBanner.START,
            ),
            mapped,
        )
    }

    @Test
    fun onlyTheCoinFlipHasNoCaption() {
        val silent = MatchIntroStep.entries.filter { MatchBanner.forIntroStep(it) == null }

        assertEquals(listOf(MatchIntroStep.COIN_FLIP), silent)
    }

    @Test
    fun noTwoIntroStepsShareACaption() {
        val captions = MatchIntroStep.entries.mapNotNull(MatchBanner::forIntroStep)

        assertEquals(captions.size, captions.toSet().size, "two intro steps share a caption")
    }

    @Test
    fun theRulesWithNoOpeningCaptionStaySilent() {
        val rules = GameRules(
            typeRule = TypeRule.ELEMENTAL,
            same = true,
            sameWall = true,
            plus = true,
            suddenDeath = true,
        )

        val captions = MatchPreparation.introSteps(rules).mapNotNull(MatchBanner::forIntroStep)

        assertEquals(listOf(MatchBanner.START), captions)
    }

    @Test
    fun everyRuleIsAnnouncedInThePhaseOrder() {
        val rules = GameRules(
            open = OpenRule.ALL_OPEN,
            order = OrderRule.CHAOS,
            random = true,
            reverse = true,
            fallenAce = true,
            swap = true,
        )

        val captions = MatchPreparation.introSteps(rules).mapNotNull(MatchBanner::forIntroStep)

        assertEquals(
            listOf(
                MatchBanner.RANDOM,
                MatchBanner.ALL_OPEN,
                MatchBanner.CHAOS,
                MatchBanner.REVERSE,
                MatchBanner.FALLEN_ACE,
                MatchBanner.SWAP,
                MatchBanner.START,
            ),
            captions,
        )
    }

    // ---- The intro, assembled ----------------------------------------------

    @Test
    fun theCoinFlipTakesTheStepWithNoCaption() {
        val flip = CoinFlip.forced(CardColor.BLUE)

        val played = introAnimations(setup(GameRules(swap = true), flip))

        assertEquals(
            listOf(
                MatchAnimation.Opening,
                MatchAnimation.Caption(MatchBanner.SWAP),
                MatchAnimation.SwapCards,
                MatchAnimation.Toss(flip),
                MatchAnimation.Caption(MatchBanner.START),
            ),
            played,
        )
    }

    @Test
    fun theSwapCaptionIsFollowedByTheCardsCrossing() {
        val played = serverIntroAnimations(GameRules(swap = true), CardColor.BLUE)

        assertEquals(
            listOf(MatchBanner.SWAP, MatchBanner.START),
            played.filterIsInstance<MatchAnimation.Caption>().map { it.banner },
        )
        val swapIndex = played.indexOf(MatchAnimation.Caption(MatchBanner.SWAP))
        assertEquals(MatchAnimation.SwapCards, played[swapIndex + 1], "SwapCards must follow SWAP")
    }

    @Test
    fun noOtherIntroStepEarnsTheCardsCrossing() {
        val rules = GameRules(
            open = OpenRule.ALL_OPEN,
            order = OrderRule.CHAOS,
            random = true,
            reverse = true,
            fallenAce = true,
        )

        val played = serverIntroAnimations(rules, CardColor.BLUE)

        assertEquals(0, played.count { it == MatchAnimation.SwapCards }, "$played")
    }

    @Test
    fun theTossCarriesTheRollsThatWereDrawn() {
        val flip = CoinFlip(listOf(CardColor.RED, CardColor.BLUE, CardColor.RED))

        val toss = introAnimations(setup(GameRules(), flip)).filterIsInstance<MatchAnimation.Toss>()

        assertEquals(listOf(MatchAnimation.Toss(flip)), toss)
    }

    @Test
    fun aRematchHasNoCoinFlip() {
        val rematch = setup(GameRules(reverse = true), flip = null, rematch = true)

        val played = introAnimations(rematch)

        assertEquals(
            listOf(
                MatchAnimation.Opening,
                MatchAnimation.Caption(MatchBanner.REVERSE),
                MatchAnimation.Caption(MatchBanner.START),
            ),
            played,
        )
    }

    @Test
    fun everyIntroOpensWithItsBeatBeforeAnythingIsAnnounced() {
        val played = serverIntroAnimations(GameRules(), CardColor.BLUE)

        assertEquals(MatchAnimation.Opening, played.first(), "the board announced itself at once")
        assertEquals(1, played.count { it == MatchAnimation.Opening })
    }

    @Test
    fun aPlayedBoardGetsItsPlacementCaptionsRatherThanTheIntro() {
        val state = midMatch(kinds = arrayOf(CaptureKind.PLUS))

        val played = animationsFor(state, setup(GameRules(), CoinFlip.forced(CardColor.BLUE)))

        assertEquals(
            listOf(
                MatchAnimation.Caption(MatchBanner.PLUS),
                MatchAnimation.Caption(MatchBanner.RED_TURN),
            ),
            played,
        )
    }

    // ---- What a placement owes ----------------------------------------------

    @Test
    fun anUntouchedBoardOwesNothing() {
        assertEquals(emptyList(), MatchBanner.afterPlacement(MatchState()))
    }

    @Test
    fun anOrdinaryPlacementAnnouncesTheNextSide() {
        val state = midMatch()

        assertEquals(listOf(MatchBanner.RED_TURN), MatchBanner.afterPlacement(state))
    }

    @Test
    fun captionsRunCapturesThenAscensionThenTheTurn() {
        val state = midMatch(
            rules = GameRules(typeRule = TypeRule.ASCENSION),
            played = typedCard,
            kinds = arrayOf(CaptureKind.SAME),
        )

        assertEquals(
            listOf(MatchBanner.SAME, MatchBanner.ASCENSION, MatchBanner.RED_TURN),
            MatchBanner.afterPlacement(state),
        )
    }

    @Test
    fun descensionUsesItsOwnCaption() {
        val state = midMatch(rules = GameRules(typeRule = TypeRule.DESCENSION), played = typedCard)

        assertEquals(
            listOf(MatchBanner.DESCENSION, MatchBanner.RED_TURN),
            MatchBanner.afterPlacement(state),
        )
    }

    @Test
    fun anUntypedCardEarnsNoAscensionCaption() {
        val state = midMatch(rules = GameRules(typeRule = TypeRule.ASCENSION), played = card)

        assertEquals(listOf(MatchBanner.RED_TURN), MatchBanner.afterPlacement(state))
    }

    @Test
    fun elementalAnnouncesNothingOnPlacement() {
        val state = midMatch(rules = GameRules(typeRule = TypeRule.ELEMENTAL), played = typedCard)

        assertEquals(listOf(MatchBanner.RED_TURN), MatchBanner.afterPlacement(state))
    }

    @Test
    fun theLastPlacementAnnouncesTheResultRatherThanATurn() {
        assertEquals(listOf(MatchBanner.BLUE_WIN), MatchBanner.afterPlacement(finished(6)))
        assertEquals(listOf(MatchBanner.RED_WIN), MatchBanner.afterPlacement(finished(3)))
        assertEquals(listOf(MatchBanner.DRAW), MatchBanner.afterPlacement(finished(5)))
    }

    @Test
    fun aSuddenDeathDrawSaysBoth() {
        val state = finished(blueCells = 5, rules = GameRules(suddenDeath = true))

        assertEquals(
            listOf(MatchBanner.DRAW, MatchBanner.SUDDEN_DEATH),
            MatchBanner.afterPlacement(state),
        )
    }

    @Test
    fun suddenDeathIsSilentWhenThereIsAWinner() {
        val state = finished(blueCells = 6, rules = GameRules(suddenDeath = true))

        assertEquals(listOf(MatchBanner.BLUE_WIN), MatchBanner.afterPlacement(state))
    }

    // ---- The table itself ---------------------------------------------------

    @Test
    fun everyCaptionHasItsOwnTexture() {
        val ids = MatchBanner.entries.map { it.textureId }

        assertEquals(ids.size, ids.toSet().size, "two captions share a texture")
        assertTrue(ids.all { it.length == TEXTURE_ID_LENGTH && it.all(Char::isDigit) })
    }

    @Test
    fun noCaptionOutlastsTwoSeconds() {
        val longest = MatchBanner.entries.maxBy { it.totalMillis }

        assertTrue(
            longest.totalMillis <= LONGEST_REASONABLE_MILLIS,
            "${longest.name} runs for ${longest.totalMillis}ms",
        )
    }

    @Test
    fun theCardsCrossingDoesNotOutlastTheCaptionItFollows() {
        assertTrue(MatchAnimation.SwapCards.totalMillis in 1..LONGEST_REASONABLE_MILLIS)
    }

    // ---- When the Open rules turn the cards over ----------------------------

    @Test
    fun aMatchWithNoOpenRuleHasNothingToTurnOver() {
        val intro = serverIntroAnimations(GameRules(reverse = true), CardColor.BLUE)

        assertEquals(null, openRevealMillis(intro))
    }

    @Test
    fun bothOpenRulesTurnTheirCardsOver() {
        for (rule in listOf(OpenRule.ALL_OPEN, OpenRule.THREE_OPEN)) {
            val intro = serverIntroAnimations(GameRules(open = rule), CardColor.BLUE)

            assertNotNull(openRevealMillis(intro), "$rule announced nothing to turn on")
        }
    }

    /**
     * The cards turn as the caption *ends*, not as it starts — so the rule finishes saying what it
     * is and the hand answers. Measured against the queue rather than a copied number.
     */
    @Test
    fun theCardsTurnAsTheOpenCaptionFinishes() {
        val intro = serverIntroAnimations(GameRules(open = OpenRule.ALL_OPEN), CardColor.BLUE)
        val caption = MatchAnimation.Caption(MatchBanner.ALL_OPEN)
        val upTo = intro.indexOf(caption)

        assertEquals(intro.take(upTo + 1).sumOf { it.totalMillis }, openRevealMillis(intro))
        // And it is genuinely after the caption began: the opening beat is in front of it.
        assertTrue(openRevealMillis(intro)!! > MatchBanner.ALL_OPEN.totalMillis)
    }

    @Test
    fun theTurnWaitsThroughEveryRuleAnnouncedBeforeOpen() {
        val early = serverIntroAnimations(GameRules(open = OpenRule.ALL_OPEN), CardColor.BLUE)
        val late = serverIntroAnimations(
            GameRules(open = OpenRule.ALL_OPEN, random = true),
            CardColor.BLUE,
        )

        // Random is announced first, so its caption is time the cards spend still face down.
        assertTrue(
            openRevealMillis(late)!! > openRevealMillis(early)!!,
            "a rule announced ahead of Open did not delay the turn",
        )
    }

    @Test
    fun everyCaptionActuallyPlays() {
        MatchBanner.entries.forEach { banner ->
            assertTrue(banner.enterMillis > 0, "${banner.name} has no entry")
            assertTrue(banner.holdMillis > 0, "${banner.name} is never held")
            assertTrue(banner.exitMillis > 0, "${banner.name} never leaves")
            assertTrue(banner.leadInMillis >= 0, "${banner.name} has a negative lead-in")
        }
    }

    @Test
    fun theComboLeadInCoversTheCaptionItFollows() {
        val precedes = MatchBanner.SAME.enterMillis + MatchBanner.SAME.holdMillis

        assertTrue(
            MatchBanner.COMBO.leadInMillis >= precedes - MatchBanner.SAME.enterMillis,
            "COMBO would land on top of SAME",
        )
    }

    // ---- Fixtures -----------------------------------------------------------

    private fun placement(vararg kinds: CaptureKind) = PlayResult(
        player = CardColor.BLUE,
        card = card,
        position = 4,
        captures = kinds.mapIndexed { index, kind ->
            Capture(position = index, kind = kind, wave = if (kind == CaptureKind.COMBO) 1 else 0)
        },
    )

    private fun setup(rules: GameRules, flip: CoinFlip?, rematch: Boolean = false) = MatchSetup(
        state = MatchState(rules = rules),
        opponentVisibility = HandVisibility.HIDDEN,
        coinFlip = flip,
        intro = MatchPreparation.introSteps(rules, rematch = rematch),
    )

    private fun midMatch(
        rules: GameRules = GameRules(),
        played: Card = card,
        kinds: Array<CaptureKind> = emptyArray(),
    ) = MatchState(
        rules = rules,
        placement = 1,
        hands = mapOf(CardColor.BLUE to listOf(card), CardColor.RED to listOf(card)),
        lastPlay = PlayResult(
            player = CardColor.BLUE,
            card = played,
            position = 4,
            captures = kinds.mapIndexed { index, kind -> Capture(index, kind, wave = 0) },
        ),
    )

    @Test
    fun aRefereedBoardAnnouncesWhatThePlacementDid() {
        for (kinds in CAPTURE_CASES) {
            val state = midMatch(kinds = kinds)
            val view = MatchView.of(state, CardColor.RED, HandVisibility.HIDDEN)

            assertEquals(
                MatchBanner.afterPlacement(state),
                MatchBanner.afterPlacement(view),
                "the two overloads disagreed on ${kinds.toList()}",
            )
        }
    }

    @Test
    fun aRefereedBoardAnnouncesHowItEnded() {
        for (blueCells in 0..Board.SIZE) {
            val state = finished(blueCells)
            val view = MatchView.of(state, CardColor.RED, HandVisibility.HIDDEN)

            assertEquals(
                MatchBanner.afterPlacement(state),
                MatchBanner.afterPlacement(view),
                "the two overloads disagreed on a $blueCells-cell board",
            )
        }
    }

    @Test
    fun anUnplayedBoardEarnsNoPlacementCaptions() {
        val fresh = MatchState(
            hands = mapOf(CardColor.BLUE to listOf(card), CardColor.RED to listOf(card)),
        )

        assertEquals(
            emptyList(),
            MatchBanner.afterPlacement(MatchView.of(fresh, CardColor.BLUE, HandVisibility.HIDDEN)),
        )
    }

    private fun finished(blueCells: Int, rules: GameRules = GameRules()) = MatchState(
        rules = rules,
        placement = 9,
        board = Board(
            cells = List(Board.SIZE) {
                PlacedCard(card, if (it < blueCells) CardColor.BLUE else CardColor.RED)
            },
        ),
        hands = mapOf(CardColor.BLUE to emptyList(), CardColor.RED to listOf(card)),
        lastPlay = PlayResult(CardColor.BLUE, card, position = 8, captures = emptyList()),
    )

    private val card = Card(
        // Ids are global; fixtures number their cards from 1.
        id = Card.idFor(block = 1, number = 1),
        nameKey = "STR_FF14_CARD_1",
        name = "Test",
        top = 1,
        right = 2,
        bottom = 3,
        left = 4,
        rarity = 1,
    )

    private val typedCard = card.copy(type = CardType.PRIMALS)

    private companion object {
        val CAPTURE_CASES: List<Array<CaptureKind>> = listOf(
            emptyArray(),
            arrayOf(CaptureKind.BASIC),
            arrayOf(CaptureKind.SAME),
            arrayOf(CaptureKind.SAME_WALL),
            arrayOf(CaptureKind.PLUS),
            arrayOf(CaptureKind.COMBO),
            arrayOf(CaptureKind.SAME, CaptureKind.COMBO),
            arrayOf(CaptureKind.PLUS, CaptureKind.COMBO),
        )

        const val TEXTURE_ID_LENGTH = 6
        const val LONGEST_REASONABLE_MILLIS = 2_000
    }
}
