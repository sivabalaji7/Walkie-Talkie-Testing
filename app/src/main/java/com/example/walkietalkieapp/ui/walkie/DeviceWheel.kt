package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.walkietalkieapp.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

private const val WHEEL_RIB_COUNT = 12
private const val WHEEL_PITCH = (2 * PI / WHEEL_RIB_COUNT).toFloat()
private const val WHEEL_RADIUS_DP = 38f
private const val STEP_ANGLE = 0.50f

@Composable
fun DeviceWheel(
    onScroll: (direction: Int) -> Unit,
    isActive: Boolean,
    deviceCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val safeDeviceCount = deviceCount.coerceAtLeast(1)
    val safeIndex = currentIndex.coerceIn(0, safeDeviceCount - 1)
    val minAngle = -(safeDeviceCount - 1) * STEP_ANGLE
    val maxAngle = 0f

    val angleAnim = remember { Animatable(-safeIndex * STEP_ANGLE) }
    var angle by remember { mutableFloatStateOf(-safeIndex * STEP_ANGLE) }
    var accY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(safeIndex, safeDeviceCount) {
        val target = -safeIndex * STEP_ANGLE
        if (abs(angle - target) > 0.02f) {
            angleAnim.snapTo(angle)
            angleAnim.animateTo(target, spring(dampingRatio = 0.70f, stiffness = 420f)) {
                angle = this.value
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(116.dp)
                .shadow(
                    elevation = if (isActive || isDragging) 8.dp else 4.dp,
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                )
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1E1E22),
                            Color(0xFF2C2C33),
                            Color(0xFF232328),
                            Color(0xFF19191C)
                        )
                    )
                )
                .border(
                    1.dp,
                    if (isActive || isDragging) WalkieAmber.copy(alpha = 0.7f) else WalkieCardBorder,
                    RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                )
                .pointerInput(safeIndex, safeDeviceCount) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            accY = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            accY = 0f
                            val target = -safeIndex * STEP_ANGLE
                            coroutineScope.launch {
                                angleAnim.snapTo(angle)
                                angleAnim.animateTo(target, spring(dampingRatio = 0.70f, stiffness = 450f)) {
                                    angle = this.value
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            accY = 0f
                            val target = -safeIndex * STEP_ANGLE
                            coroutineScope.launch {
                                angleAnim.snapTo(angle)
                                angleAnim.animateTo(target, spring(dampingRatio = 0.70f, stiffness = 450f)) {
                                    angle = this.value
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dy = dragAmount.y
                            val rawDelta = -dy * 0.045f
                            accY += dy

                            val newAngle = if (rawDelta > 0 && angle >= maxAngle) {
                                (angle + rawDelta * 0.12f).coerceAtMost(maxAngle + 0.20f)
                            } else if (rawDelta < 0 && angle <= minAngle) {
                                (angle + rawDelta * 0.12f).coerceAtLeast(minAngle - 0.20f)
                            } else {
                                (angle + rawDelta).coerceIn(minAngle - 0.20f, maxAngle + 0.20f)
                            }
                            angle = newAngle

                            // Stepped scroll triggering with bounds check and haptics
                            if (abs(accY) > 18f) {
                                val dir = if (accY > 0) 1 else -1
                                if (dir > 0 && safeIndex < safeDeviceCount - 1) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onScroll(1)
                                    accY = 0f
                                } else if (dir < 0 && safeIndex > 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onScroll(-1)
                                    accY = 0f
                                } else {
                                    // Hard stop reached: clamp travel
                                    accY = 0f
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Cylindrical lighting sheen overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f)
                            )
                        )
                    )
            )

            // Animated 3D Cylindrical Ribs/Notches rendered in high-performance Canvas
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radiusPx = WHEEL_RADIUS_DP.dp.toPx()
                val ribHeightPx = 2.dp.toPx()
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
                val baseWidthPx = (if (isActive || isDragging) 18f else 14f).dp.toPx()

                for (i in 0 until WHEEL_RIB_COUNT) {
                    var theta = (i * WHEEL_PITCH + angle) % (2 * PI).toFloat()
                    if (theta > PI) theta -= (2 * PI).toFloat()
                    if (theta < -PI) theta += (2 * PI).toFloat()

                    // Only visible on front facing arc of cylinder
                    if (abs(theta) < 1.42f) {
                        val yOffset = radiusPx * sin(theta)
                        val cosTheta = cos(theta)
                        val ribWidth = baseWidthPx * cosTheta.pow(0.4f)
                        val opacity = (cosTheta.pow(1.2f) * (if (isActive) 0.95f else 0.70f)).coerceIn(0.15f, 1f)
                        val scaleY = max(0.55f, cosTheta)
                        val isCenter = abs(theta) < 0.25f

                        val ribColor = if (isCenter && isActive) WalkieAmber.copy(alpha = opacity)
                        else if (isCenter) Color.White.copy(alpha = opacity)
                        else WalkieTextSecondary.copy(alpha = opacity)

                        val h = ribHeightPx * scaleY
                        drawRoundRect(
                            color = ribColor,
                            topLeft = Offset(centerX - ribWidth / 2f, centerY + yOffset - h / 2f),
                            size = androidx.compose.ui.geometry.Size(ribWidth, h),
                            cornerRadius = cornerRadius
                        )
                    }
                }

                // Hard limit indicators at top and bottom
                val isAtTop = safeIndex == 0 && angle >= -0.05f
                val isAtBottom = safeIndex >= safeDeviceCount - 1 && angle <= minAngle + 0.05f

                if (isAtTop) {
                    drawRoundRect(
                        color = WalkieAmber.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - 6.dp.toPx(), 4.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = cornerRadius
                    )
                }
                if (isAtBottom) {
                    drawRoundRect(
                        color = WalkieAmber.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - 6.dp.toPx(), size.height - 6.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }
    }
}
