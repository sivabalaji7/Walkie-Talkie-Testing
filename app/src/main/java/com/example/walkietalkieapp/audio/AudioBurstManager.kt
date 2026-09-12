package com.example.walkietalkieapp.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.example.walkietalkieapp.audio.engine.G711Codec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val TAG = "AudioBurstManager"

/**
 * Tactical In-Memory Audio Burst Engine.
 *
 * Implements ultra-reliable, zero-disk-I/O Push-To-Talk voice burst transmission:
 * 1. Capture: Direct in-memory AudioRecord at 16kHz 16-bit PCM mono with AudioDspProcessor.
 * 2. Compression: G.711 µ-law encoding (50% reduction) + GZIP (additional ~40% reduction).
 *    A 3-second transmission is ~22 KB total.
 * 3. Transport: Dispatched over authenticated Supabase Realtime WebSocket (Port 443 WSS).
 *    100% immune to Carrier-Grade NAT (CGNAT), 4G/5G mobile firewalls, and UDP blocks.
 * 4. Playback: Decoded and streamed directly into AudioTrack routed to the device LOUDSPEAKER.
 * 5. Replay: In-memory PCM buffer retained for instant 1-tap replay.
 */
class AudioBurstManager(private val context: Context) {

    private val sampleRate = 16000
    private val minRecordBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val recordBufferSize = if (minRecordBufferSize > 0) minRecordBufferSize.coerceAtLeast(1280) else 1280

