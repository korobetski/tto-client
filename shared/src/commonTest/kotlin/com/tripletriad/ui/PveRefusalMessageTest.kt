package com.tripletriad.ui

import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.net.AccountResult
import com.tripletriad.protocol.PveRefusal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which sentence each `PveRefusal` reaches the player as.
 *
 * With empty bundles, so a lookup answers its own key: what is asserted is the choice of sentence,
 * not its wording.
 */
class PveRefusalMessageTest {
    private val strings = Strings(AppLocale.EN_US, emptyMap(), emptyMap())

    private fun said(code: PveRefusal): String =
        AccountResult.RefusedPve(code, detail = "").message(strings)

    /**
     * A catalogue miss says the server is behind rather than that the board is.
     *
     * The 2026-09-21 case: thirty-one opponents the server had never been given, each refused
     * `NO_SUCH_OPPONENT`, each shown as "the match has moved on".
     */
    @Test
    fun aCatalogueMissBlamesTheServerNotTheBoard() {
        assertEquals(StringKeys.ERROR_NOT_ON_SERVER, said(PveRefusal.NO_SUCH_OPPONENT))
        assertEquals(StringKeys.ERROR_NOT_ON_SERVER, said(PveRefusal.NO_SUCH_FORMAT))
    }

    @Test
    fun aDeckTheFormatRefusesIsSaidToBeTheDeck() {
        assertEquals(StringKeys.ERROR_UNDEALABLE, said(PveRefusal.UNDEALABLE))
    }

    @Test
    fun everyOtherRefusalIsAStaleBoard() {
        val stale = PveRefusal.entries - setOf(
            PveRefusal.NO_SUCH_OPPONENT,
            PveRefusal.NO_SUCH_FORMAT,
            PveRefusal.UNDEALABLE,
        )
        for (code in stale) {
            assertEquals(StringKeys.ERROR_STALE_MATCH, said(code), code.name)
        }
    }
}
