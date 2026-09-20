package com.example.walkietalkieapp.location

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SquadRadarManagerTest {

    @Before
    fun setUp() {
        SquadRadarManager.clearSquadLocations()
        SquadRadarManager.setSosActive(false)
        SquadRadarManager.setRadarRange(RadarRange.RANGE_500M)
    }

    @Test
    fun testHaversineDistanceCalculation() {
        // Point A: Empire State Building (40.748817, -73.985428)
        // Point B: Chrysler Building (40.751621, -73.975502)
        // Known geodesic distance: ~880 to 900 meters
        val lat1 = 40.748817
        val lon1 = -73.985428
        val lat2 = 40.751621
        val lon2 = -73.975502

        val distance = SquadRadarManager.calculateHaversineDistance(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be approximately 890m (was $distance)", distance in 850f..950f)
    }

    @Test
    fun testTrueBearingCalculation() {
        // From Origin (0,0) to directly North (1, 0) -> bearing should be 0°
        val northBearing = SquadRadarManager.calculateTrueBearing(0.0, 0.0, 1.0, 0.0)
        assertEquals(0f, northBearing, 0.5f)

        // From Origin (0,0) to directly East (0, 1) -> bearing should be 90°
        val eastBearing = SquadRadarManager.calculateTrueBearing(0.0, 0.0, 0.0, 1.0)
        assertEquals(90f, eastBearing, 0.5f)

        // From Origin (0,0) to directly South (-1, 0) -> bearing should be 180°
        val southBearing = SquadRadarManager.calculateTrueBearing(0.0, 0.0, -1.0, 0.0)
        assertEquals(180f, southBearing, 0.5f)

        // From Origin (0,0) to directly West (0, -1) -> bearing should be 270°
        val westBearing = SquadRadarManager.calculateTrueBearing(0.0, 0.0, 0.0, -1.0)
        assertEquals(270f, westBearing, 0.5f)
    }

    @Test
    fun testRelativeBearingAndClockPosition() {
        // User is heading North (0°)
        // Target is East (90° true bearing) -> relative bearing is 90°
        val relEast = SquadRadarManager.calculateRelativeBearing(userHeading = 0f, trueBearing = 90f)
        assertEquals(90f, relEast, 0.1f)
        assertEquals("3 o'clock", SquadRadarManager.bearingToClockPosition(relEast))

        // Target is directly ahead (0° relative) -> 12 o'clock
        val relAhead = SquadRadarManager.calculateRelativeBearing(userHeading = 45f, trueBearing = 45f)
        assertEquals(0f, relAhead, 0.1f)
        assertEquals("12 o'clock", SquadRadarManager.bearingToClockPosition(relAhead))

        // Target is behind (180° relative) -> 6 o'clock
        val relBehind = SquadRadarManager.calculateRelativeBearing(userHeading = 0f, trueBearing = 180f)
        assertEquals(180f, relBehind, 0.1f)
        assertEquals("6 o'clock", SquadRadarManager.bearingToClockPosition(relBehind))

        // Target is to the left (270° relative) -> 9 o'clock
        val relLeft = SquadRadarManager.calculateRelativeBearing(userHeading = 0f, trueBearing = 270f)
        assertEquals(270f, relLeft, 0.1f)
        assertEquals("9 o'clock", SquadRadarManager.bearingToClockPosition(relLeft))
    }

    @Test
    fun testBearingToCardinal() {
        assertEquals("N", SquadRadarManager.bearingToCardinal(0f))
        assertEquals("NE", SquadRadarManager.bearingToCardinal(45f))
        assertEquals("E", SquadRadarManager.bearingToCardinal(90f))
        assertEquals("SE", SquadRadarManager.bearingToCardinal(135f))
        assertEquals("S", SquadRadarManager.bearingToCardinal(180f))
        assertEquals("SW", SquadRadarManager.bearingToCardinal(225f))
        assertEquals("W", SquadRadarManager.bearingToCardinal(270f))
        assertEquals("NW", SquadRadarManager.bearingToCardinal(315f))
        assertEquals("N", SquadRadarManager.bearingToCardinal(358f))
    }

    @Test
    fun testCircularAngleSmoothing() {
        // Normal smoothing from 100° to 120° with alpha 0.5 -> 110°
        val smoothed = SquadRadarManager.smoothDegrees(100f, 120f, 0.5f)
        assertEquals(110f, smoothed, 0.1f)

        // Smoothing across zero-degree boundary: 350° to 10° (shortest path through 0°)
        // Difference is +20°. Target with alpha 0.5 should be 360° (or 0°)
        val smoothedWrap = SquadRadarManager.smoothDegrees(350f, 10f, 0.5f)
        assertTrue("Smoothed wrap should be 0° (was $smoothedWrap)", smoothedWrap == 0f || smoothedWrap == 360f)
    }

    @Test
    fun testSquadMemberLocationIngestionAndDistanceUpdate() {
        // Set operator location at (37.7749, -122.4194) [San Francisco]
        SquadRadarManager.updateMyLocation(37.7749, -122.4194, altitude = 15.0, accuracy = 4.0f)
        assertTrue(SquadRadarManager.isGpsFixAcquired.value)
        assertNotNull(SquadRadarManager.myLocation.value)

        // Ingest peer "BRAVO-LEADER" ~100m away
        val peerLat = 37.7758
        val peerLon = -122.4194
        SquadRadarManager.updatePeerLocation("BRAVO-LEADER", peerLat, peerLon, altitude = 16.0, isSos = false)

        val squadMap = SquadRadarManager.squadLocations.value
        assertTrue(squadMap.containsKey("BRAVO-LEADER"))
        val bravo = squadMap["BRAVO-LEADER"]!!
        assertEquals("BRAVO-LEADER", bravo.callsign)
        assertTrue("Distance should be around 100m (was ${bravo.distanceMeters})", bravo.distanceMeters in 80f..120f)
        assertEquals("N", bravo.cardinalDirection)

        // Remove peer
        SquadRadarManager.removePeerLocation("BRAVO-LEADER")
        assertFalse(SquadRadarManager.squadLocations.value.containsKey("BRAVO-LEADER"))
    }

    @Test
    fun testSosDistressBeaconActivation() {
        assertFalse(SquadRadarManager.isSosActive.value)
        SquadRadarManager.setSosActive(true)
        assertTrue(SquadRadarManager.isSosActive.value)

        SquadRadarManager.setSosActive(false)
        assertFalse(SquadRadarManager.isSosActive.value)
    }

    @Test
    fun testRadarRangeScaleSelection() {
        assertEquals(RadarRange.RANGE_500M, SquadRadarManager.radarRange.value)
        SquadRadarManager.setRadarRange(RadarRange.RANGE_1KM)
        assertEquals(RadarRange.RANGE_1KM, SquadRadarManager.radarRange.value)
        assertEquals(1000f, SquadRadarManager.radarRange.value.radiusMeters, 0.1f)
    }
}
