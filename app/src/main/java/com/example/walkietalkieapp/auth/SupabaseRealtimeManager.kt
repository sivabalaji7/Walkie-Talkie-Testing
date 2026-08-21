package com.example.walkietalkieapp.auth

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SupabaseRealtimeManager {

    private const val TAG = "SupabaseRealtime"
    private const val SUPABASE_URL = "https://crlfqcrhsjybrebbbaww.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNybGZxY3Joc2p5YnJlYmJiYXd3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODczMTA5OTIsImV4cCI6MjEwMjg4Njk5Mn0.cQkNISzEEfLp6WAC-HSYGsJ56_LycXNi6IoU3j0idVY"

    private val wsUrl = SUPABASE_URL.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=" + SUPABASE_ANON_KEY + "&vsn=1.0.0"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var shouldReconnect = true
    private val mainHandler = Handler(Looper.getMainLooper())

    private val listeners = ConcurrentHashMap<String, () -> Unit>()

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendHeartbeat()
            mainHandler.postDelayed(this, 25000)
        }
    }

    fun addListener(key: String, onUpdate: () -> Unit) {
        listeners[key] = onUpdate
        connect()
    }

    fun removeListener(key: String) {
        listeners.remove(key)
        if (listeners.isEmpty()) {
            disconnect()
        }
    }

    fun connect() {
        if (isConnected || webSocket != null) return
        shouldReconnect = true
        Log.d(TAG, "Connecting to Supabase Realtime WebSocket...")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Supabase Realtime connected!")
                isConnected = true
                mainHandler.post(heartbeatRunnable)
                joinRealtimeChannels(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val event = json.optString("event")
                    if (event == "postgres_changes") {
                        Log.d(TAG, "Postgres change received: $text")
                        notifyListeners()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing realtime message: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Supabase Realtime closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Supabase Realtime closed: $code / $reason")
                isConnected = false
                SupabaseRealtimeManager.webSocket = null
                mainHandler.removeCallbacks(heartbeatRunnable)
                if (shouldReconnect && listeners.isNotEmpty()) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Supabase Realtime error: ${t.message}")
                isConnected = false
                SupabaseRealtimeManager.webSocket = null
                mainHandler.removeCallbacks(heartbeatRunnable)
                if (shouldReconnect && listeners.isNotEmpty()) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun joinRealtimeChannels(ws: WebSocket) {
        try {
            val joinPayload = JSONObject().apply {
                put("topic", "realtime:public")
                put("event", "phx_join")
                put("payload", JSONObject().apply {
                    put("config", JSONObject().apply {
                        put("postgres_changes", JSONArray().apply {
                            put(JSONObject().apply {
                                put("event", "*")
                                put("schema", "public")
                                put("table", "rooms")
                            })
                            put(JSONObject().apply {
                                put("event", "*")
                                put("schema", "public")
                                put("table", "room_members")
                            })
                        })
                    })
                })
                put("ref", "1")
            }
            ws.send(joinPayload.toString())
            Log.d(TAG, "Subscribed to postgres_changes for rooms and room_members")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send join payload: ${e.message}")
        }
    }

    private fun sendHeartbeat() {
        try {
            val heartbeat = JSONObject().apply {
                put("topic", "phoenix")
                put("event", "heartbeat")
                put("payload", JSONObject())
                put("ref", "hb_${System.currentTimeMillis()}")
            }
            webSocket?.send(heartbeat.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        mainHandler.postDelayed({
            if (shouldReconnect && !isConnected && listeners.isNotEmpty()) {
                connect()
            }
        }, 3000)
    }

    private fun notifyListeners() {
        mainHandler.post {
            listeners.values.forEach { listener ->
                try {
                    listener.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking listener: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        mainHandler.removeCallbacks(heartbeatRunnable)
        webSocket?.close(1000, "Normal Closure")
        webSocket = null
        isConnected = false
    }
}
