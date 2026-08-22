package com.example.walkietalkieapp.floor

import android.os.CountDownTimer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "FloorManager"

object FloorManager {

    private val _floorStatus = MutableStateFlow(FloorStatus())
    val floorStatus: StateFlow<FloorStatus> = _floorStatus.asStateFlow()

    // Callbacks wired by MainActivity
    var onFloorGranted: (() -> Unit)? = null
    var onFloorDenied: ((reason: String, speakerName: String) -> Unit)? = null
    var onFloorRevoked: (() -> Unit)? = null
    var onFloorReleased: (() -> Unit)? = null
    var onFloorWarning: (() -> Unit)? = null
    var onFloorTimeout: (() -> Unit)? = null

    private var transmitTimer: CountDownTimer? = null
    private var fallbackTimer: android.os.Handler? = null
    private var fallbackRunnable: Runnable? = null

    /**
     * Called when user presses PTT.
     * Transitions to REQUESTING and waits for server grant.
     * If server doesn't respond in 500ms, grants locally (backward compat).
     */
    fun requestFloor(isPriority: Boolean = false) {
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return
        if (_floorStatus.value.state == FloorState.BUSY_BLOCKED) return

        Log.d(TAG, "Requesting floor (isPriority=$isPriority)...")
        _floorStatus.update { it.copy(state = FloorState.REQUESTING) }

        // Emit request to signaling server
        com.example.walkietalkieapp.socket.SocketManager.emitRequestFloor(isPriority)

        // Fallback: if server doesn't respond in 500ms, grant locally
        fallbackTimer = android.os.Handler(android.os.Looper.getMainLooper())
        fallbackRunnable = Runnable {
            if (_floorStatus.value.state == FloorState.REQUESTING) {
                Log.d(TAG, "Fallback: server did not respond, granting locally")
                handleFloorGranted(System.currentTimeMillis() + 20000)
            }
        }
        fallbackTimer?.postDelayed(fallbackRunnable!!, 500)
    }

    /**
     * Called when user releases PTT.
     */
    fun releaseFloor() {
        cancelFallback()
        cancelTransmitTimer()

        // Always emit release to server if we were requesting or transmitting
        com.example.walkietalkieapp.socket.SocketManager.emitReleaseFloor()

        if (_floorStatus.value.state == FloorState.TRANSMITTING || _floorStatus.value.state == FloorState.REQUESTING) {
            Log.d(TAG, "Releasing floor")
            _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
            onFloorReleased?.invoke()
        }
    }

    /**
     * Server granted floor to this user.
     */
    fun handleFloorGranted(expiresAt: Long) {
        cancelFallback()
        Log.d(TAG, "Floor GRANTED")

        _floorStatus.update {
            FloorStatus(
                state = FloorState.TRANSMITTING,
                isLocalUserSpeaking = true,
                expiresAt = expiresAt
            )
        }

        startTransmitTimer(expiresAt)
        onFloorGranted?.invoke()
    }

    /**
     * Server denied floor — someone else is speaking.
     */
    fun handleFloorDenied(reason: String, currentSpeakerName: String) {
        cancelFallback()
        Log.d(TAG, "Floor DENIED: $reason (speaker: $currentSpeakerName)")

        _floorStatus.update {
            FloorStatus(
                state = FloorState.BUSY_BLOCKED,
                currentSpeakerName = currentSpeakerName
            )
        }

        onFloorDenied?.invoke(reason, currentSpeakerName)

        // Auto-reset to IDLE after 2 seconds so user can retry
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (_floorStatus.value.state == FloorState.BUSY_BLOCKED) {
                _floorStatus.update { it.copy(state = FloorState.IDLE) }
            }
        }, 2000)
    }

    /**
     * Server broadcast: someone else is now speaking.
     */
    fun handleFloorLocked(speakerId: String?, speakerName: String?, expiresAt: Long) {
        // Don't override our own TRANSMITTING state
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return

        Log.d(TAG, "Floor LOCKED by $speakerName")
        _floorStatus.update {
            FloorStatus(
                state = FloorState.RECEIVING,
                currentSpeakerId = speakerId,
                currentSpeakerName = speakerName,
                expiresAt = expiresAt
            )
        }
    }

    /**
     * Server broadcast: floor is now idle.
     */
    fun handleFloorIdle() {
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return

        Log.d(TAG, "Floor IDLE")
        cancelTransmitTimer()
        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
    }

    /**
     * Server revoked our floor (owner priority override).
     */
    fun handleFloorRevoked() {
        cancelTransmitTimer()
        Log.d(TAG, "Floor REVOKED")

        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
        onFloorRevoked?.invoke()
    }

    /**
     * Server warning: 3 seconds remaining.
     */
    fun handleFloorWarning() {
        Log.d(TAG, "Floor WARNING: 3 seconds remaining")
        onFloorWarning?.invoke()
    }

    /**
     * Server forcibly timed out our transmission.
     */
    fun handleFloorTimedOut() {
        cancelTransmitTimer()
        Log.d(TAG, "Floor TIMEOUT")

        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
        onFloorTimeout?.invoke()
    }

    /**
     * Reset everything when leaving a room.
     */
    fun reset() {
        cancelFallback()
        cancelTransmitTimer()
        _floorStatus.update { FloorStatus() }
    }

    // ── Internal Timers ────────────────────────────────────────────

    private fun startTransmitTimer(expiresAt: Long) {
        cancelTransmitTimer()
        val durationMs = (expiresAt - System.currentTimeMillis()).coerceAtLeast(1000)

        transmitTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _floorStatus.update { it.copy(expiresAt = System.currentTimeMillis() + millisUntilFinished) }
            }
            override fun onFinish() {
                // Server should send floor-timeout, but as safety net:
                if (_floorStatus.value.state == FloorState.TRANSMITTING) {
                    handleFloorTimedOut()
                }
            }
        }.start()
    }

    private fun cancelTransmitTimer() {
        transmitTimer?.cancel()
        transmitTimer = null
    }

    private fun cancelFallback() {
        fallbackRunnable?.let { fallbackTimer?.removeCallbacks(it) }
        fallbackTimer = null
        fallbackRunnable = null
    }
}
