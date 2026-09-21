package com.example.walkietalkieapp.vox

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Sensitivity presets for VOX hands-free speech detection.
 * Thresholds represent normalized RMS energy (0.0f - 1.0f) in 16-bit PCM.
 */
enum class VoxSensitivity(val threshold: Float, val displayLabel: String, val description: String) {
    LOW(0.045f, "LOW (-27 dBFS)", "High Noise / Moving Vehicles / Windy Outdoors"),
    MEDIUM(0.025f, "MEDIUM (-32 dBFS)", "Standard Conversational Speech (Recommended)"),
    HIGH(0.012f, "HIGH (-38 dBFS)", "Quiet Room / Soft Speech / Whisper Mode")
}

/**
 * Operational states of the VOX engine.
 */
enum class VoxState {
    OFF,              // VOX feature disabled
    ARMED,            // VOX active & monitoring mic, ready to auto-key
    INHIBITED_BUSY,   // Suppressed because incoming squad transmission is active
    TRANSMITTING,     // Speech detected — transmitter actively keyed up hands-free
    MANUAL_OVERRIDE   // Screen PTT or Hardware Volume Down held manually
}

/**
 * Professional military-grade Voice-Operated Exchange (VOX) engine.
 *
 * Implements hands-free auto-keying with zero hardware mic contention:
 * 1. While idle: Monitors microphone using a lightweight 16kHz AudioRecord.
 * 2. On speech attack: Releases the idle recorder immediately BEFORE keying up the transmitter.
 * 3. While transmitting: Monitors live PCM frames streamed by the active transport (WebRTC / AudioRecorder).
 * 4. On silence: Counts down silence hangover delay (default 700ms) before auto-releasing the floor.
 * 5. RX Interlock: Automatically inhibits VOX trigger during incoming squad voice to eliminate acoustic echo feedback.
 */
