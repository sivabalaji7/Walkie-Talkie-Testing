package com.example.walkietalkieapp.supabase

import com.example.walkietalkieapp.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

import kotlin.time.Duration.Companion.seconds

object SupabaseClientManager {
    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime) {
            connectOnSubscribe = true
            disconnectOnNoSubscriptions = false
            reconnectDelay = 2.seconds
            heartbeatInterval = 15.seconds
        }
    }
}
