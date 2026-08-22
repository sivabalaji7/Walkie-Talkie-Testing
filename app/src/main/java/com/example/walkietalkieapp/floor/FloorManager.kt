package com.example.walkietalkieapp.floor

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "FloorManager"

object FloorManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _floorStatus = MutableStateFlow(FloorStatus())
    val floorStatus: StateFlow<FloorStatus> = _floorStatus.asStateFlow()

    // Callbacks wired by MainActivity
    var onFloorGranted: (() -> Unit)? = null
    var onFloorDenied: ((reason: String, speakerName: String) -> Unit)? = null
    var onFloorRevoked: (() -> Unit)? = null
    var onFloorReleased: (() -> Unit)? = null
    var onFloorWarning: (() -> Unit)? = null
    var onFloorTimeout: (() -> Unit)? = null

    private var transmitTimeoutRunnable: Runnable? = null
    private var fallbackRunnable: Runnable? = null
    private var busyResetRunnable: Runnable? = null

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Called when user presses PTT.
     * Transitions to REQUESTING and waits for server grant.
     * If server doesn't respond in 500ms, grants locally (backward compat).
     */
    fun requestFloor(isPriority: Boolean = false) = runOnMain {
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return@runOnMain
        if (_floorStatus.value.state == FloorState.BUSY_BLOCKED) return@runOnMain

        Log.d(TAG, "Requesting floor (isPriority=$isPriority)...")
        _floorStatus.update { it.copy(state = FloorState.REQUESTING) }

        // Emit request to signaling server
        com.example.walkietalkieapp.socket.SocketManager.emitRequestFloor(isPriority)

        // Fallback: if server doesn't respond in 600ms, grant locally
        cancelFallback()
        fallbackRunnable = Runnable {
            if (_floorStatus.value.state == FloorState.REQUESTING) {
                Log.d(TAG, "Fallback: server did not respond, granting locally")
                handleFloorGranted(0)
            }
        }
        mainHandler.postDelayed(fallbackRunnable!!, 600)
    }

    /**
     * Called when user releases PTT.
     */
    fun releaseFloor() = runOnMain {
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
    fun handleFloorGranted(expiresAt: Long) = runOnMain {
        cancelFallback()

        // Fix tap race: If user already released PTT before server granted floor, do not stay transmitting!
        if (_floorStatus.value.state != FloorState.REQUESTING) {
            Log.d(TAG, "Floor GRANTED but user already released — immediately releasing floor")
            com.example.walkietalkieapp.socket.SocketManager.emitReleaseFloor()
            _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
            return@runOnMain
        }

        Log.d(TAG, "Floor GRANTED (unlimited talk time)")
        _floorStatus.update {
            FloorStatus(
                state = FloorState.TRANSMITTING,
                isLocalUserSpeaking = true,
                expiresAt = expiresAt
            )
        }

        onFloorGranted?.invoke()
    }

    /**
     * Server denied floor — someone else is speaking.
     */
    fun handleFloorDenied(reason: String, currentSpeakerName: String) = runOnMain {
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
        busyResetRunnable?.let { mainHandler.removeCallbacks(it) }
        busyResetRunnable = Runnable {
            if (_floorStatus.value.state == FloorState.BUSY_BLOCKED) {
                _floorStatus.update { it.copy(state = FloorState.IDLE) }
            }
        }
        mainHandler.postDelayed(busyResetRunnable!!, 2000)
    }

    /**
     * Server broadcast: someone else is now speaking.
     */
    fun handleFloorLocked(speakerId: String?, speakerName: String?, expiresAt: Long) = runOnMain {
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return@runOnMain

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
    fun handleFloorIdle() = runOnMain {
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return@runOnMain

        Log.d(TAG, "Floor IDLE")
        cancelTransmitTimer()
        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
    }

    /**
     * Server revoked our floor (owner priority override).
     */
    fun handleFloorRevoked() = runOnMain {
        cancelTransmitTimer()
        Log.d(TAG, "Floor REVOKED")

        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
        onFloorRevoked?.invoke()
    }

    /**
     * Server warning: 3 seconds remaining.
     */
    fun handleFloorWarning() = runOnMain {
        Log.d(TAG, "Floor WARNING: 3 seconds remaining")
        onFloorWarning?.invoke()
    }

    /**
     * Server forcibly timed out our transmission.
     */
    fun handleFloorTimedOut() = runOnMain {
        cancelTransmitTimer()
        Log.d(TAG, "Floor TIMEOUT")

        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
        onFloorTimeout?.invoke()
    }

    /**
     * Reset everything when leaving a room.
     */
    fun reset() = runOnMain {
        cancelFallback()
        cancelTransmitTimer()
        busyResetRunnable?.let { mainHandler.removeCallbacks(it) }
        _floorStatus.update { FloorStatus() }
    }

    private fun cancelTransmitTimer() {
        transmitTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        transmitTimeoutRunnable = null
    }

    private fun cancelFallback() {
        fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackRunnable = null
    }
}

