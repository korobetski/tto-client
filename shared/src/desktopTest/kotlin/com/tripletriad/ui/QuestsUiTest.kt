package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.DailyQuestCatalog
import com.tripletriad.model.DailyQuests
import com.tripletriad.model.GameSave
import com.tripletriad.model.questDayOf
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The daily quests screen and the badge that leads to it.
 *
 * ### What this file is responsible for, and what it is not
 *
 * *Whether* a match advances a quest is `:core`'s, decided in `MatchRewards.credit` and pinned by
 * `DailyQuestRepositoryTest` across every objective, the day rollover and idempotence. Repeating
 * that here through a rendered board would be slower, flakier and no more convincing.
 *
 * What only this level can check is the wiring: that the screen reads the *derived* draw before a
 * character has played, that it renders a pinned record faithfully, and — the one end-to-end
 * assertion — that a match played through the real UI reaches the quest record on disk at all. A
 * screen and a repository that are each correct and not connected is exactly the defect a unit
 * test cannot see.
 */
@OptIn(ExperimentalTestApi::class)
class QuestsUiTest {
    private fun ComposeUiTest.openQuests() {
        openFromDashboard(DASHBOARD_QUESTS_TEST_TAG, QUESTS_LIST_TEST_TAG)
    }

    /**
     * A character who has never played still sees the day's three quests, at zero.
     *
     * The point of `DailyQuestRepository.statuses` deriving rather than writing: a screen that
     * needed the record to exist would show an empty list until the first match, which is the one
     * moment a player is most likely to look.
     */
    @Test
    fun aCharacterWhoHasNotPlayedSeesTodaysQuestsAtZero() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openQuests()

