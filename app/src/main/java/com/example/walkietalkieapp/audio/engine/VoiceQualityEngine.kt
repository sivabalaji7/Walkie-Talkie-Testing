package com.example.walkietalkieapp.audio.engine

import android.content.Context
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

    // Callback used by the Bluetooth/WifiDirect transports to actually send the packet over the network
    var onTransmitLocalPacket: ((VoicePacket) -> Unit)? = null

    fun initialize(context: Context, webRtcRef: WebRTCManager, userId: String) {
        if (audioRecorder == null) {
            audioRecorder = AudioRecorder()
            audioPlayer = AudioPlayer(context)
            this.webRtcManager = webRtcRef
            this.myUserId = userId
            Log.d(TAG, "Voice Quality Engine initialized.")
        }
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
                webRtcManager?.isKrispAiEnabled = shouldEnableKrisp
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
            audioRecorder?.start { processedPcm ->
                // The AudioRecorder internally applies the AudioDspProcessor.
                // We must check if the DSP suppressed this frame (VAD).
                
                // Since AudioRecorder is a black box passing bytes, if it sends an empty array, it's silence.
                if (processedPcm.isEmpty()) return@start // Silence suppressed by VAD

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

                // 3. Hand off to transport
                onTransmitLocalPacket?.invoke(packet)
            }
            currentState = VoiceQualityState.TRANSMITTING
        }
    }

    /**
     * Called when the user releases Push-to-Talk.
     */
    fun stopTransmitting() {
        if (currentState != VoiceQualityState.TRANSMITTING) return
        
        Log.d(TAG, "Stopping transmission.")

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
            onTransmitLocalPacket?.invoke(finalPacket)
        }
        
        currentState = VoiceQualityState.IDLE
    }

    // =========================================================================
    // RECEIVER PIPELINE (Receive -> Jitter Buffer -> Decode -> Playback)
    // =========================================================================
    
    // An independent thread that drains the jitter buffer and plays audio at a steady rate
    private val playbackExecutor = Executors.newSingleThreadExecutor()
    private var isPlaying = false

    /**
     * Called by Bluetooth/WifiDirect transports when a VoicePacket arrives.
     * WebRTC does NOT call this, it handles its own receiving internally.
     */
    fun onPacketReceived(packet: VoicePacket) {
        if (activeTransport == TransportType.Internet) return // Ignore local packets if internet is active
        
        jitterBuffer.push(packet)
        
        if (packet.isFinalFrame) {
            currentState = VoiceQualityState.IDLE
            return
        }
        
        currentState = VoiceQualityState.RECEIVING
        startJitterDrainerIfNeeded()
    }

    private fun startJitterDrainerIfNeeded() {
        if (isPlaying) return
        isPlaying = true

        playbackExecutor.execute {
            Log.d(TAG, "Jitter buffer drainer started.")
            while (isPlaying && currentState == VoiceQualityState.RECEIVING) {
                val packet = jitterBuffer.poll()
                
                if (packet != null) {
                    if (packet.payload.isNotEmpty()) {
                        // 1. Decode G.711 to PCM
                        val pcm = G711Codec.decode(packet.payload)
                        // 2. Play
                        audioPlayer?.play(pcm)
                    }
                } else {
                    // Packet was null. This means either we are buffering, 
                    // or a packet was dropped/lost. 
                    // To keep playback smooth, we just yield a silent frame.
                    val silence = ByteArray(1280) // 40ms of 16kHz silence
                    audioPlayer?.play(silence)
                }

                // Sleep for the exact duration of one frame (40ms) to maintain smooth temporal continuity
                try {
                    Thread.sleep(40)
                } catch (e: InterruptedException) {
                    break
                }
            }
            isPlaying = false
            Log.d(TAG, "Jitter buffer drainer stopped.")
        }
    }

    fun release() {
        audioRecorder?.release()
        audioPlayer?.release()
        playbackExecutor.shutdownNow()
        jitterBuffer.reset()
    }
}
