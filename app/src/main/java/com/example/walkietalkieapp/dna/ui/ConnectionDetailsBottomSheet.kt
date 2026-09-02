package com.example.walkietalkieapp.dna.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.dna.presentation.CommunicationPresentationState

/**
 * Level 2 (Context) & Level 3 (Advanced Diagnostics) Progressive Disclosure Bottom Sheet.
 * Translates low-level DNA metrics into clear human explanations while keeping deep telemetry available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailsBottomSheet(
    presentationState: CommunicationPresentationState,
    onDismissRequest: () -> Unit
) {
    var showAdvancedDiagnostics by remember { mutableStateOf(false) }
    val raw = presentationState.rawAssessment

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF14161B),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title & Transport Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Connection Quality",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = presentationState.transportName,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = presentationState.primaryAccentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, presentationState.primaryAccentColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        SignalStrengthBars(
                            strengthLevel = presentationState.strengthLevel,
                            activeColor = presentationState.primaryAccentColor,
                            maxHeight = 12.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = presentationState.qualityLevel.label.uppercase(),
                            color = presentationState.primaryAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Level 2: Human-Facing Status Summary Card
            Surface(
                color = Color(0xFF1E2126),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E333D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(presentationState.primaryAccentColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = presentationState.headlineStatus,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = presentationState.supportingMessage,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• ${presentationState.stabilitySummary}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Level 2: Voice Readiness Card
            Surface(
                color = Color(0xFF1E2126),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E333D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Voice Readiness",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Optimized for low-latency Push-to-Talk",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${raw.roleSuitability.voiceScore}/100",
                        color = presentationState.primaryAccentColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Level 3: Toggle for Advanced Technical Diagnostics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showAdvancedDiagnostics = !showAdvancedDiagnostics }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Technical Diagnostics",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = if (showAdvancedDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = showAdvancedDiagnostics,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C0E12))
                        .padding(12.dp)
                ) {
                    DiagnosticRow(label = "Internal DNA Score", value = "${raw.overallQualityScore}/100")
                    DiagnosticRow(
                        label = "Observed Latency (RTT)",
                        value = raw.latencyMs.valueOrNull()?.let { "${it} ms" } ?: "Standby"
                    )
                    DiagnosticRow(
                        label = "Estimated Packet Loss",
                        value = raw.packetLossPercent.valueOrNull()?.let { String.format("%.1f%%", it) } ?: "0.0%"
                    )
                    DiagnosticRow(
                        label = "Bandwidth Capacity",
                        value = raw.bandwidthKbps.valueOrNull()?.let { "${it} kbps" } ?: "N/A"
                    )
                    DiagnosticRow(label = "Link Stability Rating", value = "${raw.stabilityScore}/100")
                    DiagnosticRow(label = "Assessment Confidence", value = presentationState.confidenceLevel.label)
                    DiagnosticRow(label = "Trend Direction", value = raw.trend.description)
                    DiagnosticRow(label = "Battery Impact Cost", value = raw.batteryImpact.label)
                    if (raw.disconnectCountLastMinute > 0) {
                        DiagnosticRow(
                            label = "Flapping Disconnects (60s)",
                            value = "${raw.disconnectCountLastMinute} event(s)",
                            valueColor = Color(0xFFFF5252)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.Gray, fontSize = 11.sp)
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
