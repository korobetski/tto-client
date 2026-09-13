package com.tripletriad.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnownedCardsTest {
    @Test
    fun theShippedModeIsTheQuestionMark() {
        assertEquals(UnownedCards.UNKNOWN, UnownedCards.Default)
    }

    @Test
    fun everyModeHasItsOwnTagAndTheTagsRoundTrip() {
        val tags = UnownedCards.entries.map { it.tag }
        assertEquals(tags.distinct().size, tags.size, "two modes share a tag: $tags")
        for (mode in UnownedCards.entries) {
            assertEquals(mode, UnownedCards.forTag(mode.tag), "${mode.tag} did not round-trip")
        }
    }

    @Test
    fun aTagThisBuildDoesNotHaveIsNotInvented() {
        assertNull(UnownedCards.forTag("blurred"))
        assertNull(UnownedCards.forTag("UNKNOWN"), "the tag is the stored form, not the enum name")
    }

    @Test
    fun everyModeNamesItselfThroughABundleKey() {
        // Proven to resolve by `DerivedKeysTest`; this only proves the keys are distinct.
        val keys = UnownedCards.entries.map { it.labelKey }
        assertEquals(keys.distinct().size, keys.size, "two modes share a label: $keys")
        assertTrue(keys.all { it.startsWith("APP_UNOWNED_") }, "unexpected label keys: $keys")
    }
}
