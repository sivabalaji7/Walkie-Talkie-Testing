package com.example.walkietalkieapp.mesh

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AdaptiveMeshManagerTest {

    @Before
    fun setUp() {
        AdaptiveMeshManager.resetForTesting()
    }

    @Test
    fun testInitialDefaultState() {
        assertTrue("Auto failover should be enabled by default", AdaptiveMeshManager.isAutoFailoverEnabled.value)
        assertTrue("Mesh relay should be enabled by default", AdaptiveMeshManager.isMeshRelayEnabled.value)
        assertEquals("Initial active route should be INTERNET", MeshTransportType.INTERNET, AdaptiveMeshManager.activeRoute.value)
        assertFalse("Failover should not be active initially", AdaptiveMeshManager.isFailoverActive.value)
        assertTrue("Recent events list should be empty initially", AdaptiveMeshManager.recentEvents.value.isEmpty())
    }

    @Test
    fun testInternetHealthUpdate_nominalQuality() {
        AdaptiveMeshManager.updateInternetHealth(rttMs = 45, packetLossPercent = 0.5f, jitterMs = 3)
        val metrics = AdaptiveMeshManager.primaryLinkQuality.value
        assertEquals("Quality should be EXCELLENT", LinkQualityGrade.EXCELLENT, metrics.quality)
        assertEquals(45, metrics.rttMs)
        assertEquals(0.5f, metrics.packetLossPercent, 0.01f)
        assertEquals("Active route should remain INTERNET", MeshTransportType.INTERNET, AdaptiveMeshManager.activeRoute.value)
        assertFalse(AdaptiveMeshManager.isFailoverActive.value)
    }

    @Test
    fun testInternetHealthUpdate_triggersAutoFailoverOnDegradation() {
        // High packet loss and latency
        AdaptiveMeshManager.updateInternetHealth(rttMs = 820, packetLossPercent = 38.0f, jitterMs = 25)
        val metrics = AdaptiveMeshManager.primaryLinkQuality.value
        assertEquals("Quality should be POOR", LinkQualityGrade.POOR, metrics.quality)
        assertTrue("Failover should be active", AdaptiveMeshManager.isFailoverActive.value)
        assertEquals("Active route should failover to WIFI_DIRECT", MeshTransportType.WIFI_DIRECT, AdaptiveMeshManager.activeRoute.value)
        assertTrue("Event should be recorded", AdaptiveMeshManager.recentEvents.value.isNotEmpty())
        assertNotNull(AdaptiveMeshManager.lastFailoverNotice.value)
    }

    @Test
    fun testInternetHealthUpdate_autoRecoversWhenInternetRestored() {
        // Trigger failover
        AdaptiveMeshManager.updateInternetHealth(rttMs = 900, packetLossPercent = 45.0f)
        assertTrue(AdaptiveMeshManager.isFailoverActive.value)

        // Internet recovers
        AdaptiveMeshManager.updateInternetHealth(rttMs = 35, packetLossPercent = 0.1f)
        assertFalse("Failover should recover when link quality is good", AdaptiveMeshManager.isFailoverActive.value)
        assertEquals("Active route should be back to INTERNET", MeshTransportType.INTERNET, AdaptiveMeshManager.activeRoute.value)
    }

    @Test
    fun testAutoFailoverDisabled_doesNotTrigger() {
        AdaptiveMeshManager.setAutoFailoverEnabled(false)
        assertFalse(AdaptiveMeshManager.isAutoFailoverEnabled.value)

        AdaptiveMeshManager.updateInternetHealth(rttMs = 999, packetLossPercent = 50.0f)
        assertEquals("Active route should stay INTERNET when auto-failover disabled", MeshTransportType.INTERNET, AdaptiveMeshManager.activeRoute.value)
        assertFalse(AdaptiveMeshManager.isFailoverActive.value)
    }

    @Test
    fun testManualFailoverAndRecovery() {
        AdaptiveMeshManager.triggerFailover(MeshTransportType.BLUETOOTH, "Tactical stealth radio silence requested")
        assertTrue(AdaptiveMeshManager.isFailoverActive.value)
        assertEquals(MeshTransportType.BLUETOOTH, AdaptiveMeshManager.activeRoute.value)

        AdaptiveMeshManager.restoreInternetPrimary()
        assertFalse(AdaptiveMeshManager.isFailoverActive.value)
        assertEquals(MeshTransportType.INTERNET, AdaptiveMeshManager.activeRoute.value)
    }

    @Test
    fun testSyncSquadNodesTelemetry() {
        val squad = listOf("Ghost", "Viper", "Echo-1", "Siva")
        AdaptiveMeshManager.syncSquadNodes(squad, selfName = "Siva")

        val nodes = AdaptiveMeshManager.topologyNodes.value
        assertEquals("Should have 3 peer nodes excluding self", 3, nodes.size)
        assertTrue(nodes.any { it.callsign == "Ghost" })
        assertTrue(nodes.any { it.callsign == "Viper" })
        assertTrue(nodes.any { it.callsign == "Echo-1" })
    }
}
