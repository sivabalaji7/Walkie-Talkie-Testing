package com.example.walkietalkieapp.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VoiceHistoryManagerTest {

    @Before
    fun setUp() {
        VoiceHistoryManager.clearHistory()
    }

    @Test
    fun testAddTransmission() {
        val dummyPcm = ByteArray(9600) { 0x1A }
        VoiceHistoryManager.addTransmission(
            speakerName = "Falcon-1",
            pcmData = dummyPcm,
            sampleRate = 48000,
            durationMs = 1200L,
            isSelf = false
        )

        val list = VoiceHistoryManager.transmissions.value
        assertEquals("Reel should contain 1 transmission", 1, list.size)
        val first = list.first()
        assertEquals("Falcon-1", first.speakerName)
        assertEquals(1200L, first.durationMs)
        assertEquals(48000, first.sampleRate)
        assertFalse(first.isSelf)
        assertArrayEquals(dummyPcm, first.pcmData)
    }

    @Test
    fun testRingBufferCappedAt20() {
        val dummyPcm = ByteArray(1000) { 0x01 }
        for (i in 1..25) {
            VoiceHistoryManager.addTransmission(
                speakerName = "Speaker-$i",
                pcmData = dummyPcm,
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
        val dummyPcm = ByteArray(100)
        // Duration < 200ms
        VoiceHistoryManager.addTransmission(
            speakerName = "Spam",
            pcmData = dummyPcm,
            durationMs = 150L
        )
        assertEquals("Trivial short chunks must be discarded", 0, VoiceHistoryManager.transmissions.value.size)

        // Empty bytes
        VoiceHistoryManager.addTransmission(
            speakerName = "Empty",
            pcmData = ByteArray(0),
            durationMs = 1000L
        )
        assertEquals("Empty byte chunks must be discarded", 0, VoiceHistoryManager.transmissions.value.size)
    }

    @Test
    fun testClearHistory() {
        val dummyPcm = ByteArray(500)
        VoiceHistoryManager.addTransmission("Test", dummyPcm, durationMs = 500L)
        assertEquals(1, VoiceHistoryManager.transmissions.value.size)

        VoiceHistoryManager.clearHistory()
        assertEquals("Reel must be empty after clear", 0, VoiceHistoryManager.transmissions.value.size)
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
        // 8000 bytes of stereo PCM = 2000 stereo frames
        val stereoPcm = ByteArray(8000) { (it % 128).toByte() }
        VoiceHistoryManager.addTransmission(
            speakerName = "Bravo-2",
            pcmData = stereoPcm,
            sampleRate = 48000,
            channels = 2,
            durationMs = 1000L,
            isSelf = false
        )

        val list = VoiceHistoryManager.transmissions.value
        assertEquals(1, list.size)
        val recorded = list.first()
        assertEquals("Channels should be normalized to 1 (mono) for accurate 1.0x playback", 1, recorded.channels)
        assertEquals("Downmixed PCM data size must be exactly half (4000 bytes)", 4000, recorded.pcmData.size)
        assertEquals(48000, recorded.sampleRate)
        assertEquals(1000L, recorded.durationMs)
    }
}

