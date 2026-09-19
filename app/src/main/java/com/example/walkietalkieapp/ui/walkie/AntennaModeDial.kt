package com.example.walkietalkieapp.ui.walkie

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.walkietalkieapp.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

private const val NUM_TEETH = 8
private const val STEP_RADIANS = (2 * PI / NUM_TEETH).toFloat()

@Composable
fun AntennaModeDial(
    mode: ConnectivityMode,
    onChange: (ConnectivityMode) -> Unit,
    locked: Boolean = false,
    emittingWaves: Boolean = false,
    modifier: Modifier = Modifier
) {
    val modes = remember { listOf(ConnectivityMode.INTERNET, ConnectivityMode.BLUETOOTH, ConnectivityMode.WIFI_DIRECT) }
    val currentTheme = ModeThemes.get(mode)
    val coroutineScope = rememberCoroutineScope()

    // Continuous rotation angle in radians
    val angleAnim = remember { Animatable(modes.indexOf(mode).coerceAtLeast(0) * STEP_RADIANS) }
    var angleOffset by remember { mutableFloatStateOf(modes.indexOf(mode).coerceAtLeast(0) * STEP_RADIANS) }

    // Physical sway and bounce of popup badge and text
    val dragSwayAnim = remember { Animatable(0f) }
    var dragSwayOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(mode) {
        val target = modes.indexOf(mode).coerceAtLeast(0) * STEP_RADIANS
        if (abs(angleOffset - target) > 0.01f) {
            angleAnim.snapTo(angleOffset)
            angleAnim.animateTo(target, spring(dampingRatio = 0.65f, stiffness = 400f)) {
                angleOffset = this.value
            }
        }
    }

    val haptic = LocalHapticFeedback.current

    // Step with lively physical spring bounce and tactile haptics
    fun triggerSingleStep(direction: Int) {
        if (locked) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val currentIdx = modes.indexOf(mode).coerceAtLeast(0)
        val nextIndex = (currentIdx + direction + modes.size) % modes.size
        val newTargetAngle = angleOffset + direction * STEP_RADIANS

        // Bounce popup badge in direction of step and spring back
        coroutineScope.launch {
            dragSwayAnim.snapTo(direction * 22f)
            dragSwayOffset = direction * 22f
            dragSwayAnim.animateTo(0f, spring(dampingRatio = 0.50f, stiffness = 420f)) {
                dragSwayOffset = this.value
            }
        }

        coroutineScope.launch {
            angleAnim.snapTo(angleOffset)
            angleAnim.animateTo(newTargetAngle, spring(dampingRatio = 0.60f, stiffness = 500f)) {
                angleOffset = this.value
            }
        }
        onChange(modes[nextIndex])
    }

    // Radio waves emission from antenna tip when in squad
    val infiniteTransition = rememberInfiniteTransition(label = "antennaWaves")
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "antennaWaveProgress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. STATIONARY ANTENNA ROLLER CHASSIS
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            // Emitting radio waves above tip
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(18.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (emittingWaves) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        for (i in 0 until 2) {
                            val p = (waveProgress + i * 0.5f) % 1f
                            val arcRadius = 6.dp.toPx() + p * 12.dp.toPx()
                            val alpha = (1f - p) * 0.8f
                            drawArc(
                                color = currentTheme.primaryColor.copy(alpha = alpha),
                                startAngle = 210f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(w / 2f - arcRadius, h - arcRadius),
                                size = Size(arcRadius * 2, arcRadius * 2),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }

            // Fixed Roller Cylinder (5mm x 2cm proportions, stationary chassis)
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(85.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF141416),
                                Color(0xFF38383F),
                                Color(0xFF26262B),
                                Color(0xFF141416)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .pointerInput(locked, mode) {
                        if (locked) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var accumulated = 0f
                            var isDrag = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    // Touch released without dragging = single tap step
                                    if (!isDrag) {
                                        triggerSingleStep(1)
                                    }
                                    break
                                }
                                val dragAmount = change.position.x - change.previousPosition.x
                                if (abs(accumulated + dragAmount) > 6f || isDrag) {
                                    isDrag = true
                                    accumulated += dragAmount
                                    change.consume()

                                    // Rotate teeth continuously around vertical cylinder axis
                                    angleOffset += dragAmount * 0.05f

                                    // Subtle spring sway for popup
                                    dragSwayOffset = (dragSwayOffset + dragAmount * 0.4f).coerceIn(-25f, 25f)

                                    // Continuous threshold cycling across all modes
                                    if (accumulated > 18f) {
                                        accumulated = 0f
                                        triggerSingleStep(1)
                                    } else if (accumulated < -18f) {
                                        accumulated = 0f
                                        triggerSingleStep(-1)
                                    }
                                }
                            }

                            // Spring back sway on gesture completion
                            coroutineScope.launch {
                                dragSwayAnim.snapTo(dragSwayOffset)
                                dragSwayAnim.animateTo(0f, spring(dampingRatio = 0.50f, stiffness = 320f)) {
                                    dragSwayOffset = this.value
                                }
                            }
                        }
                    }
            ) {
                // Knurl horizontal grip lines background texture
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val currentAngle = angleOffset

                    // Draw 8 serrated teeth rotating inside the cylinder
                    for (i in 0 until NUM_TEETH) {
                        val baseAngle = i * STEP_RADIANS
                        var theta = (baseAngle - currentAngle) % (2 * PI).toFloat()
                        if (theta > PI) theta -= (2 * PI).toFloat()
                        if (theta < -PI) theta += (2 * PI).toFloat()

                        val cosTheta = cos(theta)
                        // Only draw if on front half of the roller
                        if (cosTheta > 0f) {
                            val xPos = (w / 2f) + sin(theta) * (w * 0.44f)
                            val scaleX = cosTheta.coerceIn(0.15f, 1f)
                            val toothWidth = 2.dp.toPx() * scaleX
                            val opacity = (0.35f + 0.65f * cosTheta).coerceIn(0f, 1f)

                            drawRoundRect(
                                color = Color.White.copy(alpha = opacity * 0.75f),
                                topLeft = Offset(xPos - toothWidth / 2f, 4.dp.toPx()),
                                size = Size(toothWidth, h - 8.dp.toPx()),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                            )
                        }
                    }

                    // Glint reflection sheen line
                    val glintOffset = sin(currentAngle * 2f) * 4.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.20f)
                            )
                        ),
                        topLeft = Offset((w / 2f) + glintOffset - 1.dp.toPx(), 0f),
                        size = Size(2.dp.toPx(), h)
                    )

                    // Detent bead at bottom
                    drawCircle(
                        color = currentTheme.primaryColor,
                        radius = 1.8.dp.toPx(),
                        center = Offset(w / 2f, h - 5.dp.toPx())
                    )
                }
            }

            // Mounting Collar (Fixed steel base)
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(9.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF16161A), Color(0xFF45454E), Color(0xFF1B1B20))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            )
        }

        // 2. GLOWING MODE POPUP BUTTON (Sways and bounces with spring response on GPU)
        var btnPressed by remember { mutableStateOf(false) }
        val btnScale by animateFloatAsState(
            targetValue = if (btnPressed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
            label = "popupBtnScale"
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    val sway = dragSwayOffset
                    translationX = (sway * 0.22f).coerceIn(-8f, 8f).dp.toPx()
                    rotationZ = (sway * 0.08f).coerceIn(-3f, 3f)
                    scaleX = btnScale
                    scaleY = btnScale
                }
                .clip(RoundedCornerShape(18.dp))
                .background(currentTheme.primaryColor)
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .clickable(enabled = !locked) {
                    triggerSingleStep(1)
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Animated Icon Swap
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f)) + fadeIn())
                            .togetherWith(scaleOut(spring(dampingRatio = 0.7f, stiffness = 420f)) + fadeOut())
                    },
                    label = "modeIcon"
                ) { targetMode ->
                    val icon = when (targetMode) {
                        ConnectivityMode.INTERNET -> Icons.Default.Language
                        ConnectivityMode.BLUETOOTH -> Icons.Default.Bluetooth
                        ConnectivityMode.WIFI_DIRECT -> Icons.Default.Wifi
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = targetMode.name,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Bold Black Mode Name (Space Grotesk)
                AnimatedContent(
                    targetState = currentTheme.label,
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 400f)) { it / 2 } + fadeIn())
                            .togetherWith(slideOutVertically(spring(dampingRatio = 0.75f, stiffness = 400f)) { -it / 2 } + fadeOut())
                    },
                    label = "modeLabel"
                ) { labelText ->
                    Text(
                        text = labelText,
                        fontFamily = SpaceGrotesk,
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // 3. DESCRIPTION TEXT & DOTS ON THE RIGHT (Slides smoothly, hidden when locked)
        AnimatedVisibility(
            visible = !locked,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = (dragSwayOffset * 0.45f).coerceIn(-14f, 14f).dp.toPx()
                    }
                    .weight(1f)
                    .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = currentTheme,
                    transitionSpec = {
                        (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = 380f)) { it / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally(spring(dampingRatio = 0.75f, stiffness = 380f)) { -it / 3 } + fadeOut())
                    },
                    label = "modeDetails"
                ) { theme ->
                    Column {
                        Text(
                            text = theme.tag,
                            fontFamily = SpaceGrotesk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1E22),
                            letterSpacing = 0.8.sp,
                            maxLines = 1
                        )
                        Text(
                            text = theme.detail,
                            fontFamily = PlusJakartaSans,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4B4A50),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Mode 3-dots indicator
                val activeIndex = modes.indexOf(mode).coerceAtLeast(0)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    modes.forEachIndexed { index, m ->
                        val isCurrent = index == activeIndex
                        val dotScale by animateFloatAsState(
                            targetValue = if (isCurrent) 1.35f else 1f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                            label = "dotScale"
                        )
                        val dotAlpha by animateFloatAsState(
                            targetValue = if (isCurrent) 1f else 0.3f,
                            label = "dotAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .scale(dotScale)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(ModeThemes.get(m).primaryColor.copy(alpha = dotAlpha))
                        )
                    }
                }
            }
        }
    }
}
