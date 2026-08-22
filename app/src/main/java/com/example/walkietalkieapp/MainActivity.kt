package com.example.walkietalkieapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.walkietalkieapp.socket.SignalingListener
import com.example.walkietalkieapp.socket.SocketManager
import com.example.walkietalkieapp.socket.SocketUiState
import com.example.walkietalkieapp.socket.RoomMember
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.example.walkietalkieapp.webrtc.WalkieTalkieService
import com.example.walkietalkieapp.ui.theme.WalkieTalkieAppTheme
import com.example.walkietalkieapp.floor.FloorManager
import com.example.walkietalkieapp.floor.FloorState
import com.example.walkietalkieapp.floor.FloorStatus
import kotlinx.coroutines.*

private const val TAG = "WalkieTalkieApp"

class MainActivity : ComponentActivity(), SensorEventListener {
    private var hasAudioPermission by mutableStateOf(false)
    private var walkieTalkieService: WalkieTalkieService? = null
    private var isBound by mutableStateOf(false)
    private var isOthersSpeaking by mutableStateOf(false)
    private var deepLinkRoomId by mutableStateOf("")
    private var notificationMessage by mutableStateOf<String?>(null)
    private val notificationQueue = mutableStateListOf<String>()

    private lateinit var toneGenerator: ToneGenerator

    private var isBatterySaverEnabled by mutableStateOf(true)

    // Sensor / Gyro
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var gyroOffset by mutableStateOf(androidx.compose.ui.geometry.Offset(0f, 0f))

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val audioGranted = perms[Manifest.permission.RECORD_AUDIO] == true
            hasAudioPermission = audioGranted
            if (audioGranted) initializeWebRTC()
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            val binder = service as WalkieTalkieService.LocalBinder
            walkieTalkieService = binder.getService()
            isBound = true
            
            walkieTalkieService?.onOthersSpeakingStateChange = { speaking ->
                runOnUiThread { 
                    if (speaking && !isOthersSpeaking) {
                        vibrate()
                        playTone(ToneGenerator.TONE_PROP_BEEP)
                    }
                    isOthersSpeaking = speaking 
                }
            }

            walkieTalkieService?.webRTCManager?.onStateChange = { state ->
                SocketManager.updateVoiceLinkState(state.name)
            }

