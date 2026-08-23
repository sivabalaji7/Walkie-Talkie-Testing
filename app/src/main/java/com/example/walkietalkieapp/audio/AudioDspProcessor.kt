package com.example.walkietalkieapp.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Vocal-Enhanced Real-Time Audio DSP Engine for Walkie-Talkie (16kHz 16-bit Mono PCM).
 *
 * Designed specifically for normal half-foot / arm's length distance:
 * 1. High-Pass Filter (75 Hz) - Removes wind & low rumble.
 * 2. Formant Presence EQ (+4.5 dB at 2.2 kHz) - Boosts vocal intelligibility and clarity.
 * 3. Low-Pass Filter (4800 Hz) - Cuts out high hiss and RF noise.
 * 4. Adaptive Spectral Noise Reducer - Attenuates background fan/room hum smoothly.
 * 5. Far-Field Vocal AGC (Automatic Gain Control) - Dynamically amplifies distant/quiet speech up to 5x.
 * 6. Soft-Knee Studio Limiter - Prevents distortion when speaking close to the microphone.
 */
class AudioDspProcessor(
    private val sampleRate: Int = 16000
) {
    // -------------------------------------------------------------
    // 1. High-Pass Filter State (75 Hz cutoff)
    // -------------------------------------------------------------
    private var hpX1 = 0f
    private var hpX2 = 0f
    private var hpY1 = 0f
    private var hpY2 = 0f
    private val hpB0: Float
    private val hpB1: Float
    private val hpB2: Float
    private val hpA1: Float
    private val hpA2: Float

    // -------------------------------------------------------------
    // 2. Vocal Formant Peaking Filter (+4.5 dB at 2200 Hz, Q = 1.2)
    // -------------------------------------------------------------
    private var eqX1 = 0f
    private var eqX2 = 0f
    private var eqY1 = 0f
    private var eqY2 = 0f
    private val eqB0: Float
    private val eqB1: Float
    private val eqB2: Float
    private val eqA1: Float
    private val eqA2: Float

    // -------------------------------------------------------------
    // 3. Low-Pass Filter State (4800 Hz cutoff)
    // -------------------------------------------------------------
    private var lpX1 = 0f
    private var lpX2 = 0f
    private var lpY1 = 0f
    private var lpY2 = 0f
    private val lpB0: Float
    private val lpB1: Float
    private val lpB2: Float
    private val lpA1: Float
    private val lpA2: Float

    // -------------------------------------------------------------
    // 4. Adaptive Background Noise Floor Tracker
    // -------------------------------------------------------------
    private var noiseFloorEnergy = 0.003f
    private var smoothedEnergy = 0.008f

    // -------------------------------------------------------------
    // 5. Intelligent Vocal AGC (Dynamic Amplifier)
    // -------------------------------------------------------------
    private var currentGain = 2.8f
    private val targetSpeechRms = 0.22f // Target robust listening level (~ -13 dBFS)
    private val minGain = 1.2f
    private val maxGain = 5.5f // Up to 5.5x gain boost for distant speech

    init {
        // 1. Butterworth 2nd order HPF at 75 Hz
        val hpCutoff = 75.0
        val hpW0 = 2.0 * Math.PI * hpCutoff / sampleRate
        val hpCos = Math.cos(hpW0)
        val hpAlpha = Math.sin(hpW0) / (2.0 * 0.7071)
        val hpA0 = 1.0 + hpAlpha

        hpB0 = ((1.0 + hpCos) / (2.0 * hpA0)).toFloat()
        hpB1 = (-(1.0 + hpCos) / hpA0).toFloat()
        hpB2 = ((1.0 + hpCos) / (2.0 * hpA0)).toFloat()
        hpA1 = (-2.0 * hpCos / hpA0).toFloat()
        hpA2 = ((1.0 - hpAlpha) / hpA0).toFloat()

        // 2. Vocal Formant Peaking Filter (+4.5 dB gain at 2200 Hz, Q = 1.2)
        val f0 = 2200.0
        val gainDb = 4.5
        val A = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * Math.PI * f0 / sampleRate
        val cosW0 = Math.cos(w0)
        val sinW0 = Math.sin(w0)
        val alpha = sinW0 / (2.0 * 1.2)

        val eqA0 = 1.0 + alpha / A
        eqB0 = ((1.0 + alpha * A) / eqA0).toFloat()
        eqB1 = ((-2.0 * cosW0) / eqA0).toFloat()
        eqB2 = ((1.0 - alpha * A) / eqA0).toFloat()
        eqA1 = ((-2.0 * cosW0) / eqA0).toFloat()
        eqA2 = ((1.0 - alpha / A) / eqA0).toFloat()

        // 3. Butterworth 2nd order LPF at 4800 Hz
        val lpCutoff = 4800.0
        val lpW0 = 2.0 * Math.PI * lpCutoff / sampleRate
        val lpCos = Math.cos(lpW0)
        val lpAlpha = Math.sin(lpW0) / (2.0 * 0.7071)
        val lpA0 = 1.0 + lpAlpha

        lpB0 = ((1.0 - lpCos) / (2.0 * lpA0)).toFloat()
        lpB1 = ((1.0 - lpCos) / lpA0).toFloat()
        lpB2 = ((1.0 - lpCos) / (2.0 * lpA0)).toFloat()
        lpA1 = (-2.0 * lpCos / lpA0).toFloat()
        lpA2 = ((1.0 - lpAlpha) / lpA0).toFloat()
    }

    /**
     * Process 16-bit Mono PCM audio in real time:
     * - Filters unwanted sub-bass & high-frequency hiss
     * - Enhances vocal presence & intelligibility
     * - Smoothly subtracts stationary background noise
     * - Amplifies distant speech to clear, rich volume
     * - Soft-limits close loud peaks without distortion
     */
    fun process(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size): ByteArray {
        val sampleCount = length / 2
        val output = ByteArray(length)

        var frameEnergySum = 0f

        for (i in 0 until sampleCount) {
            val byteIdx = offset + i * 2
            val low = pcmBytes[byteIdx].toInt() and 0xFF
            val high = pcmBytes[byteIdx + 1].toInt()
            val shortSample = (high shl 8) or low
            val inSample = shortSample / 32768.0f

            // Stage 1: High-Pass Filter (75 Hz) - Remove wind, rumble, handling noise
            val hpOut = hpB0 * inSample + hpB1 * hpX1 + hpB2 * hpX2 - hpA1 * hpY1 - hpA2 * hpY2
            hpX2 = hpX1
            hpX1 = inSample
            hpY2 = hpY1
            hpY1 = hpOut

            // Stage 2: Vocal Presence Boost (+4.5 dB at 2.2 kHz)
            val eqOut = eqB0 * hpOut + eqB1 * eqX1 + eqB2 * eqX2 - eqA1 * eqY1 - eqA2 * eqY2
            eqX2 = eqX1
            eqX1 = hpOut
            eqY2 = eqY1
            eqY1 = eqOut

            // Stage 3: Low-Pass Filter (4800 Hz) - Remove electrical hiss & harsh static
            val lpOut = lpB0 * eqOut + lpB1 * lpX1 + lpB2 * lpX2 - lpA1 * lpY1 - lpA2 * lpY2
            lpX2 = lpX1
            lpX1 = eqOut
            lpY2 = lpY1
            lpY1 = lpOut

            // Stage 4: Track instantaneous signal energy
            val sampleAbs = abs(lpOut)
            frameEnergySum += sampleAbs * sampleAbs

            // Stage 5: Adaptive Background Noise Estimation (Smooth Floor Tracker)
            smoothedEnergy = 0.992f * smoothedEnergy + 0.008f * sampleAbs
            if (sampleAbs < noiseFloorEnergy) {
                noiseFloorEnergy = 0.998f * noiseFloorEnergy + 0.002f * sampleAbs
            } else {
                noiseFloorEnergy = 0.9998f * noiseFloorEnergy + 0.0002f * sampleAbs
            }

            // Stage 6: Smooth Spectral Subtraction (Reduces stationary noise, never cuts speech)
            val noiseRatio = (noiseFloorEnergy / (sampleAbs + 0.0002f)).coerceIn(0f, 1f)
            val attenuation = 1f - (0.55f * noiseRatio) // Maximum ~6dB gentle background reduction
            val cleanedSample = lpOut * attenuation

            // Stage 7: Vocal Amplification (AGC)
            val amplifiedSample = cleanedSample * currentGain

            // Stage 8: Soft-Knee Studio Limiter (Tanh Curve) - No digital clipping
            val limited = if (abs(amplifiedSample) > 0.80f) {
                val sign = Math.signum(amplifiedSample)
                val over = abs(amplifiedSample) - 0.80f
                sign * (0.80f + 0.18f * Math.tanh((over / 0.18f).toDouble()).toFloat())
            } else {
                amplifiedSample
            }

            // Convert back to 16-bit PCM
            val clampedShort = (limited * 32767.0f).toInt().coerceIn(-32768, 32767)
            output[i * 2] = (clampedShort and 0xFF).toByte()
            output[i * 2 + 1] = ((clampedShort shr 8) and 0xFF).toByte()
        }

        // Fast & smooth AGC adaptation per 40ms frame
        val frameRms = sqrt(frameEnergySum / max(1, sampleCount))
        if (frameRms > 0.008f) { // If speech is active (even distant voice)
            val error = targetSpeechRms - (frameRms * currentGain)
            val adaptationSpeed = if (error > 0) 0.08f else 0.04f
            currentGain = (currentGain + error * adaptationSpeed).coerceIn(minGain, maxGain)
        }

        return output
    }

    fun reset() {
        hpX1 = 0f; hpX2 = 0f; hpY1 = 0f; hpY2 = 0f
        eqX1 = 0f; eqX2 = 0f; eqY1 = 0f; eqY2 = 0f
        lpX1 = 0f; lpX2 = 0f; lpY1 = 0f; lpY2 = 0f
        noiseFloorEnergy = 0.003f
        smoothedEnergy = 0.008f
        currentGain = 2.8f
    }
}
