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
        private const val KEY_CACHED_ROOMS = "cached_rooms_json"
        private const val KEY_CACHED_MEMBERS = "cached_members_json"

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
        squadPrefs.edit().remove(KEY_CALL_SIGN).remove(KEY_CACHED_ROOMS).remove(KEY_CACHED_MEMBERS).apply()
    }

    fun saveCachedRooms(rooms: List<Room>) {
        try {
            val jsonArray = org.json.JSONArray()
            rooms.forEach { room ->
                val obj = org.json.JSONObject().apply {
                    put("id", room.id)
                    put("name", room.name)
                    put("code", room.code)
                    put("ownerId", room.ownerId)
                }
                jsonArray.put(obj)
            }
            squadPrefs.edit().putString(KEY_CACHED_ROOMS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving cached rooms: ${e.message}")
        }
    }

    fun getCachedRooms(): List<Room> {
        val jsonStr = squadPrefs.getString(KEY_CACHED_ROOMS, null) ?: return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<Room>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Room(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        code = obj.optString("code", ""),
                        ownerId = obj.optString("ownerId", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached rooms: ${e.message}")
            emptyList()
        }
    }

    fun saveCachedRoomMembers(membersMap: Map<String, List<com.example.walkietalkieapp.ui.walkie.SquadMember>>) {
        try {
            val rootObj = org.json.JSONObject()
            membersMap.forEach { (roomKey, members) ->
                val arr = org.json.JSONArray()
                members.forEach { m ->
                    val mObj = org.json.JSONObject().apply {
                        put("name", m.name)
                        put("avatar", m.avatar)
                        put("online", m.online)
                        put("isSpeaking", m.isSpeaking)
                        put("isOwner", m.isOwner)
                    }
                    arr.put(mObj)
                }
                rootObj.put(roomKey, arr)
            }
            squadPrefs.edit().putString(KEY_CACHED_MEMBERS, rootObj.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving cached room members: ${e.message}")
        }
    }

    fun getCachedRoomMembers(): Map<String, List<com.example.walkietalkieapp.ui.walkie.SquadMember>> {
        val jsonStr = squadPrefs.getString(KEY_CACHED_MEMBERS, null) ?: return emptyMap()
        return try {
            val rootObj = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, List<com.example.walkietalkieapp.ui.walkie.SquadMember>>()
            val keys = rootObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = rootObj.getJSONArray(key)
                val mList = mutableListOf<com.example.walkietalkieapp.ui.walkie.SquadMember>()
                for (i in 0 until arr.length()) {
                    val mObj = arr.getJSONObject(i)
                    mList.add(
                        com.example.walkietalkieapp.ui.walkie.SquadMember(
                            name = mObj.optString("name", ""),
                            avatar = mObj.optString("avatar", ""),
                            online = mObj.optBoolean("online", false),
                            isSpeaking = mObj.optBoolean("isSpeaking", false),
                            isOwner = mObj.optBoolean("isOwner", false)
                        )
                    )
                }
                map[key] = mList
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached room members: ${e.message}")
            emptyMap()
        }
    }
}
