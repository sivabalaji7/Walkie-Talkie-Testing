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
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    
    // Connection state tracking
    private var iceConnectionState = PeerConnection.IceConnectionState.NEW
    var onStateChange: ((PeerConnection.IceConnectionState) -> Unit)? = null

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

            // Ensure speaker is the default output for Walkie Talkie
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true

            // Ensure not muted
            audioDeviceModule?.setMicrophoneMute(false)
            audioDeviceModule?.setSpeakerMute(false)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setOptions(PeerConnectionFactory.Options().apply {
                    disableEncryption = false
                    disableNetworkMonitor = true
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
            // Keep enabled to show microphone icon when session is active
            localAudioTrack?.setEnabled(true)
            Log.d(TAG, "Local audio track initialized with pro-audio processing")
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
            PeerConnection.IceServer.builder(TURN_SERVER)
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_PASSWORD)
                .createIceServer(),
            // Adding an extra fallback TURN server
            PeerConnection.IceServer.builder("turn:turn.metered.ca:80")
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_PASSWORD)
                .createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.iceCandidatePoolSize = 10 // Increased for faster connection
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        
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
                    
                    if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                        SocketManager.addLog("VOICE LINK READY")
                    } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                        SocketManager.addLog("Network handover...")
                        mainHandler.post { restartIce() }
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
            peerConnection?.addTrack(track, listOf("LOCAL_STREAM"))
        }
        Log.d(TAG, "PeerConnection established")
    }
    
    // Task 4: Safe audio control methods
    fun startTalking() {
        try {
            // Re-initialize audio module if needed
            if (audioDeviceModule == null) initialize()
            
            localAudioTrack?.setEnabled(true)
            // Explicitly start audio recording
            audioDeviceModule?.setMicrophoneMute(false)
            
            Log.d(TAG, "Audio track enabled and microphone unmuted")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio: ${e.message}")
        }
    }

    fun stopTalking() {
        try {
            localAudioTrack?.setEnabled(false)
            Log.d(TAG, "Audio disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling audio: ${e.message}")
        }
    }

    fun startAudioCapture() {
        if (!isInitialized) initialize()
        startTalking()
        Log.d(TAG, "Push-to-Talk active")
    }
    
    fun stopAudio() {
        stopTalking()
        Log.d(TAG, "Push-to-Talk inactive")
    }
    
    fun createOffer() {
        Log.d(TAG, "Creating offer")
        
        if (peerConnection == null) {
            createPeerConnection()
        }
        
        startTalking() // Force microphone active
        
        val pc = peerConnection ?: return
        
        // If we already have a remote description, we might just need to renegotiate or we are already linked
        if (pc.signalingState() == PeerConnection.SignalingState.STABLE && isConnected()) {
            Log.d(TAG, "Already connected, skipping offer creation")
            return
        }

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                // Optimize for low latency and high quality walkie talkie audio
                val optimizedSdp = sdp.description
                    .replace("useinbandfec=1", "useinbandfec=1;minptime=10;cbr=1;maxaveragebitrate=64000;stereo=0;sprop-stereo=0")
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

    fun handleOffer(sdp: String) {
        Log.d(TAG, "Handling remote offer")
        
        if (peerConnection == null) {
            createPeerConnection()
        }
        
        startTalking() // Enable mic to respond
        
        val pc = peerConnection ?: return
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote offer set, creating answer")
                createAnswer()
                // VERY IMPORTANT: Drain candidates ONLY after remote description is set
                mainHandler.post { drainPendingCandidates() }
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote offer: $error")
            }
        }, sessionDescription)
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
        val pc = peerConnection ?: return
        Log.d(TAG, "Handling remote answer")
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote answer set successfully")
                mainHandler.post { drainPendingCandidates() }
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote answer: $error")
            }
        }, sessionDescription)
    }
    
    fun handleIceCandidate(candidateJson: String) {
        try {
            val json = JSONObject(candidateJson)
            val candidate = IceCandidate(
                json.getString("sdpMid"),
                json.getInt("sdpMLineIndex"),
                json.getString("candidate")
            )
            
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
        Log.d(TAG, "Soft reset: Muting but keeping peer connection if stable")
        stopTalking()
        // Instead of disposing immediately, we can keep the connection for a few seconds 
        // to avoid renegotiation overhead for rapid-fire PTT.
        // For now, we keep it as is but avoid full disposal if we want "Persistent" link.
        // peerConnection?.dispose() 
        // peerConnection = null
    }

    fun prepareConnection() {
        Log.d(TAG, "Pre-warming WebRTC connection")
        if (!isInitialized) initialize()
        createPeerConnection()
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
        Log.d(TAG, "Initiating ICE Restart")
        val pc = peerConnection ?: return
        
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

    fun cleanup() {
        softReset()
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        audioDeviceModule?.release()
        audioDeviceModule = null
        isInitialized = false
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e(TAG, "SDP Failure: $error") }
        override fun onSetFailure(error: String?) { Log.e(TAG, "SDP Set Failure: $error") }
    }
}
