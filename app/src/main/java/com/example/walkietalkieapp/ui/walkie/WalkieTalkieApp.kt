package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.walkietalkieapp.auth.Room
import com.example.walkietalkieapp.bluetooth.BluetoothSquadUiState
import com.example.walkietalkieapp.bluetooth.DiscoveredSquad
import com.example.walkietalkieapp.floor.FloorState
import com.example.walkietalkieapp.floor.FloorStatus
import com.example.walkietalkieapp.socket.SocketUiState
import com.example.walkietalkieapp.socket.SupabaseRealtimeManager
import com.example.walkietalkieapp.ui.TransportMode
import com.example.walkietalkieapp.ui.auth.AuthMode
import com.example.walkietalkieapp.ui.auth.WalkieAuthScreen
import com.example.walkietalkieapp.ui.theme.*
import com.example.walkietalkieapp.wifidirect.DiscoveredWifiSquad
import com.example.walkietalkieapp.wifidirect.WifiSquadUiState

@Composable
fun WalkieTalkieApp(
    currentUserId: String = "",
    currentUsername: String = "Guest Callsign",
    persistentCallSign: String = "",
    isLoggedIn: Boolean = false,
    selectedMode: TransportMode = TransportMode.INTERNET,
    onSelectMode: (TransportMode) -> Unit = {},
    onEditCallSign: () -> Unit = {},
    onLogout: () -> Unit = {},
    onAuthSuccess: (userId: String, username: String) -> Unit = { _, _ -> },

    // Internet Rooms
    myRooms: List<Room> = emptyList(),
    roomMembersMap: Map<String, List<SquadMember>> = emptyMap(),
    onCreateInternetRoom: (name: String, code: String) -> Unit = { _, _ -> },
    onJoinInternetRoomByCode: (code: String) -> Unit = {},
    onEnterInternetRoom: (roomCode: String, roomName: String) -> Unit = { _, _ -> },
    onLeaveInternetRoom: () -> Unit = {},
    activeInternetRoomId: String = "",
    activeInternetRoomName: String = "",
    onShareSquadCode: (String) -> Unit = {},
    pendingRequests: List<com.example.walkietalkieapp.auth.RoomMemberRequest> = emptyList(),
    onApproveRequest: (com.example.walkietalkieapp.auth.RoomMemberRequest) -> Unit = {},
    onDeclineRequest: (com.example.walkietalkieapp.auth.RoomMemberRequest) -> Unit = {},

    // Floor & WebRTC
    floorStatus: FloorStatus = FloorStatus(),
    socketUiState: SocketUiState = SocketUiState(),
    isOthersSpeakingOnline: Boolean = false,
    hasAudioPermission: Boolean = true,
    onRequestAudioPermission: () -> Unit = {},
    onStartTalkOnline: (isPriority: Boolean) -> Unit = {},
    onStopTalkOnline: () -> Unit = {},
    onReplayAudio: () -> Unit = {},
    onWhisperOnline: () -> Unit = {},

    // Offline (Bluetooth & Wi-Fi Direct)
    btUiState: BluetoothSquadUiState = BluetoothSquadUiState(),
    wifiUiState: WifiSquadUiState = WifiSquadUiState(),
    onStartBtScan: () -> Unit = {},
    onHostBtSquad: (user: String, squad: String) -> Unit = { _, _ -> },
    onJoinBtSquad: (squad: DiscoveredSquad, user: String) -> Unit = { _, _ -> },
    onStartWifiScan: () -> Unit = {},
    onHostWifiSquad: (user: String, squad: String) -> Unit = { _, _ -> },
    onJoinWifiSquad: (squad: DiscoveredWifiSquad, user: String) -> Unit = { _, _ -> },
    onStartTalkOffline: () -> Unit = {},
    onStopTalkOffline: () -> Unit = {},
    onLeaveOfflineSquad: () -> Unit = {},
    isUserSpeakingOffline: Boolean = false,

    // Controls
    isBatterySaverEnabled: Boolean = false,
    onBatterySaverToggle: (Boolean) -> Unit = {},
    isKrispAiEnabled: Boolean = true,
    onKrispAiToggle: (Boolean) -> Unit = {},

    modifier: Modifier = Modifier
) {
    var isDarkMode by remember { mutableStateOf(true) }
    var drawerOpen by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableIntStateOf(0) }
    var authModalMode by remember(isLoggedIn) { mutableStateOf<AuthMode?>(if (!isLoggedIn) AuthMode.LOGIN else null) }

    // Dialogs
    var showCreateSquadDialog by remember { mutableStateOf(false) }
    var showJoinSquadDialog by remember { mutableStateOf(false) }
    var showHostOfflineDialog by remember { mutableStateOf(false) }

    // Mode conversion
    val currentConnectivityMode = when (selectedMode) {
        TransportMode.INTERNET -> ConnectivityMode.INTERNET
        TransportMode.BLUETOOTH -> ConnectivityMode.BLUETOOTH
        TransportMode.WIFI_DIRECT -> ConnectivityMode.WIFI_DIRECT
    }

    // Active Room States
    val isInternetRoomActive = activeInternetRoomId.isNotEmpty()
    val isBtRoomActive = btUiState.connectionState == "HOSTING" || btUiState.connectionState == "CONNECTED" || btUiState.connectionState == "CONNECTING"
    val isWifiRoomActive = wifiUiState.connectionState == "HOSTING" || wifiUiState.connectionState == "CONNECTED" || wifiUiState.connectionState == "CONNECTING"
    val isInsideRoom = isInternetRoomActive || isBtRoomActive || isWifiRoomActive

    // Construct Active Squad for SquadRoom with ALL previous and live members
    val joinedSquad: Squad? = remember(
        isInternetRoomActive, activeInternetRoomId, activeInternetRoomName, socketUiState.roomMembers, roomMembersMap, myRooms,
        isBtRoomActive, btUiState.squadName, btUiState.members,
        isWifiRoomActive, wifiUiState.squadName, wifiUiState.members,
        currentUsername, persistentCallSign, floorStatus.state, isUserSpeakingOffline
    ) {
        when {
            isInternetRoomActive -> {
                val cleanActiveId = activeInternetRoomId.trim()
                val matchedRoom = myRooms.find {
                    it.code.equals(cleanActiveId, ignoreCase = true) || it.id.equals(cleanActiveId, ignoreCase = true)
                }
                val approved = roomMembersMap[cleanActiveId]
                    ?: roomMembersMap[cleanActiveId.uppercase()]
                    ?: roomMembersMap[cleanActiveId.lowercase()]
                    ?: (matchedRoom?.let { roomMembersMap[it.id] ?: roomMembersMap[it.code] })
                    ?: emptyList()

                val onlineSocketMembers = socketUiState.roomMembers
                val onlineUsernames = onlineSocketMembers.map { it.username.trim().lowercase() }.toSet()

                val mergedMembers = mutableListOf<SquadMember>()

                // 1. Add all approved members from DB with their live online/speaking state
                approved.forEach { appMember ->
                    val isSocketOnline = onlineUsernames.contains(appMember.name.trim().lowercase())
                    val livePeer = onlineSocketMembers.find { it.username.equals(appMember.name, ignoreCase = true) }
                    val isSpeaking = livePeer?.isSpeaking == true || (appMember.name.equals(currentUsername, ignoreCase = true) && floorStatus.state == FloorState.TRANSMITTING)
                    val isOnline = isSocketOnline || appMember.name.equals(currentUsername, ignoreCase = true)
                    val isOwner = appMember.isOwner || (matchedRoom != null && matchedRoom.ownerId == currentUserId && appMember.name.equals(currentUsername, ignoreCase = true))
                    mergedMembers.add(
                        SquadMember(
                            name = appMember.name,
                            avatar = appMember.avatar.ifBlank { appMember.name.take(1).uppercase() },
                            online = isOnline,
                            isSpeaking = isSpeaking,
                            isOwner = isOwner
                        )
                    )
                }

                // 2. Add any live socket member that wasn't in approved list
                onlineSocketMembers.forEach { sockMember ->
                    if (mergedMembers.none { it.name.equals(sockMember.username, ignoreCase = true) }) {
                        val isSpeaking = sockMember.isSpeaking || (sockMember.username.equals(currentUsername, ignoreCase = true) && floorStatus.state == FloorState.TRANSMITTING)
                        mergedMembers.add(
                            SquadMember(
                                name = sockMember.username,
                                avatar = sockMember.username.take(1).uppercase(),
                                online = true,
                                isSpeaking = isSpeaking,
                                isOwner = matchedRoom != null && matchedRoom.ownerId == currentUserId && sockMember.username.equals(currentUsername, ignoreCase = true)
                            )
                        )
                    }
                }

                // 3. Ensure current user is in list
                val selfName = currentUsername.ifBlank { persistentCallSign }
                if (selfName.isNotBlank() && mergedMembers.none { it.name.equals(selfName, ignoreCase = true) }) {
                    val isOwner = matchedRoom != null && matchedRoom.ownerId == currentUserId
                    mergedMembers.add(
                        0,
                        SquadMember(
                            name = selfName,
                            avatar = selfName.take(1).uppercase(),
                            online = true,
                            isSpeaking = floorStatus.state == FloorState.TRANSMITTING,
                            isOwner = isOwner
                        )
                    )
                }

                val finalRoomName = activeInternetRoomName.ifBlank {
                    matchedRoom?.name ?: "Squad $activeInternetRoomId"
                }

                Squad(
                    id = matchedRoom?.code ?: activeInternetRoomId,
                    name = finalRoomName,
                    lastActive = "${mergedMembers.size} members · ${mergedMembers.count { it.online }} online",
                    secure = true,
                    members = mergedMembers
                )
            }
            isBtRoomActive -> {
                val selfName = currentUsername.ifBlank { persistentCallSign }
                val membersList = btUiState.members.map {
                    SquadMember(
                        name = it.username,
                        avatar = it.username.take(1).uppercase(),
                        online = true,
                        isSpeaking = it.isSpeaking
                    )
                }.toMutableList()
                if (selfName.isNotBlank() && membersList.none { it.name.equals(selfName, ignoreCase = true) }) {
                    membersList.add(0, SquadMember(selfName, selfName.take(1).uppercase(), true, isUserSpeakingOffline))
                }
                Squad(
                    id = "bt-room",
                    name = btUiState.squadName.ifBlank { "Bluetooth Squad" },
                    lastActive = btUiState.connectionState,
                    secure = false,
                    members = membersList
                )
            }
            isWifiRoomActive -> {
                val selfName = currentUsername.ifBlank { persistentCallSign }
                val membersList = wifiUiState.members.map {
                    SquadMember(
                        name = it.username,
                        avatar = it.username.take(1).uppercase(),
                        online = true,
                        isSpeaking = it.isSpeaking
                    )
                }.toMutableList()
                if (selfName.isNotBlank() && membersList.none { it.name.equals(selfName, ignoreCase = true) }) {
                    membersList.add(0, SquadMember(selfName, selfName.take(1).uppercase(), true, isUserSpeakingOffline))
                }
                Squad(
                    id = "wifi-room",
                    name = wifiUiState.squadName.ifBlank { "Wi-Fi Direct Squad" },
                    lastActive = wifiUiState.connectionState,
                    secure = false,
                    members = membersList
                )
            }
            else -> null
        }
    }

    // Squad list to display in Hub based on mode with full member data
    val displayedSquads: List<Squad> = remember(selectedMode, myRooms, roomMembersMap, btUiState.discoveredSquads, wifiUiState.discoveredSquads, currentUsername) {
        when (selectedMode) {
            TransportMode.INTERNET -> {
                myRooms.map { room ->
                    val rawMembers = roomMembersMap[room.code]
                        ?: roomMembersMap[room.code.uppercase()]
                        ?: roomMembersMap[room.code.lowercase()]
                        ?: roomMembersMap[room.id]
                        ?: emptyList()
                    val finalMembers = if (rawMembers.isEmpty() && currentUsername.isNotBlank()) {
                        listOf(SquadMember(currentUsername, currentUsername.take(1).uppercase(), true, isOwner = room.ownerId == currentUserId))
                    } else {
                        rawMembers.map { m ->
                            val isOnline = m.name.equals(currentUsername, ignoreCase = true) ||
                                    (activeInternetRoomId.equals(room.code, ignoreCase = true) && socketUiState.roomMembers.any { it.username.equals(m.name, ignoreCase = true) })
                            val isOwner = m.isOwner || (room.ownerId == currentUserId && m.name.equals(currentUsername, ignoreCase = true))
                            m.copy(online = isOnline, isOwner = isOwner)
                        }
                    }

                    Squad(
                        id = room.code,
                        name = room.name,
                        lastActive = if (finalMembers.isNotEmpty()) "${finalMembers.size} members" else "Code: ${room.code}",
                        secure = true,
                        members = finalMembers
                    )
                }
            }
            TransportMode.BLUETOOTH -> {
                btUiState.discoveredSquads.map { bt ->
                    Squad(
                        id = bt.hostAddress,
                        name = bt.squadName,
                        lastActive = "Host: ${bt.hostUsername} • ${bt.rssi}dBm",
                        secure = false,
                        members = listOf(SquadMember(bt.hostUsername, bt.hostUsername.take(1).uppercase(), true))
                    )
                }
            }
            TransportMode.WIFI_DIRECT -> {
                wifiUiState.discoveredSquads.map { wf ->
                    Squad(
                        id = wf.deviceAddress,
                        name = wf.squadName,
                        lastActive = "Host: ${wf.hostUsername} • P2P",
                        secure = false,
                        members = listOf(SquadMember(wf.hostUsername, wf.hostUsername.take(1).uppercase(), true))
                    )
                }
            }
        }
    }

    // Transmission & Speaker state
    val isUserTransmitting = floorStatus.state == FloorState.TRANSMITTING || isUserSpeakingOffline
    val isOthersTransmitting = floorStatus.state == FloorState.RECEIVING || isOthersSpeakingOnline || btUiState.isChannelBusy || wifiUiState.isChannelBusy
    val currentTalkingState = when {
        isUserTransmitting -> TalkingState.YOU_TALKING
        isOthersTransmitting -> TalkingState.OTHER_TALKING
        else -> TalkingState.IDLE
    }

    // Connection status for LCD Display Panel
    val currentConnectionStatus = when (selectedMode) {
        TransportMode.INTERNET -> if (socketUiState.isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.SEARCHING
        TransportMode.BLUETOOTH -> if (btUiState.connectionState == "CONNECTED" || btUiState.connectionState == "HOSTING") ConnectionStatus.CONNECTED else ConnectionStatus.SEARCHING
        TransportMode.WIFI_DIRECT -> if (wifiUiState.connectionState == "CONNECTED" || wifiUiState.connectionState == "HOSTING") ConnectionStatus.CONNECTED else ConnectionStatus.SEARCHING
    }

    val currentTheme = ModeThemes.get(currentConnectivityMode)
    val screenBg = if (isDarkMode) WalkieDarkBackground else WalkieWarmCream
    val displayCallsign = if (isLoggedIn && currentUsername.isNotBlank()) currentUsername else "Guest Callsign"
    val displayAvatar = displayCallsign.take(1).uppercase()

    WalkieTalkieAppTheme(darkTheme = isDarkMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(screenBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 410.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = if (isInsideRoom) Arrangement.Top else Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Section: TopBar + Steel Antenna Mode Dial
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopBar(
                        onMenuOpen = { drawerOpen = true },
                        onProfileClick = {
                            if (!isLoggedIn) authModalMode = AuthMode.LOGIN else drawerOpen = true
                        },
                        isPowered = true,
                        userAvatar = displayAvatar,
                        username = displayCallsign,
                        isLoggedIn = isLoggedIn,
                        onShareClick = if (isInsideRoom && joinedSquad != null && !joinedSquad.id.startsWith("bt-") && !joinedSquad.id.startsWith("wifi-")) {
                            { onShareSquadCode(joinedSquad.id) }
                        } else null
                    )

                    AntennaModeDial(
                        mode = currentConnectivityMode,
                        onChange = { nextMode ->
                            val mapped = when (nextMode) {
                                ConnectivityMode.INTERNET -> TransportMode.INTERNET
                                ConnectivityMode.BLUETOOTH -> TransportMode.BLUETOOTH
                                ConnectivityMode.WIFI_DIRECT -> TransportMode.WIFI_DIRECT
                            }
                            onSelectMode(mapped)
                        },
                        locked = isInsideRoom,
                        emittingWaves = isUserTransmitting || isOthersTransmitting,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                }

                // Middle Section: Centered SquadHub or Fixed SquadRoom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isInsideRoom,
                        transitionSpec = {
                            (fadeIn(spring(dampingRatio = 0.82f, stiffness = 460f)) + scaleIn(initialScale = 0.95f))
                                .togetherWith(fadeOut(spring(dampingRatio = 0.82f, stiffness = 460f)) + scaleOut(targetScale = 0.95f))
                        },
                        label = "hubOrRoom"
                    ) { inside ->
                        if (inside && joinedSquad != null) {
                            SquadRoom(
                                squad = joinedSquad,
                                mode = currentConnectivityMode,
                                onExit = {
                                    when (selectedMode) {
                                        TransportMode.INTERNET -> onLeaveInternetRoom()
                                        else -> onLeaveOfflineSquad()
                                    }
                                },
                                talkingState = currentTalkingState,
                                status = currentConnectionStatus,
                                otherUser = floorStatus.currentSpeakerName ?: btUiState.currentSpeakerName ?: wifiUiState.currentSpeakerName ?: "",
                                onShareSquadCode = onShareSquadCode,
                                pendingRequests = pendingRequests.filter {
                                    it.roomId.equals(activeInternetRoomId, ignoreCase = true) ||
                                    it.roomCode.equals(activeInternetRoomId, ignoreCase = true) ||
                                    (myRooms.find { r -> r.code.equals(activeInternetRoomId, ignoreCase = true) }?.id == it.roomId)
                                },
                                onApproveRequest = onApproveRequest,
                                onDeclineRequest = onDeclineRequest,
                                onPressStart = {
                                    if (!hasAudioPermission) {
                                        onRequestAudioPermission()
                                    } else {
                                        when (selectedMode) {
                                            TransportMode.INTERNET -> onStartTalkOnline(false)
                                            else -> onStartTalkOffline()
                                        }
                                    }
                                },
                                onPressEnd = {
                                    when (selectedMode) {
                                        TransportMode.INTERNET -> onStopTalkOnline()
                                        else -> onStopTalkOffline()
                                    }
                                },
                                onQuickActions = onReplayAudio,
                                isE2EActive = socketUiState.isE2EActive,
                                e2eFingerprint = socketUiState.e2eFingerprint,
                                onRekeySession = { SupabaseRealtimeManager.rekeySession() }
                            )
                        } else {
                            val safeActiveIndex = if (displayedSquads.isNotEmpty()) {
                                activeIndex.mod(displayedSquads.size)
                            } else 0

                            SquadHub(
                                mode = currentConnectivityMode,
                                squads = displayedSquads,
                                activeIndex = safeActiveIndex,
                                onStep = { dir ->
                                    if (displayedSquads.isNotEmpty()) {
                                        activeIndex = (activeIndex + dir).mod(displayedSquads.size)
                                    }
                                },
                                onJoin = {
                                    when (selectedMode) {
                                        TransportMode.INTERNET -> {
                                            if (displayedSquads.isNotEmpty()) {
                                                displayedSquads.getOrNull(safeActiveIndex)?.let {
                                                    onEnterInternetRoom(it.id, it.name)
                                                }
                                            } else {
                                                showJoinSquadDialog = true
                                            }
                                        }
                                        TransportMode.BLUETOOTH -> {
                                            val btIdx = if (btUiState.discoveredSquads.isNotEmpty()) {
                                                activeIndex.mod(btUiState.discoveredSquads.size)
                                            } else 0
                                            btUiState.discoveredSquads.getOrNull(btIdx)?.let {
                                                onJoinBtSquad(it, persistentCallSign.ifBlank { displayCallsign })
                                            } ?: run { onStartBtScan() }
                                        }
                                        TransportMode.WIFI_DIRECT -> {
                                            val wifiIdx = if (wifiUiState.discoveredSquads.isNotEmpty()) {
                                                activeIndex.mod(wifiUiState.discoveredSquads.size)
                                            } else 0
                                            wifiUiState.discoveredSquads.getOrNull(wifiIdx)?.let {
                                                onJoinWifiSquad(it, persistentCallSign.ifBlank { displayCallsign })
                                            } ?: run { onStartWifiScan() }
                                        }
                                    }
                                },
                                onCreate = {
                                    if (selectedMode == TransportMode.INTERNET) {
                                        showCreateSquadDialog = true
                                    } else {
                                        showHostOfflineDialog = true
                                    }
                                },
                                onJoinByCode = {
                                    if (selectedMode == TransportMode.INTERNET) {
                                        showJoinSquadDialog = true
                                    } else if (selectedMode == TransportMode.BLUETOOTH) {
                                        onStartBtScan()
                                    } else {
                                        onStartWifiScan()
                                    }
                                }
                            )
                        }
                    }
                }

                // Bottom Section: Floating Action Buttons (in hub view)
                if (!isInsideRoom && currentConnectivityMode == ConnectivityMode.INTERNET) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Join Squad Button
                            Row(
                                modifier = Modifier
                                    .shadow(elevation = 6.dp, shape = CircleShape)
                                    .clip(CircleShape)
                                    .background(WalkieCard)
                                    .border(1.dp, WalkieCardBorder, CircleShape)
                                    .clickable { showJoinSquadDialog = true }
                                    .padding(horizontal = 22.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    tint = WalkieTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Join Squad",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalkieTextPrimary
                                )
                            }

                            // Create Squad Button
                            Row(
                                modifier = Modifier
                                    .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = currentTheme.primaryColor, spotColor = currentTheme.primaryColor)
                                    .clip(CircleShape)
                                    .background(currentTheme.gradient)
                                    .clickable { showCreateSquadDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Create Squad",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }

            // Side Drawer
            SideDrawer(
                isOpen = drawerOpen,
                onClose = { drawerOpen = false },
                onOpenAuth = { m ->
                    authModalMode = if (m == "signup") AuthMode.SIGNUP else AuthMode.LOGIN
                },
                username = displayCallsign,
                userAvatar = displayAvatar,
                isLoggedIn = isLoggedIn,
                isDarkMode = isDarkMode,
                onToggleDarkMode = { isDarkMode = it },
                onLogout = {
                    drawerOpen = false
                    onLogout()
                },
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Auth Dialog Modal Overlay
            if (authModalMode != null) {
                Dialog(
                    onDismissRequest = { authModalMode = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        WalkieAuthScreen(
                            initialMode = authModalMode ?: AuthMode.LOGIN,
                            onAuthSuccess = { uid, uname ->
                                onAuthSuccess(uid, uname)
                                authModalMode = null
                            },
                            onBackToRadio = { authModalMode = null }
                        )
                    }
                }
            }

            // Create Internet Squad Dialog
            if (showCreateSquadDialog) {
                var squadNameInput by remember { mutableStateOf("") }
                var squadCodeInput by remember {
                    mutableStateOf((100000..999999).random().toString())
                }

                Dialog(onDismissRequest = { showCreateSquadDialog = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(WalkieCardShape)
                            .background(WalkieCard)
                            .border(1.dp, WalkieCardBorder, WalkieCardShape)
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "CREATE SQUAD CHANNEL",
                                fontFamily = SpaceGrotesk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieAmberLight,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = squadNameInput,
                                onValueChange = { squadNameInput = it },
                                label = { Text("Squad Name (e.g. BRAVO TEAM)", color = WalkieTextMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = WalkieTextPrimary,
                                    unfocusedTextColor = WalkieTextPrimary,
                                    focusedBorderColor = WalkieAmber,
                                    unfocusedBorderColor = WalkieCardBorder,
                                    focusedContainerColor = WalkieDeviceBody,
                                    unfocusedContainerColor = WalkieDeviceBody
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = squadCodeInput,
                                onValueChange = { squadCodeInput = it },
                                label = { Text("Squad Code (6 digits)", color = WalkieTextMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = WalkieTextPrimary,
                                    unfocusedTextColor = WalkieTextPrimary,
                                    focusedBorderColor = WalkieAmber,
                                    unfocusedBorderColor = WalkieCardBorder,
                                    focusedContainerColor = WalkieDeviceBody,
                                    unfocusedContainerColor = WalkieDeviceBody
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TextButton(
                                    onClick = { showCreateSquadDialog = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel", color = WalkieTextMuted, fontFamily = SpaceGrotesk)
                                }
                                Button(
                                    onClick = {
                                        if (squadNameInput.isNotBlank()) {
                                            onCreateInternetRoom(squadNameInput.trim(), squadCodeInput.trim())
                                            showCreateSquadDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WalkieAmber),
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Create", color = Color.Black, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Join Internet Squad Dialog
            if (showJoinSquadDialog) {
                var codeInput by remember { mutableStateOf("") }

                Dialog(onDismissRequest = { showJoinSquadDialog = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(WalkieCardShape)
                            .background(WalkieCard)
                            .border(1.dp, WalkieCardBorder, WalkieCardShape)
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "JOIN SQUAD BY CODE",
                                fontFamily = SpaceGrotesk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieAmberLight,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { codeInput = it },
                                label = { Text("Enter 6-digit Code", color = WalkieTextMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = WalkieTextPrimary,
                                    unfocusedTextColor = WalkieTextPrimary,
                                    focusedBorderColor = WalkieAmber,
                                    unfocusedBorderColor = WalkieCardBorder,
                                    focusedContainerColor = WalkieDeviceBody,
                                    unfocusedContainerColor = WalkieDeviceBody
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TextButton(
                                    onClick = { showJoinSquadDialog = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel", color = WalkieTextMuted, fontFamily = SpaceGrotesk)
                                }
                                Button(
                                    onClick = {
                                        if (codeInput.isNotBlank()) {
                                            onJoinInternetRoomByCode(codeInput.trim())
                                            showJoinSquadDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WalkieAmber),
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Join", color = Color.Black, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Host Offline Squad Dialog
            if (showHostOfflineDialog) {
                var offlineSquadName by remember { mutableStateOf("") }

                Dialog(onDismissRequest = { showHostOfflineDialog = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(WalkieCardShape)
                            .background(WalkieCard)
                            .border(1.dp, WalkieCardBorder, WalkieCardShape)
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (selectedMode == TransportMode.BLUETOOTH) "HOST BLUETOOTH SQUAD" else "HOST WI-FI DIRECT SQUAD",
                                fontFamily = SpaceGrotesk,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentTheme.primaryColor,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = offlineSquadName,
                                onValueChange = { offlineSquadName = it },
                                label = { Text("Squad Name (e.g. ALPHA)", color = WalkieTextMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = WalkieTextPrimary,
                                    unfocusedTextColor = WalkieTextPrimary,
                                    focusedBorderColor = currentTheme.primaryColor,
                                    unfocusedBorderColor = WalkieCardBorder,
                                    focusedContainerColor = WalkieDeviceBody,
                                    unfocusedContainerColor = WalkieDeviceBody
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TextButton(
                                    onClick = { showHostOfflineDialog = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel", color = WalkieTextMuted, fontFamily = SpaceGrotesk)
                                }
                                Button(
                                    onClick = {
                                        val squad = offlineSquadName.ifBlank { "OFFLINE-1" }
                                        val user = persistentCallSign.ifBlank { displayCallsign }
                                        if (selectedMode == TransportMode.BLUETOOTH) {
                                            onHostBtSquad(user, squad)
                                        } else {
                                            onHostWifiSquad(user, squad)
                                        }
                                        showHostOfflineDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primaryColor),
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Host", color = Color.Black, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
