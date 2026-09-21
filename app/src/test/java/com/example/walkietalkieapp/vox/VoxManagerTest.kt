package com.example.walkietalkieapp.vox

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class VoxManagerTest {

    private var startPttCount = 0
    private var endPttCount = 0

    @Before
    fun setUp() {
        startPttCount = 0
        endPttCount = 0
        VoxManager.stop()
        VoxManager.isInsideActiveSquad = true
        VoxManager.isChannelBusy = false
        VoxManager.isManualPttActive = false
        VoxManager.registerPttTrigger(
            onStart = { startPttCount++ },
            onEnd = { endPttCount++ }
        )
    }

    private fun generateSinePcm(
        durationMs: Int = 20,
        sampleRate: Int = 16000,
        frequency: Double = 440.0,
        amplitude: Double = 0.5
    ): ByteArray {
        val sampleCount = (sampleRate * durationMs) / 1000
        val pcm = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val angle = 2.0 * Math.PI * frequency * (i.toDouble() / sampleRate)
            val sampleVal = (sin(angle) * amplitude * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            pcm[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    @Test
    fun testDefaultState() {
        assertFalse(VoxManager.isVoxEnabled.value)
        assertEquals(VoxState.OFF, VoxManager.voxState.value)
        assertEquals(VoxSensitivity.MEDIUM, VoxManager.sensitivity.value)
        assertEquals(700L, VoxManager.hangoverDelayMs.value)
    }

    @Test
    fun testEnableAndDisableStateTransitions() {
        VoxManager.setVoxEnabled(true)
        assertTrue(VoxManager.isVoxEnabled.value)
        assertEquals(VoxState.ARMED, VoxManager.voxState.value)

        VoxManager.setVoxEnabled(false)
        assertFalse(VoxManager.isVoxEnabled.value)
        assertEquals(VoxState.OFF, VoxManager.voxState.value)
    }

    @Test
    fun testSensitivitySettings() {
        VoxManager.setSensitivity(VoxSensitivity.LOW)
        assertEquals(VoxSensitivity.LOW, VoxManager.sensitivity.value)
        assertEquals(0.045f, VoxManager.sensitivity.value.threshold, 0.001f)

        VoxManager.setSensitivity(VoxSensitivity.HIGH)
        assertEquals(VoxSensitivity.HIGH, VoxManager.sensitivity.value)
        assertEquals(0.012f, VoxManager.sensitivity.value.threshold, 0.001f)
    }

    @Test
    fun testHangoverDelayClamping() {
        VoxManager.setHangoverDelayMs(400L)
        assertEquals(400L, VoxManager.hangoverDelayMs.value)

        // Below minimum (300ms)
        VoxManager.setHangoverDelayMs(100L)
        assertEquals(300L, VoxManager.hangoverDelayMs.value)

        // Above maximum (2000ms)
        VoxManager.setHangoverDelayMs(5000L)
        assertEquals(2000L, VoxManager.hangoverDelayMs.value)
    }

    @Test
    fun testDigitalSilenceRejection() {
        VoxManager.setVoxEnabled(true)
        val silenceFrame = ByteArray(640) // 20ms of silence

        VoxManager.processIdlePcmFrame(silenceFrame, silenceFrame.size)
        VoxManager.processIdlePcmFrame(silenceFrame, silenceFrame.size)

        assertEquals(0, startPttCount)
        assertEquals(VoxState.ARMED, VoxManager.voxState.value)
    }

    @Test
    fun testAmbientNoiseBelowThresholdRejection() {
        VoxManager.setVoxEnabled(true)
        VoxManager.setSensitivity(VoxSensitivity.MEDIUM) // Threshold = 0.025f

        // Generate low amplitude hum (amplitude = 0.010 -> RMS ~0.007)
        val lowHumFrame = generateSinePcm(durationMs = 20, amplitude = 0.010)

        VoxManager.processIdlePcmFrame(lowHumFrame, lowHumFrame.size)
        VoxManager.processIdlePcmFrame(lowHumFrame, lowHumFrame.size)

        assertEquals("Low hum must not trigger VOX", 0, startPttCount)
        assertEquals(VoxState.ARMED, VoxManager.voxState.value)
    }

    @Test
    fun testSingleTransientPopRejection() {
        VoxManager.setVoxEnabled(true)
        VoxManager.setSensitivity(VoxSensitivity.MEDIUM)

        // Single loud transient pop (1 frame only)
        val loudPopFrame = generateSinePcm(durationMs = 20, amplitude = 0.5)
        val silenceFrame = ByteArray(640)

        // Feed 1 loud frame followed by silence
        VoxManager.processIdlePcmFrame(loudPopFrame, loudPopFrame.size)
        assertEquals("1 frame must not trigger VOX (needs 2 consecutive)", 0, startPttCount)

        VoxManager.processIdlePcmFrame(silenceFrame, silenceFrame.size)
        assertEquals("Single pop followed by silence must reset counter", 0, startPttCount)
    }

    @Test
    fun testSustainedSpeechTriggersAutoKey() {
        VoxManager.setVoxEnabled(true)
        VoxManager.setSensitivity(VoxSensitivity.MEDIUM)

        // Two consecutive speech frames (amplitude = 0.20 -> RMS ~0.14)
        val speechFrame1 = generateSinePcm(durationMs = 20, amplitude = 0.20)
        val speechFrame2 = generateSinePcm(durationMs = 20, amplitude = 0.20)

        VoxManager.processIdlePcmFrame(speechFrame1, speechFrame1.size)
        VoxManager.processIdlePcmFrame(speechFrame2, speechFrame2.size)

        assertEquals("Sustained speech must trigger VOX transmission", VoxState.TRANSMITTING, VoxManager.voxState.value)
    }

    @Test
    fun testRxChannelInterlockSuppressesTrigger() {
        VoxManager.setVoxEnabled(true)
        VoxManager.isChannelBusy = true

        assertEquals(VoxState.INHIBITED_BUSY, VoxManager.voxState.value)

        val speechFrame = generateSinePcm(durationMs = 20, amplitude = 0.50)
        VoxManager.processIdlePcmFrame(speechFrame, speechFrame.size)
        VoxManager.processIdlePcmFrame(speechFrame, speechFrame.size)

        assertEquals("Voice must not trigger VOX when channel is busy", 0, startPttCount)
        assertEquals(VoxState.INHIBITED_BUSY, VoxManager.voxState.value)
    }

    @Test
    fun testOutsideActiveSquadSuppressesTrigger() {
        VoxManager.setVoxEnabled(true)
        VoxManager.isInsideActiveSquad = false

        val speechFrame = generateSinePcm(durationMs = 20, amplitude = 0.50)
        VoxManager.processIdlePcmFrame(speechFrame, speechFrame.size)
        VoxManager.processIdlePcmFrame(speechFrame, speechFrame.size)

        assertEquals("Voice must not trigger VOX when outside squad", 0, startPttCount)
    }

    @Test
    fun testManualPttOverridePrecedence() {
        VoxManager.setVoxEnabled(true)
        VoxManager.isManualPttActive = true

        assertEquals(VoxState.MANUAL_OVERRIDE, VoxManager.voxState.value)

        val speechFrame = generateSinePcm(durationMs = 20, amplitude = 0.50)
        VoxManager.processIdlePcmFrame(speechFrame, speechFrame.size)
        VoxManager.processIdlePcmFrame(speechFrame, speechFrame.size)

        assertEquals("Voice must not trigger VOX during manual PTT override", 0, startPttCount)
    }

    @Test
    fun testTransmittingFrameFeedUpdatesLiveLevel() {
        VoxManager.setVoxEnabled(true)
        VoxManager.isManualPttActive = true

        val speechChunk = generateSinePcm(durationMs = 20, amplitude = 0.40)
        VoxManager.feedTransmittingFrame(speechChunk, channels = 1)

        assertTrue("Live input level must update with incoming frame energy", VoxManager.liveInputLevel.value > 0f)
    }
}
