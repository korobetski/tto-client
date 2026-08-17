package com.tripletriad.ui

import com.tripletriad.model.CaptureKind
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchIntroStep
import com.tripletriad.model.MatchOutcome
import com.tripletriad.model.MatchState
import com.tripletriad.model.MatchView
import com.tripletriad.model.PlayResult
import com.tripletriad.model.TypeRule

enum class MatchBanner(
    val textureId: String,
    val motion: BannerMotion,
    val enterMillis: Int,
    val holdMillis: Int,
    val exitMillis: Int,
    val leadInMillis: Int = 0,
) {
    START("121601", BannerMotion.ZOOM, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    BLUE_TURN(
        "121602",
        BannerMotion.SLIDE_RIGHT,
        enterMillis = 200,
        holdMillis = 800,
        exitMillis = 200,
    ),

    RED_TURN(
        "121603",
        BannerMotion.SLIDE_LEFT,
        enterMillis = 200,
        holdMillis = 800,
        exitMillis = 200,
    ),

    BLUE_WIN(
        "121604",
        BannerMotion.ZOOM_FADE,
        enterMillis = 400,
        holdMillis = 800,
        exitMillis = 400,
    ),

    RED_WIN(
        "121605",
        BannerMotion.ZOOM_FADE,
        enterMillis = 400,
        holdMillis = 800,
        exitMillis = 400,
    ),

    DRAW("121606", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 800, exitMillis = 400),

    ALL_OPEN(
        "121611",
        BannerMotion.SLIDE_LEFT,
        enterMillis = 300,
        holdMillis = 800,
        exitMillis = 200,
        leadInMillis = 300,
    ),

    THREE_OPEN(
        "121612",
        BannerMotion.SLIDE_LEFT,
        enterMillis = 300,
        holdMillis = 800,
        exitMillis = 200,
        leadInMillis = 300,
    ),

    REVERSE(
        "121613",
        BannerMotion.ZOOM_FADE,
        enterMillis = 400,
        holdMillis = 600,
        exitMillis = 400,
    ),

    CHAOS("121614", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    ORDER("121615", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    RANDOM("121616", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    SWAP("121617", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    COMBO(
        "121618",
        BannerMotion.ZOOM,
        enterMillis = 400,
        holdMillis = 600,
        exitMillis = 200,
        leadInMillis = 800,
    ),

    PLUS("121619", BannerMotion.ZOOM, enterMillis = 400, holdMillis = 600, exitMillis = 200),

    SAME("121620", BannerMotion.ZOOM, enterMillis = 400, holdMillis = 600, exitMillis = 200),

    FALLEN_ACE(
        "121621",
        BannerMotion.ZOOM_FADE,
        enterMillis = 400,
        holdMillis = 600,
        exitMillis = 400,
    ),

    ASCENSION(
        "121622",
        BannerMotion.ZOOM_UP,
        enterMillis = 400,
        holdMillis = 400,
        exitMillis = 200,
    ),

    DESCENSION(
        "121623",
        BannerMotion.ZOOM_DOWN,
        enterMillis = 400,
        holdMillis = 400,
        exitMillis = 200,
    ),

    SUDDEN_DEATH(
        "121624",
        BannerMotion.ZOOM_BOUNCE,
        enterMillis = 400,
        holdMillis = 600,
        exitMillis = 400,
    ),
    ;

    val totalMillis: Int get() = leadInMillis + enterMillis + holdMillis + exitMillis

    companion object {
        fun outcome(winner: CardColor?): MatchBanner = when (winner) {
            CardColor.BLUE -> BLUE_WIN
            CardColor.RED -> RED_WIN
            null -> DRAW
        }

        fun turn(player: CardColor): MatchBanner =
            if (player == CardColor.BLUE) BLUE_TURN else RED_TURN

        fun forCapture(kind: CaptureKind): MatchBanner? = when (kind) {
            CaptureKind.SAME, CaptureKind.SAME_WALL -> SAME
            CaptureKind.PLUS -> PLUS
            CaptureKind.COMBO -> COMBO
            CaptureKind.BASIC -> null
        }

        fun captionsFor(play: PlayResult): List<MatchBanner> {
            val kinds = play.captures.map { it.kind }.toSet()
            val direct = kinds.firstNotNullOfOrNull { kind ->
                forCapture(kind).takeIf { it != COMBO }
            }
            val combo = if (CaptureKind.COMBO in kinds) COMBO else null
            return listOfNotNull(direct, combo)
        }

        fun forIntroStep(step: MatchIntroStep): MatchBanner? = when (step) {
            MatchIntroStep.RANDOM -> RANDOM
            MatchIntroStep.ALL_OPEN -> ALL_OPEN
            MatchIntroStep.THREE_OPEN -> THREE_OPEN
            MatchIntroStep.ORDER -> ORDER
            MatchIntroStep.CHAOS -> CHAOS
            MatchIntroStep.REVERSE -> REVERSE
            MatchIntroStep.FALLEN_ACE -> FALLEN_ACE
            MatchIntroStep.SWAP -> SWAP
            MatchIntroStep.START -> START
            MatchIntroStep.COIN_FLIP -> null
        }

        fun afterPlacement(state: MatchState): List<MatchBanner> = afterPlacement(
            play = state.lastPlay,
            rules = state.rules,
            outcome = state.outcome(),
            next = state.currentPlayer,
        )

        fun afterPlacement(view: MatchView): List<MatchBanner> = afterPlacement(
            play = view.lastPlay,
            rules = view.rules,
            outcome = view.outcome(),
            next = view.currentPlayer,
        )

        private fun afterPlacement(
            play: PlayResult?,
            rules: GameRules,
            outcome: MatchOutcome?,
            next: CardColor?,
        ): List<MatchBanner> {
            if (play == null) return emptyList()
            return buildList {
                addAll(captionsFor(play))
                ascension(rules.typeRule, play.card.type)?.let(::add)
                when (outcome) {
                    null -> next?.let { add(turn(it)) }
                    is MatchOutcome.Win -> add(outcome(outcome.winner))
                    is MatchOutcome.Draw -> add(DRAW)
                    // Both, in that order: `PVEMatchScreen.as:63-68` announces the draw and
                    // then that it is not over. The rematch's own `opening` follows.
                    is MatchOutcome.SuddenDeath -> {
                        add(DRAW)
                        add(SUDDEN_DEATH)
                    }
                }
            }
        }

        private fun ascension(rule: TypeRule, type: CardType?): MatchBanner? = when {
            type == null -> null
            rule == TypeRule.ASCENSION -> ASCENSION
            rule == TypeRule.DESCENSION -> DESCENSION
            else -> null
        }
    }
}

enum class BannerMotion {
    ZOOM,

    ZOOM_FADE,

    ZOOM_BOUNCE,

    ZOOM_UP,

    ZOOM_DOWN,

    SLIDE_RIGHT,

    SLIDE_LEFT,
}
