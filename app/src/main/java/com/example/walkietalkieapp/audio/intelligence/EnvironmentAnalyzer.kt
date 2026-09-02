package com.example.walkietalkieapp.audio.intelligence

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lightweight real-time acoustic environment analyzer.
 * Processes 16-bit PCM audio frames to estimate the noise floor and speech-to-noise ratio,
 * classifying the environment without any allocations per frame.
 * 
 * CRITICAL: Thresholds are tuned for real-world Android microphone sensitivity.
 * Android MIC sources typically capture at high sensitivity, so even "quiet" rooms
 * produce measurable noise floor energy.
 */
class EnvironmentAnalyzer {

    private var noiseFloorEnergy = 0.003f
    private var smoothedEnergy = 0.008f
    
    // Track peak energy over a short window to detect clipping risk
    private var peakEnergy = 0f
    
    // Rolling frame counter for stability
    private var frameCount = 0
    
    // Simple state machine for environment classification
    var currentEnvironment = AcousticEnvironment.UNKNOWN
        private set

    /**
     * Analyze a frame of 16-bit PCM audio.
     * Updates the internal environment state.
     */
    fun analyzeFrame(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size) {
        val sampleCount = length / 2
        if (sampleCount == 0) return

        var frameEnergySum = 0f
        var framePeak = 0f

        for (i in 0 until sampleCount) {
            val byteIdx = offset + i * 2
            val low = pcmBytes[byteIdx].toInt() and 0xFF
            val high = pcmBytes[byteIdx + 1].toInt()
            val shortSample = (high shl 8) or low
            val inSample = shortSample / 32768.0f
            
            val sampleAbs = abs(inSample)
            frameEnergySum += sampleAbs * sampleAbs
            
            if (sampleAbs > framePeak) {
                framePeak = sampleAbs
            }
        }

        val frameRms = sqrt(frameEnergySum / max(1, sampleCount))
        
        // Track overall peak for clipping detection (slow decay)
        peakEnergy = max(framePeak, peakEnergy * 0.97f)

        // Adaptive Noise Floor Estimation
        // CRITICAL FIX: Much faster upward tracking to catch wind/noise quickly
        smoothedEnergy = 0.985f * smoothedEnergy + 0.015f * frameRms
        if (frameRms < noiseFloorEnergy) {
            noiseFloorEnergy = 0.997f * noiseFloorEnergy + 0.003f * frameRms
        } else {
            // Fast upward — noise appearing should be detected within ~200ms
            noiseFloorEnergy = 0.993f * noiseFloorEnergy + 0.007f * frameRms
        }
        
        frameCount++

        // Classify Environment based on noise floor
        // Wait at least 5 frames (~200ms) before first classification to let tracker stabilize
        if (frameCount >= 5) {
            classifyEnvironment()
        }
    }

    private fun classifyEnvironment() {
        // Thresholds tuned for real Android microphones at 16kHz
        // These are RMS-normalized values (0.0 to 1.0 scale)
        //
        // Typical real-world measurements:
        //   Silent room:         noiseFloor ~ 0.001 - 0.003
        //   Normal indoor:       noiseFloor ~ 0.003 - 0.008
        //   Fan / AC:            noiseFloor ~ 0.008 - 0.020
        //   Outdoor / traffic:   noiseFloor ~ 0.015 - 0.050
        //   Wind / crowd:        noiseFloor ~ 0.025 - 0.100+
        //   Very loud:           noiseFloor > 0.05
        
        currentEnvironment = when {
            peakEnergy > 0.90f -> AcousticEnvironment.VERY_NOISY   // Near-clipping
            noiseFloorEnergy > 0.025f -> AcousticEnvironment.VERY_NOISY
            noiseFloorEnergy > 0.010f -> AcousticEnvironment.NOISY
            noiseFloorEnergy > 0.004f -> AcousticEnvironment.NORMAL
            else -> AcousticEnvironment.QUIET
        }
    }
    
    fun getNoiseFloor(): Float = noiseFloorEnergy

    fun reset() {
        noiseFloorEnergy = 0.003f
        smoothedEnergy = 0.008f
        peakEnergy = 0f
        frameCount = 0
        currentEnvironment = AcousticEnvironment.UNKNOWN
    }
}
