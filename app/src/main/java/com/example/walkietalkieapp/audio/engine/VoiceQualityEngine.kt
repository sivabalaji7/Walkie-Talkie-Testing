package com.example.walkietalkieapp.audio.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.walkietalkieapp.audio.AudioPlayer
import com.example.walkietalkieapp.audio.AudioRecorder
import com.example.walkietalkieapp.audio.intelligence.AcousticEnvironment
import com.example.walkietalkieapp.audio.intelligence.EnhancementDiagnostics
import com.example.walkietalkieapp.audio.intelligence.EnhancementPolicyEngine
import com.example.walkietalkieapp.audio.intelligence.EnvironmentAnalyzer
import com.example.walkietalkieapp.dna.model.TransportType
import com.example.walkietalkieapp.webrtc.WebRTCManager
import java.util.concurrent.Executors

/**
 * The Central Voice Quality Engine.
 *
 * This singleton abstracts all audio capture, DSP, encoding, jitter buffering, and playback.
 * It is fully transport-agnostic.
 * - For Internet, it safely defers to WebRTC's robust UDP pipeline.
 * - For Local Mesh (Bluetooth/Wi-Fi Direct), it handles DSP, G.711 compression, and jitter-buffer playback.
 */
class VoiceQualityEngine private constructor() {

    companion object {
        val instance = VoiceQualityEngine()
        private const val TAG = "VoiceQualityEngine"
    }

    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private val jitterBuffer = JitterBuffer()
    
    // We keep a reference to WebRTCManager strictly to delegate Internet transport routing
    private var webRtcManager: WebRTCManager? = null

    var currentState = VoiceQualityState.IDLE
        private set

    // The transport that is currently active. 
    // This dictates whether we use the Local Mesh Pipeline (G711) or the WebRTC Pipeline (Opus)
    var activeTransport: TransportType = TransportType.Internet

    private var packetSequence: Long = 0
    private var myUserId: String = ""
    private val localOfflineRecording = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    private var localOfflineStartTime: Long = 0L

    // Transport-specific callbacks for local mesh routing
    var onTransmitBluetoothPacket: ((VoicePacket) -> Unit)? = null
    var onTransmitWifiDirectPacket: ((VoicePacket) -> Unit)? = null
    var onTransmitLocalPacket: ((VoicePacket) -> Unit)? = null

    private fun routeLocalPacket(packet: VoicePacket) {
        when (activeTransport) {
            TransportType.Bluetooth -> onTransmitBluetoothPacket?.invoke(packet) ?: onTransmitLocalPacket?.invoke(packet)
            TransportType.WifiDirect -> onTransmitWifiDirectPacket?.invoke(packet) ?: onTransmitLocalPacket?.invoke(packet)
            else -> onTransmitLocalPacket?.invoke(packet)
        }
    }

    fun initialize(context: Context, webRtcRef: WebRTCManager? = null, userId: String = "") {
        if (audioRecorder == null) {
            audioRecorder = AudioRecorder()
            audioPlayer = AudioPlayer(context)
        }
        if (webRtcRef != null) {
            this.webRtcManager = webRtcRef
        }
        if (userId.isNotEmpty()) {
            this.myUserId = userId
        }
        Log.d(TAG, "Voice Quality Engine initialized (Recorder/Player ready).")
    }

    // =========================================================================
    // INTELLIGENCE LAYER — Environment-Aware Krisp Management (Internet only)
    // =========================================================================

    /**
     * Called by AudioRecorder's intelligence layer to report the current environment.
     * On Internet transport, this dynamically enables/disables Krisp based on need.
     */
    fun onEnvironmentUpdate(environment: AcousticEnvironment, noiseFloor: Float) {
        lastEnvironment = environment
        lastNoiseFloor = noiseFloor
        
        // For Internet transport, dynamically manage Krisp based on environment
        if (activeTransport == TransportType.Internet) {
            val shouldEnableKrisp = environment != AcousticEnvironment.QUIET
            if (webRtcManager?.isKrispAiEnabled != shouldEnableKrisp) {
                webRtcManager?.setKrispEnabled(shouldEnableKrisp)
                Log.d(TAG, "Intelligence: Krisp AI ${if (shouldEnableKrisp) "ENABLED" else "DISABLED"} (env=$environment)")
            }
        }
    }

