package com.tripletriad.desktop

import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.sanitizeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DesktopDocumentStore(
    subdirectory: String,
    private val extension: String = "sav",
    home: File = File(System.getProperty("user.home")),
) : DocumentStore {
    private val directory = File(home, "$ROOT/$subdirectory")

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        fileFor(key).let { if (it.isFile) it.readText() else null }
    }

    override suspend fun write(key: String, text: String) {
        val file = fileFor(key)
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            // Write-then-rename, as [DesktopSettingsStore]'s Android counterpart does, and for a
            // stronger reason: a save killed mid-write is a lost profile, not a lost volume
            // setting.
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeText(text)
            if (!temporary.renameTo(file)) {
                // Windows will not rename onto an existing file. Delete and retry before falling
                // back to a direct write, which is the one path that can leave a truncated save.
                file.delete()
                if (!temporary.renameTo(file)) {
                    file.writeText(text)
                    temporary.delete()
                }
            }
        }
    }

    override suspend fun keys(): List<String> = withContext(Dispatchers.IO) {
        val suffix = ".$extension"
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(suffix) }
            .map { it.name.removeSuffix(suffix) }
    }

    override suspend fun delete(key: String) {
        val file = fileFor(key)
        withContext(Dispatchers.IO) { file.delete() }
    }

    private fun fileFor(key: String): File = File(directory, "${sanitizeKey(key)}.$extension")

    private companion object {
        const val ROOT = "My Games/Triple Triad Online"
    }
}
