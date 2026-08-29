package com.tripletriad.i18n

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun theAppOwnedStringsAreTranslatedInEveryLocale() {
        for (key in StringKeys.appOwned) {
            for (locale in AppLocale.entries) {
                assertTrue(loaded.getValue(locale).isTranslated(key), "${locale.tag}/$key")
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
        //
        // Two more since, both about a deck the format does not admit: `APP_CAMPAIGN_NO_DECK`,
        // which shuts a ladder before its fee is taken, and `APP_ERROR_UNDEALABLE`, which is the
        // one `PveRefusal` that is not staleness. English and French again, so the imported gaps
        // widen by two more each.
        //
        // Three more since: `APP_AC_CAMPAIGN_BALAMB_DESC`, `_CC_DESC` and `_GS_DESC`, the
        // tournament achievements' descriptions — the labels existed already, the `_DESC` half did
        // not. English and French only, like every achievement string, so the imported gaps widen
        // by three more each.
        //
        // One fewer since: `APP_PACK_GUARANTEE` is gone. It advertised a guaranteed minimum
        // rarity from the old multi-draw booster, a promise `BoosterItem.open` stopped keeping once
        // a pack draws exactly one weighted card — see `ShopBody.packTerms`. English and French
        // only, like every app-owned string, so the imported gaps narrow by one each.
        //
        // One more since: `APP_BOOSTERS`, the shop's booster shelf header — the shop grew
        // three named shelves and only this one had no word already in the bundles
        // (`APP_BOONS` and `APP_CARDS` name the other two). English and French only, like
        // every app-owned string, so the imported gaps widen by one each.
        //
        // No change from the fifteen `STR_*_BOOSTER` overrides the shop added on top: they are
        // imported keys the `tto-*` bundles already carry, and `app-*` only wins the merge for
        // them — see `Strings.readLocale`. That is the override mechanism CLAUDE.md names, and
        // it is the reason a Square Enix bundle never has to be hand-edited to change a name.
        // Nothing above is affected by FFVIII moving from card block 2 to block 8: a bundle keys
        // its cards by `STR_FF8_CARD_<number>`, and a card's *number* within its set is exactly
        // what a block move leaves alone. Only the ids changed, and no bundle holds one.
        //
        // 602 more since: the FFXIV set's cards #154-454, `STR_FF14_CARD_154`..`_454`, plus their
        // `_DESC` counterpart in English only — the site's card detail pages carry flavour text,
        // but only in English, so `app-fr_FR.json`/`app-de_DE.json`/`app-ja_JA.json` gain the 301
        // name keys and nothing else. `CardListBody` already omits a card's description paragraph
        // when its `_DESC` key is absent for the active locale, so this widens FR's, DE's and JA's
        // gap by 301 each and leaves EN's gap alone (both halves are English-owned).
        // 73 more since, and this correction is worth reading before the next bump is written.
        //
        // The paragraph above bumped these counts by 611 for the FFXIV expansion — **derived from
        // what the change was expected to add**, not measured off the bundles afterwards. The
        // expansion also brought 75 opponents, 73 of which needed a name key, and those were
        // authored straight into all four `app-*` bundles. So the real figure was 684 and the
        // recorded one was short by exactly 73, in every locale at once.
        //
        // Two consequences to note. The gaps below are **unchanged**: a key authored in all four
        // bundles widens nobody's. And the arithmetic is why these are wrong — measure the counts
        // from a run and paste them, rather than adding up what a change ought to have done.
        //
        // +1 since: `STR_FF8_CARD_111` for Mooba, the secret FF8 card, authored in all four
        // bundles at once — so it moves every TRANSLATED_KEYS entry and UNION_KEYS by one, and
        // touches none of the gaps below.
        //
        // +1 since: `APP_MATCH_RESUME`, the roster's way back into a match the server still has
        // open — see `ResumeMatchUiTest`. App-owned, so English and French only, which is the
        // shape that moves four numbers rather than five: the two authored counts and the two
        // gaps widen, and the two authored locales' own gaps do not.
        //
        // Measured from a `:shared:desktopTest --tests "*StringsBundleTest*"` run and pasted, as
        // the correction above asks — not added up from what the change ought to have done.
        //
        // FR/DE/JA completed against the EN source: FR gains its 301 missing `_DESC` paragraphs
        // (it already had every app-owned string and every card name); DE and JA gain both their
        // 295 app-owned strings and their 301 `_DESC` paragraphs — the fallback-to-English policy
        // for app-owned strings in DE/JA is retired, hence
        // `theAppOwnedStringsAreTranslatedInEveryLocale` replacing the old fallback test.
        // `UNION_KEYS` is unchanged: every key it counts already existed in `app-en_US.json`,
        // only the other three bundles' values were filled in. The 15 `STR_*_BOOSTER` overrides
        // don't move any bundle's count — they already exist as imported keys, so DE only widens by
        // 295 + 301 = 596, not 611, on top of its APP_-string count. Measured from a
        // `:shared:desktopTest --tests "*StringsBundleTest*"` run and pasted.
        //
        // +1 to DE alone since: `RULE_SWAP_HELP`, overridden in all four `app-*` bundles to say
        // that a swapped card stays face up in the hand it moved to — see `MatchPreparation.swap`.
        // In EN, FR and JA the imported bundle already carries the key, so the override only wins
        // the merge and moves nothing, exactly as the 15 `STR_*_BOOSTER` overrides do. **The German
        // `tto-de_DE.json` has no `RULE_SWAP_HELP` at all** — that locale had been reading the
        // English sentence by fallback — so there the override adds a key rather than replacing
        // one, and DE's count and gap both move by one. `UNION_KEYS` is unchanged: three bundles
        // already had it.
        //
        // +9 to every bundle since: the title screen and the L2 lobby — `APP_TITLE_CONTINUE`,
        // `APP_TITLE_CHOOSE`, `APP_LOBBY_RESUME`, `APP_LOBBY_TODAY`, `APP_LOBBY_MORE`,
        // `APP_LOBBY_SOON`, `APP_AUCTION`, `APP_AUCTION_BLURB` and `APP_LOCKED_LEVEL`. Authored in
        // all four `app-*` bundles at once, which is the shape that moves every count by the same
        // nine and leaves all four gaps where they were. Measured from a
        // `:shared:desktopTest --tests "*StringsBundleTest*"` run and pasted.
        //
        // +23 to every bundle since: the address on an account, and getting back in without a
        // password — `APP_EMAIL` and its hint, the four confirmation strings and the code field's
        // own five, the seven the reset flow needs, and four new `APP_ERROR_*` for the refusals
        // `AccountError` gained. Authored in all four `app-*` bundles at once, which is the shape
        // that moves every count by the same 23 and leaves all four gaps where they were.
        // Measured from a `:shared:desktopTest --tests "*StringsBundleTest*"` run and pasted.
        const val UNION_KEYS = 1700

        val TRANSLATED_KEYS = mapOf(
            AppLocale.EN_US to 1696,
            AppLocale.FR_FR to 1697,
            AppLocale.DE_DE to 1657,
            AppLocale.JA_JA to 1689,
        )

        val EXPECTED_GAPS = mapOf(
            AppLocale.EN_US to 4,
            AppLocale.FR_FR to 3,
            AppLocale.DE_DE to 43,
            AppLocale.JA_JA to 11,
        )
    }
}
