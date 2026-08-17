package com.tripletriad.android

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.MUSIC_LOOP_START_MS
import com.tripletriad.audio.Sound
import com.tripletriad.log.Log

class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val resources = context.applicationContext.resources

    private val pool = SoundPool.Builder()
        // Four cards can flip from one placement, plus the combo sound over them.
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loaded = mutableMapOf<Sound, Int>()
    private val pending = mutableMapOf<Int, Sound>()

    private var music: MediaPlayer? = null
    private var playingMusic: Sound? = null

    private var pausedByLifecycle = false

    private var backgroundVolume = 1f
    private var noiseVolume = 1f

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            val sound = pending.remove(sampleId)
            if (status == 0 && sound != null) {
                loaded[sound] = sampleId
            } else {
                Log.w(TAG) { "SoundPool failed to load $sound (status $status)" }
            }
        }
        for (sound in Sound.effects) {
            val sampleId = pool.load(resources.openRawResourceFd(rawId(sound)), PRIORITY)
            pending[sampleId] = sound
        }
    }

    override fun play(sound: Sound) {
        if (sound.music) playMusic(sound) else playEffect(sound)
    }

    private fun playEffect(sound: Sound) {
        if (noiseVolume <= 0f) return
        val sampleId = loaded[sound]
        if (sampleId == null) {
            Log.d(TAG) { "$sound is not loaded yet; dropped" }
            return
        }
        pool.play(sampleId, noiseVolume, noiseVolume, PRIORITY, 0, 1f)
    }

    private fun playMusic(sound: Sound) {
        if (playingMusic == sound && music?.isPlaying == true) return
        stopMusic()
        val player = runCatching {
            MediaPlayer().apply {
                resources.openRawResourceFd(rawId(sound)).use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setVolume(backgroundVolume, backgroundVolume)
                // The loop point. `SoundManager.progressHandler` polled the position every frame
                // and restarted at 16 374 ms once past 64 300 ms, which is the end of a 64.39 s
                // track — so "on completion" is the same behaviour, minus the overshoot.
                setOnCompletionListener {
                    seekTo(MUSIC_LOOP_START_MS)
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG) { "MediaPlayer error $what/$extra on $sound" }
                    true
                }
                prepare()
                start()
            }
        }.getOrElse { failure ->
            Log.w(TAG, failure) { "could not start $sound" }
            return
        }
        music = player
        playingMusic = sound
    }

    override fun stopMusic() {
        music?.let { player ->
            runCatching {
                player.stop()
                player.release()
            }.exceptionOrNull()?.let { Log.w(TAG, it) { "releasing the music player" } }
        }
        music = null
        playingMusic = null
        pausedByLifecycle = false
    }

    override fun volumes(background: Float, noise: Float) {
        backgroundVolume = background.coerceIn(0f, 1f)
        noiseVolume = noise.coerceIn(0f, 1f)
        // Effects take the new volume at their next `play`; the music is already running, so it has
        // to be told. A muted track is left running rather than stopped, so that raising the slider
        // resumes where it was instead of restarting the intro.
        music?.let { runCatching { it.setVolume(backgroundVolume, backgroundVolume) } }
    }

    fun pauseMusic() {
        music?.let { player ->
            if (player.isPlaying) {
                runCatching { player.pause() }
                    .onSuccess { pausedByLifecycle = true }
                    .onFailure { Log.w(TAG, it) { "pausing the music" } }
            }
        }
    }

    fun resumeMusic() {
        if (!pausedByLifecycle) return
        pausedByLifecycle = false
        music?.let { player ->
            runCatching { player.start() }.exceptionOrNull()
                ?.let { Log.w(TAG, it) { "resuming the music" } }
        }
    }

    fun release() {
        stopMusic()
        pool.release()
        loaded.clear()
        pending.clear()
    }

    private companion object {
        const val TAG = "Audio"
        const val MAX_STREAMS = 6
        const val PRIORITY = 1

        fun rawId(sound: Sound): Int = when (sound) {
            Sound.MATCH_MUSIC -> R.raw.shuffle_or_boogie
            Sound.MATCH_OPEN -> R.raw.se_ttriad_scd_2
            Sound.CARD_PLACED -> R.raw.se_ttriad_scd_1
            Sound.CARD_CAPTURED -> R.raw.se_ttriad_scd_157
            Sound.COMBO -> R.raw.se_ttriad_scd_15
            Sound.TURN_CHANGE -> R.raw.se_ttriad_scd_4
            Sound.BLUE_WINS -> R.raw.se_ttriad_scd_7
            Sound.RED_WINS -> R.raw.se_ttriad_scd_8
            Sound.UI_CLICK -> R.raw.se_ui_scd_72
            Sound.NEW_MATCH -> R.raw.se_gs_scd_162
        }
    }
}
