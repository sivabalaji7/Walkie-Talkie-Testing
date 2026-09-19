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
}
