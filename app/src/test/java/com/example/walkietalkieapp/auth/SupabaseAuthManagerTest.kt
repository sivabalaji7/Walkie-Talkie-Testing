package com.example.walkietalkieapp.auth

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SupabaseAuthManagerTest {

    @Test
    fun testPbkdf2HashFormat() {
        val password = "SuperSecretTacticalPassword!2026"
        val hash = SupabaseAuthManager.hashPasswordPbkdf2(password)

        assertNotNull(hash)
        assertTrue("Hash must start with pbkdf2 prefix", hash.startsWith("pbkdf2:65536:"))
        val parts = hash.split(":")
        assertEquals("PBKDF2 string must contain 4 parts", 4, parts.size)
        assertEquals("Iterations must be 65536", "65536", parts[1])
        assertEquals("Salt hex must be 32 chars (16 bytes)", 32, parts[2].length)
        assertEquals("Hash hex must be 64 chars (256 bits)", 64, parts[3].length)
    }

    @Test
    fun testPbkdf2VerificationSuccessAndFailure() {
        val password = "TacticalPassword123"
        val hash = SupabaseAuthManager.hashPasswordPbkdf2(password)

        assertTrue("Valid password must verify true", SupabaseAuthManager.verifyPassword(password, hash))
        assertFalse("Wrong password must verify false", SupabaseAuthManager.verifyPassword("WrongPassword", hash))
        assertFalse("Empty password must verify false", SupabaseAuthManager.verifyPassword("", hash))
    }

    @Test
    fun testDifferentSaltsProduceDifferentHashes() {
        val password = "IdenticalPassword"
        val hash1 = SupabaseAuthManager.hashPasswordPbkdf2(password)
        val hash2 = SupabaseAuthManager.hashPasswordPbkdf2(password)

        assertNotEquals("Different random salts must produce different hash strings", hash1, hash2)
        assertTrue("Both must verify correctly", SupabaseAuthManager.verifyPassword(password, hash1))
        assertTrue("Both must verify correctly", SupabaseAuthManager.verifyPassword(password, hash2))
    }

    @Test
    fun testLegacySha256VerificationCompatibility() {
        val password = "LegacyUserPassword2026"
        val legacySalt = "SquadTalk_Salt_2026#"
        val salted = "$password$legacySalt"
        val legacyHash = MessageDigest.getInstance("SHA-256")
            .digest(salted.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals("Legacy hash must be 64-char hex", 64, legacyHash.length)
        assertFalse("Legacy hash does not start with pbkdf2", legacyHash.startsWith("pbkdf2:"))

        assertTrue(
            "Legacy hash must be verified successfully by verifyPassword",
            SupabaseAuthManager.verifyPassword(password, legacyHash)
        )
        assertFalse(
            "Wrong password against legacy hash must fail",
            SupabaseAuthManager.verifyPassword("IncorrectPassword", legacyHash)
        )
    }
}
