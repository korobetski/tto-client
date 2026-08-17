package com.tripletriad.net

import com.tripletriad.CLIENT_VERSION
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.ClientRelease

val runningVersion: AppVersion? by lazy { AppVersion.parse(CLIENT_VERSION) }

fun ClientRelease.isNewerThanRunning(): Boolean {
    val running = runningVersion ?: return false
    return version > running
}
