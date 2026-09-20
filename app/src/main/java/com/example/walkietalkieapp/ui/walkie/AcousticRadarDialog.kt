package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walkietalkieapp.audio.intelligence.AcousticEnvironment
import com.example.walkietalkieapp.ui.theme.*
import java.util.Locale

/**
 * Returns the Compose [Color] corresponding to this [AcousticEnvironment].
 */
fun AcousticEnvironment.toComposeColor(): Color {
    return when (this) {
        AcousticEnvironment.QUIET -> Color(0xFF22C55E)
        AcousticEnvironment.NORMAL -> Color(0xFF38BDF8)
        AcousticEnvironment.NOISY -> Color(0xFFF59E0B)
        AcousticEnvironment.VERY_NOISY -> Color(0xFFEF4444)
        AcousticEnvironment.UNKNOWN -> Color(0xFF9CA3AF)
    }
}

private val FREQUENCY_LABELS = listOf("63", "125", "250", "500", "1k", "2k", "4k", "8k")

/**
 * Tactical Acoustic Radar Dialog modal providing real-time SPL dBA measurement,
 * 8-band environmental noise profiling, noise floor diagnostics, and 1-tap baseline calibration.
 */
@Composable
fun AcousticRadarDialog(
    currentSplDb: Float,
    currentEnvironment: AcousticEnvironment,
    baselineNoiseFloorDb: Float,
    spectralBands: List<Float>,
    isCalibrating: Boolean,
    calibrationProgress: Float,
    onCalibrate: () -> Unit,
    onDismiss: () -> Unit,
    mode: ConnectivityMode = ConnectivityMode.INTERNET
) {
    val haptic = LocalHapticFeedback.current
    val envColor = currentEnvironment.toComposeColor()
    val currentTheme = ModeThemes.get(mode)

    // Animated SPL meter level (20 to 110 dBA range mapped to 0..1f)
    val meterRatio = ((currentSplDb - 20f) / 90f).coerceIn(0.05f, 1f)
    val animatedRatio by animateFloatAsState(
        targetValue = meterRatio,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "meterRatioAnim"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = WalkieCard,
            border = BorderStroke(1.dp, WalkieCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Header: Icon, Title & Close Button
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(envColor.copy(alpha = 0.16f))
                                .border(1.dp, envColor.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Radar",
                                tint = envColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "TACTICAL ACOUSTIC RADAR",
                                fontFamily = SpaceGrotesk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextPrimary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Real-time SPL dBA & Noise Profiling",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                color = WalkieTextSecondary
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = WalkieButton,
                        border = BorderStroke(1.dp, WalkieCardBorder),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = WalkieTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 2. Large Digital SPL Readout Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WalkieDeviceBody,
                    border = BorderStroke(1.dp, WalkieCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Numeric Readout
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f", currentSplDb),
                                fontFamily = SpaceGrotesk,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = envColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "dBA SPL",
                                fontFamily = SpaceGrotesk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Environment Classification Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = envColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, envColor.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(envColor)
                                )
                                Text(
                                    text = currentEnvironment.displayName,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = envColor,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }

                        // Continuous Heatmap Meter Bar
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(WalkieButton)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedRatio)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFF22C55E),
                                                    Color(0xFF38BDF8),
                                                    Color(0xFFF59E0B),
                                                    Color(0xFFEF4444)
                                                )
                                            )
                                        )
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("20 dBA", fontFamily = SpaceGrotesk, fontSize = 9.sp, color = WalkieTextMuted)
                                Text("55 dBA", fontFamily = SpaceGrotesk, fontSize = 9.sp, color = WalkieTextMuted)
                                Text("85 dBA", fontFamily = SpaceGrotesk, fontSize = 9.sp, color = WalkieTextMuted)
                                Text("110 dBA", fontFamily = SpaceGrotesk, fontSize = 9.sp, color = WalkieTextMuted)
                            }
                        }
                    }
                }

                // 3. 8-Band Live Tactical Spectral Equalizer
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WalkieDeviceBody,
                    border = BorderStroke(1.dp, WalkieCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SPECTRAL ENERGY DENSITY",
                                fontFamily = SpaceGrotesk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WalkieTextSecondary,
                                letterSpacing = 0.6.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = envColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "8 BANDS",
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = envColor
                                )
                            }
                        }

                        // 8 Animated Bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            for (i in 0 until 8) {
                                val energy = spectralBands.getOrElse(i) { 0.1f }
                                val animatedBarHeight by animateFloatAsState(
                                    targetValue = (energy * 50f).coerceIn(4f, 50f),
                                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 550f),
                                    label = "band_$i"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(animatedBarHeight.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        envColor,
                                                        envColor.copy(alpha = 0.35f)
                                                    )
                                                )
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = FREQUENCY_LABELS.getOrElse(i) { "" },
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 8.sp,
                                        color = WalkieTextMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Tactical Environmental Diagnostics Matrix
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WalkieDeviceBody,
                    border = BorderStroke(1.dp, WalkieCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        DiagnosticMetric(
                            label = "BASELINE FLOOR",
                            value = "${baselineNoiseFloorDb.toInt()} dBA"
                        )
                        DiagnosticMetric(
                            label = "GATE THRESHOLD",
                            value = "${(baselineNoiseFloorDb + 8f).toInt()} dBA"
                        )
                        DiagnosticMetric(
                            label = "DSP PROFILING",
                            value = "KRISP+RNNoise"
                        )
                    }
                }

                // 5. Tactile Auto-Calibration Physical Action Button
                TactileCalibrationButton(
                    isCalibrating = isCalibrating,
                    progress = calibrationProgress,
                    activeThemeColor = currentTheme.primaryColor,
                    onClick = onCalibrate
                )

                // 6. Dismiss Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = WalkieButton,
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WalkieTextMuted,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontFamily = SpaceGrotesk,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = WalkieTextMuted,
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = SpaceGrotesk,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WalkieTextPrimary
        )
    }
}

@Composable
private fun TactileCalibrationButton(
    isCalibrating: Boolean,
    progress: Float,
    activeThemeColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
        label = "calibBtnScale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isCalibrating) {
                        WalkieAmber.copy(alpha = 0.2f)
                    } else {
                        WalkieButton
                    }
                )
                .border(
                    1.dp,
                    if (isCalibrating) WalkieAmber else WalkieCardBorder,
                    RoundedCornerShape(14.dp)
                )
                .clickable(
                    enabled = !isCalibrating,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                )
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = if (isCalibrating) WalkieAmber else WalkieTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isCalibrating) {
                        "SWEEPING AMBIENT NOISE (${(progress * 100).toInt()}%)"
                    } else {
                        "AUTO-CALIBRATE BASELINE (2.0s SWEEP)"
                    },
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isCalibrating) WalkieAmber else WalkieTextPrimary,
                    letterSpacing = 0.6.sp
                )
            }
        }

        if (isCalibrating) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = WalkieAmber,
                trackColor = WalkieButton
            )
        }
    }
}
