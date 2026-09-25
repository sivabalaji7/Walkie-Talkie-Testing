package com.example.walkietalkieapp.wifidirect

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.walkietalkieapp.audio.AudioPlayer
import com.example.walkietalkieapp.audio.AudioRecorder
import com.example.walkietalkieapp.audio.engine.VoicePacket
import com.example.walkietalkieapp.audio.engine.VoiceQualityEngine
import com.example.walkietalkieapp.bluetooth.SquadMember
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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class DiscoveredWifiSquad(
    val squadName: String,
    val hostUsername: String,
    val deviceAddress: String,
    val device: WifiP2pDevice,
    val networkName: String,
    val passphrase: String,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class WifiJoinRequest(
    val clientId: String,
    val username: String,
    val deviceName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class WifiSquadUiState(
    val isHost: Boolean = false,
    val connectionState: String = "IDLE", // IDLE, HOSTING, SCANNING, CONNECTING, WAITING_APPROVAL, CONNECTED
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
    val eventLog: List<String> = listOf("Wi-Fi Direct Ready"),
    val discoveredSquads: List<DiscoveredWifiSquad> = emptyList(),
    val isScanning: Boolean = false,
    val isWifiP2pEnabled: Boolean = true,
    val groupOwnerAddress: String? = null,
    val isGroupOwner: Boolean = false,
    val pendingJoinRequests: List<WifiJoinRequest> = emptyList()
)

class WifiDirectManager(private val context: Context) : WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

    private val TAG = "WifiDirectManager"
    private val PORT = 8888
    private val SERVICE_TYPE = "_squadtalk._tcp"

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
        const val PKT_JOIN_DENY: Byte = 0x0D
    }

    private val wifiP2pManager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    init {
        // Wire the engine to transmit packets over Wi-Fi Direct when on the local mesh
        VoiceQualityEngine.instance.onTransmitWifiDirectPacket = { packet ->
            broadcastVoicePacket(packet)
        }
    }

    private val _uiState = MutableStateFlow(WifiSquadUiState())
    val uiState: StateFlow<WifiSquadUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 20)
    val events = _events.asSharedFlow()

    // Host Server State
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val clientHandlers = ConcurrentHashMap<String, TcpPeerConnectionHandler>()
    private val pendingClientHandlers = ConcurrentHashMap<String, TcpPeerConnectionHandler>()
    private var serviceInfo: WifiP2pDnsSdServiceInfo? = null

    // Client State
    private var clientSocket: Socket? = null
    private var clientHandler: TcpPeerConnectionHandler? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null

    // High Performance WifiLock & Pixel Auto-Disable Prevention
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null

    // Floor Control & Ownership Tracking
    @Volatile var currentFloorHolderId: String? = null
    @Volatile var isChannelBusy: Boolean = false
    @Volatile var isLocallyHoldingFloor: Boolean = false
    private val floorLock = Any()
    private var floorTimeoutRunnable: Runnable? = null

    // Keepalive Job & Timers
    private var keepAliveRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanStopRunnable: Runnable? = null
    private var connectingTimeoutRunnable: Runnable? = null
    private val ioExecutor = Executors.newCachedThreadPool()

    @Volatile
    private var isShuttingDown = false

    // Guards against multiple concurrent TCP connection attempts
    private val tcpConnecting = AtomicBoolean(false)

    val audioPlayer: AudioPlayer
        get() = VoiceQualityEngine.instance.getOrCreateAudioPlayer(context)
    private val audioRecorder = AudioRecorder()

    // Stored join parameters for retry
    private var lastJoinedSquad: DiscoveredWifiSquad? = null
    private var lastJoinUsername: String = ""

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
    }

    // =========================================================================
    // SINGLE BROADCAST RECEIVER FOR BOTH HOST AND CLIENT
    // =========================================================================
    private val receiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    Log.d(TAG, "WIFI_STATE_CHANGED_ACTION: wifiState=$wifiState")
                    if (wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_STATE_DISABLING) {
                        Log.w(TAG, "Wi-Fi turned off by system/user! Prompting to re-enable...")
                        mainHandler.post {
                            addLog("Wi-Fi turned off. Please keep Wi-Fi on for Walkie Talkie.")
                            promptEnableWifiIfDisabled()
                        }
                    } else if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                        Log.d(TAG, "Wi-Fi enabled, acquiring low-latency lock")
                        acquireWifiLock()
                    }
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    _uiState.update { it.copy(isWifiP2pEnabled = isEnabled) }
                    if (!isEnabled) addLog("Wi-Fi Direct is disabled")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    channel?.let { ch ->
                        try {
                            wifiP2pManager?.requestPeers(ch, this@WifiDirectManager)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to request peers", e)
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }

                    val p2pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    }

                    Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION: isConnected=${networkInfo?.isConnected}, groupFormed=${p2pInfo?.groupFormed}, isGroupOwner=${p2pInfo?.isGroupOwner}, state=${_uiState.value.connectionState}")

                    val isConnected = networkInfo?.isConnected == true || p2pInfo?.groupFormed == true

                    if (isConnected) {
                        val isHost = _uiState.value.isHost || p2pInfo?.isGroupOwner == true
                        val ch = ensureChannel() ?: return
                        if (isHost) {
                            // Host: Group is up! Request group info with retry to get valid SSID + Passphrase, then bind ServerSocket & publish DNS-SD
                            requestHostGroupInfoWithRetry(ch)
                        } else {
                            // Client: Mandatory call to requestConnectionInfo on Client side
                            wifiP2pManager?.requestConnectionInfo(ch, this@WifiDirectManager)
                        }
                    } else {
                        val currentState = _uiState.value.connectionState
                        if (currentState == "CONNECTED") {
                            addLog("Wi-Fi Direct disconnected")
                            mainHandler.post {
                                _uiState.update { it.copy(connectionState = "IDLE", members = emptyList(), isChannelBusy = false) }
                            }
                            releaseWifiLock()
                            stopKeepAlive()
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    }
                    Log.d(TAG, "This device Wi-Fi Direct name: ${device?.deviceName}")
                }
            }
        }
    }

    @Volatile
    private var isReceiverRegistered = false

    init {
        channel = wifiP2pManager?.initialize(context, Looper.getMainLooper()) {
            Log.w(TAG, "Wi-Fi P2P Channel lost, re-initializing")
            channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
        }
        try {
            ContextCompat.registerReceiver(context, receiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver", e)
        }
        acquireWifiLock()
    }

    fun ensureChannel(): WifiP2pManager.Channel? {
        if (channel == null) {
            channel = wifiP2pManager?.initialize(context, Looper.getMainLooper()) {
                Log.w(TAG, "Wi-Fi P2P Channel lost, re-initializing")
                channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
            }
        }
        return channel
    }

    private fun requestHostGroupInfoWithRetry(ch: WifiP2pManager.Channel, retryCount: Int = 0) {
        wifiP2pManager?.requestGroupInfo(ch) { group ->
            if (group != null && group.isGroupOwner && !group.networkName.isNullOrBlank()) {
                val ssid = group.networkName ?: ""
                val passphrase = group.passphrase ?: ""
                Log.d(TAG, "Host group ready: SSID=$ssid, passphrase length=${passphrase.length}")
                bindServerSocket()
                advertiseDnsSd(ssid, passphrase)
                acquireWifiLock()
                startKeepAlive()
            } else if (retryCount < 5 && !isShuttingDown && _uiState.value.isHost) {
                Log.d(TAG, "Host group not ready yet, retrying requestGroupInfo (${retryCount + 1}/5)...")
                mainHandler.postDelayed({
                    val activeCh = ensureChannel()
                    if (activeCh != null && !isShuttingDown && _uiState.value.isHost) {
                        requestHostGroupInfoWithRetry(activeCh, retryCount + 1)
                    }
                }, 350)
            }
        }
    }

    // =========================================================================
    // HIGH-PERFORMANCE WIFI LOCK & PIXEL KEEP-ALIVE
    // =========================================================================
    fun promptEnableWifiIfDisabled() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager?.isWifiEnabled == false) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(panelIntent)
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prompt Wi-Fi enable", e)
        }
    }

    private fun acquireWifiLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null) {
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager?.createWifiLock(lockMode, "SquadTalk:WifiDirectLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
                Log.d(TAG, "WifiLock acquired (LOW_LATENCY/HIGH_PERF)")
            }

            // PowerManager Partial WakeLock to keep Wi-Fi Direct active if screen sleeps
            val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SquadTalk:DirectWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout, auto-renewed
                Log.d(TAG, "Partial WakeLock acquired")
            }

            // NetworkRequest for TRANSPORT_WIFI on Pixel / Android 10+
            // Tells ConnectivityManager that foreground app actively needs the Wi-Fi interface,
            // which halts the Pixel OS idle timer that turns off Wi-Fi when not connected to an AP.
            if (connectivityManager == null) {
                connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            }
            if (wifiNetworkCallback == null && connectivityManager != null) {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "ConnectivityManager: Wi-Fi network pinned for P2P/Mesh")
                    }
                    override fun onLost(network: Network) {
                        Log.d(TAG, "ConnectivityManager: Wi-Fi network unpinned")
                    }
                }
                try {
                    connectivityManager?.requestNetwork(request, callback)
                    wifiNetworkCallback = callback
                    Log.d(TAG, "ConnectivityManager: NetworkRequest for TRANSPORT_WIFI registered")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register NetworkRequest for TRANSPORT_WIFI", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WifiLock or WakeLock", e)
        }
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.d(TAG, "WifiLock released")
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WifiLock", e)
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock", e)
        }

        try {
            wifiNetworkCallback?.let { cb ->
                connectivityManager?.unregisterNetworkCallback(cb)
                Log.d(TAG, "NetworkRequest unregistered")
            }
            wifiNetworkCallback = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister NetworkCallback", e)
        }
    }

    // =========================================================================
    // HEARTBEAT / KEEPALIVE MECHANISM & DEAD CLIENT TIMEOUT (FIX 1A & FIX 2)
    // =========================================================================
    private fun startKeepAlive() {
        stopKeepAlive()
        keepAliveRunnable = object : Runnable {
            override fun run() {
                if (_uiState.value.isHost && !isShuttingDown) {
                    val now = System.currentTimeMillis()
                    val deadClients = mutableListOf<String>()

                    // Check for dead clients (no keepalive ack in 45s)
                    clientHandlers.forEach { (id, handler) ->
                        if (now - handler.lastSeenTimestamp > 45_000L) {
                            Log.w(TAG, "Client $id keepalive expired (last seen ${(now - handler.lastSeenTimestamp) / 1000}s ago)")
                            deadClients.add(id)
                        }
                    }

                    deadClients.forEach { id ->
                        val handler = clientHandlers[id]
                        handleClientDisconnect(id, handler)
                    }

                    // Send PKT_KEEPALIVE to all connected clients
                    clientHandlers.values.forEach { it.lastKeepaliveSent = System.currentTimeMillis() }
                    sendPacketToAll(PKT_KEEPALIVE, ByteArray(0))

                    mainHandler.postDelayed(this, 20_000L)
                }
            }
        }
        mainHandler.postDelayed(keepAliveRunnable!!, 20_000L)
    }

    private fun stopKeepAlive() {
        keepAliveRunnable?.let { mainHandler.removeCallbacks(it) }
        keepAliveRunnable = null
    }

    private fun hasRequiredPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // =========================================================================
    // PEER LIST DISCOVERY CALLBACK (STRICT SQUAD FILTER)
    // =========================================================================
    override fun onPeersAvailable(peers: WifiP2pDeviceList?) {
        if (peers == null) return
        // Only update WifiP2pDevice metadata for verified squads discovered through DNS-SD.
        _uiState.update { state ->
            val updatedList = state.discoveredSquads.map { squad ->
                val matchingPeer = peers.deviceList.firstOrNull { it.deviceAddress == squad.deviceAddress }
                if (matchingPeer != null) {
                    squad.copy(device = matchingPeer, lastSeenTimestamp = System.currentTimeMillis())
                } else {
                    squad
                }
            }
            state.copy(discoveredSquads = updatedList)
        }
    }

    // =========================================================================
    // CONNECTION INFO CALLBACK (CLIENT TCP CONNECTION TRIGGER)
    // =========================================================================
    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info == null || !info.groupFormed) return

        val isOwner = info.isGroupOwner
        val rawIp = info.groupOwnerAddress?.hostAddress
        val groupOwnerIp = rawIp?.trim()?.removePrefix("/")?.ifBlank { "192.168.49.1" } ?: "192.168.49.1"

        Log.d(TAG, "onConnectionInfoAvailable: groupFormed=${info.groupFormed}, isOwner=$isOwner, ip=$groupOwnerIp, currentState=${_uiState.value.connectionState}")

        _uiState.update {
            it.copy(
                isGroupOwner = isOwner,
                groupOwnerAddress = groupOwnerIp
            )
        }

        if (isOwner) {
            Log.d(TAG, "I am the Group Owner ($groupOwnerIp)")
        } else {
            // Client side: cancel 15-second timeout and open TCP socket to host
            cancelConnectingTimeout()
            if (_uiState.value.connectionState == "CONNECTING" || _uiState.value.connectionState == "CONNECTED") {
                connectTcpSocket(groupOwnerIp, PORT)
            }
        }
    }

    // =========================================================================
    // DISCOVERY (STRICT ORDER OF LISTENERS & DISCOVERY)
    // =========================================================================
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!hasRequiredPermissions()) {
            addLog("Missing required Wi-Fi permissions")
            _events.tryEmit("Missing required Wi-Fi permissions")
            return
        }

        if (channel == null) {
            channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
        }
        val ch = channel ?: run {
            Log.e(TAG, "Wi-Fi P2P channel is null, cannot start discovery")
            _uiState.update { it.copy(isScanning = false) }
            return
        }

        // Cancel previous timer
        scanStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scanStopRunnable = null

        isShuttingDown = false
        acquireWifiLock()
        _uiState.update { it.copy(isScanning = true) }
        addLog("Scanning for on-air Squad signals...")

        try {
            // Sequential registration: Clear old service requests FIRST
            wifiP2pManager?.clearServiceRequests(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "clearServiceRequests succeeded, setting listeners & request")
                    registerAndStartDnsSd(ch)
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "clearServiceRequests failed: $reason, proceeding to register")
                    registerAndStartDnsSd(ch)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Wi-Fi Direct discovery", e)
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerAndStartDnsSd(ch: WifiP2pManager.Channel) {
        try {
            // 2. Set BOTH listeners BEFORE adding service request
            wifiP2pManager?.setDnsSdResponseListeners(
                ch,
                { instanceName, registrationType, device ->
                    Log.d(TAG, "DNS-SD Service Response: $instanceName ($registrationType) from ${device.deviceName} (${device.deviceAddress})")
                },
                { fullDomain, record, device ->
                    Log.d(TAG, "DNS-SD TXT Record: $record from ${device.deviceName} (${device.deviceAddress})")
                    val ssid = record["ssid"] ?: return@setDnsSdResponseListeners
                    val passphrase = record["passphrase"] ?: return@setDnsSdResponseListeners
                    val squadName = record["squadName"] ?: return@setDnsSdResponseListeners
                    val host = record["host"] ?: "Host"

                    if (ssid.isNotBlank() && passphrase.isNotBlank() && squadName.isNotBlank()) {
                        val discovered = DiscoveredWifiSquad(
                            squadName = squadName,
                            hostUsername = host,
                            deviceAddress = device.deviceAddress,
                            device = device,
                            networkName = ssid,
                            passphrase = passphrase,
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                        addDiscoveredSquad(discovered)
                    }
                }
            )

            // 3. Add service request
            val req = WifiP2pDnsSdServiceRequest.newInstance()
            serviceRequest = req
            wifiP2pManager?.addServiceRequest(ch, req, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "addServiceRequest succeeded — starting scan loop")
                    triggerRealtimeScanCycle(ch)
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "addServiceRequest failed: $reason")
                    if (reason == WifiP2pManager.BUSY && _uiState.value.isScanning && !isShuttingDown) {
                        mainHandler.postDelayed({
                            if (_uiState.value.isScanning && !isShuttingDown) {
                                registerAndStartDnsSd(ch)
                            }
                        }, 1000L)
                    } else {
                        _uiState.update { it.copy(isScanning = false) }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in registerAndStartDnsSd", e)
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerRealtimeScanCycle(ch: WifiP2pManager.Channel) {
        if (!_uiState.value.isScanning || isShuttingDown) return

        // 4. Trigger active channel scan via discoverPeers
        wifiP2pManager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "discoverPeers active for channel scan")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "discoverPeers failed: $reason")
            }
        })

        // 5. Trigger service discovery
        discoverServicesWithRetry(ch)

        // 6. Realtime periodic heartbeat: retrigger probe scan every 12s so scanning stays alive
        scanStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scanStopRunnable = Runnable {
            if (_uiState.value.isScanning && !_uiState.value.isHost && _uiState.value.connectionState == "IDLE" && !isShuttingDown) {
                Log.d(TAG, "Heartbeat: retriggering realtime scan cycle")
                val now = System.currentTimeMillis()
                _uiState.update { state ->
                    state.copy(discoveredSquads = state.discoveredSquads.filter { now - it.lastSeenTimestamp < 60_000L })
                }
                triggerRealtimeScanCycle(ch)
            }
        }
        mainHandler.postDelayed(scanStopRunnable!!, 12_000L)
    }

    @SuppressLint("MissingPermission")
    private fun discoverServicesWithRetry(ch: WifiP2pManager.Channel) {
        wifiP2pManager?.discoverServices(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "discoverServices running")
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "discoverServices failed: $reason")
                if (reason == WifiP2pManager.BUSY && _uiState.value.isScanning && !isShuttingDown) {
                    mainHandler.postDelayed({
                        if (_uiState.value.isScanning && !isShuttingDown) {
                            discoverServicesWithRetry(ch)
                        }
                    }, 2000)
                }
            }
        })
    }

    private fun addDiscoveredSquad(squad: DiscoveredWifiSquad) {
        _uiState.update { state ->
            val existingIndex = state.discoveredSquads.indexOfFirst { it.deviceAddress == squad.deviceAddress }
            val updated = if (existingIndex >= 0) {
                state.discoveredSquads.toMutableList().apply { set(existingIndex, squad) }
            } else {
                state.discoveredSquads + squad
            }
            state.copy(discoveredSquads = updated)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        scanStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scanStopRunnable = null

        val ch = channel ?: return
        try {
            serviceRequest?.let { wifiP2pManager?.removeServiceRequest(ch, it, null) }
            serviceRequest = null
            wifiP2pManager?.clearServiceRequests(ch, null)
            wifiP2pManager?.stopPeerDiscovery(ch, null)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Wi-Fi Direct discovery", e)
        }
        _uiState.update { it.copy(isScanning = false) }
    }

    // =========================================================================
    // HOST SQUAD
    // =========================================================================
    @SuppressLint("MissingPermission")
    fun hostSquad(username: String, squadName: String) {
        if (!hasRequiredPermissions()) {
            addLog("Missing required Wi-Fi permissions")
            _events.tryEmit("Missing required Wi-Fi permissions")
            return
        }

        cleanupLocalSockets()
        isShuttingDown = false
        acquireWifiLock()

        val ch = ensureChannel() ?: run {
            addLog("Error: Unable to initialize Wi-Fi Direct channel")
            _events.tryEmit("Unable to initialize Wi-Fi Direct")
            return
        }
        val myId = _uiState.value.myId
        val finalSquadName = squadName.ifBlank { "WIFI-${myId.take(4).uppercase()}" }

        VoiceQualityEngine.instance.activeTransport = com.example.walkietalkieapp.dna.model.TransportType.WifiDirect
        VoiceQualityEngine.instance.initialize(context, userId = myId)

        val hostMember = SquadMember(
            id = myId,
            username = username,
            address = "GROUP_OWNER",
            isSpeaking = false
        )

        _uiState.update {
            it.copy(
                isHost = true,
                connectionState = "HOSTING",
                squadName = finalSquadName,
                myUsername = username,
                members = listOf(hostMember),
                isGroupOwner = true,
                isChannelBusy = false,
                currentSpeakerId = null,
                currentSpeakerName = null
            )
        }

        stopDiscovery()
        audioPlayer.start()

        // Clean any existing group first, then create autonomous group with settling delay
        wifiP2pManager?.requestGroupInfo(ch) { group ->
            if (group != null) {
                wifiP2pManager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        mainHandler.postDelayed({
                            if (!isShuttingDown && _uiState.value.isHost) {
                                performCreateGroup(ch, finalSquadName)
                            }
                        }, 300)
                    }
                    override fun onFailure(reason: Int) {
                        mainHandler.postDelayed({
                            if (!isShuttingDown && _uiState.value.isHost) {
                                performCreateGroup(ch, finalSquadName)
                            }
                        }, 300)
                    }
                })
            } else {
                mainHandler.postDelayed({
                    if (!isShuttingDown && _uiState.value.isHost) {
                        performCreateGroup(ch, finalSquadName)
                    }
                }, 150)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun performCreateGroup(ch: WifiP2pManager.Channel, finalSquadName: String, retryCount: Int = 0) {
        if (isShuttingDown || !_uiState.value.isHost) return

        wifiP2pManager?.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "createGroup request accepted by framework, awaiting connection event...")
                addLog("Creating squad room '$finalSquadName'...")
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "createGroup failed ($reason), attempt $retryCount")
                if (isShuttingDown || !_uiState.value.isHost) return

                if (reason == WifiP2pManager.BUSY && retryCount < 3) {
                    addLog("Wi-Fi hardware busy, retrying squad creation (${retryCount + 1}/3)...")
                    mainHandler.postDelayed({
                        val activeCh = ensureChannel()
                        if (activeCh != null && !isShuttingDown && _uiState.value.isHost) {
                            performCreateGroup(activeCh, finalSquadName, retryCount + 1)
                        }
                    }, 500)
                } else if (reason == WifiP2pManager.ERROR && retryCount < 2) {
                    addLog("Re-initializing Wi-Fi channel and retrying...")
                    channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
                    mainHandler.postDelayed({
                        val activeCh = ensureChannel()
                        if (activeCh != null && !isShuttingDown && _uiState.value.isHost) {
                            performCreateGroup(activeCh, finalSquadName, retryCount + 1)
                        }
                    }, 500)
                } else {
                    val errorMsg = when (reason) {
                        WifiP2pManager.BUSY -> "Wi-Fi is busy. Please disconnect from other Wi-Fi networks and try again."
                        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device."
                        else -> "Failed to create Wi-Fi Direct squad ($reason). Disconnect from any active Wi-Fi and try again."
                    }
                    addLog("Failed to create squad room ($reason): $errorMsg")
                    _events.tryEmit(errorMsg)
                    _uiState.update { it.copy(connectionState = "IDLE", isHost = false) }
                }
            }
        })
    }

    private fun bindServerSocket() {
        if (serverSocket != null && serverSocket?.isClosed == false) return
        acceptThread = Thread {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                addLog("Voice Server active on port $PORT")

                while (!isShuttingDown) {
                    val socket = serverSocket?.accept() ?: break
                    if (isShuttingDown) {
                        socket.close()
                        break
                    }
                    // Socket-level keepalive settings
                    socket.keepAlive = true
                    socket.soTimeout = 0 // No read timeout
                    socket.tcpNoDelay = true
                    socket.setPerformancePreferences(0, 1, 0) // Low latency priority
                    socket.sendBufferSize = 64 * 1024
                    socket.receiveBufferSize = 64 * 1024

                    Log.d(TAG, "Host accepted incoming client: ${socket.inetAddress?.hostAddress}")
                    val peerHandler = TcpPeerConnectionHandler(socket, isHostSide = true)
                    peerHandler.start()
                }
            } catch (e: Exception) {
                if (!isShuttingDown) {
                    Log.e(TAG, "TCP Server exception", e)
                    addLog("Voice Server stopped")
                }
            }
        }.apply {
            name = "WifiDirectTcpServerThread"
            start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun advertiseDnsSd(ssid: String, passphrase: String) {
        val ch = channel ?: return
        serviceInfo?.let { sInfo ->
            wifiP2pManager?.removeLocalService(ch, sInfo, null)
            serviceInfo = null
        }

        // Exact lowercase keys: ssid, passphrase, squadName, host, port
        val record = mapOf(
            "ssid" to ssid,
            "passphrase" to passphrase,
            "squadName" to _uiState.value.squadName,
            "host" to _uiState.value.myUsername,
            "port" to PORT.toString()
        )

        val sInfo = WifiP2pDnsSdServiceInfo.newInstance("SquadTalk", SERVICE_TYPE, record)
        serviceInfo = sInfo
        wifiP2pManager?.addLocalService(ch, sInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "DNS-SD Local Service published: ${_uiState.value.squadName} (SSID=$ssid)")
                addLog("Squad room on-air and discoverable!")
                // Also trigger discoverPeers so the host responds to probe queries
                wifiP2pManager?.discoverPeers(ch, null)
            }
            override fun onFailure(code: Int) {
                Log.w(TAG, "Failed to publish DNS-SD service: $code")
            }
        })
    }

    // =========================================================================
    // JOIN SQUAD (ANDROID 10+ ONLY: WifiP2pConfig.Builder & 15s TIMEOUT)
    // =========================================================================
    @SuppressLint("MissingPermission")
    fun joinSquad(squad: DiscoveredWifiSquad, username: String) {
        if (!hasRequiredPermissions()) {
            addLog("Missing required Wi-Fi permissions")
            _events.tryEmit("Missing required Wi-Fi permissions")
            return
        }

        cleanupLocalSockets()
        isShuttingDown = false
        stopDiscovery()
        acquireWifiLock()

        lastJoinedSquad = squad
        lastJoinUsername = username

        val myId = _uiState.value.myId
        VoiceQualityEngine.instance.activeTransport = com.example.walkietalkieapp.dna.model.TransportType.WifiDirect
        VoiceQualityEngine.instance.initialize(context, userId = myId)

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
        addLog("Connecting to ${squad.squadName}...")

        // Android 10+ (API 29+) ONLY: WifiP2pConfig.Builder() direct WPA2 connection
        val config = WifiP2pConfig.Builder()
            .setNetworkName(squad.networkName)
            .setPassphrase(squad.passphrase)
            .build()

        // Start 15-second connecting timeout
        startConnectingTimeout()

        val ch = channel ?: return
        // Cancel any pending connection first, then initiate connect
        wifiP2pManager?.cancelConnect(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                doP2pConnect(ch, config)
            }
            override fun onFailure(reason: Int) {
                doP2pConnect(ch, config)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun doP2pConnect(ch: WifiP2pManager.Channel, config: WifiP2pConfig) {
        wifiP2pManager?.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                addLog("Connecting to Wi-Fi Direct network...")
                Log.d(TAG, "wifiP2pManager.connect() accepted")
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "connect failed code=$reason (0=ERROR, 1=P2P_UNSUPPORTED, 2=BUSY)")
                if (reason == WifiP2pManager.BUSY && _uiState.value.connectionState == "CONNECTING" && !isShuttingDown) {
                    mainHandler.postDelayed({
                        if (_uiState.value.connectionState == "CONNECTING" && !isShuttingDown) {
                            doP2pConnect(ch, config)
                        }
                    }, 1000)
                } else {
                    cancelConnectingTimeout()
                    addLog("Connection failed ($reason)")
                    _uiState.update { it.copy(connectionState = "IDLE") }
                }
            }
        })
    }

    private fun startConnectingTimeout() {
        cancelConnectingTimeout()
        connectingTimeoutRunnable = Runnable {
            if (_uiState.value.connectionState == "CONNECTING") {
                Log.w(TAG, "Connection timed out after 15s")
                val ch = channel
                if (ch != null) {
                    try { wifiP2pManager?.cancelConnect(ch, null) } catch (e: Exception) {}
                }
                addLog("Connection timed out. Try again.")
                _events.tryEmit("Connection timed out. Try again.")
                _uiState.update { it.copy(connectionState = "IDLE") }
                closeClientSocket()
            }
        }
        mainHandler.postDelayed(connectingTimeoutRunnable!!, 15000)
    }

    private fun cancelConnectingTimeout() {
        connectingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        connectingTimeoutRunnable = null
    }

    /** Clean up failed connection and re-attempt join from scratch. */
    @SuppressLint("MissingPermission")
    fun retryJoin() {
        val squad = lastJoinedSquad ?: return
        val username = lastJoinUsername.ifBlank { return }

        Log.d(TAG, "retryJoin: re-attempting connection to ${squad.squadName}")
        addLog("Retrying connection...")

        cancelConnectingTimeout()
        isShuttingDown = true
        tcpConnecting.set(false)
        closeClientSocket()
        releaseWifiLock()

        val ch = channel
        if (ch != null) {
            wifiP2pManager?.cancelConnect(ch, null)
            wifiP2pManager?.removeGroup(ch, null)
            wifiP2pManager?.stopPeerDiscovery(ch, null)
        }

        mainHandler.postDelayed({
            isShuttingDown = false
            joinSquad(squad, username)
        }, 1000)
    }

    // =========================================================================
    // TCP SOCKET ON BACKGROUND THREAD
    // =========================================================================
    private fun connectTcpSocket(hostIp: String, port: Int) {
        if (!tcpConnecting.compareAndSet(false, true)) {
            Log.d(TAG, "TCP connection already in progress, skipping duplicate call")
            return
        }

        ioExecutor.execute {
            try {
                var socket: Socket? = null
                var attempts = 0
                val maxAttempts = 20
                val cleanIp = hostIp.trim().removePrefix("/").ifBlank { "192.168.49.1" }
                Log.d(TAG, "TCP connect starting to $cleanIp:$port (background IO thread)")

                while (attempts < maxAttempts && socket == null && !isShuttingDown) {
                    try {
                        val s = Socket()
                        s.keepAlive = true
                        s.soTimeout = 0 // No read timeout
                        s.tcpNoDelay = true // Disable Nagle's algorithm
                        s.setPerformancePreferences(0, 1, 0) // Low latency priority
                        s.sendBufferSize = 64 * 1024
                        s.receiveBufferSize = 64 * 1024
                        s.connect(InetSocketAddress(cleanIp, port), 3000)
                        socket = s
                    } catch (e: Exception) {
                        socket = null
                        attempts++
                        Log.d(TAG, "TCP attempt $attempts/$maxAttempts to $cleanIp failed: ${e.message}")
                        if (attempts < maxAttempts && !isShuttingDown) {
                            Thread.sleep(400)
                        }
                    }
                }

                if (socket == null || !socket.isConnected) {
                    throw java.io.IOException("Could not connect to host voice server at $cleanIp:$port")
                }

                cancelConnectingTimeout()
                acquireWifiLock()
                clientSocket = socket
                val myId = _uiState.value.myId
                val myUsername = _uiState.value.myUsername

                val handler = TcpPeerConnectionHandler(socket, isHostSide = false)
                clientHandler = handler
                handler.start()

                handler.sendJoinRequest(myId, myUsername)
                mainHandler.post {
                    _uiState.update { it.copy(connectionState = "WAITING_APPROVAL") }
                }
                addLog("Join request sent, waiting for owner approval...")
                _events.tryEmit("Join request sent, waiting for owner approval...")
                Log.d(TAG, "TCP connected successfully to $cleanIp:$port, sent join request")
            } catch (e: Exception) {
                Log.e(TAG, "TCP connection failed", e)
                addLog("Connection failed: ${e.localizedMessage}")
                mainHandler.post {
                    _uiState.update { it.copy(connectionState = "IDLE") }
                }
                closeClientSocket()
                releaseWifiLock()
            } finally {
                tcpConnecting.set(false)
            }
        }
    }

    // =========================================================================
    // FIX #2: CLIENT REQUESTS SYNC AFTER UI IS READY
    // =========================================================================
    fun onUiReady() {
        val state = _uiState.value
        if (state.connectionState == "CONNECTED" && !state.isHost) {
            ioExecutor.execute {
                try {
                    Thread.sleep(300L) // Small delay to ensure UI collector is active
                    clientHandler?.sendBytes(buildFramedPacket(PKT_SYNC_REQUEST, ByteArray(0)))
                    Log.d(TAG, "Sent PKT_SYNC_REQUEST to Host from onUiReady()")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send PKT_SYNC_REQUEST", e)
                }
            }
        }
    }

    // =========================================================================
    // PUSH-TO-TALK & DYNAMIC SPEAKER FLOOR CONTROL
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

    // Release floor when app goes to background
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

        // Start 30-second auto-release timer
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

    private fun sendPacketToAll(type: Byte, payload: ByteArray, excludeHandler: TcpPeerConnectionHandler? = null) {
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
        val packet = ByteArray(4 + length)
        packet[0] = MAGIC_BYTE
        packet[1] = type
        packet[2] = ((length shr 8) and 0xFF).toByte()
        packet[3] = (length and 0xFF).toByte()
        System.arraycopy(payload, 0, packet, 4, length)
        return packet
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

    private fun handleClientDisconnect(clientId: String, handler: TcpPeerConnectionHandler?) {
        clientHandlers.remove(clientId)
        pendingClientHandlers.remove(clientId)
        mainHandler.post {
            _uiState.update { state ->
                state.copy(pendingJoinRequests = state.pendingJoinRequests.filter { it.clientId != clientId })
            }
        }
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

                // Broadcast PKT_FLOOR_RELEASE to ALL remaining clients FIRST
                val payload = JSONObject().apply {
                    put("speakerId", clientId)
                    put("speakerName", memberName)
                }.toString().toByteArray(Charsets.UTF_8)

                sendPacketToAll(PKT_FLOOR_RELEASE, payload)
                onFloorReleasedLocally(clientId)
            }
        }

        // AFTER floor release, broadcast updated member list
        broadcastMembersList()
    }

    // FIX #4: Helper to build complete, authoritative member list
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
                address = "GROUP_OWNER",
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
                        address = handler.socket.inetAddress?.hostAddress ?: "CLIENT",
                        isSpeaking = (_uiState.value.currentSpeakerId == pid)
                    )
                )
            }
        }
        return allMembers
    }

    // FIX #4: Include host in its own member list, broadcast, and update host UI on main thread
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
    // SQUAD OWNER ADMISSION CONTROLS (APPROVE / DECLINE INVITATION)
    // =========================================================================
    fun approveJoinRequest(clientId: String) {
        if (!_uiState.value.isHost) return
        val handler = pendingClientHandlers.remove(clientId) ?: clientHandlers[clientId]
        if (handler == null) {
            Log.w(TAG, "approveJoinRequest: no handler found for client $clientId")
            return
        }

        clientHandlers[clientId] = handler
        mainHandler.post {
            _uiState.update { state ->
                state.copy(pendingJoinRequests = state.pendingJoinRequests.filter { it.clientId != clientId })
            }
        }

        ioExecutor.execute {
            try {
                // 1. Send JOIN_ACK to admitted client
                val ackPayload = JSONObject().apply {
                    put("squadName", _uiState.value.squadName)
                    put("hostId", _uiState.value.myId)
                    put("hostUsername", _uiState.value.myUsername)
                }.toString().toByteArray(Charsets.UTF_8)
                handler.sendBytes(buildFramedPacket(PKT_JOIN_ACK, ackPayload))

                // 2. Send current floor state immediately to the new client
                val isBusy = isChannelBusy && currentFloorHolderId != null
                val floorPayload = if (isBusy) {
                    byteArrayOf(0x01) + (currentFloorHolderId ?: "").toByteArray(Charsets.UTF_8)
                } else {
                    byteArrayOf(0x00)
                }
                handler.sendBytes(buildFramedPacket(PKT_FLOOR_STATE, floorPayload))

                // 3. Broadcast authoritative full member list to ALL squad members
                broadcastMembersList()

                val admittedName = handler.peerUsername ?: clientId
                val msg = "$admittedName joined the squad"
                addLog(msg)
                _events.tryEmit(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Error admitting client $clientId", e)
            }
        }
    }

    fun declineJoinRequest(clientId: String) {
        if (!_uiState.value.isHost) return
        val handler = pendingClientHandlers.remove(clientId)
        mainHandler.post {
            _uiState.update { state ->
                state.copy(pendingJoinRequests = state.pendingJoinRequests.filter { it.clientId != clientId })
            }
        }

        if (handler != null) {
            ioExecutor.execute {
                try {
                    val denyPayload = "Declined by squad owner".toByteArray(Charsets.UTF_8)
                    handler.sendBytes(buildFramedPacket(PKT_JOIN_DENY, denyPayload))
                    Thread.sleep(300L)
                    handler.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending PKT_JOIN_DENY to $clientId", e)
                }
            }
            val name = handler.peerUsername ?: clientId
            val msg = "Declined join request from $name"
            addLog(msg)
            _events.tryEmit(msg)
        }
    }

    // =========================================================================
    // CLEANUP & LEAVE
    // =========================================================================
    private fun cleanupLocalSockets() {
        cancelConnectingTimeout()
        stopKeepAlive()
        floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        floorTimeoutRunnable = null
        currentFloorHolderId = null
        isChannelBusy = false
        isLocallyHoldingFloor = false
        tcpConnecting.set(false)
        serverSocket?.runCatching { close() }
        serverSocket = null
        acceptThread = null

        clientHandlers.values.forEach { it.close() }
        clientHandlers.clear()

        pendingClientHandlers.values.forEach { it.close() }
        pendingClientHandlers.clear()
        mainHandler.post {
            _uiState.update { it.copy(pendingJoinRequests = emptyList()) }
        }

        closeClientSocket()
        releaseWifiLock()
    }

    @SuppressLint("MissingPermission")
    fun leaveSquad() {
        isShuttingDown = true
        cancelConnectingTimeout()
        stopKeepAlive()
        floorTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        floorTimeoutRunnable = null
        currentFloorHolderId = null
        isChannelBusy = false
        isLocallyHoldingFloor = false
        VoiceQualityEngine.instance.stopTransmitting()
        audioRecorder.stop()
        stopDiscovery()
        tcpConnecting.set(false)
        releaseWifiLock()

        ioExecutor.execute {
            val payload = JSONObject().apply { put("id", _uiState.value.myId) }.toString().toByteArray(Charsets.UTF_8)
            sendPacketToAll(PKT_LEAVE, payload)

            cleanupLocalSockets()

            val ch = channel
            if (ch != null) {
                serviceInfo?.let { sInfo ->
                    wifiP2pManager?.removeLocalService(ch, sInfo, null)
                    serviceInfo = null
                }
                wifiP2pManager?.clearLocalServices(ch, null)
                wifiP2pManager?.clearServiceRequests(ch, null)
                wifiP2pManager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "removeGroup succeeded upon leaving squad")
                        isShuttingDown = false
                    }
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "removeGroup failed ($reason) upon leaving squad")
                        isShuttingDown = false
                    }
                })
                wifiP2pManager?.cancelConnect(ch, null)
                wifiP2pManager?.stopPeerDiscovery(ch, null)
            } else {
                isShuttingDown = false
            }
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
                    discoveredSquads = emptyList()
                )
            }
        }
        addLog("Left Wi-Fi Direct squad")
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
        releaseWifiLock()
        ioExecutor.shutdown()
        if (isReceiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            isReceiverRegistered = false
        }
    }

    fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        mainHandler.post {
            val newLogs = listOf("[$time] $msg") + _uiState.value.eventLog
            _uiState.update { it.copy(eventLog = newLogs.take(20), lastActivityTimestamp = System.currentTimeMillis()) }
        }
    }

    // =========================================================================
    // TCP SOCKET WORKER (PER-SOCKET THREAD WITH ATOMIC WRITE LOCK & DISCONNECT DETECTION)
    // =========================================================================
    private inner class TcpPeerConnectionHandler(
        val socket: Socket,
        private val isHostSide: Boolean
    ) : Thread("TcpPeerConnectionHandler-${socket.inetAddress?.hostAddress ?: "peer"}") {

        private val inputStream: InputStream = socket.getInputStream()
        private val outputStream: OutputStream = socket.getOutputStream()
        @Volatile var peerId: String? = null
        @Volatile var peerUsername: String? = null
        @Volatile var lastSeenTimestamp: Long = System.currentTimeMillis()
        @Volatile var lastKeepaliveSent: Long = 0L

        private val writeLock = Any()

        @Volatile
        private var isRunning = true

        private val sendQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>(256)
        private val writerThread = Thread({
            while (isRunning) {
                try {
                    val data = sendQueue.take()
                    synchronized(writeLock) {
                        outputStream.write(data)
                        outputStream.flush()
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Socket write failed: ${e.message}")
                        close()
                    }
                    break
                }
            }
        }, "TcpWriter-${socket.inetAddress?.hostAddress ?: "peer"}").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }

        override fun run() {
            try {
                // Intelligence Engine: Connected
                CommunicationDnaEngine.wifiDirectAdapter?.reportConnectionState(true, 1)
                
                val input = inputStream
                val headerBuffer = ByteArray(4)

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

                    // Any packet received updates lastSeenTimestamp
                    lastSeenTimestamp = System.currentTimeMillis()

                    try {
                        handleReceivedPacket(pktType, payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing packet type $pktType: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.d(TAG, "TCP Peer connection ended: ${socket.inetAddress?.hostAddress} (${e.message})")
                }
                // Intelligence Engine: Error
                CommunicationDnaEngine.wifiDirectAdapter?.reportSocketError()
            } finally {
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
                    val device = json.optString("device", "Unknown Device")
                    peerId = id
                    peerUsername = username
                    lastSeenTimestamp = System.currentTimeMillis()

                    if (isHostSide) {
                        // Admission Gate: Buffer in pending requests, await host approve/decline in SquadRoom
                        pendingClientHandlers[id] = this@TcpPeerConnectionHandler
                        val req = WifiJoinRequest(clientId = id, username = username, deviceName = device)
                        mainHandler.post {
                            _uiState.update { state ->
                                state.copy(pendingJoinRequests = state.pendingJoinRequests.filter { it.clientId != id } + req)
                            }
                        }
                        val msg = "Join request from $username ($device)"
                        addLog(msg)
                        _events.tryEmit(msg)
                    }
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

                    audioPlayer.start()
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

                PKT_JOIN_DENY -> {
                    val reason = if (payload.isNotEmpty()) String(payload, Charsets.UTF_8) else "Declined by squad owner"
                    addLog("Join request declined: $reason")
                    _events.tryEmit("Join request declined: $reason")
                    mainHandler.post {
                        _uiState.update { it.copy(connectionState = "IDLE") }
                    }
                    leaveSquad()
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
                    // FIX #2: Host handles sync request from ready client
                    if (isHostSide) {
                        Log.d(TAG, "Received PKT_SYNC_REQUEST from client ${peerId ?: socket.inetAddress?.hostAddress}, resending current state")
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
                            sendPacketToAll(PKT_FLOOR_CLAIM, payload, excludeHandler = this@TcpPeerConnectionHandler)
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
                    try {
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
                            sendPacketToAll(PKT_VOICE_CHUNK, payload, excludeHandler = this@TcpPeerConnectionHandler)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing voice chunk", e)
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
                            sendPacketToAll(PKT_FLOOR_RELEASE, payload, excludeHandler = this@TcpPeerConnectionHandler)
                        }
                    } else {
                        isLocallyHoldingFloor = false
                        onFloorReleasedLocally(speakerId)
                    }
                }

                PKT_KEEPALIVE -> {
                    // Client: respond with PKT_KEEPALIVE_ACK
                    sendBytes(buildFramedPacket(PKT_KEEPALIVE_ACK, ByteArray(0)))
                    // Client: We can assume generic ping for client if they don't send their own
                    CommunicationDnaEngine.wifiDirectAdapter?.reportKeepaliveRtt(35L)
                }

                PKT_KEEPALIVE_ACK -> {
                    // Host: update lastSeenTimestamp
                    lastSeenTimestamp = System.currentTimeMillis()
                    if (lastKeepaliveSent > 0) {
                        val rtt = lastSeenTimestamp - lastKeepaliveSent
                        CommunicationDnaEngine.wifiDirectAdapter?.reportKeepaliveRtt(rtt)
                    }
                }

                PKT_LEAVE -> {
                    val json = JSONObject(String(payload, Charsets.UTF_8))
                    val id = json.optString("id")
                    handlePeerDisconnected(id)
                }
            }
        }

        fun sendJoinRequest(myId: String, myUsername: String) {
            val deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
            val payload = JSONObject().apply {
                put("id", myId)
                put("username", myUsername)
                put("device", deviceModel)
            }.toString().toByteArray(Charsets.UTF_8)
            sendBytes(buildFramedPacket(PKT_JOIN_REQ, payload))
        }

        fun sendBytes(data: ByteArray) {
            if (!isRunning) return
            // Non-blocking queue insertion: if queue is full, drop oldest voice frame
            if (!sendQueue.offer(data)) {
                sendQueue.poll() // drop oldest
                sendQueue.offer(data)
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
                    handleClientDisconnect(id, this@TcpPeerConnectionHandler)
                }
            } else {
                // Client side: gracefully disconnect, release P2P group and resume radar scanning
                leaveSquad()
                mainHandler.post {
                    addLog("Disconnected from room. Resuming radar scan...")
                    _events.tryEmit("Disconnected from room. Resuming radar scan...")
                    startDiscovery()
                }
            }
            // Intelligence Engine: Disconnected
            CommunicationDnaEngine.wifiDirectAdapter?.reportConnectionState(false, 0)
        }

        fun close() {
            if (!isRunning) return
            isRunning = false
            writerThread.interrupt()
            outputStream.runCatching { close() }
            inputStream.runCatching { close() }
            socket.runCatching { close() }
        }
    }
}
