package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Format
import com.tripletriad.data.FormatCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameSave
import com.tripletriad.net.PvpClient
import com.tripletriad.ui.theme.TripleTriadTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TextScalingTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theTableEditorStaysUsableAtDoubleTextSize() = tableEditor(fontScale = 2f) {
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun itsControlsAreStillReachableAtDoubleTextSize() = tableEditor(fontScale = 2f) {
        onNodeWithTag(PVP_TABLE_ROULETTE_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun theSameControlsAreReachableAtOrdinarySize() = tableEditor(fontScale = 1f) {
        onNodeWithTag(PVP_TABLE_OPEN_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(PVP_TABLE_ROULETTE_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(PVP_TABLE_MGP_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun theSettingsScreenStaysUsableAtDoubleTextSize() = app(fontScale = 2f) {
        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPTIONS_BACKGROUND_VOLUME_TEST_TAG) }

        onNodeWithTag(optionsLanguageTestTag(AppLocale.EN_US)).assertIsDisplayed()
        onNodeWithTag(OPTIONS_BACKGROUND_VOLUME_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(OPTIONS_NOISE_VOLUME_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun theDashboardStaysUsableAtDoubleTextSize() = app(fontScale = 2f) {
        newCharacter()

        onNodeWithTag(DASHBOARD_PLAY_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(navTestTag("home")).assertIsDisplayed()
    }

    private fun app(
        fontScale: Float,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(modifier = Modifier.size(PHONE_WIDTH, PHONE_HEIGHT)) {
                    TestApp(store = settingsFor(AppLocale.EN_US))
                }
            }
        }
        block()
    }

    private fun tableEditor(
        fontScale: Float,
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val engine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val session = PvpSession(PvpClient(http, { "http://server" }), tokenOf = { "token" })

        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalStrings provides strings,
                // The player's text size, and nothing else: the density stays put so this is a
                // test about type scaling rather than about a differently sized screen.
                LocalDensity provides Density(base.density, fontScale),
            ) {
                TripleTriadTheme {
                    // A small handset. The default test surface is roomy enough to hide exactly
                    // the problem this file is looking for.
                    Box(modifier = Modifier.size(PHONE_WIDTH, PHONE_HEIGHT)) {
                        PvpTableScreen(
                            profile = GameSave.new(username = "Sigfrid", createdAt = 0L),
                            catalog = pvpCards,
                            formats = FormatCatalog(listOf(format)),
                            session = session,
                            onOpened = {},
                            onBack = {},
                        )
                    }
                }
            }
        }
        block()
    }

    private val format = Format(
        id = "test-format",
        nameKey = "STR_TEST",
        blocks = listOf(1),
        rules = listOf(
            "RULE_SAME",
            "RULE_PLUS",
            "RULE_ALL_OPEN",
            "RULE_THREE_OPEN",
            "RULE_ROULETTE",
        ),
    )

    private companion object {
        val PHONE_WIDTH = 360.dp
        val PHONE_HEIGHT = 640.dp
    }
}
