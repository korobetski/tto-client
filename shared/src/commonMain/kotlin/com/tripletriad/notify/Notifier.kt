package com.tripletriad.notify

/**
 * A note the operating system shows when the player is not looking at this screen.
 *
 * ### What this is not
 *
 * It is **not** push. Nothing here wakes a closed app: every note posted through this interface is
 * decided by code running in this process, which means the app must be alive — foreground or
 * recently backgrounded — for a note to appear at all. Real push would need a device token
 * registered with a service, a server that keeps it, and an FCM/APNs account; none of the three
 * exists, and pretending otherwise in the type name would be the lie. What this does buy is the
 * case that actually bites: the player wanders off to their collection, somebody invites them, and
 * until now the invitation sat unread behind two taps with nothing to say it was there.
 *
 * @property id what makes two notes the same note. A second note with the same id replaces the
 *   first rather than stacking under it, which is what keeps a poll that sees the same invitation
 *   twice from ringing twice.
 */
data class Note(
    val id: String,
    val title: String,
    val body: String,
)

/** Posts [Note]s. Implemented per host; see `AndroidNotifier` and `DesktopNotifier`. */
interface Notifier {
    fun post(note: Note)
}

/**
 * The default: a host that has not wired notifications up, and every test that does not read them.
 *
 * Inert like every other capability `App` takes, so a screenshot run cannot ring a real
 * notification bar.
 */
object SilentNotifier : Notifier {
    override fun post(note: Note) = Unit
}

/** What a test reads instead of a notification bar. */
class RecordingNotifier : Notifier {
    private val notes = mutableListOf<Note>()

    val posted: List<Note> get() = notes.toList()

    override fun post(note: Note) {
        notes += note
    }
}
