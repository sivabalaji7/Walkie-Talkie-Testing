package com.example.walkietalkieapp.auth

import android.util.Log
import com.example.walkietalkieapp.supabase.SupabaseClientManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.UnknownHostException

sealed class AuthResult {
    data class Success(val userId: String, val username: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object SupabaseAuthManager {

    private const val TAG = "SupabaseAuthManager"

    fun toEmail(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.contains("@")) {
            trimmed.lowercase()
        } else {
            val sanitized = trimmed.lowercase().replace(Regex("[^a-z0-9_]"), "")
            "$sanitized@squadtalk.app"
        }
    }

    suspend fun signUp(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.length < 3) {
            return@withContext AuthResult.Error("Username must be at least 3 characters")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters")
        }

        val email = toEmail(cleanUsername)

        try {
            // 1. Check if username is already taken in public.users
            val isTaken = try {
                val existingList = SupabaseClientManager.client.from("users").select {
                    filter {
                        eq("username", cleanUsername)
                    }
                }.decodeList<JsonObject>()
                existingList.isNotEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "Username check warning (proceeding): ${e.message}")
                false
            }

            if (isTaken) {
                return@withContext AuthResult.Error("Username '$cleanUsername' is already taken")
            }

            // 2. Sign up using official Supabase Auth
            SupabaseClientManager.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = buildJsonObject {
                    put("username", cleanUsername)
                }
            }

            // 3. Ensure user is signed in & get session
            var currentUser = SupabaseClientManager.client.auth.currentUserOrNull()
            if (currentUser == null) {
                // If signUpWith didn't auto-sign-in, call signInWith
                SupabaseClientManager.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                currentUser = SupabaseClientManager.client.auth.currentUserOrNull()
            }

            if (currentUser != null) {
                val userId = currentUser.id
                val userMetadataName = currentUser.userMetadata?.get("username")?.jsonPrimitive?.contentOrNull
                val finalUsername = userMetadataName ?: cleanUsername
                Log.i(TAG, "SignUp successful for $finalUsername ($userId)")
                return@withContext AuthResult.Success(userId, finalUsername)
            } else {
                return@withContext AuthResult.Error("Authentication succeeded but session could not be established.")
            }

        } catch (e: UnknownHostException) {
            Log.e(TAG, "Network error during sign up", e)
            return@withContext AuthResult.Error("Network error: Cannot reach Supabase. Check your internet connection.")
        } catch (e: Exception) {
            Log.e(TAG, "SignUp exception: ${e::class.java.simpleName}", e)
            val msg = e.localizedMessage ?: e.message ?: "Sign up failed"
            if (msg.contains("already registered", ignoreCase = true) || msg.contains("unique", ignoreCase = true)) {
                return@withContext AuthResult.Error("Username '$cleanUsername' is already registered")
            }
            return@withContext AuthResult.Error(msg)
        }
    }

    suspend fun signIn(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty() || password.isEmpty()) {
            return@withContext AuthResult.Error("Please enter username and password")
        }

        val email = toEmail(cleanUsername)

        try {
            SupabaseClientManager.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val currentUser = SupabaseClientManager.client.auth.currentUserOrNull()
            if (currentUser != null) {
                val userId = currentUser.id
                val userMetadataName = currentUser.userMetadata?.get("username")?.jsonPrimitive?.contentOrNull
                val finalUsername = userMetadataName ?: cleanUsername
                Log.i(TAG, "SignIn successful for $finalUsername ($userId)")
                return@withContext AuthResult.Success(userId, finalUsername)
            } else {
                return@withContext AuthResult.Error("Sign in failed: No user session found.")
            }

        } catch (e: UnknownHostException) {
            Log.e(TAG, "Network error during sign in", e)
            return@withContext AuthResult.Error("Network error: Cannot reach Supabase. Check your internet connection.")
        } catch (e: Exception) {
            Log.e(TAG, "SignIn exception: ${e::class.java.simpleName}", e)
            val msg = e.localizedMessage ?: e.message ?: "Sign in failed"
            if (msg.contains("Invalid login credentials", ignoreCase = true) || msg.contains("invalid_grant", ignoreCase = true)) {
                return@withContext AuthResult.Error("Invalid username or password. Please try again.")
            }
            return@withContext AuthResult.Error(msg)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            SupabaseClientManager.client.auth.signOut()
            Log.i(TAG, "SignOut successful")
        } catch (e: Exception) {
            Log.w(TAG, "Error during signOut", e)
        }
    }
}
