package com.tripletriad.android

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tripletriad.data.MatchHistoryRepository
import com.tripletriad.data.SaveRepository
import com.tripletriad.log.Log
import androidx.lifecycle.lifecycleScope
import com.tripletriad.log.CrashLog
import com.tripletriad.log.LogLevel
import com.tripletriad.log.LogSink
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerStores
import com.tripletriad.net.SessionStore
import com.tripletriad.net.TicketStore
import com.tripletriad.net.TranscriptQueue
import com.tripletriad.net.serverConnection
import com.tripletriad.net.serverEntries
import com.tripletriad.ui.App
import kotlin.system.exitProcess
import android.util.Log as AndroidLog

class MainActivity : ComponentActivity() {
    private lateinit var audio: AndroidAudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullScreen()
        installLogcatSink()
        // The store is built here because this is where the `Context` is. `:shared` deliberately
        // has no platform file access of its own — see `SettingsStore`.
        val settings = AndroidSettingsStore(applicationContext)
        // `SaveRepository.COLLECTION` rather than the literal "saves": the shared module owns the
        // directory name, so the two hosts cannot drift apart on where a profile lives.
        val documents = AndroidDocumentStore(applicationContext, SaveRepository.COLLECTION)
        // Its own directory, for the reason the shared module gives on `App`'s parameter: a store
        // is a flat namespace and the character picker enumerates the whole of the one it is
        // handed.
        val history = AndroidDocumentStore(
            applicationContext,
            MatchHistoryRepository.COLLECTION,
        )
        audio = AndroidAudioPlayer(applicationContext)
        askToNotify()
        val server = buildServerConnection()
        setContent {
            App(
                store = settings,
                documents = documents,
                history = history,
                clock = AndroidClock,
                audio = audio,
                notifier = AndroidNotifier(applicationContext),
                onQuit = ::quit,
                server = server,
            )
        }
    }

    /**
     * Asks for the notification permission once, at launch, and does nothing with the answer.
     *
     * Nothing, because there is nothing to do with it: `AndroidNotifier` checks the grant before
     * every note it posts, so a refusal is already handled, and a callback here would only be a
     * second place that could disagree with the first. Below API 33 the permission does not exist
     * and the request is a no-op the framework answers immediately.
     */
    private fun askToNotify() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            Log.i(TAG) { "notifications allowed: $allowed" }
        }.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun quit() {
        finishAndRemoveTask()
        exitProcess(0)
    }

    // Named apart from the `serverConnection` it calls: a member function shadows a top-level one
    // of the same name outright in Kotlin, so sharing the name would not overload — it would hide.
    private fun buildServerConnection(): ServerConnection? {
        val servers = serverEntries(getString(R.string.servers))
        if (servers.isEmpty()) {
            Log.i(TAG) { "no server configured; this build plays offline only" }
            return null
        }
        Log.i(TAG) { "${servers.size} server(s): ${servers.joinToString { it.baseUrl }}" }
        return serverConnection(
            stores = ServerStores(
                queue = AndroidDocumentStore(applicationContext, TranscriptQueue.COLLECTION),
                session = AndroidDocumentStore(applicationContext, SessionStore.COLLECTION),
                directory = AndroidDocumentStore(applicationContext, ServerDirectory.COLLECTION),
                tickets = AndroidDocumentStore(applicationContext, TicketStore.COLLECTION),
            ),
            servers = servers,
            clock = AndroidClock,
        )
    }

    override fun onStop() {
        super.onStop()
        audio.pauseMusic()
    }

    override fun onStart() {
        super.onStart()
        audio.resumeMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
    }

    private fun installLogcatSink() {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val logcat = LogSink { level, tag, message, error ->
            when (level) {
                LogLevel.DEBUG -> AndroidLog.d(tag, message, error)
                LogLevel.INFO -> AndroidLog.i(tag, message, error)
                LogLevel.WARN -> AndroidLog.w(tag, message, error)
                LogLevel.ERROR -> AndroidLog.e(tag, message, error)
            }
        }

        // Logcat **and** a file. On a sideloaded build logcat is a stream nobody is attached to:
        // a player whose game closes itself has nothing to send, so the report that reaches anyone
        // who could fix it is "it crashed". `CrashLog` keeps the last few serious lines where the
        // player can find them, and passes everything through to logcat unchanged — see its KDoc
        // for why this is a file and not a crash-reporting SDK.
        Log.install(
            minLevel = if (debuggable) LogLevel.DEBUG else LogLevel.INFO,
            sink = CrashLog(
                store = AndroidDocumentStore(applicationContext, CrashLog.COLLECTION),
                clock = AndroidClock,
                scope = lifecycleScope,
            ).apply { next = logcat },
        )
    }

    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        const val TAG = "Host"
    }
}
