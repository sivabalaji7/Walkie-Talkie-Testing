package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.walkietalkieapp.chat.TacticalChatManager
import com.example.walkietalkieapp.location.RadarRange
import com.example.walkietalkieapp.location.SquadMemberLocation
import com.example.walkietalkieapp.location.SquadRadarManager
import com.example.walkietalkieapp.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SquadRadarDialog(
    onDismiss: () -> Unit,
    squadName: String = "SQUAD",
    currentUsername: String = "OPERATOR"
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val myLocation by SquadRadarManager.myLocation.collectAsState()
    val heading by SquadRadarManager.compassHeading.collectAsState()
    val squadLocations by SquadRadarManager.squadLocations.collectAsState()
    val radarRange by SquadRadarManager.radarRange.collectAsState()
    val isSosActive by SquadRadarManager.isSosActive.collectAsState()
    val isGpsLocked by SquadRadarManager.isGpsFixAcquired.collectAsState()

    // Start hardware compass when dialog is open, stop when closed
    DisposableEffect(Unit) {
        SquadRadarManager.startCompass(context)
        SquadRadarManager.fetchDeviceLocation(context) { /* location primed */ }
        onDispose {
            SquadRadarManager.stopCompass()
        }
    }

    // Rotating radar sweep animation (360° continuously)
    val infiniteTransition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    // Pulsing halo animation for active SOS distress
    val sosPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sosPulse"
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
                .border(1.5.dp, if (isSosActive) WalkieRed else WalkieCardBorder, RoundedCornerShape(26.dp))
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
                                .background(if (isGpsLocked) StatusReady else WalkieAmber)
                        )
                        Column {
                            Text(
                                text = "TACTICAL SQUAD RADAR",
                                color = WalkieTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                            Text(
                                text = "PPI SCANNER • ${squadName.uppercase()} GRID",
                                color = WalkieGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = SpaceGrotesk
                            )
                        }
                    }

                    // Close Button
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

                Spacer(modifier = Modifier.height(10.dp))

                // Compass & GPS Telemetry Chip Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(WalkieDeviceBodyLight.copy(alpha = 0.5f))
                        .border(1.dp, WalkieCardBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cardinal = SquadRadarManager.bearingToCardinal(heading)
                    Text(
                        text = "HEADING: ${heading.toInt()}° $cardinal",
                        color = WalkieGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGrotesk
                    )
                    Text(
                        text = if (isGpsLocked) "GPS 3D FIX: ±${myLocation?.accuracy?.toInt() ?: 4}m" else "ACQUIRING GPS...",
                        color = if (isGpsLocked) StatusReady else WalkieAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SpaceGrotesk
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Authentic Plan Position Indicator (PPI) Radar Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF040D0A))
                            .border(1.5.dp, WalkieGreen.copy(alpha = 0.5f), CircleShape)
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.width / 2f

                        // Concentric Range Rings (33%, 66%, 100%)
                        drawCircle(
                            color = WalkieGreen.copy(alpha = 0.15f),
                            radius = radius * 0.33f,
                            center = center,
                            style = Stroke(width = 1f)
                        )
                        drawCircle(
                            color = WalkieGreen.copy(alpha = 0.25f),
                            radius = radius * 0.66f,
                            center = center,
                            style = Stroke(width = 1f)
                        )
                        drawCircle(
                            color = WalkieGreen.copy(alpha = 0.4f),
                            radius = radius * 0.98f,
                            center = center,
                            style = Stroke(width = 1.5f)
                        )

                        // Crosshair Grid Lines
                        drawLine(
                            color = WalkieGreen.copy(alpha = 0.2f),
                            start = Offset(center.x, 0f),
                            end = Offset(center.x, size.height),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = WalkieGreen.copy(alpha = 0.2f),
                            start = Offset(0f, center.y),
                            end = Offset(size.width, center.y),
                            strokeWidth = 1f
                        )

                        // Rotating Sweep Line with Phosphor Glow Persistence
                        val sweepRad = Math.toRadians(sweepAngle.toDouble())
                        val sweepEnd = Offset(
                            (center.x + radius * cos(sweepRad)).toFloat(),
                            (center.y + radius * sin(sweepRad)).toFloat()
                        )
                        drawLine(
                            brush = Brush.radialGradient(
                                colors = listOf(WalkieGreen, WalkieGreen.copy(alpha = 0.1f)),
                                center = center,
                                radius = radius
                            ),
                            start = center,
                            end = sweepEnd,
                            strokeWidth = 2.5f
                        )

                        // Plotted Squad Member Blips
                        val maxDistance = radarRange.radiusMeters
                        squadLocations.values.forEach { member ->
                            val clampedDist = member.distanceMeters.coerceAtMost(maxDistance)
                            val normalizedRadius = (clampedDist / maxDistance) * (radius * 0.9f)

                            // 0° relative bearing points UP (-Y direction in canvas)
                            val angleRad = Math.toRadians((member.relativeBearing - 90f).toDouble())
                            val blipX = (center.x + normalizedRadius * cos(angleRad)).toFloat()
                            val blipY = (center.y + normalizedRadius * sin(angleRad)).toFloat()

                            if (member.isSos) {
                                // Flashing Red SOS Distress Blip
                                drawCircle(
                                    color = WalkieRed.copy(alpha = sosPulseAlpha * 0.4f),
                                    radius = 16f,
                                    center = Offset(blipX, blipY)
                                )
                                drawCircle(
                                    color = WalkieRed,
                                    radius = 6f,
                                    center = Offset(blipX, blipY)
                                )
                            } else {
                                // Glowing Emerald Squad Blip
                                drawCircle(
                                    color = WalkieGreen.copy(alpha = 0.35f),
                                    radius = 10f,
                                    center = Offset(blipX, blipY)
                                )
                                drawCircle(
                                    color = Color(0xFF34D399),
                                    radius = 4.5f,
                                    center = Offset(blipX, blipY)
                                )
                            }
                        }

                        // Operator Position at Center (Glow dot + Forward heading Chevron)
                        drawCircle(
                            color = WalkieAmber.copy(alpha = 0.4f),
                            radius = 9f,
                            center = center
                        )
                        drawCircle(
                            color = WalkieAmber,
                            radius = 4f,
                            center = center
                        )
                    }

                    // Overlay Range Tag at Top of Radar
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "RANGE: ${radarRange.label}",
                            color = WalkieGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SpaceGrotesk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Range Zoom Selector Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RadarRange.values().forEach { range ->
                        val isSelected = radarRange == range
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                SquadRadarManager.setRadarRange(range)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) WalkieGreen.copy(alpha = 0.2f) else WalkieDeviceBodyLight.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) WalkieGreen else WalkieCardBorder.copy(alpha = 0.4f)
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = range.label,
                                    color = if (isSelected) WalkieGreen else WalkieTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = SpaceGrotesk
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Squad Distance & Direction Cards
                Text(
                    text = "SQUAD BEARINGS (${squadLocations.size} TRACKED)",
                    color = WalkieTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SpaceGrotesk
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (squadLocations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(WalkieDeviceBodyLight.copy(alpha = 0.3f))
                            .border(1.dp, WalkieCardBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = WalkieTextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Awaiting squad member location beacons",
                                color = WalkieTextMuted,
                                fontSize = 11.sp,
                                fontFamily = SpaceGrotesk
                            )
                            Text(
                                text = "Use 'DROP BEACON' below to broadcast your fix",
                                color = WalkieGreen.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontFamily = SpaceGrotesk
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(squadLocations.values.toList(), key = { it.callsign }) { member ->
                            SquadBearingCard(member = member)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tactical Action Controls: Drop Beacon & Distress SOS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Drop GPS Beacon Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            TacticalChatManager.sendGpsBeacon(
                                context = context,
                                senderName = currentUsername,
                                label = "$squadName RADAR BEACON"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = WalkieGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, WalkieGreen.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = WalkieGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DROP BEACON",
                                color = WalkieGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGrotesk
                            )
                        }
                    }

                    // Distress SOS Beacon Button
                    val sosButtonColor = if (isSosActive) WalkieRed else WalkieRed.copy(alpha = 0.15f)
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val newSos = !isSosActive
                            SquadRadarManager.setSosActive(newSos)
                            if (newSos) {
                                TacticalChatManager.sendGpsBeacon(
                                    context = context,
                                    senderName = currentUsername,
                                    label = "🚨 EMERGENCY DISTRESS SOS"
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = sosButtonColor,
                        border = BorderStroke(1.dp, WalkieRed.copy(alpha = if (isSosActive) 1f else 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSosActive) Icons.Default.Warning else Icons.Default.Sos,
                                contentDescription = null,
                                tint = if (isSosActive) Color.White else WalkieRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSosActive) "CANCEL SOS" else "EMERGENCY SOS",
                                color = if (isSosActive) Color.White else WalkieRed,
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
}

@Composable
private fun SquadBearingCard(member: SquadMemberLocation) {
    val distanceDisplay = if (member.distanceMeters < 1000f) {
        "${member.distanceMeters.toInt()}m"
    } else {
        String.format(java.util.Locale.US, "%.1f km", member.distanceMeters / 1000f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (member.isSos) WalkieRed.copy(alpha = 0.2f) else WalkieDeviceBodyLight.copy(alpha = 0.45f))
            .border(
                1.dp,
                if (member.isSos) WalkieRed else WalkieCardBorder.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (member.isSos) WalkieRed else WalkieGreen)
            )
            Column {
                Text(
                    text = member.callsign,
                    color = WalkieTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SpaceGrotesk
                )
                Text(
                    text = "${member.cardinalDirection} ${member.trueBearing.toInt()}° • ${member.clockPosition}",
                    color = WalkieTextMuted,
                    fontSize = 10.sp,
                    fontFamily = SpaceGrotesk
                )
            }
        }

        // Distance & Status Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (member.isSos) WalkieRed else WalkieGreen.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (member.isSos) "SOS $distanceDisplay" else distanceDisplay,
                color = if (member.isSos) Color.White else WalkieGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SpaceGrotesk
            )
        }
    }
}
