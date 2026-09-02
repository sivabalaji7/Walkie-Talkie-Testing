package com.example.walkietalkieapp.audio.engine

/**
 * G.711 µ-law Codec
 * 
 * Provides ultra-fast, zero-allocation compression and decompression for voice audio.
 * Reduces 16-bit PCM to 8-bit µ-law (50% bandwidth reduction) while maintaining excellent voice intelligibility.
 * This is crucial for stabilizing Bluetooth and Wi-Fi Direct transports without the CPU overhead of Opus.
 */
object G711Codec {

    private const val BIAS = 0x84
    private const val CLIP = 32635

    private val pcmToMuLawTable = ByteArray(65536)
    private val muLawToPcmTable = ShortArray(256)

    init {
        // Pre-compute lookup tables for O(1) instantaneous encoding/decoding
        for (i in 0..65535) {
            val pcm = (i - 32768).toShort()
            pcmToMuLawTable[i] = encodeSample(pcm)
        }

        for (i in 0..255) {
            muLawToPcmTable[i] = decodeSample(i.toByte())
        }
    }

    /**
     * Encodes 16-bit PCM (Little Endian) to 8-bit µ-law.
     * Allocates a new byte array of exactly half the size of the input.
     */
    fun encode(pcmBytes: ByteArray): ByteArray {
        require(pcmBytes.size % 2 == 0) { "PCM byte array length must be even" }
        val sampleCount = pcmBytes.size / 2
        val muLawBytes = ByteArray(sampleCount)

        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val pcmSample = ((high shl 8) or low)
            // Use lookup table (offset by 32768 to handle unsigned array index)
            val index = (pcmSample + 32768) and 0xFFFF
            muLawBytes[i] = pcmToMuLawTable[index]
        }
        return muLawBytes
    }

    /**
     * Decodes 8-bit µ-law back to 16-bit PCM (Little Endian).
     * Allocates a new byte array of exactly double the size of the input.
     */
    fun decode(muLawBytes: ByteArray): ByteArray {
        val pcmBytes = ByteArray(muLawBytes.size * 2)

        for (i in muLawBytes.indices) {
            val muLawSample = muLawBytes[i].toInt() and 0xFF
            val pcmSample = muLawToPcmTable[muLawSample].toInt()

            pcmBytes[i * 2] = (pcmSample and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((pcmSample shr 8) and 0xFF).toByte()
        }
        return pcmBytes
    }

    private fun encodeSample(pcm: Short): Byte {
        var sign = (pcm.toInt() shr 8) and 0x80
        var sample = pcm.toInt()
        if (sign != 0) {
            sample = -sample
        }
        if (sample > CLIP) {
            sample = CLIP
        }
        sample += BIAS
        var exponent = 7
        var expMask = 0x4000
        while ((sample and expMask) == 0 && exponent > 0) {
            exponent--
            expMask = expMask shr 1
        }
        val mantissa = (sample shr (exponent + 3)) and 0x0F
        val muLaw = (sign or (exponent shl 4) or mantissa).inv()
        return muLaw.toByte()
    }

    private fun decodeSample(muLaw: Byte): Short {
        var muLawInt = muLaw.toInt().inv()
        val sign = muLawInt and 0x80
        val exponent = (muLawInt and 0x70) shr 4
        val mantissa = muLawInt and 0x0F
        var sample = (mantissa shl 3) + BIAS
        sample = sample shl exponent
        sample -= BIAS
        return (if (sign != 0) -sample else sample).toShort()
    }
}
