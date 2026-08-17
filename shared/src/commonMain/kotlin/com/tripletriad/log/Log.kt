package com.tripletriad.log

object Log {
    var minLevel: LogLevel = LogLevel.DEBUG
        private set

    private var sink: LogSink = PrintlnSink

    fun install(sink: LogSink, minLevel: LogLevel = LogLevel.DEBUG) {
        this.sink = sink
        this.minLevel = minLevel
    }

    fun reset() {
        sink = PrintlnSink
        minLevel = LogLevel.DEBUG
    }

    fun d(tag: String, message: () -> String) = write(LogLevel.DEBUG, tag, null, message)

    fun i(tag: String, message: () -> String) = write(LogLevel.INFO, tag, null, message)

    fun w(tag: String, error: Throwable? = null, message: () -> String) =
        write(LogLevel.WARN, tag, error, message)

    fun e(tag: String, error: Throwable? = null, message: () -> String) =
        write(LogLevel.ERROR, tag, error, message)

    // Named `write` and not `log`: detekt's MemberNameEqualsClassName fires on `Log.log`.
    private inline fun write(
        level: LogLevel,
        tag: String,
        error: Throwable?,
        message: () -> String,
    ) {
        if (level.ordinal >= minLevel.ordinal) {
            sink.write(level, tag, message(), error)
        }
    }
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

fun interface LogSink {
    fun write(level: LogLevel, tag: String, message: String, error: Throwable?)
}

object PrintlnSink : LogSink {
    override fun write(level: LogLevel, tag: String, message: String, error: Throwable?) {
        println("${level.name.first()}/$tag: $message")
        error?.let { println("  ${it::class.simpleName}: ${it.message}") }
    }
}

class RecordingSink : LogSink {
    private val entries = mutableListOf<LogEntry>()

    val lines: List<LogEntry> get() = entries.toList()

    override fun write(level: LogLevel, tag: String, message: String, error: Throwable?) {
        entries += LogEntry(level, tag, message, error)
    }

    fun clear() = entries.clear()

    fun at(level: LogLevel): List<LogEntry> = entries.filter { it.level == level }
}

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val error: Throwable? = null,
)
