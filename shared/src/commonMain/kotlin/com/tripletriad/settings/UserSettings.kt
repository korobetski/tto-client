package com.tripletriad.settings

import com.tripletriad.i18n.AppLocale
import com.tripletriad.log.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UserSettings(
    @SerialName("language") val language: String = AppLocale.Default.tag,
    @SerialName("background_volume") val backgroundVolume: Float = FULL_VOLUME,
    @SerialName("noise_volume") val noiseVolume: Float = FULL_VOLUME,
    @SerialName("lessons_done") val lessonsDone: Int = 0,
) {
    val locale: AppLocale get() = AppLocale.forTag(language) ?: AppLocale.match(language)

    fun sane(): UserSettings = copy(
        backgroundVolume = backgroundVolume.coerceIn(0f, FULL_VOLUME),
        noiseVolume = noiseVolume.coerceIn(0f, FULL_VOLUME),
        // Only the floor is enforced. A count above the course length is what a *downgrade* looks
        // like — a file written by a build with more lessons in it — and clamping it here would
        // quietly reopen lessons the player has finished if they went back to that build.
        lessonsDone = lessonsDone.coerceAtLeast(0),
    )

    companion object {
        const val FULL_VOLUME: Float = 1f
    }
}

class UserSettingsRepository(private val store: SettingsStore) {
    suspend fun load(deviceLocale: AppLocale): UserSettings {
        val read = runCatching { store.read() }
        read.exceptionOrNull()?.let { failure ->
            Log.w(TAG, failure) { "could not read the settings file; starting from defaults" }
        }
        val stored = read.getOrNull()
        if (stored.isNullOrBlank()) {
            return UserSettings(language = deviceLocale.tag).also { save(it) }
        }
        // A corrupt file is repaired rather than fatal. `conf.as` would have thrown out of
        // `JSON.parse` here and taken the launch with it; there is nothing in this file worth
        // failing to start over, and leaving it unrepaired would mean throwing on every launch.
        // It is logged, though: repairing silently is how a file that is rewritten on every
        // launch goes unnoticed for months.
        val parsed = runCatching { Format.decodeFromString<UserSettings>(stored) }
        parsed.exceptionOrNull()?.let { failure ->
            Log.w(TAG, failure) { "settings file is not readable JSON; replacing it" }
        }
        return parsed.getOrNull()?.sane()
            ?: UserSettings(language = deviceLocale.tag).also { save(it) }
    }

    suspend fun save(settings: UserSettings) {
        runCatching { store.write(Format.encodeToString(settings.sane())) }
            .exceptionOrNull()
            ?.let { failure -> Log.w(TAG, failure) { "could not write the settings file" } }
    }

    private companion object {
        const val TAG = "Settings"

        val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
