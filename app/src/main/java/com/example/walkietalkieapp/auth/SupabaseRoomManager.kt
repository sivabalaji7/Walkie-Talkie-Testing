package com.example.walkietalkieapp.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import com.example.walkietalkieapp.supabase.SupabaseClientManager
import java.net.URLEncoder

data class Room(
    val id: String,
    val name: String,
    val code: String,
    val ownerId: String
)

data class RoomMember(
    val userId: String,
    val username: String,
    val status: String = "APPROVED"
)

data class RoomMemberRequest(
    val roomId: String,
    val userId: String,
    val username: String,
    val status: String,
    val roomName: String? = null,
    val roomCode: String? = null
)

sealed class RoomResult<out T> {
    data class Success<out T>(val data: T) : RoomResult<T>()
    data class Error(val message: String) : RoomResult<Nothing>()
}

object SupabaseRoomManager {

    private const val TAG = "SupabaseRoomManager"
    private val SUPABASE_URL get() = SupabaseClientManager.SUPABASE_URL
    private val SUPABASE_ANON_KEY get() = SupabaseClientManager.SUPABASE_ANON_KEY

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomString = (1..4).map { chars.random() }.joinToString("")
        return "WT-$randomString"
    }

    suspend fun createRoom(name: String, ownerId: String, ownerUsername: String): RoomResult<Room> = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext RoomResult.Error("Room name cannot be empty")
        val code = generateRoomCode()
        
        try {
            // 1. Insert into rooms
            val roomJson = JSONObject().apply {
                put("name", name.trim())
                put("code", code)
                put("owner_id", ownerId)
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rooms")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Prefer", "return=representation")
                .post(roomJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                Log.e(TAG, "createRoom HTTP error ${response.code}: $responseBody")
                return@withContext RoomResult.Error("Failed to create room")
            }

            val jsonArray = JSONArray(responseBody)
            if (jsonArray.length() == 0) return@withContext RoomResult.Error("Room created but no data returned")
            
            val roomObj = jsonArray.getJSONObject(0)
            val roomId = roomObj.getString("id")
            val createdRoom = Room(
                id = roomId,
                name = roomObj.getString("name"),
                code = roomObj.getString("code"),
                ownerId = roomObj.getString("owner_id")
            )

            // 2. Insert owner into room_members as APPROVED
            val memberJson = JSONObject().apply {
                put("room_id", roomId)
                put("user_id", ownerId)
                put("username", ownerUsername)
                put("status", "APPROVED")
            }.toString()

            val memberRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/room_members")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .post(memberJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(memberRequest).execute().use { memResponse ->
                if (!memResponse.isSuccessful) {
                    Log.e(TAG, "Failed to add owner to room_members: ${memResponse.body?.string()}")
                    // Even if this fails, the room exists, but this is bad state.
                }
            }

            return@withContext RoomResult.Success(createdRoom)

        } catch (e: Exception) {
            Log.e(TAG, "createRoom exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun getMyRooms(userId: String): RoomResult<List<Room>> = withContext(Dispatchers.IO) {
        try {
            val roomsMap = mutableMapOf<String, Room>()

            // 1. Fetch rooms where user is an APPROVED member
            val memberUrl = "$SUPABASE_URL/rest/v1/room_members?user_id=eq.$userId&status=eq.APPROVED&select=rooms(id,name,code,owner_id)"
            val memberReq = Request.Builder()
                .url(memberUrl)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(memberReq).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(responseBody)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        if (obj.has("rooms") && !obj.isNull("rooms")) {
                            val rObj = obj.getJSONObject("rooms")
                            val id = rObj.getString("id")
                            roomsMap[id] = Room(
                                id = id,
                                name = rObj.getString("name"),
                                code = rObj.getString("code"),
                                ownerId = rObj.getString("owner_id")
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "getMyRooms member query returned code ${response.code}")
                }
            }

            // 2. Also fetch rooms where user is the OWNER
            val ownerUrl = "$SUPABASE_URL/rest/v1/rooms?owner_id=eq.$userId&select=id,name,code,owner_id"
            val ownerReq = Request.Builder()
                .url(ownerUrl)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(ownerReq).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(responseBody)
                    for (i in 0 until jsonArray.length()) {
                        val rObj = jsonArray.getJSONObject(i)
                        val id = rObj.getString("id")
                        if (!roomsMap.containsKey(id)) {
                            roomsMap[id] = Room(
                                id = id,
                                name = rObj.getString("name"),
                                code = rObj.getString("code"),
                                ownerId = rObj.getString("owner_id")
                            )
                        }
                    }
                }
            }

            return@withContext RoomResult.Success(roomsMap.values.toList())
        } catch (e: Exception) {
            Log.e(TAG, "getMyRooms exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun getAllRoomMembersForRooms(roomIds: List<String>): RoomResult<Map<String, List<RoomMember>>> = withContext(Dispatchers.IO) {
        val cleanIds = roomIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return@withContext RoomResult.Success(emptyMap())
        try {
            val inClause = cleanIds.joinToString(",")
            // Fetch all room members regardless of status casing
            val url = "$SUPABASE_URL/rest/v1/room_members?room_id=in.($inClause)&select=room_id,user_id,username,status"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val resultMap = mutableMapOf<String, MutableList<RoomMember>>()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful && responseBody.isNotBlank()) {
                    val jsonArray = JSONArray(responseBody)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val rId = obj.optString("room_id", "")
                        val uId = obj.optString("user_id", "")
                        val uName = obj.optString("username", "")
                        val status = obj.optString("status", "APPROVED")
                        // Accept APPROVED, approved, or blank
                        if (rId.isNotBlank() && uName.isNotBlank() && (status.isEmpty() || status.equals("APPROVED", ignoreCase = true))) {
                            val list = resultMap.getOrPut(rId) { mutableListOf() }
                            if (list.none { it.username.equals(uName, ignoreCase = true) }) {
                                list.add(RoomMember(userId = uId, username = uName, status = "APPROVED"))
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "getAllRoomMembersForRooms response ${response.code}: $responseBody")
                }
            }

            // Also ensure room owners are included for any room missing owner in room_members table
            try {
                val roomsUrl = "$SUPABASE_URL/rest/v1/rooms?id=in.($inClause)&select=id,owner_id"
                val rReq = Request.Builder()
                    .url(roomsUrl)
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .get()
                    .build()
                client.newCall(rReq).execute().use { rRes ->
                    val rBody = rRes.body?.string() ?: ""
                    if (rRes.isSuccessful && rBody.isNotBlank()) {
                        val rArr = JSONArray(rBody)
                        val ownerIds = mutableListOf<String>()
                        val roomOwnerMap = mutableMapOf<String, String>() // roomId -> ownerId
                        for (i in 0 until rArr.length()) {
                            val rObj = rArr.getJSONObject(i)
                            val rid = rObj.optString("id", "")
                            val oid = rObj.optString("owner_id", "")
                            if (rid.isNotBlank() && oid.isNotBlank()) {
                                roomOwnerMap[rid] = oid
                                ownerIds.add(oid)
                            }
                        }

                        if (ownerIds.isNotEmpty()) {
                            val uClause = ownerIds.distinct().joinToString(",")
                            val uUrl = "$SUPABASE_URL/rest/v1/users?id=in.($uClause)&select=id,username"
                            val uReq = Request.Builder()
                                .url(uUrl)
                                .addHeader("apikey", SUPABASE_ANON_KEY)
                                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                                .get()
                                .build()
                            client.newCall(uReq).execute().use { uRes ->
                                val uBody = uRes.body?.string() ?: ""
                                if (uRes.isSuccessful && uBody.isNotBlank()) {
                                    val uArr = JSONArray(uBody)
                                    val userMap = mutableMapOf<String, String>() // userId -> username
                                    for (i in 0 until uArr.length()) {
                                        val uObj = uArr.getJSONObject(i)
                                        userMap[uObj.optString("id")] = uObj.optString("username")
                                    }

                                    roomOwnerMap.forEach { (rid, oid) ->
                                        val ownerUsername = userMap[oid]
                                        if (!ownerUsername.isNullOrBlank()) {
                                            val list = resultMap.getOrPut(rid) { mutableListOf() }
                                            if (list.none { it.username.equals(ownerUsername, ignoreCase = true) }) {
                                                list.add(0, RoomMember(userId = oid, username = ownerUsername, status = "APPROVED"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve missing room owners: ${e.message}")
            }

            return@withContext RoomResult.Success(resultMap)
        } catch (e: Exception) {
            Log.e(TAG, "getAllRoomMembersForRooms exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun getApprovedRoomMembers(roomCodeOrId: String): RoomResult<List<String>> = withContext(Dispatchers.IO) {
        val clean = roomCodeOrId.trim()
        if (clean.isBlank()) return@withContext RoomResult.Success(emptyList())
        try {
            var targetRoomId = clean
            var ownerId: String? = null
            // If clean is likely a code (starts with WT- or length <= 10 without hyphen dashes of UUID), resolve room id
            val isCode = clean.startsWith("WT-", ignoreCase = true) || (clean.length <= 10 && !clean.contains("-"))
            val findRoomUrl = if (isCode) {
                "$SUPABASE_URL/rest/v1/rooms?code=eq.${clean.uppercase()}&select=id,owner_id"
            } else {
                "$SUPABASE_URL/rest/v1/rooms?id=eq.$clean&select=id,owner_id"
            }
            val req = Request.Builder()
                .url(findRoomUrl)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (res.isSuccessful && body.isNotBlank()) {
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val rObj = arr.getJSONObject(0)
                        targetRoomId = rObj.optString("id", clean)
                        ownerId = rObj.optString("owner_id").takeIf { it.isNotBlank() }
                    }
                }
            }

            val url = "$SUPABASE_URL/rest/v1/room_members?room_id=eq.$targetRoomId&select=username,status"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val usernames = mutableListOf<String>()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful && responseBody.isNotBlank()) {
                    val jsonArray = JSONArray(responseBody)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val uname = obj.optString("username", "")
                        val status = obj.optString("status", "APPROVED")
                        if (uname.isNotBlank() && (status.isEmpty() || status.equals("APPROVED", ignoreCase = true))) {
                            usernames.add(uname)
                        }
                    }
                }
            }

            // If owner is not yet in usernames, resolve owner username
            if (!ownerId.isNullOrBlank()) {
                try {
                    val uUrl = "$SUPABASE_URL/rest/v1/users?id=eq.$ownerId&select=username"
                    val uReq = Request.Builder()
                        .url(uUrl)
                        .addHeader("apikey", SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                        .get()
                        .build()
                    client.newCall(uReq).execute().use { uRes ->
                        val uBody = uRes.body?.string() ?: ""
                        if (uRes.isSuccessful && uBody.isNotBlank()) {
                            val uArr = JSONArray(uBody)
                            if (uArr.length() > 0) {
                                val oName = uArr.getJSONObject(0).optString("username")
                                if (oName.isNotBlank() && usernames.none { it.equals(oName, ignoreCase = true) }) {
                                    usernames.add(0, oName)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error resolving owner username: ${e.message}")
                }
            }

            return@withContext RoomResult.Success(usernames.distinct())
        } catch (e: Exception) {
            Log.e(TAG, "getApprovedRoomMembers exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun requestJoin(code: String, userId: String, username: String): RoomResult<String> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) return@withContext RoomResult.Error("Code cannot be empty")

        try {
            // 1. Find room by code
            val roomRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rooms?code=eq.$cleanCode&select=id,name")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            var roomId = ""
            var roomName = ""
            client.newCall(roomRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext RoomResult.Error("Failed to find room")
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext RoomResult.Error("Invalid room code")
                val rObj = arr.getJSONObject(0)
                roomId = rObj.getString("id")
                roomName = rObj.getString("name")
            }

            // 2. Check if already a member or pending
            val checkRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/room_members?room_id=eq.$roomId&user_id=eq.$userId")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(checkRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val status = arr.getJSONObject(0).getString("status")
                        if (status == "APPROVED") return@withContext RoomResult.Error("You are already in this room")
                        if (status == "PENDING") return@withContext RoomResult.Error("Join request is already pending")
                    }
                }
            }

            // 3. Insert as PENDING
            val memberJson = JSONObject().apply {
                put("room_id", roomId)
                put("user_id", userId)
                put("username", username)
                put("status", "PENDING")
            }.toString()

            val insertRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/room_members")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .post(memberJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(insertRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "requestJoin error ${response.code}: $body")
                    return@withContext RoomResult.Error("Failed to send join request")
                }
            }

            // Immediately broadcast join request over realtime so active room owner receives it instantly
            try {
                com.example.walkietalkieapp.socket.SupabaseRealtimeManager.broadcastJoinRequest(
                    roomCode = cleanCode,
                    userId = userId,
                    username = username
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast join request: ${e.message}")
            }

            return@withContext RoomResult.Success(roomName)
        } catch (e: Exception) {
            Log.e(TAG, "requestJoin exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun getPendingRequests(ownerId: String): RoomResult<List<RoomMemberRequest>> = withContext(Dispatchers.IO) {
        try {
            // Find all pending requests for rooms owned by ownerId.
            // Using PostgREST inner join: room_members?status=eq.PENDING&rooms.owner_id=eq.X&select=room_id,user_id,username,status,rooms!inner(name,code)
            val url = "$SUPABASE_URL/rest/v1/room_members?status=eq.PENDING&select=room_id,user_id,username,status,rooms!inner(name,code)&rooms.owner_id=eq.$ownerId"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "getPendingRequests error ${response.code}: $body")
                    return@withContext RoomResult.Error("Failed to fetch requests")
                }

                val requests = mutableListOf<RoomMemberRequest>()
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val roomObj = obj.getJSONObject("rooms")
                    requests.add(RoomMemberRequest(
                        roomId = obj.getString("room_id"),
                        userId = obj.getString("user_id"),
                        username = obj.getString("username"),
                        status = obj.getString("status"),
                        roomName = roomObj.getString("name"),
                        roomCode = roomObj.getString("code")
                    ))
                }
                return@withContext RoomResult.Success(requests)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPendingRequests exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun approveRequest(roomId: String, userId: String): RoomResult<Boolean> = withContext(Dispatchers.IO) {
        updateMemberStatus(roomId, userId, "APPROVED")
    }

    suspend fun declineRequest(roomId: String, userId: String): RoomResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/rest/v1/room_members?room_id=eq.$roomId&user_id=eq.$userId"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .delete() // DELETE request to decline and remove
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "declineRequest error ${response.code}: ${response.body?.string()}")
                    return@withContext RoomResult.Error("Failed to decline request")
                }
                return@withContext RoomResult.Success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "declineRequest exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun leaveRoom(roomId: String, userId: String): RoomResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/rest/v1/room_members?room_id=eq.$roomId&user_id=eq.$userId"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "leaveRoom error ${response.code}: ${response.body?.string()}")
                    return@withContext RoomResult.Error("Failed to leave squad")
                }
                return@withContext RoomResult.Success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "leaveRoom exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun getRoomByCode(code: String): RoomResult<Room> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        try {
            val url = "$SUPABASE_URL/rest/v1/rooms?code=eq.$cleanCode&select=id,name,code,owner_id"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext RoomResult.Error("Failed to find squad")
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext RoomResult.Error("Squad room not found")
                val rObj = arr.getJSONObject(0)
                return@withContext RoomResult.Success(
                    Room(
                        id = rObj.getString("id"),
                        name = rObj.getString("name"),
                        code = rObj.getString("code"),
                        ownerId = rObj.getString("owner_id")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRoomByCode exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun getMemberStatus(roomId: String, userId: String): RoomResult<String?> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/rest/v1/room_members?room_id=eq.$roomId&user_id=eq.$userId&select=status"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext RoomResult.Error("Failed to check status")
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext RoomResult.Success(null)
                val status = arr.getJSONObject(0).getString("status")
                return@withContext RoomResult.Success(status)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMemberStatus exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun getApprovedMembers(roomId: String): RoomResult<List<RoomMember>> = withContext(Dispatchers.IO) {
        val clean = roomId.trim()
        if (clean.isBlank()) return@withContext RoomResult.Success(emptyList())
        try {
            val url = "$SUPABASE_URL/rest/v1/room_members?room_id=eq.$clean&status=eq.APPROVED&select=user_id,username,status"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "getApprovedMembers error ${response.code}: $responseBody")
                    return@withContext RoomResult.Error("Failed to fetch squad members")
                }

                val members = mutableListOf<RoomMember>()
                val jsonArray = JSONArray(responseBody)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val uId = obj.optString("user_id", "")
                    val uName = obj.optString("username", "")
                    val status = obj.optString("status", "APPROVED")
                    if (uId.isNotBlank() && uName.isNotBlank()) {
                        members.add(RoomMember(userId = uId, username = uName, status = status))
                    }
                }
                return@withContext RoomResult.Success(members)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getApprovedMembers exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun transferOwnershipAndLeave(roomId: String, currentOwnerId: String, newOwnerId: String): RoomResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 1. Update owner_id in rooms table
            val updateJson = JSONObject().apply {
                put("owner_id", newOwnerId)
            }.toString()

            val updateRoomRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rooms?id=eq.$roomId")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .patch(updateJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(updateRoomRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "transferOwnership error ${response.code}: $err")
                    return@withContext RoomResult.Error("Failed to transfer ownership")
                }
            }

            // 2. Remove current owner from room_members
            val deleteMemberRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/room_members?room_id=eq.$roomId&user_id=eq.$currentOwnerId")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .delete()
                .build()

            client.newCall(deleteMemberRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to remove previous owner from room_members: ${response.body?.string()}")
                }
            }

            return@withContext RoomResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "transferOwnershipAndLeave exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    suspend fun destroyRoom(roomId: String): RoomResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 1. Delete all room_members
            val deleteMembersRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/room_members?room_id=eq.$roomId")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .delete()
                .build()

            client.newCall(deleteMembersRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Warning deleting room_members: ${response.body?.string()}")
                }
            }

            // 2. Delete room from rooms table
            val deleteRoomRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/rooms?id=eq.$roomId")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .delete()
                .build()

            client.newCall(deleteRoomRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "destroyRoom error ${response.code}: $err")
                    return@withContext RoomResult.Error("Failed to destroy room")
                }
            }

            return@withContext RoomResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "destroyRoom exception", e)
            return@withContext RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    private suspend fun updateMemberStatus(roomId: String, userId: String, status: String): RoomResult<Boolean> {
        try {
            val json = JSONObject().apply {
                put("status", status)
            }.toString()

            val url = "$SUPABASE_URL/rest/v1/room_members?room_id=eq.$roomId&user_id=eq.$userId"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .patch(json.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "updateMemberStatus error ${response.code}: ${response.body?.string()}")
                    return RoomResult.Error("Failed to update status")
                }
                return RoomResult.Success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateMemberStatus exception", e)
            return RoomResult.Error(e.localizedMessage ?: "Network error")
        }
    }
}
