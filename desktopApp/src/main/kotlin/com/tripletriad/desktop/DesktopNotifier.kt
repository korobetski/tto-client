package com.tripletriad.desktop

import com.tripletriad.log.Log
import com.tripletriad.notify.Note
import com.tripletriad.notify.Notifier
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

/**
 * The desktop's own notification bubble, through AWT's system tray.
 *
 * ### Why the tray icon appears only when there is something to say
 *
 * AWT has no way to post a notification without an icon in the tray — `TrayIcon.displayMessage` is
 * a method *on* the icon. Adding one at launch would put a permanent icon in the player's tray for
 * a feature most sessions never use, so the icon is created on the first note and kept afterwards;
 * a player who is never invited to anything never sees it.
 *
 * ### A tray that is not there is not a failure
 *
 * `SystemTray.isSupported` is false on plenty of real desktops — a bare Wayland session among them
 * — and the honest response is a log line. Nothing else in the game depends on a note being seen,
 * because everything a note says is also on the multiplayer screen.
 *
 * **Not verified.** No note posted from this class has been seen on any desktop; what is verified
 * is the decision to post it (`PvpAlertsTest`) and that this compiles into the desktop app.
 */
object DesktopNotifier : Notifier {
    private var icon: TrayIcon? = null

    override fun post(note: Note) {
        val tray = tray() ?: return
        tray.displayMessage(note.title, note.body, TrayIcon.MessageType.INFO)
    }

    private fun tray(): TrayIcon? = icon ?: if (SystemTray.isSupported()) {
        add()
    } else {
        Log.i(TAG) { "no system tray on this desktop; notifications are off" }
        null
    }

    private fun add(): TrayIcon? =
        runCatching {
            val fresh = TrayIcon(Toolkit.getDefaultToolkit().createImage(ByteArray(0)), APP_NAME)
            fresh.isImageAutoSize = true
            SystemTray.getSystemTray().add(fresh)
            icon = fresh
            fresh
        }.onFailure { failure ->
            Log.i(TAG) { "could not add a tray icon: $failure" }
        }.getOrNull()

    private const val TAG = "DesktopNotifier"

    private const val APP_NAME = "Triple Triad"
}
