package com.example.walkietalkieapp.ptt

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages physical device hardware buttons (Volume Down / Headset / Media keys)
 * as tactile push-to-talk triggers, allowing eyes-free communication without touching the screen.
 */
object HardwarePttManager {

    private val _isVolumeKeyPttEnabled = MutableStateFlow(true)
    val isVolumeKeyPttEnabled: StateFlow<Boolean> = _isVolumeKeyPttEnabled.asStateFlow()

    private val _isHeadsetPttEnabled = MutableStateFlow(true)
    val isHeadsetPttEnabled: StateFlow<Boolean> = _isHeadsetPttEnabled.asStateFlow()

    private val _isHardwareKeyDown = MutableStateFlow(false)
    val isHardwareKeyDown: StateFlow<Boolean> = _isHardwareKeyDown.asStateFlow()

    private val _activeTriggerSource = MutableStateFlow<String?>(null)
    val activeTriggerSource: StateFlow<String?> = _activeTriggerSource.asStateFlow()

    private var onPttStartCallback: (() -> Unit)? = null
    private var onPttEndCallback: (() -> Unit)? = null

    @Volatile
    var isInsideActiveSquad: Boolean = false

    /**
     * Registers listener callbacks for starting and ending PTT transmissions.
     */
    fun registerPttTrigger(onStart: () -> Unit, onEnd: () -> Unit) {
        this.onPttStartCallback = onStart
        this.onPttEndCallback = onEnd
    }

    fun unregisterPttTrigger() {
        this.onPttStartCallback = null
        this.onPttEndCallback = null
    }

    fun setVolumeKeyPttEnabled(enabled: Boolean) {
        _isVolumeKeyPttEnabled.value = enabled
        if (!enabled && _isHardwareKeyDown.value) {
            forceRelease()
        }
    }

    fun setHeadsetPttEnabled(enabled: Boolean) {
        _isHeadsetPttEnabled.value = enabled
        if (!enabled && _isHardwareKeyDown.value) {
            forceRelease()
        }
    }

    /**
     * Handles hardware key down events. Returns true if the key was intercepted as a PTT trigger.
     */
    fun onHardwareKeyDown(keyCode: Int, repeatCount: Int): Boolean {
        if (!isInsideActiveSquad) return false

        val isVolDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val isHeadset = keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_STOP

        if (isVolDown && _isVolumeKeyPttEnabled.value) {
            _isHardwareKeyDown.value = true
            if (repeatCount == 0 && _activeTriggerSource.value == null) {
                _activeTriggerSource.value = "VOLUME_DOWN"
                onPttStartCallback?.invoke()
            }
            return true
        }

        if (isHeadset && _isHeadsetPttEnabled.value) {
            _isHardwareKeyDown.value = true
            if (repeatCount == 0) {
                // Headset buttons can act as a press-and-hold or quick toggle
                if (_activeTriggerSource.value == null) {
                    _activeTriggerSource.value = "HEADSET"
                    onPttStartCallback?.invoke()
                }
            }
            return true
        }

        return false
    }

    /**
     * Handles hardware key up events. Returns true if the key was intercepted as a PTT trigger.
     */
    fun onHardwareKeyUp(keyCode: Int): Boolean {
        val isVolDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val isHeadset = keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_STOP

        if (isVolDown && _isVolumeKeyPttEnabled.value) {
            _isHardwareKeyDown.value = false
            if (_activeTriggerSource.value == "VOLUME_DOWN") {
                _activeTriggerSource.value = null
                onPttEndCallback?.invoke()
            }
            return true
        }

        if (isHeadset && _isHeadsetPttEnabled.value) {
            _isHardwareKeyDown.value = false
            if (_activeTriggerSource.value == "HEADSET") {
                _activeTriggerSource.value = null
                onPttEndCallback?.invoke()
            }
            return true
        }

        return false
    }

    /**
     * Toggles PTT transmission from a background notification action or lock-screen intent.
     */
    fun toggleNotificationPtt() {
        if (!isInsideActiveSquad) return

        if (_activeTriggerSource.value == "NOTIFICATION") {
            _activeTriggerSource.value = null
            _isHardwareKeyDown.value = false
            onPttEndCallback?.invoke()
        } else {
            _activeTriggerSource.value = "NOTIFICATION"
            _isHardwareKeyDown.value = true
            onPttStartCallback?.invoke()
        }
    }

    fun forceRelease() {
        if (_activeTriggerSource.value != null) {
            _activeTriggerSource.value = null
            _isHardwareKeyDown.value = false
            onPttEndCallback?.invoke()
        }
    }
}
