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

const val NO_SEEDS_TEST_TAG: String = "match-no-seeds"

internal fun seedFor(script: MatchScript?, clock: Clock, nextSeed: () -> Int?): Int? =
    if (script != null) clock.nowMillis().toInt() else nextSeed()
