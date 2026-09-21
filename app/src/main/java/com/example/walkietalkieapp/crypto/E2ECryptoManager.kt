package com.example.walkietalkieapp.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * E2ECryptoManager provides zero-knowledge End-to-End Encryption (E2EE)
 * for walkie-talkie signaling and message payloads.
 *
 * Architecture:
 * - ECDH (Elliptic Curve Diffie-Hellman) over NIST P-256 (secp256r1)
 * - SHA-256 Key Derivation Function (KDF) producing 256-bit symmetric keys
 * - AES-256-GCM authenticated encryption with 12-byte random IVs and 128-bit auth tags
 */
object E2ECryptoManager {
    private const val TAG = "E2ECryptoManager"
    private const val EC_CURVE = "secp256r1"
    private const val AES_KEY_ALGO = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    // Ephemeral session keypair generated per room session
    @Volatile
    private var sessionKeyPair: KeyPair? = null

    // Map: Normalized Peer Username/ID -> Derived AES-256 SecretKey
    private val peerSharedKeys = ConcurrentHashMap<String, SecretKey>()

    // Map: Normalized Peer Username/ID -> Public Key Base64
    private val peerPublicKeys = ConcurrentHashMap<String, String>()

    // Squad Room shared key derived from room ID and room code (PBKDF2-HMAC-SHA256)
    @Volatile
    private var squadSharedKey: SecretKey? = null

    init {
        initSession()
    }

    private fun normKey(peerId: String): String = peerId.trim().lowercase()

