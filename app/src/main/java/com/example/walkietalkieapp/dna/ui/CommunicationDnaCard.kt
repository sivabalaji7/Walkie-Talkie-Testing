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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walkietalkieapp.dna.model.*

/**
 * Tactical, glanceable Communication DNA network intelligence card.
 * Integrates seamlessly with the Walkie-Talkie dark tactical aesthetic without looking like a tech demo.
 */
@Composable
fun CommunicationDnaCard(
    assessment: CommunicationPathAssessment,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val qualityColor = when {
        !assessment.isAvailable -> Color.Gray
        !assessment.isConnected -> Color(0xFFFFA000)
        assessment.overallQualityScore >= 80 -> Color(0xFF00FF66)
        assessment.overallQualityScore >= 50 -> Color(0xFF00E5FF)
        else -> Color(0xFFFF5252)
    }

    val trendIcon = when (assessment.trend) {
        TrendDirection.IMPROVING -> Icons.Filled.TrendingUp
        TrendDirection.DEGRADING -> Icons.Filled.TrendingDown
        TrendDirection.VOLATILE -> Icons.Default.Warning
        else -> Icons.Filled.TrendingFlat
    }

    val trendColor = when (assessment.trend) {
        TrendDirection.IMPROVING -> Color(0xFF00FF66)
        TrendDirection.DEGRADING -> Color(0xFFFF5252)
        TrendDirection.VOLATILE -> Color(0xFFFFA000)
        else -> Color.White.copy(alpha = 0.7f)
    }

    Surface(
        onClick = { isExpanded = !isExpanded },
        color = Color(0xFF14161B),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262B35)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Transport Name, Quality Score Pill, Expand Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(qualityColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = assessment.transportType.displayName.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = qualityColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, qualityColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (assessment.isConnected) "DNA: ${assessment.overallQualityScore}" else "OFFLINE",
                                color = qualityColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            if (assessment.isConnected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = trendIcon,
                                    contentDescription = null,
                                    tint = trendColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Telemetry",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Summary Sub-line: Confidence & Latency
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val latencyText = assessment.latencyMs.valueOrNull()?.let { "${it}ms RTT" } ?: "Standby"
                Text(
                    text = if (assessment.isConnected) "$latencyText • ${assessment.trend.description}" else "Path is inactive",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = assessment.confidenceLevel.label,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Expandable Detailed Telemetry Drawer
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C0E12))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "DIAGNOSTIC METRICS",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    TelemetryMetricRow(label = "Voice Suitability", value = "${assessment.roleSuitability.voiceScore}/100")
                    TelemetryMetricRow(label = "Link Stability", value = "${assessment.stabilityScore}/100")
                    TelemetryMetricRow(label = "Battery Impact", value = assessment.batteryImpact.label)
                    TelemetryMetricRow(
                        label = "Estimated Packet Loss",
                        value = assessment.packetLossPercent.valueOrNull()?.let { String.format("%.1f%%", it) } ?: "N/A"
                    )
                    TelemetryMetricRow(
                        label = "Bandwidth Capacity",
                        value = assessment.bandwidthKbps.valueOrNull()?.let { "${it} kbps" } ?: "N/A"
                    )
                    if (assessment.disconnectCountLastMinute > 0) {
                        TelemetryMetricRow(
                            label = "Flapping Disconnects (60s)",
                            value = "${assessment.disconnectCountLastMinute} drop(s)",
                            valueColor = Color(0xFFFF5252)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.Gray, fontSize = 11.sp)
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
