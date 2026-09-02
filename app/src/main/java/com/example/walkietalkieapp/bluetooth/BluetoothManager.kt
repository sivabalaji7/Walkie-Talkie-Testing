package com.example.walkietalkieapp.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.walkietalkieapp.audio.AudioPlayer
import com.example.walkietalkieapp.audio.AudioRecorder
import com.example.walkietalkieapp.audio.engine.VoicePacket
import com.example.walkietalkieapp.audio.engine.VoiceQualityEngine
import com.example.walkietalkieapp.dna.engine.CommunicationDnaEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class DiscoveredSquad(
    val squadName: String,
    val hostUsername: String,
    val hostAddress: String,
    val rssi: Int,
    val device: BluetoothDevice,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class SquadMember(
    val id: String,
    val username: String,
    val address: String,
    val isSpeaking: Boolean = false
)

data class BluetoothSquadUiState(
    val isHost: Boolean = false,
    val connectionState: String = "IDLE", // IDLE, HOSTING, SCANNING, CONNECTING, CONNECTED
    val squadName: String = "",
    val myUsername: String = "",
    val myId: String = UUID.randomUUID().toString().take(8),
    val members: List<SquadMember> = emptyList(),
    val isChannelBusy: Boolean = false,
    val currentSpeakerId: String? = null,
    val currentSpeakerName: String? = null,
    val lastSpeakerName: String? = null,
    val lastSpeakerTimestamp: Long = 0L,
    val lastActivityTimestamp: Long = System.currentTimeMillis(),
    val eventLog: List<String> = listOf("App Ready"),
    val discoveredSquads: List<DiscoveredSquad> = emptyList(),
    val isScanning: Boolean = false,
    val isBeaconActive: Boolean = false,
    val beaconCountdownSeconds: Int = 0
)

class BluetoothManager(private val context: Context) {
    private val TAG = "BluetoothManager"
    private val SERVICE_NAME = "SquadTalkMesh"

    // Dedicated 128-bit RFCOMM UUID for Squad Audio Transmission
    private val SQUAD_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")

    // Manufacturer ID for Squad Talk BLE Beacon (0x0220)
    private val SQUAD_MANUFACTURER_ID = 0x0220
    private val BEACON_MAGIC_0 = 0x53.toByte() // 'S'
    private val BEACON_MAGIC_1 = 0x51.toByte() // 'Q'
    private val SQUAD_BEACON_UUID: ParcelUuid = ParcelUuid.fromString("0000FA87-0000-1000-8000-00805F9B34FB")

    // Packet Types for RFCOMM socket
    companion object {
        const val MAGIC_BYTE: Byte = 0x57 // 'W'
        const val PKT_JOIN_REQ: Byte = 0x01
        const val PKT_JOIN_ACK: Byte = 0x02
        const val PKT_MEMBERS_SYNC: Byte = 0x03
        const val PKT_FLOOR_CLAIM: Byte = 0x04
        const val PKT_VOICE_CHUNK: Byte = 0x05
        const val PKT_FLOOR_RELEASE: Byte = 0x06
        const val PKT_LEAVE: Byte = 0x07
        const val PKT_KEEPALIVE: Byte = 0x08
        const val PKT_KEEPALIVE_ACK: Byte = 0x09
        const val PKT_FLOOR_STATE: Byte = 0x0A
        const val PKT_FLOOR_DENY: Byte = 0x0B
        const val PKT_SYNC_REQUEST: Byte = 0x0C
    }

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    init {
        // Wire the engine to transmit packets over Bluetooth when on the local mesh
        VoiceQualityEngine.instance.onTransmitLocalPacket = { packet ->
            broadcastVoicePacket(packet)
        }
    }

    private val _uiState = MutableStateFlow(BluetoothSquadUiState())
    val uiState: StateFlow<BluetoothSquadUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 20)
    val events = _events.asSharedFlow()

    // Host Server & BLE Advertiser State
    private var serverSocket: BluetoothServerSocket? = null
    private var acceptThread: Thread? = null
    private val clientHandlers = ConcurrentHashMap<String, PeerConnectionHandler>()
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var beaconTimerRunnable: Runnable? = null

    // Client State & BLE Scanner
    private var clientSocket: BluetoothSocket? = null
    private var clientHandler: PeerConnectionHandler? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanStopRunnable: Runnable? = null
    private var lastScanStartTime = 0L

    // Floor Control & Ownership Tracking
    @Volatile var currentFloorHolderId: String? = null
    @Volatile var isChannelBusy: Boolean = false
    @Volatile var isLocallyHoldingFloor: Boolean = false
    private val floorLock = Any()
    private var floorTimeoutRunnable: Runnable? = null

    // Dedicated background IO thread pool
    private val ioExecutor = Executors.newCachedThreadPool()

    @Volatile
    private var isShuttingDown = false

    val audioPlayer = AudioPlayer(context)
    private val audioRecorder = AudioRecorder()

    // =========================================================================
    // BLE FILTERED SCANNER (SCAN ON-AIR BUTTON TRIGGER)
    // =========================================================================
    @SuppressLint("MissingPermission")
    fun startSquadScan() {
        val now = System.currentTimeMillis()
        if (now - lastScanStartTime < 1500 && _uiState.value.isScanning) {
            return
        }
        lastScanStartTime = now

        stopSquadScan()

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) {
            addLog("Bluetooth is disabled")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            addLog("BLE scanner not available")
            return
        }

        bleScanner = scanner
        _uiState.update { it.copy(isScanning = true, discoveredSquads = emptyList()) }
        addLog("Scanning for on-air Squad signals...")

        val filter1 = ScanFilter.Builder()
            .setManufacturerData(
                SQUAD_MANUFACTURER_ID,
                byteArrayOf(BEACON_MAGIC_0, BEACON_MAGIC_1),
                byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            )
            .build()

        val filter2 = ScanFilter.Builder()
            .setServiceUuid(SQUAD_BEACON_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result == null) return
                val scanRecord = result.scanRecord ?: return

                // Check 1: Manufacturer Specific Data (0x0220)
                val mfgData = scanRecord.getManufacturerSpecificData(SQUAD_MANUFACTURER_ID)
                if (mfgData != null && mfgData.size >= 3 && mfgData[0] == BEACON_MAGIC_0 && mfgData[1] == BEACON_MAGIC_1) {
                    val squadLen = mfgData[2].toInt() and 0xFF
                    if (mfgData.size >= 3 + squadLen) {
                        val squadName = String(mfgData, 3, squadLen, Charsets.UTF_8).trim()
                        val userLen = mfgData.size - (3 + squadLen)
                        val hostUser = if (userLen > 0) {
                            String(mfgData, 3 + squadLen, userLen, Charsets.UTF_8).trim()
                        } else "Host"

                        val discovered = DiscoveredSquad(
                            squadName = squadName.ifBlank { "Squad" },
                            hostUsername = hostUser.ifBlank { "Host" },
                            hostAddress = result.device.address,
                            rssi = result.rssi,
                            device = result.device,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )

                        addDiscoveredSquad(discovered)
                        return
                    }
                }

                // Check 2: Service Data fallback (0xFA87)
                val serviceData = scanRecord.getServiceData(SQUAD_BEACON_UUID)
                if (serviceData != null && serviceData.isNotEmpty()) {
                    val info = String(serviceData, Charsets.UTF_8)
                    val parts = info.split(":")
                    val squadName = if (parts.isNotEmpty()) parts[0] else "Squad"
                    val hostUser = if (parts.size > 1) parts[1] else "Host"

                    val discovered = DiscoveredSquad(
                        squadName = squadName.ifBlank { "Squad" },
                        hostUsername = hostUser.ifBlank { "Host" },
                        hostAddress = result.device.address,
                        rssi = result.rssi,
                        device = result.device,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )

                    addDiscoveredSquad(discovered)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE Scan error code: $errorCode")
                _uiState.update { it.copy(isScanning = false) }
            }
        }

        try {
            scanner.startScan(listOf(filter1, filter2), settings, scanCallback)

            // Auto stop scan after 15 seconds
            scanStopRunnable = Runnable { stopSquadScan() }
            mainHandler.postDelayed(scanStopRunnable!!, 15000)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopSquadScan() {
        scanStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scanStopRunnable = null

        scanCallback?.let {
            try {
                bleScanner?.stopScan(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan", e)
            }
        }
        scanCallback = null
        _uiState.update { it.copy(isScanning = false) }
    }

    private fun addDiscoveredSquad(squad: DiscoveredSquad) {
        _uiState.update { state ->
            val existingIndex = state.discoveredSquads.indexOfFirst { it.hostAddress == squad.hostAddress }
            val updated = if (existingIndex >= 0) {
                state.discoveredSquads.toMutableList().apply { set(existingIndex, squad) }
            } else {
                state.discoveredSquads + squad
            }
            state.copy(discoveredSquads = updated)
        }
    }

    // =========================================================================
    // SQUAD HOST (ALWAYS LISTENS FOR CONNECTIONS, "GO VISIBLE" FOR 5s BEACON)
    // =========================================================================
    @SuppressLint("MissingPermission")
    fun hostSquad(username: String, squadName: String) {
        cleanupExistingConnections()
        isShuttingDown = false
        val myId = _uiState.value.myId
        val finalSquadName = squadName.ifBlank { "SQUAD-${myId.take(4).uppercase()}" }

        val hostMember = SquadMember(
            id = myId,
            username = username,
            address = bluetoothAdapter?.address ?: "HOST",
            isSpeaking = false
        )

        _uiState.update {
            it.copy(
                isHost = true,
                connectionState = "HOSTING",
                squadName = finalSquadName,
                myUsername = username,
                members = listOf(hostMember),
                isBeaconActive = false,
                beaconCountdownSeconds = 0,
                isChannelBusy = false,
                currentSpeakerId = null,
                currentSpeakerName = null
            )
        }

        stopSquadScan()
        audioPlayer.start()

        // RFCOMM Audio Server Listener
        acceptThread = Thread {
            try {
                serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SQUAD_UUID)
                addLog("Squad '$finalSquadName' created (Listening for members)")

                while (!isShuttingDown) {
                    val socket = serverSocket?.accept() ?: break
                    if (isShuttingDown) {
                        socket.close()
                        break
                    }
                    Log.d(TAG, "Host accepted incoming connection from: ${socket.remoteDevice?.address}")
                    val peerHandler = PeerConnectionHandler(socket, isHostSide = true)
                    peerHandler.start()
                }
            } catch (e: Exception) {
                if (!isShuttingDown) {
                    Log.e(TAG, "Host accept thread terminated", e)
                    addLog("Host listener stopped")
                }
            }
        }.apply {
            name = "SquadHostAcceptThread"
            start()
        }
    }

    // 5-Second "Go Visible" On-Demand Signal Transmission
    @SuppressLint("MissingPermission")
    fun triggerGoVisible(durationSeconds: Int = 5) {
        if (!_uiState.value.isHost) return
        stopBleBeacon()

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            addLog("Bluetooth not enabled")
            return
        }

        val squadName = _uiState.value.squadName
        val username = _uiState.value.myUsername

        _uiState.update {
            it.copy(
                isBeaconActive = true,
                beaconCountdownSeconds = durationSeconds
            )
        }
        addLog("Transmitting 'Hello Hello' signal on air for ${durationSeconds}s 📡")
        _events.tryEmit("Shooting signal for ${durationSeconds}s 📡")

        startBleBeacon(squadName, username, durationSeconds)

        var remaining = durationSeconds
        beaconTimerRunnable = object : Runnable {
            override fun run() {
                remaining--
                if (remaining > 0 && _uiState.value.isBeaconActive) {
                    _uiState.update { it.copy(beaconCountdownSeconds = remaining) }
                    mainHandler.postDelayed(this, 1000)
                } else {
                    _uiState.update { it.copy(isBeaconActive = false, beaconCountdownSeconds = 0) }
                    stopBleBeacon()
                    addLog("Signal broadcast ended (Still listening for join)")
                }
            }
        }
        mainHandler.postDelayed(beaconTimerRunnable!!, 1000)
    }

    @SuppressLint("MissingPermission")
    private fun startBleBeacon(squadName: String, username: String, timeoutSeconds: Int = 5) {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            addLog("BLE Advertiser not supported on device")
            _uiState.update { it.copy(isBeaconActive = false, beaconCountdownSeconds = 0) }
            return
        }

        bleAdvertiser = advertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0) // Let mainHandler manage timer
            .build()

        val squadBytes = squadName.take(10).toByteArray(Charsets.UTF_8)
        val userBytes = username.take(6).toByteArray(Charsets.UTF_8)

        val payload = ByteArray(3 + squadBytes.size + userBytes.size).apply {
            this[0] = BEACON_MAGIC_0
            this[1] = BEACON_MAGIC_1
            this[2] = squadBytes.size.toByte()
            System.arraycopy(squadBytes, 0, this, 3, squadBytes.size)
            System.arraycopy(userBytes, 0, this, 3 + squadBytes.size, userBytes.size)
        }

        // Primary Advertisement Data: Keep strictly under 31-byte limit
        val data = AdvertiseData.Builder()
            .addManufacturerData(SQUAD_MANUFACTURER_ID, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        // Scan Response Data: Secondary packet under 31-byte limit
        val scanResponse = AdvertiseData.Builder()
            .addServiceData(SQUAD_BEACON_UUID, "$squadName:$username".take(12).toByteArray(Charsets.UTF_8))
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "BLE Beacon shooting on air for ${timeoutSeconds}s: $squadName")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE Beacon start failed: $errorCode (1=DATA_TOO_LARGE, 2=TOO_MANY_ADVERTISERS, 3=ALREADY_STARTED, 4=INTERNAL_ERROR)")
                mainHandler.post {
                    beaconTimerRunnable?.let { mainHandler.removeCallbacks(it) }
                    beaconTimerRunnable = null
                    _uiState.update { it.copy(isBeaconActive = false, beaconCountdownSeconds = 0) }
                    addLog("Beacon failed to broadcast (code $errorCode)")
                }
            }
        }

        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE advertisement", e)
            mainHandler.post {
                beaconTimerRunnable?.let { mainHandler.removeCallbacks(it) }
                beaconTimerRunnable = null
                _uiState.update { it.copy(isBeaconActive = false, beaconCountdownSeconds = 0) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleBeacon() {
        beaconTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        beaconTimerRunnable = null

        val callback = advertiseCallback
        if (callback != null) {
            try {
                val advertiser = bleAdvertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser
                advertiser?.stopAdvertising(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE beacon", e)
            }
        }
        advertiseCallback = null
        bleAdvertiser = null
    }

    // =========================================================================
    // SQUAD CLIENT (JOIN) LOGIC
    // =========================================================================
    @SuppressLint("MissingPermission")
    fun joinSquad(squad: DiscoveredSquad, username: String) {
        cleanupExistingConnections()
        isShuttingDown = false
        stopSquadScan()

        val myId = _uiState.value.myId
        val myMember = SquadMember(myId, username, "CLIENT", isSpeaking = false)

        _uiState.update {
            it.copy(
                isHost = false,
                connectionState = "CONNECTING",
                myUsername = username,
                squadName = squad.squadName,
                members = listOf(myMember),
                isChannelBusy = false,
                currentSpeakerId = null,
                currentSpeakerName = null
            )
        }
        addLog("Connecting to ${squad.squadName} (${squad.hostUsername})...")

        ioExecutor.execute {
            try {
                // Cancel any active Bluetooth discovery to avoid RFCOMM connection failure / high latency
                bluetoothAdapter?.cancelDiscovery()

                val targetDevice = if (BluetoothAdapter.checkBluetoothAddress(squad.hostAddress)) {
                    bluetoothAdapter?.getRemoteDevice(squad.hostAddress) ?: squad.device
                } else {
                    squad.device
                }

                val socket = try {
                    targetDevice.createInsecureRfcommSocketToServiceRecord(SQUAD_UUID)
                } catch (e1: Exception) {
                    try {
                        val m = targetDevice.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                        m.invoke(targetDevice, 1) as BluetoothSocket
                    } catch (e2: Exception) {
                        targetDevice.createRfcommSocketToServiceRecord(SQUAD_UUID)
                    }
                }
                socket.connect()

                audioPlayer.start()
                clientSocket = socket

                val handler = PeerConnectionHandler(socket, isHostSide = false)
                clientHandler = handler
                handler.start()

                handler.sendJoinRequest(myId, username)
                addLog("Connected to ${squad.hostUsername}, waiting for admission...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to squad", e)
                addLog("Connection failed: ${e.localizedMessage}")
                mainHandler.post {
                    _uiState.update { it.copy(connectionState = "IDLE") }
                }
                closeClientSocket()
            }
        }
    }

    // Proactive sync request when client UI is ready
    fun onUiReady() {
        val state = _uiState.value
        if (state.connectionState == "CONNECTED" && !state.isHost) {
            ioExecutor.execute {
                try {
                    Thread.sleep(300L) // Ensure UI collector is active
                    clientHandler?.sendBytes(buildFramedPacket(PKT_SYNC_REQUEST, ByteArray(0)))
                    Log.d(TAG, "Sent PKT_SYNC_REQUEST to Host from onUiReady()")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send PKT_SYNC_REQUEST", e)
                }
            }
        }
    }

    // =========================================================================
    // PUSH-TO-TALK & DYNAMIC SPEAKER FLOOR CONTROL (SYNCHRONIZED)
    // =========================================================================
    fun startTalking() {
        val state = _uiState.value
        val myId = state.myId
        val myName = state.myUsername

        if (isLocallyHoldingFloor) return

        if (_uiState.value.isHost) {
            synchronized(floorLock) {
                if (isChannelBusy && currentFloorHolderId != null && currentFloorHolderId != myId) {
                    addLog("Channel Busy: ${state.currentSpeakerName ?: "Someone"} is speaking")
                    return
                }
                isLocallyHoldingFloor = true
                isChannelBusy = true
                currentFloorHolderId = myId
                onFloorClaimed(myId, myName)

                val payload = JSONObject().apply {
                    put("speakerId", myId)
                    put("speakerName", myName)
                }.toString().toByteArray(Charsets.UTF_8)

                sendPacketToAll(PKT_FLOOR_CLAIM, payload)
            }
        } else {
            if (state.isChannelBusy && currentFloorHolderId != null && currentFloorHolderId != myId) {
                addLog("Channel Busy: ${state.currentSpeakerName ?: "Someone"} is speaking")
                return
            }
            isLocallyHoldingFloor = true
            claimFloor(myId, myName)
        }

        if (isLocallyHoldingFloor) {
            // Intelligence Engine: Active Voice
            CommunicationDnaEngine.onAudioSessionActive(true)
            
            VoiceQualityEngine.instance.startTransmitting()
        }
    }

    fun stopTalking() {
        if (!isLocallyHoldingFloor) return
        isLocallyHoldingFloor = false
        VoiceQualityEngine.instance.stopTransmitting()
        
        // Intelligence Engine: Voice Ended
        CommunicationDnaEngine.onAudioSessionActive(false)

        val myId = _uiState.value.myId
        val myName = _uiState.value.myUsername

        if (_uiState.value.isHost) {
            synchronized(floorLock) {
                isChannelBusy = false
                currentFloorHolderId = null
                onFloorReleased(myId)

                val payload = JSONObject().apply {
                    put("speakerId", myId)
                    put("speakerName", myName)
                }.toString().toByteArray(Charsets.UTF_8)

                sendPacketToAll(PKT_FLOOR_RELEASE, payload)
            }
        } else {
            releaseFloor(myId, myName)
        }
    }

    // Auto release floor when app goes to background
    fun onAppBackgrounded() {
        if (isLocallyHoldingFloor) {
            Log.d(TAG, "App backgrounded during PTT transmission — releasing floor")
            isLocallyHoldingFloor = false
            VoiceQualityEngine.instance.stopTransmitting()
            val myId = _uiState.value.myId
            val myName = _uiState.value.myUsername

            if (_uiState.value.isHost) {
                synchronized(floorLock) {
                    if (currentFloorHolderId == myId) {
                        isChannelBusy = false
                        currentFloorHolderId = null
                        floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                        floorTimeoutRunnable = null

                        val payload = JSONObject().apply {
                            put("speakerId", myId)
                            put("speakerName", myName)
                        }.toString().toByteArray(Charsets.UTF_8)

                        sendPacketToAll(PKT_FLOOR_RELEASE, payload)
                        onFloorReleasedLocally(myId)
                    }
                }
            } else {
                val payload = JSONObject().apply {
                    put("speakerId", myId)
                    put("speakerName", myName)
                }.toString().toByteArray(Charsets.UTF_8)

                clientHandler?.sendBytes(buildFramedPacket(PKT_FLOOR_RELEASE, payload))
                onFloorReleasedLocally(myId)
            }
        }
    }

    private fun claimFloor(speakerId: String, speakerName: String) {
        onFloorClaimedLocally(speakerId, speakerName)

        ioExecutor.execute {
            val payload = JSONObject().apply {
                put("speakerId", speakerId)
                put("speakerName", speakerName)
            }.toString().toByteArray(Charsets.UTF_8)

            sendPacketToAll(PKT_FLOOR_CLAIM, payload)
        }
    }

    private fun releaseFloor(speakerId: String, speakerName: String) {
        onFloorReleasedLocally(speakerId)

        ioExecutor.execute {
            val payload = JSONObject().apply {
                put("speakerId", speakerId)
                put("speakerName", speakerName)
            }.toString().toByteArray(Charsets.UTF_8)

            sendPacketToAll(PKT_FLOOR_RELEASE, payload)
        }
    }

    private fun onFloorClaimed(holderId: String, holderName: String) {
        isChannelBusy = true
        currentFloorHolderId = holderId

        // Cancel previous timer
        floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

        // Start 30-second auto-release watchdog
        floorTimeoutRunnable = Runnable {
            if (_uiState.value.isHost && isChannelBusy && currentFloorHolderId == holderId) {
                Log.w(TAG, "Floor timeout reached (30s) for holder $holderId ($holderName) — force releasing floor")
                handleFloorForceRelease(holderId, "TIMEOUT")
            }
        }
        mainHandler.postDelayed(floorTimeoutRunnable!!, 30_000L)

        onFloorClaimedLocally(holderId, holderName)
    }

    private fun onFloorReleased(holderId: String) {
        isChannelBusy = false
        currentFloorHolderId = null
        floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        floorTimeoutRunnable = null
        onFloorReleasedLocally(holderId)
    }

    private fun handleFloorForceRelease(holderId: String, reason: String) {
        synchronized(floorLock) {
            isChannelBusy = false
            currentFloorHolderId = null
            floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            floorTimeoutRunnable = null

            val payload = JSONObject().apply {
                put("speakerId", holderId)
                put("speakerName", "Floor Released ($reason)")
            }.toString().toByteArray(Charsets.UTF_8)

            sendPacketToAll(PKT_FLOOR_RELEASE, payload)
            onFloorReleasedLocally(holderId)
        }
    }

    private fun broadcastVoicePacket(packet: VoicePacket) {
        val speakerIdBytes = packet.senderId.toByteArray(Charsets.UTF_8)
        val idLen = speakerIdBytes.size.toByte()

        val buffer = ByteBuffer.allocate(1 + 8 + 8 + 1 + idLen + packet.payload.size)
        buffer.put(if (packet.isFinalFrame) 1.toByte() else 0.toByte())
        buffer.putLong(packet.sequenceNumber)
        buffer.putLong(packet.timestampMs)
        buffer.put(idLen)
        buffer.put(speakerIdBytes)
        buffer.put(packet.payload)

        sendPacketToAll(PKT_VOICE_CHUNK, buffer.array())
    }

    private fun sendPacketToAll(type: Byte, payload: ByteArray, excludeHandler: PeerConnectionHandler? = null) {
        val framedPacket = buildFramedPacket(type, payload)

        if (_uiState.value.isHost) {
            clientHandlers.values.forEach { handler ->
                if (handler != excludeHandler) {
                    handler.sendBytes(framedPacket)
                }
            }
        } else {
            clientHandler?.sendBytes(framedPacket)
        }
    }

    private fun buildFramedPacket(type: Byte, payload: ByteArray): ByteArray {
        val length = payload.size
        val packet = ByteBuffer.allocate(4 + length)
        packet.put(MAGIC_BYTE)
        packet.put(type)
        packet.putShort(length.toShort())
        packet.put(payload)
        return packet.array()
    }

    private fun onFloorClaimedLocally(speakerId: String, speakerName: String) {
        mainHandler.post {
            _uiState.update { state ->
                val updatedMembers = state.members.map { m ->
                    m.copy(isSpeaking = (m.id == speakerId))
                }
                state.copy(
                    isChannelBusy = true,
                    currentSpeakerId = speakerId,
                    currentSpeakerName = speakerName,
                    lastSpeakerName = speakerName,
                    lastSpeakerTimestamp = System.currentTimeMillis(),
                    members = updatedMembers
                )
            }
        }
        if (speakerId != _uiState.value.myId) {
            _events.tryEmit("$speakerName is transmitting")
        }
    }

    private fun onFloorReleasedLocally(speakerId: String) {
        mainHandler.post {
            _uiState.update { state ->
                val updatedMembers = state.members.map { it.copy(isSpeaking = false) }
                state.copy(
                    isChannelBusy = false,
                    currentSpeakerId = null,
                    currentSpeakerName = null,
                    members = updatedMembers
                )
            }
        }
    }

    private fun handleClientDisconnect(clientId: String, handler: PeerConnectionHandler?) {
        clientHandlers.remove(clientId)
        handler?.close()

        val memberName = handler?.peerUsername ?: "Member"
        val msg = "$memberName left the squad"
        addLog(msg)
        _events.tryEmit(msg)

        synchronized(floorLock) {
            if (currentFloorHolderId == clientId) {
                Log.w(TAG, "Floor holder $clientId disconnected — force releasing floor on behalf of client")
                isChannelBusy = false
                currentFloorHolderId = null
                floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                floorTimeoutRunnable = null

                val payload = JSONObject().apply {
                    put("speakerId", clientId)
                    put("speakerName", memberName)
                }.toString().toByteArray(Charsets.UTF_8)

                sendPacketToAll(PKT_FLOOR_RELEASE, payload)
                onFloorReleasedLocally(clientId)
            }
        }

        broadcastMembersList()
    }

    // Build authoritative full member list
    private fun buildFullMembersList(): List<SquadMember> {
        val allMembers = mutableListOf<SquadMember>()
        val hostId = _uiState.value.myId
        val hostUsername = _uiState.value.myUsername
        val isHostSpeaking = _uiState.value.currentSpeakerId == hostId

        // 1. Host as first entry
        allMembers.add(
            SquadMember(
                id = hostId,
                username = hostUsername,
                address = "HOST",
                isSpeaking = isHostSpeaking
            )
        )

        // 2. All connected clients (only admitted clients with verified name)
        clientHandlers.forEach { (clientId, handler) ->
            val pid = handler.peerId ?: clientId
            val puser = handler.peerUsername
            if (puser != null) {
                allMembers.add(
                    SquadMember(
                        id = pid,
                        username = puser,
                        address = handler.socket.remoteDevice?.address ?: "CLIENT",
                        isSpeaking = (_uiState.value.currentSpeakerId == pid)
                    )
                )
            }
        }
        return allMembers
    }

    private fun broadcastMembersList() {
        if (!_uiState.value.isHost) return
        ioExecutor.execute {
            val allMembers = buildFullMembersList()

            val jsonArray = JSONArray()
            allMembers.forEach { m ->
                jsonArray.put(JSONObject().apply {
                    put("id", m.id)
                    put("username", m.username)
                    put("address", m.address)
                    put("isSpeaking", m.isSpeaking)
                })
            }
            val payload = jsonArray.toString().toByteArray(Charsets.UTF_8)
            sendPacketToAll(PKT_MEMBERS_SYNC, payload)

            mainHandler.post {
                _uiState.update { it.copy(members = allMembers) }
            }
        }
    }

    // =========================================================================
    // DISCONNECTION & CLEANUP
    // =========================================================================
    private fun cleanupExistingConnections() {
        stopBleBeacon()
        stopSquadScan()

        floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        floorTimeoutRunnable = null
        currentFloorHolderId = null
        isChannelBusy = false
        isLocallyHoldingFloor = false

        serverSocket?.runCatching { close() }
        serverSocket = null
        acceptThread = null

        clientHandlers.values.forEach { it.close() }
        clientHandlers.clear()

        closeClientSocket()
    }

    fun leaveSquad() {
        isShuttingDown = true
        audioRecorder.stop()
        stopBleBeacon()
        stopSquadScan()

        floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        floorTimeoutRunnable = null
        currentFloorHolderId = null
        isChannelBusy = false
        isLocallyHoldingFloor = false

        ioExecutor.execute {
            val payload = JSONObject().apply { put("id", _uiState.value.myId) }.toString().toByteArray(Charsets.UTF_8)
            sendPacketToAll(PKT_LEAVE, payload)

            cleanupExistingConnections()
        }

        mainHandler.post {
            _uiState.update {
                it.copy(
                    isHost = false,
                    connectionState = "IDLE",
                    squadName = "",
                    members = emptyList(),
                    isChannelBusy = false,
                    currentSpeakerId = null,
                    currentSpeakerName = null,
                    isBeaconActive = false,
                    beaconCountdownSeconds = 0,
                    discoveredSquads = emptyList()
                )
            }
        }
        addLog("Left squad")
    }

    private fun closeClientSocket() {
        clientHandler?.close()
        clientHandler = null
        clientSocket?.runCatching { close() }
        clientSocket = null
    }

    fun release() {
        leaveSquad()
        audioPlayer.release()
        audioRecorder.release()
        ioExecutor.shutdown()
    }

    fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        mainHandler.post {
            val newLogs = listOf("[$time] $msg") + _uiState.value.eventLog
            _uiState.update { it.copy(eventLog = newLogs.take(20), lastActivityTimestamp = System.currentTimeMillis()) }
        }
    }

    // =========================================================================
    // PEER CONNECTION WORKER (PER-SOCKET THREAD WITH ATOMIC WRITE LOCK)
    // =========================================================================
    private inner class PeerConnectionHandler(
        val socket: BluetoothSocket,
        private val isHostSide: Boolean
    ) : Thread("PeerConnectionHandler-${socket.remoteDevice?.address ?: "device"}") {

        private val inputStream: InputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream
        @Volatile var peerId: String? = null
        @Volatile var peerUsername: String? = null

        private val writeLock = Any()

        @Volatile
        private var isRunning = true
        
        private var lastKeepaliveSent = 0L
        private var keepaliveRunnable: Runnable? = null

        override fun run() {
            try {
                // Intelligence Engine: Report Connection
                CommunicationDnaEngine.bluetoothAdapter?.reportConnectionState(true, 1)
                
                val input = inputStream
                val headerBuffer = ByteArray(4)

                // Keepalive Pinger Loop (Client and Host send ping)
                keepaliveRunnable = object : Runnable {
                    override fun run() {
                        if (isRunning) {
                            lastKeepaliveSent = System.currentTimeMillis()
                            sendBytes(buildFramedPacket(PKT_KEEPALIVE, ByteArray(0)))
                            mainHandler.postDelayed(this, 15_000L) // Ping every 15s
                        }
                    }
                }
                mainHandler.postDelayed(keepaliveRunnable!!, 5000L)

                while (isRunning) {
                    // 1. Scan byte-by-byte until MAGIC_BYTE (0x57) is found
                    var magicFound = false
                    while (isRunning && !magicFound) {
                        val b = input.read()
                        if (b < 0) throw java.io.EOFException("Socket closed")
                        if (b.toByte() == MAGIC_BYTE) {
                            headerBuffer[0] = MAGIC_BYTE
                            magicFound = true
                        }
                    }

                    if (!isRunning) break

                    // 2. Read the remaining 3 header bytes: type (1B) + payload length (2B)
                    readExact(input, headerBuffer, 1, 3)

                    val pktType = headerBuffer[1]
                    val payloadLen = ((headerBuffer[2].toInt() and 0xFF) shl 8) or (headerBuffer[3].toInt() and 0xFF)

                    if (payloadLen < 0 || payloadLen > 65535) {
                        Log.w(TAG, "Invalid payload length: $payloadLen")
                        continue
                    }

                    // 3. Read exact payload bytes
                    val payload = if (payloadLen > 0) {
                        val buffer = ByteArray(payloadLen)
                        readExact(input, buffer, 0, payloadLen)
                        buffer
                    } else {
                        ByteArray(0)
                    }

                    handleReceivedPacket(pktType, payload)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.d(TAG, "Peer connection ended: ${socket.remoteDevice?.address} (${e.message})")
                }
                // Intelligence Engine: Report socket error
                CommunicationDnaEngine.bluetoothAdapter?.reportSocketError()
            } finally {
                keepaliveRunnable?.let { mainHandler.removeCallbacks(it) }
                close()
                handlePeerDisconnected()
            }
        }

        private fun handleReceivedPacket(type: Byte, payload: ByteArray) {
            when (type) {
                PKT_JOIN_REQ -> {
                    val json = JSONObject(String(payload, Charsets.UTF_8))
                    val id = json.getString("id")
                    val username = json.getString("username").trim().ifBlank { "Radio-${id.take(4)}" }
                    peerId = id
                    peerUsername = username

                    // ADMISSION GATE: Officially admit client to active roster NOW that name is validated
                    clientHandlers[id] = this@PeerConnectionHandler

                    val msg = "$username joined the squad"
                    addLog(msg)
                    _events.tryEmit(msg)

                    // 1. Send JOIN_ACK to the newly connected client
                    val ackPayload = JSONObject().apply {
                        put("squadName", _uiState.value.squadName)
                        put("hostId", _uiState.value.myId)
                        put("hostUsername", _uiState.value.myUsername)
                    }.toString().toByteArray(Charsets.UTF_8)
                    sendBytes(buildFramedPacket(PKT_JOIN_ACK, ackPayload))

                    // 2. Send current floor state immediately to the new client
                    val isBusy = isChannelBusy && currentFloorHolderId != null
                    val floorPayload = if (isBusy) {
                        byteArrayOf(0x01) + (currentFloorHolderId ?: "").toByteArray(Charsets.UTF_8)
                    } else {
                        byteArrayOf(0x00)
                    }
                    sendBytes(buildFramedPacket(PKT_FLOOR_STATE, floorPayload))

                    // 3. Broadcast authoritative full member list to ALL squad members
                    broadcastMembersList()

                    // 4. Delayed 1-second safety re-sync to the new client
                    val targetClientId = id
                    mainHandler.postDelayed({
                        if (_uiState.value.isHost && clientHandlers.containsKey(targetClientId)) {
                            broadcastMembersList()
                        }
                    }, 1000L)
                }

                PKT_JOIN_ACK -> {
                    val json = JSONObject(String(payload, Charsets.UTF_8))
                    val squadName = json.optString("squadName", "Squad")
                    val hostId = json.optString("hostId", "host")
                    val hostUsername = json.optString("hostUsername", "Host")

                    val hostMember = SquadMember(hostId, hostUsername, "HOST", isSpeaking = false)
                    val myId = _uiState.value.myId
                    val myUsername = _uiState.value.myUsername
                    val myMember = SquadMember(myId, myUsername, "CLIENT", isSpeaking = false)

                    mainHandler.post {
                        _uiState.update { state ->
                            state.copy(
                                squadName = squadName,
                                members = listOf(hostMember, myMember),
                                connectionState = "CONNECTED"
                            )
                        }
                    }
                }

                PKT_MEMBERS_SYNC -> {
                    val jsonArray = JSONArray(String(payload, Charsets.UTF_8))
                    val syncedMembers = mutableListOf<SquadMember>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        syncedMembers.add(
                            SquadMember(
                                id = obj.getString("id"),
                                username = obj.getString("username"),
                                address = obj.getString("address"),
                                isSpeaking = obj.getBoolean("isSpeaking")
                            )
                        )
                    }

                    val myId = _uiState.value.myId
                    val myUsername = _uiState.value.myUsername
                    val myMember = SquadMember(myId, myUsername, "CLIENT", isSpeaking = false)

                    // Ensure Client's own self is ALWAYS preserved in the list
                    if (syncedMembers.none { it.id == myId }) {
                        syncedMembers.add(myMember)
                    }

                    mainHandler.post {
                        _uiState.update { it.copy(members = syncedMembers, connectionState = "CONNECTED") }
                    }
                }

                PKT_SYNC_REQUEST -> {
                    if (isHostSide) {
                        Log.d(TAG, "Received PKT_SYNC_REQUEST from client ${peerId ?: socket.remoteDevice?.address}, resending current state")
                        broadcastMembersList()

                        val isBusy = isChannelBusy && currentFloorHolderId != null
                        val floorPayload = if (isBusy) {
                            byteArrayOf(0x01) + (currentFloorHolderId ?: "").toByteArray(Charsets.UTF_8)
                        } else {
                            byteArrayOf(0x00)
                        }
                        sendBytes(buildFramedPacket(PKT_FLOOR_STATE, floorPayload))
                    }
                }

                PKT_FLOOR_STATE -> {
                    if (payload.isEmpty() || payload[0] == 0x00.toByte()) {
                        isChannelBusy = false
                        currentFloorHolderId = null
                        mainHandler.post {
                            _uiState.update { state ->
                                val updatedMembers = state.members.map { it.copy(isSpeaking = false) }
                                state.copy(
                                    isChannelBusy = false,
                                    currentSpeakerId = null,
                                    currentSpeakerName = null,
                                    members = updatedMembers
                                )
                            }
                        }
                    } else {
                        isChannelBusy = true
                        val holderId = if (payload.size > 1) String(payload.copyOfRange(1, payload.size), Charsets.UTF_8) else ""
                        currentFloorHolderId = holderId
                        mainHandler.post {
                            _uiState.update { state ->
                                val speakerName = state.members.find { it.id == holderId }?.username ?: "Peer"
                                val updatedMembers = state.members.map { m ->
                                    m.copy(isSpeaking = (m.id == holderId))
                                }
                                state.copy(
                                    isChannelBusy = true,
                                    currentSpeakerId = holderId,
                                    currentSpeakerName = speakerName,
                                    members = updatedMembers
                                )
                            }
                        }
                    }
                }

                PKT_FLOOR_CLAIM -> {
                    val json = JSONObject(String(payload, Charsets.UTF_8))
                    val speakerId = json.getString("speakerId")
                    val speakerName = json.getString("speakerName")

                    if (isHostSide) {
                        synchronized(floorLock) {
                            if (isChannelBusy && currentFloorHolderId != null && currentFloorHolderId != speakerId) {
                                val denyPayload = (currentFloorHolderId ?: "").toByteArray(Charsets.UTF_8)
                                sendBytes(buildFramedPacket(PKT_FLOOR_DENY, denyPayload))
                                Log.d(TAG, "Floor claim by $speakerId denied, currently held by $currentFloorHolderId")
                                return
                            }

                            onFloorClaimed(speakerId, speakerName)
                            sendPacketToAll(PKT_FLOOR_CLAIM, payload, excludeHandler = this@PeerConnectionHandler)
                        }
                    } else {
                        onFloorClaimedLocally(speakerId, speakerName)
                    }
                }

                PKT_FLOOR_DENY -> {
                    isLocallyHoldingFloor = false
                    VoiceQualityEngine.instance.stopTransmitting()
                    val holderId = String(payload, Charsets.UTF_8)
                    addLog("Channel busy — transmission denied")
                    _events.tryEmit("Channel busy — transmission denied")
                    mainHandler.post {
                        _uiState.update { state ->
                            val speakerName = state.members.find { it.id == holderId }?.username ?: "Peer"
                            state.copy(
                                isChannelBusy = true,
                                currentSpeakerId = holderId,
                                currentSpeakerName = speakerName
                            )
                        }
                    }
                }

                PKT_VOICE_CHUNK -> {
                    val buffer = ByteBuffer.wrap(payload)
                    val isFinalFrame = buffer.get() == 1.toByte()
                    val sequenceNumber = buffer.long
                    val timestampMs = buffer.long
                    val idLen = buffer.get().toInt() and 0xFF
                    val idBytes = ByteArray(idLen)
                    buffer.get(idBytes)
                    val speakerId = String(idBytes, Charsets.UTF_8)

                    val audioPayload = ByteArray(buffer.remaining())
                    buffer.get(audioPayload)
                    
                    val packet = VoicePacket(sequenceNumber, timestampMs, speakerId, audioPayload, isFinalFrame)

                    // Reset/extend 30s floor timeout on Host when active holder transmits
                    if (isHostSide && currentFloorHolderId == speakerId) {
                        floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                        floorTimeoutRunnable = Runnable {
                            if (_uiState.value.isHost && isChannelBusy && currentFloorHolderId == speakerId) {
                                Log.w(TAG, "Floor timeout reached (30s) for holder $speakerId — force releasing floor")
                                handleFloorForceRelease(speakerId, "TIMEOUT")
                            }
                        }
                        mainHandler.postDelayed(floorTimeoutRunnable!!, 30_000L)
                    }

                    // Hand the packet directly to the Voice Quality Engine
                    VoiceQualityEngine.instance.onPacketReceived(packet)

                    // Host relays to all other connected clients
                    if (isHostSide) {
                        sendPacketToAll(PKT_VOICE_CHUNK, payload, excludeHandler = this@PeerConnectionHandler)
                    }
                }

                PKT_FLOOR_RELEASE -> {
                    val json = JSONObject(String(payload, Charsets.UTF_8))
                    val speakerId = json.getString("speakerId")

                    if (isHostSide) {
                        synchronized(floorLock) {
                            if (currentFloorHolderId == speakerId || currentFloorHolderId == null) {
                                isChannelBusy = false
                                currentFloorHolderId = null
                                floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                                floorTimeoutRunnable = null
                            }
                            onFloorReleasedLocally(speakerId)
                            sendPacketToAll(PKT_FLOOR_RELEASE, payload, excludeHandler = this@PeerConnectionHandler)
                        }
                    } else {
                        isLocallyHoldingFloor = false
                        onFloorReleasedLocally(speakerId)
                    }
                }

                PKT_LEAVE -> {
                    val json = JSONObject(String(payload, Charsets.UTF_8))
                    val id = json.optString("id")
                    handlePeerDisconnected(id)
                }

                PKT_KEEPALIVE -> {
                    // Respond with ACK immediately
                    sendBytes(buildFramedPacket(PKT_KEEPALIVE_ACK, ByteArray(0)))
                }

                PKT_KEEPALIVE_ACK -> {
                    val rtt = System.currentTimeMillis() - lastKeepaliveSent
                    // Intelligence Engine: Report real RTT telemetry
                    CommunicationDnaEngine.bluetoothAdapter?.reportKeepaliveRtt(rtt)
                }
            }
        }

        fun sendJoinRequest(myId: String, myUsername: String) {
            val payload = JSONObject().apply {
                put("id", myId)
                put("username", myUsername)
            }.toString().toByteArray(Charsets.UTF_8)
            sendBytes(buildFramedPacket(PKT_JOIN_REQ, payload))
        }

        fun sendBytes(data: ByteArray) {
            try {
                synchronized(writeLock) {
                    outputStream.write(data)
                    outputStream.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket write failed", e)
                close()
            }
        }

        private fun readExact(input: InputStream, buffer: ByteArray, offset: Int, length: Int) {
            var bytesRead = 0
            while (bytesRead < length && isRunning) {
                val read = input.read(buffer, offset + bytesRead, length - bytesRead)
                if (read < 0) throw java.io.EOFException("Socket closed")
                bytesRead += read
            }
        }

        private fun handlePeerDisconnected(explicitId: String? = null) {
            val id = explicitId ?: peerId
            if (isHostSide) {
                if (id != null) {
                    handleClientDisconnect(id, this@PeerConnectionHandler)
                }
            } else {
                mainHandler.post {
                    _uiState.update { it.copy(connectionState = "IDLE", members = emptyList(), isChannelBusy = false, currentSpeakerId = null, currentSpeakerName = null) }
                    addLog("Disconnected from Host")
                    _events.tryEmit("Disconnected from Host")
                }
            }
            // Intelligence Engine: Disconnected
            CommunicationDnaEngine.bluetoothAdapter?.reportConnectionState(false, 0)
        }

        fun close() {
            isRunning = false
            outputStream.runCatching { close() }
            inputStream.runCatching { close() }
            socket.runCatching { close() }
        }
    }
}
