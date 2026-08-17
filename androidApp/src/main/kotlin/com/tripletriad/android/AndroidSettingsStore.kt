package com.tripletriad.android

import android.content.Context
import com.tripletriad.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidSettingsStore(context: Context) : SettingsStore {
    private val file = File(context.filesDir, SETTINGS_FILE)

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (file.isFile) file.readText() else null
    }

    override suspend fun write(text: String) {
        withContext(Dispatchers.IO) {
            // Write beside it and rename, so a kill mid-write leaves the old file rather than a
            // truncated one. `File.renameTo` is atomic within a directory on every Android
            // filesystem, and the settings are worth exactly this much care and no more.
            val temporary = File(file.parentFile, "$SETTINGS_FILE.tmp")
            temporary.writeText(text)
            if (!temporary.renameTo(file)) {
                file.writeText(text)
                temporary.delete()
            }
        }
    }

    private companion object {
        const val SETTINGS_FILE = "UserSettings.json"
    }
}
