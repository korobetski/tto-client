package com.tripletriad.log

import com.tripletriad.storage.DocumentStore
import com.tripletriad.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The last few serious lines, kept on disk so a crash leaves something behind.
 *
 * ### Why this exists at all
 *
 * [PrintlnSink] writes to stdout, which on a sideloaded Android build is a stream nobody is
 * attached to. A player whose game closes itself has nothing to send and nothing to describe
 * beyond "it crashed", and the one person who could act on it — whoever is reading this — gets a
 * sentence instead of a stack trace. That is the difference between a bug that is fixed and one
 * that is reported twice a year for ever.
 *
 * ### Why not a crash-reporting SDK
 *
 * Because it would be the only thing in this app that sends anything anywhere without being asked.
 * A sideloaded game with no store listing has no privacy policy, no consent flow and no data
 * processing agreement, and adding an SDK that ships device identifiers to a third party would
 * require all three — for a game whose entire distribution is a link. Writing to a file the player
 * already owns costs none of that, and the player decides whether it ever leaves the device.
 *
 * ### Why only WARN and above, and only a few
 *
 * A ring of the last [KEEP] serious lines, not a transcript. Debug logging is per-frame in places
 * — `Log.d` exists precisely so the card path can afford it — and persisting that would write to
 * flash continuously to capture the moments nobody needs. What is worth keeping is what was going
 * wrong just before the end.
 *
 * ### Written as it happens, not flushed on exit
 *
 * A buffer flushed on shutdown is empty exactly when it matters, because the process that would
 * flush it is the one that died. So each warning is handed to a writer immediately.
 *
 * The awkwardness is that [LogSink.write] cannot suspend and [DocumentStore.write] must. Launching
 * a coroutine per line would be unbounded and unordered; a conflated channel with a single consumer
 * is neither. Each message is the **whole ring**, so a snapshot dropped by conflation loses nothing
 * — the next one contains everything it did.
 *
 * ### What this still cannot promise
 *
 * A process killed between the warning and the write loses that warning. The window is one local
 * file write and the ring survives, so what is lost is the last line rather than the history — but
 * it is a real gap and not one worth pretending away. Closing it would mean a synchronous write
 * from the logging thread, which is a stall on every warning in exchange for the last line of a
 * session that has already ended.
 */
class CrashLog(
    private val store: DocumentStore,
    private val clock: Clock,
    scope: CoroutineScope,
    private val keep: Int = KEEP,
) : LogSink {

    /** Also written to [next], so installing this does not silence the ordinary output. */
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

    /** Everything kept, oldest first — what an export or a bug report would carry. */
    suspend fun readAll(): String = runCatching { store.read(DOCUMENT) }.getOrNull().orEmpty()

    /** Forgets everything, for a player who would rather not keep it. */
    suspend fun clear() {
        recent.clear()
        store.write(DOCUMENT, "")
    }

    /**
     * The whole ring each time, rather than an append.
     *
     * It is a handful of lines, and a rewrite is what makes the file self-trimming with no separate
     * rotation to get wrong. Conflated, because an older snapshot is a strict prefix of a newer one
     * and writing both would be writing the same lines twice.
     */
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
        /** The document, in the host's own collection. */
        const val DOCUMENT = "crash"

        /** The subdirectory a host store uses, as `SessionStore.COLLECTION` is for tokens. */
        const val COLLECTION = "diagnostics"

        /**
         * Enough to see what led up to a failure, few enough to read.
         *
         * Twenty warnings is several minutes of a badly behaving session. A hundred would be a file
         * nobody scrolls through and a longer write on every line.
         */
        const val KEEP = 20
    }
}
