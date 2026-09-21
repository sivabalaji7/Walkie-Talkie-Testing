package com.example.walkietalkieapp.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Military-grade Voice Activity Detector (VAD) for Walkie-Talkie communications.
 * Analyzes raw 16-bit PCM audio frames to distinguish authentic human speech
 * from background ambient silence, steady AC/fan noise, and mic button clicks.
 */
object VoiceActivityDetector {
    private const val TAG = "VoiceActivityDetector"

    // Real-time live speech detection flag (for live PTT UI indicator and VOX gating)
    private val _isLiveSpeechActive = MutableStateFlow(false)
    val isLiveSpeechActive: StateFlow<Boolean> = _isLiveSpeechActive.asStateFlow()

    @Volatile
    private var lastLiveVoicedTimestamp: Long = 0L
    private const val LIVE_HOLD_MS = 250L // Bridging window between micro-syllabic pauses

    // Thresholds calibrated for mobile microphones with Android VOICE_COMMUNICATION AGC
    private const val MIN_GLOBAL_PEAK = 0.045f        // Calibrated for natural speech (-27 dBFS)
    private const val MIN_FRAME_PEAK = 0.026f         // Calibrated for softer word inflections (-32 dBFS)
    private const val MIN_ABSOLUTE_RMS = 0.008f       // Absolute floor for speech RMS
    private const val NOISE_FLOOR_MULTIPLIER = 1.50f  // Speech must rise 50% above ambient floor
    private const val MIN_VOICED_DURATION_MS = 140L   // Human speech phonemes last >= 140ms

    /**
     * Determines whether an entire recorded audio transmission contains authentic human speech.
     *
     * Case 1: Human speech detected (returns true) -> Transmitted audio is recorded and saved.
     * Case 2: No speech / silence / ambient hum (returns false) -> Discarded and omitted from recording.
     *
     * @param pcmData Raw 16-bit signed PCM audio bytes
     * @param sampleRate Sampling frequency in Hz (e.g. 48000 or 16000)
     * @param channels 1 for mono, 2 for interleaved stereo
     * @return true if speech is confirmed; false if silence or ambient noise
     */
    fun hasSpeech(
        pcmData: ByteArray,
        sampleRate: Int = 48000,
        channels: Int = 1
    ): Boolean {
        if (pcmData.isEmpty()) return false

        val bytesPerSample = 2 // 16-bit PCM
        val bytesPerFrame = bytesPerSample * channels
        val frameSamples = (sampleRate * 20) / 1000 // 20ms analysis window
        val frameBytes = frameSamples * bytesPerFrame

        if (pcmData.size < frameBytes) {
            // Buffer is shorter than a single 20ms frame
            return false
        }

        val frameCount = pcmData.size / frameBytes
        if (frameCount == 0) return false

        val frameRmsList = FloatArray(frameCount)
        val framePeakList = FloatArray(frameCount)
        var globalPeak = 0f

        for (f in 0 until frameCount) {
            val frameOffset = f * frameBytes
            var sumSquares = 0.0
            var localPeak = 0f

            for (s in 0 until frameSamples) {
                val byteIndex = frameOffset + s * bytesPerFrame
                if (byteIndex + 1 >= pcmData.size) break

                val sample = if (channels == 2 && byteIndex + 3 < pcmData.size) {
                    val left = ((pcmData[byteIndex].toInt() and 0xFF) or (pcmData[byteIndex + 1].toInt() shl 8)).toShort()
                    val right = ((pcmData[byteIndex + 2].toInt() and 0xFF) or (pcmData[byteIndex + 3].toInt() shl 8)).toShort()
                    ((left.toInt() + right.toInt()) / 2).toShort()
                } else {
                    ((pcmData[byteIndex].toInt() and 0xFF) or (pcmData[byteIndex + 1].toInt() shl 8)).toShort()
                }

                val norm = abs(sample / 32768.0f)
                sumSquares += (norm * norm).toDouble()
                if (norm > localPeak) localPeak = norm
            }

            val rms = sqrt(sumSquares / max(1, frameSamples)).toFloat()
            frameRmsList[f] = rms
            framePeakList[f] = localPeak
            if (localPeak > globalPeak) globalPeak = localPeak
        }

        // Must exceed minimum global peak to be considered intelligible speech
        if (globalPeak < MIN_GLOBAL_PEAK) {
            return false
        }

        // Estimate adaptive ambient noise floor using the 15th percentile of frame RMS values
        val sortedRms = frameRmsList.clone().also { it.sort() }
        val percentileIndex = (frameCount * 0.15f).toInt().coerceIn(0, frameCount - 1)
        val ambientNoiseFloor = sortedRms[percentileIndex]

        val dynamicSpeechThreshold = max(MIN_ABSOLUTE_RMS, ambientNoiseFloor * NOISE_FLOOR_MULTIPLIER)

        // Count voiced frames that exceed both RMS threshold and peak speech threshold
        var voicedFrameCount = 0
        for (f in 0 until frameCount) {
            if (frameRmsList[f] >= dynamicSpeechThreshold && framePeakList[f] >= MIN_FRAME_PEAK) {
                voicedFrameCount++
            }
        }

        val totalVoicedMs = voicedFrameCount * 20L
        return totalVoicedMs >= MIN_VOICED_DURATION_MS
    }

    /**
     * Real-time analysis for a single streaming PCM chunk (typically 10-20ms).
     * Used to drive live visual feedback in the PTT button.
     */
    fun feedLivePcmChunk(pcmData: ByteArray, length: Int = pcmData.size, channels: Int = 1) {
        val bytesPerSample = 2 * channels
        val sampleCount = length / bytesPerSample
        if (sampleCount <= 0) {
            _isLiveSpeechActive.value = false
            return
        }

        var sumSquares = 0.0
        var peak = 0f

        for (i in 0 until sampleCount) {
            val idx = i * bytesPerSample
            if (idx + 1 >= pcmData.size) break

            val sample = if (channels == 2 && idx + 3 < pcmData.size) {
                val left = ((pcmData[idx].toInt() and 0xFF) or (pcmData[idx + 1].toInt() shl 8)).toShort()
                val right = ((pcmData[idx + 2].toInt() and 0xFF) or (pcmData[idx + 3].toInt() shl 8)).toShort()
                ((left.toInt() + right.toInt()) / 2).toShort()
            } else {
                ((pcmData[idx].toInt() and 0xFF) or (pcmData[idx + 1].toInt() shl 8)).toShort()
            }

            val norm = abs(sample / 32768.0f)
            sumSquares += (norm * norm).toDouble()
            if (norm > peak) peak = norm
        }

        val rms = sqrt(sumSquares / max(1, sampleCount)).toFloat()
        // Fast attack, smooth decay with speech crest factor verification
        val crestFactor = peak / max(0.001f, rms)
        val isVoiced = (rms >= 0.010f && peak >= 0.020f && crestFactor >= 1.25f) || (rms >= 0.030f)
        
        val now = System.currentTimeMillis()
        if (isVoiced) {
            lastLiveVoicedTimestamp = now
            _isLiveSpeechActive.value = true
        } else if (now - lastLiveVoicedTimestamp > LIVE_HOLD_MS) {
            _isLiveSpeechActive.value = false
        }
    }

    /**
     * Resets live speech state (e.g. when PTT is released).
     */
    fun resetLiveState() {
        lastLiveVoicedTimestamp = 0L
        _isLiveSpeechActive.value = false
    }
}
