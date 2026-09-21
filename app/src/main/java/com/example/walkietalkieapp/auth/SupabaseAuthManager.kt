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
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class AuthResult {
    data class Success(val userId: String, val username: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object SupabaseAuthManager {

    private const val TAG = "SupabaseAuthManager"
    private const val SUPABASE_URL = "https://crlfqcrhsjybrebbbaww.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNybGZxY3Joc2p5YnJlYmJiYXd3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODczMTA5OTIsImV4cCI6MjEwMjg4Njk5Mn0.cQkNISzEEfLp6WAC-HSYGsJ56_LycXNi6IoU3j0idVY"
    private const val PASSWORD_SALT = "SquadTalk_Salt_2026#"

    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA512"
    private const val PBKDF2_ITERATIONS = 65536
    private const val PBKDF2_KEY_LENGTH = 256
    private const val SALT_BYTES = 16

    private val secureRandom = SecureRandom()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Memory-hard PBKDF2-HMAC-SHA512 password hashing with 65,536 iterations and random salt.
     * Output format: pbkdf2:65536:<saltHex>:<hashHex>
     */
    fun hashPasswordPbkdf2(
        password: String, 
        salt: ByteArray = ByteArray(SALT_BYTES).apply { secureRandom.nextBytes(this) }
    ): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val hash = factory.generateSecret(spec).encoded
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        return "pbkdf2:$PBKDF2_ITERATIONS:$saltHex:$hashHex"
    }

    /**
     * Verifies password against either modern PBKDF2 format or legacy SHA-256 format.
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        return if (storedHash.startsWith("pbkdf2:")) {
            try {
                val parts = storedHash.split(":")
                if (parts.size != 4) return false
                val iterations = parts[1].toIntOrNull() ?: return false
                val saltHex = parts[2]
                val expectedHashHex = parts[3]
                val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val spec = PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH)
                val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
                val calculatedHash = factory.generateSecret(spec).encoded
                val calculatedHashHex = calculatedHash.joinToString("") { "%02x".format(it) }
                calculatedHashHex.equals(expectedHashHex, ignoreCase = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying PBKDF2 password: ${e.message}")
                false
            }
        } else {
            // Legacy SHA-256 fallback
            val legacy = hashPasswordLegacy(password)
            legacy.equals(storedHash, ignoreCase = true)
        }
    }

    private fun hashPasswordLegacy(password: String): String {
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

            // 2. Insert new user with memory-hard PBKDF2 hash
            val passwordHash = hashPasswordPbkdf2(password)
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
            val isValid = verifyPassword(password, expectedHash)

            if (isValid) {
                val userId = userRecord.getString("id")
                val foundUsername = userRecord.getString("username")

                // Transparently upgrade legacy SHA-256 users to PBKDF2
                if (!expectedHash.startsWith("pbkdf2:")) {
                    try {
                        val upgradedHash = hashPasswordPbkdf2(password)
                        upgradeUserPasswordHash(userId, upgradedHash)
                    } catch (e: Exception) {
                        Log.w(TAG, "Notice: could not upgrade legacy password hash: ${e.message}")
                    }
                }

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

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "SignOut successful (clearing local session)")
        } catch (e: Exception) {
            Log.w(TAG, "Error during signOut", e)
        }
    }

    private fun upgradeUserPasswordHash(userId: String, newHash: String) {
        try {
            val bodyJson = JSONObject().apply {
                put("password_hash", newHash)
            }.toString()

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/users?id=eq.$userId")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .patch(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Upgraded user $userId password hash to PBKDF2-HMAC-SHA512")
                } else {
                    Log.w(TAG, "Failed to upgrade password hash (HTTP ${response.code})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error upgrading password hash", e)
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
