package com.example.walkietalkieapp.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val squadPrefs: SharedPreferences = context.getSharedPreferences(SQUAD_PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "walkie_talkie_session"
        private const val SQUAD_PREFS_NAME = "squad_talk_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"
        private const val KEY_CALL_SIGN = "key_call_sign"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun saveSession(userId: String, username: String) {
        val cleanUsername = username.trim()
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_ID, userId)
            putString(KEY_USERNAME, cleanUsername)
            putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
        // Sync to squad talk offline prefs as well
        squadPrefs.edit().putString(KEY_CALL_SIGN, cleanUsername).apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getUsername(): String {
        return prefs.getString(KEY_USERNAME, "") ?: ""
    }

    fun getUserId(): String {
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }

    fun getCallSign(): String {
        val sessionName = getUsername()
        if (sessionName.isNotBlank()) return sessionName
        
        val savedSquadCallSign = squadPrefs.getString(KEY_CALL_SIGN, "") ?: ""
        if (savedSquadCallSign.isNotBlank()) return savedSquadCallSign

        val defaultModel = Build.MODEL.take(10).replace(" ", "-").uppercase()
        return "RADIO-$defaultModel"
    }

    fun saveCallSign(name: String) {
        val clean = name.trim().ifBlank { "OPERATOR" }
        squadPrefs.edit().putString(KEY_CALL_SIGN, clean).apply()
        if (isLoggedIn()) {
            prefs.edit().putString(KEY_USERNAME, clean).apply()
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        squadPrefs.edit().remove(KEY_CALL_SIGN).apply()
    }
}
