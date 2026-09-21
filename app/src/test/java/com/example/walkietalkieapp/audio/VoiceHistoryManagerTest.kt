package com.example.walkietalkieapp.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceHistoryManagerTest {

    private fun createSpeechPcm(
        durationMs: Long,
        sampleRate: Int = 48000,
        channels: Int = 1,
        amplitude: Double = 8000.0
    ): ByteArray {
        val totalSamples = ((sampleRate * durationMs) / 1000).toInt()
        val bytesPerSample = 2 * channels
        val pcm = ByteArray(totalSamples * bytesPerSample)
        for (i in 0 until totalSamples) {
            val envelope = sin(PI * i / totalSamples)
            val wave = sin(2.0 * PI * 240.0 * i / sampleRate)
            val sample = (wave * envelope * amplitude).toInt().toShort()
            val byteIdx = i * bytesPerSample
            pcm[byteIdx] = (sample.toInt() and 0xFF).toByte()
            pcm[byteIdx + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            if (channels == 2) {
                pcm[byteIdx + 2] = (sample.toInt() and 0xFF).toByte()
                pcm[byteIdx + 3] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
        }
        return pcm
    }

    @Before
    fun setUp() {
        VoiceHistoryManager.clearHistory()
    }

    @Test
    fun testAddTransmission() {
        val speechPcm = createSpeechPcm(durationMs = 1200L, sampleRate = 48000, channels = 1)
        val added = VoiceHistoryManager.addTransmission(
            speakerName = "Falcon-1",
            pcmData = speechPcm,
            sampleRate = 48000,
            durationMs = 1200L,
            isSelf = false
        )

        assertTrue("Speech transmission must be accepted", added)
        val list = VoiceHistoryManager.transmissions.value
        assertEquals("Reel should contain 1 transmission", 1, list.size)
        val first = list.first()
        assertEquals("Falcon-1", first.speakerName)
        assertEquals(1200L, first.durationMs)
        assertEquals(48000, first.sampleRate)
        assertFalse(first.isSelf)
        assertArrayEquals(speechPcm, first.pcmData)
    }

    @Test
    fun testCase2SilentOrAmbientAudioIgnored() {
        // Accidental PTT button press: 1.5 seconds of silence
        val silentPcm = ByteArray(48000 * 2) { 0 }
        val addedSilent = VoiceHistoryManager.addTransmission(
            speakerName = "Whisper-Ghost",
            pcmData = silentPcm,
            sampleRate = 48000,
            durationMs = 1500L,
            isSelf = true
        )

        assertFalse("Case 2: Silent audio must be ignored and omitted from reel", addedSilent)
        assertEquals("Reel must remain empty after silent transmission", 0, VoiceHistoryManager.transmissions.value.size)

        // Steady low-level noise (e.g. AC fan hum)
        val totalFanSamples = 48000
        val fanPcm = ByteArray(totalFanSamples * 2)
        for (i in 0 until totalFanSamples) {
            val hum = (sin(2.0 * PI * 60.0 * i / 48000) * 200).toInt().toShort()
            fanPcm[i * 2] = (hum.toInt() and 0xFF).toByte()
            fanPcm[i * 2 + 1] = ((hum.toInt() shr 8) and 0xFF).toByte()
        }

        val addedFan = VoiceHistoryManager.addTransmission(
            speakerName = "Fan-Noise",
            pcmData = fanPcm,
            sampleRate = 48000,
            durationMs = 1000L
        )
        assertFalse("Case 2: Ambient noise must be ignored and omitted from reel", addedFan)
        assertEquals(0, VoiceHistoryManager.transmissions.value.size)
    }

    @Test
    fun testRingBufferCappedAt20() {
        for (i in 1..25) {
            val speechPcm = createSpeechPcm(durationMs = 500L, sampleRate = 16000, channels = 1)
            VoiceHistoryManager.addTransmission(
                speakerName = "Speaker-$i",
                pcmData = speechPcm,
                sampleRate = 16000,
                durationMs = 500L,
                isSelf = (i % 2 == 0)
            )
        }

        val list = VoiceHistoryManager.transmissions.value
        assertEquals("Buffer must be capped at 20 transmissions", 20, list.size)
        assertEquals("Newest transmission should be at index 0", "Speaker-25", list.first().speakerName)
        assertEquals("Oldest kept transmission should be Speaker-6", "Speaker-6", list.last().speakerName)
    }

    @Test
    fun testIgnoreTrivialChunks() {
        val dummyPcm = createSpeechPcm(durationMs = 150L, sampleRate = 48000)
        // Duration < 200ms
        val addedShort = VoiceHistoryManager.addTransmission(
            speakerName = "Spam",
            pcmData = dummyPcm,
            durationMs = 150L
        )
        assertFalse("Trivial short chunks must be discarded", addedShort)
        assertEquals(0, VoiceHistoryManager.transmissions.value.size)

        // Empty bytes
        val addedEmpty = VoiceHistoryManager.addTransmission(
            speakerName = "Empty",
            pcmData = ByteArray(0),
            durationMs = 1000L
        )
        assertFalse("Empty byte chunks must be discarded", addedEmpty)
        assertEquals(0, VoiceHistoryManager.transmissions.value.size)
    }

    @Test
    fun testClearHistoryPurgesReel() {
        val speechPcm = createSpeechPcm(durationMs = 600L, sampleRate = 48000)
        VoiceHistoryManager.addTransmission("Test", speechPcm, durationMs = 600L)
        assertEquals(1, VoiceHistoryManager.transmissions.value.size)

        VoiceHistoryManager.clearHistory()
        assertEquals("Reel must be empty after clear / squad exit", 0, VoiceHistoryManager.transmissions.value.size)
        assertNull("Currently playing ID must be null after clear", VoiceHistoryManager.currentlyPlayingId.value)
    }

    @Test
    fun testStereoToMonoDspDownmixing() {
        // Construct 2 stereo frames (16-bit signed PCM, little-endian)
        // Frame 1: Left = 1000 (0x03E8), Right = 3000 (0x0BB8) -> Avg = 2000 (0x07D0)
        // Frame 2: Left = -4000 (0xF060), Right = -2000 (0xF830) -> Avg = -3000 (0xF448)
        val stereoBytes = byteArrayOf(
            // Frame 1 Left: 1000
            0xE8.toByte(), 0x03.toByte(),
            // Frame 1 Right: 3000
            0xB8.toByte(), 0x0B.toByte(),
            // Frame 2 Left: -4000
            0x60.toByte(), 0xF0.toByte(),
            // Frame 2 Right: -2000
            0x30.toByte(), 0xF8.toByte()
        )

        val monoBytes = VoiceHistoryManager.stereoToMono(stereoBytes)
        assertEquals("Mono bytes must be half of stereo bytes", 4, monoBytes.size)

        // Decode Frame 1 Mono
        val m1 = ((monoBytes[0].toInt() and 0xFF) or ((monoBytes[1].toInt() and 0xFF) shl 8)).toShort()
        assertEquals("Frame 1 Mono should be average (2000)", 2000.toShort(), m1)

        // Decode Frame 2 Mono
        val m2 = ((monoBytes[2].toInt() and 0xFF) or ((monoBytes[3].toInt() and 0xFF) shl 8)).toShort()
        assertEquals("Frame 2 Mono should be average (-3000)", (-3000).toShort(), m2)
    }

    @Test
    fun testAddTransmissionStereoAutoDownmixesToMono() {
        val stereoSpeech = createSpeechPcm(durationMs = 1000L, sampleRate = 48000, channels = 2)
        val added = VoiceHistoryManager.addTransmission(
            speakerName = "Bravo-2",
            pcmData = stereoSpeech,
            sampleRate = 48000,
            channels = 2,
            durationMs = 1000L,
            isSelf = false
        )

        assertTrue(added)
        val list = VoiceHistoryManager.transmissions.value
        assertEquals(1, list.size)
        val recorded = list.first()
        assertEquals("Channels should be normalized to 1 (mono) for accurate 1.0x playback", 1, recorded.channels)
        assertEquals("Downmixed PCM data size must be exactly half", stereoSpeech.size / 2, recorded.pcmData.size)
        assertEquals(48000, recorded.sampleRate)
        assertEquals(1000L, recorded.durationMs)
    }
}
