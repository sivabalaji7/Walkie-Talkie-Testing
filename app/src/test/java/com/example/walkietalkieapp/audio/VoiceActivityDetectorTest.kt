package com.example.walkietalkieapp.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

class VoiceActivityDetectorTest {

    @Before
    fun setUp() {
        VoiceActivityDetector.resetLiveState()
    }

    @Test
    fun testDigitalSilenceRejected() {
        // 1.0 second of pure 0x00 bytes at 48kHz mono (96,000 bytes)
        val silencePcm = ByteArray(96000) { 0x00 }
        val hasSpeech = VoiceActivityDetector.hasSpeech(silencePcm, sampleRate = 48000, channels = 1)
        assertFalse("Case 2: Pure digital silence must be rejected as no speech", hasSpeech)
    }

    @Test
    fun testSteadyAmbientFanNoiseRejected() {
        // 1.5 seconds of steady low-level AC/fan noise at 48kHz mono
        // Constant low amplitude hum around ~300 out of 32767 (RMS ~0.009, peak ~0.01)
        val sampleRate = 48000
        val totalSamples = (sampleRate * 1.5).toInt()
        val fanPcm = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val hum = (sin(2.0 * Math.PI * 60.0 * i / sampleRate) * 300).toInt().toShort()
            fanPcm[i * 2] = (hum.toInt() and 0xFF).toByte()
            fanPcm[i * 2 + 1] = ((hum.toInt() shr 8) and 0xFF).toByte()
        }

        val hasSpeech = VoiceActivityDetector.hasSpeech(fanPcm, sampleRate = 48000, channels = 1)
        assertFalse("Case 2: Steady low-level ambient fan hum must be rejected", hasSpeech)
    }

    @Test
    fun testTransientClickImpulseRejected() {
        // 1.0 second of silence with a single 20ms click (mechanical button release)
        val sampleRate = 48000
        val totalSamples = sampleRate
        val clickPcm = ByteArray(totalSamples * 2)

        // Add transient spike at 0.5s for 15ms
        val clickStart = sampleRate / 2
        val clickSamples = (sampleRate * 0.015).toInt()
        for (i in clickStart until (clickStart + clickSamples)) {
            val spike = (sin(2.0 * Math.PI * 1000.0 * i / sampleRate) * 5000).toInt().toShort()
            clickPcm[i * 2] = (spike.toInt() and 0xFF).toByte()
            clickPcm[i * 2 + 1] = ((spike.toInt() shr 8) and 0xFF).toByte()
        }

        val hasSpeech = VoiceActivityDetector.hasSpeech(clickPcm, sampleRate = 48000, channels = 1)
        assertFalse("Case 2: Brief transient click (<160ms) must not qualify as human speech", hasSpeech)
    }

    @Test
    fun testRealHumanSpeechAccepted() {
        // 1.2 seconds of speech: vocal fundamental (220 Hz) with envelope modulation
        // Sustained for 600ms with peaks reaching ~6000 (amplitude ~0.18)
        val sampleRate = 48000
        val totalSamples = (sampleRate * 1.2).toInt()
        val speechPcm = ByteArray(totalSamples * 2)

        val speechStart = (sampleRate * 0.2).toInt()
        val speechEnd = (sampleRate * 0.9).toInt()

        for (i in 0 until totalSamples) {
            val sampleVal: Short = if (i in speechStart..speechEnd) {
                val envelope = sin(Math.PI * (i - speechStart) / (speechEnd - speechStart))
                val wave = sin(2.0 * Math.PI * 220.0 * i / sampleRate)
                (wave * envelope * 7000.0).toInt().toShort()
            } else {
                0
            }
            speechPcm[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            speechPcm[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        val hasSpeech = VoiceActivityDetector.hasSpeech(speechPcm, sampleRate = 48000, channels = 1)
        assertTrue("Case 1: Authentic human speech modulation must be detected", hasSpeech)
    }

    @Test
    fun testWhisperSpeechAccepted() {
        // 0.8 seconds containing a 400ms whisper (peaks ~2500, RMS ~0.035)
        val sampleRate = 16000 // 16kHz telephony / offline PTT rate
        val totalSamples = (sampleRate * 0.8).toInt()
        val whisperPcm = ByteArray(totalSamples * 2)

        val speechStart = (sampleRate * 0.1).toInt()
        val speechEnd = (sampleRate * 0.6).toInt()

        for (i in 0 until totalSamples) {
            val sampleVal: Short = if (i in speechStart..speechEnd) {
                val envelope = sin(Math.PI * (i - speechStart) / (speechEnd - speechStart))
                val wave = sin(2.0 * Math.PI * 300.0 * i / sampleRate)
                (wave * envelope * 2800.0).toInt().toShort()
            } else {
                0
            }
            whisperPcm[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            whisperPcm[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        val hasSpeech = VoiceActivityDetector.hasSpeech(whisperPcm, sampleRate = 16000, channels = 1)
        assertTrue("Case 1: Soft speech / whisper with sustained phonemes must be detected", hasSpeech)
    }

    @Test
    fun testLiveChunkFeedback() {
        assertFalse(VoiceActivityDetector.isLiveSpeechActive.value)

        // Silent frame (10ms at 48kHz mono = 480 samples = 960 bytes)
        val silentChunk = ByteArray(960) { 0 }
        VoiceActivityDetector.feedLivePcmChunk(silentChunk, channels = 1)
        assertFalse(VoiceActivityDetector.isLiveSpeechActive.value)

        // Voiced frame (10ms at 48kHz with amplitude 5000)
        val voicedChunk = ByteArray(960)
        for (i in 0 until 480) {
            val sample = (sin(2.0 * Math.PI * 250.0 * i / 48000) * 5000).toInt().toShort()
            voicedChunk[i * 2] = (sample.toInt() and 0xFF).toByte()
            voicedChunk[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        VoiceActivityDetector.feedLivePcmChunk(voicedChunk, channels = 1)
        assertTrue(VoiceActivityDetector.isLiveSpeechActive.value)

        VoiceActivityDetector.resetLiveState()
        assertFalse(VoiceActivityDetector.isLiveSpeechActive.value)
    }
}
