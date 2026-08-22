package com.example.walkietalkieapp.webrtc

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.walkietalkieapp.socket.SocketManager
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer

private const val TAG = "WebRTCManager"

class WebRTCManager(private val context: Context) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "WebRTCAudioWorker").apply {
            priority = Thread.NORM_PRIORITY + 2
        }
    }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    
    // Voice Buffer for Replay
    private val voiceBuffer = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    private val MAX_BUFFER_FRAMES = 500 // ~10 seconds at 20ms frames
    
    private var isInitialized = false
    private var isSessionActive = false
    private var isTalking = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    
    // Connection state tracking
    private var iceConnectionState = PeerConnection.IceConnectionState.NEW
    var onStateChange: ((PeerConnection.IceConnectionState) -> Unit)? = null
    var onCallConnected: (() -> Unit)? = null
    var onCallDisconnected: (() -> Unit)? = null

    fun isFailed(): Boolean {
        return iceConnectionState == PeerConnection.IceConnectionState.FAILED || 
               iceConnectionState == PeerConnection.IceConnectionState.CLOSED ||
               iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED
    }

    companion object {
        private const val TURN_SERVER = "turn:openrelay.metered.ca:80"
        private const val TURN_USERNAME = "openrelayproject"
        private const val TURN_PASSWORD = "openrelayproject"
        
        @Volatile
        private var isLibraryInitialized = false
        
        fun initializeLibrary(context: Context) {
            synchronized(this) {
                if (!isLibraryInitialized) {
                    val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
                    PeerConnectionFactory.initialize(options)
                    isLibraryInitialized = true
                }
            }
        }
    }
    
    fun init() {
        if (isInitialized) return
        try {
            initializeLibrary(context)
            
            // Optimized audio device module for VoIP
            audioDeviceModule = JavaAudioDeviceModule.builder(context.applicationContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setAudioSource(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setUseStereoInput(false)
                .setUseStereoOutput(false)
                .createAudioDeviceModule()

            // Ensure not muted by default
            audioDeviceModule?.setMicrophoneMute(false)
            audioDeviceModule?.setSpeakerMute(false)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setOptions(PeerConnectionFactory.Options().apply {
                    disableEncryption = false
                    disableNetworkMonitor = false
                })
                .createPeerConnectionFactory()

            isInitialized = true
            Log.d(TAG, "WebRTC init successful")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init WebRTC: ${e.message}")
        }
    }
    
    fun initialize() {
        if (!isInitialized) init()
        try {
            if (localAudioTrack == null) {
                // High-quality audio constraints for Walkie Talkie
                val audioConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
                }
                audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
                localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)
            }
            localAudioTrack?.setEnabled(true)
            audioDeviceModule?.setMicrophoneMute(true)
            Log.d(TAG, "Local audio track initialized and enabled for SDP negotiation — mic muted until PTT")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing tracks: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return iceConnectionState == PeerConnection.IceConnectionState.CONNECTED || 
               iceConnectionState == PeerConnection.IceConnectionState.COMPLETED
    }
    
    fun createPeerConnection() {
        if (peerConnectionFactory == null) return
        
        // If already connected or connecting, don't recreate unless necessary
        if (peerConnection != null && (isConnected() || iceConnectionState == PeerConnection.IceConnectionState.CHECKING)) {
            Log.d(TAG, "PeerConnection already active, skipping creation")
            return
        }
        
        peerConnection?.dispose()
        peerConnection = null
        pendingIceCandidates.clear()
        
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
            
            // TURN Servers (Relay for when direct connection fails)
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.iceCandidatePoolSize = 10 
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    mainHandler.post {
                        try {
                            val json = JSONObject()
                            json.put("sdpMid", candidate.sdpMid)
                            json.put("sdpMLineIndex", candidate.sdpMLineIndex)
                            json.put("candidate", candidate.sdp)
                            SocketManager.sendIceCandidate(json.toString())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onIceCandidate: ${e.message}")
                        }
                    }
                }
                
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                    Log.d(TAG, "Signaling State: $newState")
                    SocketManager.addLog("Signaling: $newState")
                }
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "ICE Connection: $newState")
                    SocketManager.addLog("ICE: $newState")
                    iceConnectionState = newState
                    mainHandler.post { onStateChange?.invoke(newState) }
                    
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            SocketManager.addLog("VOICE LINK READY")
                            // Signal the service that audio is truly flowing
                            mainHandler.post { onCallConnected?.invoke() }
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            SocketManager.addLog("Network handover...")
                            mainHandler.post { restartIce() }
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            mainHandler.post { onCallDisconnected?.invoke() }
                        }
                        else -> {}
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    Log.d(TAG, "ICE Gathering: $newState")
                    SocketManager.addLog("Gathering: $newState")
                }
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(dc: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                    mainHandler.post {
                        val track = receiver.track()
                        if (track is AudioTrack) {
                            Log.d(TAG, "Remote audio track received and enabled")
                            try {
                                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                                audioManager?.isSpeakerphoneOn = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting speakerphone on track", e)
                            }
                            track.setEnabled(true)
                            track.setVolume(1.0)
                            track.addSink { buffer, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
                                val data = ByteArray(buffer.remaining())
                                buffer.get(data)
                                synchronized(voiceBuffer) {
                                    voiceBuffer.add(data)
                                    if (voiceBuffer.size > MAX_BUFFER_FRAMES) {
                                        voiceBuffer.removeAt(0)
                                    }
                                }
                            }
                            SocketManager.addLog("Voice Stream Active")
                        }
                    }
                }
            }
        )
        
        localAudioTrack?.let { track ->
            track.setEnabled(true)
            peerConnection?.addTrack(track, listOf("LOCAL_STREAM"))
        }
        Log.d(TAG, "PeerConnection established")
    }
    
    // PTT audio control — all run on audioExecutor
    fun startTalking() {
        audioExecutor.execute {
            try {
                isTalking = true
                if (!isSessionActive) {
                    isSessionActive = true
                    if (!isInitialized) initialize()
                    createPeerConnection()
                }
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = true
                if (audioDeviceModule == null) initialize()
                localAudioTrack?.setEnabled(true)
                audioDeviceModule?.setMicrophoneMute(false)
                Log.d(TAG, "PTT started — mic enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting audio: ${e.message}")
            }
        }
    }

    fun stopTalking() {
        audioExecutor.execute {
            try {
                isTalking = false
                localAudioTrack?.setEnabled(false)
                audioDeviceModule?.setMicrophoneMute(true)
                Log.d(TAG, "PTT released — mic disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling audio: ${e.message}")
            }
        }
    }

    fun startAudioCapture() {
        if (!isInitialized) initialize()
        startTalking()
    }

    fun stopAudio() {
        stopTalking()
    }
    
    fun createOffer(isRestart: Boolean = false) {
        audioExecutor.execute {
            // Auto-activate session if not yet ready
            if (!isSessionActive) {
                isSessionActive = true
                if (!isInitialized) initialize()
            }
            Log.d(TAG, "Creating offer (restart=$isRestart)")
            
            if (peerConnection == null) {
                createPeerConnection()
            }
            
            // Ensure WebRTC negotiates bidirectional sendrecv
            localAudioTrack?.setEnabled(true)
            if (!isTalking) {
                audioDeviceModule?.setMicrophoneMute(true)
            }
            
            val pc = peerConnection ?: return@execute
            
            if (!isRestart && pc.signalingState() == PeerConnection.SignalingState.STABLE && isConnected()) {
                Log.d(TAG, "Already connected, skipping offer creation")
                return@execute
            }

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                if (isRestart) {
                    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                }
            }
            
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    // Optimize for stability over high-latency links
                    val optimizedSdp = sdp.description
                        .replace("useinbandfec=1", "useinbandfec=1;minptime=20;cbr=1;maxaveragebitrate=32000;stereo=0;sprop-stereo=0")
                    val newSdp = SessionDescription(sdp.type, optimizedSdp)
                    
                    pc.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local offer set, sending to socket")
                            SocketManager.sendOffer(newSdp.description)
                        }
                    }, newSdp)
                }
            }, constraints)
        }
    }

    fun handleOffer(sdp: String) {
        audioExecutor.execute {
            // Auto-activate receiving session if not yet ready
            if (!isSessionActive) {
                Log.d(TAG, "Auto-activating session for incoming offer")
                isSessionActive = true
                if (!isInitialized) initialize()
            }
            Log.d(TAG, "Handling remote offer")
            
            if (peerConnection == null) {
                createPeerConnection()
            }
            
            try {
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager?.isSpeakerphoneOn = true
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring audio for incoming offer", e)
            }
            
            val pc = peerConnection ?: return@execute
            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
            
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote offer set, creating answer")
                    createAnswer()
                    // VERY IMPORTANT: Drain candidates ONLY after remote description is set
                    audioExecutor.execute { drainPendingCandidates() }
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Failed to set remote offer: $error")
                }
            }, sessionDescription)
        }
    }

    private fun createAnswer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        SocketManager.sendAnswer(sdp.description)
                    }
                }, sdp)
            }
        }, constraints)
    }

    fun handleAnswer(sdp: String) {
        audioExecutor.execute {
            val pc = peerConnection ?: return@execute
            Log.d(TAG, "Handling remote answer")
            val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote answer set successfully")
                    audioExecutor.execute { drainPendingCandidates() }
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Failed to set remote answer: $error")
                }
            }, sessionDescription)
        }
    }
    
    fun handleIceCandidate(candidateJson: String) {
        audioExecutor.execute {
            try {
                val json = JSONObject(candidateJson)
                val candidate = IceCandidate(
                    json.optString("sdpMid", "0"),
                    json.optInt("sdpMLineIndex", 0),
                    json.optString("candidate", "")
                )
                
                if (candidate.sdp.isEmpty()) return@execute
                
                val pc = peerConnection
                if (pc != null && pc.remoteDescription != null) {
                    pc.addIceCandidate(candidate)
                } else {
                    pendingIceCandidates.add(candidate)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ICE Candidate Error: ${e.message}")
            }
        }
    }
    
    private fun drainPendingCandidates() {
        val pc = peerConnection ?: return
        val iterator = pendingIceCandidates.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            pc.addIceCandidate(candidate)
            iterator.remove()
        }
    }
    
    fun softReset() {
        Log.d(TAG, "Soft reset: Muting microphone")
        stopTalking()
    }

    fun prepareConnection() {
        Log.d(TAG, "Starting voice session and pre-warming WebRTC connection")
        isSessionActive = true
        audioExecutor.execute {
            if (!isInitialized) initialize()
            createPeerConnection()
        }
    }

    fun replayLastTransmissions() {
        if (voiceBuffer.isEmpty()) {
            SocketManager.addLog("No voice data to replay")
            return
        }
        
        val bufferCopy = synchronized(voiceBuffer) { voiceBuffer.toList() }
        SocketManager.addLog("Replaying last 10s...")
        
        Thread {
            try {
                val sampleRate = 48000 // standard for WebRTC audio
                val minBufferSize = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                
                val audioTrack = android.media.AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize.coerceAtLeast(bufferCopy.sumOf { it.size }),
                    android.media.AudioTrack.MODE_STREAM
                )
                
                audioTrack.play()
                for (data in bufferCopy) {
                    audioTrack.write(data, 0, data.size)
                }
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.e(TAG, "Replay failed: ${e.message}")
            }
        }.start()
    }

    fun restartIce() {
        if (!isSessionActive) return
        audioExecutor.execute {
            Log.d(TAG, "Initiating ICE Restart")
            val pc = peerConnection ?: return@execute
            
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            }
            
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    // Keep optimizations during restart
                    val optimizedSdp = sdp.description
                        .replace("useinbandfec=1", "useinbandfec=1;minptime=10;cbr=1;maxaveragebitrate=64000;stereo=0;sprop-stereo=0")
                    val newSdp = SessionDescription(sdp.type, optimizedSdp)
                    
                    pc.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "ICE Restart offer sent")
                            SocketManager.sendOffer(newSdp.description)
                        }
                    }, newSdp)
                }
            }, constraints)
        }
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up and destroying WebRTC and audio pipeline")
        isSessionActive = false
        stopTalking()
        
        audioExecutor.execute {
            try {
                peerConnection?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing peerConnection: ${e.message}")
            }
            peerConnection = null
            pendingIceCandidates.clear()
            
            try {
                localAudioTrack?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing localAudioTrack: ${e.message}")
            }
            localAudioTrack = null
            
            try {
                audioSource?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing audioSource: ${e.message}")
            }
            audioSource = null
            
            try {
                peerConnectionFactory?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing peerConnectionFactory: ${e.message}")
            }
            peerConnectionFactory = null
            
            try {
                audioDeviceModule?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing audioDeviceModule: ${e.message}")
            }
            audioDeviceModule = null
            
            synchronized(voiceBuffer) {
                voiceBuffer.clear()
            }
            
            try {
                audioManager?.apply {
                    isSpeakerphoneOn = false
                    mode = AudioManager.MODE_NORMAL
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting audioManager: ${e.message}")
            }
            
            isInitialized = false
            iceConnectionState = PeerConnection.IceConnectionState.NEW
        }
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e(TAG, "SDP Failure: $error") }
        override fun onSetFailure(error: String?) { Log.e(TAG, "SDP Set Failure: $error") }
    }
}
