package com.example.walkietalkieapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.walkietalkieapp.socket.SocketManager
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
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
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF6366F1).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "INTERNET",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF818CF8)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (socketUiState.roomName.isNotEmpty()) socketUiState.roomName.uppercase() else "SQUAD: ${socketUiState.roomId}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
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
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
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

                        if (isKrispAiEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF00E676).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("KRISP AI", color = Color(0xFF00E676), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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

            Spacer(modifier = Modifier.height(20.dp))

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
            InternetSpeakingIndicator(floorStatus = floorStatus, isOthersSpeaking = isOthersSpeaking, socketUiState = socketUiState)

            Spacer(modifier = Modifier.height(24.dp))

            // PTT Button with Floor Control State
            InternetPushToTalkButton(
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
                        icon = Icons.Default.GraphicEq,
                        title = "✨ Krisp AI Denoising",
                        subtitle = if (isKrispAiEnabled) "Active: Neural background & transient noise filter" else "Standard audio filtering",
                        color = if (isKrispAiEnabled) Color(0xFF00E676) else Color.Gray,
                        onClick = { onKrispAiToggle(!isKrispAiEnabled) }
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
            FloorBannerConfig(
                Color(0xFF4CAF50).copy(alpha = 0.2f),
                Color(0xFF4CAF50),
                "🔴 TRANSMITTING • LIVE",
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
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val lockThresholdPx = with(density) { 65.dp.toPx() }

    LaunchedEffect(floorStatus.state) {
        if (floorStatus.state != FloorState.TRANSMITTING && floorStatus.state != FloorState.REQUESTING) {
            isLocked = false
            dragOffsetY = 0f
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isTransmitting || isLocked) 0.95f else 1f,
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
        isLocked -> Color(0xFF00E676)
        isTransmitting -> Color(0xFF4CAF50)
        isRequesting -> Color(0xFFFFA000)
        isBusy -> Color(0xFFE53935)
        else -> Color.White.copy(alpha = 0.1f)
    }

    val buttonGradient = when {
        isLocked -> listOf(Color(0xFF00E676), Color(0xFF2E7D32))
        isTransmitting -> listOf(Color(0xFF66BB6A), Color(0xFF43A047))
        isRequesting -> listOf(Color(0xFFFFA000), Color(0xFFFF8F00))
        isBusy -> listOf(Color(0xFF263238), Color(0xFF1E2124))
        else -> listOf(Color(0xFF2C2F33), Color(0xFF1E2126))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = (isTransmitting || isRequesting) && !isLocked,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val visualDragOffset = (dragOffsetY.coerceIn(-lockThresholdPx, 0f) * 0.5f).toInt()
            Surface(
                color = Color(0xFF1E2124).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                modifier = Modifier.offset { IntOffset(0, visualDragOffset) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SWIPE UP TO LOCK",
                        color = Color.White,
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
            if (isTransmitting || isLocked) {
                Box(modifier = Modifier.size(170.dp).scale(pulse1Scale).background(Color(0xFF4CAF50).copy(alpha = pulse1Alpha), CircleShape))
                Box(modifier = Modifier.size(170.dp).scale(pulse2Scale).background(Color(0xFF4CAF50).copy(alpha = pulse2Alpha), CircleShape))
            } else if (isRequesting) {
                Box(modifier = Modifier.size(170.dp).scale(pulse1Scale).background(Color(0xFFFFA000).copy(alpha = pulse1Alpha), CircleShape))
            }

            if (isTransmitting || isLocked) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(186.dp)) {
                    drawCircle(
                        color = if (isLocked) Color(0xFF00E676) else Color(0xFF4CAF50),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
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
                        if (!isTransmitting && !isBusy && !isLocked) {
                            Modifier.graphicsLayer(alpha = breathingAlpha)
                        } else Modifier
                    )
                    .border(
                        width = if (isTransmitting || isLocked) 4.dp else 2.dp,
                        color = ringColor,
                        shape = CircleShape
                    )
                    .pointerInput(isConnected, isLocked) {
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
                        tint = if (isBusy) Color.Gray else Color.White, 
                        modifier = Modifier
                            .size(54.dp)
                            .graphicsLayer(alpha = if (isTransmitting || isLocked) 1f else 0.85f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val buttonText = when {
                        isLocked -> "LOCKED • LIVE"
                        isTransmitting -> "TRANSMITTING"
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("TAP TO STOP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                
                if (isSpeaking) {
                    WaveformSmall()
                }
            }
            
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
fun InternetSpeakingIndicator(floorStatus: FloorStatus, isOthersSpeaking: Boolean, socketUiState: SocketUiState) {
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
