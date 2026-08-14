package com.tripletriad.ui

import androidx.compose.runtime.Composable
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import com.tripletriad.time.Clock

/*
 * Where a match's randomness comes from, and what to show when there is none.
 *
 * Its own file rather than two more functions in `MatchScreen.kt`, which is at both of detekt's
 * limits for a reason its own KDoc already gives: it is the largest screen in the app and every
 * addition has to earn its place there. Neither of these is about playing a match.
 */

/**
 * Shown instead of a board when an account has no seed left to play on.
 *
 * ### Why a match is refused rather than played
 *
 * Because the alternative is worse. A match played on a seed the server never issued is a real
 * match, honestly played, that is then **rejected** — the player watches nine placements count for
 * nothing and is told why afterwards, if at all. Saying so first costs them a match they have not
 * played yet.
 *
 * It should be rare to the point of never: the stock is fifty and tops itself up whenever the app
 * has a network. Reaching this screen means a long stretch offline, which is exactly the case the
 * stock exists for and exactly the case it can eventually run out of.
 */
@Composable
internal fun NoSeedNotice(profile: GameSave, onExit: () -> Unit) {
    val strings = LocalStrings.current

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.PLAY],
        onBack = onExit,
    ) {
        EmptyNote(strings[StringKeys.NO_SEEDS], NO_SEEDS_TEST_TAG)
    }
}

/** The notice shown when an account has run its offline seed stock down. */
const val NO_SEEDS_TEST_TAG: String = "match-no-seeds"

/**
 * The seed one match is played on, or null when an account has none left.
 *
 * A **scripted** match invents one and must: `reportTranscript` never submits a scripted match —
 * the script forces the coin flip, fixes the deal and hands the opponent a different strategy, none
 * of which a seed carries — so spending a ticket on the tutorial would spend it on a match that can
 * never be credited.
 *
 * Everything else draws from the stock. On a local profile that is a clock reading, because there
 * is nobody to keep honest; on an account it is a seed the server issued and will accept once. See
 * `SeedTickets`.
 */
internal fun seedFor(script: MatchScript?, clock: Clock, nextSeed: () -> Int?): Int? =
    if (script != null) clock.nowMillis().toInt() else nextSeed()
