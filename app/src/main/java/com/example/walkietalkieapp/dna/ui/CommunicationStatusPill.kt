package com.example.walkietalkieapp.dna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.dna.presentation.CommunicationPresentationState

/**
 * Level 1 (Room Glance): Compact communication status pill for active room headers.
 * Minimalist, glanceable, and non-intrusive to the Push-to-Talk voice experience.
 */
@Composable
fun CommunicationStatusPill(
    presentationState: CommunicationPresentationState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF14161B),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, presentationState.primaryAccentColor.copy(alpha = 0.35f)),
        modifier = modifier.semantics {
            contentDescription = presentationState.accessibilityDescription
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Signal dot indicator
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(presentationState.primaryAccentColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // 4-Bar visual strength indicator
            SignalStrengthBars(
                strengthLevel = presentationState.strengthLevel,
                activeColor = presentationState.primaryAccentColor,
                maxHeight = 10.dp,
                barWidth = 2.5.dp,
                spacing = 1.5.dp
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Semantic status text
            Text(
                text = presentationState.pillBadgeText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}
