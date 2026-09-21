package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.walkietalkieapp.ui.theme.*
import com.example.walkietalkieapp.vox.VoxManager
import com.example.walkietalkieapp.vox.VoxSensitivity
import com.example.walkietalkieapp.vox.VoxState

/**
 * Tactical VOX (Voice-Operated Exchange) Hands-Free Controller Modal Dialog.
 * Provides master enable toggle, live VU meter calibrator with threshold line,
 * sensitivity presets, and hangover hold time configuration.
 */
@Composable
fun VoxSettingsDialog(
    mode: ConnectivityMode,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentTheme = ModeThemes.get(mode)

    val isVoxEnabled by VoxManager.isVoxEnabled.collectAsState()
    val sensitivity by VoxManager.sensitivity.collectAsState()
    val hangoverDelayMs by VoxManager.hangoverDelayMs.collectAsState()
    val voxState by VoxManager.voxState.collectAsState()
    val liveLevel by VoxManager.liveInputLevel.collectAsState()

    // Smooth live audio level animation
    val animatedLevel by animateFloatAsState(
        targetValue = liveLevel,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 600f),
        label = "voxLiveLevel"
    )

    // Pulse animation for active transmission or armed status
    val pulseTransition = rememberInfiniteTransition(label = "voxPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voxPulseAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(26.dp),
            color = WalkieCard,
            border = BorderStroke(1.2.dp, WalkieCardBorder),
            shadowElevation = 28.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                // ==================== HEADER ====================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(currentTheme.primaryColor.copy(alpha = 0.18f))
                                .border(1.2.dp, currentTheme.primaryColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "VOX Control",
                                tint = currentTheme.primaryColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "VOX HANDS-FREE AUTO-PTT",
                                fontFamily = SpaceGrotesk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextPrimary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "VOICE OPERATED EXCHANGE // AUTO-KEY",
                                fontFamily = SpaceGrotesk,
                                fontSize = 9.sp,
                                color = WalkieTextMuted,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = WalkieCardBorder.copy(alpha = 0.3f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = WalkieTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // ==================== MASTER TOGGLE CARD ====================
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WalkieSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isVoxEnabled) currentTheme.primaryColor.copy(alpha = 0.4f) else WalkieCardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (voxState) {
                                                VoxState.TRANSMITTING -> StatusReady.copy(alpha = pulseAlpha)
                                                VoxState.ARMED -> WalkieAmber.copy(alpha = pulseAlpha)
                                                VoxState.INHIBITED_BUSY -> WalkieAmber
                                                VoxState.MANUAL_OVERRIDE -> currentTheme.primaryColor
                                                VoxState.OFF -> StatusOff
                                            }
                                        )
                                )
                                Text(
                                    text = "VOX AUTO-KEY ENGINE",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalkieTextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (voxState) {
                                    VoxState.TRANSMITTING -> "TRANSMITTING LIVE (HANDS-FREE)"
                                    VoxState.ARMED -> "ARMED • MONITORING MIC FOR SPEECH"
                                    VoxState.INHIBITED_BUSY -> "INHIBITED (REMOTE VOICE ACTIVE)"
                                    VoxState.MANUAL_OVERRIDE -> "MANUAL OVERRIDE ACTIVE"
                                    VoxState.OFF -> "Disabled • Hold PTT or Volume Key to talk"
                                },
                                fontFamily = SpaceGrotesk,
                                fontSize = 9.sp,
                                color = when (voxState) {
                                    VoxState.TRANSMITTING -> StatusReady
                                    VoxState.ARMED -> WalkieAmber
                                    VoxState.INHIBITED_BUSY -> WalkieAmber
                                    else -> WalkieTextMuted
                                }
                            )
                        }

                        Switch(
                            checked = isVoxEnabled,
                            onCheckedChange = { checked ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                VoxManager.setVoxEnabled(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = currentTheme.primaryColor,
                                uncheckedThumbColor = WalkieTextMuted,
                                uncheckedTrackColor = WalkieButton
                            )
                        )
                    }
                }

                // ==================== LIVE VU METER & THRESHOLD CALIBRATOR ====================
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WalkieSurface,
                    border = BorderStroke(1.dp, WalkieCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "VU Meter",
                                    tint = WalkieTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "MIC AUDIO LEVEL & THRESHOLD",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WalkieTextSecondary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            val isOverThreshold = animatedLevel >= sensitivity.threshold
                            Text(
                                text = if (isOverThreshold) "● TRIGGER DETECTED" else "IDLE / AMBIENT",
                                fontFamily = SpaceGrotesk,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOverThreshold) StatusReady else WalkieTextMuted
                            )
                        }

                        // Horizontal Level Bar Canvas with Threshold Line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0D1217))
                                .border(1.dp, WalkieCardBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val normalizedInput = (animatedLevel * 5f).coerceIn(0f, 1f) // Scaled for visible speech dynamics
                                val thresholdX = (sensitivity.threshold * 5f).coerceIn(0.05f, 0.95f) * width

                                // 1. Background segmented grid ticks
                                val tickCount = 20
                                val tickSpacing = width / tickCount
                                for (i in 1 until tickCount) {
                                    drawLine(
                                        color = Color(0x1AFFFFFF),
                                        start = Offset(i * tickSpacing, 0f),
                                        end = Offset(i * tickSpacing, height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                // 2. Live Audio Energy Fill Bar
                                val fillWidth = normalizedInput * width
                                if (fillWidth > 0f) {
                                    val isTriggered = animatedLevel >= sensitivity.threshold
                                    val barBrush = Brush.horizontalGradient(
                                        colors = if (isTriggered) {
                                            listOf(StatusReady.copy(alpha = 0.7f), StatusReady)
                                        } else {
                                            listOf(currentTheme.primaryColor.copy(alpha = 0.6f), WalkieAmber)
                                        }
                                    )
                                    drawRoundRect(
                                        brush = barBrush,
                                        topLeft = Offset(0f, 0f),
                                        size = Size(fillWidth, height),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                }

                                // 3. Target Threshold Marker Line
                                drawLine(
                                    color = Color.White,
                                    start = Offset(thresholdX, 0f),
                                    end = Offset(thresholdX, height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Speak to test — level must cross white threshold",
                                fontFamily = SpaceGrotesk,
                                fontSize = 8.5.sp,
                                color = WalkieTextMuted
                            )
                            Text(
                                text = "THRESHOLD: ${(sensitivity.threshold * 100).toInt()}%",
                                fontFamily = SpaceGrotesk,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextSecondary
                            )
                        }
                    }
                }

                // ==================== SENSITIVITY PRESETS ====================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SENSITIVITY PRESET",
                        fontFamily = SpaceGrotesk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WalkieTextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    VoxSensitivity.entries.forEach { level ->
                        val isSelected = sensitivity == level
                        val cardBorderColor by animateColorAsState(
                            targetValue = if (isSelected) currentTheme.primaryColor else WalkieCardBorder,
                            label = "voxBorderAnim"
                        )

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                VoxManager.setSensitivity(level)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) currentTheme.primaryColor.copy(alpha = 0.12f) else WalkieSurface,
                            border = BorderStroke(1.dp, cardBorderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = level.displayLabel,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) currentTheme.primaryColor else WalkieTextPrimary
                                    )
                                    Text(
                                        text = level.description,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 8.5.sp,
                                        color = WalkieTextMuted
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(
                                            1.2.dp,
                                            if (isSelected) currentTheme.primaryColor else WalkieTextMuted,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(currentTheme.primaryColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== HANGOVER DELAY SELECTOR ====================
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SILENCE HANGOVER DELAY",
                            fontFamily = SpaceGrotesk,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${hangoverDelayMs}ms",
                            fontFamily = SpaceGrotesk,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.primaryColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            400L to "400ms\nFast",
                            700L to "700ms\nStandard",
                            1200L to "1200ms\nExtended"
                        ).forEach { (delay, label) ->
                            val isSelected = hangoverDelayMs == delay
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    VoxManager.setHangoverDelayMs(delay)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) currentTheme.primaryColor.copy(alpha = 0.16f) else WalkieSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) currentTheme.primaryColor else WalkieCardBorder
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) currentTheme.primaryColor else WalkieTextSecondary,
                                        lineHeight = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== RX INTERLOCK BADGE ====================
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x0E00D9A5),
                    border = BorderStroke(1.dp, StatusReady.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Interlock",
                            tint = StatusReady,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "RX Interlock: Auto-keying is suppressed while incoming squad audio plays to eliminate acoustic echo feedback loops.",
                            fontFamily = SpaceGrotesk,
                            fontSize = 8.5.sp,
                            color = WalkieTextSecondary,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}
