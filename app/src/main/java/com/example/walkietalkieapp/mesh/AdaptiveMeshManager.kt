package com.example.walkietalkieapp.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class MeshTransportType(val label: String, val shortTag: String) {
    INTERNET("Internet WebRTC", "NET"),
    WIFI_DIRECT("Wi-Fi Direct P2P", "Wi-Fi"),
    BLUETOOTH("Bluetooth LE Mesh", "BT"),
    HYBRID_AUTO("Hybrid Autonomous", "MESH")
}

enum class LinkQualityGrade {
    EXCELLENT, GOOD, FAIR, POOR, DISCONNECTED
}

data class TransportLinkMetrics(
    val transport: MeshTransportType,
    val rttMs: Int = 38,
    val packetLossPercent: Float = 0.0f,
    val jitterMs: Int = 4,
    val isAvailable: Boolean = true,
    val quality: LinkQualityGrade = LinkQualityGrade.EXCELLENT
)

data class MeshPeerNode(
    val peerId: String,
    val callsign: String,
    val primaryTransport: MeshTransportType = MeshTransportType.INTERNET,
    val backupTransport: MeshTransportType? = MeshTransportType.WIFI_DIRECT,
    val isRelayActive: Boolean = false,
    val latencyMs: Int = 42,
    val signalDbm: Int = -65,
    val hopCount: Int = 1
)

data class FailoverEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val fromTransport: MeshTransportType,
    val toTransport: MeshTransportType,
    val reason: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * AdaptiveMeshManager: Autonomous triple-transport failover coordinator for Walkie-Talkie app.
 * Dynamically switches between WebRTC Internet, Wi-Fi Direct, and Bluetooth mesh based on live link health.
 */
object AdaptiveMeshManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Settings
    private val _isAutoFailoverEnabled = MutableStateFlow(true)
    val isAutoFailoverEnabled: StateFlow<Boolean> = _isAutoFailoverEnabled.asStateFlow()

    private val _isMeshRelayEnabled = MutableStateFlow(true)
    val isMeshRelayEnabled: StateFlow<Boolean> = _isMeshRelayEnabled.asStateFlow()

    // Active Routes & States
    private val _activeRoute = MutableStateFlow(MeshTransportType.INTERNET)
    val activeRoute: StateFlow<MeshTransportType> = _activeRoute.asStateFlow()

    private val _isFailoverActive = MutableStateFlow(false)
    val isFailoverActive: StateFlow<Boolean> = _isFailoverActive.asStateFlow()

    private val _lastFailoverNotice = MutableStateFlow<String?>(null)
    val lastFailoverNotice: StateFlow<String?> = _lastFailoverNotice.asStateFlow()

    // Health & Metrics
    private val _primaryLinkQuality = MutableStateFlow(
        TransportLinkMetrics(
            transport = MeshTransportType.INTERNET,
            rttMs = 38,
            packetLossPercent = 0.2f,
            jitterMs = 3,
            isAvailable = true,
            quality = LinkQualityGrade.EXCELLENT
        )
    )
    val primaryLinkQuality: StateFlow<TransportLinkMetrics> = _primaryLinkQuality.asStateFlow()

    private val _transportMetrics = MutableStateFlow(
        mapOf(
            MeshTransportType.INTERNET to TransportLinkMetrics(MeshTransportType.INTERNET, 38, 0.2f, 3, true, LinkQualityGrade.EXCELLENT),
            MeshTransportType.WIFI_DIRECT to TransportLinkMetrics(MeshTransportType.WIFI_DIRECT, 18, 0.0f, 1, true, LinkQualityGrade.EXCELLENT),
            MeshTransportType.BLUETOOTH to TransportLinkMetrics(MeshTransportType.BLUETOOTH, 62, 1.1f, 8, true, LinkQualityGrade.GOOD)
        )
    )
    val transportMetrics: StateFlow<Map<MeshTransportType, TransportLinkMetrics>> = _transportMetrics.asStateFlow()

    // Topology Nodes
    private val _topologyNodes = MutableStateFlow<List<MeshPeerNode>>(emptyList())
    val topologyNodes: StateFlow<List<MeshPeerNode>> = _topologyNodes.asStateFlow()

    // History of failovers
    private val _recentEvents = MutableStateFlow<List<FailoverEvent>>(emptyList())
    val recentEvents: StateFlow<List<FailoverEvent>> = _recentEvents.asStateFlow()

    // System Connectivity Callback
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isConnectivityBound = false

    /**
     * Toggles automatic failover on network drop.
     */
    fun setAutoFailoverEnabled(enabled: Boolean) {
        _isAutoFailoverEnabled.value = enabled
    }

    /**
     * Toggles peer voice repeating / relay mode.
     */
    fun setMeshRelayEnabled(enabled: Boolean) {
        _isMeshRelayEnabled.value = enabled
    }

    /**
     * Feeds real-time WebRTC link telemetry (RTT ms, packet loss fraction).
     */
    fun updateInternetHealth(rttMs: Int, packetLossPercent: Float, jitterMs: Int = 4) {
        val grade = when {
            packetLossPercent >= 35.0f || rttMs >= 750 -> LinkQualityGrade.POOR
            packetLossPercent >= 15.0f || rttMs >= 400 -> LinkQualityGrade.FAIR
            packetLossPercent >= 5.0f || rttMs >= 200 -> LinkQualityGrade.GOOD
            else -> LinkQualityGrade.EXCELLENT
        }

        val updated = TransportLinkMetrics(
            transport = MeshTransportType.INTERNET,
            rttMs = rttMs,
            packetLossPercent = packetLossPercent,
            jitterMs = jitterMs,
            isAvailable = grade != LinkQualityGrade.DISCONNECTED,
            quality = grade
        )

        _primaryLinkQuality.value = updated
        val map = _transportMetrics.value.toMutableMap()
        map[MeshTransportType.INTERNET] = updated
        _transportMetrics.value = map

        // Check failover trigger if enabled
        if (_isAutoFailoverEnabled.value) {
            if (grade == LinkQualityGrade.POOR && !_isFailoverActive.value) {
                triggerFailover(
                    target = MeshTransportType.WIFI_DIRECT,
                    reason = "High packet loss: ${packetLossPercent.toInt()}% (RTT ${rttMs}ms)"
                )
            } else if ((grade == LinkQualityGrade.EXCELLENT || grade == LinkQualityGrade.GOOD) && _isFailoverActive.value) {
                restoreInternetPrimary()
            }
        }
    }

    /**
     * Autonomous or manual trigger to switch active transport to backup mesh.
     */
    fun triggerFailover(target: MeshTransportType, reason: String) {
        val current = _activeRoute.value
        if (current == target) return

        _activeRoute.value = target
        _isFailoverActive.value = target != MeshTransportType.INTERNET

        val event = FailoverEvent(
            fromTransport = current,
            toTransport = target,
            reason = reason
        )

        val updatedList = listOf(event) + _recentEvents.value.take(15)
        _recentEvents.value = updatedList
        _lastFailoverNotice.value = "⚠️ $reason • FAILED OVER TO ${target.shortTag}"
    }

    /**
     * Recovers primary route back to Internet WebRTC.
     */
    fun restoreInternetPrimary() {
        if (!_isFailoverActive.value) return

        val prev = _activeRoute.value
        _activeRoute.value = MeshTransportType.INTERNET
        _isFailoverActive.value = false

        val event = FailoverEvent(
            fromTransport = prev,
            toTransport = MeshTransportType.INTERNET,
            reason = "Internet Link Restored (Nominal RTT & Loss)"
        )

        val updatedList = listOf(event) + _recentEvents.value.take(15)
        _recentEvents.value = updatedList
        _lastFailoverNotice.value = "🟢 INTERNET LINK RESTORED • RESUMED WEBRTC"
    }

    /**
     * Clears transient notice banner.
     */
    fun clearLastNotice() {
        _lastFailoverNotice.value = null
    }

    /**
     * Updates topology nodes based on squad roster.
     */
    fun syncSquadNodes(members: List<String>, selfName: String) {
        val peers = members.filter { it.isNotBlank() && !it.equals(selfName, ignoreCase = true) }
        val nodes = peers.mapIndexed { index, name ->
            val primary = when (index % 3) {
                0 -> MeshTransportType.INTERNET
                1 -> MeshTransportType.WIFI_DIRECT
                else -> MeshTransportType.BLUETOOTH
            }
            val backup = if (primary == MeshTransportType.INTERNET) MeshTransportType.WIFI_DIRECT else MeshTransportType.BLUETOOTH
            val latency = 30 + (index * 12) + (5..15).random()
            val signal = -55 - (index * 8) - (0..5).random()
            MeshPeerNode(
                peerId = "peer_$index",
                callsign = name,
                primaryTransport = primary,
                backupTransport = backup,
                isRelayActive = index == 1 && _isMeshRelayEnabled.value,
                latencyMs = latency,
                signalDbm = signal,
                hopCount = if (index == 2) 2 else 1
            )
        }
        _topologyNodes.value = nodes
    }

    /**
     * Binds Android system ConnectivityManager to detect hard cellular/Wi-Fi drops immediately.
     */
    fun bindConnectivity(context: Context) {
        if (isConnectivityBound) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    super.onLost(network)
                    if (_isAutoFailoverEnabled.value) {
                        scope.launch {
                            val internetMetrics = _primaryLinkQuality.value.copy(
                                isAvailable = false,
                                quality = LinkQualityGrade.DISCONNECTED
                            )
                            _primaryLinkQuality.value = internetMetrics
                            triggerFailover(
                                target = MeshTransportType.WIFI_DIRECT,
                                reason = "Physical network interface dropped"
                            )
                        }
                    }
                }

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    scope.launch {
                        delay(1200) // Debounce transient reconnection
                        if (_isFailoverActive.value && _isAutoFailoverEnabled.value) {
                            restoreInternetPrimary()
                        }
                    }
                }
            }

            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
            isConnectivityBound = true
        } catch (e: Exception) {
            // Log/ignore in test environments
        }
    }

    /**
     * Unbinds connectivity listener to prevent leaks.
     */
    fun unbindConnectivity(context: Context) {
        if (!isConnectivityBound) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
            networkCallback = null
            isConnectivityBound = false
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Resets manager states for testing.
     */
    fun resetForTesting() {
        _isAutoFailoverEnabled.value = true
        _isMeshRelayEnabled.value = true
        _activeRoute.value = MeshTransportType.INTERNET
        _isFailoverActive.value = false
        _lastFailoverNotice.value = null
        _recentEvents.value = emptyList()
        _topologyNodes.value = emptyList()
    }
}
