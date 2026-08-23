package com.example.walkietalkieapp.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.util.concurrent.Executors

class AudioRecorder {
    private val TAG = "AudioRecorder"
    private val sampleRate = 16000
    private val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    // 1280 bytes = 640 samples = 40ms of 16kHz 16-bit audio
    private val bufferSize = if (minBufferSize > 0) minBufferSize.coerceAtLeast(1280) else 1280

    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var recorder: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null

    // Real-Time Vocal-Enhanced DSP Engine (High-Sensitivity Far-Field & AGC)
    private val dspProcessor = AudioDspProcessor(sampleRate = sampleRate)

    private val cleanupExecutor = Executors.newSingleThreadExecutor()

    @Synchronized
    fun start(onData: (ByteArray) -> Unit) {
        if (isRecording) return

        dspProcessor.reset()
        val activeRecorder = createRecorder() ?: return
        isRecording = true

        try {
            activeRecorder.startRecording()
            Log.d(TAG, "AudioRecorder started recording with Far-Field Vocal Boost DSP (16kHz PCM)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            isRecording = false
            return
        }

        recordingThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = activeRecorder.read(buffer, 0, buffer.size)
                if (read > 0 && isRecording) {
                    // Apply real-time vocal presence boost, noise reduction, and AGC
                    val processedAudio = dspProcessor.process(buffer, 0, read)
                    onData(processedAudio)
                }
            }
        }.apply {
            name = "WalkieAudioRecorderThread"
            start()
        }
    }

    @Synchronized
    fun stop() {
        if (!isRecording) return
        isRecording = false

        val activeRecorder = recorder
        val oldThread = recordingThread
        recordingThread = null
        recorder = null

        val ec = echoCanceler
        val ns = noiseSuppressor
        val agc = automaticGainControl
        echoCanceler = null
        noiseSuppressor = null
        automaticGainControl = null

        cleanupExecutor.execute {
            try {
                activeRecorder?.runCatching {
                    stop()
                    release()
                }
                oldThread?.join(150)
                ec?.release()
                ns?.release()
                agc?.release()
                Log.d(TAG, "AudioRecorder stopped and released")
            } catch (e: Exception) {
                Log.e(TAG, "Error in recorder cleanup", e)
            }
        }
    }

    fun release() {
        stop()
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(): AudioRecord? {
        if (minBufferSize <= 0) return null

        // AudioSource.MIC provides full-range sensitivity for normal hand distance / walkie-talkie range
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).also {
                recorder = it
                setupAudioEffects(it.audioSessionId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioRecord with MIC, trying VOICE_COMMUNICATION fallback", e)
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                ).also {
                    recorder = it
                    setupAudioEffects(it.audioSessionId)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback AudioRecord failed", e2)
                null
            }
        }
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hardware audio effect setup failed", e)
        }
    }
}