object VoxManager {
    private const val TAG = "VoxManager"
    private const val SAMPLE_RATE = 16000
    private const val REQUIRED_VOICED_FRAMES = 2 // 2 consecutive 20ms frames (~40ms) prevents transient pops
    private const val RX_COOLDOWN_MS = 400L      // Inhibit window after remote transmission ceases

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun logD(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logW(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] WARN: $msg")
        }
    }

    private fun logE(msg: String, tr: Throwable? = null) {
        try {
            Log.e(TAG, msg, tr)
        } catch (_: Throwable) {
            System.err.println("[$TAG] ERROR: $msg ${tr?.message}")
        }
    }

    private fun runOnMain(block: () -> Unit) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                Handler(Looper.getMainLooper()).post(block)
            }
        } catch (_: Throwable) {
            // JVM unit test fallback
            block()
        }
    }

    private fun postDelayedOnMain(delayMs: Long, block: () -> Unit) {
        try {
            Handler(Looper.getMainLooper()).postDelayed(block, delayMs)
        } catch (_: Throwable) {
            scope.launch {
                delay(delayMs)
                block()
            }
        }
    }

    // --- StateFlows ---
    private val _isVoxEnabled = MutableStateFlow(false)
    val isVoxEnabled: StateFlow<Boolean> = _isVoxEnabled.asStateFlow()

    private val _sensitivity = MutableStateFlow(VoxSensitivity.MEDIUM)
    val sensitivity: StateFlow<VoxSensitivity> = _sensitivity.asStateFlow()

    private val _hangoverDelayMs = MutableStateFlow(700L)
    val hangoverDelayMs: StateFlow<Long> = _hangoverDelayMs.asStateFlow()

    private val _voxState = MutableStateFlow(VoxState.OFF)
    val voxState: StateFlow<VoxState> = _voxState.asStateFlow()

    private val _liveInputLevel = MutableStateFlow(0f)
    val liveInputLevel: StateFlow<Float> = _liveInputLevel.asStateFlow()

    // --- Callbacks & Lifecycle Flags ---
    private var onPttStartCallback: (() -> Unit)? = null
    private var onPttEndCallback: (() -> Unit)? = null

    @Volatile
    var isInsideActiveSquad: Boolean = false
        set(value) {
            field = value
            if (!value) {
                stopListening()
                if (_voxState.value == VoxState.TRANSMITTING) {
                    forceRelease()
                }
                updateState()
            } else if (_isVoxEnabled.value && !isManualPttActive && !isChannelBusy) {
                startListening()
            }
        }

    @Volatile
    var isChannelBusy: Boolean = false
        set(value) {
            val wasBusy = field
            field = value
            if (value) {
                lastRxCeasedTimestamp = 0L
                if (_voxState.value == VoxState.ARMED) {
                    _voxState.value = VoxState.INHIBITED_BUSY
                }
            } else if (wasBusy && _isVoxEnabled.value && isInsideActiveSquad) {
                lastRxCeasedTimestamp = System.currentTimeMillis()
                postDelayedOnMain(RX_COOLDOWN_MS) {
                    if (!isChannelBusy && _isVoxEnabled.value && isInsideActiveSquad && !isManualPttActive) {
                        if (_voxState.value == VoxState.INHIBITED_BUSY) {
                            _voxState.value = VoxState.ARMED
                        }
                    }
                }
            }
        }

    @Volatile
    var isManualPttActive: Boolean = false
        set(value) {
            field = value
            if (value) {
                stopListening()
                cancelHangoverWatchdog()
                _voxState.value = VoxState.MANUAL_OVERRIDE
            } else {
                updateState()
                if (_isVoxEnabled.value && isInsideActiveSquad && !isChannelBusy) {
                    postDelayedOnMain(200L) {
                        if (!isManualPttActive && _isVoxEnabled.value && isInsideActiveSquad) {
                            startListening()
                        }
                    }
                }
            }
        }

    // --- Internal Engine State ---
    private val isListeningActive = AtomicBoolean(false)
    private var idleListeningThread: Thread? = null
    private var idleAudioRecord: AudioRecord? = null

    @Volatile
    private var lastVoiceDetectedTimestamp: Long = 0L

    @Volatile
    private var lastRxCeasedTimestamp: Long = 0L

    private var hangoverWatchdogJob: Job? = null
    private var consecutiveVoicedCount = 0

    /**
     * Registers PTT trigger callbacks for auto-keying the transmitter.
     */
    fun registerPttTrigger(onStart: () -> Unit, onEnd: () -> Unit) {
        this.onPttStartCallback = onStart
        this.onPttEndCallback = onEnd
    }

    fun unregisterPttTrigger() {
        this.onPttStartCallback = null
        this.onPttEndCallback = null
    }

    /**
     * Master toggle for VOX Hands-Free Mode.
     */
    fun setVoxEnabled(enabled: Boolean) {
        _isVoxEnabled.value = enabled
        consecutiveVoicedCount = 0
        if (enabled) {
            if (isInsideActiveSquad && !isManualPttActive && !isChannelBusy) {
                startListening()
            }
            updateState()
        } else {
            stopListening()
            if (_voxState.value == VoxState.TRANSMITTING) {
                forceRelease()
            }
            _voxState.value = VoxState.OFF
            _liveInputLevel.value = 0f
        }
    }

    fun setSensitivity(level: VoxSensitivity) {
        _sensitivity.value = level
        logD("VOX sensitivity updated to: ${level.displayLabel}")
    }

    fun setHangoverDelayMs(delayMs: Long) {
        _hangoverDelayMs.value = delayMs.coerceIn(300L, 2000L)
        logD("VOX hangover delay updated to: ${_hangoverDelayMs.value} ms")
    }

    private fun updateState() {
        _voxState.value = when {
            !_isVoxEnabled.value -> VoxState.OFF
            isManualPttActive -> VoxState.MANUAL_OVERRIDE
            isChannelBusy -> VoxState.INHIBITED_BUSY
            idleListeningThread != null -> VoxState.ARMED
            else -> if (_isVoxEnabled.value) VoxState.ARMED else VoxState.OFF
        }
    }

    /**
     * Starts lightweight AudioRecord in background to monitor ambient mic energy while idle.
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun startListening() {
        if (isListeningActive.get() || !_isVoxEnabled.value || !isInsideActiveSquad || isManualPttActive || isChannelBusy) {
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val frameBytes = 640 // 20ms window (320 samples * 2 bytes)
            val bufferSize = if (minBufferSize > 0) minBufferSize.coerceAtLeast(frameBytes * 2) else frameBytes * 2

            idleAudioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (idleAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                logW("VOX idle AudioRecord initialization failed — retrying with MIC")
                idleAudioRecord?.release()
                idleAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            if (idleAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                logE("VOX idle AudioRecord could not be initialized")
                idleAudioRecord?.release()
                idleAudioRecord = null
                return
            }

            idleAudioRecord?.startRecording()
            isListeningActive.set(true)
            consecutiveVoicedCount = 0
            _voxState.value = VoxState.ARMED
            logD("VOX idle listening started (ARMED, threshold=${_sensitivity.value.threshold})")

            idleListeningThread = Thread {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                val buffer = ByteArray(frameBytes)

                while (isListeningActive.get()) {
                    val recorder = idleAudioRecord ?: break
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0 && isListeningActive.get()) {
                        processIdlePcmFrame(buffer, read)
                    }
                }
            }.apply {
                name = "VoxIdleListenerThread"
                start()
            }
        } catch (e: Throwable) {
            logE("Error starting VOX idle listener: ${e.message}")
            stopListening()
        }
    }

    /**
     * Halts idle AudioRecord and frees microphone hardware.
     */
    @Synchronized
    fun stopListening() {
        if (!isListeningActive.get() && idleAudioRecord == null && idleListeningThread == null) return

        isListeningActive.set(false)
        try {
            idleAudioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Throwable) {
            logW("Notice releasing idle AudioRecord: ${e.message}")
        } finally {
            idleAudioRecord = null
        }

        try {
            idleListeningThread?.interrupt()
        } catch (_: Throwable) {}
        idleListeningThread = null
        consecutiveVoicedCount = 0
    }

    /**
     * Evaluates a 20ms PCM frame from the idle listener.
     */
    fun processIdlePcmFrame(pcmBytes: ByteArray, length: Int) {
        val sampleCount = length / 2
        if (sampleCount <= 0) return

        var sumSquares = 0.0
        var peak = 0f

        for (i in 0 until sampleCount) {
            val idx = i * 2
            if (idx + 1 >= length) break
            val sample = ((pcmBytes[idx].toInt() and 0xFF) or (pcmBytes[idx + 1].toInt() shl 8)).toShort()
            val norm = abs(sample / 32768.0f)
            sumSquares += (norm * norm).toDouble()
            if (norm > peak) peak = norm
        }

        val rms = sqrt(sumSquares / max(1, sampleCount)).toFloat()

        // Smooth live input level for UI VU meter
        val currentLevel = _liveInputLevel.value
        val smoothed = currentLevel * 0.4f + rms * 0.6f
        _liveInputLevel.value = smoothed.coerceIn(0f, 1f)

        // Safety interlocks: Do not trigger if channel is busy, RX cooled down, or manual PTT active
        if (isChannelBusy || isManualPttActive || !isInsideActiveSquad || !_isVoxEnabled.value) {
            consecutiveVoicedCount = 0
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastRxCeasedTimestamp < RX_COOLDOWN_MS) {
            consecutiveVoicedCount = 0
            return
        }

        val targetThreshold = _sensitivity.value.threshold
        if (rms >= targetThreshold && peak >= targetThreshold * 1.25f) {
            consecutiveVoicedCount++
            if (consecutiveVoicedCount >= REQUIRED_VOICED_FRAMES) {
                logD("VOX Speech Attack confirmed (rms=$rms, peak=$peak) -> AUTO-KEYING TRANSMITTER")
                triggerVoxAutoKey()
            }
        } else {
            consecutiveVoicedCount = 0
        }
    }

    /**
     * Executes auto-key transmission transition.
     */
    private fun triggerVoxAutoKey() {
        if (_voxState.value == VoxState.TRANSMITTING || isManualPttActive || isChannelBusy) return

        _voxState.value = VoxState.TRANSMITTING
        lastVoiceDetectedTimestamp = System.currentTimeMillis()

        // 1. Release the idle mic hardware FIRST so active transport has 100% exclusive access
        stopListening()

        // 2. Invoke PTT start callback on main thread
        runOnMain {
            onPttStartCallback?.invoke()
        }

        // 3. Start hangover delay watchdog
        startHangoverWatchdog()
    }

    /**
     * Feeds real-time PCM frames during active transmission (from WebRTC or AudioRecorder).
     * Maintains active transmission while speech continues, and resets hangover countdown.
     */
    fun feedTransmittingFrame(pcmBytes: ByteArray, channels: Int = 1) {
        if (_voxState.value != VoxState.TRANSMITTING && !isManualPttActive) return

        val bytesPerSample = 2 * channels
        val sampleCount = pcmBytes.size / bytesPerSample
        if (sampleCount <= 0) return

        var sumSquares = 0.0
        var peak = 0f

        for (i in 0 until sampleCount) {
            val idx = i * bytesPerSample
            if (idx + 1 >= pcmBytes.size) break

            val sample = if (channels == 2 && idx + 3 < pcmBytes.size) {
                val left = ((pcmBytes[idx].toInt() and 0xFF) or (pcmBytes[idx + 1].toInt() shl 8)).toShort()
                val right = ((pcmBytes[idx + 2].toInt() and 0xFF) or (pcmBytes[idx + 3].toInt() shl 8)).toShort()
                ((left.toInt() + right.toInt()) / 2).toShort()
            } else {
                ((pcmBytes[idx].toInt() and 0xFF) or (pcmBytes[idx + 1].toInt() shl 8)).toShort()
            }

            val norm = abs(sample / 32768.0f)
            sumSquares += (norm * norm).toDouble()
            if (norm > peak) peak = norm
        }

        val rms = sqrt(sumSquares / max(1, sampleCount)).toFloat()
        _liveInputLevel.value = (_liveInputLevel.value * 0.4f + rms * 0.6f).coerceIn(0f, 1f)

        // Hysteresis: 85% of threshold allows speech continuation without premature cutoff
        val continuationThreshold = _sensitivity.value.threshold * 0.85f
        if (rms >= continuationThreshold) {
            lastVoiceDetectedTimestamp = System.currentTimeMillis()
        }
    }

    /**
     * Monitors silence duration during active transmission. When silence exceeds
     * the hangover delay, automatically un-keys the transmitter.
     */
    private fun startHangoverWatchdog() {
        cancelHangoverWatchdog()
        hangoverWatchdogJob = scope.launch {
            while (_voxState.value == VoxState.TRANSMITTING && isActive) {
                delay(60L)
                val silenceDuration = System.currentTimeMillis() - lastVoiceDetectedTimestamp
                if (silenceDuration >= _hangoverDelayMs.value) {
                    logD("VOX silence hangover expired (${silenceDuration}ms >= ${_hangoverDelayMs.value}ms) -> AUTO-RELEASING TRANSMITTER")
                    runOnMain {
                        triggerVoxAutoRelease()
                    }
                    break
                }
            }
        }
    }

    private fun cancelHangoverWatchdog() {
        hangoverWatchdogJob?.cancel()
        hangoverWatchdogJob = null
    }

    /**
     * Executes auto-release transmission transition.
     */
    private fun triggerVoxAutoRelease() {
        if (_voxState.value != VoxState.TRANSMITTING) return

        cancelHangoverWatchdog()
        _voxState.value = if (_isVoxEnabled.value) VoxState.ARMED else VoxState.OFF

        // 1. Invoke PTT end callback on main thread
        onPttEndCallback?.invoke()

        // 2. Allow transmission hardware to tear down, then re-acquire idle listener
        postDelayedOnMain(220L) {
            if (_isVoxEnabled.value && isInsideActiveSquad && !isManualPttActive && !isChannelBusy && _voxState.value == VoxState.ARMED) {
                startListening()
            }
        }
    }

    /**
     * Emergency / manual release.
     */
    fun forceRelease() {
        cancelHangoverWatchdog()
        if (_voxState.value == VoxState.TRANSMITTING) {
            _voxState.value = if (_isVoxEnabled.value) VoxState.ARMED else VoxState.OFF
            runOnMain {
                onPttEndCallback?.invoke()
            }
        }
        stopListening()
    }

    /**
     * Full cleanup on leaving squad or app destroy.
     */
    fun stop() {
        forceRelease()
        _isVoxEnabled.value = false
        _voxState.value = VoxState.OFF
        _liveInputLevel.value = 0f
        isInsideActiveSquad = false
        isChannelBusy = false
        isManualPttActive = false
        consecutiveVoicedCount = 0
        lastVoiceDetectedTimestamp = 0L
        lastRxCeasedTimestamp = 0L
    }
}