            val currentRoom = SocketManager.socketUiState.value.roomId
            if (hasAudioPermission && currentRoom.isNotEmpty()) {
                walkieTalkieService?.startVoiceSession(currentRoom)
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            isBound = false
            walkieTalkieService = null
        }
    }

    private fun startService() {
        val intent = android.content.Intent(this, WalkieTalkieService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun stopService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        val intent = android.content.Intent(this, WalkieTalkieService::class.java)
        stopService(intent)
    }

    private fun enterSquad(roomId: String, username: String, roomName: String = "") {
        if (hasAudioPermission) {
            startService()
        }
        val currentRoom = SocketManager.socketUiState.value.roomId
        if (currentRoom.isNotEmpty() && currentRoom != roomId) {
            walkieTalkieService?.webRTCManager?.cleanup()
            SocketManager.leaveRoom()
        }
        // Always join the room code provided by Supabase
        SocketManager.joinRoom(roomId, username, roomName)
        walkieTalkieService?.startVoiceSession(roomId)
    }

    private lateinit var sessionManager: com.example.walkietalkieapp.auth.SessionManager
    private var isLoggedIn by mutableStateOf(false)
    private var currentUsername by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        refreshAudioPermissionState()
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        
        sessionManager = com.example.walkietalkieapp.auth.SessionManager.getInstance(this)
        isLoggedIn = sessionManager.isLoggedIn()
        currentUsername = sessionManager.getUsername()

        val permsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val ungranted = permsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            permissionsLauncher.launch(ungranted.toTypedArray())
        }
        
        SocketManager.initialize()
        SocketManager.connect()

        if (SocketManager.socketUiState.value.roomId.isNotEmpty()) {
            startService()
        }

        // Floor Arbitration Callbacks
        FloorManager.onFloorGranted = {
            runOnUiThread {
                playTone(ToneGenerator.TONE_CDMA_PIP)
                vibrate()
                SocketManager.sendStartVoice()
                walkieTalkieService?.webRTCManager?.startTalking()
                walkieTalkieService?.updateNotification("🔴 Transmitting...")
            }
        }

        FloorManager.onFloorDenied = { reason, speakerName ->
            runOnUiThread {
                playTone(ToneGenerator.TONE_SUP_ERROR)
                vibrateError()
                notificationQueue.add("🔒 Channel Busy: $speakerName is speaking")
            }
        }

        FloorManager.onFloorRevoked = {
            runOnUiThread {
                walkieTalkieService?.webRTCManager?.stopTalking()
                SocketManager.sendStopVoice()
                playTone(ToneGenerator.TONE_SUP_ERROR)
                vibrateError()
                notificationQueue.add("⚠️ Priority Override: Mic Revoked")
                val roomId = SocketManager.socketUiState.value.roomId
                walkieTalkieService?.updateNotification(if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk")
            }
        }

        FloorManager.onFloorReleased = {
            runOnUiThread {
                walkieTalkieService?.webRTCManager?.stopTalking()
                SocketManager.sendStopVoice()
                playTone(ToneGenerator.TONE_PROP_BEEP2)
                val roomId = SocketManager.socketUiState.value.roomId
                walkieTalkieService?.updateNotification(if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk")
            }
        }

        FloorManager.onFloorWarning = {
            runOnUiThread {
                playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD)
            }
        }

        FloorManager.onFloorTimeout = {
            runOnUiThread {
                walkieTalkieService?.webRTCManager?.stopTalking()
                SocketManager.sendStopVoice()
                playTone(ToneGenerator.TONE_SUP_ERROR)
                vibrateError()
                notificationQueue.add("⏱️ Transmission timed out (20s limit)")
                val roomId = SocketManager.socketUiState.value.roomId
                walkieTalkieService?.updateNotification(if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk")
            }
        }

        // Gyro Setup
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        setContent {
            WalkieTalkieAppTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0B)) {
                    val socketUiState by SocketManager.socketUiState.collectAsState()
                    val floorStatus by FloorManager.floorStatus.collectAsState()
                    val isUserSpeakingLocal = floorStatus.state == FloorState.TRANSMITTING
                    
                    val othersSpeakingState = socketUiState.roomMembers.any { it.isSpeaking && it.username != socketUiState.username }
                    val activeSpeaking = isUserSpeakingLocal || isOthersSpeaking || othersSpeakingState || floorStatus.state == FloorState.RECEIVING

                    BackgroundComposable(activeSpeaking, gyroOffset)

                    if (!isLoggedIn) {
                        com.example.walkietalkieapp.ui.AuthScreen(
                            onAuthSuccess = { userId, username ->
                                sessionManager.saveSession(userId, username)
                                currentUsername = username
                                isLoggedIn = true
                            }
                        )
                    } else if (socketUiState.roomId.isEmpty()) {
                        com.example.walkietalkieapp.RoomsDashboardScreen(
                            currentUserId = sessionManager.getUserId(),
                            currentUsername = currentUsername,
                            onLogout = {
                                sessionManager.clearSession()
                                currentUsername = ""
                                isLoggedIn = false
                            },
                            onJoinRoom = { roomCode, roomName ->
                                deepLinkRoomId = "" // Clear after use
                                enterSquad(roomCode, currentUsername, roomName)
                            }
                        )
                    } else {
                        SquadScreen(
                            currentUserId = sessionManager.getUserId(),
                            socketUiState = socketUiState,
                            isOthersSpeaking = isOthersSpeaking,
                            hasAudioPermission = hasAudioPermission,
                            onRequestPermission = { permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                            onStartTalk = { isPriority ->
                                startPushToTalk(isPriority)
                            },
                            onStopTalk = {
                                stopPushToTalk()
                            },
                            onLeave = { 
                                walkieTalkieService?.webRTCManager?.cleanup()
                                SocketManager.leaveRoom()
                                walkieTalkieService?.updateNotification("Ready to talk")
                            },
                            onShare = { shareRoom(it) },
                            vibrate = ::vibrate,
                            onReplay = { walkieTalkieService?.replayLastTransmissions() },
                            onRestartIce = { walkieTalkieService?.restartIce() },
                            onWhisper = {
                                vibrate()
                                startPushToTalk(false)
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(2000)
                                    stopPushToTalk()
                                }
                            },
                            isBatterySaverEnabled = isBatterySaverEnabled,
                            onBatterySaverToggle = { isBatterySaverEnabled = it },
                            notificationMessage = notificationMessage
                        )
                    }

                    // Notification Queue Handler: processes events continuously and auto-dismisses after 2.5s
                    LaunchedEffect(Unit) {
                        while (true) {
                            if (notificationQueue.isNotEmpty()) {
                                val msg = notificationQueue.removeAt(0)
                                notificationMessage = msg
                                delay(2500)
                                notificationMessage = null
                                delay(300)
                            } else {
                                delay(100)
                            }
                        }
                    }

                    // Collect explicit events from Socket
                    LaunchedEffect(Unit) {
                        SocketManager.events.collect { event ->
                            notificationQueue.add(event)
                        }
                    }

                    LaunchedEffect(isOthersSpeaking) {
                        if (isOthersSpeaking) {
                            vibrate()
                            playTone(ToneGenerator.TONE_PROP_BEEP)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // orientation[1] is pitch, orientation[2] is roll
            val pitch = orientation[1]
            val roll = orientation[2]
            
            // Map to small offset
            gyroOffset = androidx.compose.ui.geometry.Offset(
                x = roll * 40f,
                y = pitch * 40f
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun playTone(toneType: Int) {
        try {
            toneGenerator.startTone(toneType, 150)
        } catch (e: Exception) {
            Log.e(TAG, "Tone error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator.release()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent?.data?.let { data ->
            val roomId = data.getQueryParameter("roomId")
            if (!roomId.isNullOrEmpty()) {
                deepLinkRoomId = roomId.uppercase()
                Log.d(TAG, "Deep link received for room: $deepLinkRoomId")
            }
        }
    }

    private fun shareRoom(roomId: String) {
        val inviteLink = "https://walkie-talkie-web.vercel.app/join?roomId=$roomId"
        val intent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, "Join my Squad on Squad Talk!\n\nLink: $inviteLink\n\nCode: $roomId")
            type = "text/plain"
        }
        startActivity(android.content.Intent.createChooser(intent, "Invite Friends"))
        notificationQueue.add("Invite link copied to share 🚀")
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrateError() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(200)
        }
    }

    private fun initializeWebRTC() {
        if (walkieTalkieService != null) return
        startService()
    }
    
    private fun startPushToTalk(isPriority: Boolean = false) {
        val roomId = SocketManager.socketUiState.value.roomId
        if (roomId.isEmpty()) return
        if (walkieTalkieService == null) startService()
        FloorManager.requestFloor(isPriority)
    }
    
    private fun stopPushToTalk() {
        FloorManager.releaseFloor()
    }

    private fun refreshAudioPermissionState() {
        hasAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadScreen(
    currentUserId: String,
    socketUiState: SocketUiState,
    isOthersSpeaking: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartTalk: (isPriority: Boolean) -> Unit,
    onStopTalk: () -> Unit,
    onLeave: () -> Unit,
    onShare: (String) -> Unit,
    vibrate: () -> Unit,
    onReplay: () -> Unit,
    onRestartIce: () -> Unit,
    onWhisper: () -> Unit,
    isBatterySaverEnabled: Boolean,
    onBatterySaverToggle: (Boolean) -> Unit,
    notificationMessage: String? = null
) {
    val floorStatus by FloorManager.floorStatus.collectAsState()
    val isUserSpeaking = floorStatus.state == FloorState.TRANSMITTING
    var showSettings by remember { mutableStateOf(false) }
    var isOwner by remember { mutableStateOf(false) }
    
    var pendingRequests by remember { mutableStateOf<List<com.example.walkietalkieapp.auth.RoomMemberRequest>>(emptyList()) }
    var approvedMembers by remember { mutableStateOf<List<String>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    fun fetchPending() {
        coroutineScope.launch {
            val pendingResult = com.example.walkietalkieapp.auth.SupabaseRoomManager.getPendingRequests(currentUserId)
            if (pendingResult is com.example.walkietalkieapp.auth.RoomResult.Success) {
                pendingRequests = pendingResult.data.filter { it.roomId == socketUiState.roomId || it.roomCode == socketUiState.roomId }
            }
        }
    }

    fun fetchApprovedMembers() {
        coroutineScope.launch {
            val result = com.example.walkietalkieapp.auth.SupabaseRoomManager.getApprovedRoomMembers(socketUiState.roomId)
            if (result is com.example.walkietalkieapp.auth.RoomResult.Success) {
                approvedMembers = result.data
            }
        }
    }

    LaunchedEffect(socketUiState.roomId) {
        fetchPending()
        fetchApprovedMembers()
        coroutineScope.launch {
            val myRoomsRes = com.example.walkietalkieapp.auth.SupabaseRoomManager.getMyRooms(currentUserId)
            if (myRoomsRes is com.example.walkietalkieapp.auth.RoomResult.Success) {
                isOwner = myRoomsRes.data.any { (it.code.equals(socketUiState.roomId, ignoreCase = true) || it.id == socketUiState.roomId) && it.ownerId == currentUserId }
            }
        }
        com.example.walkietalkieapp.auth.SupabaseRealtimeManager.addListener("squad_${socketUiState.roomId}") {
            fetchPending()
            fetchApprovedMembers()
        }
    }

    DisposableEffect(socketUiState.roomId) {
        onDispose {
            com.example.walkietalkieapp.auth.SupabaseRealtimeManager.removeListener("squad_${socketUiState.roomId}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section: Squad Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (socketUiState.roomName.isNotEmpty()) socketUiState.roomName.uppercase() else "SQUAD: ${socketUiState.roomId}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                        if (isOwner) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFFFA000).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "👑",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        val (statusText, statusColor) = when {
                            !socketUiState.isConnected -> "🔴 Disconnected" to Color(0xFFF44336)
                            socketUiState.detail.contains("Retrying") -> "🟡 Reconnecting" to Color(0xFFFFA000)
                            else -> "🟢 Connected" to Color(0xFF4CAF50)
                        }
                        Text(text = statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }

                IconButton(
                    onClick = { onShare(socketUiState.roomId) },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }

            // Floor Status Banner
            Spacer(modifier = Modifier.height(12.dp))
            ActiveTransmissionBanner(floorStatus = floorStatus)

            if (pendingRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFA000).copy(alpha = 0.2f)).border(1.dp, Color(0xFFFFA000), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("${pendingRequests.size} Pending Request(s)", color = Color(0xFFFFA000), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    pendingRequests.forEach { req ->
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(req.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { 
                                    coroutineScope.launch { 
                                        com.example.walkietalkieapp.auth.SupabaseRoomManager.approveRequest(req.roomId, req.userId)
                                        fetchPending()
                                        fetchApprovedMembers()
                                    }
                                }, modifier = Modifier.size(28.dp).background(Color(0xFF4CAF50), CircleShape)) {
                                    Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { 
                                    coroutineScope.launch { 
                                        com.example.walkietalkieapp.auth.SupabaseRoomManager.declineRequest(req.roomId, req.userId)
                                        fetchPending()
                                        fetchApprovedMembers()
                                    }
                                }, modifier = Modifier.size(28.dp).background(Color(0xFFF44336), CircleShape)) {
                                    Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            TimerView()

            Spacer(modifier = Modifier.height(24.dp))

            // Member List: Combined approved squad members with online/offline status
            val onlineUsernames = socketUiState.roomMembers.map { it.username.trim().lowercase() }.toSet()
            val allUsernames = (approvedMembers + socketUiState.roomMembers.map { it.username })
                .filter { it.isNotBlank() }
                .distinctBy { it.trim().lowercase() }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (allUsernames.isEmpty()) {
                    item {
                        Text(
                            "Waiting for squad members...",
                            color = Color.White.copy(alpha = 0.2f),
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    items(allUsernames, key = { it.trim().lowercase() }) { uname ->
                        val isSelf = uname.equals(socketUiState.username, ignoreCase = true)
                        val isMemberSpeaking = if (isSelf) {
                            isUserSpeaking
                        } else {
                            val matchesFloorSpeaker = floorStatus.currentSpeakerName?.equals(uname, ignoreCase = true) == true
                            val activeSpeaker = socketUiState.roomMembers.find { it.isSpeaking && !it.username.equals(socketUiState.username, ignoreCase = true) }?.username
                            val matchesSocketSpeaker = uname.equals(activeSpeaker, ignoreCase = true) || uname.equals(socketUiState.lastSpeakerName, ignoreCase = true)
                            val isSingleRemote = allUsernames.size <= 2 && !isSelf
                            matchesFloorSpeaker || (isOthersSpeaking && (matchesSocketSpeaker || isSingleRemote)) || (matchesSocketSpeaker && socketUiState.roomMembers.any { it.isSpeaking })
                        }
                        val isOnline = onlineUsernames.contains(uname.trim().lowercase()) || isMemberSpeaking
                        MemberItem(
                            username = uname,
                            isOnline = isOnline,
                            isSpeaking = isMemberSpeaking,
                            onReplay = onReplay
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center Speaker Indicator
            SpeakingIndicator(floorStatus = floorStatus, isOthersSpeaking = isOthersSpeaking, socketUiState = socketUiState)

            Spacer(modifier = Modifier.height(24.dp))

            // PTT Button with Floor Control State
            PushToTalkButton(
                floorStatus = floorStatus,
                isConnected = socketUiState.isConnected,
                isOwner = isOwner,
                onPress = { isPriority ->
                    if (!hasAudioPermission) onRequestPermission()
                    else {
                        vibrate()
                        onStartTalk(isPriority)
                        SocketManager.updateActivity()
                    }
                },
                onRelease = {
                    onStopTalk()
                },
                onWhisper = {
                    if (hasAudioPermission) {
                        onWhisper()
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1.2f))

            // Bottom Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLeave,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LEAVE SQUAD", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Settings Bottom Sheet
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = Color(0xFF1E2124),
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)
                ) {
                    Text("Squad Settings", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.Bolt,
                        title = "Whisper Mode",
                        subtitle = "Send a quick 2-second voice burst",
                        color = Color(0xFF9C27B0), // Purple
                        onClick = { 
                            onWhisper()
                            showSettings = false
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = "Replay Last 10s",
                        subtitle = "Play back the most recent incoming voice audio",
                        color = Color(0xFFFFA000),
                        onClick = { 
                            onReplay()
                            showSettings = false
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.BatterySaver,
                        title = "Battery Saver",
                        subtitle = if (isBatterySaverEnabled) "Auto-idle disconnect enabled" else "Always stay connected",
                        color = if (isBatterySaverEnabled) Color(0xFF4CAF50) else Color.Gray,
                        onClick = { onBatterySaverToggle(!isBatterySaverEnabled) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsItem(
                        icon = Icons.Default.Share,
                        title = "Invite Friends",
                        subtitle = "Share squad code: ${socketUiState.roomId}",
                        color = Color(0xFF2196F3),
                        onClick = { 
                            onShare(socketUiState.roomId)
                            showSettings = false
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SettingsItem(
                        icon = Icons.Default.Settings,
                        title = "⚡ Fix Audio Issue",
                        subtitle = "Manually refresh connection if audio drops",
                        color = Color.Gray,
                        onClick = { 
                            onRestartIce()
                            showSettings = false
                        }
                    )
                }
            }
        }

        // Animated Notification Overlay
        AnimatedVisibility(
            visible = notificationMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2124)),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notificationMessage ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveTransmissionBanner(floorStatus: FloorStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "bannerPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "bannerAlpha"
    )

    val (bgColor, borderColor, text, textColor, iconColor) = when (floorStatus.state) {
        FloorState.IDLE -> FloorBannerConfig(
            Color(0xFF1E2126),
            Color.White.copy(alpha = 0.08f),
            "📻 CHANNEL OPEN • HOLD TO TALK",
            Color.White.copy(alpha = 0.7f),
            Color(0xFF4CAF50)
        )
        FloorState.REQUESTING -> FloorBannerConfig(
            Color(0xFFFFA000).copy(alpha = 0.15f),
            Color(0xFFFFA000).copy(alpha = 0.6f),
            "⏳ ACQUIRING CHANNEL...",
            Color(0xFFFFA000),
            Color(0xFFFFA000)
        )
        FloorState.TRANSMITTING -> {
            val secondsRemaining = ((floorStatus.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) / 1000).toInt()
            FloorBannerConfig(
                Color(0xFF4CAF50).copy(alpha = 0.2f),
                Color(0xFF4CAF50),
                "🔴 TRANSMITTING (${secondsRemaining}s remaining)",
                Color(0xFF4CAF50),
                Color(0xFF4CAF50)
            )
        }
        FloorState.RECEIVING -> {
            val name = floorStatus.currentSpeakerName ?: "MEMBER"
            FloorBannerConfig(
                Color(0xFF2196F3).copy(alpha = 0.15f),
                Color(0xFF2196F3).copy(alpha = 0.6f),
                "🟢 $name IS TRANSMITTING",
                Color(0xFF2196F3),
                Color(0xFF2196F3)
            )
        }
        FloorState.BUSY_BLOCKED -> {
            val name = floorStatus.currentSpeakerName ?: "SOMEONE"
            FloorBannerConfig(
                Color(0xFFE53935).copy(alpha = 0.15f),
                Color(0xFFE53935).copy(alpha = 0.6f),
                "🔒 CHANNEL BUSY • $name IS SPEAKING",
                Color(0xFFFF5252),
                Color(0xFFFF5252)
            )
        }
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(alpha = if (floorStatus.state != FloorState.IDLE) pulseAlpha else 1f)
                    .background(iconColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private data class FloorBannerConfig(
    val bgColor: Color,
    val borderColor: Color,
    val text: String,
    val textColor: Color,
    val iconColor: Color
)

@Composable
fun PushToTalkButton(
    floorStatus: FloorStatus,
    isConnected: Boolean,
    isOwner: Boolean = false,
    onPress: (isPriority: Boolean) -> Unit,
    onRelease: () -> Unit,
    onWhisper: () -> Unit
) {
    val isTransmitting = floorStatus.state == FloorState.TRANSMITTING
    val isRequesting = floorStatus.state == FloorState.REQUESTING
    val isBusy = floorStatus.state == FloorState.BUSY_BLOCKED || floorStatus.state == FloorState.RECEIVING

    val scale by animateFloatAsState(
        targetValue = if (isTransmitting) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val pulse1Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1Scale"
    )
    val pulse1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1Alpha"
    )

    val pulse2Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing, delayMillis = 1000), RepeatMode.Restart),
        label = "pulse2Scale"
    )
    val pulse2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing, delayMillis = 1000), RepeatMode.Restart),
        label = "pulse2Alpha"
    )
    
    val breathingTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by breathingTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

    val ringColor = when {
        isTransmitting -> Color(0xFF4CAF50)
        isRequesting -> Color(0xFFFFA000)
        isBusy -> Color(0xFFE53935)
        else -> Color.White.copy(alpha = 0.1f)
    }

    val buttonGradient = when {
        isTransmitting -> listOf(Color(0xFF66BB6A), Color(0xFF43A047))
        isRequesting -> listOf(Color(0xFFFFA000), Color(0xFFFF8F00))
        isBusy -> listOf(Color(0xFF263238), Color(0xFF1E2124))
        else -> listOf(Color(0xFF2C2F33), Color(0xFF1E2126))
    }

    Box(contentAlignment = Alignment.Center) {
        // Pulsing Rings
        if (isTransmitting) {
            Box(modifier = Modifier.size(170.dp).scale(pulse1Scale).background(Color(0xFF4CAF50).copy(alpha = pulse1Alpha), CircleShape))
            Box(modifier = Modifier.size(170.dp).scale(pulse2Scale).background(Color(0xFF4CAF50).copy(alpha = pulse2Alpha), CircleShape))
        } else if (isRequesting) {
            Box(modifier = Modifier.size(170.dp).scale(pulse1Scale).background(Color(0xFFFFA000).copy(alpha = pulse1Alpha), CircleShape))
        }

        // Circular countdown progress ring
        if (isTransmitting) {
            var timeRemainingRatio by remember { mutableFloatStateOf(1f) }
            var remainingSec by remember { mutableIntStateOf(20) }
            LaunchedEffect(floorStatus.expiresAt) {
                while (isTransmitting) {
                    val remainingMs = (floorStatus.expiresAt - System.currentTimeMillis()).coerceIn(0, 20000)
                    timeRemainingRatio = remainingMs / 20000f
                    remainingSec = (remainingMs / 1000).toInt()
                    delay(100)
                }
            }
            androidx.compose.foundation.Canvas(modifier = Modifier.size(186.dp)) {
                drawArc(
                    color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx())
                )
                drawArc(
                    color = if (remainingSec <= 3) Color(0xFFFF5252) else Color(0xFF00FF66),
                    startAngle = -90f,
                    sweepAngle = 360f * timeRemainingRatio,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(brush = Brush.verticalGradient(colors = buttonGradient))
                .then(
                    if (!isTransmitting && !isBusy) {
                        Modifier.graphicsLayer(alpha = breathingAlpha)
                    } else Modifier
                )
                .border(
                    width = if (isTransmitting) 4.dp else 2.dp,
                    color = ringColor,
                    shape = CircleShape
                )
                .pointerInput(isConnected) {
                    if (isConnected) {
                        detectTapGestures(
                            onPress = { 
                                onPress(false)
                                try { awaitRelease() } finally { onRelease() }
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = when {
                        isBusy -> Icons.Default.Close
                        isRequesting -> Icons.Default.Bolt
                        else -> Icons.Default.Mic
                    }, 
                    contentDescription = null, 
                    tint = if (isBusy) Color.Gray else Color.White, 
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer(alpha = if (isTransmitting) 1f else 0.85f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                val buttonText = when {
                    isTransmitting -> {
                        val secs = ((floorStatus.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) / 1000).toInt()
                        "TRANSMITTING (${secs}s)"
                    }
                    isRequesting -> "ACQUIRING..."
                    isBusy -> "CHANNEL BUSY"
                    else -> "HOLD TO TALK"
                }
                Text(
                    text = buttonText, 
                    color = if (isBusy) Color.Gray else Color.White, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(0xFF2C2F33),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MemberItem(
    username: String,
    isOnline: Boolean,
    isSpeaking: Boolean,
    onReplay: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val speakingScale by animateFloatAsState(
        targetValue = if (isSpeaking) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "speakingScale"
    )

    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .scale(speakingScale)
            .pointerInput(username) {
                detectTapGestures(
                    onTap = { onReplay() }
                )
            }
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(
                        width = if (isSpeaking) 3.dp else 1.dp,
                        color = when {
                            isSpeaking -> Color(0xFF4CAF50).copy(alpha = borderAlpha)
                            isOnline -> Color.White.copy(alpha = 0.15f)
                            else -> Color.White.copy(alpha = 0.05f)
                        },
                        shape = CircleShape
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isOnline) {
                                listOf(Color(0xFF2C2F33), Color(0xFF1E2124))
                            } else {
                                listOf(Color(0xFF1A1C1E), Color(0xFF121315))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.take(1).uppercase(), 
                    color = when {
                        isSpeaking -> Color.White
                        isOnline -> Color.White.copy(alpha = 0.85f)
                        else -> Color.White.copy(alpha = 0.3f)
                    }, 
                    fontWeight = FontWeight.ExtraBold, 
                    fontSize = 22.sp
                )
                
                // Overlay "Playing" state if they are speaking
                if (isSpeaking) {
                    WaveformSmall()
                }
            }
            
            // Status Dot: Green if actively connected in room, Red if offline
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFF0F1115), CircleShape)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isOnline) Color(0xFF4CAF50) else Color(0xFFE53935),
                            CircleShape
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = username, 
            color = when {
                isSpeaking -> Color.White
                isOnline -> Color.White.copy(alpha = 0.85f)
                else -> Color.White.copy(alpha = 0.35f)
            }, 
            fontSize = 12.sp, 
            fontWeight = if (isSpeaking || isOnline) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun WaveformSmall() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    Row(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(3) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    tween(300 + index * 100),
                    RepeatMode.Reverse
                ),
                label = "bar"
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight(height)
                    .width(3.dp)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun TimerView() {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while(true) { delay(1000); seconds++ }
    }
    
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    val timeText = if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, secs)
    }

    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LIVE: $timeText",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun SpeakingIndicator(floorStatus: FloorStatus, isOthersSpeaking: Boolean, socketUiState: SocketUiState) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .height(100.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val anyOtherSpeaking = socketUiState.roomMembers.any { it.isSpeaking && !it.username.equals(socketUiState.username, ignoreCase = true) }
        val isRemoteTransmitting = floorStatus.state == FloorState.RECEIVING || floorStatus.state == FloorState.BUSY_BLOCKED
        val isLocalTransmitting = floorStatus.state == FloorState.TRANSMITTING
        val isRequesting = floorStatus.state == FloorState.REQUESTING

        AnimatedContent(
            targetState = when {
                isLocalTransmitting -> "YOU"
                isRequesting -> "REQUESTING"
                isRemoteTransmitting || isOthersSpeaking || anyOtherSpeaking -> "OTHERS"
                else -> "IDLE"
            },
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "indicator"
        ) { state ->
            when (state) {
                "YOU" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingDot(Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "YOU ARE TRANSMITTING",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                "REQUESTING" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingDot(Color(0xFFFFA000))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "ACQUIRING CHANNEL...",
                            color = Color(0xFFFFA000),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                "OTHERS" -> {
                    val activeSpeaker = floorStatus.currentSpeakerName
                        ?: socketUiState.roomMembers.find { it.isSpeaking && !it.username.equals(socketUiState.username, ignoreCase = true) }?.username
                        ?: socketUiState.lastSpeakerName
                    val speakerName = activeSpeaker?.trim()?.uppercase()
                    val displayText = if (!speakerName.isNullOrBlank() && speakerName != socketUiState.username.uppercase()) {
                        "$speakerName IS SPEAKING"
                    } else {
                        "SQUAD MEMBER SPEAKING"
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingDot(Color(0xFFF44336))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            displayText,
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No one is talking...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        
                        val lastSpeaker = socketUiState.lastSpeakerName
                        if (lastSpeaker != null) {
                            val timeAgo = (currentTime - socketUiState.lastSpeakerTimestamp) / 1000
                            if (timeAgo < 60) {
                                Text(
                                    "Last: $lastSpeaker (${timeAgo}s ago)",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Text(
                            "Hold the button to start the conversation 🎤",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "scale"
    )
    Box(modifier = Modifier.size(8.dp).scale(scale).background(color, CircleShape))
}

@Composable
fun ControlButton(icon: ImageVector, label: String, tint: Color = Color.Gray, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.background(Color(0xFF1E2126), CircleShape)) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Text(text = label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun BackgroundComposable(isActive: Boolean, gyroOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")

    val move1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "move1"
    )
    val move2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Reverse),
        label = "move2"
    )

    val intensity by animateFloatAsState(
        targetValue = if (isActive) 0.55f else 0.35f,
        animationSpec = tween(1000),
        label = "intensity"
    )

    val pulseScale by animateFloatAsState(
        targetValue = if (isActive) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020203))
    ) {
        // Blob 1: Indigo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (-0.3f + 0.6f * move1) * size.width + gyroOffset.x
                    translationY = (-0.2f + 0.5f * move2) * size.height + gyroOffset.y
                    scaleX = 2.0f * pulseScale
                    scaleY = 2.0f * pulseScale
                    alpha = intensity
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF6366F1), Color.Transparent),
                    )
                )
        )

        // Blob 2: Cyan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (0.3f - 0.6f * move2) * size.width - gyroOffset.x
                    translationY = (0.2f + 0.5f * move1) * size.height - gyroOffset.y
                    scaleX = 2.2f * pulseScale
                    scaleY = 2.2f * pulseScale
                    alpha = intensity * 0.8f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF06B6D4), Color.Transparent),
                    )
                )
        )
    }
}

@Composable
fun JoinCreateScreen(
    socketUiState: SocketUiState, 
    initialRoomId: String = "",
    onJoin: (String, String) -> Unit, 
    onCreate: (String) -> Unit
) {
    var u by remember { mutableStateOf("") }
    var r by remember { mutableStateOf(initialRoomId) }
    
    // Sync r if initialRoomId changes (e.g. deep link arrives while app is open)
    LaunchedEffect(initialRoomId) {
        if (initialRoomId.isNotEmpty()) r = initialRoomId
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Radio, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(80.dp))
        Text("Squad Talk", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("Minimal. Private. Instant.", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedTextField(value = u, onValueChange = { u = it }, label = { Text("Your Display Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = r, onValueChange = { r = it }, label = { Text("Squad Code (to join)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = { if(u.isNotBlank() && r.isNotBlank()) onJoin(r, u) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), enabled = u.isNotBlank() && r.isNotBlank()) {
            Text("JOIN SQUAD", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        TextButton(onClick = { if(u.isNotBlank()) onCreate(u) }, enabled = u.isNotBlank()) {
            Text("CREATE NEW SQUAD", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        }
        
        if (socketUiState.detail.contains("Retry")) {
            Text("Connecting to server...", color = Color.Yellow, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
