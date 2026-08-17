package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.FF14_FORMAT
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadFormatCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.TOTAL_CARDS
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.MatchVerdict
import com.tripletriad.protocol.TranscriptVerifier
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MatchTranscriptTest {
    private val cards = runBlocking { loadCardCatalog() }

    private val formats = runBlocking { loadFormatCatalog() }
    private val format = formats[FF14_FORMAT]!!

    private val npcs = runBlocking { loadNpcCatalog() }
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    private val opponent = npcs
        .available(FF14_FORMAT, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
        .first { it.iconId == "tt-master" }

    @Test
    fun aMatchPlayedInTheUiReplaysInTheEngine() = runComposeUiTest {
        val transcript = playAMatch()

        val verdict = TranscriptVerifier.verify(transcript, cards, npcs, formats)

        assertIs<MatchVerdict.Accepted>(
            verdict,
            "the engine could not replay a match the UI just played: $verdict",
        )
    }

    @Test
    fun theReplayedScoreAccountsForEveryCard() = runComposeUiTest {
        val transcript = playAMatch()

        val accepted = assertIs<MatchVerdict.Accepted>(
            TranscriptVerifier.verify(transcript, cards, npcs, formats),
        )

        assertEquals(TOTAL_CARDS, accepted.blue + accepted.red)
    }

    @Test
    fun theTranscriptCarriesOnlyThePlayersOwnPlacements() = runComposeUiTest {
        val transcript = playAMatch()

        assertTrue(
            transcript.moves.size in PLAYER_MOVES_MIN..PLAYER_MOVES_MAX,
            "four or five placements depending on the coin flip, was ${transcript.moves.size}",
        )
        assertEquals(
            transcript.moves.map { it.position }.distinct().size,
            transcript.moves.size,
            "no cell is played twice",
        )
        assertEquals(HAND_SIZE, transcript.deck.size)
    }

    private fun ComposeUiTest.playAMatch(): MatchTranscript {
        var emitted: MatchTranscript? = null
        setContent {
            TripleTriadTheme {
                CompositionLocalProvider(LocalStrings provides english) {
                    MatchScreen(
                        catalog = cards,
                        profile = GameSave.new(createdAt = 0L),
                        npc = opponent,
                        format = format,
                        clock = FixedClock(),
                        nextSeed = { 1 },
                        onPersist = {},
                        onExit = {},
                        onTranscript = { emitted = it },
                    )
                }
            }
        }
        settleDeck()
        playOut()
        // The transcript is emitted from the same effect that credits the match, after the reward,
        // so it can land a frame after the result panel that `playOut` waits for.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { emitted != null }
        return assertNotNull(emitted, "the match finished but no transcript was emitted")
    }

    private companion object {
        const val PLAYER_MOVES_MIN = 4
        const val PLAYER_MOVES_MAX = 5
    }
}
