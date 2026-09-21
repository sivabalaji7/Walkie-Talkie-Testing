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

    val currentOnStep by rememberUpdatedState(onStep)
    val currentOnPull by rememberUpdatedState(onPull)
    val currentDisabled by rememberUpdatedState(disabled)
    val currentSafeIndex by rememberUpdatedState(safeIndex)
    val currentSafeItemCount by rememberUpdatedState(safeItemCount)

    val angleAnim = remember { Animatable(0f) }
    var angle by remember { mutableFloatStateOf(0f) }
    var lastReportedIndex by remember { mutableIntStateOf(safeIndex) }

    var isEngaged by remember { mutableStateOf(false) }
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }
    var isPullingRight by remember { mutableStateOf(false) }
    var hasPulledTrigger by remember { mutableStateOf(false) }

    // Synchronize angle when external index changes (e.g. tapped item or remote event)
    LaunchedEffect(safeIndex, safeItemCount) {
        if (!isEngaged) {
            if (safeIndex != lastReportedIndex) {
                val diff = safeIndex - lastReportedIndex
                val stepDelta = if (safeItemCount > 1) {
                    val modDiff = diff.mod(safeItemCount)
                    if (modDiff <= safeItemCount / 2) modDiff else modDiff - safeItemCount
                } else {
                    0
                }
                lastReportedIndex = safeIndex
                if (stepDelta != 0) {
                    val target = angle - stepDelta * PITCH
                    angleAnim.snapTo(angle)
                    angleAnim.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 450f)) {
                        angle = this.value
                    }
                }
            }
        } else {
            lastReportedIndex = safeIndex
        }
    }

    val scaleX by animateFloatAsState(
        targetValue = if (isEngaged) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f),
        label = "scrollerScaleX"
    )

    val targetThumbFraction = if (safeItemCount > 1) {
        safeIndex.toFloat() / (safeItemCount - 1).coerceAtLeast(1)
    } else 0f
    val animatedThumbFraction by animateFloatAsState(
        targetValue = targetThumbFraction,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
        label = "thumbFraction"
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
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            if (currentDisabled) return@detectDragGestures
                            isEngaged = true
                            isPullingRight = false
                            hasPulledTrigger = false
                            accX = 0f
                            accY = 0f
                        },
                        onDragEnd = {
                            if (currentDisabled) return@detectDragGestures
                            if (hasPulledTrigger) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnPull()
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
                            // Magnetic detent snapping to nearest physical rib detent
                            val target = round(angle / PITCH) * PITCH
                            coroutineScope.launch {
                                angleAnim.snapTo(angle)
                                angleAnim.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 450f)) {
                                    angle = this.value
                                }
                            }
                        },
                        onDragCancel = {
                            if (currentDisabled) return@detectDragGestures
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
                            val target = round(angle / PITCH) * PITCH
                            coroutineScope.launch {
                                angleAnim.snapTo(angle)
                                angleAnim.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 450f)) {
                                    angle = this.value
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (currentDisabled) return@detectDragGestures
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

                            // Infinite rotary scroll: unconstrained physical tracking
                            val dy = dragAmount.y
                            val rawDelta = -dy * 0.020f
                            angle += rawDelta

                            // Continuous multi-point stepped rotary scroll without boundary stop
                            val stepThreshold = 35f
                            while (abs(accY) >= stepThreshold) {
                                val dir = if (accY > 0) 1 else -1
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnStep(dir)
                                accY -= dir * stepThreshold
                            }
                        }
                    )
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

                // Vertical thumb track position indicator
                if (safeItemCount > 1) {
                    val trackHeight = size.height - 24.dp.toPx()
                    val thumbY = 12.dp.toPx() + trackHeight * animatedThumbFraction
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

        val isDark = LocalWalkieDarkTheme.current
        Text(
            text = label,
            fontFamily = SpaceGrotesk,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) WalkieTextSecondary else WalkieTextMuted,
            letterSpacing = 1.sp
        )
    }
}
