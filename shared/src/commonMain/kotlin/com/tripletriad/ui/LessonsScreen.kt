package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

const val LESSONS_LIST_TEST_TAG: String = "lessons-list"

fun lessonRowTestTag(lesson: Int): String = "lesson-row-$lesson"

fun lessonDoneTestTag(lesson: Int): String = "lesson-done-$lesson"

const val LESSONS_BLURB_TEST_TAG: String = "lessons-blurb"
const val LESSONS_ALL_DONE_TEST_TAG: String = "lessons-all-done"

/** Where the course is picked up again: the next lesson, whole, at the top of its own list. */
const val LESSONS_RESUME_TEST_TAG: String = "lessons-resume"

const val LESSONS_RESUME_PLAY_TEST_TAG: String = "lessons-resume-play"

/** A chapter's heading, and the count beside it. See [LessonChapter]. */
fun lessonChapterTestTag(slug: String): String = "lesson-chapter-$slug"

fun lessonChapterCountTestTag(slug: String): String = "lesson-chapter-count-$slug"

/**
 * The course: where to pick it up, and how much of it there is.
 *
 * @param onRule opens the rule book at that entry — see [RulePill]. The one link the two screens
 *   were missing, and it costs a key both sides already hold.
 */
@Composable
internal fun LessonsScreen(
    profile: GameSave,
    done: Int,
    onPlay: (Int) -> Unit,
    onRule: (String) -> Unit,
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
        //
        // Two lines at most now that a resume card stands under it. Cut by the layout rather than
        // by splitting the string on a full stop: `。` ends a Japanese sentence and `.` does not,
        // so a first-sentence rule written here would silently keep the whole paragraph in one
        // locale and cut mid-word in another.
        Text(
            text = strings[if (finished) StringKeys.LESSONS_ALL_DONE else StringKeys.LESSONS_BLURB],
            color = if (finished) {
                LocalTtoColors.current.positive
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED)
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .testTag(if (finished) LESSONS_ALL_DONE_TEST_TAG else LESSONS_BLURB_TEST_TAG)
                .padding(bottom = SpaceMd),
        )

        LazyColumn(
            modifier = Modifier.testTag(LESSONS_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SpaceSm),
        ) {
            // Above the chapters rather than inside the one it belongs to: a course is resumed
            // from wherever the player left the screen, and a card that moved down as the course
            // advanced would be somewhere else every visit.
            if (!finished) {
                item(key = "resume") {
                    ResumeCard(lesson = done, onPlay = onPlay, onRule = onRule)
                }
            }

            for (chapter in LessonChapter.entries) {
                item(key = "chapter-${chapter.slug}") {
                    ChapterHeader(chapter = chapter, done = done)
                }

                items(
                    chapter.lessons.toList(),
                    key = { index -> TUTORIAL_COURSE[index].titleKey },
                ) { index ->
                    LessonRow(
                        index = index,
                        lesson = TUTORIAL_COURSE[index],
                        isDone = index < done,
                        // The lesson to resume at, highlighted the way the deck list marks the
                        // deck in play: a course put down halfway should say where it was put
                        // down. It is now said twice — here and in the card above — because the
                        // card is a shortcut and the list is where the course actually lives.
                        isNext = index == done,
                        onPlay = { onPlay(index) },
                        onRule = onRule,
                    )
                }
            }
        }
    }
}

/**
 * The next lesson, whole.
 *
 * A tinted background was the only thing marking it before, and on twelve near-identical rows a
 * tint is a hint rather than an invitation. What it teaches is the rule book's own sentence for
 * the first rule it covers — the text is already written, already translated, and already the
 * answer to "what am I about to learn".
 */
@Composable
private fun ResumeCard(lesson: Int, onPlay: (Int) -> Unit, onRule: (String) -> Unit) {
    val strings = LocalStrings.current
    val course = TUTORIAL_COURSE[lesson]
    val help = course.ruleKeys.firstOrNull()?.let { "${it}_HELP" }

    Column(
        modifier = Modifier
            .testTag(LESSONS_RESUME_TEST_TAG)
            .fillMaxWidth()
            .rowSurface(selected = true)
            .padding(SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            text = strings[StringKeys.LESSON_NEXT],
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${lesson + 1}. ${strings[course.titleKey]}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        // Absent rather than a key on screen where a bundle has no sentence — the four locales do
        // not describe the same set of rules. `RulesStrip` makes the same test.
        if (help != null && strings.has(help)) {
            Text(
                // The French bundle sets its qualifiers in italics — see [markup].
                text = markup(strings[help]),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RulePills(ruleKeys = course.ruleKeys, onRule = onRule)
        WideButton(
            label = strings[StringKeys.PLAY],
            tag = LESSONS_RESUME_PLAY_TEST_TAG,
            onClick = { onPlay(lesson) },
        )
    }
}

/**
 * A chapter's name, and how far into it the player is.
 *
 * The bar is per chapter and there is no longer a global one. Twelve is a number a player has no
 * feeling for; "three of five captures" is a position in something they can see the end of.
 */
@Composable
private fun ChapterHeader(chapter: LessonChapter, done: Int) {
    val strings = LocalStrings.current
    val total = chapter.lessons.count()
    val reached = chapter.doneIn(done)

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = SpaceMd),
        verticalArrangement = Arrangement.spacedBy(SpaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings[chapter.labelKey],
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .testTag(lessonChapterTestTag(chapter.slug))
                    .weight(1f),
            )
            Text(
                text = "$reached / $total",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(lessonChapterCountTestTag(chapter.slug)),
            )
        }
        Meter(
            fraction = reached.toFloat() / total,
            modifier = Modifier.width(MeterWidth),
        )
    }
}

@Composable
private fun LessonRow(
    index: Int,
    lesson: TutorialLesson,
    isDone: Boolean,
    isNext: Boolean,
    onPlay: () -> Unit,
    onRule: (String) -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(lessonRowTestTag(index))
            .fillMaxWidth()
            .rowSurface(selected = isNext)
            .ttoClickable(onClick = onPlay)
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
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            Text(
                text = strings[lesson.titleKey],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RulePills(ruleKeys = lesson.ruleKeys, onRule = onRule)
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

/**
 * The rules a lesson teaches, each one a way into the book.
 *
 * A `Row` rather than a `FlowRow`: three pills is the most any lesson carries (the exam), and a
 * row that wrapped would change a lesson's height with the length of a translation. They are
 * clipped instead, which loses a word off the third pill in German and keeps every row the height
 * the list was measured at.
 */
@Composable
private fun RulePills(ruleKeys: List<String>, onRule: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (key in ruleKeys) {
            RulePill(ruleKey = key, onOpen = onRule)
        }
    }
}

private val TickSize = 18.dp

/**
 * How wide a chapter's bar is drawn.
 *
 * Short and left-aligned rather than the full width the quest meters use: a bar that spanned the
 * screen under every heading would read as a section divider, and there are three of them on one
 * list.
 */
private val MeterWidth = 96.dp
