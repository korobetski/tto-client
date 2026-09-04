package com.tripletriad.settings

/**
 * How long the board is allowed to take over the things it announces.
 *
 * ### Why this is a setting and not a constant
 *
 * The pauses themselves are the design and stay where they are written — a caption holds for as
 * long as `MatchBanner` says, the tutor speaks for as long as `TalkBubble` says. What varies is how
 * many times one player has already seen them. A Card Club run is seven matches and therefore seven
 * full introductions: a roulette, a coin toss, a caption per rule in force, and a card crossing the
 * board for Swap. The first is the game explaining itself; the seventh is a wait.
 *
 * The factor already existed — `com.tripletriad.ui.Pacing` multiplies every authored pause on the
 * way into `delay` and `tween` — but its only source was a parameter on `App` that a test passes
 * and nothing else ever did. This is that same dial, turned by the player instead.
 *
 * ### [scale] is a duration factor, so smaller is faster
 *
 * The names are the player's way round and the numbers are the code's. [INSTANT] is zero, which
 * every consumer already handles: a `delay(0)` yields and a `tween(0)` snaps, and both the banner
 * overlay and the gates that wait out an intro — `introFinished`, `pveIntroFinished`,
 * `openRevealed` — read the same factor, so they cannot disagree about when the intro ended.
 *
 * ### What it does *not* touch
 *
 * The turn clock. `DEFAULT_TURN_LIMIT` is not an authored pause but a rule about how long a player
 * may think, and nothing multiplies it by this — a player who wanted shorter animations has not
 * asked for thirty seconds of their turn to become none.
 */
enum class MatchSpeed(val tag: String, val scale: Double) {
    NORMAL("normal", 1.0),
    FAST("fast", 0.5),
    FASTER("faster", 0.25),
    INSTANT("instant", 0.0),
    ;

    /** Named in the bundles rather than here, so the chip reads in the player's language. */
    val labelKey: String get() = "APP_SPEED_$name"

    companion object {
        /** What the game ships at, and what an unreadable [tag] falls back to. */
        val Default: MatchSpeed = NORMAL

        fun forTag(tag: String): MatchSpeed? = entries.firstOrNull { it.tag == tag }
    }
}
