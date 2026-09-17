package com.itantra.core.crypto

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * Standard JCA cryptographic primitives for Secure Transport Layer (Task 04C).
 * Uses AES-256-GCM, ECDH (NIST P-256 / secp256r1), and HKDF-SHA256.
 * Zero external dependencies.
 */
object CryptoPrimitives {

    const val AES_GCM_NONCE_LEN = 12
    const val AES_GCM_TAG_LEN = 16

    private val secureRandom = SecureRandom()

    /**
     * Generates an ephemeral ECDH KeyPair on NIST P-256 (secp256r1).
     */
    fun generateEcdhKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return kpg.generateKeyPair()
    }

    /**
     * Computes the ECDH shared secret given local private key and peer's raw public key bytes.
     */
    fun computeSharedSecret(localPrivateKey: PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
        
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(localPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        
        return keyAgreement.generateSecret()
    }

    /**
     * HKDF-SHA256 (RFC 5869) Extract-and-Expand.
     * Derives cryptographic keys from a shared secret.
     */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray = ByteArray(0), info: ByteArray = ByteArray(0), outputLength: Int): ByteArray {
        // Step 1: Extract
        val mac = Mac.getInstance("HmacSHA256")
        val actualSalt = if (salt.isEmpty()) ByteArray(32) { 0 } else salt
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        // Step 2: Expand
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(outputLength)
        val n = ceil(outputLength / 32.0).toInt()
        
        var t = ByteArray(0)
        var offset = 0
        for (i in 1..n) {
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            
            val toCopy = minOf(32, outputLength - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
        }
        return result
    }

    /**
     * Encrypts a payload using AES-256-GCM.
     * @return Ciphertext + 16-byte authentication tag
     */
    fun encryptAesGcm(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(AES_GCM_TAG_LEN * 8, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypts and authenticates a payload using AES-256-GCM.
     * @return Plaintext if successful, throws exception if authentication fails.
     */
    fun decryptAesGcm(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(AES_GCM_TAG_LEN * 8, nonce)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(ciphertext)
    }

    /**
     * Derives a 6-digit numeric Short Authentication String (SAS).
     */
    fun deriveSas(sharedSecret: ByteArray, transcriptHash: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
        val hash = mac.doFinal(transcriptHash)
        
        // Extract 4 bytes deterministically
        val buffer = ByteBuffer.wrap(hash, 0, 4)
        val code = buffer.int.toUInt()
        
        // Modulo 1,000,000 for a 6-digit number
        val digits = code % 1000000u
        return digits.toString().padStart(6, '0')
    }

    /**
     * Securely generates a random challenge/nonce for handshakes.
     */
    fun generateRandomNonce(length: Int = 16): ByteArray {
        val nonce = ByteArray(length)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * SHA-256 utility for transcript hashing
     */
    fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}
