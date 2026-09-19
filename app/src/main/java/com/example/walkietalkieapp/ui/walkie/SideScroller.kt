package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

private const val RIB_COUNT = 14
private const val PITCH = (2 * PI / RIB_COUNT).toFloat()
private const val RADIUS_DP = 44f // Cylinder radius
private const val STEP_ANGLE = 0.50f // Angle per squad step

@Composable
fun SideScroller(
    onStep: (direction: Int) -> Unit,
    onPull: () -> Unit,
    disabled: Boolean = false,
    label: String = "PULL TO JOIN",
    currentIndex: Int = 0,
    itemCount: Int = 1,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dragXAnim = remember { Animatable(0f) }
    var dragXOffset by remember { mutableFloatStateOf(0f) }

    val safeItemCount = itemCount.coerceAtLeast(1)
    val safeIndex = currentIndex.coerceIn(0, safeItemCount - 1)
    val minAngle = -(safeItemCount - 1) * STEP_ANGLE
    val maxAngle = 0f

    val angleAnim = remember { Animatable(-safeIndex * STEP_ANGLE) }
    var angle by remember { mutableFloatStateOf(-safeIndex * STEP_ANGLE) }

    var isEngaged by remember { mutableStateOf(false) }
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }
    var isPullingRight by remember { mutableStateOf(false) }
    var hasPulledTrigger by remember { mutableStateOf(false) }

    // Synchronize angle when external index changes
    LaunchedEffect(safeIndex, safeItemCount) {
        val target = -safeIndex * STEP_ANGLE
        if (abs(angle - target) > 0.02f) {
            angleAnim.snapTo(angle)
            angleAnim.animateTo(target, spring(dampingRatio = 0.70f, stiffness = 420f)) {
                angle = this.value
            }
        }
    }

    val scaleX by animateFloatAsState(
        targetValue = if (isEngaged) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f),
        label = "scrollerScaleX"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = dragXOffset.dp.toPx()
                    this.scaleX = scaleX
                    this.scaleY = 1f + (dragXOffset / 70f) * 0.15f
                }
                .width(32.dp)
                .height(128.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(WalkieDeviceBodyLight)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .pointerInput(disabled, safeIndex, safeItemCount) {
                    if (!disabled) {
                        detectDragGestures(
                            onDragStart = {
                                isEngaged = true
                                isPullingRight = false
                                hasPulledTrigger = false
                                accX = 0f
                                accY = 0f
                            },
                            onDragEnd = {
                                if (hasPulledTrigger) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPull()
                                }
                                hasPulledTrigger = false
                                isPullingRight = false
                                isEngaged = false
                                accX = 0f
                                accY = 0f
                                coroutineScope.launch {
                                    dragXAnim.snapTo(dragXOffset)
                                    dragXAnim.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 420f)) {
                                        dragXOffset = this.value
                                    }
                                }
                                // Spring back to snapped step angle
                                val target = -safeIndex * STEP_ANGLE
                                coroutineScope.launch {
                                    angleAnim.snapTo(angle)
                                    angleAnim.animateTo(target, spring(dampingRatio = 0.70f, stiffness = 450f)) {
                                        angle = this.value
                                    }
                                }
                            },
                            onDragCancel = {
                                hasPulledTrigger = false
                                isPullingRight = false
                                isEngaged = false
                                accX = 0f
                                accY = 0f
                                coroutineScope.launch {
                                    dragXAnim.snapTo(dragXOffset)
                                    dragXAnim.animateTo(0f) {
                                        dragXOffset = this.value
                                    }
                                }
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
                                accX += dragAmount.x
                                accY += dragAmount.y

                                // Intentional right pull requires strong horizontal intent
                                if ((accX > 22f && accX > abs(accY) * 1.3f) || isPullingRight) {
                                    isPullingRight = true
                                    dragXOffset = (dragXOffset + dragAmount.x).coerceIn(0f, 65f)
                                    hasPulledTrigger = dragXOffset > 44f
                                    return@detectDragGestures
                                }

                                // Bounded vertical scroll mode with hard limit stops
                                val dy = dragAmount.y
                                val rawDelta = -dy * 0.040f

                                val newAngle = if (rawDelta > 0 && angle >= maxAngle) {
                                    // At top boundary: high rubberband resistance damping
                                    (angle + rawDelta * 0.12f).coerceAtMost(maxAngle + 0.20f)
                                } else if (rawDelta < 0 && angle <= minAngle) {
                                    // At bottom boundary: high rubberband resistance damping
                                    (angle + rawDelta * 0.12f).coerceAtLeast(minAngle - 0.20f)
                                } else {
                                    (angle + rawDelta).coerceIn(minAngle - 0.20f, maxAngle + 0.20f)
                                }
                                angle = newAngle

                                if (abs(accY) > 20f) {
                                    val dir = if (accY > 0) 1 else -1
                                    if (dir > 0 && safeIndex < safeItemCount - 1) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onStep(1)
                                        accY = 0f
                                    } else if (dir < 0 && safeIndex > 0) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onStep(-1)
                                        accY = 0f
                                    } else {
                                        // Hard stop reached: clamp travel
                                        accY = 0f
                                    }
                                }
                            }
                        )
                    }
                }
        ) {
            // Internal bevel lighting and depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )

            // Cylindrical serrated ribs rendered in high-performance Canvas
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radiusPx = RADIUS_DP.dp.toPx()
                val ribHeightPx = 1.8.dp.toPx()
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
                val baseWidthPx = (if (isEngaged) 20f else 16f).dp.toPx()

                for (i in 0 until RIB_COUNT) {
                    var theta = (i * PITCH + angle) % (2 * PI).toFloat()
                    if (theta > PI) theta -= (2 * PI).toFloat()
                    if (theta < -PI) theta += (2 * PI).toFloat()

                    if (abs(theta) < 1.42f) {
                        val yOffset = radiusPx * sin(theta)
                        val cosTheta = cos(theta)
                        val ribWidth = baseWidthPx * cosTheta.pow(0.4f)
                        val opacity = (cosTheta.pow(1.2f) * (if (isEngaged) 0.95f else 0.75f)).coerceIn(0.12f, 1f)
                        val scaleY = max(0.6f, cosTheta)
                        val isCenter = abs(theta) < 0.25f

                        val ribColor = if (isCenter) Color.White.copy(alpha = opacity) else WalkieTextSecondary.copy(alpha = opacity)
                        val h = ribHeightPx * scaleY
                        drawRoundRect(
                            color = ribColor,
                            topLeft = Offset(centerX - ribWidth / 2f, centerY + yOffset - h / 2f),
                            size = androidx.compose.ui.geometry.Size(ribWidth, h),
                            cornerRadius = cornerRadius
                        )
                    }
                }

                // Visual hard stop limits at top and bottom
                val isAtTop = safeIndex == 0 && angle >= -0.05f
                val isAtBottom = safeIndex >= safeItemCount - 1 && angle <= minAngle + 0.05f

                if (isAtTop) {
                    drawRoundRect(
                        color = WalkieAmber.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - 8.dp.toPx(), 4.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 2.5.dp.toPx()),
                        cornerRadius = cornerRadius
                    )
                }
                if (isAtBottom) {
                    drawRoundRect(
                        color = WalkieAmber.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - 8.dp.toPx(), size.height - 6.5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 2.5.dp.toPx()),
                        cornerRadius = cornerRadius
                    )
                }

                // Vertical thumb track position indicator
                if (safeItemCount > 1) {
                    val trackHeight = size.height - 24.dp.toPx()
                    val thumbFraction = safeIndex.toFloat() / (safeItemCount - 1).coerceAtLeast(1)
                    val thumbY = 12.dp.toPx() + trackHeight * thumbFraction
                    drawCircle(
                        color = WalkieAmber,
                        radius = 1.8.dp.toPx(),
                        center = Offset(size.width - 4.dp.toPx(), thumbY)
                    )
                }
            }

            // Tactile indicator on left edge
            if (isEngaged) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp)
                        .width(2.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(WalkieAmber)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = WalkieAmber.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-2).dp)
                    .size(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontFamily = SpaceGrotesk,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = WalkieTextMuted.copy(alpha = 0.7f),
            letterSpacing = 1.sp
        )
    }
}