    private val minPlayBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val playBufferSize = if (minPlayBufferSize > 0) minPlayBufferSize.coerceAtLeast(2048) else 2048

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AudioBurstWorker").apply { priority = Thread.MAX_PRIORITY }
    }

    private val dspProcessor = AudioDspProcessor(sampleRate = sampleRate)

    @Volatile
    private var isRecording = false
    private var recordThread: Thread? = null
    private var activeAudioRecord: AudioRecord? = null
    private var recordingStartTime = 0L
    private val recordedPcmStream = ByteArrayOutputStream()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _isPlayingIncoming = MutableStateFlow(false)
    val isPlayingIncoming: StateFlow<Boolean> = _isPlayingIncoming.asStateFlow()

    private val _incomingSpeaker = MutableStateFlow<String?>(null)
    val incomingSpeaker: StateFlow<String?> = _incomingSpeaker.asStateFlow()

    var onPlaybackStateChange: ((Boolean, String?) -> Unit)? = null

    // Replay buffer (retains raw PCM of last incoming burst)
    private var lastIncomingPcm: ByteArray? = null
    var lastIncomingSender: String? = null
        private set
    var lastIncomingDurationMs: Long = 0L
        private set

    // Playback queue for incoming bursts
    private data class IncomingBurstItem(val sender: String, val pcmData: ByteArray, val durationMs: Long)
    private val playbackQueue = ConcurrentLinkedQueue<IncomingBurstItem>()
    @Volatile
    private var isDrainingQueue = false

    private var toneGenerator: ToneGenerator? = null
    private var maxDurationRunnable: Runnable? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init warning", e)
        }
    }

    // =========================================================================
    // 1. CAPTURE PIPELINE (In-Memory AudioRecord -> DSP -> PCM Buffer)
    // =========================================================================

    @Synchronized
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "startRecording ignored: already recording")
            return false
        }

        try {
            dspProcessor.reset()
            synchronized(recordedPcmStream) {
                recordedPcmStream.reset()
            }

            val recorder = createAudioRecord() ?: run {
                Log.e(TAG, "Failed to create AudioRecord instance")
                return false
            }

            recorder.startRecording()
            activeAudioRecord = recorder
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            _isTransmitting.value = true

            recordThread = Thread {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                val buffer = ByteArray(recordBufferSize)
                while (isRecording) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0 && isRecording) {
                        val processed = dspProcessor.process(buffer, 0, read)
                        synchronized(recordedPcmStream) {
                            recordedPcmStream.write(processed, 0, processed.size)
                        }
                    }
                }
            }.apply {
                name = "AudioBurstRecordThread"
                start()
            }

            Log.d(TAG, "AudioBurst recording STARTED (16kHz PCM mono)")

            // 25-second maximum transmission cutoff
            maxDurationRunnable?.let { mainHandler.removeCallbacks(it) }
            maxDurationRunnable = Runnable {
                if (isRecording) {
                    Log.w(TAG, "Max 25s transmission cutoff reached. Automatically releasing PTT.")
                    stopRecordingAndBroadcast { _, _ -> }
                }
            }
            mainHandler.postDelayed(maxDurationRunnable!!, 25000L)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord", e)
            com.example.walkietalkieapp.socket.SupabaseRealtimeManager.addLog("Mic Error: ${e.message ?: e.javaClass.simpleName}")
            cleanupRecording()
            return false
        }
    }

    @Synchronized
    fun stopRecordingAndBroadcast(onAudioReady: (audioBase64: String, durationMs: Long) -> Unit) {
        if (!isRecording) {
            Log.w(TAG, "stopRecording ignored: not recording")
            return
        }

        maxDurationRunnable?.let { mainHandler.removeCallbacks(it) }
        maxDurationRunnable = null

        val duration = System.currentTimeMillis() - recordingStartTime
        isRecording = false
        _isTransmitting.value = false

        val recorder = activeAudioRecord
        val thread = recordThread
        activeAudioRecord = null
        recordThread = null

        audioExecutor.execute {
            try {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping AudioRecord", e)
                }
                thread?.join(150)

                val rawPcm = synchronized(recordedPcmStream) { recordedPcmStream.toByteArray() }
                Log.d(TAG, "Raw recorded PCM bytes: ${rawPcm.size}, duration: ${duration}ms")

                // Micro-tap guard: if less than 3200 bytes (~100ms), ignore
                if (rawPcm.size >= 3200) {
                    // 1. Encode PCM to G.711 µ-law (halves size from 16-bit to 8-bit)
                    val g711Bytes = G711Codec.encode(rawPcm)
                    
                    // 2. Compress G.711 with GZIP
                    val compressedBytes = gzipCompress(g711Bytes)

                    // 3. Base64 encode
                    val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                    val actualDuration = (rawPcm.size * 1000L) / 32000L

                    Log.d(TAG, "AudioBurst compressed: ${rawPcm.size}B raw -> ${g711Bytes.size}B G711 -> ${compressedBytes.size}B gzip (b64 len: ${base64.length}) in ${actualDuration}ms")

                    mainHandler.post {
                        onAudioReady(base64, actualDuration)
                    }
                } else {
                    Log.w(TAG, "AudioBurst discarded: too short (${rawPcm.size} bytes)")
                    com.example.walkietalkieapp.socket.SupabaseRealtimeManager.addLog("Burst too short: discarded")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding audio burst", e)
                com.example.walkietalkieapp.socket.SupabaseRealtimeManager.addLog("Encoding Error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                synchronized(recordedPcmStream) { recordedPcmStream.reset() }
            }
        }
    }

    private fun cleanupRecording() {
        isRecording = false
        _isTransmitting.value = false
        try {
            activeAudioRecord?.stop()
            activeAudioRecord?.release()
        } catch (ignored: Exception) {}
        activeAudioRecord = null
        recordThread = null
        synchronized(recordedPcmStream) { recordedPcmStream.reset() }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        if (recordBufferSize <= 0) return null
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recordBufferSize
            ).also { rec ->
                setupAudioEffects(rec.audioSessionId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord with MIC, fallback to VOICE_COMMUNICATION", e)
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufferSize
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Failed fallback AudioRecord", e2)
                null
            }
        }
    }

    private fun setupAudioEffects(sessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.enabled = true
            }
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.enabled = true
            }
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.enabled = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioFx setup warning", e)
        }
    }

    // =========================================================================
    // 2. PLAYBACK PIPELINE (Incoming Burst -> Decode -> Loudspeaker AudioTrack)
    // =========================================================================

    fun playIncomingBurst(sender: String, audioBase64: String, durationMs: Long) {
        if (audioBase64.isBlank()) return

        audioExecutor.execute {
            try {
                val compressedBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                if (compressedBytes.isEmpty()) return@execute

                // Decompress GZIP if compressed, otherwise treat as raw G711
                val g711Bytes = gzipDecompress(compressedBytes)

                // Decode G.711 µ-law to 16-bit PCM
                val pcmData = G711Codec.decode(g711Bytes)

                lastIncomingPcm = pcmData
                lastIncomingSender = sender
                lastIncomingDurationMs = durationMs

                Log.d(TAG, "Incoming AudioBurst queued: sender=$sender, pcmSize=${pcmData.size} bytes, duration=${durationMs}ms")

                playbackQueue.offer(IncomingBurstItem(sender, pcmData, durationMs))
                drainPlaybackQueue()
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding incoming burst from $sender", e)
                com.example.walkietalkieapp.socket.SupabaseRealtimeManager.addLog("Decode Error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun replayLastBurst(): Boolean {
        val pcm = lastIncomingPcm
        val sender = lastIncomingSender ?: "Squad Member"
        val duration = lastIncomingDurationMs

        if (pcm != null && pcm.isNotEmpty()) {
            Log.d(TAG, "Replaying last AudioBurst: sender=$sender, pcmSize=${pcm.size} bytes")
            playbackQueue.offer(IncomingBurstItem(sender, pcm, duration))
            drainPlaybackQueue()
            return true
        } else {
            Log.w(TAG, "Replay buffer is empty")
            return false
        }
    }

    @Synchronized
    private fun drainPlaybackQueue() {
        if (isDrainingQueue) return

        val nextItem = playbackQueue.poll() ?: return
        isDrainingQueue = true

        audioExecutor.execute {
            executeTrackPlayback(nextItem)
        }
    }

    private fun executeTrackPlayback(item: IncomingBurstItem) {
        var track: AudioTrack? = null
        try {
            // Force hardware loudspeaker output at maximum clarity
            audioManager?.apply {
                mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                isSpeakerphoneOn = true
            }

            // Play incoming alert chirp
            playTacticalChirp()
            Thread.sleep(120)

            mainHandler.post {
                _isPlayingIncoming.value = true
                _incomingSpeaker.value = item.sender
                onPlaybackStateChange?.invoke(true, item.sender)
            }

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(playBufferSize.coerceAtLeast(8192)) // Safe standard stream buffer size
                .build()

            track.play()
            track.write(item.pcmData, 0, item.pcmData.size)

            // Sleep duration of playback to maintain smooth squelch and temporal continuity
            val playMs = (item.pcmData.size * 1000L) / 32000L
            Thread.sleep(playMs.coerceAtLeast(100L) + 50L)

            track.stop()
            track.release()
            track = null

        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error for ${item.sender}", e)
            com.example.walkietalkieapp.socket.SupabaseRealtimeManager.addLog("Playback Error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            try { track?.release() } catch (ignored: Exception) {}

            mainHandler.post {
                _isPlayingIncoming.value = false
                _incomingSpeaker.value = null
                onPlaybackStateChange?.invoke(false, null)
            }

            isDrainingQueue = false
            // Drain remaining queued bursts if any arrived during playback
            drainPlaybackQueue()
        }
    }

    private fun playTacticalChirp() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
        } catch (e: Exception) {
            Log.w(TAG, "Error playing chirp", e)
        }
    }

    // =========================================================================
    // 3. COMPRESSION UTILITIES (GZIP)
    // =========================================================================

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(data: ByteArray): ByteArray {
        return try {
            if (data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
                val bis = ByteArrayInputStream(data)
                GZIPInputStream(bis).use { it.readBytes() }
            } else {
                data
            }
        } catch (e: Exception) {
            Log.w(TAG, "GZIP decompress fallback to raw bytes", e)
            data
        }
    }

    fun release() {
        cleanupRecording()
        playbackQueue.clear()
        isDrainingQueue = false
        try { toneGenerator?.release() } catch (ignored: Exception) {}
        toneGenerator = null
        audioExecutor.shutdownNow()
        scope.cancel()
    }
}
