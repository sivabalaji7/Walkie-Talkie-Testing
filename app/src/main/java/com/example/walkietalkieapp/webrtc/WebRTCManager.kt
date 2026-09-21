package com.example.walkietalkieapp.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.walkietalkieapp.socket.SupabaseRealtimeManager
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val TAG = "WebRTCManager"

class WebRTCManager(private val context: Context) {
    
    // Case-insensitive Peer ID normalization to prevent casing mismatches in multi-device routing
    private fun normKey(peerId: String): String = peerId.trim().lowercase()
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "WebRTCAudioWorker").apply {
            priority = Thread.NORM_PRIORITY + 2
        }
    }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // Multi-Peer Mesh Map: Peer Socket ID -> PeerConnection
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val peerIceStates = ConcurrentHashMap<String, PeerConnection.IceConnectionState>()
    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    
    // Batching ICE candidates to avoid Supabase rate limits
    private val batchedCandidates = ConcurrentHashMap<String, MutableList<String>>()
    private val batchRunnables = ConcurrentHashMap<String, Runnable>()

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    
    // Voice Buffer for Replay & Blackbox Reel
    private val voiceBuffer = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    private val MAX_BUFFER_FRAMES = 500 // ~10 seconds at 20ms frames
    
    // Blackbox Audio Recording Buffers
    private val remoteAudioRecording = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    @Volatile private var activeRemoteSpeaker: String? = null
    private var activeRemoteStartTime: Long = 0L
    private var activeRemoteSampleRate: Int = 48000
    private var activeRemoteChannels: Int = 1

    private val localAudioRecording = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
    private var localAudioStartTime: Long = 0L
    private var localAudioSampleRate: Int = 48000
    private var localAudioChannels: Int = 1

    private var isInitialized = false
    private var isSessionActive = false
    private var isTalking = false
    @Volatile
    private var authorizedSpeaker: String? = null

    fun setAuthorizedSpeaker(speaker: String?) {
        val clean = speaker?.trim()?.lowercase()
        val previousSpeaker = authorizedSpeaker
        authorizedSpeaker = clean
        Log.d(TAG, "Audio gating: setting authorized speaker to $clean across ${peerConnections.size} peer(s)")
        
        if (clean == null && previousSpeaker != null && remoteAudioRecording.isNotEmpty()) {
            commitRemoteAudioTransmission(previousSpeaker)
        } else if (clean != null && !clean.equals(previousSpeaker, ignoreCase = true)) {
            if (previousSpeaker != null && remoteAudioRecording.isNotEmpty()) {
                commitRemoteAudioTransmission(previousSpeaker)
            }
            activeRemoteSpeaker = clean
            activeRemoteStartTime = System.currentTimeMillis()
            remoteAudioRecording.clear()
        }

        audioExecutor.execute {
            peerConnections.forEach { (peerKey, pc) ->
                val isAllowed = clean == null || peerKey.equals(clean, ignoreCase = true) || peerConnections.size == 1
                pc.receivers.forEach { receiver ->
                    val track = receiver.track()
                    if (track is AudioTrack) {
                        track.setEnabled(isAllowed)
                        track.setVolume(if (isAllowed) 1.0 else 0.0)
                    }
                }
            }
        }
    }

    private fun commitRemoteAudioTransmission(speaker: String) {
        try {
            val bufferCopy = synchronized(remoteAudioRecording) {
                val list = remoteAudioRecording.toList()
                remoteAudioRecording.clear()
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
                val duration = (System.currentTimeMillis() - activeRemoteStartTime).coerceAtLeast(300L)
                val saved = com.example.walkietalkieapp.audio.VoiceHistoryManager.addTransmission(
                    speakerName = speaker,
                    pcmData = pcmData,
                    sampleRate = activeRemoteSampleRate,
                    channels = activeRemoteChannels,
                    durationMs = duration,
                    isSelf = false
                )
                if (saved) {
                    Log.d(TAG, "Remote speech from $speaker saved to local Blackbox Reel ($duration ms)")
                } else {
                    Log.d(TAG, "Remote transmission from $speaker had no speech activity — omitted from reel")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error committing remote voice transmission: ${e.message}")
        } finally {
            activeRemoteSpeaker = null
        }
    }
    
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
    
    fun requestAudioFocus() {
        try {
            audioManager?.let { am ->
                if (audioFocusRequest == null) {
                    val playbackAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()

                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener { focusChange ->
                            Log.d(TAG, "Audio focus changed: $focusChange")
                        }
                        .build()
                }
                am.requestAudioFocus(audioFocusRequest!!)
                Log.d(TAG, "Requested System Audio Focus (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus: ${e.message}")
        }
    }

    fun abandonAudioFocus() {
        try {
            audioManager?.let { am ->
                audioFocusRequest?.let { req ->
                    am.abandonAudioFocusRequest(req)
                }
                Log.d(TAG, "Abandoned System Audio Focus")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus: ${e.message}")
        }
    }

    fun ensureHandsFreeAudioRouting() {
        try {
            requestAudioFocus()
            audioManager?.apply {
                mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                isSpeakerphoneOn = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val devices = availableCommunicationDevices
                    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        setCommunicationDevice(speaker)
                    }
                }
                isMicrophoneMute = false
            }
            audioDeviceModule?.setSpeakerMute(false)
            Log.d(TAG, "Hands-free audio routing active (MODE_IN_COMMUNICATION, speakerphone ON)")
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring hands-free audio routing: ${e.message}")
        }
    }

    fun prepareForIncomingVoice(speaker: String? = authorizedSpeaker) {
        audioExecutor.execute {
            try {
                if (speaker != null) {
                    authorizedSpeaker = speaker.trim().lowercase()
                }
                ensureHandsFreeAudioRouting()
                audioDeviceModule?.setSpeakerMute(false)
                audioManager?.let { am ->
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    val currentVol = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                    if (currentVol < (maxVol * 0.7f).toInt()) {
                        am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVol * 0.85f).toInt(), 0)
                    }
                }
                val allowedKey = authorizedSpeaker
                peerConnections.forEach { (peerKey, pc) ->
                    val isAllowed = allowedKey == null || peerKey.equals(allowedKey, ignoreCase = true) || peerConnections.size == 1
                    pc.receivers.forEach { receiver ->
                        val track = receiver.track()
                        if (track is AudioTrack) {
                            track.setEnabled(isAllowed)
                            track.setVolume(if (isAllowed) 1.0 else 0.0)
                        }
                    }
                }
                Log.d(TAG, "Hardware prepared for incoming voice (authorized speaker: $allowedKey)")
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing for incoming voice: ${e.message}")
            }
        }
    }

    fun init() {
        if (isInitialized) return
        try {
            initializeLibrary(context)
            ensureHandsFreeAudioRouting()
            
            // Production-grade Audio Device Module with hardware AEC/NS and robust error callbacks
            audioDeviceModule = JavaAudioDeviceModule.builder(context.applicationContext)
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .setAudioSource(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setUseStereoInput(false)
                .setUseStereoOutput(false)
                .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                    override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                        Log.e(TAG, "WebRTC AudioRecord Init Error: $errorMessage")
                    }
                    override fun onWebRtcAudioRecordStartError(errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?, errorMessage: String?) {
                        Log.e(TAG, "WebRTC AudioRecord Start Error: $errorCode - $errorMessage")
                    }
                    override fun onWebRtcAudioRecordError(errorMessage: String?) {
                        Log.e(TAG, "WebRTC AudioRecord Error: $errorMessage")
                    }
                })
                .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                    override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                        Log.e(TAG, "WebRTC AudioTrack Init Error: $errorMessage")
                    }
                    override fun onWebRtcAudioTrackStartError(errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?, errorMessage: String?) {
                        Log.e(TAG, "WebRTC AudioTrack Start Error: $errorCode - $errorMessage")
                    }
                    override fun onWebRtcAudioTrackError(errorMessage: String?) {
                        Log.e(TAG, "WebRTC AudioTrack Error: $errorMessage")
                    }
                })
                .createAudioDeviceModule()

            audioDeviceModule?.setSpeakerMute(false)
            audioDeviceModule?.setMicrophoneMute(false)

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
                    val newTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", newSource)?.also {
                        attachLocalAudioSink(it)
                    }
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

    private fun attachLocalAudioSink(track: AudioTrack) {
        track.addSink { buffer, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
            if (isTalking) {
                localAudioSampleRate = sampleRate
                localAudioChannels = numberOfChannels
                val dup = buffer.duplicate()
                val data = ByteArray(dup.remaining())
                dup.get(data)
                localAudioRecording.add(data)
                com.example.walkietalkieapp.audio.intelligence.AcousticRadarManager.feedPcmFrame(data, 0, data.size)
                com.example.walkietalkieapp.audio.VoiceActivityDetector.feedLivePcmChunk(data, data.size, numberOfChannels)
                com.example.walkietalkieapp.vox.VoxManager.feedTransmittingFrame(data, numberOfChannels)
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
                localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)?.also {
                    attachLocalAudioSink(it)
                }
            }
            // Initially disable outgoing audio track until user holds PTT
            localAudioTrack?.setEnabled(false)
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
                if (line.contains("opus/48000", ignoreCase = true)) {
                    val parts = line.split(" ")
                    if (parts.isNotEmpty()) {
                        opusPayloadType = parts[0].substringAfter("a=rtpmap:").trim()
                    }
                }
            }

            // Enable inband FEC and disable stereo for robust walkie-talkie voice transmission
            if (opusPayloadType != null) {
                val fmtpPrefix = "a=fmtp:$opusPayloadType"
                val fmtpIndex = lines.indexOfFirst { it.startsWith(fmtpPrefix) }
                if (fmtpIndex != -1) {
                    val existing = lines[fmtpIndex]
                    if (!existing.contains("useinbandfec=1")) {
                        lines[fmtpIndex] = "$existing;useinbandfec=1"
                    }
                }
            }

            lines.joinToString("\r\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing SDP: ${e.message}", e)
            sdp
        }
    }

    fun isConnected(): Boolean {
        return peerIceStates.values.any { 
            it == PeerConnection.IceConnectionState.CONNECTED || 
            it == PeerConnection.IceConnectionState.COMPLETED 
        }
    }

    fun computeAggregatedVoiceLinkState(): String {
        if (peerConnections.isEmpty()) return "IDLE"
        val states = peerIceStates.values
        return when {
            states.any { it == PeerConnection.IceConnectionState.CONNECTED || it == PeerConnection.IceConnectionState.COMPLETED } -> "CONNECTED"
            states.any { it == PeerConnection.IceConnectionState.CHECKING || it == PeerConnection.IceConnectionState.NEW } -> "LINKING"
            states.isNotEmpty() && states.all { it == PeerConnection.IceConnectionState.FAILED } -> "FAILED"
            else -> "IDLE"
        }
    }

    fun isPeerConnected(rawPeerId: String): Boolean {
        val peerId = normKey(rawPeerId)
        val state = peerIceStates[peerId]
        return state == PeerConnection.IceConnectionState.CONNECTED || 
               state == PeerConnection.IceConnectionState.COMPLETED
    }

    private fun getRtcConfig(): PeerConnection.RTCConfiguration {
        val iceServers = mutableListOf(
            // High-reliability global STUN servers (verified low latency over UDP)
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer(),
            
            // Public Fallback TURN Servers
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
        )

        // Dynamically load active TURN servers from BuildConfig (configurable via app/build.gradle.kts)
        val turnUrl = com.example.walkietalkieapp.BuildConfig.TURN_SERVER_URL.trim()
        val turnUser = com.example.walkietalkieapp.BuildConfig.TURN_USERNAME.trim()
        val turnPass = com.example.walkietalkieapp.BuildConfig.TURN_PASSWORD.trim()

        if (turnUrl.isNotEmpty()) {
            turnUrl.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { singleUrl ->
                val builder = PeerConnection.IceServer.builder(singleUrl)
                if (turnUser.isNotEmpty()) builder.setUsername(turnUser)
                if (turnPass.isNotEmpty()) builder.setPassword(turnPass)
                iceServers.add(builder.createIceServer())
                Log.d(TAG, "Configured active TURN server: $singleUrl")
            }
        }
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.iceCandidatePoolSize = 2 // Optimized from 20 to prevent socket flood and speed up gathering
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL
        return rtcConfig
    }
    
    @Synchronized
    private fun getOrCreatePeerConnection(rawPeerId: String): PeerConnection? {
        val peerId = rawPeerId.trim().lowercase()
        if (peerId.isBlank()) return null
        if (peerConnectionFactory == null) init()
        if (!isInitialized) initialize()

        // Absolute guarantee that local audio track exists BEFORE peer connection is created.
        if (localAudioTrack == null) {
            try {
                if (audioDeviceModule == null) init()
                val audioConstraints = createAudioConstraints(isKrispAiEnabled)
                audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
                localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)
                localAudioTrack?.setEnabled(isTalking)
                Log.d(TAG, "Forced synchronous creation of localAudioTrack before PC creation.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forcefully create audio track: ${e.message}")
            }
        }

        val key = normKey(peerId)
        val existing = peerConnections[key]
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
                            // Send ICE candidate immediately for sub-second NAT traversal
                            SupabaseRealtimeManager.sendIceCandidate(peerId, json.toString())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending ICE Candidate for $peerId: ${e.message}")
                        }
                    }
                }
                
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                    Log.d(TAG, "Signaling [$peerId]: $newState")
                }
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "ICE [$peerId]: $newState")
                    peerIceStates[key] = newState
                    mainHandler.post { onStateChange?.invoke(newState) }
                    
                    val aggState = computeAggregatedVoiceLinkState()
                    SupabaseRealtimeManager.updateVoiceLinkState(aggState)

                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            Log.d(TAG, "VOICE LINK ESTABLISHED with $peerId")
                            SupabaseRealtimeManager.addLog("Voice Link: $peerId")
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
                override fun onAddStream(stream: MediaStream) {
                    mainHandler.post {
                        if (stream.audioTracks.isNotEmpty()) {
                            val track = stream.audioTracks[0]
                            Log.d(TAG, "Remote audio stream received from $peerId")
                            try {
                                ensureHandsFreeAudioRouting()
                                audioDeviceModule?.setSpeakerMute(false)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error ensuring hands-free routing on stream", e)
                            }
                            track.setEnabled(true)
                            track.setVolume(1.0)
                        }
                    }
                }
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(dc: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                    mainHandler.post {
                        val track = receiver.track()
                        if (track is AudioTrack) {
                            Log.d(TAG, "Remote audio track received from $peerId")
                            try {
                                requestAudioFocus()
                                ensureHandsFreeAudioRouting()
                                audioDeviceModule?.setSpeakerMute(false)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error ensuring hands-free routing on track", e)
                            }
                            val isAllowed = authorizedSpeaker == null || normKey(peerId).equals(authorizedSpeaker, ignoreCase = true)
                            track.setEnabled(isAllowed)
                            track.setVolume(if (isAllowed) 1.0 else 0.0)
                            track.addSink { buffer, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
                                val dup = buffer.duplicate()
                                val data = ByteArray(dup.remaining())
                                dup.get(data)
                                synchronized(voiceBuffer) {
                                    voiceBuffer.add(data)
                                    if (voiceBuffer.size > MAX_BUFFER_FRAMES) {
                                        voiceBuffer.removeAt(0)
                                    }
                                }
                                if (authorizedSpeaker != null) {
                                    activeRemoteSampleRate = sampleRate
                                    activeRemoteChannels = numberOfChannels
                                    remoteAudioRecording.add(data)
                                }
                            }
                            SupabaseRealtimeManager.addLog("Active stream from $peerId")
                        }
                    }
                }
            }
        ) ?: return null

        localAudioTrack?.let { track ->
            track.setEnabled(isTalking)
            pc.addTrack(track, listOf("LOCAL_STREAM"))
        }

        peerConnections[key] = pc
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

    private fun createOfferForPeer(rawPeerId: String, isRestart: Boolean = false) {
        val peerId = normKey(rawPeerId)
        val existing = peerConnections[peerId]
        val state = peerIceStates[peerId]
        if (existing != null) {
            val sigState = existing.signalingState()
            if (sigState != PeerConnection.SignalingState.STABLE) {
                Log.d(TAG, "PeerConnection for $peerId is in signaling state $sigState, skipping duplicate offer")
                return
            }
            if (!isRestart && (state == PeerConnection.IceConnectionState.CONNECTED || 
                               state == PeerConnection.IceConnectionState.COMPLETED || 
                               state == PeerConnection.IceConnectionState.CHECKING)) {
                Log.d(TAG, "PeerConnection for $peerId is in progress/connected ($state), skipping duplicate offer")
                return
            }
            if (!isRestart && existing.localDescription != null && existing.remoteDescription == null) {
                Log.d(TAG, "PeerConnection for $peerId already has active pending offer, skipping duplicate offer")
                return
            }
            if (state == PeerConnection.IceConnectionState.FAILED || state == PeerConnection.IceConnectionState.DISCONNECTED) {
                Log.d(TAG, "PeerConnection for $peerId in state $state, recreating connection")
                try { existing.dispose() } catch (e: Exception) {}
                peerConnections.remove(peerId)
                peerIceStates.remove(peerId)
            }
        }

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
                        SupabaseRealtimeManager.sendOffer(peerId, newSdp.description)
                    }
                }, newSdp)
            }
        }, constraints)
    }

    fun handleOffer(rawFromPeerId: String, sdp: String) {
        val fromPeerId = rawFromPeerId.trim().lowercase()
        audioExecutor.execute {
            if (fromPeerId.isBlank()) return@execute
            if (!isSessionActive) {
                isSessionActive = true
                if (!isInitialized) initialize()
            }
            Log.d(TAG, "Handling remote offer from $fromPeerId")

            val key = normKey(fromPeerId)
            var pc = getOrCreatePeerConnection(key) ?: return@execute

            if (pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                val myName = normKey(SupabaseRealtimeManager.socketUiState.value.username)
                if (myName > key) {
                    Log.d(TAG, "Signaling glare with $fromPeerId: I am polite peer, rolling back local offer to accept remote offer")
                    val rollbackDesc = SessionDescription(SessionDescription.Type.ROLLBACK, "")
                    pc.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Rollback successful for $fromPeerId")
                        }
                    }, rollbackDesc)
                } else {
                    Log.d(TAG, "Signaling glare with $fromPeerId: I am impolite peer, ignoring incoming offer")
                    return@execute
                }
            }

            ensureHandsFreeAudioRouting()

            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote offer set for $fromPeerId, creating answer")
                    createAnswerForPeer(fromPeerId, pc)
                    audioExecutor.execute { drainPendingCandidates(fromPeerId) }
                }
                override fun onSetFailure(error: String?) {
                    Log.w(TAG, "Failed to set remote offer for $fromPeerId: $error, recreating connection")
                    try { pc.dispose() } catch (e: Exception) {}
                    peerConnections.remove(key)
                    val newPc = getOrCreatePeerConnection(key) ?: return
                    newPc.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            createAnswerForPeer(fromPeerId, newPc)
                            audioExecutor.execute { drainPendingCandidates(fromPeerId) }
                        }
                    }, sessionDescription)
                }
            }, sessionDescription)
        }
    }

    private fun createAnswerForPeer(rawPeerId: String, pc: PeerConnection) {
        val peerId = rawPeerId.trim().lowercase()
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
                        SupabaseRealtimeManager.sendAnswer(peerId, newSdp.description)
                    }
                }, newSdp)
            }
        }, constraints)
    }

    fun handleAnswer(rawFromPeerId: String, sdp: String) {
        val fromPeerId = rawFromPeerId.trim().lowercase()
        audioExecutor.execute {
            if (fromPeerId.isBlank()) return@execute
            val key = normKey(fromPeerId)
            val pc = peerConnections[key] ?: return@execute
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
    
    fun handleIceCandidate(rawFromPeerId: String, candidateJson: String) {
        val fromPeerId = rawFromPeerId.trim().lowercase()
        audioExecutor.execute {
            if (fromPeerId.isBlank()) return@execute
            try {
                if (candidateJson.startsWith("[")) {
                    val array = JSONArray(candidateJson)
                    for (i in 0 until array.length()) {
                        processSingleCandidate(fromPeerId, array.getString(i))
                    }
                } else {
                    processSingleCandidate(fromPeerId, candidateJson)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ICE Candidate Error from $fromPeerId: ${e.message}")
            }
        }
    }

    private fun processSingleCandidate(fromPeerId: String, singleJson: String) {
        val candidate: IceCandidate? = try {
            val json = JSONObject(singleJson)
            val candidateStr = json.optString("candidate", "")
            if (candidateStr.isNotEmpty()) {
                val sdpMid = json.optString("sdpMid", json.optString("id", "0"))
                val sdpMLineIndex = json.optInt("sdpMLineIndex", json.optInt("label", 0))
                IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
            } else null
        } catch (e: Exception) {
            if (singleJson.startsWith("candidate:")) {
                IceCandidate("0", 0, singleJson)
            } else null
        }

        if (candidate == null) return

        val key = normKey(fromPeerId)
        val pc = peerConnections[key]
        if (pc != null && pc.remoteDescription != null) {
            pc.addIceCandidate(candidate)
        } else {
            pendingIceCandidates.getOrPut(key) { mutableListOf() }.add(candidate)
        }
    }
    
    private fun drainPendingCandidates(rawPeerId: String) {
        val peerId = normKey(rawPeerId)
        val pc = peerConnections[peerId] ?: return
        val list = pendingIceCandidates[peerId] ?: return
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            pc.addIceCandidate(candidate)
            iterator.remove()
        }
    }

    fun removePeer(rawPeerId: String) {
        val peerId = rawPeerId.trim().lowercase()
        audioExecutor.execute {
            val key = normKey(peerId)
            Log.d(TAG, "Removing peer connection for $peerId ($key)")
            try {
                peerConnections.remove(key)?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing pc for $peerId: ${e.message}")
            }
            pendingIceCandidates.remove(key)
            peerIceStates.remove(key)
            val aggState = computeAggregatedVoiceLinkState()
            SupabaseRealtimeManager.updateVoiceLinkState(aggState)
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
                
                localAudioStartTime = System.currentTimeMillis()
                localAudioRecording.clear()
                isTalking = true
                com.example.walkietalkieapp.audio.intelligence.AcousticRadarManager.setPttActive(true)
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
                    val audioSender = pc.senders.find { it.track()?.kind() == "audio" || it.track() == null }
                    if (audioSender != null) {
                        audioSender.setTrack(localAudioTrack, false)
                    } else if (localAudioTrack != null) {
                        try {
                            pc.addTrack(localAudioTrack, listOf("LOCAL_STREAM"))
                        } catch (e: Exception) {
                            Log.w(TAG, "addTrack notice: ${e.message}")
                        }
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
                localAudioTrack?.setEnabled(false)
                com.example.walkietalkieapp.audio.intelligence.AcousticRadarManager.setPttActive(false)
                com.example.walkietalkieapp.audio.VoiceActivityDetector.resetLiveState()
                commitLocalAudioTransmission()
                Log.d(TAG, "PTT released — mic muted, audio focus retained for incoming transmission")
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling audio: ${e.message}")
            }
        }
    }

    private fun commitLocalAudioTransmission() {
        try {
            val bufferCopy = synchronized(localAudioRecording) {
                val list = localAudioRecording.toList()
                localAudioRecording.clear()
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
                val duration = (System.currentTimeMillis() - localAudioStartTime).coerceAtLeast(300L)
                val myName = SupabaseRealtimeManager.socketUiState.value.username.ifBlank { "You" }
                val saved = com.example.walkietalkieapp.audio.VoiceHistoryManager.addTransmission(
                    speakerName = myName,
                    pcmData = pcmData,
                    sampleRate = localAudioSampleRate,
                    channels = localAudioChannels,
                    durationMs = duration,
                    isSelf = true
                )
                if (saved) {
                    Log.d(TAG, "Local speech recorded and saved to local Blackbox Reel ($duration ms)")
                } else {
                    Log.d(TAG, "Empty/silent PTT release ignored — omitted from Blackbox Reel")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error committing local voice transmission: ${e.message}")
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
        com.example.walkietalkieapp.audio.VoiceHistoryManager.playLatest()
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up all WebRTC peer connections")
        isSessionActive = false
        stopTalking()
        com.example.walkietalkieapp.audio.VoiceHistoryManager.clearHistory()
        
        audioExecutor.execute {
            peerConnections.forEach { (peerId, pc) ->
                try {
                    pc.close()
                    pc.dispose()
                } catch (e: Exception) {
                    Log.e(TAG, "Error disposing peer $peerId: ${e.message}")
                }
            }
            peerConnections.clear()
            peerIceStates.clear()
            pendingIceCandidates.clear()
            SupabaseRealtimeManager.updateVoiceLinkState("IDLE")
            
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
                abandonAudioFocus()
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
