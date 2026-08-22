package com.tripletriad.i18n

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringsBundleTest {
    private val loaded = AppLocale.entries.associateWith { runBlocking { loadStrings(it) } }

    @Test
    fun allFourBundlesLoad() {
        assertEquals(AppLocale.entries.size, loaded.size)
        for ((locale, strings) in loaded) {
            assertEquals(locale, strings.locale)
        }
    }

    @Test
    fun everyKeyTheUiUsesResolvesInEveryLocale() {
        val unresolved = mutableListOf<String>()
        for ((locale, strings) in loaded) {
            for (key in StringKeys.all) {
                if (!strings.has(key) || strings[key] == key) {
                    unresolved += "${locale.tag}/$key"
                }
            }
        }
        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    @Test
    fun theFallbackLocaleDefinesEveryKeyTheUiUses() {
        val fallback = loaded.getValue(AppLocale.Default)
        for (key in StringKeys.all) {
            assertTrue(fallback.isTranslated(key), "$key is absent from ${AppLocale.Default.tag}")
        }
    }

    @Test
    fun theImportedBundlesStillHoldTheirFullKeySets() {
        // Counts measured from `sources/bin/datas/locales/` after duplicate resolution, plus what
        // this port has added on top. A change here means a re-import changed the data, or content
        // was authored — either is fine, but it should be deliberate.
        for ((locale, expected) in TRANSLATED_KEYS) {
            assertEquals(expected, loaded.getValue(locale).translatedKeys.size, locale.tag)
        }
    }

    @Test
    fun theTranslationGapsAreTheKnownOnes() {
        val union = AppLocale.entries.flatMap { loaded.getValue(it).translatedKeys }.toSet()
        assertEquals(UNION_KEYS, union.size, "keys across all four bundles")
        for ((locale, expected) in TRANSLATED_KEYS) {
            val missing = union.size - expected
            assertEquals(EXPECTED_GAPS.getValue(locale), missing, "${locale.tag} missing count")
        }
    }

    @Test
    fun theAppOwnedStringsAreTranslatedInEnglishAndFrenchAndFallBackElsewhere() {
        for (key in StringKeys.appOwned) {
            for (locale in listOf(AppLocale.EN_US, AppLocale.FR_FR)) {
                assertTrue(loaded.getValue(locale).isTranslated(key), "${locale.tag}/$key")
            }
            for (locale in listOf(AppLocale.DE_DE, AppLocale.JA_JA)) {
                assertFalse(
                    loaded.getValue(locale).isTranslated(key),
                    "${locale.tag}/$key is translated now — update this test and the KDoc",
                )
                assertEquals(
                    loaded.getValue(AppLocale.EN_US)[key],
                    loaded.getValue(locale)[key],
                    "${locale.tag}/$key should fall through to English",
                )
            }
        }
    }

    @Test
    fun theBundlesActuallyDiffer() {
        val english = loaded.getValue(AppLocale.EN_US)
        val french = loaded.getValue(AppLocale.FR_FR)
        assertEquals("Continue", english[CONTINUE])
        assertEquals("Continuer", french[CONTINUE])
        assertEquals("chargement des cartes…", french[StringKeys.LOADING_CARDS])
        assertEquals("続行", loaded.getValue(AppLocale.JA_JA)[CONTINUE])
        assertEquals("Weiter", loaded.getValue(AppLocale.DE_DE)[CONTINUE])
    }

    @Test
    fun theDuplicatedKeyKeepsTheValueTheOriginalDisplayed() {
        assertEquals("Defy", loaded.getValue(AppLocale.EN_US)[REGISTER_MATCH])
        assertEquals("Défier", loaded.getValue(AppLocale.FR_FR)[REGISTER_MATCH])
    }

    private companion object {
        const val CONTINUE = "STR_CONTINUE"
        const val REGISTER_MATCH = "STR_REGISTER_MATCH"

        // Fourteen keys up on the count before the tournament work: `APP_RANDOM_OPPONENT`,
        // `APP_CAMPAIGN_FINAL_REWARD`, `APP_CAMPAIGN_BALAMB`; the bilan's four
        // (`APP_CAMPAIGN_RESULTS`, `_COMPLETE`, `_ELIMINATED`, `_NOT_REACHED`);
        // `APP_CAMPAIGN_LOCKED` and `APP_CAMPAIGN_ENTERED_TODAY`, the two reasons a ladder is
        // shut; the three achievements' labels, `APP_AC_CAMPAIGN_*`; and the two Ishtar needed,
        // `APP_NPC_ISHTAR` and `APP_OPPONENTS_UNEARNED`.
        //
        // `APP_NPC_ISHTAR` is a *name key*, reached through `Npc.nameKey` and not through
        // [StringKeys] — the same way every `STR_NPC_*` is. It exists because the FFVIII Queen of
        // Cards had to stop sharing the FFXIV one's key once they became two opponents.
        //
        // The achievement labels are **not** in [StringKeys] and are not meant to be: a label is
        // reached through `AchievementCatalog`, the same way the ported `STR_Triple_Team_*` are,
        // so the bundle is where they live and this count is what holds them to it.
        //
        // Authored in English and French only, like the rest of the app-owned strings, so the two
        // imported bundles' gaps each widen by fourteen.
        const val UNION_KEYS = 982

        val TRANSLATED_KEYS = mapOf(
            AppLocale.EN_US to 978,
            AppLocale.FR_FR to 979,
            AppLocale.DE_DE to 653,
            AppLocale.JA_JA to 686,
        )

        val EXPECTED_GAPS = mapOf(
            AppLocale.EN_US to 4,
            AppLocale.FR_FR to 3,
            // 44 imported keys short, plus all 285 app-owned; and 11 short, plus the 285.
            AppLocale.DE_DE to 329,
            AppLocale.JA_JA to 296,
        )
    }
}
