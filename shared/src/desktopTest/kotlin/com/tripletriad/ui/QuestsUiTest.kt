package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.DailyQuestCatalog
import com.tripletriad.model.DailyQuests
import com.tripletriad.model.GameSave
import com.tripletriad.model.questDayOf
import com.tripletriad.time.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QuestsUiTest {
    private val stub = PveStubServer()

    private fun ComposeUiTest.openQuests() {
        openFromDashboard(DASHBOARD_QUESTS_TEST_TAG, QUESTS_LIST_TEST_TAG)
    }

    @Test
    fun aCharacterWhoHasNotPlayedSeesTodaysQuestsAtZero() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
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

    @Test
    fun theDashboardBadgeCountsWhatIsFinished() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        onNodeWithTag(DASHBOARD_QUESTS_BADGE_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("0 / ${DailyQuestCatalog.PER_DAY}")
    }

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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
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
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openQuests()

        for (id in DailyQuestCatalog.idsForDay(FixedClock.DEFAULT_MILLIS, CREATED_AT)) {
            onNodeWithTag(questProgressTestTag(id))
                .assertTextEquals("0 / ${DailyQuestCatalog.getValue(id).objective.target}")
        }
    }

    @Test
    fun aMatchPlayedThroughTheUiReachesTheQuestRecord() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = stub.connection) }
        openDashboard()
        openOpponents()
        challenge()
        playOut()

        // The referee's profile. Crediting a match is one write, on the server, and the day's
        // quests are pinned by the same call that pays it — see `MatchRewards.credit`.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            stub.player.save.quests.day.isNotEmpty()
        }
        val stored = stub.player.save
        assertEquals(questDayOf(FixedClock.DEFAULT_MILLIS), stored.quests.day)
        assertEquals(DailyQuestCatalog.PER_DAY, stored.quests.questIds.size)
        assertTrue(
            stored.quests.questIds.all { DailyQuestCatalog[it] != null },
            "pinned ids that are not in the catalogue: ${stored.quests.questIds}",
        )
    }

    @Test
    fun aQuestFinishedByTheMatchIsAnnouncedOnThePanel() = runComposeUiTest {
        val save = freshSave(createdAt = CREATED_AT).copy(
            quests = DailyQuests(
                day = questDayOf(FixedClock.DEFAULT_MILLIS),
                questIds = listOf(PLAY_THREE),
                progress = mapOf(PLAY_THREE to ONE_SHORT),
            ),
        )
        // Seeded on the **server**, because that is where a profile lives now: the quest is one
        // match short of done, and the match that finishes it is refereed.
        val server = PveStubServer(save = save)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), server = server.connection) }
        openDashboard()
        openOpponents()
        challenge()
        playOut()

        onNodeWithTag(questRowTestTag(PLAY_THREE)).assertExists()
        assertTrue(
            PLAY_THREE in server.player.save.quests.completed,
            "the quest was announced and not recorded",
        )
    }

    @Test
    fun theHeaderNamesTheDayAndTheReset() = runComposeUiTest {
        setContent { TestApp(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openQuests()

        onNodeWithTag(QUESTS_RESET_TEST_TAG)
            .assertTextContains(questDayOf(FixedClock.DEFAULT_MILLIS), substring = true)
    }

    @Test
    fun aRecordOfUnknownQuestsReadsAsEmpty() = runComposeUiTest {
        val save = GameSave.new(createdAt = CREATED_AT).copy(
            quests = DailyQuests(
                day = questDayOf(FixedClock.DEFAULT_MILLIS),
                questIds = listOf("q-from-a-later-build"),
            ),
        )
        val documents = seeded(save)
        setContent { TestApp(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_QUESTS_TEST_TAG, QUESTS_NONE_TEST_TAG)

        onNodeWithTag(QUESTS_NONE_TEST_TAG).assertExists()
        onNodeWithTag(QUESTS_LIST_TEST_TAG).assertDoesNotExist()
    }

    private companion object {
        const val PLAY_THREE = "q-play-3"

        const val ONE_SHORT = 2

        const val CREATED_AT = FixedClock.DEFAULT_MILLIS

        const val DAY_MILLIS = 86_400_000L

        const val STALE_PROGRESS = 2

        fun DailyQuestCatalog.getValue(id: String) =
            this[id] ?: error("$id is not in the catalogue")
    }
}
