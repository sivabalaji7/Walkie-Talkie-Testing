package com.example.walkietalkieapp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
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

    private var appContext: Context? = context?.applicationContext
    private var audioManager: AudioManager? = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    @Volatile
    private var muted = false

    @Volatile
    private var isPlaying = false

    @Volatile
    private var isPrebuffering = true

    private val audioQueue = LinkedBlockingQueue<ByteArray>(50)
    private var playerThread: Thread? = null
    private var lastPlayTime = 0L

    fun attachContext(ctx: Context) {
        if (audioManager == null) {
            val app = ctx.applicationContext
            appContext = app
            audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
    }

    /**
     * Guarantees hands-free loudspeaker output for walkie-talkie mode.
     * Prevents voice from defaulting to phone earpiece (receiver).
     */
    fun ensureHandsFreeSpeakerRouting() {
        val am = audioManager ?: return
        try {
            requestAudioFocus()

            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // 1. Android 12+ (API 31+) Modern Audio Routing API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableDevices = am.availableCommunicationDevices
                val headsetDevice = availableDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                val speakerDevice = headsetDevice ?: availableDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }

                if (speakerDevice != null) {
                    val result = am.setCommunicationDevice(speakerDevice)
                    Log.d(TAG, "Loudspeaker: setCommunicationDevice(${speakerDevice.type}): $result")
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }

            // 2. Legacy / OEM fallback flag (Samsung, Xiaomi, Oppo, etc.)
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true

            // 3. AudioTrack Preferred Device Routing (API 23+)
            routeTrackToSpeaker(track)

            // 4. Ensure STREAM_VOICE_CALL volume is sufficiently loud
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val currentVol = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            if (currentVol < (maxVol * 0.7f).toInt()) {
                val targetVol = (maxVol * 0.85f).toInt().coerceAtLeast(1)
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetVol, 0)
                Log.d(TAG, "Loudspeaker: Boosted STREAM_VOICE_CALL volume to $targetVol / $maxVol")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ensureHandsFreeSpeakerRouting: ${e.message}", e)
        }
    }

    private fun routeTrackToSpeaker(t: AudioTrack?) {
        val am = audioManager ?: return
        val currentTrack = t ?: return
        try {
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val headset = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            val speaker = headset ?: outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speaker != null) {
                currentTrack.preferredDevice = speaker
                Log.d(TAG, "AudioTrack.preferredDevice set to: ${speaker.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting AudioTrack preferred device: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { focusChange ->
                            Log.d(TAG, "Audio focus changed: $focusChange")
                        }
                        .build()
                }
                audioFocusRequest?.let { am.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus: ${e.message}")
        }
    }

    @Synchronized
    fun start() {
        muted = false

        if (track != null && track?.state == AudioTrack.STATE_INITIALIZED && isPlaying) {
            ensureHandsFreeSpeakerRouting()
            return
        }

        try {
            ensureHandsFreeSpeakerRouting()

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

            routeTrackToSpeaker(track)

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

            Log.d(TAG, "AudioPlayer started successfully (16kHz PCM, Loudspeaker Low-Latency Stream)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack", e)
        }
    }

    fun play(data: ByteArray) {
        if (muted || data.isEmpty()) return

        val now = System.currentTimeMillis()
        if (track == null || !isPlaying) {
            start()
        } else if (now - lastPlayTime > 1500L) {
            // Re-affirm speaker routing on new incoming transmission burst after pause
            ensureHandsFreeSpeakerRouting()
        }
        lastPlayTime = now

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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager?.clearCommunicationDevice()
            }
            audioManager?.apply {
                @Suppress("DEPRECATION")
                isSpeakerphoneOn = false
                mode = AudioManager.MODE_NORMAL
            }
            abandonAudioFocus()
            Log.d(TAG, "AudioPlayer released: routing restored to normal")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audioManager in release: ${e.message}")
        }
    }
}