    private var lastEnvironment = AcousticEnvironment.UNKNOWN
    private var lastNoiseFloor = 0f

    /**
     * Returns a snapshot of the current enhancement diagnostics for engineering validation.
     */
    fun getDiagnostics(): EnhancementDiagnostics {
        return EnhancementDiagnostics(
            environment = lastEnvironment,
            activeProfile = com.example.walkietalkieapp.audio.intelligence.EnhancementProfile.BALANCED, // reported by AudioRecorder
            noiseFloorEstimate = lastNoiseFloor,
            isKrispActive = webRtcManager?.isKrispAiEnabled ?: false,
            transportType = activeTransport.id
        )
    }

    // =========================================================================
    // CAPTURE PIPELINE (Microphone -> DSP -> Encode -> Transmit)
    // =========================================================================

    /**
     * Called when the user presses Push-to-Talk.
     */
    fun startTransmitting() {
        if (currentState != VoiceQualityState.IDLE) return
        currentState = VoiceQualityState.CAPTURING

        Log.d(TAG, "Starting transmission. Active Transport: $activeTransport")

        if (activeTransport == TransportType.Internet) {
            // Defer to WebRTC for Internet UDP voice
            webRtcManager?.startTalking()
            currentState = VoiceQualityState.TRANSMITTING
        } else {
            // Spin up the Local Mesh Pipeline (Bluetooth / Wi-Fi Direct)
            packetSequence = 0
            localOfflineRecording.clear()
            localOfflineStartTime = System.currentTimeMillis()
            audioRecorder?.start { processedPcm ->
                // The AudioRecorder internally applies the AudioDspProcessor.
                // We must check if the DSP suppressed this frame (VAD).
                if (processedPcm.isEmpty()) return@start // Silence suppressed by VAD

                localOfflineRecording.add(processedPcm.copyOf())

                // 1. Encode via G.711
                val encodedPayload = G711Codec.encode(processedPcm)
                
                // 2. Packetize
                val packet = VoicePacket(
                    sequenceNumber = packetSequence++,
                    timestampMs = System.currentTimeMillis(),
                    senderId = myUserId,
                    payload = encodedPayload,
                    isFinalFrame = false
                )

                // 3. Hand off to active local transport
                routeLocalPacket(packet)
            }
            currentState = VoiceQualityState.TRANSMITTING
        }
    }

    /**
     * Called when the user releases Push-to-Talk.
     */
    fun stopTransmitting() {
        if (currentState == VoiceQualityState.IDLE) return
        
        Log.d(TAG, "Stopping transmission.")

        try {
            if (activeTransport == TransportType.Internet) {
                webRtcManager?.stopTalking()
            } else {
                audioRecorder?.stop()
                // Send one final empty frame to signal the end of transmission to the receiver's jitter buffer
                val finalPacket = VoicePacket(
                    sequenceNumber = packetSequence++,
                    timestampMs = System.currentTimeMillis(),
                    senderId = myUserId,
                    payload = ByteArray(0),
                    isFinalFrame = true
                )
                routeLocalPacket(finalPacket)
                commitLocalOfflineTransmission()
            }
        } finally {
            currentState = VoiceQualityState.IDLE
        }
    }

