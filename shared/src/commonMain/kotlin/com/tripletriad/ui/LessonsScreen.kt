package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import com.tripletriad.ui.theme.LocalTtoColors

/** The course, as a list. */
const val LESSONS_LIST_TEST_TAG: String = "lessons-list"

/** `lesson-row-<index>` — one lesson, tapped to play it. */
fun lessonRowTestTag(lesson: Int): String = "lesson-row-$lesson"

/** Present on a lesson the player has finished. */
fun lessonDoneTestTag(lesson: Int): String = "lesson-done-$lesson"

/**
 * The line above the list, under whichever of its two readings applies.
 *
 * Two tags on one `Text` rather than one, because what a test needs to know is *which* sentence is
 * up: a single tag would be present either way and would assert only that a paragraph exists.
 */
const val LESSONS_BLURB_TEST_TAG: String = "lessons-blurb"
const val LESSONS_ALL_DONE_TEST_TAG: String = "lessons-all-done"

/**
 * The course — every lesson, in order, with what each one teaches and how far the player has got.
 *
 * ### Why the course needed a screen of its own
 *
 * There was one lesson and it was a row on the opponent list, which is where `PVEScreen.as:79`
 * drew it: a bare `tt_tuto` texture above the opponents. That is the right place for *one* thing
 * called "Tutorial" and the wrong place for eight — a course has an order, a place you are up to,
 * and lessons worth going back to, none of which a single row can say. It is also not an opponent,
 * which is what everything else on that list is.
 *
 * So it lives on the dashboard now, beside the rule book it ends at, and the opponent list has lost
 * its row. That keeps [Screen.up] a function: a screen reachable from two places with different
 * back destinations is the case `Screen`'s own KDoc names as the point to stop using an enum, and
 * one entry point costs nothing here.
 *
 * ### Nothing is locked
 *
 * Progress is shown, not enforced. The lessons are ordered so that none needs anything a later one
 * teaches — Combo is played under Same, the pair lesson after each of its halves — but a player who
 * already knows Plus and wants only the Combo lesson is not somebody to argue with, and a locked
 * row would be arguing. What [done] buys is a course you can put down and pick up, which is the
 * thing twelve lessons need and one did not.
 *
 * @param done how many lessons have been finished, so lesson [done] is the one to resume at.
 */
@Composable
internal fun LessonsScreen(
    profile: GameSave,
    done: Int,
    onPlay: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    val finished = done >= TUTORIAL_COURSE.size

    CharacterScaffold(profile = profile, title = strings[StringKeys.LESSONS], onBack = onBack) {
        // The standing blurb explains what the course is and what it costs; once there is nothing
        // left to explain it gives way to a send-off. A player who has finished twelve lessons is
        // still being told "play them in any order, as often as you like", which is an instruction
        // for somebody about to start.
        //
        // In the blurb's own place rather than added above it: this is the same sentence at a
        // different point in the course, and a screen that grew a second paragraph on completion
        // would push the first row off a phone as a reward for finishing.
        Text(
            text = strings[if (finished) StringKeys.LESSONS_ALL_DONE else StringKeys.LESSONS_BLURB],
            color = if (finished) {
                LocalTtoColors.current.positive
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED)
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .testTag(if (finished) LESSONS_ALL_DONE_TEST_TAG else LESSONS_BLURB_TEST_TAG)
                .padding(bottom = SpaceMd),
        )

        LazyColumn(
            modifier = Modifier.testTag(LESSONS_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            itemsIndexed(TUTORIAL_COURSE, key = { _, lesson -> lesson.titleKey }) { index, lesson ->
                LessonRow(
                    index = index,
                    lesson = lesson,
                    isDone = index < done,
                    // The lesson to resume at, highlighted the way the deck list marks the deck in
                    // play: a course put down halfway should say where it was put down.
                    isNext = index == done,
                    onClick = { onPlay(index) },
                )
            }
        }
    }
}

/**
 * One lesson: its number, what it is called, what it teaches, and whether it is done.
 *
 * The rules are named from their own AS3 constants, which are also i18n keys — the same trick the
 * opponent list and the quest list use, and the reason this row reads in German and Japanese while
 * the title above it falls back to English.
 */
@Composable
private fun LessonRow(
    index: Int,
    lesson: TutorialLesson,
    isDone: Boolean,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(lessonRowTestTag(index))
            .fillMaxWidth()
            .rowSurface(selected = isNext)
            .ttoClickable(onClick = onClick)
            .padding(SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        Text(
            text = "${index + 1}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = strings[lesson.titleKey],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = lesson.ruleKeys.joinToString(DOT_SEPARATOR) { strings[it] },
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isDone) {
            Icon(
                imageVector = TtoIcons.Done,
                // The row already names the lesson; a tick announced separately would have a
                // screen reader read "done" with nothing to attach it to. The label goes on the
                // row instead, through `ttoClickable`'s own semantics.
                contentDescription = strings[StringKeys.LESSON_DONE],
                tint = LocalTtoColors.current.positive,
                modifier = Modifier.testTag(lessonDoneTestTag(index)).size(TickSize),
            )
        }
    }
}

private val TickSize = 18.dp
