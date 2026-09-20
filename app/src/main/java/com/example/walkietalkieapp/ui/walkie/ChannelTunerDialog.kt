package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.walkietalkieapp.channel.SquadChannelManager
import com.example.walkietalkieapp.channel.TacticalChannel
import com.example.walkietalkieapp.ui.theme.*
import kotlin.math.*

@Composable
fun ChannelTunerDialog(
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val activeChannel by SquadChannelManager.activeChannel.collectAsState()
    val channelList by SquadChannelManager.channelList.collectAsState()
    val isEmergencyActive by SquadChannelManager.isEmergencyOverrideActive.collectAsState()
    val channelOccupancy by SquadChannelManager.channelOccupancy.collectAsState()

    // Smooth rotational angle for the rotary dial knob
    var knobRotation by remember { mutableFloatStateOf(0f) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // Pulsing emergency strobe
    val infiniteTransition = rememberInfiniteTransition(label = "emergencyStrobe")
    val emergencyPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emergencyPulse"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .border(
                    1.5.dp,
                    if (isEmergencyActive) WalkieRed else WalkieCardBorder,
                    RoundedCornerShape(26.dp)
                )
                .shadow(24.dp, RoundedCornerShape(26.dp)),
            color = WalkieDeviceBody
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
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
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isEmergencyActive) WalkieRed else WalkieGreen)
                        )
                        Column {
                            Text(
                                text = "TACTICAL FREQUENCY SYNTHESIZER",
                                color = WalkieTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                            Text(
                                text = "UHF / FRS SUB-BAND CHANNELS • CTCSS TONE",
                                color = if (isEmergencyActive) WalkieRed else WalkieAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = SpaceGrotesk
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = WalkieTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Digital Frequency Synthesizer Display (Cockpit HUD)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0C1210),
                                    Color(0xFF080D0B)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            if (isEmergencyActive) WalkieRed.copy(alpha = emergencyPulseAlpha) else WalkieGreen.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Top Sub-band Status Line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${activeChannel.id} • ${activeChannel.callsign}",
                                color = if (activeChannel.isEmergencyPriority) WalkieRed else WalkieGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                            Text(
                                text = "CTCSS ${activeChannel.ctcssHz} Hz",
                                color = WalkieAmber,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = SpaceGrotesk
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Large Glowing Frequency Readout
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.4f", activeChannel.frequencyMhz),
                                color = if (activeChannel.isEmergencyPriority) WalkieRed else Color(0xFF34D399),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MHz",
                                color = WalkieTextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = SpaceGrotesk,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // S-Meter Segmented LED Signal Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "S-METER",
                                color = WalkieTextMuted,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(9) { idx ->
                                    val isLit = idx < 7
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(
                                                if (isLit) WalkieGreen else WalkieTextMuted.copy(alpha = 0.2f)
                                            )
                                    )
                                }
                                repeat(3) { idx ->
                                    val isLit = idx < 2
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(
                                                if (isLit) WalkieRed else WalkieTextMuted.copy(alpha = 0.2f)
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "S9+10dB",
                                color = WalkieGreen,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Rotary Dial Tuner Control Deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Channel Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            knobRotation -= 30f
                            SquadChannelManager.tuneStep(-1)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = WalkieDeviceBodyLight,
                        border = BorderStroke(1.dp, WalkieCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Previous",
                                tint = WalkieTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PREV CH",
                                color = WalkieTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Center Rotary Metallic Tuning Dial Knob
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF2E2E35),
                                        Color(0xFF1B1B20),
                                        Color(0xFF111114)
                                    )
                                )
                            )
                            .border(2.dp, WalkieAmber.copy(alpha = 0.6f), CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAccumulator += dragAmount.y
                                        if (abs(dragAccumulator) > 28f) {
                                            val dir = if (dragAccumulator < 0) 1 else -1
                                            knobRotation += dir * 25f
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            SquadChannelManager.tuneStep(dir)
                                            dragAccumulator = 0f
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(knobRotation)
                        ) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val r = size.width / 2f

                            // Concentric metallic texture rings
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = r * 0.8f,
                                center = center,
                                style = Stroke(width = 1f)
                            )

                            // Graduation indicator notches (12 positions around dial)
                            repeat(12) { i ->
                                val angleRad = Math.toRadians((i * 30.0) - 90.0)
                                val innerR = r * 0.72f
                                val outerR = r * 0.95f
                                drawLine(
                                    color = if (i == 0) WalkieAmber else WalkieTextMuted.copy(alpha = 0.4f),
                                    start = Offset(
                                        (center.x + innerR * cos(angleRad)).toFloat(),
                                        (center.y + innerR * sin(angleRad)).toFloat()
                                    ),
                                    end = Offset(
                                        (center.x + outerR * cos(angleRad)).toFloat(),
                                        (center.y + outerR * sin(angleRad)).toFloat()
                                    ),
                                    strokeWidth = if (i == 0) 3f else 1.5f
                                )
                            }
                        }

                        // Center Knob Cap
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(WalkieAmber.copy(alpha = 0.2f))
                                .border(1.dp, WalkieAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = null,
                                tint = WalkieAmber,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Next Channel Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            knobRotation += 30f
                            SquadChannelManager.tuneStep(1)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = WalkieDeviceBodyLight,
                        border = BorderStroke(1.dp, WalkieCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT CH",
                                color = WalkieTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Next",
                                tint = WalkieTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tactical Channel Spectrum Roster
                Text(
                    text = "SUB-BAND FREQUENCY MATRIX (${channelList.size} CHANNELS)",
                    color = WalkieTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SpaceGrotesk
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(channelList, key = { it.id }) { channel ->
                        val isSelected = channel.id == activeChannel.id
                        val occupancy = channelOccupancy[channel.id] ?: 0

                        ChannelRowCard(
                            channel = channel,
                            isSelected = isSelected,
                            occupancy = occupancy,
                            onTune = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                SquadChannelManager.tuneToChannel(channel.id)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Emergency Priority Channel One-Tap Quick Override
                val emergencyBtnColor = if (isEmergencyActive) WalkieRed else WalkieRed.copy(alpha = 0.15f)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val newState = !isEmergencyActive
                        if (newState) {
                            SquadChannelManager.tuneToChannel("CH-06")
                        } else {
                            SquadChannelManager.setEmergencyOverride(false)
                            SquadChannelManager.tuneToChannel("CH-01")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = emergencyBtnColor,
                    border = BorderStroke(
                        1.dp,
                        WalkieRed.copy(alpha = if (isEmergencyActive) 1f else 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isEmergencyActive) Icons.Default.Warning else Icons.Default.Sos,
                            contentDescription = null,
                            tint = if (isEmergencyActive) Color.White else WalkieRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEmergencyActive) "CANCEL EMERGENCY PREEMPTION" else "🚨 EMERGENCY DISPATCH OVERRIDE (CH-06)",
                            color = if (isEmergencyActive) Color.White else WalkieRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SpaceGrotesk
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRowCard(
    channel: TacticalChannel,
    isSelected: Boolean,
    occupancy: Int,
    onTune: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cardColor = when {
        channel.isEmergencyPriority && isSelected -> WalkieRed.copy(alpha = 0.25f)
        channel.isEmergencyPriority -> WalkieRed.copy(alpha = 0.08f)
        isSelected -> WalkieGreen.copy(alpha = 0.15f)
        else -> WalkieDeviceBodyLight.copy(alpha = 0.45f)
    }

    val borderColor = when {
        channel.isEmergencyPriority && isSelected -> WalkieRed
        channel.isEmergencyPriority -> WalkieRed.copy(alpha = 0.4f)
        isSelected -> WalkieGreen
        else -> WalkieCardBorder.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            channel.isEmergencyPriority -> WalkieRed
                            isSelected -> WalkieGreen
                            else -> WalkieTextMuted
                        }
                    )
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${channel.id} • ${channel.callsign}",
                        color = WalkieTextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGrotesk
                    )
                    if (occupancy > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(WalkieAmber.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "👥 $occupancy",
                                color = WalkieAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                        }
                    }
                }
                Text(
                    text = "${channel.frequencyMhz} MHz • CTCSS ${channel.ctcssHz} Hz • ${channel.description}",
                    color = WalkieTextMuted,
                    fontSize = 9.5.sp,
                    fontFamily = SpaceGrotesk,
                    maxLines = 1
                )
            }
        }

        // Tune Button
        Surface(
            onClick = {
                onTune()
            },
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) WalkieGreen else WalkieButton,
            modifier = Modifier.height(26.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSelected) "TUNED" else "TUNE",
                    color = if (isSelected) Color.Black else WalkieTextPrimary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SpaceGrotesk
                )
            }
        }
    }
}
