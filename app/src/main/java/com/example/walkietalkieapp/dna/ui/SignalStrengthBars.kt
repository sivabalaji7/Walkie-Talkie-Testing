package com.example.walkietalkieapp.dna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.walkietalkieapp.dna.presentation.StrengthLevel

/**
 * Custom 4-bar segmented signal strength indicator.
 * Renders cleanly at 60 Hz with zero expensive canvas/shader operations.
 */
@Composable
fun SignalStrengthBars(
    strengthLevel: StrengthLevel,
    activeColor: Color,
    inactiveColor: Color = Color.White.copy(alpha = 0.15f),
    maxHeight: Dp = 14.dp,
    barWidth: Dp = 3.dp,
    spacing: Dp = 2.dp,
    modifier: Modifier = Modifier
) {
    val barHeights = listOf(
        0.35f, // Bar 1
        0.55f, // Bar 2
        0.80f, // Bar 3
        1.00f  // Bar 4
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        barHeights.forEachIndexed { index, heightFraction ->
            val isActive = index < strengthLevel.activeSegments
            Box(
                modifier = Modifier
                    .fillMaxHeight(heightFraction)
                    .width(barWidth)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isActive) activeColor else inactiveColor)
            )
        }
    }
}
