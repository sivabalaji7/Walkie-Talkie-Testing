package com.example.walkietalkieapp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.Executors

class AudioPlayer(private val context: Context? = null) {
    private val TAG = "AudioPlayer"
    private val sampleRate = 16000
    private val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val bufferSize = if (minBufferSize > 0) minBufferSize.coerceAtLeast(2048) else 2048
    private var track: AudioTrack? = null
    private val audioManager: AudioManager? = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    private var muted = false

    private val playExecutor = Executors.newSingleThreadExecutor()
    private val replayBuffer = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    private val MAX_REPLAY_BYTES = sampleRate * 2 * 10 // 10s of audio

    @Synchronized
    fun start() {
        muted = false // Always unmute when starting

        if (track != null && track?.state == AudioTrack.STATE_INITIALIZED) {
            try {
                if (track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track?.play()
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack play resume error", e)
            }
            return
        }

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
                .build()

            track?.play()
            Log.d(TAG, "AudioPlayer started successfully (16kHz PCM)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack", e)
        }
    }

    fun play(data: ByteArray) {
        if (muted || data.isEmpty()) return

        if (track == null || track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
            start()
        }

        // Buffer for 10s replay
        synchronized(replayBuffer) {
            replayBuffer.add(data.copyOf())
            var currentTotal = replayBuffer.sumOf { it.size }
            while (currentTotal > MAX_REPLAY_BYTES && replayBuffer.isNotEmpty()) {
                val removed = replayBuffer.removeAt(0)
                currentTotal -= removed.size
            }
        }

        playExecutor.execute {
            try {
                track?.write(data, 0, data.size)
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack write error", e)
            }
        }
    }

    fun replayLastTransmissions() {
        val bufferCopy = synchronized(replayBuffer) { replayBuffer.toList() }
        if (bufferCopy.isEmpty()) return

        Thread {
            try {
                val tempFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val tempAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val replayTrack = AudioTrack.Builder()
                    .setAudioAttributes(tempAttributes)
                    .setAudioFormat(tempFormat)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                replayTrack.play()
                for (chunk in bufferCopy) {
                    replayTrack.write(chunk, 0, chunk.size)
                }
                replayTrack.stop()
                replayTrack.release()
            } catch (e: Exception) {
                Log.e(TAG, "Replay failed", e)
            }
        }.start()
    }

    fun setMuted(value: Boolean) {
        muted = value
        if (!value) {
            track?.flush()
        }
    }

    @Synchronized
    fun release() {
        muted = true
        track?.runCatching {
            stop()
            flush()
            release()
        }
        track = null
    }
}
