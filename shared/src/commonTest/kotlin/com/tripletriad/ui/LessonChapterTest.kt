package com.tripletriad.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The course's three chapters against the course itself.
 *
 * The chapters are written as three starting indices, so the one thing that can go wrong is a
 * lesson falling between two of them — or a chapter claiming a lesson that is not there after an
 * edit to `TUTORIAL_COURSE`. Neither shows on screen: the missing lesson simply is not drawn.
 */
class LessonChapterTest {
    @Test
    fun theChaptersCoverTheCourseExactlyOnce() {
        val covered = LessonChapter.entries.flatMap { it.lessons.toList() }

        assertEquals(TUTORIAL_COURSE.indices.toList(), covered)
    }

    @Test
    fun theChaptersAreReadInTheOrderTheCourseIsPlayed() {
        val firsts = LessonChapter.entries.map { it.first }

        assertEquals(firsts.sorted(), firsts)
        assertEquals(0, firsts.first(), "the course starts at its first lesson")
    }

    @Test
    fun noChapterIsEmpty() {
        for (chapter in LessonChapter.entries) {
            assertTrue(chapter.lessons.count() > 0, "$chapter holds no lesson")
        }
    }

    @Test
    fun aChapterNotReachedYetIsAtZeroRatherThanBelowIt() {
        val last = LessonChapter.entries.last()

        assertEquals(0, last.doneIn(0))
        assertEquals(0, last.doneIn(last.first))
    }

    @Test
    fun aChapterLeftBehindIsFullRatherThanOverfull() {
        for (chapter in LessonChapter.entries) {
            assertEquals(
                chapter.lessons.count(),
                chapter.doneIn(TUTORIAL_COURSE.size),
                "$chapter reads past its own end on a finished course",
            )
        }
    }

    @Test
    fun aChapterInProgressCountsFromItsOwnStart() {
        val captures = LessonChapter.CAPTURES

        assertEquals(2, captures.doneIn(captures.first + 2))
    }

    @Test
    fun everyChapterNamesItselfWithItsOwnSlug() {
        val slugs = LessonChapter.entries.map { it.slug }

        assertEquals(slugs.size, slugs.toSet().size, "two chapters share a test tag: $slugs")
    }
}