    private fun logD(msg: String) {
        try {
            android.util.Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logW(msg: String) {
        try {
            android.util.Log.w(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG][WARN] $msg")
        }
    }

    private fun logE(msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(TAG, msg, tr)
        } catch (_: Throwable) {
            System.err.println("[$TAG][ERROR] $msg: ${tr?.message}")
        }
    }

    /**
     * Initializes or refreshes the ephemeral session keypair.
     * Called whenever a user enters a new room or session.
     */
    @Synchronized
    fun initSession(): KeyPair {
        return try {
            val keyGen = KeyPairGenerator.getInstance("EC")
            keyGen.initialize(ECGenParameterSpec(EC_CURVE))
            val kp = keyGen.generateKeyPair()
            sessionKeyPair = kp
            peerSharedKeys.clear()
            peerPublicKeys.clear()
            squadSharedKey = null
            logD("Initialized fresh ECDH P-256 ephemeral session keypair. Fingerprint: ${getMyFingerprint()}")
            kp
        } catch (e: Exception) {
            logE("Failed to generate ECDH keypair", e)
            throw e
        }
    }

    /**
     * Resets the session crypto state when leaving a room.
     */
    @Synchronized
    fun resetSession() {
        peerSharedKeys.clear()
        peerPublicKeys.clear()
        squadSharedKey = null
        sessionKeyPair = null
        logD("E2EE session reset")
    }

    /**
     * Returns the Base64-encoded X.509 public key for sharing with squad peers.
     */
    fun getMyPublicKeyBase64(): String? {
        val kp = sessionKeyPair ?: initSession()
        return try {
            Base64.getEncoder().encodeToString(kp.public.encoded)
        } catch (e: Exception) {
            logE("Error encoding public key: ${e.message}")
            null
        }
    }

    /**
     * Generates a human-verifiable SHA-256 fingerprint for public key verification.
     */
    fun getMyFingerprint(): String {
        val pubBase64 = getMyPublicKeyBase64() ?: return "UNKNOWN"
        return computeFingerprint(pubBase64)
    }

    /**
     * Computes a 16-character hex fingerprint from a Base64-encoded public key.
     */
    fun computeFingerprint(pubKeyBase64: String): String {
        return try {
            val bytes = Base64.getDecoder().decode(pubKeyBase64)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.take(8).joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "ERR"
        }
    }

    /**
     * Ingests a peer's public key (received during key-exchange or peer-join)
     * and derives the shared AES-256 symmetric key via ECDH + SHA-256 KDF.
     *
     * @param peerId Peer's unique identifier/username
     * @param peerPublicKeyBase64 Peer's Base64-encoded X.509 public key
     * @return true if shared secret was derived and stored successfully
     */
    fun registerPeerPublicKey(peerId: String, peerPublicKeyBase64: String): Boolean {
        val cleanPeerId = normKey(peerId)
        if (cleanPeerId.isBlank() || peerPublicKeyBase64.isBlank()) return false

        return try {
            val kp = sessionKeyPair ?: initSession()

            // 1. Decode peer's X.509 public key
            val pubBytes = Base64.getDecoder().decode(peerPublicKeyBase64)
            val keyFactory = KeyFactory.getInstance("EC")
            val peerPubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

            // 2. Perform ECDH Key Agreement
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(kp.private)
            keyAgreement.doPhase(peerPubKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            // 3. Derive 256-bit AES key using SHA-256 KDF
            val sha256 = MessageDigest.getInstance("SHA-256")
            val derivedAesKeyBytes = sha256.digest(sharedSecret)
            val secretKey = SecretKeySpec(derivedAesKeyBytes, AES_KEY_ALGO)

            peerSharedKeys[cleanPeerId] = secretKey
            peerPublicKeys[cleanPeerId] = peerPublicKeyBase64

            logD("Derived E2EE AES-256 key for peer [$cleanPeerId]. Peer fingerprint: ${computeFingerprint(peerPublicKeyBase64)}")
            true
        } catch (e: Exception) {
            logE("Error deriving shared secret for peer [$cleanPeerId]: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if a shared symmetric key exists for the target peer.
     */
    fun hasPeerKey(peerId: String): Boolean {
        return peerSharedKeys.containsKey(normKey(peerId))
    }

    /**
     * Returns the count of peers with established E2EE keys in the current session.
     */
    fun getEncryptedPeersCount(): Int = peerSharedKeys.size

    /**
     * Encrypts plaintext string using AES-256-GCM with a random 12-byte IV.
     * Output format: Base64( IV [12 bytes] + CiphertextWithTag )
     *
     * @param peerId Recipient peer ID
     * @param plaintext Content to encrypt (SDP, ICE candidate JSON, etc.)
     * @return Base64 ciphertext or null if peer key not found or encryption fails
     */
    fun encryptForPeer(peerId: String, plaintext: String): String? {
        val cleanPeerId = normKey(peerId)
        val secretKey = peerSharedKeys[cleanPeerId] ?: run {
            logW("Cannot encrypt for peer [$cleanPeerId]: no shared secret established")
            return null
        }

        return try {
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Output buffer: IV (12 bytes) + Ciphertext + Tag
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            logE("Encryption error for peer [$cleanPeerId]: ${e.message}", e)
            null
        }
    }

    /**
     * Decrypts Base64( IV [12 bytes] + CiphertextWithTag ) using AES-256-GCM.
     *
     * @param peerId Sender peer ID
     * @param encryptedBase64 Base64-encoded encrypted payload
     * @return Decrypted plaintext string or null if decryption fails
     */
    fun decryptFromPeer(peerId: String, encryptedBase64: String): String? {
        val cleanPeerId = normKey(peerId)
        val secretKey = peerSharedKeys[cleanPeerId] ?: run {
            logW("Cannot decrypt from peer [$cleanPeerId]: no shared secret established")
            return null
        }

        return try {
            val combined = Base64.getDecoder().decode(encryptedBase64)
            if (combined.size <= GCM_IV_LENGTH_BYTES) {
                logE("Decryption error from peer [$cleanPeerId]: payload too short (${combined.size} bytes)")
                return null
            }

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

            val cipherLength = combined.size - GCM_IV_LENGTH_BYTES
            val ciphertext = ByteArray(cipherLength)
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, ciphertext, 0, cipherLength)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            logE("Decryption error from peer [$cleanPeerId]: ${e.message}", e)
            null
        }
    }

    /**
     * Removes keys for a peer when they leave the squad.
     */
    fun removePeer(peerId: String) {
        val clean = normKey(peerId)
        peerSharedKeys.remove(clean)
        peerPublicKeys.remove(clean)
        logD("Cleared crypto keys for departed peer [$clean]")
    }

    /**
     * Derives a 256-bit symmetric AES key for squad-wide broadcast encryption
     * using PBKDF2WithHmacSHA256 from roomId and roomCode.
     */
    @Synchronized
    fun deriveSquadKey(roomId: String, roomCode: String): Boolean {
        val cleanRoomId = roomId.trim().lowercase()
        val cleanCode = roomCode.trim().uppercase()
        if (cleanRoomId.isBlank() || cleanCode.isBlank()) return false

        return try {
            val combinedPass = "$cleanRoomId:$cleanCode".toCharArray()
            val salt = ("SquadTalk_E2EE_Salt_2026#" + cleanRoomId).toByteArray(Charsets.UTF_8)
            val spec = PBEKeySpec(combinedPass, salt, 10000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            squadSharedKey = SecretKeySpec(keyBytes, AES_KEY_ALGO)
            logD("Derived Squad E2EE AES-256 key for room [$cleanRoomId]")
            true
        } catch (e: Exception) {
            logE("Error deriving squad key: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if the squad room symmetric key is derived.
     */
    fun hasSquadKey(): Boolean = squadSharedKey != null

    /**
     * Encrypts plaintext using the squad-wide AES-256-GCM key.
     * Output format: Base64( IV [12 bytes] + CiphertextWithTag )
     */
    fun encryptSquadPayload(plaintext: String): String? {
        val key = squadSharedKey ?: run {
            logW("Cannot encrypt squad payload: squad key not established")
            return null
        }

        return try {
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            logE("Squad payload encryption error: ${e.message}", e)
            null
        }
    }

    /**
     * Decrypts ciphertext using the squad-wide AES-256-GCM key.
     */
    fun decryptSquadPayload(encryptedBase64: String): String? {
        val key = squadSharedKey ?: run {
            logW("Cannot decrypt squad payload: squad key not established")
            return null
        }

        return try {
            val combined = Base64.getDecoder().decode(encryptedBase64)
            if (combined.size <= GCM_IV_LENGTH_BYTES) {
                logE("Squad payload decryption error: payload too short (${combined.size} bytes)")
                return null
            }

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

            val cipherLength = combined.size - GCM_IV_LENGTH_BYTES
            val ciphertext = ByteArray(cipherLength)
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, ciphertext, 0, cipherLength)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            logE("Squad payload decryption error: ${e.message}", e)
            null
        }
    }
}
