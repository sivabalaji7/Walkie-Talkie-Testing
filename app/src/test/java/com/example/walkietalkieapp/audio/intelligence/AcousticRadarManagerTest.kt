package com.example.walkietalkieapp.audio.intelligence

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class AcousticRadarManagerTest {

    @Test
    fun testCalculateSplDb_nearSilence() {
        val silentDb = AcousticRadarManager.calculateSplDb(0f)
        assertEquals(28.0f, silentDb, 0.1f)

        val tinyRmsDb = AcousticRadarManager.calculateSplDb(1e-6f)
        assertEquals(28.0f, tinyRmsDb, 0.1f)
    }

    @Test
    fun testCalculateSplDb_fullScale() {
        val fullScaleDb = AcousticRadarManager.calculateSplDb(1.0f)
        // 20 * log10(1.0) + 98.0 = 98.0 dBA
        assertEquals(98.0f, fullScaleDb, 0.1f)
    }

    @Test
    fun testCalculateSplDb_nominalSpeech() {
        // Nominal speech at -20 dBFS (RMS ~ 0.1)
        val speechDb = AcousticRadarManager.calculateSplDb(0.1f)
        // 20 * log10(0.1) + 98.0 = -20.0 + 98.0 = 78.0 dBA
        assertEquals(78.0f, speechDb, 0.1f)
    }

    @Test
    fun testFeedPcmFrame_updatesSplAndSpectralBands() {
        // Generate 1600 samples of 16-bit PCM (100ms at 16kHz) with a 1000Hz tone
        val sampleRate = 16000
        val freq = 1000.0
        val sampleCount = 1600
        val pcmBytes = ByteArray(sampleCount * 2)

        for (i in 0 until sampleCount) {
            val sampleVal = (sin(2.0 * Math.PI * freq * i / sampleRate) * 16000.0).toInt().toShort()
            pcmBytes[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        AcousticRadarManager.feedPcmFrame(pcmBytes, 0, pcmBytes.size)

        val currentDb = AcousticRadarManager.currentSplDb.value
        assertTrue("Measured dB should be above silence (> 35 dB), was: $currentDb", currentDb > 35.0f)

        val bands = AcousticRadarManager.spectralBands.value
        assertEquals("Spectral bands should contain 8 elements", 8, bands.size)
        for (b in bands) {
            assertTrue("Band energy must be normalized between 0.08 and 1.0, was: $b", b in 0.08f..1.0f)
        }
    }

    @Test
    fun testPttPriority_cancelsCalibration() {
        AcousticRadarManager.setPttActive(false)
        assertFalse("PTT should be inactive", AcousticRadarManager.isCalibrating.value)

        AcousticRadarManager.setPttActive(true)
        assertFalse("Calibration must be cancelled when PTT is active", AcousticRadarManager.isCalibrating.value)
    }

    @Test
    fun testEnvironmentClassificationColorsAndEnums() {
        assertEquals("QUIET (STUDIO)", AcousticEnvironment.QUIET.displayName)
        assertEquals("NORMAL (ROOM)", AcousticEnvironment.NORMAL.displayName)
        assertEquals("NOISY (TRAFFIC/OFFICE)", AcousticEnvironment.NOISY.displayName)
        assertEquals("VERY NOISY (HIGH SPL)", AcousticEnvironment.VERY_NOISY.displayName)
        assertEquals("CALIBRATING...", AcousticEnvironment.UNKNOWN.displayName)
    }

    @Test
    fun testPeriodicMonitoringLifecycle() {
        AcousticRadarManager.stopPeriodicMonitoring()
        // Idempotent stop should not throw
        AcousticRadarManager.stopPeriodicMonitoring()
    }
}
