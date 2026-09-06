package com.tripletriad.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tripletriad.log.Log
import com.tripletriad.notify.Note
import com.tripletriad.notify.Notifier

/**
 * Android's notification shade, for the notes `PvpAlerts` decides are worth one.
 *
 * ### The permission is asked for, and a refusal is not an error
 *
 * From API 33 a notification posted without `POST_NOTIFICATIONS` is dropped silently, so the
 * permission is checked here rather than assumed: a player who said no gets a log line and nothing
 * else, and every other part of the game keeps working. The activity asks once at launch — see
 * `MainActivity.askToNotify` — because asking at the moment an invitation arrives would put a
 * permission dialog over whatever they were doing.
 *
 * ### Why the note's id is hashed into the Android id
 *
 * `NotificationManager` identifies a notification by an `Int`, and [Note.id] is a string built from
 * a challenge or match id. Hashing keeps the promise the interface makes — the same note replaces
 * itself rather than stacking — and a collision between two different ids costs one note being
 * overwritten by another, which is the same outcome the shade produces when it collapses a group.
 *
 * **Not verified on a device.** No emulator or handset has run this; what is verified is that the
 * decisions above it hold (`PvpAlertsTest`) and that the APK builds. The shade itself, the channel
 * and the permission prompt are untested.
 */
class AndroidNotifier(context: Context) : Notifier {
    private val app = context.applicationContext

    private val manager = NotificationManagerCompat.from(app)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    // The permission check is written out here rather than behind `allowed()`, because Android
    // Lint's `MissingPermission` follows `checkSelfPermission` only within the method that posts.
    // A helper reads better and fails the build; this reads slightly worse and is checked.
    override fun post(note: Note) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            Log.i(TAG) { "not allowed to notify; dropping ${note.id}" }
            return
        }
        val built = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(note.title)
            .setContentText(note.body)
            .setAutoCancel(true)
            .build()
        manager.notify(note.id.hashCode(), built)
    }

    private companion object {
        const val TAG = "AndroidNotifier"

        const val CHANNEL = "multiplayer"

        /** Shown in the system's per-channel settings, so it is the player's word, not ours. */
        const val CHANNEL_NAME = "Multiplayer"
    }
}
