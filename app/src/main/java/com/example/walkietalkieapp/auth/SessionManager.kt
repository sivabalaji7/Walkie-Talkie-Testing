package com.example.walkietalkieapp.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context, PREF_NAME)
    private val squadPrefs: SharedPreferences = createEncryptedPrefs(context, SQUAD_PREFS_NAME)

    companion object {
        private const val TAG = "SessionManager"
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

        private fun createEncryptedPrefs(context: Context, prefName: String): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val encrypted = EncryptedSharedPreferences.create(
                    context,
                    "${prefName}_encrypted",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                // Seamlessly migrate legacy plaintext preferences on first launch
                val legacyPrefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val allLegacy = legacyPrefs.all
                if (allLegacy.isNotEmpty()) {
                    val editor = encrypted.edit()
                    for ((k, v) in allLegacy) {
                        when (v) {
                            is String -> editor.putString(k, v)
                            is Boolean -> editor.putBoolean(k, v)
                            is Long -> editor.putLong(k, v)
                            is Int -> editor.putInt(k, v)
                            is Float -> editor.putFloat(k, v)
                        }
                    }
                    editor.apply()
                    legacyPrefs.edit().clear().apply()
                    Log.i(TAG, "Migrated legacy plaintext preferences for $prefName to hardware-backed EncryptedSharedPreferences")
                }

                encrypted
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences initialization notice: ${e.message}. Using private fallback.", e)
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
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
