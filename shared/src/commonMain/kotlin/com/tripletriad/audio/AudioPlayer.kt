package com.tripletriad.audio

import androidx.compose.runtime.staticCompositionLocalOf

enum class Sound(val file: String, val music: Boolean = false) {
    MATCH_MUSIC("shuffle_or_boogie", music = true),

    MATCH_OPEN("se_ttriad.scd_2"),

    CARD_PLACED("se_ttriad.scd_1"),

    CARD_CAPTURED("se_ttriad.scd_157"),

    COMBO("se_ttriad.scd_15"),

    TURN_CHANGE("se_ttriad.scd_4"),

    BLUE_WINS("se_ttriad.scd_7"),

    RED_WINS("se_ttriad.scd_8"),

    UI_CLICK("se_ui.scd_72"),

    NEW_MATCH("se_gs.scd_162"),
    ;

    companion object {
        val Music: Sound = MATCH_MUSIC

        val effects: List<Sound> get() = entries.filterNot { it.music }
    }
}

const val MUSIC_LOOP_START_MS: Int = 16_374

interface AudioPlayer {
    fun play(sound: Sound)

    fun stopMusic()

    fun volumes(background: Float, noise: Float)
}

object SilentAudioPlayer : AudioPlayer {
    override fun play(sound: Sound) = Unit

    override fun stopMusic() = Unit

    override fun volumes(background: Float, noise: Float) = Unit
}

class RecordingAudioPlayer : AudioPlayer {
    private val calls = mutableListOf<Sound>()

    val played: List<Sound> get() = calls.toList()

    var musicStops: Int = 0
        private set

    var volumes: Pair<Float, Float>? = null
        private set

    override fun play(sound: Sound) {
        calls += sound
    }

    override fun stopMusic() {
        musicStops++
    }

    override fun volumes(background: Float, noise: Float) {
        volumes = background to noise
    }

    fun clear() {
        calls.clear()
        musicStops = 0
    }

    operator fun contains(sound: Sound): Boolean = sound in calls
}

val LocalAudio = staticCompositionLocalOf<AudioPlayer> { SilentAudioPlayer }
