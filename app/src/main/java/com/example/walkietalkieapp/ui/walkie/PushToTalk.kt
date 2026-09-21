package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.ui.theme.*

@Composable
fun PushToTalk(
    isTalking: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    disabled: Boolean = false,
    mode: ConnectivityMode = ConnectivityMode.INTERNET,
    onHardwarePttClick: () -> Unit = {},
    isHardwarePttEnabled: Boolean = true,
    isHardwareKeyDown: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentTheme = ModeThemes.get(mode)
    val haptic = LocalHapticFeedback.current

    // Ultra-smooth button scale animation with high-stiffness tactile spring
    val buttonScale by animateFloatAsState(
        targetValue = if (isTalking) 0.89f else 1f,
        animationSpec = spring(dampingRatio = 0.50f, stiffness = 550f),
        label = "pttScale"
    )

    // Breathing ring animation when idle (isolated to draw phase)
    val infiniteTransition = rememberInfiniteTransition(label = "pttInfinite")
    val breatheScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )
    val breatheAlpha = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )

    // Concentric transmission pulse rings when talking (isolated to draw phase)
    val transmissionPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "transmissionPhase"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center
        ) {
            // High-Performance Concentric Transmission Rings & Boundary in Draw Phase
            Canvas(modifier = Modifier.size(116.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // 1. Outer transmission pulse rings (when talking)
                if (isTalking) {
                    val phase = transmissionPhase.value
                    val baseRadius = 42.dp.toPx()
                    for (i in 0 until 3) {
                        val ringProgress = ((phase + i * 0.33f) % 1f)
                        val ringScale = 1f + ringProgress * 0.45f
                        val ringAlpha = (1f - ringProgress) * 0.5f
                        drawCircle(
                            color = currentTheme.primaryColor.copy(alpha = ringAlpha),
                            radius = baseRadius * ringScale,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                } else if (!disabled) {
                    // 2. Idle breathing ring
                    val scale = breatheScale.value
                    val alpha = breatheAlpha.value
                    drawCircle(
                        color = WalkieCardBorder.copy(alpha = alpha),
                        radius = 48.dp.toPx() * scale,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 3. Dashed boundary ring
                drawCircle(
                    color = if (isTalking) currentTheme.primaryColor.copy(alpha = 0.8f) else Color(0x1FFFFFFF),
                    radius = 46.dp.toPx(),
                    center = center,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                )
            }

            // 4. Main PTT Push Button (Hardware accelerated scale via graphicsLayer)
            val buttonBrush = if (isTalking) {
                currentTheme.gradient
            } else {
                Brush.radialGradient(
                    listOf(WalkieButtonHover, WalkieButton)
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .size(82.dp)
                    .shadow(
                        elevation = if (isTalking) 8.dp else 3.dp,
                        shape = CircleShape,
                        ambientColor = if (isTalking) currentTheme.primaryColor else Color.Black,
                        spotColor = if (isTalking) currentTheme.primaryColor else Color.Black
                    )
                    .clip(CircleShape)
                    .background(buttonBrush)
                    .border(
                        width = 1.5.dp,
                        color = if (isTalking) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
                    .pointerInput(disabled) {
                        if (!disabled) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPressStart()
                                waitForUpOrCancellation()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPressEnd()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Push to talk",
                    tint = if (isTalking) Color.White else WalkieTextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Observe live VAD speech detection from microphone stream
        val isLiveSpeechActive by com.example.walkietalkieapp.audio.VoiceActivityDetector.isLiveSpeechActive.collectAsState()

        // Tactile Status Hint Text
        Text(
            text = when {
                isTalking && isLiveSpeechActive -> "VOICE ACTIVE • CAPTURING"
                isTalking -> "TRANSMITTING LIVE"
                else -> "HOLD TO TALK"
            },
            fontFamily = SpaceGrotesk,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                isTalking && isLiveSpeechActive -> StatusReady
                isTalking -> currentTheme.primaryColor
                else -> WalkieTextMuted
            },
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(5.dp))

        // Hardware PTT Physical Chassis Switch Pill
        val hwInteractionSource = remember { MutableInteractionSource() }
        val hwPressed by hwInteractionSource.collectIsPressedAsState()
        val hwScale by animateFloatAsState(
            targetValue = if (hwPressed) 0.93f else 1f,
            animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
            label = "hwScale"
        )

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onHardwarePttClick()
            },
            shape = RoundedCornerShape(8.dp),
            color = if (isHardwareKeyDown) WalkieAmber.copy(alpha = 0.25f)
                    else if (isHardwarePttEnabled) StatusReady.copy(alpha = 0.12f)
                    else WalkieButton,
            border = BorderStroke(
                1.dp,
                if (isHardwareKeyDown) WalkieAmber
                else if (isHardwarePttEnabled) StatusReady.copy(alpha = 0.5f)
                else WalkieCardBorder
            ),
            modifier = Modifier.graphicsLayer {
                scaleX = hwScale
                scaleY = hwScale
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isHardwareKeyDown) WalkieAmber else if (isHardwarePttEnabled) StatusReady else StatusOff)
                )
                Text(
                    text = if (isHardwareKeyDown) "HW KEY PRESSED" else if (isHardwarePttEnabled) "HW PTT: VOL KEY ON" else "HW PTT: OFF",
                    fontFamily = SpaceGrotesk,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHardwareKeyDown) WalkieAmber else if (isHardwarePttEnabled) StatusReady else WalkieTextSecondary,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
