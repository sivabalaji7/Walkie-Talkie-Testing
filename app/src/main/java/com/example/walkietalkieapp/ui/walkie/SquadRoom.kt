package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.example.walkietalkieapp.audio.VoiceHistoryManager
import com.example.walkietalkieapp.audio.VoiceTransmission
import com.example.walkietalkieapp.audio.intelligence.AcousticEnvironment
import com.example.walkietalkieapp.audio.intelligence.AcousticRadarManager
import com.example.walkietalkieapp.auth.RoomMemberRequest
import com.example.walkietalkieapp.chat.TacticalChatManager
import com.example.walkietalkieapp.location.SquadRadarManager
import com.example.walkietalkieapp.mesh.AdaptiveMeshManager
import com.example.walkietalkieapp.ptt.HardwarePttManager
import com.example.walkietalkieapp.socket.SupabaseRealtimeManager
import com.example.walkietalkieapp.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SquadRoom(
    squad: Squad,
    mode: ConnectivityMode,
    onExit: () -> Unit,
    talkingState: TalkingState = TalkingState.IDLE,
    onPressStart: () -> Unit = {},
    onPressEnd: () -> Unit = {},
    speakerOn: Boolean = true,
    onSpeakerToggle: () -> Unit = {},
    onQuickActions: () -> Unit = {},
    status: ConnectionStatus = ConnectionStatus.CONNECTED,
    otherUser: String = "",
    onShareSquadCode: (String) -> Unit = {},
    pendingRequests: List<RoomMemberRequest> = emptyList(),
    onApproveRequest: (RoomMemberRequest) -> Unit = {},
    onDeclineRequest: (RoomMemberRequest) -> Unit = {},
    isE2EActive: Boolean = false,
    e2eFingerprint: String = "",
    onRekeySession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentTheme = ModeThemes.get(mode)
    var memberIndex by remember { mutableIntStateOf(0) }
    var wheelActive by remember { mutableStateOf(false) }
    var showE2ESecurityModal by remember { mutableStateOf(false) }
    var showVoiceReelModal by remember { mutableStateOf(false) }
    var showAcousticRadarModal by remember { mutableStateOf(false) }
    var showTacticalChatModal by remember { mutableStateOf(false) }
    var showHardwarePttModal by remember { mutableStateOf(false) }
    var showMeshModal by remember { mutableStateOf(false) }
    var showSquadRadarModal by remember { mutableStateOf(false) }
    val isMeshFailoverActive by AdaptiveMeshManager.isFailoverActive.collectAsState()
    val squadLocations by SquadRadarManager.squadLocations.collectAsState()
    val isGpsLocked by SquadRadarManager.isGpsFixAcquired.collectAsState()
    val isHwPttEnabled by HardwarePttManager.isVolumeKeyPttEnabled.collectAsState()
    val isHwKeyDown by HardwarePttManager.isHardwareKeyDown.collectAsState()
    val tacticalMessages by TacticalChatManager.messages.collectAsState()
    val tacticalUnreadCount by TacticalChatManager.unreadCount.collectAsState()
    val transmissions by VoiceHistoryManager.transmissions.collectAsState()
    val currentlyPlayingId by VoiceHistoryManager.currentlyPlayingId.collectAsState()
    val acousticSplDb by AcousticRadarManager.currentSplDb.collectAsState()
    val acousticEnvironment by AcousticRadarManager.currentEnvironment.collectAsState()
    val acousticNoiseFloor by AcousticRadarManager.baselineNoiseFloorDb.collectAsState()
    val acousticSpectralBands by AcousticRadarManager.spectralBands.collectAsState()
    val isRadarCalibrating by AcousticRadarManager.isCalibrating.collectAsState()
    val radarCalibrationProgress by AcousticRadarManager.calibrationProgress.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Actively sample ambient room noise every 15s (10-25s user window) when idle
    DisposableEffect(Unit) {
        AcousticRadarManager.startPeriodicMonitoring(context, intervalSeconds = 15L)
        onDispose {
            AcousticRadarManager.stopPeriodicMonitoring()
        }
    }

    LaunchedEffect(showTacticalChatModal) {
        TacticalChatManager.isChatDialogVisible = showTacticalChatModal
        if (showTacticalChatModal) {
            TacticalChatManager.markAllRead()
        }
    }

    val rosterListState = rememberLazyListState()

    // Smoothly scroll the horizontal squad roster when memberIndex changes
    LaunchedEffect(memberIndex) {
        if (squad.members.isNotEmpty() && memberIndex in squad.members.indices) {
            rosterListState.animateScrollToItem(memberIndex)
        }
    }

    LaunchedEffect(wheelActive) {
        if (wheelActive) {
            delay(2500)
            wheelActive = false
        }
    }

    // Keep Mesh Topology Nodes synchronized with real-time squad roster
    LaunchedEffect(squad.members) {
        AdaptiveMeshManager.syncSquadNodes(
            members = squad.members.map { it.name },
            selfName = otherUser.ifBlank { "OPERATOR" }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Connected Tactical Chassis - Fitted to Screen & Substantial
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(WalkieDeviceBody)
                .border(1.dp, WalkieCardBorder, RoundedCornerShape(30.dp))
                .padding(bottom = 6.dp)
        ) {
            // 1. Top Chassis Grip Notch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(WalkieDeviceBodyLight)
                )
            }

            // 2. Tactical Display LCD Panel - Unified Cockpit Box
            val totalCount = squad.members.size
            val onlineCount = squad.members.count { it.online }

            DisplayPanel(
                status = status,
                talkingState = talkingState,
                channelName = squad.name,
                connectivityMode = mode,
                squadCode = squad.id,
                onlineCount = onlineCount,
                totalMembers = totalCount,
                tacticalUnreadCount = tacticalUnreadCount,
                onCodeClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShareSquadCode(squad.id)
                },
                onDataLinkClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    TacticalChatManager.markAllRead()
                    showTacticalChatModal = true
                },
                otherUser = otherUser.ifBlank { squad.members.firstOrNull { it.online && it.name != squad.name }?.name ?: "" },
                isWheelActive = wheelActive,
                wheelDeviceIndex = memberIndex,
                pairedDevices = squad.members.map { DisplayDevice(it.name, it.avatar, it.online) },
                isE2EActive = isE2EActive,
                e2eFingerprint = e2eFingerprint,
                onE2EClick = { showE2ESecurityModal = true },
                acousticSplDb = acousticSplDb,
                acousticEnvironment = acousticEnvironment,
                onRadarClick = { showAcousticRadarModal = true },
                isFailoverActive = isMeshFailoverActive,
                onMeshClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMeshModal = true
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Tactical Squad Members Horizontal Roster
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WalkieDeviceBodyLight.copy(alpha = 0.35f))
                    .border(1.dp, WalkieCardBorder.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            ) {
                if (squad.members.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "No members recorded for this squad",
                            fontFamily = PlusJakartaSans,
                            fontSize = 11.sp,
                            color = WalkieTextMuted
                        )
                    }
                } else {
                    LazyRow(
                        state = rosterListState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(squad.members, key = { it.name }) { member ->
                            val isCurrentSpeaker = member.name.equals(otherUser, ignoreCase = true) ||
                                (member.online && member.isSpeaking)
                            val isSelected = squad.members.indexOf(member) == memberIndex && wheelActive
                            TacticalMemberBadge(
                                member = member,
                                isCurrentSpeaker = isCurrentSpeaker,
                                isSelected = isSelected,
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    memberIndex = squad.members.indexOf(member)
                                    wheelActive = true
                                }
                            )
                        }
                    }
                }
            }

            // 5. Pending Join Requests Panel (If Owner and Has Requests)
            if (pendingRequests.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WalkieAmber.copy(alpha = 0.12f))
                        .border(1.dp, WalkieAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${pendingRequests.size} PENDING JOIN REQUEST(S)",
                        fontFamily = SpaceGrotesk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieAmber,
                        letterSpacing = 0.5.sp
                    )

                    pendingRequests.take(2).forEach { req ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = req.username,
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextPrimary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onApproveRequest(req)
                                    },
                                    shape = CircleShape,
                                    color = StatusReady.copy(alpha = 0.25f),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Approve",
                                            tint = StatusReady,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDeclineRequest(req)
                                    },
                                    shape = CircleShape,
                                    color = StatusOff.copy(alpha = 0.25f),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Decline",
                                            tint = StatusOff,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Action Buttons (Exit, Channel, Members, Speaker, Replay Reel)
            ActionButtons(
                isPowered = true,
                onPowerToggle = onExit,
                onCreateChannel = { onShareSquadCode(squad.id) },
                onPairedDevices = { wheelActive = true },
                onSpeaker = onSpeakerToggle,
                onQuickActions = {
                    showVoiceReelModal = true
                },
                onRadarClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSquadRadarModal = true
                },
                radarTargetCount = squadLocations.size,
                isGpsActive = isGpsLocked,
                speakerOn = speakerOn,
                inSquad = true,
                mode = mode,
                replayCount = transmissions.size
            )

            // 7. Push To Talk Button
            PushToTalk(
                isTalking = talkingState == TalkingState.YOU_TALKING,
                onPressStart = onPressStart,
                onPressEnd = onPressEnd,
                disabled = false,
                mode = mode,
                onHardwarePttClick = { showHardwarePttModal = true },
                isHardwarePttEnabled = isHwPttEnabled,
                isHardwareKeyDown = isHwKeyDown
            )
        }

        // 8. Right-Edge DeviceWheel (Hardware dial on side)
        DeviceWheel(
            onScroll = { dir ->
                wheelActive = true
                memberIndex = (memberIndex + dir).coerceIn(0, (squad.members.size - 1).coerceAtLeast(0))
            },
            isActive = wheelActive,
            deviceCount = squad.members.size,
            currentIndex = memberIndex,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 18.dp)
        )

        // 9. Interactive E2E Security Modal
        if (showE2ESecurityModal) {
            E2ESecurityDialog(
                isE2EActive = isE2EActive,
                fingerprint = e2eFingerprint,
                onRekey = onRekeySession,
                onDismiss = { showE2ESecurityModal = false }
            )
        }

        // 10. Interactive Voice Reel (Tactical Blackbox Replay Modal)
        if (showVoiceReelModal) {
            VoiceReelDialog(
                transmissions = transmissions,
                currentlyPlayingId = currentlyPlayingId,
                mode = mode,
                onPlayTransmission = { id ->
                    if (currentlyPlayingId == id) {
                        VoiceHistoryManager.stopPlayback()
                    } else {
                        VoiceHistoryManager.playTransmission(id)
                    }
                },
                onPlayLatest = {
                    if (currentlyPlayingId != null) {
                        VoiceHistoryManager.stopPlayback()
                    } else {
                        VoiceHistoryManager.playLatest()
                    }
                },
                onClear = {
                    VoiceHistoryManager.clearHistory()
                },
                onDismiss = {
                    VoiceHistoryManager.stopPlayback()
                    showVoiceReelModal = false
                }
            )
        }

        // 11. Interactive Tactical Acoustic Radar Modal
        if (showAcousticRadarModal) {
            AcousticRadarDialog(
                currentSplDb = acousticSplDb,
                currentEnvironment = acousticEnvironment,
                baselineNoiseFloorDb = acousticNoiseFloor,
                spectralBands = acousticSpectralBands,
                isCalibrating = isRadarCalibrating,
                calibrationProgress = radarCalibrationProgress,
                onCalibrate = {
                    AcousticRadarManager.startAutoCalibration(context)
                },
                onDismiss = {
                    showAcousticRadarModal = false
                },
                mode = mode
            )
        }

        // 12. Interactive Tactical Micro-Chat & GPS Beacon Modal
        if (showTacticalChatModal) {
            val myUsername = SupabaseRealtimeManager.socketUiState.collectAsState().value.username.ifBlank { otherUser }.ifBlank { "OPERATOR" }
            TacticalChatDialog(
                messages = tacticalMessages,
                senderName = myUsername,
                mode = mode,
                onSendMessage = { text ->
                    TacticalChatManager.sendTextMessage(myUsername, text)
                },
                onSendGpsBeacon = {
                    TacticalChatManager.sendGpsBeacon(context, myUsername, label = "${squad.name} BEACON")
                },
                onDismiss = {
                    showTacticalChatModal = false
                }
            )
        }

        // 13. Interactive Hardware PTT & Hands-Free Controller Modal
        if (showHardwarePttModal) {
            HardwarePttDialog(
                mode = mode,
                onDismiss = { showHardwarePttModal = false }
            )
        }

        // 14. Interactive Hybrid Mesh Topology Radar & Fallback Modal
        if (showMeshModal) {
            MeshTopologyDialog(
                onDismiss = {
                    showMeshModal = false
                }
            )
        }

        // 15. Interactive Tactical Squad GPS Radar Modal
        if (showSquadRadarModal) {
            val myUsername = SupabaseRealtimeManager.socketUiState.collectAsState().value.username.ifBlank { otherUser }.ifBlank { "OPERATOR" }
            SquadRadarDialog(
                squadName = squad.name,
                currentUsername = myUsername,
                onDismiss = { showSquadRadarModal = false }
            )
        }
    }
}

