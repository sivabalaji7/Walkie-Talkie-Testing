package com.example.walkietalkieapp.auth

import android.util.Log
import com.example.walkietalkieapp.supabase.SupabaseClientManager
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object SupabaseRealtimeManager {

    private const val TAG = "SupabaseDbRealtime"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var channel: RealtimeChannel? = null
    private var listenJob: Job? = null

    private val listeners = ConcurrentHashMap<String, () -> Unit>()

    fun addListener(key: String, onUpdate: () -> Unit) {
        listeners[key] = onUpdate
        ensureSubscribed()
    }

    fun removeListener(key: String) {
        listeners.remove(key)
        if (listeners.isEmpty()) {
            unsubscribe()
        }
    }

    private fun ensureSubscribed() {
        if (channel != null) return
        scope.launch {
            try {
                Log.d(TAG, "Subscribing to Postgres DB changes using unified Supabase Realtime client...")
                val ch = SupabaseClientManager.client.channel("db-changes")
                channel = ch

                listenJob = launch {
                    ch.postgresChangeFlow<PostgresAction>(schema = "public").collect { action ->
                        Log.d(TAG, "Postgres change received via unified client: $action")
                        notifyListeners()
                    }
                }

                ch.subscribe()
                Log.d(TAG, "Subscribed to DB changes successfully via unified SDK WebSocket")
            } catch (e: Exception) {
                Log.e(TAG, "Error subscribing to DB changes", e)
            }
        }
    }

    private fun unsubscribe() {
        listenJob?.cancel()
        listenJob = null
        val currentChannel = channel
        channel = null
        scope.launch {
            try {
                currentChannel?.unsubscribe()
            } catch (e: Exception) {
                Log.e(TAG, "Error unsubscribing DB changes channel", e)
            }
        }
    }

    private fun notifyListeners() {
        listeners.values.forEach { listener ->
            try {
                listener.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying DB change listener", e)
            }
        }
    }

    fun connect() {
        ensureSubscribed()
    }

    fun disconnect() {
        unsubscribe()
    }
}
