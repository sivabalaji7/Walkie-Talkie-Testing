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

    // Local identity for deterministic conflict arbitration
    var myUsername: String = ""
    var localClaimTimestamp: Long = 0L
    var isLocalPriority: Boolean = false

    // Callbacks wired by MainActivity
    var onFloorGranted: (() -> Unit)? = null
    var onFloorDenied: ((reason: String, speakerName: String) -> Unit)? = null
    var onFloorRevoked: (() -> Unit)? = null
    var onFloorReleased: (() -> Unit)? = null
    var onFloorWarning: (() -> Unit)? = null
    var onFloorTimeout: (() -> Unit)? = null

    private var transmitTimeoutRunnable: Runnable? = null
    private var transmitWarningRunnable: Runnable? = null
    private var fallbackRunnable: Runnable? = null
    private var busyResetRunnable: Runnable? = null
    private var receiverTimeoutRunnable: Runnable? = null

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Called when user presses PTT.
     * Transitions to REQUESTING and grants locally after emitting claim.
     */
    fun requestFloor(isPriority: Boolean = false) = runOnMain {
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return@runOnMain
        if (_floorStatus.value.state == FloorState.BUSY_BLOCKED) return@runOnMain

        if (_floorStatus.value.state == FloorState.RECEIVING && !isPriority) {
            handleFloorDenied("Floor occupied", _floorStatus.value.currentSpeakerName ?: "Squad Member")
            return@runOnMain
        }

        val now = System.currentTimeMillis()
        localClaimTimestamp = now
        isLocalPriority = isPriority

        Log.d(TAG, "Requesting floor (isPriority=$isPriority, timestamp=$now)...")
        _floorStatus.update { it.copy(state = FloorState.REQUESTING) }

        // Emit claim to signaling server / broadcast channel
        com.example.walkietalkieapp.socket.SupabaseRealtimeManager.sendStartVoice(isPriority, now)

        // Grant floor locally with 20s safety lease
        cancelFallback()
        fallbackRunnable = Runnable {
            if (_floorStatus.value.state == FloorState.REQUESTING) {
                Log.d(TAG, "Granting floor locally to $myUsername")
                handleFloorGranted(now + 20000L)
            }
        }
        mainHandler.post(fallbackRunnable!!)
    }

    /**
     * Called when user releases PTT.
     * Crucial half-duplex rule: ONLY broadcast floor-release if local user was actually transmitting/requesting!
     */
    fun releaseFloor() = runOnMain {
        cancelFallback()
        cancelTransmitTimer()

        val wasHoldingFloor = _floorStatus.value.state == FloorState.TRANSMITTING || _floorStatus.value.state == FloorState.REQUESTING
        if (wasHoldingFloor) {
            Log.d(TAG, "Releasing floor — broadcasting release to squad")
            com.example.walkietalkieapp.socket.SupabaseRealtimeManager.emitReleaseFloor()
            _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
            onFloorReleased?.invoke()
        }
    }

    /**
     * Server or local arbitration granted floor to this user.
     */
    fun handleFloorGranted(expiresAt: Long) = runOnMain {
        cancelFallback()

        // Fix tap race: If user already released PTT before server granted floor, do not stay transmitting!
        if (_floorStatus.value.state != FloorState.REQUESTING) {
            Log.d(TAG, "Floor GRANTED but user already released — immediately releasing floor")
            com.example.walkietalkieapp.socket.SupabaseRealtimeManager.emitReleaseFloor()
            _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
            return@runOnMain
        }

        val actualExpiresAt = if (expiresAt > System.currentTimeMillis()) expiresAt else System.currentTimeMillis() + 20000L
        Log.d(TAG, "Floor GRANTED (20s safety limit active)")
        _floorStatus.update {
            FloorStatus(
                state = FloorState.TRANSMITTING,
                isLocalUserSpeaking = true,
                currentSpeakerId = myUsername,
                currentSpeakerName = myUsername,
                expiresAt = actualExpiresAt
            )
        }

        // Schedule hard cutoff safety timer (20s limit)
        cancelTransmitTimer()
        val durationMs = (actualExpiresAt - System.currentTimeMillis()).coerceAtLeast(1000L)
        if (durationMs > 3000L) {
            transmitWarningRunnable = Runnable {
                if (_floorStatus.value.state == FloorState.TRANSMITTING) {
                    handleFloorWarning()
                }
            }
            mainHandler.postDelayed(transmitWarningRunnable!!, durationMs - 3000L)
        }
        transmitTimeoutRunnable = Runnable {
            if (_floorStatus.value.state == FloorState.TRANSMITTING) {
                Log.d(TAG, "Floor 20s transmit limit reached — auto-unlocking")
                handleFloorTimedOut()
            }
        }
        mainHandler.postDelayed(transmitTimeoutRunnable!!, durationMs)

        onFloorGranted?.invoke()
    }

    /**
     * Remote peer claimed floor. Perform deterministic collision arbitration if local user is also talking.
     */
    fun handleFloorClaimReceived(
        remoteSpeaker: String,
        remoteTimestamp: Long,
        remoteIsPriority: Boolean,
        expiresAt: Long
    ) = runOnMain {
        if (remoteSpeaker.isBlank() || remoteSpeaker.equals(myUsername, ignoreCase = true)) return@runOnMain

        val localState = _floorStatus.value.state
        if (localState == FloorState.TRANSMITTING || localState == FloorState.REQUESTING) {
            // Collision detected! Resolve deterministically:
            // 1. Priority override
            // 2. Earlier timestamp
            // 3. Username tie-breaker
            val remoteWins = when {
                remoteIsPriority && !isLocalPriority -> true
                !remoteIsPriority && isLocalPriority -> false
                remoteTimestamp != 0L && localClaimTimestamp != 0L && remoteTimestamp != localClaimTimestamp ->
                    remoteTimestamp < localClaimTimestamp
                else -> remoteSpeaker.lowercase() < myUsername.lowercase()
            }

            if (remoteWins) {
                Log.w(TAG, "Collision arbitration: yielding floor to $remoteSpeaker (remoteTS=$remoteTimestamp vs localTS=$localClaimTimestamp)")
                handleFloorRevoked()
                handleFloorLocked(remoteSpeaker, remoteSpeaker, expiresAt)
            } else {
                Log.d(TAG, "Collision arbitration: local user retains floor over $remoteSpeaker (localTS=$localClaimTimestamp vs remoteTS=$remoteTimestamp)")
            }
            return@runOnMain
        }

        // Local user is idle or listening: lock to new speaker
        handleFloorLocked(remoteSpeaker, remoteSpeaker, expiresAt)
    }

    /**
     * Floor denied — someone else is speaking.
     */
    fun handleFloorDenied(reason: String, currentSpeakerName: String) = runOnMain {
        cancelFallback()
        Log.d(TAG, "Floor DENIED: $reason (speaker: $currentSpeakerName)")

        val activeSpeaker = currentSpeakerName.ifBlank { _floorStatus.value.currentSpeakerName ?: "Squad Member" }
        val expiresAt = _floorStatus.value.expiresAt.coerceAtLeast(System.currentTimeMillis() + 10000L)

        _floorStatus.update {
            FloorStatus(
                state = FloorState.BUSY_BLOCKED,
                currentSpeakerName = activeSpeaker,
                expiresAt = expiresAt
            )
        }

        onFloorDenied?.invoke(reason, activeSpeaker)

        // After busy buzz/animation, revert back to RECEIVING if speaker is still talking (NEVER IDLE!)
        busyResetRunnable?.let { mainHandler.removeCallbacks(it) }
        busyResetRunnable = Runnable {
            if (_floorStatus.value.state == FloorState.BUSY_BLOCKED) {
                val current = _floorStatus.value.currentSpeakerName
                val currentExp = _floorStatus.value.expiresAt
                if (!current.isNullOrBlank() && System.currentTimeMillis() < currentExp) {
                    Log.d(TAG, "Busy cooldown ended, speaker $current still active — reverting to RECEIVING")
                    _floorStatus.update {
                        FloorStatus(
                            state = FloorState.RECEIVING,
                            currentSpeakerId = it.currentSpeakerId ?: current,
                            currentSpeakerName = current,
                            expiresAt = currentExp
                        )
                    }
                } else {
                    _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
                }
            }
        }
        mainHandler.postDelayed(busyResetRunnable!!, 1800)
    }

    /**
     * Server/peer broadcast: someone else is now speaking.
     */
    fun handleFloorLocked(speakerId: String?, speakerName: String?, expiresAt: Long) = runOnMain {
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return@runOnMain

        cancelReceiverTimeout()
        val actualExpiresAt = if (expiresAt > System.currentTimeMillis()) expiresAt else System.currentTimeMillis() + 25000L
        Log.d(TAG, "Floor LOCKED by $speakerName (expires in ${(actualExpiresAt - System.currentTimeMillis()) / 1000}s)")
        
        _floorStatus.update {
            FloorStatus(
                state = FloorState.RECEIVING,
                currentSpeakerId = speakerId,
                currentSpeakerName = speakerName,
                expiresAt = actualExpiresAt
            )
        }

        // Safety watchdog: If speaker drops connection without sending floor-release, auto-free floor
        val watchdogDuration = (actualExpiresAt - System.currentTimeMillis()).coerceIn(2000L, 25000L)
        receiverTimeoutRunnable = Runnable {
            if (_floorStatus.value.state == FloorState.RECEIVING && _floorStatus.value.currentSpeakerName == speakerName) {
                Log.w(TAG, "Watchdog: floor lease expired for $speakerName without release — resetting to IDLE")
                handleFloorIdle()
            }
        }
        mainHandler.postDelayed(receiverTimeoutRunnable!!, watchdogDuration)
    }

    /**
     * Floor is now idle.
     */
    fun handleFloorIdle() = runOnMain {
        if (_floorStatus.value.state == FloorState.TRANSMITTING) return@runOnMain

        Log.d(TAG, "Floor IDLE")
        cancelTransmitTimer()
        cancelReceiverTimeout()
        busyResetRunnable?.let { mainHandler.removeCallbacks(it) }
        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
    }

    /**
     * Server or arbitration revoked our floor.
     */
    fun handleFloorRevoked() = runOnMain {
        cancelTransmitTimer()
        Log.d(TAG, "Floor REVOKED")

        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
        onFloorRevoked?.invoke()
    }

    /**
     * Warning: 3 seconds remaining.
     */
    fun handleFloorWarning() = runOnMain {
        Log.d(TAG, "Floor WARNING: 3 seconds remaining")
        onFloorWarning?.invoke()
    }

    /**
     * Transmit cutoff limit reached.
     */
    fun handleFloorTimedOut() = runOnMain {
        cancelTransmitTimer()
        Log.d(TAG, "Floor TIMEOUT")

        // Broadcast release on transmit timeout
        com.example.walkietalkieapp.socket.SupabaseRealtimeManager.emitReleaseFloor()
        _floorStatus.update { FloorStatus(state = FloorState.IDLE) }
        onFloorTimeout?.invoke()
    }

    /**
     * Reset everything when leaving a room.
     */
    fun reset() = runOnMain {
        cancelFallback()
        cancelTransmitTimer()
        cancelReceiverTimeout()
        busyResetRunnable?.let { mainHandler.removeCallbacks(it) }
        _floorStatus.update { FloorStatus() }
    }

    private fun cancelTransmitTimer() {
        transmitTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        transmitTimeoutRunnable = null
        transmitWarningRunnable?.let { mainHandler.removeCallbacks(it) }
        transmitWarningRunnable = null
    }

    private fun cancelReceiverTimeout() {
        receiverTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        receiverTimeoutRunnable = null
    }

    private fun cancelFallback() {
        fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        fallbackRunnable = null
    }
}
