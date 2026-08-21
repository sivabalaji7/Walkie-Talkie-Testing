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
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class AuthResult {
    data class Success(val userId: String, val username: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object SupabaseAuthManager {

    private const val TAG = "SupabaseAuthManager"
    private const val SUPABASE_URL = "https://crlfqcrhsjybrebbbaww.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNybGZxY3Joc2p5YnJlYmJiYXd3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODczMTA5OTIsImV4cCI6MjEwMjg4Njk5Mn0.cQkNISzEEfLp6WAC-HSYGsJ56_LycXNi6IoU3j0idVY"
    private const val PASSWORD_SALT = "SquadTalk_Salt_2026#"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun hashPassword(password: String): String {
        val salted = "$password$PASSWORD_SALT"
        val bytes = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun signUp(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.length < 3) {
            return@withContext AuthResult.Error("Username must be at least 3 characters")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters")
        }

        try {
            // 1. Check if user already exists
            val existing = getUserByUsername(cleanUsername)
            if (existing != null) {
                return@withContext AuthResult.Error("Username '$cleanUsername' is already taken")
            }

            // 2. Insert new user
            val passwordHash = hashPassword(password)
            val bodyJson = JSONObject().apply {
                put("username", cleanUsername)
                put("password_hash", passwordHash)
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/users")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Prefer", "return=representation")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(responseBody)
                    if (jsonArray.length() > 0) {
                        val userObj = jsonArray.getJSONObject(0)
                        val userId = userObj.getString("id")
                        val returnedUsername = userObj.getString("username")
                        return@withContext AuthResult.Success(userId, returnedUsername)
                    }
                } else {
                    Log.e(TAG, "SignUp HTTP error ${response.code}: $responseBody")
                    if (responseBody.contains("unique", ignoreCase = true) || responseBody.contains("duplicate", ignoreCase = true)) {
                        return@withContext AuthResult.Error("Username '$cleanUsername' is already taken")
                    }
                    return@withContext AuthResult.Error("Sign up failed (HTTP ${response.code})")
                }
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet / DNS resolution failure", e)
            return@withContext AuthResult.Error("Network error: Cannot reach server. Please check your internet connection or restart the emulator.")
        } catch (e: Exception) {
            Log.e(TAG, "SignUp exception", e)
            return@withContext AuthResult.Error(e.localizedMessage ?: "Network error during sign up")
        }

        return@withContext AuthResult.Error("Registration failed. Please try again.")
    }

    suspend fun signIn(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty() || password.isEmpty()) {
            return@withContext AuthResult.Error("Please enter username and password")
        }

        try {
            val userRecord = getUserByUsername(cleanUsername)
                ?: return@withContext AuthResult.Error("User '$cleanUsername' not found")

            val expectedHash = userRecord.getString("password_hash")
            val inputHash = hashPassword(password)

            if (expectedHash == inputHash) {
                val userId = userRecord.getString("id")
                val foundUsername = userRecord.getString("username")
                return@withContext AuthResult.Success(userId, foundUsername)
            } else {
                return@withContext AuthResult.Error("Invalid password. Please try again.")
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet / DNS resolution failure", e)
            return@withContext AuthResult.Error("Network error: Cannot reach server. Please check your internet connection or restart the emulator.")
        } catch (e: Exception) {
            Log.e(TAG, "SignIn exception", e)
            return@withContext AuthResult.Error(e.localizedMessage ?: "Network error during sign in")
        }
    }

    private fun getUserByUsername(username: String): JSONObject? {
        val encodedUsername = URLEncoder.encode(username, "UTF-8")
        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/users?username=eq.$encodedUsername&select=id,username,password_hash")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val jsonArray = JSONArray(responseBody)
                if (jsonArray.length() > 0) {
                    return jsonArray.getJSONObject(0)
                }
            } else {
                Log.e(TAG, "getUserByUsername HTTP error ${response.code}")
            }
        }
        return null
    }
}