    private fun commitLocalOfflineTransmission() {
        try {
            val bufferCopy = synchronized(localOfflineRecording) {
                val list = localOfflineRecording.toList()
                localOfflineRecording.clear()
                list
            }
            if (bufferCopy.isNotEmpty()) {
                val totalBytes = bufferCopy.sumOf { it.size }
                val pcmData = ByteArray(totalBytes)
                var destPos = 0
                for (chunk in bufferCopy) {
                    System.arraycopy(chunk, 0, pcmData, destPos, chunk.size)
                    destPos += chunk.size
                }
                val duration = (System.currentTimeMillis() - localOfflineStartTime).coerceAtLeast(300L)
                com.example.walkietalkieapp.audio.VoiceHistoryManager.addTransmission(
                    speakerName = myUserId.ifBlank { "You" },
                    pcmData = pcmData,
                    sampleRate = 16000,
                    durationMs = duration,
                    isSelf = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error committing local offline voice transmission: ${e.message}")
        }
    }

    private fun commitRemoteOfflineTransmission(senderId: String, chunks: List<ByteArray>, startTime: Long) {
        try {
            if (chunks.isNotEmpty()) {
                val totalBytes = chunks.sumOf { it.size }
                val pcmData = ByteArray(totalBytes)
                var destPos = 0
                for (chunk in chunks) {
                    System.arraycopy(chunk, 0, pcmData, destPos, chunk.size)
                    destPos += chunk.size
                }
                val duration = (System.currentTimeMillis() - startTime).coerceAtLeast(300L)
                com.example.walkietalkieapp.audio.VoiceHistoryManager.addTransmission(
                    speakerName = senderId,
                    pcmData = pcmData,
                    sampleRate = 16000,
                    durationMs = duration,
                    isSelf = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error committing remote offline transmission: ${e.message}")
        }
    }

    // =========================================================================
    // RECEIVER PIPELINE (Instant Direct Decode & Low-Latency Hardware Playback)
    // =========================================================================
    
    private val rxBuffer = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    private var rxStartTime = 0L
    private var rxSenderId = "Remote"
    private var rxTimeoutRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Called by Bluetooth/WifiDirect transports when a VoicePacket arrives.
     * WebRTC does NOT call this, it handles its own receiving internally.
     */
    fun onPacketReceived(packet: VoicePacket) {
        if (activeTransport == TransportType.Internet) return // Ignore local packets if internet is active

        if (currentState != VoiceQualityState.RECEIVING) {
            currentState = VoiceQualityState.RECEIVING
            rxStartTime = System.currentTimeMillis()
            rxBuffer.clear()
        }

        if (packet.senderId.isNotBlank()) rxSenderId = packet.senderId

        if (packet.payload.isNotEmpty()) {
            // 1. Instantly decode G.711 to PCM with zero jitter buffer delay
            val pcm = G711Codec.decode(packet.payload)
            // 2. Play immediately into low-latency AudioTrack
            audioPlayer?.play(pcm)
            // 3. Collect for offline blackbox history reel
            rxBuffer.add(pcm)
        }

        // Reset inactivity watchdog timer (500ms without packet concludes transmission)
        rxTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        rxTimeoutRunnable = Runnable {
            if (currentState == VoiceQualityState.RECEIVING) {
                currentState = VoiceQualityState.IDLE
                finalizeRemoteTransmission()
            }
        }
        mainHandler.postDelayed(rxTimeoutRunnable!!, 500L)

        if (packet.isFinalFrame) {
            rxTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            rxTimeoutRunnable = null
            currentState = VoiceQualityState.IDLE
            finalizeRemoteTransmission()
        }
    }

    private fun finalizeRemoteTransmission() {
        val chunks = synchronized(rxBuffer) {
            val list = rxBuffer.toList()
            rxBuffer.clear()
            list
        }
        if (chunks.isNotEmpty()) {
            commitRemoteOfflineTransmission(rxSenderId, chunks, rxStartTime)
        }
        audioPlayer?.resetPrebuffering()
    }

    fun release() {
        rxTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        rxTimeoutRunnable = null
        audioRecorder?.release()
        audioPlayer?.release()
        jitterBuffer.reset()
    }
}
