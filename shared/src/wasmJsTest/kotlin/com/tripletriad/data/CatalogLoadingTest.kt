package com.tripletriad.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The five catalogues, read the way the browser build will read them.
 *
 * On the other hosts `Res.readBytes` opens a file or an asset; here it is a `fetch` against the
 * page's origin, which is a different path through Compose's resource library and one the common
 * tests never take. A match cannot start without these, so the resource half of step 3.2 is checked
 * before there is an application to find out with.
 */
class CatalogLoadingTest {
    @Test
    fun everyCatalogueLoadsUnderWasm() = runTest {
        assertTrue(loadCardCatalog().cards.isNotEmpty(), "no cards")
        assertTrue(loadNpcCatalog().npcs.isNotEmpty(), "no opponents")
        assertTrue(loadStarterCatalog().starters.isNotEmpty(), "no starters")
        loadCampaignCatalog()
        loadFormatCatalog()
    }
}
