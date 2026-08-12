package com.tripletriad.android

import android.content.Context
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.sanitizeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One file per document in a subdirectory of the app's private storage.
 *
 * `filesDir/<subdirectory>/<key>.<extension>`. The original wrote `saves/` into AIR's
 * `applicationStorageDirectory` (`TTOFiles.STORAGE_DIR`), which is private per-application storage
 * — so unlike `UserSettings.json`, whose AS3 location was shared external storage and had to move,
 * **this one is already the right place**. `filesDir` is its direct equivalent: no permission,
 * survives updates, removed on uninstall.
 *
 * ### Why the root is a parameter and not a `Context`
 *
 * The only thing this class ever wanted from Android is `filesDir`, which is a `File`. Taking the
 * directory instead of the `Context` makes it a plain JVM class, so `AndroidDocumentStoreTest` runs
 * on the host with no Robolectric and no instrumented run — see there for why that mattered. The
 * `Context` overload below is what the app actually calls, so no call site changed.
 *
 * @param root where this app may write. `context.filesDir` in the app, a temporary directory in a
 *   test.
 * @param subdirectory the collection this store holds, e.g. `saves`.
 * @param extension appended to every key on disk. `sav` keeps the original's file naming.
 */
class AndroidDocumentStore(
    root: File,
    subdirectory: String,
    private val extension: String = "sav",
) : DocumentStore {
    /** What the app uses. `filesDir` is private per-application storage — see above. */
    constructor(
        context: Context,
        subdirectory: String,
        extension: String = "sav",
    ) : this(context.filesDir, subdirectory, extension)

    private val directory = File(root, subdirectory)

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        fileFor(key).let { if (it.isFile) it.readText() else null }
    }

    override suspend fun write(key: String, text: String) {
        val file = fileFor(key)
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            // Write beside it and rename, so a kill mid-write leaves the previous save rather than
            // a truncated one. `File.renameTo` is atomic within a directory on every Android
            // filesystem.
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeText(text)
            if (!temporary.renameTo(file)) {
                file.writeText(text)
                temporary.delete()
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
}
