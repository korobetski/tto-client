package com.tripletriad.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tripletriad.data.SaveRepository
import com.tripletriad.log.CrashLog
import com.tripletriad.log.Log
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerStores
import com.tripletriad.net.SessionStore
import com.tripletriad.net.TicketStore
import com.tripletriad.net.TranscriptQueue
import com.tripletriad.net.serverConnection
import com.tripletriad.net.serverEntries
import com.tripletriad.ui.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.system.exitProcess

fun main() {
    installCrashLog()
    val settings = DesktopSettingsStore()
    // `SaveRepository.COLLECTION` rather than the literal "saves": the shared module owns the
    // directory name, so the two hosts cannot drift apart on where a profile lives.
    val documents = DesktopDocumentStore(SaveRepository.COLLECTION)
    val server = buildServerConnection()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Triple Triad",
            state = rememberWindowState(size = DpSize(560.dp, 640.dp)),
        ) {
            App(
                store = settings,
                documents = documents,
                clock = JvmClock,
                onQuit = ::exitApplication,
                server = server,
            )
        }
    }
    // `application {}` returning is not the process ending. Compose shuts the window down and the
    // main thread falls out of the block, but the JVM lives until its last non-daemon thread does
    // — and this app has some: the HTTP client's engine keeps a pool alive whether or not a request
    // is in flight, and it outlives the window that was using it. Quit left an invisible process
    // behind. Nothing is lost by ending it here: every save goes through `SaveRepository` at the
    // point of the change, so anything that had to reach disk did so before the window closed.
    exitProcess(0)
}

private fun installCrashLog() {
    Log.install(
        CrashLog(
            store = DesktopDocumentStore(CrashLog.COLLECTION),
            clock = JvmClock,
            scope = CoroutineScope(Dispatchers.IO),
        ),
    )
}

private fun buildServerConnection(): ServerConnection? {
    val configured = System.getProperty("tto.servers")
        ?: System.getenv("TTO_SERVERS")
        ?: DEFAULT_SERVERS
    val servers = serverEntries(configured)
    if (servers.isEmpty()) {
        Log.i(TAG) { "no server configured; this build plays offline only" }
        return null
    }
    Log.i(TAG) { "${servers.size} server(s): ${servers.joinToString { it.baseUrl }}" }
    return serverConnection(
        stores = ServerStores(
            queue = DesktopDocumentStore(TranscriptQueue.COLLECTION),
            session = DesktopDocumentStore(SessionStore.COLLECTION),
            directory = DesktopDocumentStore(ServerDirectory.COLLECTION),
            tickets = DesktopDocumentStore(TicketStore.COLLECTION),
        ),
        servers = servers,
        clock = JvmClock,
    )
}

private const val DEFAULT_SERVERS =
    "Local=http://127.0.0.1:8080, Moebius=https://tto.moebiuscore.fr"

private const val TAG = "Host"
