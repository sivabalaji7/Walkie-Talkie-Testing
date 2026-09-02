package com.example.walkietalkieapp.dna.valour

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import com.example.walkietalkieapp.dna.ui.SignalStrengthBars

/**
 * Valour Connection Details Sheet (Level 2 Context + Level 3 Advanced Diagnostics)
 * Progressive disclosure bottom sheet balancing human-facing clarity and deep telemetry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValourConnectionDetailsSheet(
    linkState: ValourLinkState,
    onDismissRequest: () -> Unit
) {
    var showAdvancedDiagnostics by remember { mutableStateOf(false) }
    val diag = linkState.diagnostics

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
                        text = linkState.transportName,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = linkState.accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, linkState.accentColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        SignalStrengthBars(
                            strengthLevel = linkState.strengthLevel,
                            activeColor = linkState.accentColor,
                            maxHeight = 12.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = linkState.qualityLevel.label.uppercase(),
                            color = linkState.accentColor,
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
                                .background(linkState.accentColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = linkState.headlineText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = linkState.subheadText,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• ${linkState.accessibilityDescription}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Level 2: Voice Readiness Card (with subordinate secondary score)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Readiness",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = linkState.voiceReadiness.description,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${linkState.voiceReadinessScore}/100",
                        color = linkState.accentColor,
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
                    DiagnosticRow(label = "Connection Quality", value = "${diag.dnaScore}/100")
                    DiagnosticRow(
                        label = "Latency",
                        value = diag.rttMs?.let { "${it} ms" } ?: "Not measured"
                    )
                    DiagnosticRow(
                        label = "Packet Loss",
                        value = diag.packetLossPercent?.let { String.format("%.1f%%", it) } ?: "Not measured"
                    )
                    DiagnosticRow(
                        label = "Bandwidth Capacity",
                        value = diag.bandwidthKbps?.let { "${it} kbps" } ?: "N/A"
                    )
                    DiagnosticRow(label = "Link Stability", value = "${diag.stabilityScore}/100")
                    DiagnosticRow(label = "Confidence", value = diag.confidenceLevel)
                    DiagnosticRow(label = "Connection Trend", value = diag.trendDescription)
                    DiagnosticRow(label = "Battery Impact", value = diag.batteryImpact)
                    if (diag.disconnectCountLastMinute > 0) {
                        DiagnosticRow(
                            label = "Flapping Events (60s)",
                            value = "${diag.disconnectCountLastMinute} event(s)",
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
