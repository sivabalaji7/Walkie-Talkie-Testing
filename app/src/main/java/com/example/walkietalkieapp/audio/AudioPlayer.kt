package com.example.walkietalkieapp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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

    @Volatile
    private var isPlaying = false

    @Volatile
    private var isPrebuffering = true

    private val audioQueue = LinkedBlockingQueue<ByteArray>(50)
    private var playerThread: Thread? = null

    @Synchronized
    fun start() {
        muted = false

        if (track != null && track?.state == AudioTrack.STATE_INITIALIZED && isPlaying) {
            return
        }

        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            track?.play()
            isPlaying = true
            isPrebuffering = true

            playerThread = Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                while (isPlaying) {
                    try {
                        val chunk = audioQueue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                        if (muted) continue

                        if (isPrebuffering) {
                            // Prebuffer cushion of 2 chunks (~40-80ms) to prevent speaker underruns
                            if (audioQueue.size >= 1) {
                                isPrebuffering = false
                            } else {
                                val second = audioQueue.poll(30, TimeUnit.MILLISECONDS)
                                isPrebuffering = false
                                track?.write(chunk, 0, chunk.size)
                                if (second != null) track?.write(second, 0, second.size)
                                continue
                            }
                        }

                        track?.write(chunk, 0, chunk.size)
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "AudioTrack write error", e)
                    }
                }
            }.apply {
                name = "WalkieAudioPlayerThread"
                isDaemon = true
                start()
            }

            Log.d(TAG, "AudioPlayer started successfully (16kHz PCM, Low-Latency Stream)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack", e)
        }
    }

    fun play(data: ByteArray) {
        if (muted || data.isEmpty()) return

        if (track == null || !isPlaying) {
            start()
        }

        audioQueue.offer(data)
    }

    fun resetPrebuffering() {
        isPrebuffering = true
    }

    fun replayLastTransmissions() {
        VoiceHistoryManager.playLatest()
    }

    fun setMuted(value: Boolean) {
        muted = value
        if (value) {
            audioQueue.clear()
            track?.flush()
        }
    }

    @Synchronized
    fun release() {
        muted = true
        isPlaying = false
        playerThread?.interrupt()
        playerThread = null
        audioQueue.clear()
        track?.runCatching {
            stop()
            flush()
            release()
        }
        track = null
    }
}