@Composable
private fun SpeakingAura() {
    val infiniteTransition = rememberInfiniteTransition(label = "haloTransition")
    val haloPulse = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = 0.35f * haloPulse.value
            }
            .size(38.dp)
            .clip(CircleShape)
            .background(WalkieAmber)
    )
}

@Composable
fun TacticalMemberBadge(
    member: SquadMember,
    isCurrentSpeaker: Boolean,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(58.dp)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            // Speaking Halo when transmitting
            if (isCurrentSpeaker) {
                SpeakingAura()
            } else if (isSelected) {
                // Tactical Selection Ring when dialed by scroller
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, WalkieAmber, CircleShape)
                )
            }

            // Avatar Circle
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrentSpeaker) WalkieAmber
                        else if (isSelected) WalkieAmber.copy(alpha = 0.25f)
                        else if (member.online) WalkieButton
                        else WalkieDeviceBody
                    )
                    .border(
                        width = if (isCurrentSpeaker || isSelected) 2.dp else 1.dp,
                        color = if (isCurrentSpeaker || isSelected) WalkieAmber
                        else if (member.online) WalkieAmber.copy(alpha = 0.6f)
                        else WalkieCardBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.avatar.ifBlank { member.name.take(1).uppercase() },
                    fontFamily = SpaceGrotesk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentSpeaker) Color.Black
                    else if (isSelected) WalkieAmber
                    else if (member.online) WalkieTextPrimary
                    else WalkieTextMuted
                )
            }

            // Owner Crown (👑) badge at Top-End
            if (member.isOwner) {
                Text(
                    text = "👑",
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-3).dp)
                )
            }

            // Online / Offline Status Dot at Bottom-End
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .clip(CircleShape)
                    .background(if (member.online) StatusReady else StatusOff)
                    .border(1.dp, WalkieDeviceBody, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Username
        Text(
            text = member.name,
            fontFamily = PlusJakartaSans,
            fontSize = 10.sp,
            fontWeight = if (isCurrentSpeaker || isSelected || member.online) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrentSpeaker || isSelected) WalkieAmber
            else if (member.online) WalkieTextPrimary
            else WalkieTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun E2ESecurityDialog(
    isE2EActive: Boolean,
    fingerprint: String,
    onRekey: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(WalkieCard)
                .border(1.dp, if (isE2EActive) Color(0xFF10B981).copy(alpha = 0.5f) else WalkieCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Lock Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isE2EActive) Color(0xFF10B981) else WalkieAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ZERO-KNOWLEDGE E2EE",
                        fontFamily = SpaceGrotesk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieTextPrimary,
                        letterSpacing = 1.sp
                    )
                }

                // Security Status Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isE2EActive) Color(0xFF064E3B).copy(alpha = 0.5f) else WalkieAmber.copy(alpha = 0.15f))
                        .border(0.5.dp, if (isE2EActive) Color(0xFF10B981).copy(alpha = 0.6f) else WalkieAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isE2EActive) Color(0xFF10B981) else WalkieAmber)
                    )
                    Text(
                        text = if (isE2EActive) "PEER KEYS SECURED & ACTIVE" else "AWAITING SQUAD PEERS",
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isE2EActive) Color(0xFFD1FAE5) else WalkieAmberLight
                    )
                }

                // Cryptographic Specs Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WalkieDeviceBody)
                        .border(1.dp, WalkieCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SESSION KEY FINGERPRINT (SHA-256)",
                        fontFamily = SpaceGrotesk,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieTextMuted,
                        letterSpacing = 0.8.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (fingerprint.isNotBlank()) fingerprint else "INITIALIZING...",
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399),
                            letterSpacing = 0.8.sp
                        )

                        Surface(
                            onClick = {
                                if (fingerprint.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(fingerprint))
                                    copied = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (copied) Color(0xFF10B981).copy(alpha = 0.2f) else WalkieButton,
                            border = BorderStroke(1.dp, if (copied) Color(0xFF10B981) else WalkieCardBorder)
                        ) {
                            Text(
                                text = if (copied) "COPIED" else "COPY",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (copied) Color(0xFF10B981) else WalkieTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "• Cipher: AES-256-GCM (128-bit Tag)\n• Key Agreement: NIST P-256 (ECDH)\n• KDF: SHA-256 Key Derivation\n• Server: Zero-Knowledge Relay",
                        fontFamily = SpaceGrotesk,
                        fontSize = 10.sp,
                        color = WalkieTextSecondary,
                        lineHeight = 15.sp
                    )
                }

                // Interactive Re-Key Action Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRekey()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = WalkieButton,
                    border = BorderStroke(1.dp, WalkieAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-Key",
                            tint = WalkieAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FORCE RE-KEY SESSION",
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieAmber,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Close Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = WalkieDeviceBody,
                    border = BorderStroke(1.dp, WalkieCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DISMISS",
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceReelDialog(
    transmissions: List<VoiceTransmission>,
    currentlyPlayingId: String?,
    mode: ConnectivityMode,
    onPlayTransmission: (String) -> Unit,
    onPlayLatest: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentTheme = ModeThemes.get(mode)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(26.dp),
            color = WalkieCard,
            border = BorderStroke(1.2.dp, WalkieCardBorder),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(currentTheme.primaryColor.copy(alpha = 0.15f))
                                .border(1.dp, currentTheme.primaryColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Audio Reel",
                                tint = currentTheme.primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "TACTICAL AUDIO REEL",
                                fontFamily = SpaceGrotesk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextPrimary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "BLACKBOX ROLLING BUFFER • 20 SLOTS",
                                fontFamily = SpaceGrotesk,
                                fontSize = 9.sp,
                                color = WalkieTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Count Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (transmissions.isNotEmpty()) StatusReady.copy(alpha = 0.15f) else WalkieButton,
                        border = BorderStroke(1.dp, if (transmissions.isNotEmpty()) StatusReady.copy(alpha = 0.5f) else WalkieCardBorder)
                    ) {
                        Text(
                            text = if (transmissions.isNotEmpty()) "${transmissions.size} SAVED" else "EMPTY",
                            fontFamily = SpaceGrotesk,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (transmissions.isNotEmpty()) StatusReady else WalkieTextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Quick Play / Stop Action Button
                if (transmissions.isNotEmpty()) {
                    val isAnyPlaying = currentlyPlayingId != null
                    val quickBtnScale by animateFloatAsState(
                        targetValue = if (isAnyPlaying) 1.02f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 550f),
                        label = "quickReplayScale"
                    )

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayLatest()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isAnyPlaying) WalkieAmber.copy(alpha = 0.18f) else WalkieButton,
                        border = BorderStroke(1.2.dp, if (isAnyPlaying) WalkieAmber else currentTheme.primaryColor.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = quickBtnScale
                                scaleY = quickBtnScale
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp, horizontal = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isAnyPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isAnyPlaying) "Stop" else "Quick Replay",
                                tint = if (isAnyPlaying) WalkieAmber else currentTheme.primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAnyPlaying) "STOP ACTIVE PLAYBACK" else "⚡ QUICK REPLAY LAST TRANSMISSION",
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAnyPlaying) WalkieAmber else currentTheme.primaryColor,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }

                // Main Transmissions List or Empty State
                if (transmissions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(WalkieDeviceBody)
                            .border(1.dp, WalkieCardBorder, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MicNone,
                                contentDescription = "Empty",
                                tint = WalkieTextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "NO RECORDED TRANSMISSIONS",
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextSecondary
                            )
                            Text(
                                text = "Squad voice audio sent or received will automatically buffer here for instant replay.",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                color = WalkieTextMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transmissions, key = { it.id }) { item ->
                            val isItemPlaying = currentlyPlayingId == item.id
                            ReelItemCard(
                                item = item,
                                isPlaying = isItemPlaying,
                                themeColor = currentTheme.primaryColor,
                                onTogglePlay = { onPlayTransmission(item.id) }
                            )
                        }
                    }
                }

                // Footer Row: Clear & Dismiss Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (transmissions.isNotEmpty()) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClear()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = WalkieButton,
                            border = BorderStroke(1.dp, StatusOff.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear",
                                        tint = StatusOff,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "CLEAR REEL",
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusOff
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = WalkieDeviceBody,
                        border = BorderStroke(1.dp, WalkieCardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "DISMISS",
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReelItemCard(
    item: VoiceTransmission,
    isPlaying: Boolean,
    themeColor: Color,
    onTogglePlay: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val timeFormatted = remember(item.timestamp) {
        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(item.timestamp))
    }
    val durationFormatted = remember(item.durationMs) {
        String.format(Locale.US, "%.1fs", item.durationMs / 1000f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPlaying) themeColor.copy(alpha = 0.12f) else WalkieButton,
        border = BorderStroke(1.dp, if (isPlaying) themeColor else WalkieCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Avatar + Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (item.isSelf) themeColor.copy(alpha = 0.2f) else WalkieDeviceBodyLight)
                        .border(1.dp, if (item.isSelf) themeColor else WalkieCardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.speakerName.take(1).uppercase(),
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isSelf) themeColor else WalkieTextPrimary
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.speakerName,
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.isSelf) {
                            Text(
                                text = "(You)",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                color = WalkieTextSecondary
                            )
                        }
                    }
                    Text(
                        text = timeFormatted,
                        fontFamily = SpaceGrotesk,
                        fontSize = 9.sp,
                        color = WalkieTextMuted
                    )
                }
            }

            // Right: Duration + Waveform + Play/Stop Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Duration Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = WalkieDeviceBody,
                    border = BorderStroke(1.dp, WalkieCardBorder)
                ) {
                    Text(
                        text = durationFormatted,
                        fontFamily = SpaceGrotesk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieTextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Mini Waveform Equalizer
                MiniWaveformBars(isPlaying = isPlaying, activeColor = themeColor)

                // Play / Stop Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePlay()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPlaying) WalkieAmber.copy(alpha = 0.2f) else themeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isPlaying) WalkieAmber else themeColor.copy(alpha = 0.5f)),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            tint = if (isPlaying) WalkieAmber else themeColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniWaveformBars(
    isPlaying: Boolean,
    activeColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = if (isPlaying) 16f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(260, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = if (isPlaying) 20f else 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = if (isPlaying) 18f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w3"
    )

    Row(
        modifier = Modifier.width(20.dp).height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barColor = if (isPlaying) activeColor else WalkieTextMuted.copy(alpha = 0.4f)
        Box(modifier = Modifier.width(3.dp).height(h1.dp).clip(RoundedCornerShape(1.dp)).background(barColor))
        Box(modifier = Modifier.width(3.dp).height(h2.dp).clip(RoundedCornerShape(1.dp)).background(barColor))
        Box(modifier = Modifier.width(3.dp).height(h3.dp).clip(RoundedCornerShape(1.dp)).background(barColor))
    }
}
