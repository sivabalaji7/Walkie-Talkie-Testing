package com.example.walkietalkieapp.audio.intelligence

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * AcousticRadarManager profiles live ambient acoustic pressure (SPL dBA)
 * and drives dynamic noise-floor calibration and LCD visual radar telemetry.
 */
object AcousticRadarManager {
    private const val TAG = "AcousticRadarManager"

    private val environmentAnalyzer = EnvironmentAnalyzer()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Live Decibel Level (SPL dBA)
    private val _currentSplDb = MutableStateFlow(38.0f)
    val currentSplDb: StateFlow<Float> = _currentSplDb.asStateFlow()

    // Environment Classification
    private val _currentEnvironment = MutableStateFlow(AcousticEnvironment.QUIET)
    val currentEnvironment: StateFlow<AcousticEnvironment> = _currentEnvironment.asStateFlow()

    // Noise Floor Energy Estimate (RMS 0.0 to 1.0)
    private val _noiseFloor = MutableStateFlow(0.003f)
    val noiseFloor: StateFlow<Float> = _noiseFloor.asStateFlow()

    // Auto-Calibration Status & Progress
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0f)
    val calibrationProgress: StateFlow<Float> = _calibrationProgress.asStateFlow()

    private val _baselineNoiseFloorDb = MutableStateFlow(38.0f)
    val baselineNoiseFloorDb: StateFlow<Float> = _baselineNoiseFloorDb.asStateFlow()

    // 8-Band Spectral Density Array (Normalized 0.0f - 1.0f for radar animation)
    private val _spectralBands = MutableStateFlow(listOf(0.15f, 0.25f, 0.35f, 0.40f, 0.30f, 0.20f, 0.15f, 0.10f))
    val spectralBands: StateFlow<List<Float>> = _spectralBands.asStateFlow()

    private val isPttActive = AtomicBoolean(false)
    private var calibrationJob: Job? = null

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
            System.err.println("[$TAG] $msg: ${tr?.message}")
        }
    }

    /**
     * Converts raw 16-bit PCM RMS energy to an estimated Sound Pressure Level (SPL dBA).
     * 0 dBFS corresponds to ~98 dBA on standard mobile mic hardware.
     */
    fun calculateSplDb(frameRms: Float): Float {
        if (frameRms <= 1e-5f) return 28.0f
        val dBFS = 20f * log10(frameRms)
        return (dBFS + 98.0f).coerceIn(28.0f, 102.0f)
    }

    /**
     * Analyzes incoming PCM audio frames from the active mic stream (WebRTC or local PTT).
     */
    fun feedPcmFrame(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size) {
        val sampleCount = length / 2
        if (sampleCount <= 0) return

        var frameEnergySum = 0f
        val bandCount = 8
        val samplesPerBand = max(1, sampleCount / bandCount)
        val bandEnergies = FloatArray(bandCount)

        for (i in 0 until sampleCount) {
            val byteIdx = offset + i * 2
            if (byteIdx + 1 >= pcmBytes.size) break
            val low = pcmBytes[byteIdx].toInt() and 0xFF
            val high = pcmBytes[byteIdx + 1].toInt()
            val sample = ((high shl 8) or low) / 32768.0f
            val sampleSq = sample * sample

            frameEnergySum += sampleSq

            val bandIdx = (i / samplesPerBand).coerceIn(0, bandCount - 1)
            bandEnergies[bandIdx] += sampleSq
        }

        val frameRms = sqrt(frameEnergySum / max(1, sampleCount))
        val measuredDb = calculateSplDb(frameRms)

        // Smooth dB reading (fast attack, steady decay)
        val previousDb = _currentSplDb.value
        val smoothedDb = if (measuredDb > previousDb) {
            0.60f * previousDb + 0.40f * measuredDb
        } else {
            0.85f * previousDb + 0.15f * measuredDb
        }
        _currentSplDb.value = smoothedDb

        // Update environment analyzer
        environmentAnalyzer.analyzeFrame(pcmBytes, offset, length)
        _currentEnvironment.value = environmentAnalyzer.currentEnvironment
        _noiseFloor.value = environmentAnalyzer.getNoiseFloor()

        // Update 8-band spectral density
        val bands = List(bandCount) { idx ->
            val bandRms = sqrt(bandEnergies[idx] / samplesPerBand)
            (bandRms * 3.5f).coerceIn(0.08f, 1.0f)
        }
        _spectralBands.value = bands
    }

    /**
     * Notifies the radar whether Push-to-Talk is actively holding the microphone.
     */
    fun setPttActive(active: Boolean) {
        isPttActive.set(active)
        if (active && _isCalibrating.value) {
            logD("Canceling ambient calibration because PTT was initiated")
            calibrationJob?.cancel()
            _isCalibrating.value = false
            _calibrationProgress.value = 0f
        }
    }

    /**
     * Executes a 2.0-second auto-calibration sweep of background noise.
     * Recalibrates ambient baseline noise floor and updates DSP baseline.
     */
    @SuppressLint("MissingPermission")
    fun startAutoCalibration(context: Context, onComplete: ((Float) -> Unit)? = null) {
        if (_isCalibrating.value || isPttActive.get()) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            logD("No RECORD_AUDIO permission for calibration; setting synthetic calibrated baseline")
            _baselineNoiseFloorDb.value = 38.0f
            onComplete?.invoke(38.0f)
            return
        }

        _isCalibrating.value = true
        _calibrationProgress.value = 0f

        calibrationJob = scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            try {
                val sampleRate = 16000
                val minBuffer = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1280)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialized")
                }

                audioRecord.startRecording()
                val buffer = ByteArray(minBuffer)
                val totalSteps = 40 // 40 steps x 50ms = 2000ms
                val collectedDb = mutableListOf<Float>()

                for (step in 1..totalSteps) {
                    if (!isActive || isPttActive.get()) break

                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0f
                        val count = read / 2
                        for (i in 0 until count) {
                            val low = buffer[i * 2].toInt() and 0xFF
                            val high = buffer[i * 2 + 1].toInt()
                            val s = ((high shl 8) or low) / 32768.0f
                            sum += s * s
                        }
                        val rms = sqrt(sum / max(1, count))
                        val db = calculateSplDb(rms)
                        collectedDb.add(db)
                        feedPcmFrame(buffer, 0, read)
                    }

                    _calibrationProgress.value = step.toFloat() / totalSteps
                    delay(50L)
                }

                val finalBaseline = if (collectedDb.isNotEmpty()) {
                    collectedDb.sorted().let { sorted ->
                        // Take the 25th percentile (true ambient noise floor without sudden impulses)
                        val idx = (sorted.size * 0.25).toInt().coerceIn(0, sorted.size - 1)
                        sorted[idx]
                    }
                } else {
                    38.0f
                }

                _baselineNoiseFloorDb.value = finalBaseline
                _currentSplDb.value = finalBaseline
                logD("Auto-calibration complete: Ambient baseline set to ${finalBaseline} dBA")

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(finalBaseline)
                }
            } catch (e: Exception) {
                logE("Error during auto-calibration: ${e.message}", e)
                _baselineNoiseFloorDb.value = 38.0f
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(38.0f)
                }
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                _isCalibrating.value = false
                _calibrationProgress.value = 1f
            }
        }
    }

    /**
     * Performs a fast non-blocking 300ms ambient acoustic sample.
     */
    @SuppressLint("MissingPermission")
    fun sampleAmbientOnce(context: Context) {
        if (_isCalibrating.value || isPttActive.get()) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return

        scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            try {
                val sampleRate = 16000
                val minBuffer = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1280)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )

                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    val buffer = ByteArray(minBuffer)
                    for (i in 0..5) { // ~250ms
                        if (isPttActive.get()) break
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            feedPcmFrame(buffer, 0, read)
                        }
                        delay(40L)
                    }
                }
            } catch (e: Exception) {
                logD("Ambient sample note: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
            }
        }
    }

    private var periodicMonitorJob: Job? = null

    /**
     * Starts background periodic ambient noise monitoring, updating SPL dBA and acoustic environment
     * every [intervalSeconds] (default 15s) while idle. Automatically yields if PTT is active.
     */
    fun startPeriodicMonitoring(context: Context, intervalSeconds: Long = 15L) {
        if (periodicMonitorJob?.isActive == true) return

        periodicMonitorJob = scope.launch(Dispatchers.IO) {
            logD("Started periodic ambient monitoring every ${intervalSeconds}s")
            if (!isPttActive.get() && !_isCalibrating.value) {
                sampleAmbientOnce(context)
            }
            while (isActive) {
                delay(intervalSeconds * 1000L)
                if (!isPttActive.get() && !_isCalibrating.value) {
                    sampleAmbientOnce(context)
                }
            }
        }
    }

    /**
     * Stops periodic ambient monitoring to release resources when screen is left.
     */
    fun stopPeriodicMonitoring() {
        periodicMonitorJob?.cancel()
        periodicMonitorJob = null
        logD("Stopped periodic ambient monitoring")
    }
}
