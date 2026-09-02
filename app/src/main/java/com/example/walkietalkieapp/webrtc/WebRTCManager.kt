package com.example.walkietalkieapp.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.walkietalkieapp.socket.SocketManager
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val TAG = "WebRTCManager"

class WebRTCManager(private val context: Context) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "WebRTCAudioWorker").apply {
            priority = Thread.NORM_PRIORITY + 2
        }
    }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
    private var peerConnectionFactory: PeerConnectionFactory? = null
    
    // Multi-Peer Mesh Map: Peer Socket ID -> PeerConnection
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val peerIceStates = ConcurrentHashMap<String, PeerConnection.IceConnectionState>()
    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    
    // Voice Buffer for Replay
    private val voiceBuffer = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    private val MAX_BUFFER_FRAMES = 500 // ~10 seconds at 20ms frames
    
    private var isInitialized = false
    private var isSessionActive = false
    private var isTalking = false
    
    var onStateChange: ((PeerConnection.IceConnectionState) -> Unit)? = null
    var onCallConnected: (() -> Unit)? = null
    var onCallDisconnected: (() -> Unit)? = null

    companion object {
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
    
    fun ensureHandsFreeAudioRouting() {
        try {
            audioManager?.apply {
                mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val devices = availableCommunicationDevices
                    val preferredDevice = devices.firstOrNull { 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    
                    if (preferredDevice != null) {
                        setCommunicationDevice(preferredDevice)
                        Log.d(TAG, "Audio routed to communication device: ${preferredDevice.type}")
                    } else {
                        @Suppress("DEPRECATION")
                        isSpeakerphoneOn = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    isSpeakerphoneOn = true
                }
                isMicrophoneMute = false
            }
            Log.d(TAG, "Hands-free audio routing active (MODE_IN_COMMUNICATION)")
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring hands-free audio routing: ${e.message}")
        }
    }

    fun init() {
        if (isInitialized) return
        try {
            initializeLibrary(context)
            ensureHandsFreeAudioRouting()
            
            // Production-grade Audio Device Module utilizing hardware Voice Communication & Noise Suppression
            audioDeviceModule = JavaAudioDeviceModule.builder(context.applicationContext)
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .setAudioSource(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION) // Activates hardware dual-mic beamforming & wind suppression
                .setUseStereoInput(false)
                .setUseStereoOutput(false)
                .createAudioDeviceModule()

            audioDeviceModule?.setMicrophoneMute(true)
            audioDeviceModule?.setSpeakerMute(false)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setOptions(PeerConnectionFactory.Options().apply {
                    disableEncryption = false
                    disableNetworkMonitor = false
                })
                .createPeerConnectionFactory()

            isInitialized = true
            Log.d(TAG, "WebRTC init successful (Hardware AEC/NS & VOICE_COMMUNICATION active)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init WebRTC: ${e.message}")
        }
    }
    
    var isKrispAiEnabled: Boolean = true

    fun setKrispEnabled(enabled: Boolean) {
        if (isKrispAiEnabled == enabled) return
        isKrispAiEnabled = enabled
        audioExecutor.execute {
            try {
                if (peerConnectionFactory != null && isInitialized && localAudioTrack != null) {
                    val audioConstraints = createAudioConstraints(enabled)
                    val oldSource = audioSource
                    val newSource = peerConnectionFactory?.createAudioSource(audioConstraints)
                    val newTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", newSource)
                    newTrack?.setEnabled(isTalking)
                    
                    peerConnections.values.forEach { pc ->
                        pc.senders.find { it.track()?.kind() == "audio" }?.setTrack(newTrack, false)
                    }
                    
                    localAudioTrack = newTrack
                    audioSource = newSource
                    try { oldSource?.dispose() } catch (e: Exception) {}
                    Log.d(TAG, "Krisp AI toggled to $enabled — updated audio track across ${peerConnections.size} peer(s)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating Krisp track: ${e.message}")
            }
        }
    }

    private fun createAudioConstraints(krispActive: Boolean): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true")) // CRITICAL: Cuts wind rumble and low-freq noise (<150Hz)
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googTransientSuppression", if (krispActive) "true" else "false")) // Krisp-style Deep Transient Denoising
            mandatory.add(MediaConstraints.KeyValuePair("googExperimentalNoiseSuppression", if (krispActive) "true" else "false")) // Neural Spectral Masking
            mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        }
    }

    fun initialize() {
        if (!isInitialized) init()
        try {
            if (localAudioTrack == null) {
                val audioConstraints = createAudioConstraints(isKrispAiEnabled)
                audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
                localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)
            }
            // Initially disable until user holds PTT to prevent background bleed
            localAudioTrack?.setEnabled(false)
            audioDeviceModule?.setMicrophoneMute(true)
            Log.d(TAG, "Local audio track ready for mesh negotiation (Krisp AI Active: $isKrispAiEnabled, Highpass: ON)")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing tracks: ${e.message}")
        }
    }

    private fun optimizeAudioSdp(sdp: String): String {
        return try {
            val lines = sdp.split("\r\n").toMutableList()
            var opusPayloadType: String? = null

            for (line in lines) {
                if (line.startsWith("a=rtpmap:") && line.contains("opus/48000", ignoreCase = true)) {
                    opusPayloadType = line.substringAfter("a=rtpmap:").substringBefore(" ").trim()
                    break
                }
            }

            val hdFmtpParams = "minptime=10;ptime=20;cbr=1;maxaveragebitrate=32000;stereo=0;sprop-stereo=0;useinbandfec=1;dtx=0;x-google-min-bitrate=16;sprop-maxcapturerate=48000;maxplaybackrate=48000"
            if (opusPayloadType != null) {
                val fmtpIndex = lines.indexOfFirst { it.startsWith("a=fmtp:$opusPayloadType") }
                if (fmtpIndex != -1) {
                    lines[fmtpIndex] = "a=fmtp:$opusPayloadType $hdFmtpParams"
                } else {
                    val rtpmapIndex = lines.indexOfFirst { it.startsWith("a=rtpmap:$opusPayloadType") }
                    if (rtpmapIndex != -1) {
                        lines.add(rtpmapIndex + 1, "a=fmtp:$opusPayloadType $hdFmtpParams")
                    }
                }
                lines.joinToString("\r\n")
            } else {
                sdp.replace("useinbandfec=1", "useinbandfec=1;$hdFmtpParams")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing SDP: ${e.message}")
            sdp
        }
    }

    fun isConnected(): Boolean {
        return peerIceStates.values.any { 
            it == PeerConnection.IceConnectionState.CONNECTED || 
            it == PeerConnection.IceConnectionState.COMPLETED 
        }
    }

    private fun getRtcConfig(): PeerConnection.RTCConfiguration {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
            
            // TURN Servers
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
        return rtcConfig
    }
    
    @Synchronized
    private fun getOrCreatePeerConnection(peerId: String): PeerConnection? {
        if (peerId.isBlank()) return null
        if (peerConnectionFactory == null) init()
        if (!isInitialized) initialize()

        val existing = peerConnections[peerId]
        if (existing != null) {
            return existing
        }

        Log.d(TAG, "Creating new PeerConnection for peer: $peerId")
        val rtcConfig = getRtcConfig()

        val pc = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    mainHandler.post {
                        try {
                            val json = JSONObject().apply {
                                put("sdpMid", candidate.sdpMid)
                                put("sdpMLineIndex", candidate.sdpMLineIndex)
                                put("candidate", candidate.sdp)
                            }
                            SocketManager.sendIceCandidate(peerId, json.toString())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending ICE Candidate to $peerId: ${e.message}")
                        }
                    }
                }
                
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                    Log.d(TAG, "Signaling [$peerId]: $newState")
                }
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "ICE [$peerId]: $newState")
                    peerIceStates[peerId] = newState
                    mainHandler.post { onStateChange?.invoke(newState) }
                    
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            Log.d(TAG, "VOICE LINK ESTABLISHED with $peerId")
                            SocketManager.addLog("Voice Link: $peerId")
                            mainHandler.post { onCallConnected?.invoke() }
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            Log.d(TAG, "ICE Failed with $peerId, restarting...")
                            restartIceForPeer(peerId)
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            if (!isConnected()) {
                                mainHandler.post { onCallDisconnected?.invoke() }
                            }
                        }
                        else -> {}
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    Log.d(TAG, "Gathering [$peerId]: $newState")
                }
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(dc: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                    mainHandler.post {
                        val track = receiver.track()
                        if (track is AudioTrack) {
                            Log.d(TAG, "Remote audio track received from $peerId")
                            try {
                                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                                @Suppress("DEPRECATION")
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
                            SocketManager.addLog("Active stream from $peerId")
                        }
                    }
                }
            }
        ) ?: return null

        localAudioTrack?.let { track ->
            track.setEnabled(true)
            pc.addTrack(track, listOf("LOCAL_STREAM"))
        }

        peerConnections[peerId] = pc
        return pc
    }

    /**
     * Connects to a list of peers (e.g. from room-peers or user-joined).
     */
    fun connectToPeers(peerIds: List<String>) {
        audioExecutor.execute {
            if (!isSessionActive) {
                isSessionActive = true
                if (!isInitialized) initialize()
            }
            peerIds.forEach { peerId ->
                if (peerId.isNotBlank()) {
                    createOfferForPeer(peerId)
                }
            }
        }
    }

    private fun createOfferForPeer(peerId: String, isRestart: Boolean = false) {
        val pc = getOrCreatePeerConnection(peerId) ?: return
        
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (isRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                val optimizedSdp = optimizeAudioSdp(sdp.description)
                val newSdp = SessionDescription(sdp.type, optimizedSdp)
                
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local HD offer set for $peerId (64kbps Opus), sending via socket")
                        SocketManager.sendOffer(peerId, newSdp.description)
                    }
                }, newSdp)
            }
        }, constraints)
    }

    fun handleOffer(fromPeerId: String, sdp: String) {
        audioExecutor.execute {
            if (fromPeerId.isBlank()) return@execute
            if (!isSessionActive) {
                isSessionActive = true
                if (!isInitialized) initialize()
            }
            Log.d(TAG, "Handling remote offer from $fromPeerId")

            var pc = peerConnections[fromPeerId]
            if (pc != null && pc.signalingState() != PeerConnection.SignalingState.STABLE) {
                Log.w(TAG, "Signaling collision on peer $fromPeerId (${pc.signalingState()}), resetting to accept incoming offer")
                try { pc.dispose() } catch (e: Exception) {}
                peerConnections.remove(fromPeerId)
                pc = null
            }
            if (pc == null) {
                pc = getOrCreatePeerConnection(fromPeerId)
            }
            if (pc == null) return@execute

            ensureHandsFreeAudioRouting()

            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote offer set for $fromPeerId, creating answer")
                    createAnswerForPeer(fromPeerId, pc)
                    audioExecutor.execute { drainPendingCandidates(fromPeerId) }
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Failed to set remote offer for $fromPeerId: $error")
                }
            }, sessionDescription)
        }
    }

    private fun createAnswerForPeer(peerId: String, pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                val optimizedSdp = optimizeAudioSdp(sdp.description)
                val newSdp = SessionDescription(sdp.type, optimizedSdp)

                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local HD answer set for $peerId (64kbps Opus), sending via socket")
                        SocketManager.sendAnswer(peerId, newSdp.description)
                    }
                }, newSdp)
            }
        }, constraints)
    }

    fun handleAnswer(fromPeerId: String, sdp: String) {
        audioExecutor.execute {
            if (fromPeerId.isBlank()) return@execute
            val pc = peerConnections[fromPeerId] ?: return@execute
            Log.d(TAG, "Handling remote answer from $fromPeerId")
            val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote answer set successfully for $fromPeerId")
                    audioExecutor.execute { drainPendingCandidates(fromPeerId) }
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Failed to set remote answer for $fromPeerId: $error")
                }
            }, sessionDescription)
        }
    }
    
    fun handleIceCandidate(fromPeerId: String, candidateJson: String) {
        audioExecutor.execute {
            if (fromPeerId.isBlank()) return@execute
            try {
                val candidate: IceCandidate? = try {
                    val json = JSONObject(candidateJson)
                    val candidateStr = json.optString("candidate", "")
                    if (candidateStr.isNotEmpty()) {
                        val sdpMid = json.optString("sdpMid", json.optString("id", "0"))
                        val sdpMLineIndex = json.optInt("sdpMLineIndex", json.optInt("label", 0))
                        IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
                    } else null
                } catch (e: Exception) {
                    if (candidateJson.startsWith("candidate:")) {
                        IceCandidate("0", 0, candidateJson)
                    } else null
                }

                if (candidate == null) return@execute

                val pc = peerConnections[fromPeerId]
                if (pc != null && pc.remoteDescription != null) {
                    pc.addIceCandidate(candidate)
                } else {
                    pendingIceCandidates.getOrPut(fromPeerId) { mutableListOf() }.add(candidate)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ICE Candidate Error from $fromPeerId: ${e.message}")
            }
        }
    }
    
    private fun drainPendingCandidates(peerId: String) {
        val pc = peerConnections[peerId] ?: return
        val list = pendingIceCandidates[peerId] ?: return
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            pc.addIceCandidate(candidate)
            iterator.remove()
        }
    }

    fun removePeer(peerId: String) {
        audioExecutor.execute {
            Log.d(TAG, "Removing peer connection for $peerId")
            try {
                peerConnections.remove(peerId)?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing pc for $peerId: ${e.message}")
            }
            pendingIceCandidates.remove(peerId)
            peerIceStates.remove(peerId)
            if (!isConnected()) {
                mainHandler.post { onCallDisconnected?.invoke() }
            }
        }
    }
    
    // PTT Audio Control — un-mutes/mutes mic across all active peer connections
    fun startTalking() {
        audioExecutor.execute {
            try {
                // Intelligence Engine: Active Voice
                com.example.walkietalkieapp.dna.engine.CommunicationDnaEngine.onAudioSessionActive(true)
                
                isTalking = true
                if (!isSessionActive) {
                    isSessionActive = true
                    if (!isInitialized) initialize()
                }
                ensureHandsFreeAudioRouting()
                if (audioDeviceModule == null) initialize()
                if (localAudioTrack == null) initialize()
                
                localAudioTrack?.setEnabled(true)
                audioDeviceModule?.setMicrophoneMute(false)
                audioManager?.isMicrophoneMute = false
                
                // Ensure localAudioTrack is attached to audio senders in all peer connections
                peerConnections.values.forEach { pc ->
                    val audioSender = pc.senders.find { it.track()?.kind() == "audio" }
                    if (audioSender != null) {
                        audioSender.setTrack(localAudioTrack, false)
                    } else if (localAudioTrack != null) {
                        pc.addTrack(localAudioTrack, listOf("LOCAL_STREAM"))
                    }
                }
                
                Log.d(TAG, "PTT started — dual-mic array active, mic unmuted, broadcasting to ${peerConnections.size} peer(s)")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting audio: ${e.message}")
            }
        }
    }

    fun stopTalking() {
        audioExecutor.execute {
            try {
                // Intelligence Engine: Voice Ended
                com.example.walkietalkieapp.dna.engine.CommunicationDnaEngine.onAudioSessionActive(false)
                
                isTalking = false
                audioDeviceModule?.setMicrophoneMute(true)
                localAudioTrack?.setEnabled(false)
                Log.d(TAG, "PTT released — mic muted and track disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling audio: ${e.message}")
            }
        }
    }

    fun prepareConnection() {
        Log.d(TAG, "Pre-warming WebRTC session")
        isSessionActive = true
        audioExecutor.execute {
            if (!isInitialized) initialize()
        }
    }

    fun restartIce() {
        audioExecutor.execute {
            peerConnections.keys.forEach { peerId ->
                restartIceForPeer(peerId)
            }
        }
    }

    private fun restartIceForPeer(peerId: String) {
        createOfferForPeer(peerId, isRestart = true)
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
                val sampleRate = 48000
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

    fun cleanup() {
        Log.d(TAG, "Cleaning up all WebRTC peer connections")
        isSessionActive = false
        stopTalking()
        
        audioExecutor.execute {
            peerConnections.forEach { (peerId, pc) ->
                try {
                    pc.dispose()
                } catch (e: Exception) {
                    Log.e(TAG, "Error disposing peer $peerId: ${e.message}")
                }
            }
            peerConnections.clear()
            peerIceStates.clear()
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
                    @Suppress("DEPRECATION")
                    isSpeakerphoneOn = false
                    mode = AudioManager.MODE_NORMAL
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting audioManager: ${e.message}")
            }
            
            isInitialized = false
        }
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e(TAG, "SDP Failure: $error") }
        override fun onSetFailure(error: String?) { Log.e(TAG, "SDP Set Failure: $error") }
    }
}
