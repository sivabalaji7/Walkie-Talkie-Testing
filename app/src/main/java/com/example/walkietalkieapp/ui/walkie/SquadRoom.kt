package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walkietalkieapp.auth.RoomMemberRequest
import com.example.walkietalkieapp.ui.theme.*
import kotlinx.coroutines.delay

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
    var memberIndex by remember { mutableIntStateOf(0) }
    var wheelActive by remember { mutableStateOf(false) }
    var showE2ESecurityModal by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(wheelActive) {
        if (wheelActive) {
            delay(2500)
            wheelActive = false
        }
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

            // 2. Display LCD Panel (124dp fixed height, rich color and styling)
            val totalCount = squad.members.size
            val onlineCount = squad.members.count { it.online }
            val memberCountText = "$totalCount Members • $onlineCount Online"

            DisplayPanel(
                status = status,
                talkingState = talkingState,
                channelName = squad.name,
                connectivityMode = mode,
                pairedDevice = memberCountText,
                otherUser = otherUser.ifBlank { squad.members.firstOrNull { it.online && it.name != squad.name }?.name ?: "" },
                isWheelActive = wheelActive,
                wheelDeviceIndex = memberIndex,
                pairedDevices = squad.members.map { DisplayDevice(it.name, it.avatar, it.online) },
                onCodeClick = { onShareSquadCode(squad.id) },
                isE2EActive = isE2EActive,
                e2eFingerprint = e2eFingerprint,
                onE2EClick = { showE2ESecurityModal = true }
            )

            // 3. Copyable Squad Code Chip & Online Status Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copyable Squad Code Chip (Tap to share/copy)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShareSquadCode(squad.id)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = WalkieButton,
                    border = BorderStroke(1.dp, WalkieCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = "Code",
                            tint = WalkieAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "CODE: ${squad.id}",
                            fontFamily = SpaceGrotesk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = WalkieTextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                // Member Count Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (onlineCount > 0) StatusReady else StatusOff)
                    )
                    Text(
                        text = "$onlineCount/$totalCount ONLINE",
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (onlineCount > 0) StatusReady else WalkieTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(squad.members, key = { it.name }) { member ->
                            val isCurrentSpeaker = member.name.equals(otherUser, ignoreCase = true) ||
                                (member.online && member.isSpeaking)
                            TacticalMemberBadge(
                                member = member,
                                isCurrentSpeaker = isCurrentSpeaker,
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

            // 6. Action Buttons (Exit, Channel, Members, Speaker, Quick)
            ActionButtons(
                isPowered = true,
                onPowerToggle = onExit,
                onCreateChannel = { onShareSquadCode(squad.id) },
                onPairedDevices = { wheelActive = true },
                onSpeaker = onSpeakerToggle,
                onQuickActions = onQuickActions,
                speakerOn = speakerOn,
                inSquad = true,
                mode = mode
            )

            // 7. Push To Talk Button
            PushToTalk(
                isTalking = talkingState == TalkingState.YOU_TALKING,
                onPressStart = onPressStart,
                onPressEnd = onPressEnd,
                disabled = false,
                mode = mode
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
            }

            // Avatar Circle
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrentSpeaker) WalkieAmber
                        else if (member.online) WalkieButton
                        else WalkieDeviceBody
                    )
                    .border(
                        width = if (isCurrentSpeaker) 2.dp else 1.dp,
                        color = if (isCurrentSpeaker) WalkieAmber
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
            fontWeight = if (isCurrentSpeaker || member.online) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrentSpeaker) WalkieAmber
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
