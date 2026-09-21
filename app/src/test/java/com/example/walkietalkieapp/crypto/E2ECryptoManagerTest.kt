package com.example.walkietalkieapp.crypto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class E2ECryptoManagerTest {

    @Before
    fun setUp() {
        E2ECryptoManager.resetSession()
        E2ECryptoManager.initSession()
    }

    @Test
    fun testSessionInitializationAndFingerprint() {
        val myPubKey = E2ECryptoManager.getMyPublicKeyBase64()
        assertNotNull("Public key should not be null", myPubKey)
        assertTrue("Public key should not be empty", myPubKey!!.isNotEmpty())

        val fingerprint = E2ECryptoManager.getMyFingerprint()
        assertNotNull("Fingerprint should not be null", fingerprint)
        assertTrue("Fingerprint should contain colons", fingerprint.contains(":"))
        assertEquals("Fingerprint should have 8 hex pairs", 8, fingerprint.split(":").size)
    }

    @Test
    fun testTwoPartyE2EEncryptionAndDecryption() {
        // Party A is E2ECryptoManager
        val alicePubKey = E2ECryptoManager.getMyPublicKeyBase64()!!

        // Party B: Generate independent Bob EC P-256 keypair
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val bobKeyPair = keyGen.generateKeyPair()
        val bobPubKey = Base64.getEncoder().encodeToString(bobKeyPair.public.encoded)

        // Derive shared secret from Bob's perspective
        val bobKa = javax.crypto.KeyAgreement.getInstance("ECDH")
        bobKa.init(bobKeyPair.private)
        val aliceKeySpec = java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(alicePubKey))
        val alicePubKeyObj = java.security.KeyFactory.getInstance("EC").generatePublic(aliceKeySpec)
        bobKa.doPhase(alicePubKeyObj, true)
        val bobSecret = bobKa.generateSecret()
        val bobAesKey = javax.crypto.spec.SecretKeySpec(
            java.security.MessageDigest.getInstance("SHA-256").digest(bobSecret),
            "AES"
        )

        // Register Bob's public key in Alice's manager
        val registered = E2ECryptoManager.registerPeerPublicKey("bob", bobPubKey)
        assertTrue("Bob's key should be registered", registered)
        assertTrue("Alice should recognize Bob as an encrypted peer", E2ECryptoManager.hasPeerKey("bob"))

        // Alice encrypts an SDP offer for Bob
        val originalSdp = "v=0\r\no=- 420790890 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111"
        val encryptedSdp = E2ECryptoManager.encryptForPeer("bob", originalSdp)
        assertNotNull("Encrypted payload should not be null", encryptedSdp)
        assertNotEquals("Ciphertext should differ from plaintext", originalSdp, encryptedSdp)

        // Bob decrypts Alice's ciphertext
        val combined = Base64.getDecoder().decode(encryptedSdp)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)

        val bobCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        bobCipher.init(javax.crypto.Cipher.DECRYPT_MODE, bobAesKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        val decryptedByBob = String(bobCipher.doFinal(ciphertext), Charsets.UTF_8)

        assertEquals("Decrypted SDP must match original plaintext SDP exactly", originalSdp, decryptedByBob)

        // Bob sends an encrypted response to Alice
        val bobResponseText = "{\"type\":\"answer\",\"sdp\":\"v=0\\r\\nm=audio 9...\"}"
        val bobIv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
        val bobEncryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        bobEncryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, bobAesKey, javax.crypto.spec.GCMParameterSpec(128, bobIv))
        val bobCiphertext = bobEncryptCipher.doFinal(bobResponseText.toByteArray(Charsets.UTF_8))
        val bobPayload = Base64.getEncoder().encodeToString(bobIv + bobCiphertext)

        // Alice decrypts Bob's response
        val decryptedByAlice = E2ECryptoManager.decryptFromPeer("bob", bobPayload)
        assertEquals("Alice should decrypt Bob's payload correctly", bobResponseText, decryptedByAlice)
    }

    @Test
    fun testTamperedCiphertextReturnsNull() {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val bobKeyPair = keyGen.generateKeyPair()
        val bobPubKey = Base64.getEncoder().encodeToString(bobKeyPair.public.encoded)
        E2ECryptoManager.registerPeerPublicKey("bob", bobPubKey)

        val ciphertext = E2ECryptoManager.encryptForPeer("bob", "secret audio candidate")!!
        val bytes = Base64.getDecoder().decode(ciphertext)
        // Tamper with one byte
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        val tamperedCiphertext = Base64.getEncoder().encodeToString(bytes)

        val decrypted = E2ECryptoManager.decryptFromPeer("bob", tamperedCiphertext)
        assertNull("Decryption of tampered ciphertext must fail and return null", decrypted)
    }

    @Test
    fun testRemovePeerCleansKey() {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val bobKeyPair = keyGen.generateKeyPair()
        val bobPubKey = Base64.getEncoder().encodeToString(bobKeyPair.public.encoded)
        E2ECryptoManager.registerPeerPublicKey("bob", bobPubKey)

        assertTrue(E2ECryptoManager.hasPeerKey("bob"))
        E2ECryptoManager.removePeer("bob")
        assertFalse("Bob should no longer have a stored key", E2ECryptoManager.hasPeerKey("bob"))

        val result = E2ECryptoManager.encryptForPeer("bob", "test")
        assertNull("Encrypting for removed peer should return null", result)
    }

    @Test
    fun testSquadKeyDerivationAndEncryptionDecryption() {
        val roomId = "room-alpha-99"
        val roomCode = "WT-9999"

        assertTrue("Squad key should derive successfully", E2ECryptoManager.deriveSquadKey(roomId, roomCode))
        assertTrue("hasSquadKey should return true", E2ECryptoManager.hasSquadKey())

        val secretMessage = "{\"text\":\"MOVE TO EXTRACTION POINT\",\"lat\":37.7749,\"lng\":-122.4194}"
        val ciphertext = E2ECryptoManager.encryptSquadPayload(secretMessage)
        assertNotNull("Ciphertext should not be null", ciphertext)
        assertNotEquals("Ciphertext should differ from plaintext", secretMessage, ciphertext)

        val decrypted = E2ECryptoManager.decryptSquadPayload(ciphertext!!)
        assertEquals("Decrypted payload must match original", secretMessage, decrypted)
    }

    @Test
    fun testSquadKeyTamperingRejection() {
        E2ECryptoManager.deriveSquadKey("room-123", "WT-1234")
        val payload = "SENSITIVE GPS BEACON"
        val ciphertext = E2ECryptoManager.encryptSquadPayload(payload)!!

        val bytes = Base64.getDecoder().decode(ciphertext)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        val tampered = Base64.getEncoder().encodeToString(bytes)

        val decrypted = E2ECryptoManager.decryptSquadPayload(tampered)
        assertNull("Decryption of tampered squad payload must return null", decrypted)
    }

    @Test
    fun testWrongSquadCodeFailsDecryption() {
        // Squad A
        E2ECryptoManager.deriveSquadKey("room-alpha", "WT-AAAA")
        val ciphertext = E2ECryptoManager.encryptSquadPayload("TOP SECRET TACTICAL ORDER")!!

        // Attacker / Squad B with wrong code
        E2ECryptoManager.deriveSquadKey("room-alpha", "WT-BBBB")
        val decryptedWithWrongCode = E2ECryptoManager.decryptSquadPayload(ciphertext)
        assertNull("Decryption with incorrect squad code must fail", decryptedWithWrongCode)
    }

    @Test
    fun testResetSessionClearsSquadKey() {
        E2ECryptoManager.deriveSquadKey("room-test", "WT-TEST")
        assertTrue(E2ECryptoManager.hasSquadKey())

        E2ECryptoManager.resetSession()
        assertFalse("Squad key should be cleared after session reset", E2ECryptoManager.hasSquadKey())
        assertNull("Encrypting without squad key should return null", E2ECryptoManager.encryptSquadPayload("test"))
    }
}
