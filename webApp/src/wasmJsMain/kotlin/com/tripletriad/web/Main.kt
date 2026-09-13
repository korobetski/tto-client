package com.tripletriad.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.tripletriad.data.MatchHistoryRepository
import com.tripletriad.data.SaveRepository
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerStores
import com.tripletriad.net.SessionStore
import com.tripletriad.net.TicketStore
import com.tripletriad.net.TranscriptQueue
import com.tripletriad.net.serverConnection
import com.tripletriad.storage.BrowserDocumentStore
import com.tripletriad.ui.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val server = buildServerConnection()
    ComposeViewport(viewportContainerId = VIEWPORT_ID) {
        App(
            store = LocalSettingsStore(BrowserDocumentStore(SETTINGS_COLLECTION)),
            documents = BrowserDocumentStore(SaveRepository.COLLECTION),
            history = BrowserDocumentStore(MatchHistoryRepository.COLLECTION),
            clock = BrowserClock,
            // No `onQuit`: a page is closed by its tab, and the title screen has nothing to end.
            server = server,
        )
    }
}

/*
 * One server, and it is this page's own origin.
 *
 * The desktop's list of servers is a choice for whoever launches the binary. Here it is not a
 * choice at all: Caddy proxies the game's API routes on the host that serves the bundle, so the
 * page talks to the one server that delivered it and never makes a cross-origin request —
 * `tto-server/docs/web-platform.md` § The shape of it. A second entry could only name another
 * origin, which the server's missing CORS configuration refuses by design.
 *
 * ### Where the session token lives
 *
 * `localStorage`, beside everything else, and this is the decision the platform document left to
 * this module. The alternative was memory, which signs the player out on every reload — and a
 * reload is what a browser does on a flaky connection, on a phone that backgrounded the tab, on a
 * new release of this very bundle.
 *
 * What it costs is that any script running on the page can read the token. On this host that is a
 * short list, which is what makes the trade acceptable rather than merely convenient:
 *
 * - the host serves this bundle and the API, nothing else — no editorial content, no third-party
 *   script, which is why the game did not go under the portal's origin;
 * - the page is served under `script-src 'self'`, so an injected `<script>` does not run;
 * - Compose draws into a canvas, so nothing the server sends — a player's name, a chat line — is
 *   ever parsed as HTML.
 *
 * If one of those three stops being true, the fix is here: hand `session` an
 * `InMemoryDocumentStore` and accept the sign-in on reload.
 */
private fun buildServerConnection(): ServerConnection =
    serverConnection(
        stores = ServerStores(
            queue = BrowserDocumentStore(TranscriptQueue.COLLECTION),
            session = BrowserDocumentStore(SessionStore.COLLECTION),
            directory = BrowserDocumentStore(ServerDirectory.COLLECTION),
            tickets = BrowserDocumentStore(TicketStore.COLLECTION),
        ),
        servers = listOf(ServerEntry.of(baseUrl = pageOrigin(), label = SERVER_LABEL)),
        clock = BrowserClock,
    )

private fun pageOrigin(): String = js("window.location.origin")

/** The element `index.html` gives the game. */
private const val VIEWPORT_ID = "tto"

/** Where the desktop writes `UserSettings.json`, the browser writes one document. */
private const val SETTINGS_COLLECTION = "settings"

private const val SERVER_LABEL = "Moebius"
