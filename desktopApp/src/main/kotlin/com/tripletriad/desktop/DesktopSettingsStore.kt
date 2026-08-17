package com.tripletriad.desktop

import com.tripletriad.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DesktopSettingsStore(home: File = File(System.getProperty("user.home"))) : SettingsStore {
    private val file = File(home, SETTINGS_PATH)

    override suspend fun read(): String? =
        withContext(Dispatchers.IO) {
            if (file.isFile) file.readText() else null
        }

    override suspend fun write(text: String) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
    }

    private companion object {
        const val SETTINGS_PATH = "My Games/Triple Triad Online/UserSettings.json"
    }
}
