package com.example.walkietalkieapp.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class VoiceTransmission(
    val id: String = UUID.randomUUID().toString(),
    val speakerName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val pcmData: ByteArray,
    val sampleRate: Int = 48000,
    val isSelf: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceTransmission
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * VoiceHistoryManager manages the rolling Blackbox Audio Reel.
 * Captures, stores, and plays back past voice transmissions.
 */
object VoiceHistoryManager {
    private const val TAG = "VoiceHistoryManager"
    private const val MAX_TRANSMISSIONS = 20

    private val _transmissions = MutableStateFlow<List<VoiceTransmission>>(emptyList())
    val transmissions: StateFlow<List<VoiceTransmission>> = _transmissions.asStateFlow()

    private val _currentlyPlayingId = MutableStateFlow<String?>(null)
    val currentlyPlayingId: StateFlow<String?> = _currentlyPlayingId.asStateFlow()

    @Volatile
    private var activePlaybackTrack: AudioTrack? = null
    private val isStopping = AtomicBoolean(false)

    private fun logD(msg: String) {
        try {
            android.util.Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logE(msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(TAG, msg, tr)
        } catch (_: Throwable) {
            System.err.println("[$TAG][ERROR] $msg: ${tr?.message}")
        }
    }

    /**
     * Adds a newly completed transmission to the rolling reel.
     */
    fun addTransmission(
        speakerName: String,
        pcmData: ByteArray,
        sampleRate: Int = 48000,
        durationMs: Long,
        isSelf: Boolean = false
    ) {
        if (pcmData.isEmpty() || durationMs < 200L) {
            logD("Ignored trivial audio chunk (<200ms or empty)")
            return
        }

        val record = VoiceTransmission(
            speakerName = speakerName.trim().ifBlank { "Unknown" },
            timestamp = System.currentTimeMillis(),
            durationMs = durationMs,
            pcmData = pcmData,
            sampleRate = sampleRate,
            isSelf = isSelf
        )

        _transmissions.update { current ->
            // Newest at index 0, capped at MAX_TRANSMISSIONS
            listOf(record) + current.take(MAX_TRANSMISSIONS - 1)
        }

        logD("Added transmission from ${record.speakerName} (${record.durationMs}ms, ${pcmData.size} bytes). Total reel count: ${_transmissions.value.size}")
    }

    /**
     * Plays a specific transmission by ID over AudioTrack.
     */
    @Synchronized
    fun playTransmission(id: String, onComplete: (() -> Unit)? = null) {
        val target = _transmissions.value.find { it.id == id } ?: run {
            logD("Transmission $id not found")
            return
        }

        stopPlayback()

        _currentlyPlayingId.value = id
        isStopping.set(false)

        Thread({
            var track: AudioTrack? = null
            try {
                val sampleRate = target.sampleRate
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minBufferSize.coerceAtLeast(4096))
                    .build()

                activePlaybackTrack = track
                track.play()

                val data = target.pcmData
                val chunkSize = 2048
                var offset = 0

                while (offset < data.size && !isStopping.get()) {
                    val bytesToWrite = (data.size - offset).coerceAtMost(chunkSize)
                    track.write(data, offset, bytesToWrite)
                    offset += bytesToWrite
                }

                if (!isStopping.get()) {
                    track.stop()
                }
            } catch (e: Exception) {
                logE("Error during voice playback: ${e.message}", e)
            } finally {
                try {
                    track?.release()
                } catch (_: Exception) {}
                activePlaybackTrack = null
                if (_currentlyPlayingId.value == id) {
                    _currentlyPlayingId.value = null
                }
                onComplete?.invoke()
            }
        }, "VoiceReelPlayer").start()
    }

    /**
     * Plays the latest transmission in the reel.
     */
    fun playLatest() {
        val latest = _transmissions.value.firstOrNull() ?: return
        playTransmission(latest.id)
    }

    /**
     * Stops any currently active playback immediately.
     */
    @Synchronized
    fun stopPlayback() {
        isStopping.set(true)
        try {
            activePlaybackTrack?.pause()
            activePlaybackTrack?.flush()
            activePlaybackTrack?.stop()
            activePlaybackTrack?.release()
        } catch (e: Exception) {
            logD("Playback track release note: ${e.message}")
        } finally {
            activePlaybackTrack = null
            _currentlyPlayingId.value = null
        }
    }

    /**
     * Clears all recorded transmissions.
     */
    @Synchronized
    fun clearHistory() {
        stopPlayback()
        _transmissions.value = emptyList()
        logD("Cleared all transmissions from reel")
    }
}