        val drawn = DailyQuestCatalog.idsForDay(FixedClock.DEFAULT_MILLIS, CREATED_AT)
        assertEquals(DailyQuestCatalog.PER_DAY, drawn.size, "the day's draw")
        for (id in drawn) {
            val quest = DailyQuestCatalog[id] ?: error("$id is not in the catalogue")
            onNodeWithTag(questProgressTestTag(id))
                .assertTextEquals("0 / ${quest.objective.target}")
        }
    }

    /** And the dashboard says so before the screen is ever opened. */
    @Test
    fun theDashboardBadgeCountsWhatIsFinished() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        onNodeWithTag(DASHBOARD_QUESTS_BADGE_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("0 / ${DailyQuestCatalog.PER_DAY}")
    }

    /**
     * A pinned record is rendered as it stands, and a finished quest reads as finished.
     *
     * Seeded rather than played, for the reason [seeded] gives about boosters: reaching a
     * half-finished day through the board would take three matches whose outcomes nothing here
     * controls.
     */
    @Test
    fun aPinnedRecordIsShownAsItStands() = runComposeUiTest {
        val today = questDayOf(FixedClock.DEFAULT_MILLIS)
        val drawn = DailyQuestCatalog.idsForDay(FixedClock.DEFAULT_MILLIS, CREATED_AT)
        val done = drawn.first()
        val open = drawn.last()
        val save = GameSave.new(createdAt = CREATED_AT).copy(
            quests = DailyQuests(
                day = today,
                questIds = drawn,
                progress = mapOf(done to DailyQuestCatalog.getValue(done).objective.target),
                completed = mapOf(done to FixedClock.DEFAULT_MILLIS),
            ),
        )
        val documents = seeded(save)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)

        // The badge first, because it is read from the same record and computed separately.
        onNodeWithTag(DASHBOARD_QUESTS_BADGE_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("1 / ${DailyQuestCatalog.PER_DAY}")

        openQuests()
        // A finished quest drops its counter for a word: `3 / 3` above a full bar says nothing.
        onNodeWithTag(questProgressTestTag(done)).assertTextEquals("Completed")
        onNodeWithTag(questProgressTestTag(open))
            .assertTextEquals("0 / ${DailyQuestCatalog.getValue(open).objective.target}")
    }

    /**
     * A record pinned to **yesterday** is not shown; today's derived draw is.
     *
     * The rollover as a player experiences it. `statuses` does not write, so the stale record is
     * still on disk at this point — which is the case that would render yesterday's progress if
     * the day check were missing.
     */
    @Test
    fun yesterdaysRecordDoesNotLeakIntoToday() = runComposeUiTest {
        val yesterday = questDayOf(FixedClock.DEFAULT_MILLIS - DAY_MILLIS)
        val drawn = DailyQuestCatalog.idsForDay(FixedClock.DEFAULT_MILLIS, CREATED_AT)
        val save = GameSave.new(createdAt = CREATED_AT).copy(
            quests = DailyQuests(
                day = yesterday,
                questIds = drawn,
                progress = drawn.associateWith { STALE_PROGRESS },
                completed = mapOf(drawn.first() to FixedClock.DEFAULT_MILLIS - DAY_MILLIS),
            ),
        )
        val documents = seeded(save)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openQuests()

        for (id in DailyQuestCatalog.idsForDay(FixedClock.DEFAULT_MILLIS, CREATED_AT)) {
            onNodeWithTag(questProgressTestTag(id))
                .assertTextEquals("0 / ${DailyQuestCatalog.getValue(id).objective.target}")
        }
    }

    /**
     * One real match, and the quest record on disk is stamped with today.
     *
     * The end-to-end assertion, and deliberately the weakest claim that is still worth making: it
     * asserts the day is pinned, not what advanced, because *what* advances depends on whether the
     * match was won and on which three quests the draw produced — neither of which this test
     * controls, and both of which `:core` already covers. What it does prove is that the client's
     * credit path reaches `DailyQuests` at all.
     */
    @Test
    fun aMatchPlayedThroughTheUiReachesTheQuestRecord() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        openOpponents()
        challenge()
        playOut()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).quests.day.isNotEmpty()
        }
        val stored = storedSave(documents)
        assertEquals(questDayOf(FixedClock.DEFAULT_MILLIS), stored.quests.day)
        assertEquals(DailyQuestCatalog.PER_DAY, stored.quests.questIds.size)
        assertTrue(
            stored.quests.questIds.all { DailyQuestCatalog[it] != null },
            "pinned ids that are not in the catalogue: ${stored.quests.questIds}",
        )
    }

    /**
     * A quest finished by a match is announced on the end-of-match panel.
     *
     * Deterministic without controlling the outcome, because the record's draw is **pinned**: the
     * day matches, so `rolledTo` keeps these ids, and `q-play-3` at 2 is completed by any match at
     * all — won, lost or drawn. That is the one objective whose completion does not depend on how
     * the match went, which is what makes this assertable at UI level.
     *
     * The gap it guards is a real one: before this, a quest could finish mid-match and say nothing,
     * so the only way to learn of it was to go looking.
     */
    @Test
    fun aQuestFinishedByTheMatchIsAnnouncedOnThePanel() = runComposeUiTest {
        val save = freshSave(createdAt = CREATED_AT).copy(
            quests = DailyQuests(
                day = questDayOf(FixedClock.DEFAULT_MILLIS),
                questIds = listOf(PLAY_THREE),
                progress = mapOf(PLAY_THREE to ONE_SHORT),
            ),
        )
        val documents = seeded(save)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openOpponents()
        challenge()
        playOut()

        onNodeWithTag(questRowTestTag(PLAY_THREE)).assertExists()
        assertTrue(
            PLAY_THREE in storedSave(documents).quests.completed,
            "the quest was announced and not recorded",
        )
    }

    private companion object {
        /** The one objective any match advances, whatever the result. */
        const val PLAY_THREE = "q-play-3"

        /** Two of its three, so the next match — any match — finishes it. */
        const val ONE_SHORT = 2

        /** What `newCharacter` creates with — [FixedClock] is the app's default in a test. */
        const val CREATED_AT = FixedClock.DEFAULT_MILLIS

        const val DAY_MILLIS = 86_400_000L

        /** Any non-zero count, so a leak would be visible rather than coincidentally right. */
        const val STALE_PROGRESS = 2

        fun DailyQuestCatalog.getValue(id: String) =
            this[id] ?: error("$id is not in the catalogue")
    }
}
