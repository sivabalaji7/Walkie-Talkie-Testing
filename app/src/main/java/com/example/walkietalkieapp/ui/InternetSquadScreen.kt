package com.example.walkietalkieapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.floor.FloorManager
import com.example.walkietalkieapp.floor.FloorState
import com.example.walkietalkieapp.floor.FloorStatus
import com.example.walkietalkieapp.socket.SupabaseRealtimeManager
import com.example.walkietalkieapp.socket.SocketUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternetSquadScreen(
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
    isKrispAiEnabled: Boolean,
    onKrispAiToggle: (Boolean) -> Unit,
    notificationMessage: String? = null
) {
    val floorStatus by FloorManager.floorStatus.collectAsStateWithLifecycle()
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
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section: Squad Info & Navigation Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings Icon Button (Machined Pod)
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TactileColors.surfaceContainerLow)
                        .border(1.dp, TactileColors.ghostBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Squad Settings",
                        tint = TactileColors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Squad Name & Channel Status Pills
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = TactileColors.primaryContainer.copy(alpha = 0.15f),
                            shape = TactileShapes.small
                        ) {
                            Text(
                                text = "INTERNET",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TactileColors.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (socketUiState.roomName.isNotEmpty()) socketUiState.roomName.uppercase() else "SQUAD: ${socketUiState.roomId}",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TactileColors.onSurface,
                            letterSpacing = 0.5.sp
                        )
                        if (isOwner) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = TactileColors.primaryContainer.copy(alpha = 0.2f),
                                shape = TactileShapes.small
                            ) {
                                Text(
                                    text = "👑",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Status Indicator Row (Valour Link + Krisp AI + Voice Mesh Link)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        val allLinks by com.example.walkietalkieapp.dna.valour.ValourLinkIntelligence.allLinksState.collectAsStateWithLifecycle()
                        val internetLinkState = allLinks[com.example.walkietalkieapp.dna.model.TransportType.Internet]
                            ?: com.example.walkietalkieapp.dna.valour.ValourLinkIntelligence.mapToValourLinkState(
                                com.example.walkietalkieapp.dna.model.CommunicationPathAssessment.unavailable(com.example.walkietalkieapp.dna.model.TransportType.Internet)
                            )

                        var showDetailsSheet by remember { mutableStateOf(false) }

                        com.example.walkietalkieapp.dna.valour.ValourLinkPill(
                            linkState = internetLinkState,
                            onClick = { showDetailsSheet = true }
                        )

                        if (showDetailsSheet) {
                            com.example.walkietalkieapp.dna.valour.ValourConnectionDetailsSheet(
                                linkState = internetLinkState,
                                onDismissRequest = { showDetailsSheet = false }
                            )
                        }

                        if (isKrispAiEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = TactileColors.statusActive.copy(alpha = 0.15f),
                                shape = TactileShapes.pill,
                                modifier = Modifier.border(1.dp, TactileColors.statusActive.copy(alpha = 0.3f), TactileShapes.pill)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = TactileColors.statusActive,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "KRISP AI",
                                        color = TactileColors.statusActive,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // WebRTC Voice Link Live Status
                        val voiceLink = socketUiState.voiceLinkState
                        val isMeshUp = voiceLink == "CONNECTED"
                        val isMeshLinking = voiceLink == "LINKING"
                        val isMeshFailed = voiceLink == "FAILED"

                        val linkColor = when {
                            isMeshUp -> TactileColors.statusActive
                            isMeshLinking -> TactileColors.primaryContainer
                            isMeshFailed -> TactileColors.error
                            else -> TactileColors.onSecondaryContainer
                        }
                        val linkText = when {
                            isMeshUp -> "VOICE READY"
                            isMeshLinking -> "LINKING..."
                            isMeshFailed -> "RETRY LINK"
                            else -> "VOICE IDLE"
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = linkColor.copy(alpha = 0.15f),
                            shape = TactileShapes.pill,
                            modifier = Modifier
                                .border(1.dp, linkColor.copy(alpha = 0.35f), TactileShapes.pill)
                                .clickable {
                                    if (isMeshFailed || !isMeshUp) {
                                        vibrate()
                                        onRestartIce()
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMeshUp) Icons.Default.CheckCircle else if (isMeshFailed) Icons.Default.Refresh else Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = linkColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = linkText,
                                    color = linkColor,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Share Button (Machined Pod)
                IconButton(
                    onClick = { onShare(socketUiState.roomId) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TactileColors.surfaceContainerLow)
                        .border(1.dp, TactileColors.ghostBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Squad Code",
                        tint = TactileColors.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Active Transmission Floor Banner
            Spacer(modifier = Modifier.height(14.dp))
            ActiveTransmissionBanner(floorStatus = floorStatus)

            // Pending Join Requests for Owner
            if (pendingRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = TactileColors.surfaceContainerLow,
                    shape = TactileShapes.tile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, TactileColors.primaryContainer.copy(alpha = 0.5f), TactileShapes.tile)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${pendingRequests.size} PENDING REQUEST(S)",
                            fontFamily = SpaceGrotesk,
                            color = TactileColors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                        pendingRequests.forEach { req ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = req.username,
                                    fontFamily = SpaceGrotesk,
                                    color = TactileColors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                com.example.walkietalkieapp.auth.SupabaseRoomManager.approveRequest(req.roomId, req.userId)
                                                fetchPending()
                                                fetchApprovedMembers()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(TactileColors.statusActive.copy(alpha = 0.2f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = TactileColors.statusActive, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                com.example.walkietalkieapp.auth.SupabaseRoomManager.declineRequest(req.roomId, req.userId)
                                                fetchPending()
                                                fetchApprovedMembers()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(TactileColors.error.copy(alpha = 0.2f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = TactileColors.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TimerView()

            Spacer(modifier = Modifier.height(18.dp))

            // Squad Member Avatars (LazyRow with speaking halo)
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
                            text = "Waiting for squad members to join...",
                            fontFamily = Manrope,
                            color = TactileColors.onSecondaryContainer.copy(alpha = 0.5f),
                            fontSize = 12.sp
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
                        val isOnline = isSelf || onlineUsernames.contains(uname.trim().lowercase()) || isMemberSpeaking
                        InternetMemberItem(
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
            InternetSpeakingIndicator(
                floorStatus = floorStatus,
                isOthersSpeaking = isOthersSpeaking,
                socketUiState = socketUiState
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Signature Tactile PTT Button
            InternetPushToTalkButton(
                floorStatus = floorStatus,
                isConnected = socketUiState.isConnected,
                isOwner = isOwner,
                onPress = { isPriority ->
                    if (!hasAudioPermission) onRequestPermission()
                    else {
                        vibrate()
                        onStartTalk(isPriority)
                        SupabaseRealtimeManager.updateActivity()
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

            Spacer(modifier = Modifier.weight(1f))

            // Leave Squad Action Button
            Surface(
                onClick = onLeave,
                color = TactileColors.surfaceContainerLow,
                shape = TactileShapes.pill,
                modifier = Modifier
                    .border(1.dp, TactileColors.error.copy(alpha = 0.35f), TactileShapes.pill)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = TactileColors.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LEAVE SQUAD",
                        color = TactileColors.error,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Settings Bottom Sheet
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = TactileColors.surfaceContainerLow,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TactileColors.outlineVariant) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Text(
                        text = "Squad Controls",
                        fontFamily = SpaceGrotesk,
                        fontSize = 20.sp,
                        color = TactileColors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsItem(
                        icon = Icons.Default.Bolt,
                        title = "Whisper Mode",
                        subtitle = "Transmit a quick 2-second voice burst",
                        color = TactileColors.primaryContainer,
                        onClick = {
                            onWhisper()
                            showSettings = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = "Replay Last 10s",
                        subtitle = "Play back the most recent incoming voice transmission",
                        color = TactileColors.primary,
                        onClick = {
                            onReplay()
                            showSettings = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsItem(
                        icon = Icons.Default.GraphicEq,
                        title = "Krisp AI Denoising",
                        subtitle = if (isKrispAiEnabled) "Active neural transient & background noise filter" else "Standard audio filter",
                        color = if (isKrispAiEnabled) TactileColors.statusActive else TactileColors.onSecondaryContainer,
                        onClick = { onKrispAiToggle(!isKrispAiEnabled) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsItem(
                        icon = Icons.Default.BatterySaver,
                        title = "Battery Saver",
                        subtitle = if (isBatterySaverEnabled) "Auto-idle disconnect enabled" else "Always stay connected in foreground",
                        color = if (isBatterySaverEnabled) TactileColors.statusActive else TactileColors.onSecondaryContainer,
                        onClick = { onBatterySaverToggle(!isBatterySaverEnabled) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsItem(
                        icon = Icons.Default.Share,
                        title = "Share Squad Code",
                        subtitle = "Invite team with code: ${socketUiState.roomId}",
                        color = TactileColors.statusConnecting,
                        onClick = {
                            onShare(socketUiState.roomId)
                            showSettings = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsItem(
                        icon = Icons.Default.Sync,
                        title = "Refresh Audio Link",
                        subtitle = "Restart ICE signaling if remote audio drops",
                        color = TactileColors.onSecondaryContainer,
                        onClick = {
                            onRestartIce()
                            showSettings = false
                        }
                    )
                }
            }
        }

        // Notification Chip
        if (notificationMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                NotificationBanner(message = notificationMessage)
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
            TactileColors.surfaceContainerLow,
            TactileColors.ghostBorder,
            "CHANNEL OPEN • READY",
            TactileColors.onSurfaceVariant,
            TactileColors.primaryContainer
        )
        FloorState.REQUESTING -> FloorBannerConfig(
            TactileColors.primaryContainer.copy(alpha = 0.15f),
            TactileColors.primaryContainer.copy(alpha = 0.6f),
            "ACQUIRING CHANNEL...",
            TactileColors.primary,
            TactileColors.primaryContainer
        )
        FloorState.TRANSMITTING -> FloorBannerConfig(
            TactileColors.primaryContainer.copy(alpha = 0.2f),
            TactileColors.primaryContainer,
            "TRANSMITTING • LIVE",
            TactileColors.primary,
            TactileColors.primaryContainer
        )
        FloorState.RECEIVING -> {
            val name = floorStatus.currentSpeakerName ?: "MEMBER"
            FloorBannerConfig(
                TactileColors.statusConnecting.copy(alpha = 0.15f),
                TactileColors.statusConnecting.copy(alpha = 0.5f),
                "$name IS TRANSMITTING",
                TactileColors.statusConnecting,
                TactileColors.statusConnecting
            )
        }
        FloorState.BUSY_BLOCKED -> {
            val name = floorStatus.currentSpeakerName ?: "SOMEONE"
            FloorBannerConfig(
                TactileColors.error.copy(alpha = 0.15f),
                TactileColors.error.copy(alpha = 0.5f),
                "CHANNEL BUSY • $name IS SPEAKING",
                TactileColors.error,
                TactileColors.error
            )
        }
    }

    Surface(
        color = bgColor,
        shape = TactileShapes.pill,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, borderColor, TactileShapes.pill)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
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
                fontFamily = SpaceGrotesk,
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
fun InternetPushToTalkButton(
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

    var isLocked by remember { mutableStateOf(false) }
    var lockSecondsRemaining by remember { mutableIntStateOf(20) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val lockThresholdPx = with(density) { 65.dp.toPx() }

    LaunchedEffect(isLocked) {
        if (isLocked) {
            lockSecondsRemaining = 20
            while (isLocked && lockSecondsRemaining > 0) {
                delay(1000L)
                lockSecondsRemaining--
            }
            if (isLocked && lockSecondsRemaining <= 0) {
                isLocked = false
                dragOffsetY = 0f
                onRelease()
            }
        } else {
            lockSecondsRemaining = 20
        }
    }

    LaunchedEffect(floorStatus.state) {
        if (floorStatus.state != FloorState.TRANSMITTING && floorStatus.state != FloorState.REQUESTING) {
            isLocked = false
            dragOffsetY = 0f
            lockSecondsRemaining = 20
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isTransmitting || isLocked) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulse1Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2.1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1Scale"
    )
    val pulse1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1Alpha"
    )

    val pulse2Scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing, delayMillis = 900), RepeatMode.Restart),
        label = "pulse2Scale"
    )
    val pulse2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing, delayMillis = 900), RepeatMode.Restart),
        label = "pulse2Alpha"
    )

    val ringColor = when {
        isLocked -> TactileColors.statusActive
        isTransmitting -> TactileColors.primaryContainer
        isRequesting -> TactileColors.primary
        isBusy -> TactileColors.error
        else -> TactileColors.ghostBorder
    }

    val buttonGradient = when {
        isLocked -> listOf(TactileColors.statusActive, Color(0xFF2E7D32))
        isTransmitting -> listOf(TactileColors.primary, TactileColors.primaryContainer)
        isRequesting -> listOf(TactileColors.primaryContainer, TactileColors.primaryGradientEnd)
        isBusy -> listOf(TactileColors.surfaceContainerHigh, TactileColors.surfaceContainerLow)
        else -> listOf(TactileColors.surfaceContainerHighest, TactileColors.surfaceContainerLow)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        contentAlignment = Alignment.Center
    ) {
        // Lock instruction overlay
        AnimatedVisibility(
            visible = (isTransmitting || isRequesting) && !isLocked,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val visualDragOffset = (dragOffsetY.coerceIn(-lockThresholdPx, 0f) * 0.5f).toInt()
            Surface(
                color = TactileColors.surfaceContainerLow,
                shape = TactileShapes.pill,
                modifier = Modifier
                    .offset { IntOffset(0, visualDragOffset) }
                    .border(1.dp, TactileColors.primaryContainer.copy(alpha = 0.5f), TactileShapes.pill)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TactileColors.primaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SWIPE UP TO LOCK",
                        color = TactileColors.onSurface,
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // Expanding concentric transmission rings
            if (isTransmitting || isLocked) {
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .scale(pulse1Scale)
                        .background(TactileColors.primaryContainer.copy(alpha = pulse1Alpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .scale(pulse2Scale)
                        .background(TactileColors.primaryContainer.copy(alpha = pulse2Alpha), CircleShape)
                )
            } else if (isRequesting) {
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .scale(pulse1Scale)
                        .background(TactileColors.primary.copy(alpha = pulse1Alpha), CircleShape)
                )
            }

            // Machined outer ring chassis (Vintage hardware aesthetic)
            Canvas(modifier = Modifier.size(200.dp)) {
                drawCircle(
                    color = Color(0xFF1E1D21),
                    radius = size.minDimension / 2f
                )
                drawCircle(
                    color = Color(0xFF353437).copy(alpha = 0.4f),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFF2A2A2D).copy(alpha = 0.3f),
                    radius = (size.minDimension / 2f) - 10.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Core PTT Button
            Box(
                modifier = Modifier
                    .size(165.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(brush = Brush.verticalGradient(colors = buttonGradient))
                    .border(
                        width = if (isTransmitting || isLocked) 3.dp else 1.5.dp,
                        color = ringColor,
                        shape = CircleShape
                    )
                    .pointerInput(isConnected, isLocked, isBusy) {
                        if (!isConnected) return@pointerInput
                        if (isLocked) {
                            detectTapGestures(
                                onTap = {
                                    isLocked = false
                                    dragOffsetY = 0f
                                    onRelease()
                                }
                            )
                        } else {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (isBusy && !isOwner) {
                                    onPress(false)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.all { !it.pressed }) break
                                    }
                                    return@awaitEachGesture
                                }
                                dragOffsetY = 0f
                                onPress(false)
                                var isReleased = false
                                while (!isReleased) {
                                    val event = awaitPointerEvent()
                                    val pointer = event.changes.find { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (pointer == null || !pointer.pressed) {
                                        isReleased = true
                                        if (!isLocked) {
                                            dragOffsetY = 0f
                                            onRelease()
                                        }
                                    } else {
                                        val currentDrag = pointer.position.y - down.position.y
                                        if (currentDrag < 0) {
                                            dragOffsetY = currentDrag
                                            if (-currentDrag >= lockThresholdPx && !isLocked) {
                                                isLocked = true
                                            }
                                        } else {
                                            dragOffsetY = 0f
                                        }
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when {
                            isLocked -> Icons.Default.Lock
                            isBusy -> Icons.Default.Close
                            isRequesting -> Icons.Default.Bolt
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = when {
                            isTransmitting || isLocked -> Color(0xFF131315)
                            isBusy -> TactileColors.onSecondaryContainer
                            else -> TactileColors.onSurface
                        },
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val buttonText = when {
                        isLocked -> "LOCKED • ${lockSecondsRemaining}s"
                        isTransmitting -> {
                            val secs = ((floorStatus.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) / 1000).toInt()
                            if (secs in 1..20) "TRANSMITTING (${secs}s)" else "TRANSMITTING"
                        }
                        isRequesting -> "ACQUIRING..."
                        isBusy -> "CHANNEL BUSY"
                        else -> "HOLD TO TALK"
                    }
                    Text(
                        text = buttonText,
                        color = when {
                            isTransmitting || isLocked -> Color(0xFF131315)
                            isBusy -> TactileColors.onSecondaryContainer
                            else -> TactileColors.onSurface
                        },
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Tap to stop banner when locked
        AnimatedVisibility(
            visible = isLocked,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Button(
                onClick = {
                    isLocked = false
                    dragOffsetY = 0f
                    onRelease()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TactileColors.error),
                shape = TactileShapes.pill,
                modifier = Modifier.height(42.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TAP TO STOP (${lockSecondsRemaining}s)",
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun InternetMemberItem(
    username: String,
    isOnline: Boolean,
    isSpeaking: Boolean,
    onReplay: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val speakingScale by animateFloatAsState(
        targetValue = if (isSpeaking) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "speakingScale"
    )

    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 8.dp)
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
                    .size(60.dp)
                    .border(
                        width = if (isSpeaking) 2.5.dp else 1.dp,
                        color = when {
                            isSpeaking -> TactileColors.primaryContainer.copy(alpha = borderAlpha)
                            isOnline -> TactileColors.outlineVariant.copy(alpha = 0.4f)
                            else -> TactileColors.ghostBorder
                        },
                        shape = CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isOnline) {
                                listOf(TactileColors.surfaceContainerHighest, TactileColors.surfaceContainerLow)
                            } else {
                                listOf(TactileColors.surfaceContainerLow, TactileColors.surfaceContainerLowest)
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.take(1).uppercase(),
                    color = when {
                        isSpeaking -> TactileColors.primaryContainer
                        isOnline -> TactileColors.onSurface
                        else -> TactileColors.onSecondaryContainer.copy(alpha = 0.4f)
                    },
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                if (isSpeaking) {
                    WaveformSmall()
                }
            }

            // Online / Offline status dot
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .background(TactileColors.surfaceContainerLowest, CircleShape)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isOnline) TactileColors.statusActive else TactileColors.onSecondaryContainer,
                            CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = username,
            color = when {
                isSpeaking -> TactileColors.primary
                isOnline -> TactileColors.onSurface
                else -> TactileColors.onSecondaryContainer.copy(alpha = 0.5f)
            },
            fontFamily = Manrope,
            fontSize = 11.sp,
            fontWeight = if (isSpeaking || isOnline) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun InternetSpeakingIndicator(
    floorStatus: FloorStatus,
    isOthersSpeaking: Boolean,
    socketUiState: SocketUiState
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .height(84.dp)
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
                        PulsingDot(TactileColors.primaryContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "YOU ARE TRANSMITTING",
                            color = TactileColors.primary,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                "REQUESTING" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingDot(TactileColors.primaryContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ACQUIRING CHANNEL...",
                            color = TactileColors.primaryContainer,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
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
                        "SQUAD MEMBER TRANSMITTING"
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulsingDot(TactileColors.statusConnecting)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = displayText,
                            color = TactileColors.statusConnecting,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No one is talking...",
                            color = TactileColors.onSurfaceVariant.copy(alpha = 0.6f),
                            fontFamily = Manrope,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        val lastSpeaker = socketUiState.lastSpeakerName
                        if (lastSpeaker != null) {
                            val timeAgo = (currentTime - socketUiState.lastSpeakerTimestamp) / 1000
                            if (timeAgo < 60) {
                                Text(
                                    text = "Last: $lastSpeaker (${timeAgo}s ago)",
                                    color = TactileColors.onSecondaryContainer,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Hold PTT button to transmit",
                            color = TactileColors.onSecondaryContainer.copy(alpha = 0.6f),
                            fontFamily = Manrope,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
