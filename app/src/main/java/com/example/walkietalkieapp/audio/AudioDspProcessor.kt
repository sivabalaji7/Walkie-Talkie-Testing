package com.example.walkietalkieapp.audio

import com.example.walkietalkieapp.audio.intelligence.EnhancementProfile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Vocal-Enhanced Real-Time Audio DSP Engine for Walkie-Talkie (16kHz 16-bit Mono PCM).
 *
 * Processing Pipeline:
 * 1. Wind-Cut High-Pass Filter (200 Hz in VOICE_FOCUS, 120 Hz normally) - Aggressively removes wind & low rumble.
 * 2. Formant Presence EQ (+4.5 dB at 2.2 kHz) - Boosts vocal intelligibility and clarity.
 * 3. Low-Pass Filter (4800 Hz) - Cuts out high hiss and RF noise.
 * 4. Adaptive Noise Gate + Spectral Subtraction - Strongly attenuates background noise.
 * 5. Far-Field Vocal AGC - Dynamically amplifies speech while limiting noise amplification.
 * 6. Soft-Knee Studio Limiter - Prevents distortion when speaking close to the microphone.
 */
class AudioDspProcessor(
    private val sampleRate: Int = 16000
) {
    // -------------------------------------------------------------
    // Voice Activity Detection (VAD) State
    // -------------------------------------------------------------
    var isVoiceActive: Boolean = false
        private set
    
    private var silenceFramesCount = 0
    private val SILENCE_TIMEOUT_FRAMES = 12 // ~480ms hangover before declaring silence

    // -------------------------------------------------------------
    // 1. High-Pass Filter State (120 Hz default cutoff — raised from 75 Hz)
    // Wind noise concentrates in 50–400 Hz; 120 Hz catches more of it while preserving voice fundamentals
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

    // Secondary aggressive wind-cut HPF (300 Hz) — only active in VOICE_FOCUS profile
    private var hp2X1 = 0f
    private var hp2X2 = 0f
    private var hp2Y1 = 0f
    private var hp2Y2 = 0f
    private val hp2B0: Float
    private val hp2B1: Float
    private val hp2B2: Float
    private val hp2A1: Float
    private val hp2A2: Float

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
    // Now with faster upward adaptation to track wind/noise that appears quickly
    // -------------------------------------------------------------
    private var noiseFloorEnergy = 0.003f
    private var smoothedEnergy = 0.008f

    // -------------------------------------------------------------
    // 5. Intelligent Vocal AGC (Dynamic Amplifier)
    // Reduced max gain to prevent noise amplification
    // -------------------------------------------------------------
    private var currentGain = 2.0f
    private val targetSpeechRms = 0.22f // Target robust listening level (~ -13 dBFS)
    private val minGain = 1.0f
    private val maxGain = 4.0f // Reduced from 5.5x to limit noise amplification

    init {
        // 1. Butterworth 2nd order HPF at 120 Hz (raised from 75 Hz for better wind rejection)
        val hpCutoff = 120.0
        val hpW0 = 2.0 * Math.PI * hpCutoff / sampleRate
        val hpCos = Math.cos(hpW0)
        val hpAlpha = Math.sin(hpW0) / (2.0 * 0.7071)
        val hpA0 = 1.0 + hpAlpha

        hpB0 = ((1.0 + hpCos) / (2.0 * hpA0)).toFloat()
        hpB1 = (-(1.0 + hpCos) / hpA0).toFloat()
        hpB2 = ((1.0 + hpCos) / (2.0 * hpA0)).toFloat()
        hpA1 = (-2.0 * hpCos / hpA0).toFloat()
        hpA2 = ((1.0 - hpAlpha) / hpA0).toFloat()

        // 1b. Secondary aggressive wind-cut HPF at 300 Hz (only used in VOICE_FOCUS)
        val hp2Cutoff = 300.0
        val hp2W0 = 2.0 * Math.PI * hp2Cutoff / sampleRate
        val hp2Cos = Math.cos(hp2W0)
        val hp2Alpha = Math.sin(hp2W0) / (2.0 * 0.7071)
        val hp2A0 = 1.0 + hp2Alpha

        hp2B0 = ((1.0 + hp2Cos) / (2.0 * hp2A0)).toFloat()
        hp2B1 = (-(1.0 + hp2Cos) / hp2A0).toFloat()
        hp2B2 = ((1.0 + hp2Cos) / (2.0 * hp2A0)).toFloat()
        hp2A1 = (-2.0 * hp2Cos / hp2A0).toFloat()
        hp2A2 = ((1.0 - hp2Alpha) / hp2A0).toFloat()

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
     * Process 16-bit Mono PCM audio in real time.
     */
    fun process(
        pcmBytes: ByteArray, 
        offset: Int = 0, 
        length: Int = pcmBytes.size,
        profile: EnhancementProfile = EnhancementProfile.BALANCED
    ): ByteArray {
        val sampleCount = length / 2
        val output = ByteArray(length)

        var frameEnergySum = 0f
        
        // Dynamic parameters based on the intelligence profile
        // Key change: much stronger noise suppression, and noise-aware AGC limiting
        val useWindCutFilter: Boolean
        val noiseGateThresholdMultiplier: Float  // how aggressively we gate below noise floor
        val spectralSubtractionStrength: Float   // how much noise energy to subtract
        val useEq: Boolean
        val dynamicMaxGain: Float
        
        when (profile) {
            EnhancementProfile.MINIMAL -> {
                useWindCutFilter = false
                noiseGateThresholdMultiplier = 0f      // no gating
                spectralSubtractionStrength = 0.3f     // gentle
                useEq = false
                dynamicMaxGain = 2.0f
            }
            EnhancementProfile.BALANCED -> {
                useWindCutFilter = false
                noiseGateThresholdMultiplier = 1.5f    // moderate gate
                spectralSubtractionStrength = 0.7f     // strong
                useEq = true
                dynamicMaxGain = 3.5f
            }
            EnhancementProfile.VOICE_FOCUS -> {
                useWindCutFilter = true                 // activate 300 Hz wind cut
                noiseGateThresholdMultiplier = 2.5f    // aggressive gate
                spectralSubtractionStrength = 0.92f    // very strong
                useEq = true
                dynamicMaxGain = 3.0f                  // lower to avoid amplifying residual noise
            }
        }

        for (i in 0 until sampleCount) {
            val byteIdx = offset + i * 2
            val low = pcmBytes[byteIdx].toInt() and 0xFF
            val high = pcmBytes[byteIdx + 1].toInt()
            val shortSample = (high shl 8) or low
            val inSample = shortSample / 32768.0f

            // Stage 1: Primary High-Pass Filter (120 Hz) - Remove wind, rumble, handling noise
            val hpOut = hpB0 * inSample + hpB1 * hpX1 + hpB2 * hpX2 - hpA1 * hpY1 - hpA2 * hpY2
            hpX2 = hpX1
            hpX1 = inSample
            hpY2 = hpY1
            hpY1 = hpOut

            // Stage 1b: Secondary Wind-Cut Filter (300 Hz) — only in VOICE_FOCUS
            val windCutOut = if (useWindCutFilter) {
                val temp = hp2B0 * hpOut + hp2B1 * hp2X1 + hp2B2 * hp2X2 - hp2A1 * hp2Y1 - hp2A2 * hp2Y2
                hp2X2 = hp2X1
                hp2X1 = hpOut
                hp2Y2 = hp2Y1
                hp2Y1 = temp
                temp
            } else {
                hpOut
            }

            // Stage 2: Vocal Presence Boost (+4.5 dB at 2.2 kHz)
            val eqOut = if (useEq) {
                val tempEq = eqB0 * windCutOut + eqB1 * eqX1 + eqB2 * eqX2 - eqA1 * eqY1 - eqA2 * eqY2
                eqX2 = eqX1
                eqX1 = windCutOut
                eqY2 = eqY1
                eqY1 = tempEq
                tempEq
            } else {
                windCutOut
            }

            // Stage 3: Low-Pass Filter (4800 Hz) - Remove electrical hiss & harsh static
            val lpOut = lpB0 * eqOut + lpB1 * lpX1 + lpB2 * lpX2 - lpA1 * lpY1 - lpA2 * lpY2
            lpX2 = lpX1
            lpX1 = eqOut
            lpY2 = lpY1
            lpY1 = lpOut

            // Stage 4: Track instantaneous signal energy
            val sampleAbs = abs(lpOut)
            frameEnergySum += sampleAbs * sampleAbs

            // Stage 5: Adaptive Background Noise Estimation
            // CRITICAL FIX: Much faster upward tracking (was 0.0002, now 0.005)
            // This lets the noise floor rise quickly when wind/noise appears
            smoothedEnergy = 0.985f * smoothedEnergy + 0.015f * sampleAbs
            if (sampleAbs < noiseFloorEnergy) {
                noiseFloorEnergy = 0.997f * noiseFloorEnergy + 0.003f * sampleAbs // fast down
            } else {
                noiseFloorEnergy = 0.995f * noiseFloorEnergy + 0.005f * sampleAbs // much faster up (was 0.0002)
            }

            // Stage 6: Noise Gate + Spectral Subtraction
            // NEW: Two-stage approach:
            //   a) If sample energy is close to noise floor, apply hard gating (reduce to near-zero)
            //   b) Otherwise, apply spectral subtraction proportional to noise ratio
            val noiseFloorSqrt = sqrt(noiseFloorEnergy)
            val cleanedSample: Float
            
            if (noiseGateThresholdMultiplier > 0f && sampleAbs < noiseFloorSqrt * noiseGateThresholdMultiplier) {
                // Below gate threshold — heavily attenuate (soft gate, not hard zero to avoid clicks)
                val gateRatio = sampleAbs / (noiseFloorSqrt * noiseGateThresholdMultiplier + 0.0001f)
                cleanedSample = lpOut * gateRatio * gateRatio // quadratic curve for smooth gating
            } else {
                // Above gate — apply spectral subtraction
                val noiseRatio = (noiseFloorEnergy / (sampleAbs + 0.0001f)).coerceIn(0f, 1f)
                val attenuation = 1f - (spectralSubtractionStrength * noiseRatio)
                cleanedSample = lpOut * attenuation.coerceAtLeast(0.05f) // never fully zero
            }

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
        
        // VAD Logic: Require signal to be meaningfully above noise floor
        val noiseFloorRms = sqrt(noiseFloorEnergy)
        val isCurrentFrameVoiced = frameRms > (noiseFloorRms * 2.5f + 0.005f) // Raised threshold (was 1.5x + 0.003)
        
        if (isCurrentFrameVoiced) {
            silenceFramesCount = 0
            isVoiceActive = true
            
            // Adapt AGC only when someone is actually speaking
            val error = targetSpeechRms - (frameRms * currentGain)
            val adaptationSpeed = if (error > 0) 0.06f else 0.03f // Slower adaptation to avoid pumping
            currentGain = (currentGain + error * adaptationSpeed).coerceIn(minGain, dynamicMaxGain)
        } else {
            silenceFramesCount++
            if (silenceFramesCount > SILENCE_TIMEOUT_FRAMES) {
                isVoiceActive = false
            }
            // NEW: Slowly reduce gain during silence to prevent noise amplification buildup
            if (silenceFramesCount > 3) {
                currentGain = (currentGain * 0.98f).coerceAtLeast(minGain)
            }
        }

        return output
    }

    fun reset() {
        hpX1 = 0f; hpX2 = 0f; hpY1 = 0f; hpY2 = 0f
        hp2X1 = 0f; hp2X2 = 0f; hp2Y1 = 0f; hp2Y2 = 0f
        eqX1 = 0f; eqX2 = 0f; eqY1 = 0f; eqY2 = 0f
        lpX1 = 0f; lpX2 = 0f; lpY1 = 0f; lpY2 = 0f
        noiseFloorEnergy = 0.003f
        smoothedEnergy = 0.008f
        currentGain = 2.0f
        isVoiceActive = false
        silenceFramesCount = 0
    }
}
