package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.audio.intelligence.AcousticEnvironment
import com.example.walkietalkieapp.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin

enum class ConnectionStatus {
    OFF, SEARCHING, READY, CONNECTED
}

enum class TalkingState {
    IDLE, YOU_TALKING, OTHER_TALKING, LISTENING
}

data class DisplayDevice(
    val name: String,
    val avatar: String,
    val online: Boolean
)

@Composable
fun DisplayPanel(
    status: ConnectionStatus,
    talkingState: TalkingState,
    channelName: String,
    connectivityMode: ConnectivityMode,
    squadCode: String = "",
    onlineCount: Int = 0,
    totalMembers: Int = 0,
    tacticalUnreadCount: Int = 0,
    onDataLinkClick: (() -> Unit)? = null,
    pairedDevice: String = "",
    otherUser: String = "",
    isWheelActive: Boolean = false,
    wheelDeviceIndex: Int = 0,
    pairedDevices: List<DisplayDevice> = emptyList(),
    onCodeClick: (() -> Unit)? = null,
    isE2EActive: Boolean = false,
    e2eFingerprint: String = "",
    onE2EClick: (() -> Unit)? = null,
    acousticSplDb: Float = 38.0f,
    acousticEnvironment: AcousticEnvironment = AcousticEnvironment.QUIET,
    onRadarClick: (() -> Unit)? = null,
    isFailoverActive: Boolean = false,
    onMeshClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentTheme = ModeThemes.get(connectivityMode)
    val isTalking = talkingState == TalkingState.YOU_TALKING || talkingState == TalkingState.OTHER_TALKING
    val haptic = LocalHapticFeedback.current

    // Pulsing animation for scanning status dot
    val infiniteTransition = rememberInfiniteTransition(label = "displayInfinite")
    val scanningAlpha = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanningAlpha"
    )

    // Mic glow pulse when talking
    val micPulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseAlpha"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splDotPulse"
    )

    // Unified Cockpit LCD Box - Rigid Layout with Next-Level Spacing & Zero Shifting
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = currentTheme.primaryColor.copy(alpha = 0.5f),
                spotColor = currentTheme.primaryColor.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(currentTheme.gradient)
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp)
            .height(154.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // TIER 1: Top Status, Security & Radar Strip (24dp)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Connection Dot + Status + Mode Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val dotColor = when (status) {
                        ConnectionStatus.OFF -> StatusOff
                        ConnectionStatus.SEARCHING -> StatusSearching
                        ConnectionStatus.READY -> StatusReady
                        ConnectionStatus.CONNECTED -> StatusReady
                    }

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = if (status == ConnectionStatus.SEARCHING) scanningAlpha.value else 1f
                            }
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    val statusText = when (status) {
                        ConnectionStatus.OFF -> "OFF"
                        ConnectionStatus.SEARCHING -> "SCANNING..."
                        ConnectionStatus.READY -> "READY"
                        ConnectionStatus.CONNECTED -> "CONNECTED"
                    }

                    Text(
                        text = statusText,
                        color = Color.White.copy(alpha = 0.95f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.8.sp
                    )

                    // Tactical Hybrid Mesh & Failover Button
                    val meshInteraction = remember { MutableInteractionSource() }
                    val isMeshPressed by meshInteraction.collectIsPressedAsState()
                    val meshScale by animateFloatAsState(
                        targetValue = if (isMeshPressed) 0.88f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
                        label = "meshBtnScale"
                    )

                    val (modeIcon, modeLabel) = when {
                        isFailoverActive -> Pair(Icons.Default.Wifi, "FAILOVER")
                        connectivityMode == ConnectivityMode.INTERNET -> Pair(Icons.Default.Language, "MESH")
                        connectivityMode == ConnectivityMode.BLUETOOTH -> Pair(Icons.Default.Bluetooth, "BT")
                        connectivityMode == ConnectivityMode.WIFI_DIRECT -> Pair(Icons.Default.Wifi, "Wi-Fi")
                        else -> Pair(Icons.Default.Language, "NET")
                    }

                    val meshBorder = if (isFailoverActive) WalkieAmber.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f)
                    val meshBg = if (isFailoverActive) Color(0xFF78350F).copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.24f)

                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = meshScale
                                scaleY = meshScale
                            }
                            .clip(RoundedCornerShape(6.dp))
                            .background(meshBg)
                            .border(0.5.dp, meshBorder, RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = meshInteraction,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMeshClick?.invoke()
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.5.dp)
                                .clip(CircleShape)
                                .background(if (isFailoverActive) WalkieAmber else StatusReady)
                        )
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = modeLabel,
                            tint = if (isFailoverActive) WalkieAmber else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(9.dp)
                        )
                        Text(
                            text = modeLabel,
                            color = if (isFailoverActive) WalkieAmber else Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp
                        )
                    }
                }

                // Right: E2E Security Badge & Acoustic Radar Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (connectivityMode == ConnectivityMode.INTERNET) {
                        val e2eColor = if (isE2EActive) Color(0xFF10B981) else Color.White.copy(alpha = 0.55f)
                        val e2eBg = if (isE2EActive) Color(0xFF064E3B).copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.25f)
                        val e2eBorder = if (isE2EActive) Color(0xFF10B981).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.16f)

                        val e2eInteraction = remember { MutableInteractionSource() }
                        val isE2EPressed by e2eInteraction.collectIsPressedAsState()
                        val e2eScale by animateFloatAsState(
                            targetValue = if (isE2EPressed) 0.88f else 1f,
                            animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
                            label = "e2eBtnScale"
                        )

                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = e2eScale
                                    scaleY = e2eScale
                                }
                                .clip(RoundedCornerShape(7.dp))
                                .background(e2eBg)
                                .border(0.5.dp, e2eBorder, RoundedCornerShape(7.dp))
                                .clickable(
                                    interactionSource = e2eInteraction,
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onE2EClick?.invoke()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.5.dp)
                                    .clip(CircleShape)
                                    .background(e2eColor)
                            )
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "E2E Encrypted",
                                tint = e2eColor,
                                modifier = Modifier.size(9.5.dp)
                            )
                            Text(
                                text = if (isE2EActive) "E2E" else "E2E READY",
                                color = if (isE2EActive) Color(0xFFD1FAE5) else Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }

                    // Tactical Acoustic Radar Badge
                    val radarColor = when (acousticEnvironment) {
                        AcousticEnvironment.QUIET -> StatusReady
                        AcousticEnvironment.NORMAL -> Color(0xFF38BDF8)
                        AcousticEnvironment.NOISY -> StatusSearching
                        AcousticEnvironment.VERY_NOISY -> Color(0xFFEF4444)
                        AcousticEnvironment.UNKNOWN -> Color.White.copy(alpha = 0.6f)
                    }
                    val radarBg = Color.Black.copy(alpha = 0.28f)
                    val radarBorder = radarColor.copy(alpha = 0.45f)

                    val radarInteraction = remember { MutableInteractionSource() }
                    val isRadarPressed by radarInteraction.collectIsPressedAsState()
                    val radarScale by animateFloatAsState(
                        targetValue = if (isRadarPressed) 0.88f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
                        label = "radarBtnScale"
                    )

                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = radarScale
                                scaleY = radarScale
                            }
                            .clip(RoundedCornerShape(7.dp))
                            .background(radarBg)
                            .border(0.5.dp, radarBorder, RoundedCornerShape(7.dp))
                            .clickable(
                                interactionSource = radarInteraction,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRadarClick?.invoke()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.5.dp)
                                .clip(CircleShape)
                                .background(radarColor.copy(alpha = pulseAlpha))
                        )
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Acoustic Radar",
                            tint = radarColor,
                            modifier = Modifier.size(9.5.dp)
                        )
                        val envShort = when (acousticEnvironment) {
                            AcousticEnvironment.QUIET -> "QUIET"
                            AcousticEnvironment.NORMAL -> "NORM"
                            AcousticEnvironment.NOISY -> "NOISY"
                            AcousticEnvironment.VERY_NOISY -> "COMBAT"
                            AcousticEnvironment.UNKNOWN -> "CAL"
                        }
                        Text(
                            text = "${acousticSplDb.toInt()}dB $envShort",
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            // ==========================================
            // TIER 2: Channel Cockpit Title (30dp)
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isWheelActive && pairedDevices.isNotEmpty()) {
                    val activeDevice = pairedDevices.getOrNull(wheelDeviceIndex) ?: pairedDevices.first()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeDevice.avatar,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = activeDevice.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .size(5.5.dp)
                                .clip(CircleShape)
                                .background(if (activeDevice.online) StatusReady else StatusOff)
                        )
                    }
                } else {
                    Text(
                        text = channelName,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // ==========================================
            // TIER 3: Tactical Action & Status Chips (26dp)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                horizontalArrangement = if (squadCode.isNotBlank()) Arrangement.SpaceBetween else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (squadCode.isNotBlank()) {
                    // 1. Squad Code Chip (Tap to Copy/Share)
                    val codeInteraction = remember { MutableInteractionSource() }
                    val isCodePressed by codeInteraction.collectIsPressedAsState()
                    val codeScale by animateFloatAsState(
                        targetValue = if (isCodePressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
                        label = "codeScale"
                    )
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = codeScale
                                scaleY = codeScale
                            }
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(7.dp))
                            .clickable(
                                interactionSource = codeInteraction,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCodeClick?.invoke()
                            }
                            .padding(horizontal = 7.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = "Code",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "CODE: $squadCode",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.3.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(9.5.dp)
                        )
                    }

                    // 2. Tactical Data Link Physical Chassis Button
                    val chatInteraction = remember { MutableInteractionSource() }
                    val isChatPressed by chatInteraction.collectIsPressedAsState()
                    val chatScale by animateFloatAsState(
                        targetValue = if (isChatPressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
                        label = "chatScale"
                    )
                    val isUnread = tacticalUnreadCount > 0
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = chatScale
                                scaleY = chatScale
                            }
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isUnread) Color(0xFFB45309).copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.28f))
                            .border(
                                0.6.dp,
                                if (isUnread) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(7.dp)
                            )
                            .clickable(
                                interactionSource = chatInteraction,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDataLinkClick?.invoke()
                            }
                            .padding(horizontal = 7.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = "Data Link",
                            tint = if (isUnread) Color(0xFFFDE68A) else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = if (isUnread) "DATA LINK ($tacticalUnreadCount)" else "DATA LINK",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnread) Color(0xFFFDE68A) else Color.White,
                            letterSpacing = 0.3.sp
                        )
                    }

                    // 3. Online Members Status Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 7.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (onlineCount > 0) StatusReady else StatusOff)
                        )
                        Text(
                            text = "$onlineCount/$totalMembers ONLINE",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (onlineCount > 0) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.6f),
                            letterSpacing = 0.3.sp
                        )
                    }
                } else if (pairedDevice.isNotBlank()) {
                    // Fallback for standalone/demo usage
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(StatusReady)
                        )
                        Text(
                            text = pairedDevice,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            // ==========================================
            // TIER 4: Live Telemetry & Audio Status Footer (22dp)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isTalking || talkingState == TalkingState.LISTENING) {
                        AnimatedMicBars(modifier = Modifier.size(width = 20.dp, height = 13.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(StatusReady.copy(alpha = 0.85f))
                        )
                    }

                    val talkingLabel = when (talkingState) {
                        TalkingState.IDLE -> "Ambient: ${acousticSplDb.toInt()} dB • Ready"
                        TalkingState.YOU_TALKING -> "You're transmitting"
                        TalkingState.OTHER_TALKING -> if (otherUser.isNotEmpty()) "$otherUser transmitting" else "Incoming transmission"
                        TalkingState.LISTENING -> "Channel open • Listening..."
                    }

                    Text(
                        text = talkingLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isTalking) FontWeight.Bold else FontWeight.SemiBold,
                        color = Color.White.copy(alpha = if (isTalking) 1f else 0.88f),
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                if (isTalking) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone Active",
                        tint = Color.White,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = micPulseAlpha.value
                            }
                            .size(15.dp)
                    )
                } else {
                    Text(
                        text = "SQUELCH AUTO",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedMicBars(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "micBars")
    val phase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "micWavePhase"
    )

    androidx.compose.foundation.Canvas(modifier = modifier.size(width = 20.dp, height = 13.dp)) {
        val currentPhase = phase.value
        val barWidth = 2.5.dp.toPx()
        val gap = 1.8.dp.toPx()
        val maxHeight = size.height
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())
        val color = Color.White.copy(alpha = 0.9f)

        for (i in 0 until 5) {
            val scaleY = (0.35f + 0.65f * (sin(currentPhase + i * 0.8f) * 0.5f + 0.5f)).coerceIn(0.2f, 1f)
            val barHeight = maxHeight * scaleY
            val left = i * (barWidth + gap)
            val top = maxHeight - barHeight
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}
