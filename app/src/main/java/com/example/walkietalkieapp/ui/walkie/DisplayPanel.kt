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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
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
    modifier: Modifier = Modifier
) {
    val currentTheme = ModeThemes.get(connectivityMode)
    val isTalking = talkingState == TalkingState.YOU_TALKING || talkingState == TalkingState.OTHER_TALKING

    // Pulsing animation for scanning status dot (isolated to draw phase)
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

    // Mic glow pulse when talking (isolated to draw phase)
    val micPulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = currentTheme.primaryColor,
                spotColor = currentTheme.primaryColor
            )
            .clip(RoundedCornerShape(18.dp))
            .background(currentTheme.gradient)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(126.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Status Dot + Label & Connectivity Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Indicator
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
                            .size(8.dp)
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
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }

                // Top Right: E2E Security Badge & Mode Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (connectivityMode == ConnectivityMode.INTERNET) {
                        val e2eColor = if (isE2EActive) Color(0xFF10B981) else Color.White.copy(alpha = 0.5f)
                        val e2eBg = if (isE2EActive) Color(0xFF064E3B).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.25f)
                        val e2eBorder = if (isE2EActive) Color(0xFF10B981).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)

                        val e2eInteraction = remember { MutableInteractionSource() }
                        val isE2EPressed by e2eInteraction.collectIsPressedAsState()
                        val e2eScale by animateFloatAsState(
                            targetValue = if (isE2EPressed) 0.88f else 1f,
                            animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
                            label = "e2eBtnScale"
                        )
                        val haptic = LocalHapticFeedback.current

                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = e2eScale
                                    scaleY = e2eScale
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(e2eBg)
                                .border(0.5.dp, e2eBorder, RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = e2eInteraction,
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onE2EClick?.invoke()
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(e2eColor)
                            )
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "E2E Encrypted",
                                tint = e2eColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = if (isE2EActive) "E2E" else "E2E READY",
                                color = if (isE2EActive) Color(0xFFD1FAE5) else Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Tactical Acoustic Radar Badge Button
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
                    val infiniteTransition = rememberInfiniteTransition(label = "splPulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "splDotPulse"
                    )
                    val haptic = LocalHapticFeedback.current

                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = radarScale
                                scaleY = radarScale
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(radarBg)
                            .border(0.6.dp, radarBorder, RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = radarInteraction,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRadarClick?.invoke()
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(radarColor.copy(alpha = pulseAlpha))
                        )
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Acoustic Radar",
                            tint = radarColor,
                            modifier = Modifier.size(10.dp)
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
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.4.sp
                        )
                    }

                    // Mode Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val (modeIcon, modeLabel) = when (connectivityMode) {
                            ConnectivityMode.INTERNET -> Pair(Icons.Default.Language, "NET")
                            ConnectivityMode.BLUETOOTH -> Pair(Icons.Default.Bluetooth, "BT")
                            ConnectivityMode.WIFI_DIRECT -> Pair(Icons.Default.Wifi, "Wi-Fi")
                        }

                        Icon(
                            imageVector = modeIcon,
                            contentDescription = modeLabel,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = modeLabel,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Center: Channel / Squad Title OR Device Carousel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isWheelActive,
                    transitionSpec = {
                        (slideInVertically { it / 3 } + fadeIn())
                            .togetherWith(slideOutVertically { -it / 3 } + fadeOut())
                    },
                    label = "centerContent"
                ) { wheelActive ->
                    if (wheelActive && pairedDevices.isNotEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SELECT DEVICE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.6f),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val activeDevice = pairedDevices.getOrNull(wheelDeviceIndex) ?: pairedDevices.first()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = activeDevice.avatar,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = activeDevice.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (activeDevice.online) StatusReady else StatusOff)
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = channelName,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            if (status != ConnectionStatus.OFF && pairedDevice.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.clickable(enabled = onCodeClick != null) {
                                        onCodeClick?.invoke()
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (pairedDevices.any { it.online }) StatusReady else StatusOff)
                                    )
                                    Text(
                                        text = pairedDevice,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.9f),
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Row: Audio Equalizer Mic Bars & Speaking Status Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isTalking) {
                        AnimatedMicBars()
                    }

                    val talkingLabel = when (talkingState) {
                        TalkingState.IDLE -> "Ambient: ${acousticSplDb.toInt()} dB • Ready"
                        TalkingState.YOU_TALKING -> "You're talking"
                        TalkingState.OTHER_TALKING -> if (otherUser.isNotEmpty()) "$otherUser is talking" else "Someone is talking"
                        TalkingState.LISTENING -> "Listening..."
                    }

                    Text(
                        text = talkingLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f)
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
                            .size(16.dp)
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

    androidx.compose.foundation.Canvas(modifier = modifier.size(width = 23.dp, height = 16.dp)) {
        val currentPhase = phase.value
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val maxHeight = size.height
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        val color = Color.White.copy(alpha = 0.85f)

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
