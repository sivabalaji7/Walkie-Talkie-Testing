package com.example.walkietalkieapp.ptt

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HardwarePttManagerTest {

    private var startCallCount = 0
    private var endCallCount = 0

    @Before
    fun setUp() {
        startCallCount = 0
        endCallCount = 0
        HardwarePttManager.forceRelease()
        HardwarePttManager.setVolumeKeyPttEnabled(true)
        HardwarePttManager.setHeadsetPttEnabled(true)
        HardwarePttManager.isInsideActiveSquad = true
        HardwarePttManager.registerPttTrigger(
            onStart = { startCallCount++ },
            onEnd = { endCallCount++ }
        )
    }

    @Test
    fun testVolumeDownKeyPressAndReleaseTriggersPtt() {
        // Press Volume Down
        val handledDown = HardwarePttManager.onHardwareKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0)
        assertTrue("Volume Down key press must be intercepted", handledDown)
        assertEquals(1, startCallCount)
        assertEquals(0, endCallCount)
        assertTrue(HardwarePttManager.isHardwareKeyDown.value)
        assertEquals("VOLUME_DOWN", HardwarePttManager.activeTriggerSource.value)

        // Key repeat while holding should be consumed but not trigger extra starts
        val handledRepeat = HardwarePttManager.onHardwareKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 1)
        assertTrue("Repeat event must be consumed to prevent OS volume slider", handledRepeat)
        assertEquals("Should not fire extra start on key repeat", 1, startCallCount)

        // Release Volume Down
        val handledUp = HardwarePttManager.onHardwareKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertTrue("Volume Down key release must be intercepted", handledUp)
        assertEquals(1, startCallCount)
        assertEquals(1, endCallCount)
        assertFalse(HardwarePttManager.isHardwareKeyDown.value)
        assertNull(HardwarePttManager.activeTriggerSource.value)
    }

    @Test
    fun testIgnoredWhenNotInsideActiveSquad() {
        HardwarePttManager.isInsideActiveSquad = false

        val handled = HardwarePttManager.onHardwareKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0)
        assertFalse("Must not intercept keys when outside active squad", handled)
        assertEquals(0, startCallCount)
        assertFalse(HardwarePttManager.isHardwareKeyDown.value)
    }

    @Test
    fun testIgnoredWhenVolumeKeyPttDisabled() {
        HardwarePttManager.setVolumeKeyPttEnabled(false)

        val handled = HardwarePttManager.onHardwareKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0)
        assertFalse("Must not intercept volume keys when disabled in settings", handled)
        assertEquals(0, startCallCount)
    }

    @Test
    fun testHeadsetMediaButtonPtt() {
        val handledDown = HardwarePttManager.onHardwareKeyDown(KeyEvent.KEYCODE_HEADSETHOOK, repeatCount = 0)
        assertTrue(handledDown)
        assertEquals(1, startCallCount)
        assertEquals("HEADSET", HardwarePttManager.activeTriggerSource.value)

        val handledUp = HardwarePttManager.onHardwareKeyUp(KeyEvent.KEYCODE_HEADSETHOOK)
        assertTrue(handledUp)
        assertEquals(1, endCallCount)
        assertNull(HardwarePttManager.activeTriggerSource.value)
    }

    @Test
    fun testToggleNotificationPtt() {
        assertEquals(0, startCallCount)
        assertEquals(0, endCallCount)

        // First tap starts transmission
        HardwarePttManager.toggleNotificationPtt()
        assertEquals(1, startCallCount)
        assertEquals(0, endCallCount)
        assertEquals("NOTIFICATION", HardwarePttManager.activeTriggerSource.value)
        assertTrue(HardwarePttManager.isHardwareKeyDown.value)

        // Second tap releases transmission
        HardwarePttManager.toggleNotificationPtt()
        assertEquals(1, startCallCount)
        assertEquals(1, endCallCount)
        assertNull(HardwarePttManager.activeTriggerSource.value)
        assertFalse(HardwarePttManager.isHardwareKeyDown.value)
    }

    @Test
    fun testForceRelease() {
        HardwarePttManager.onHardwareKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, 0)
        assertEquals(1, startCallCount)

        HardwarePttManager.forceRelease()
        assertEquals(1, endCallCount)
        assertFalse(HardwarePttManager.isHardwareKeyDown.value)
        assertNull(HardwarePttManager.activeTriggerSource.value)
    }
}
