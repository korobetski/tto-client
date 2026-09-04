package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.data.MatchHistoryRepository
import com.tripletriad.model.MatchRecord
import com.tripletriad.model.OpponentKind
import com.tripletriad.protocol.PveMatchView
import com.tripletriad.protocol.PvpMatchView
import com.tripletriad.storage.DocumentStore
import com.tripletriad.time.Clock

/**
 * What this character has played, kept on this device.
 *
 * ### Why the client keeps a record the server already has
 *
 * The server settles every match and could be asked. It is asked for nothing here on purpose: a
 * history is read on the profile screen, which is reachable with no connection at all, and a list
 * that is empty offline is a list nobody trusts. It is also the one thing about a match worth
 * keeping that the profile itself throws away — `Stats` counts three numbers, and a run of four
 * defeats to the same opponent is invisible in them.
 *
 * ### Written where the result is *settled*, not where it is *shown*
 *
 * [MatchJournal] is composed once at the root, beside `MatchSettlement`, and watches the two
 * sessions rather than the two boards. A player who walks off the board before the result panel
 * opens still has the match recorded, and a match resumed on another screen is recorded once —
 * `MatchHistoryRepository.append` drops a row whose id it already holds, and a match id is the
 * server's own.
 *
 * ### The key is the character, not the account
 *
 * `ProfileGate.queueKey` — the same key the transcript queue uses. On an account it names the
 * server and the character; on a local `.sav` it names the file. Two characters on one device
 * therefore keep two histories, which is the only reading of "what have I played" that makes
 * sense.
 */
@Stable
internal class MatchJournal(private val repository: MatchHistoryRepository) {
    var records: List<MatchRecord> by mutableStateOf(emptyList())
        private set

    /** True until the first read has answered, so an empty list can be told from an unread one. */
    var isLoading: Boolean by mutableStateOf(true)
        private set

    suspend fun refresh(profileKey: String?) {
        if (profileKey == null) {
            records = emptyList()
            isLoading = false
            return
        }
        records = repository.all(profileKey)
        isLoading = false
    }

    suspend fun record(profileKey: String?, record: MatchRecord) {
        if (profileKey == null) return
        repository.append(profileKey, record)
        records = repository.all(profileKey)
    }
}

@Composable
internal fun rememberMatchJournal(store: DocumentStore): MatchJournal =
    remember(store) { MatchJournal(MatchHistoryRepository(store)) }

/**
 * Writes a row the moment either session reports a settled match.
 *
 * Two effects rather than one, because the two sessions carry different types and neither is a
 * subtype of anything shared. What they have in common is the shape of the question — *is there an
 * outcome, and have we written it down* — and the answer to the second half is
 * [MatchHistoryRepository.append]'s, not this composable's: it is keyed on the match id, so an
 * effect that re-runs writes nothing new.
 *
 * A **sudden-death draw is not settled** and must not be recorded: the rematch decides it, and
 * `MatchResult.of` answers null for exactly that reason. Neither session reports one as an
 * outcome — a rematch arrives as the same match id with `rematch` bumped — so nothing here has to
 * special-case it, and the id would collapse the pair into one row if it did.
 */
@Composable
internal fun MatchJournalWriter(
    journal: MatchJournal,
    profileKey: String?,
    clock: Clock,
    pve: PveSession?,
    pvp: PvpSession?,
) {
    LaunchedEffect(journal, profileKey) { journal.refresh(profileKey) }

    val pveMatch = pve?.match
    LaunchedEffect(journal, profileKey, pveMatch?.matchId, pveMatch?.outcome) {
        pveMatch?.asRecord(clock.nowMillis())?.let { journal.record(profileKey, it) }
    }

    val pvpMatch = pvp?.match
    LaunchedEffect(journal, profileKey, pvpMatch?.matchId, pvpMatch?.outcome) {
        pvpMatch?.asRecord(clock.nowMillis())?.let { journal.record(profileKey, it) }
    }
}

/**
 * This board as a history row, or null while it is still being played.
 *
 * The opponent is stored by **`iconID`** and not by the name on screen, for the reason
 * `GameSave.npcWins` is keyed that way: ids are not unique across the two tables and a translated
 * name is not a key at all. The screen resolves it back through the catalogue, so a history read in
 * French says the French name for a row written in English.
 *
 * `durationMillis` is left at zero rather than guessed. Nothing on the wire says when the match was
 * dealt, and the only figure this end could produce — how long this screen had the board — is wrong
 * for every resumed match, which is precisely the case a duration would be interesting for.
 */
internal fun PveMatchView.asRecord(at: Long): MatchRecord? {
    val settled = outcome ?: return null
    return MatchRecord(
        id = matchId,
        formatId = formatId,
        opponentKind = OpponentKind.NPC,
        opponentName = opponentIconId,
        timestamp = at,
        result = settled.result,
        scoreBlue = settled.blue,
        scoreRed = settled.red,
        rules = rules,
        mgpDelta = settled.reward?.mgp ?: 0,
        xpGained = (settled.reward?.xp ?: 0).toLong(),
    )
}

/**
 * The same for a match against a person.
 *
 * `self` is the side the server dealt this player, not blue: `PvpSession.view` mirrors a red
 * player's board so they see themselves in blue, and a row that recorded the *mirror* would report
 * the wrong half of the score. [MatchRecord.ownScore] reads `score[self]`, so storing the board as
 * the server states it and naming the side is the only arrangement that survives the mirror.
 *
 * The stake is folded into `mgpDelta` because that is what the purse actually did — a wagered match
 * that pays 100 and returns a 500 stake moved 600, and reporting the flat payout alone would make
 * the row disagree with the account it describes.
 */
internal fun PvpMatchView.asRecord(at: Long): MatchRecord? {
    val settled = outcome ?: return null
    return MatchRecord(
        id = matchId,
        formatId = formatId,
        opponentKind = OpponentKind.PVP,
        opponentName = opponentName,
        timestamp = at,
        result = settled.result,
        self = side,
        scoreBlue = settled.blue,
        scoreRed = settled.red,
        rules = rules,
        mgpDelta = settled.mgp + settled.stakeMgp,
        xpGained = settled.xp.toLong(),
    )
}
