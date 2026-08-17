package com.tripletriad.log

import com.tripletriad.storage.DocumentStore
import com.tripletriad.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class CrashLog(
    private val store: DocumentStore,
    private val clock: Clock,
    scope: CoroutineScope,
    private val keep: Int = KEEP,
) : LogSink {

    var next: LogSink = PrintlnSink

    private val recent = ArrayDeque<String>()

    override fun write(level: LogLevel, tag: String, message: String, error: Throwable?) {
        next.write(level, tag, message, error)
        if (level < LogLevel.WARN) return

        val line = buildString {
            append(clock.nowMillis())
            append(' ')
            append(level.name.first())
            append('/')
            append(tag)
            append(": ")
            append(message)
            error?.let {
                append(" | ")
                append(it::class.simpleName)
                append(": ")
                append(it.message)
            }
        }

        recent.addLast(line)
        while (recent.size > keep) recent.removeFirst()
        // `trySend` and not `send`: this is called from whichever thread hit the problem, and a
        // logger that blocks its caller is a logger that turns a warning into a hang. On a
        // conflated channel it never fails for want of room.
        snapshots.trySend(recent.joinToString("\n"))
    }

    suspend fun readAll(): String = runCatching { store.read(DOCUMENT) }.getOrNull().orEmpty()

    suspend fun clear() {
        recent.clear()
        store.write(DOCUMENT, "")
    }

    private val snapshots = Channel<String>(Channel.CONFLATED)

    init {
        scope.launch {
            for (text in snapshots) {
                // Swallowed: a logger that throws while recording a problem turns one failure into
                // two, and the thing it would have reported is already lost.
                runCatching { store.write(DOCUMENT, text) }
            }
        }
    }

    companion object {
        const val DOCUMENT = "crash"

        const val COLLECTION = "diagnostics"

        const val KEEP = 20
    }
}
