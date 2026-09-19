package com.example.walkietalkieapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walkietalkieapp.auth.Room
import com.example.walkietalkieapp.auth.RoomResult
import com.example.walkietalkieapp.auth.SessionManager
import com.example.walkietalkieapp.auth.SupabaseAuthManager
import com.example.walkietalkieapp.auth.SupabaseRoomManager
import com.example.walkietalkieapp.bluetooth.BluetoothSquadUiState
import com.example.walkietalkieapp.bluetooth.BluetoothWalkieTalkieService
import com.example.walkietalkieapp.floor.FloorManager
import com.example.walkietalkieapp.floor.FloorState
import com.example.walkietalkieapp.socket.SupabaseRealtimeManager
import com.example.walkietalkieapp.ui.*
import com.example.walkietalkieapp.ui.theme.WalkieTalkieAppTheme
import com.example.walkietalkieapp.ui.walkie.SquadMember
import com.example.walkietalkieapp.ui.walkie.WalkieTalkieApp
import com.example.walkietalkieapp.webrtc.WalkieTalkieService
import com.example.walkietalkieapp.audio.engine.VoiceQualityEngine
import com.example.walkietalkieapp.wifidirect.WifiSquadUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity(), SensorEventListener {

    // WebRTC / Online Background Service
    private var webRtcService: WalkieTalkieService? = null
    private var isWebRtcBound by mutableStateOf(false)
    private var pendingTalkStart = false

    // Bluetooth & Wi-Fi Direct Offline Background Service
    private var offlineService: BluetoothWalkieTalkieService? = null
    private var isOfflineBound by mutableStateOf(false)

    // Session & Auth
    private lateinit var sessionManager: SessionManager
    private var isLoggedIn by mutableStateOf(false)
    private var currentUserId by mutableStateOf("")
    private var currentUsername by mutableStateOf("")
    private var persistentCallSign by mutableStateOf("")

    // Hardware and Mode Management
    private var selectedTransportMode by mutableStateOf(TransportMode.INTERNET)
    private var hasAllPermissions by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)

    // Dialog States
    private var showCallSignDialog by mutableStateOf(false)
    private var showBluetoothDialog by mutableStateOf(false)
    private var showWifiDialog by mutableStateOf(false)
    private var showLocationDialog by mutableStateOf(false)

    // Tone & Vibrations
    private var toneGenerator: ToneGenerator? = null
    private var isBatterySaverEnabled by mutableStateOf(true)
    private var isKrispAiEnabled by mutableStateOf(true)
    private var isOthersSpeakingOnline by mutableStateOf(false)
    private var lastIncomingAlertTimestamp = 0L
    private val INCOMING_ALERT_COOLDOWN_MS = 5000L

    // Notifications & Deep Links
    private var notificationMessage by mutableStateOf<String?>(null)
    private val notificationQueue = mutableStateListOf<String>()
    private var deepLinkRoomId by mutableStateOf("")

    // Parallax Gyro Sensors
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var gyroOffset by mutableStateOf(Offset.Zero)
    private var isSystemReceiverRegistered = false

    // Bluetooth Enable System Launcher
    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startOfflineService()
                offlineService?.bluetoothManager?.startSquadScan()
            }
        }

    // Permission Launcher for All Transports
    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            hasAllPermissions = results.values.all { it }
            hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] == true
            if (hasAllPermissions) {
                startOfflineService()
                checkHardwareStateForCurrentMode()
            }
        }

    // System Broadcast Receiver for Bluetooth, Wi-Fi, and Location changes
    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        showBluetoothDialog = false
                        startOfflineService()
                        if (selectedTransportMode == TransportMode.BLUETOOTH) {
                            offlineService?.bluetoothManager?.startSquadScan()
                        }
                    }
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                        showWifiDialog = false
                        if (selectedTransportMode == TransportMode.WIFI_DIRECT) {
                            offlineService?.wifiDirectManager?.startDiscovery()
                        }
                    }
                }
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    if (isLocationEnabled()) {
                        showLocationDialog = false
                    }
                }
            }
        }
    }

    // Service Connection for WebRTC Online Voice
    private val webRtcServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WalkieTalkieService.LocalBinder
            webRtcService = binder.getService()
            isWebRtcBound = true

            webRtcService?.onOthersSpeakingStateChange = { speaking ->
                runOnUiThread {
                    if (speaking && !isOthersSpeakingOnline) {
                        triggerIncomingSpeakerAlert()
                    }
                    isOthersSpeakingOnline = speaking
                }
            }

            webRtcService?.webRTCManager?.onStateChange = { _ ->
                val aggState = webRtcService?.webRTCManager?.computeAggregatedVoiceLinkState() ?: "IDLE"
                SupabaseRealtimeManager.updateVoiceLinkState(aggState)
            }

            webRtcService?.webRTCManager?.let { webrtc ->
                VoiceQualityEngine.instance.initialize(
                    context = this@MainActivity,
                    webRtcRef = webrtc,
                    userId = currentUserId
                )
            }

            val currentRoom = SupabaseRealtimeManager.socketUiState.value.roomId
            if (hasAudioPermission && currentRoom.isNotEmpty()) {
                webRtcService?.startVoiceSession(currentRoom)
            }

            if (pendingTalkStart) {
                pendingTalkStart = false
                webRtcService?.webRTCManager?.startTalking()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isWebRtcBound = false
            webRtcService = null
        }
    }

    // Service Connection for Offline (Bluetooth & Wi-Fi Direct) Voice
    private val offlineServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothWalkieTalkieService.LocalBinder
            offlineService = binder.getService()
            isOfflineBound = true

            if (selectedTransportMode == TransportMode.BLUETOOTH) {
                if (isBluetoothEnabled()) offlineService?.bluetoothManager?.startSquadScan()
            } else if (selectedTransportMode == TransportMode.WIFI_DIRECT) {
                if (isWifiEnabled()) offlineService?.wifiDirectManager?.startDiscovery()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isOfflineBound = false
            offlineService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        // Request maximum available refresh rate (e.g. 120Hz / 90Hz) for fluid responsiveness
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val modes = display?.supportedModes ?: emptyArray()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate > 60f) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = maxMode.modeId
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "High refresh rate request skipped", e)
            }
        }

        sessionManager = SessionManager.getInstance(this)
        isLoggedIn = sessionManager.isLoggedIn()
        currentUserId = sessionManager.getUserId()
        currentUsername = sessionManager.getUsername()
        persistentCallSign = sessionManager.getCallSign()

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed", e)
        }

        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        } catch (e: Exception) {
            Log.w(TAG, "Sensor init failed", e)
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        if (!isSystemReceiverRegistered) {
            try {
                registerReceiver(systemStateReceiver, filter)
                isSystemReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register systemStateReceiver", e)
            }
        }

        checkAndRequestPermissions()

        // Initialize Communication DNA / Network Intelligence Engine
        com.example.walkietalkieapp.dna.engine.CommunicationDnaEngine.initialize(this)

        // Initialize Online WebRTC Socket
        SupabaseRealtimeManager.initialize()
        SupabaseRealtimeManager.connect()

        if (SupabaseRealtimeManager.socketUiState.value.roomId.isNotEmpty()) {
            startWebRtcService()
        }

        // WebRTC Online Floor Arbitration Callbacks
        FloorManager.onFloorGranted = {
            runOnUiThread {
                playTone(ToneGenerator.TONE_CDMA_PIP)
                vibrate()
                if (webRtcService?.webRTCManager != null) {
                    webRtcService?.webRTCManager?.startTalking()
                } else {
                    pendingTalkStart = true
                    startWebRtcService()
                }
                webRtcService?.updateNotification("🔴 Transmitting...")
            }
        }

        FloorManager.onFloorDenied = { _, speakerName ->
            runOnUiThread {
                pendingTalkStart = false
                playTone(ToneGenerator.TONE_SUP_ERROR)
                vibrateError()
                notificationQueue.add("🔒 Channel Busy: $speakerName is speaking")
            }
        }

        FloorManager.onFloorRevoked = {
            runOnUiThread {
                pendingTalkStart = false
                webRtcService?.webRTCManager?.stopTalking()
                playTone(ToneGenerator.TONE_SUP_ERROR)
                vibrateError()
                val speaker = FloorManager.floorStatus.value.currentSpeakerName ?: "Squad Member"
                notificationQueue.add("⚠️ Mic Yielded: $speaker is speaking")
                val roomId = SupabaseRealtimeManager.socketUiState.value.roomId
                webRtcService?.updateNotification(if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk")
            }
        }

        FloorManager.onFloorReleased = {
            runOnUiThread {
                pendingTalkStart = false
                webRtcService?.webRTCManager?.stopTalking()
                SupabaseRealtimeManager.sendStopVoice()
                playTone(ToneGenerator.TONE_PROP_BEEP2)
                val roomId = SupabaseRealtimeManager.socketUiState.value.roomId
                webRtcService?.updateNotification(if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk")
            }
        }

        FloorManager.onFloorWarning = {
            runOnUiThread {
                playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD)
            }
        }

        FloorManager.onFloorTimeout = {
            runOnUiThread {
                webRtcService?.webRTCManager?.stopTalking()
                SupabaseRealtimeManager.sendStopVoice()
                playTone(ToneGenerator.TONE_SUP_ERROR)
                vibrateError()
                notificationQueue.add("⏱️ Transmission timed out (20s limit)")
                val roomId = SupabaseRealtimeManager.socketUiState.value.roomId
                webRtcService?.updateNotification(if (roomId.isNotEmpty()) "In Squad: $roomId" else "Ready to talk")
            }
        }

        setContent {
            WalkieTalkieAppTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = TactileColors.background) {
                    val socketUiState by SupabaseRealtimeManager.socketUiState.collectAsState()
                    val floorStatus by FloorManager.floorStatus.collectAsState()

                    val btManager = offlineService?.bluetoothManager
                    val wifiDirectManager = offlineService?.wifiDirectManager

                    val btUiState by (btManager?.uiState ?: remember { MutableStateFlow(BluetoothSquadUiState()) })
                        .collectAsStateWithLifecycle(initialValue = BluetoothSquadUiState())
                    val wifiUiState by (wifiDirectManager?.uiState ?: remember { MutableStateFlow(WifiSquadUiState()) })
                        .collectAsStateWithLifecycle(initialValue = WifiSquadUiState())

                    // UI Ready state sync for offline mesh
                    LaunchedEffect(wifiUiState.connectionState) {
                        if (wifiUiState.connectionState == "CONNECTED") {
                            wifiDirectManager?.onUiReady()
                        }
                    }
                    LaunchedEffect(btUiState.connectionState) {
                        if (btUiState.connectionState == "CONNECTED") {
                            btManager?.onUiReady()
                        }
                    }

                    val isBtActiveRoom = btUiState.connectionState == "HOSTING" || btUiState.connectionState == "CONNECTED" || btUiState.connectionState == "CONNECTING"
                    val isWifiActiveRoom = wifiUiState.connectionState == "HOSTING" || wifiUiState.connectionState == "CONNECTED" || wifiUiState.connectionState == "CONNECTING"
                    val isInternetActiveRoom = socketUiState.roomId.isNotEmpty()

                    val isUserSpeakingLocalOnline = floorStatus.state == FloorState.TRANSMITTING
                    val othersSpeakingStateOnline = socketUiState.roomMembers.any { it.isSpeaking && it.username != socketUiState.username }
                    val activeSpeakingOnline = isUserSpeakingLocalOnline || isOthersSpeakingOnline || othersSpeakingStateOnline || floorStatus.state == FloorState.RECEIVING

                    var isUserSpeakingLocalOffline by remember { mutableStateOf(false) }
                    val activeSpeakingOffline = isUserSpeakingLocalOffline || btUiState.isChannelBusy || wifiUiState.isChannelBusy
                    val activeSpeaking = activeSpeakingOnline || activeSpeakingOffline

                    // Load and sync myRooms, member rosters, and pending join requests for Internet mode
                    var myRooms by remember { mutableStateOf<List<Room>>(emptyList()) }
                    var roomMembersMap by remember { mutableStateOf<Map<String, List<SquadMember>>>(emptyMap()) }
                    var pendingRequests by remember { mutableStateOf<List<com.example.walkietalkieapp.auth.RoomMemberRequest>>(emptyList()) }
                    val scope = rememberCoroutineScope()

                    fun fetchPendingRequests() {
                        if (isLoggedIn && currentUserId.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                val res = SupabaseRoomManager.getPendingRequests(currentUserId)
                                if (res is RoomResult.Success) {
                                    withContext(Dispatchers.Main) {
                                        pendingRequests = res.data
                                    }
                                }
                            }
                        }
                    }

                    fun refreshRoomsAndMembers() {
                        if (isLoggedIn && currentUserId.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                val res = SupabaseRoomManager.getMyRooms(currentUserId)
                                if (res is RoomResult.Success) {
                                    val rooms = res.data
                                    withContext(Dispatchers.Main) {
                                        myRooms = rooms
                                    }

                                    val roomIds = rooms.map { it.id }
                                    val membersRes = SupabaseRoomManager.getAllRoomMembersForRooms(roomIds)
                                    if (membersRes is RoomResult.Success) {
                                        val newMap = mutableMapOf<String, List<SquadMember>>()
                                        val fetchedMap = membersRes.data
                                        rooms.forEach { room ->
                                            val membersList = fetchedMap[room.id] ?: emptyList()
                                            val squadMembers = membersList.map { m ->
                                                val isOwner = room.ownerId == currentUserId && m.username.equals(currentUsername, ignoreCase = true)
                                                SquadMember(
                                                    name = m.username,
                                                    avatar = m.username.take(1).uppercase(),
                                                    online = m.username.equals(currentUsername, ignoreCase = true),
                                                    isOwner = isOwner
                                                )
                                            }.toMutableList()

                                            // Ensure current user is in squad members if not present
                                            if (currentUsername.isNotBlank() && squadMembers.none { it.name.equals(currentUsername, ignoreCase = true) }) {
                                                squadMembers.add(
                                                    SquadMember(
                                                        name = currentUsername,
                                                        avatar = currentUsername.take(1).uppercase(),
                                                        online = true,
                                                        isOwner = room.ownerId == currentUserId
                                                    )
                                                )
                                            }

                                            // Multi-key mapping for bulletproof lookups
                                            val codeTrimmed = room.code.trim()
                                            val idTrimmed = room.id.trim()
                                            newMap[codeTrimmed] = squadMembers
                                            newMap[codeTrimmed.uppercase()] = squadMembers
                                            newMap[codeTrimmed.lowercase()] = squadMembers
                                            newMap[idTrimmed] = squadMembers
                                            newMap[idTrimmed.lowercase()] = squadMembers
                                        }
                                        withContext(Dispatchers.Main) {
                                            roomMembersMap = newMap
                                        }
                                    }
                                }
                            }
                            fetchPendingRequests()
                        } else {
                            myRooms = emptyList()
                            roomMembersMap = emptyMap()
                            pendingRequests = emptyList()
                        }
                    }

                    LaunchedEffect(isLoggedIn, currentUserId) {
                        refreshRoomsAndMembers()
                    }

                    // Auto-load members for active internet room if code is entered directly or via deep link
                    LaunchedEffect(socketUiState.roomId) {
                        val activeCode = socketUiState.roomId.trim()
                        if (activeCode.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                val res = SupabaseRoomManager.getApprovedRoomMembers(activeCode)
                                if (res is RoomResult.Success) {
                                    val mems = res.data.map { uname ->
                                        SquadMember(
                                            name = uname,
                                            avatar = uname.take(1).uppercase(),
                                            online = uname.equals(currentUsername, ignoreCase = true)
                                        )
                                    }.toMutableList()

                                    if (currentUsername.isNotBlank() && mems.none { it.name.equals(currentUsername, ignoreCase = true) }) {
                                        mems.add(
                                            SquadMember(
                                                name = currentUsername,
                                                avatar = currentUsername.take(1).uppercase(),
                                                online = true
                                            )
                                        )
                                    }

                                    withContext(Dispatchers.Main) {
                                        val updated = roomMembersMap.toMutableMap()
                                        updated[activeCode] = mems
                                        updated[activeCode.uppercase()] = mems
                                        updated[activeCode.lowercase()] = mems
                                        roomMembersMap = updated
                                    }
                                }
                            }
                            fetchPendingRequests()
                        }
                    }

                    // Auto-process deep links if any
                    LaunchedEffect(deepLinkRoomId, isLoggedIn) {
                        if (deepLinkRoomId.isNotEmpty() && isLoggedIn && currentUsername.isNotEmpty()) {
                            val targetCode = deepLinkRoomId
                            deepLinkRoomId = ""
                            val userId = currentUserId

                            CoroutineScope(Dispatchers.Main).launch {
                                val roomRes = SupabaseRoomManager.getRoomByCode(targetCode)
                                if (roomRes is RoomResult.Success) {
                                    val room = roomRes.data
                                    val statusRes = SupabaseRoomManager.getMemberStatus(room.id, userId)
                                    val status = if (statusRes is RoomResult.Success) statusRes.data else null
                                    val isOwner = room.ownerId == userId

                                    if (isOwner || status == "APPROVED") {
                                        notificationQueue.add("Entering ${room.name} 🚀")
                                        enterInternetSquad(room.code, currentUsername, room.name)
                                    } else if (status == "PENDING") {
                                        notificationQueue.add("⏳ Join request pending owner approval for ${room.name}")
                                    } else {
                                        val reqRes = SupabaseRoomManager.requestJoin(room.code, userId, currentUsername)
                                        if (reqRes is RoomResult.Success) {
                                            notificationQueue.add("⏳ Join request sent to ${room.name} owner for approval")
                                        } else {
                                            val errMsg = (reqRes as RoomResult.Error).message
                                            notificationQueue.add(errMsg)
                                        }
                                    }
                                } else {
                                    notificationQueue.add("⚠️ Squad not found for code: $targetCode")
                                }
                            }
                        }
                    }

                    WalkieTalkieApp(
                        currentUserId = currentUserId,
                        currentUsername = currentUsername.ifBlank { persistentCallSign.ifBlank { "User" } },
                        persistentCallSign = persistentCallSign,
                        isLoggedIn = isLoggedIn,
                        selectedMode = selectedTransportMode,
                        onSelectMode = { mode ->
                            selectedTransportMode = mode
                            val activeType = when (mode) {
                                TransportMode.BLUETOOTH -> com.example.walkietalkieapp.dna.model.TransportType.Bluetooth
                                TransportMode.WIFI_DIRECT -> com.example.walkietalkieapp.dna.model.TransportType.WifiDirect
                                TransportMode.INTERNET -> com.example.walkietalkieapp.dna.model.TransportType.Internet
                            }
                            VoiceQualityEngine.instance.activeTransport = activeType

                            if (mode == TransportMode.BLUETOOTH) {
                                wifiDirectManager?.stopDiscovery()
                                if (!isBluetoothEnabled()) {
                                    showBluetoothDialog = true
                                } else {
                                    btManager?.startSquadScan()
                                }
                            } else if (mode == TransportMode.WIFI_DIRECT) {
                                btManager?.stopSquadScan()
                                if (!isWifiEnabled()) {
                                    showWifiDialog = true
                                } else {
                                    wifiDirectManager?.startDiscovery()
                                }
                            } else {
                                btManager?.stopSquadScan()
                                wifiDirectManager?.stopDiscovery()
                            }
                        },
                        onEditCallSign = { showCallSignDialog = true },
                        onLogout = {
                            CoroutineScope(Dispatchers.IO).launch {
                                SupabaseAuthManager.signOut()
                            }
                            sessionManager.clearSession()
                            currentUserId = ""
                            currentUsername = ""
                            persistentCallSign = ""
                            isLoggedIn = false
                            webRtcService?.webRTCManager?.cleanup()
                            SupabaseRealtimeManager.leaveRoom()
                            btManager?.leaveSquad()
                            wifiDirectManager?.leaveSquad()
                        },
                        onAuthSuccess = { uid, uname ->
                            sessionManager.saveSession(uid, uname)
                            currentUserId = uid
                            currentUsername = uname
                            persistentCallSign = uname
                            isLoggedIn = true
                            refreshRoomsAndMembers()
                        },

                        // Internet Rooms
                        myRooms = myRooms,
                        roomMembersMap = roomMembersMap,
                        onCreateInternetRoom = { name, code ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val res = SupabaseRoomManager.createRoom(name, currentUserId, currentUsername)
                                if (res is RoomResult.Success) {
                                    refreshRoomsAndMembers()
                                    runOnUiThread {
                                        enterInternetSquad(res.data.code, currentUsername, res.data.name)
                                    }
                                }
                            }
                        },
                        onJoinInternetRoomByCode = { code ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val res = SupabaseRoomManager.getRoomByCode(code)
                                if (res is RoomResult.Success) {
                                    val room = res.data
                                    val statusRes = SupabaseRoomManager.getMemberStatus(room.id, currentUserId)
                                    val status = if (statusRes is RoomResult.Success) statusRes.data else null
                                    val isOwner = room.ownerId == currentUserId

                                    if (isOwner || status == "APPROVED") {
                                        refreshRoomsAndMembers()
                                        runOnUiThread {
                                            notificationQueue.add("Entering ${room.name} 🚀")
                                            enterInternetSquad(room.code, currentUsername, room.name)
                                        }
                                    } else if (status == "PENDING") {
                                        runOnUiThread {
                                            notificationQueue.add("⏳ Join request pending owner approval for ${room.name}")
                                        }
                                    } else {
                                        val reqRes = SupabaseRoomManager.requestJoin(room.code, currentUserId, currentUsername)
                                        if (reqRes is RoomResult.Success) {
                                            runOnUiThread {
                                                notificationQueue.add("⏳ Join request sent to ${room.name} owner for approval")
                                            }
                                        } else {
                                            val errMsg = (reqRes as RoomResult.Error).message
                                            runOnUiThread {
                                                notificationQueue.add(errMsg)
                                            }
                                        }
                                    }
                                } else {
                                    runOnUiThread {
                                        notificationQueue.add("⚠️ Squad not found for code: $code")
                                    }
                                }
                            }
                        },
                        onEnterInternetRoom = { roomCode, roomName ->
                            enterInternetSquad(roomCode, currentUsername, roomName)
                        },
                        onLeaveInternetRoom = {
                            webRtcService?.webRTCManager?.cleanup()
                            SupabaseRealtimeManager.leaveRoom()
                            webRtcService?.updateNotification("Ready to talk")
                        },
                        activeInternetRoomId = socketUiState.roomId,
                        activeInternetRoomName = socketUiState.roomName,
                        onShareSquadCode = { code ->
                            try {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Squad Code", code)
                                clipboard.setPrimaryClip(clip)
                                notificationQueue.add("📋 Squad Code copied: $code")
                            } catch (e: Exception) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Join my squad on SquadTalk with code: $code")
                                }
                                startActivity(Intent.createChooser(shareIntent, "Share Squad Code"))
                            }
                        },
                        pendingRequests = pendingRequests,
                        onApproveRequest = { req ->
                            scope.launch(Dispatchers.IO) {
                                val res = SupabaseRoomManager.approveRequest(req.roomId, req.userId)
                                if (res is RoomResult.Success) {
                                    withContext(Dispatchers.Main) {
                                        notificationQueue.add("✅ Approved ${req.username}")
                                    }
                                    refreshRoomsAndMembers()
                                }
                            }
                        },
                        onDeclineRequest = { req ->
                            scope.launch(Dispatchers.IO) {
                                val res = SupabaseRoomManager.declineRequest(req.roomId, req.userId)
                                if (res is RoomResult.Success) {
                                    withContext(Dispatchers.Main) {
                                        notificationQueue.add("❌ Declined ${req.username}")
                                    }
                                    refreshRoomsAndMembers()
                                }
                            }
                        },

                        // Floor & WebRTC
                        floorStatus = floorStatus,
                        socketUiState = socketUiState,
                        isOthersSpeakingOnline = isOthersSpeakingOnline,
                        hasAudioPermission = hasAudioPermission,
                        onRequestAudioPermission = { checkAndRequestPermissions() },
                        onStartTalkOnline = { isPriority ->
                            startPushToTalkOnline(isPriority)
                        },
                        onStopTalkOnline = {
                            stopPushToTalkOnline()
                        },
                        onReplayAudio = {
                            if (selectedTransportMode == TransportMode.INTERNET) {
                                webRtcService?.replayLastTransmissions()
                            } else if (selectedTransportMode == TransportMode.BLUETOOTH) {
                                btManager?.audioPlayer?.replayLastTransmissions()
                            } else {
                                wifiDirectManager?.audioPlayer?.replayLastTransmissions()
                            }
                        },
                        onWhisperOnline = {
                            vibrate()
                            startPushToTalkOnline(false)
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(2000)
                                stopPushToTalkOnline()
                            }
                        },

                        // Offline (Bluetooth & Wi-Fi Direct)
                        btUiState = btUiState,
                        wifiUiState = wifiUiState,
                        onStartBtScan = {
                            if (!isBluetoothEnabled()) showBluetoothDialog = true
                            else btManager?.startSquadScan()
                        },
                        onHostBtSquad = { user, squad ->
                            if (!isBluetoothEnabled()) showBluetoothDialog = true
                            else btManager?.hostSquad(user, squad)
                        },
                        onJoinBtSquad = { squad, user ->
                            if (!isBluetoothEnabled()) showBluetoothDialog = true
                            else btManager?.joinSquad(squad, user)
                        },
                        onStartWifiScan = {
                            if (!isWifiEnabled()) showWifiDialog = true
                            else wifiDirectManager?.startDiscovery()
                        },
                        onHostWifiSquad = { user, squad ->
                            if (!isWifiEnabled()) showWifiDialog = true
                            else wifiDirectManager?.hostSquad(user, squad)
                        },
                        onJoinWifiSquad = { squad, user ->
                            if (!isWifiEnabled()) showWifiDialog = true
                            else wifiDirectManager?.joinSquad(squad, user)
                        },
                        onStartTalkOffline = {
                            isUserSpeakingLocalOffline = true
                            vibrate()
                            playTone(ToneGenerator.TONE_CDMA_PIP)
                            if (selectedTransportMode == TransportMode.BLUETOOTH) {
                                btManager?.startTalking()
                            } else {
                                wifiDirectManager?.startTalking()
                            }
                            offlineService?.updateNotification("Transmitting...")
                        },
                        onStopTalkOffline = {
                            isUserSpeakingLocalOffline = false
                            if (selectedTransportMode == TransportMode.BLUETOOTH) {
                                btManager?.stopTalking()
                            } else {
                                wifiDirectManager?.stopTalking()
                            }
                            offlineService?.updateNotification("Ready to talk")
                        },
                        onLeaveOfflineSquad = {
                            if (selectedTransportMode == TransportMode.BLUETOOTH) {
                                btManager?.leaveSquad()
                            } else {
                                wifiDirectManager?.leaveSquad()
                            }
                            offlineService?.updateNotification("Squad Talk Ready")
                        },
                        isUserSpeakingOffline = isUserSpeakingLocalOffline,

                        // Controls
                        isBatterySaverEnabled = isBatterySaverEnabled,
                        onBatterySaverToggle = { isBatterySaverEnabled = it },
                        isKrispAiEnabled = isKrispAiEnabled,
                        onKrispAiToggle = { enabled ->
                            isKrispAiEnabled = enabled
                            webRtcService?.webRTCManager?.setKrispEnabled(enabled)
                            notificationQueue.add(if (enabled) "✨ Krisp AI Voice Denoising Active" else "Krisp AI Voice Denoising Disabled")
                        }
                    )

                    // Persistent Call Sign Edit Dialog
                    if (showCallSignDialog) {
                        CallSignDialog(
                            currentCallSign = persistentCallSign,
                            onSave = { newName ->
                                sessionManager.saveCallSign(newName)
                                persistentCallSign = newName
                                currentUsername = newName
                                showCallSignDialog = false
                            },
                            onDismiss = { showCallSignDialog = false }
                        )
                    }

                    // Bluetooth Enable System Dialog
                    if (showBluetoothDialog) {
                        BluetoothEnableDialog(
                            onDismiss = { showBluetoothDialog = false },
                            onLaunchEnable = {
                                showBluetoothDialog = false
                                try {
                                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                    enableBtLauncher.launch(enableBtIntent)
                                } catch (e: Exception) {
                                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                            }
                        )
                    }

                    // Wi-Fi Enable System Dialog
                    if (showWifiDialog) {
                        WifiEnableDialog(
                            onDismiss = { showWifiDialog = false },
                            onLaunchEnable = {
                                showWifiDialog = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    try {
                                        startActivity(Intent(Settings.Panel.ACTION_WIFI))
                                    } catch (e: Exception) {
                                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                    }
                                } else {
                                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                }
                            }
                        )
                    }

                    // Location Enable Dialog (Required by Android for BT/Wi-Fi Discovery)
                    if (showLocationDialog) {
                        LocationEnableDialog(
                            onDismiss = { showLocationDialog = false },
                            onLaunchSettings = {
                                showLocationDialog = false
                                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                        )
                    }

                    // Notification Queue Processor
                    LaunchedEffect(Unit) {
                        while (true) {
                            if (notificationQueue.isNotEmpty()) {
                                val msg = notificationQueue.removeAt(0)
                                notificationMessage = msg
                                android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
                                delay(2000)
                                notificationMessage = null
                            } else {
                                delay(500)
                            }
                        }
                    }

                    // Event Collectors for Online & Offline Transports
                    LaunchedEffect(Unit) {
                        SupabaseRealtimeManager.events.collect { event ->
                            notificationQueue.add(event)
                        }
                    }

                    LaunchedEffect(btManager) {
                        btManager?.events?.collect { event ->
                            notificationQueue.add(event)
                        }
                    }

                    LaunchedEffect(wifiDirectManager) {
                        wifiDirectManager?.events?.collect { event ->
                            notificationQueue.add(event)
                        }
                    }

                    // Incoming Speech Audio & Vibration Triggers for Offline Transports
                    val isOtherSpeakingBt = btUiState.isChannelBusy && btUiState.currentSpeakerId != btUiState.myId
                    val isOtherSpeakingWifi = wifiUiState.isChannelBusy && wifiUiState.currentSpeakerId != wifiUiState.myId
                    LaunchedEffect(isOtherSpeakingBt, isOtherSpeakingWifi) {
                        if (isOtherSpeakingBt || isOtherSpeakingWifi) {
                            vibrate()
                            playTone(ToneGenerator.TONE_PROP_BEEP)
                            val speaker = if (isOtherSpeakingBt) btUiState.currentSpeakerName else wifiUiState.currentSpeakerName
                            offlineService?.updateNotification("$speaker is transmitting...")
                        }
                    }
                }
            }
        }
    }

    private fun enterInternetSquad(roomId: String, username: String, roomName: String = "") {
        if (hasAudioPermission) {
            startWebRtcService()
        }
        val currentRoom = SupabaseRealtimeManager.socketUiState.value.roomId
        if (currentRoom.isNotEmpty() && currentRoom != roomId) {
            webRtcService?.webRTCManager?.cleanup()
            SupabaseRealtimeManager.leaveRoom()
        }
        SupabaseRealtimeManager.joinRoom(roomId, username, roomName)
        webRtcService?.startVoiceSession(roomId)
    }

    private fun startPushToTalkOnline(isPriority: Boolean = false) {
        val roomId = SupabaseRealtimeManager.socketUiState.value.roomId
        if (roomId.isEmpty()) return
        if (webRtcService == null) {
            startWebRtcService()
        }
        FloorManager.requestFloor(isPriority)
    }

    private fun stopPushToTalkOnline() {
        pendingTalkStart = false
        FloorManager.releaseFloor()
    }

    private fun startWebRtcService() {
        val intent = Intent(this, WalkieTalkieService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, webRtcServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startOfflineService() {
        val intent = Intent(this, BluetoothWalkieTalkieService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, offlineServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            hasAllPermissions = true
            hasAudioPermission = true
            startOfflineService()
            checkHardwareStateForCurrentMode()
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter?.isEnabled == true
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun checkHardwareStateForCurrentMode() {
        if (!isLocationEnabled()) {
            showLocationDialog = true
            return
        }

        if (selectedTransportMode == TransportMode.BLUETOOTH) {
            if (!isBluetoothEnabled()) {
                showBluetoothDialog = true
            }
        } else if (selectedTransportMode == TransportMode.WIFI_DIRECT) {
            if (!isWifiEnabled()) {
                showWifiDialog = true
            }
        }
    }

    private fun triggerIncomingSpeakerAlert() {
        val now = System.currentTimeMillis()
        if (now - lastIncomingAlertTimestamp >= INCOMING_ALERT_COOLDOWN_MS) {
            lastIncomingAlertTimestamp = now
            vibrate()
            playTone(ToneGenerator.TONE_PROP_BEEP)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions) {
            checkHardwareStateForCurrentMode()
        }
    }

    override fun onPause() {
        super.onPause()
        offlineService?.wifiDirectManager?.onAppBackgrounded()
        offlineService?.bluetoothManager?.onAppBackgrounded()
    }

    override fun onSensorChanged(event: SensorEvent?) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun playTone(toneType: Int) {
        try {
            toneGenerator?.startTone(toneType, 150)
        } catch (e: Exception) {
            Log.w(TAG, "Tone error", e)
        }
    }

    private fun vibrate() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration error", e)
        }
    }

    private fun vibrateError() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration error", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { data ->
            val paramRoomId = data.getQueryParameter("roomId") ?: data.getQueryParameter("code")
            val pathRoomId = if (data.pathSegments.size >= 2 && data.pathSegments[0].equals("join", ignoreCase = true)) {
                data.pathSegments[1]
            } else null

            val roomId = paramRoomId ?: pathRoomId
            if (!roomId.isNullOrBlank()) {
                deepLinkRoomId = roomId.trim().uppercase()
                Log.d(TAG, "Deep link received for room: $deepLinkRoomId")
            }
        }
    }

    private fun shareRoom(roomId: String) {
        val inviteLink = "https://walkie-talkie-app-server.onrender.com/join?roomId=$roomId"
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Join my Squad on Squad Talk!\n\nLink: $inviteLink\n\nCode: $roomId")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Invite Friends"))
        notificationQueue.add("Invite link copied to share 🚀")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isSystemReceiverRegistered) {
            runCatching { unregisterReceiver(systemStateReceiver) }
            isSystemReceiverRegistered = false
        }
        toneGenerator?.release()
        toneGenerator = null

        if (isWebRtcBound) {
            unbindService(webRtcServiceConnection)
            isWebRtcBound = false
        }
        if (isOfflineBound) {
            unbindService(offlineServiceConnection)
            isOfflineBound = false
        }

        // Stop services completely and dismiss any persistent notifications on app close
        runCatching { stopService(Intent(this, WalkieTalkieService::class.java)) }
        runCatching { stopService(Intent(this, BluetoothWalkieTalkieService::class.java)) }
    }
}
