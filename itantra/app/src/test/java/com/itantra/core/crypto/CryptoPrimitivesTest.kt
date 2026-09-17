package com.itantra.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoPrimitivesTest {

    @Test
    fun testEcdhKeyExchange() {
        val aliceKp = CryptoPrimitives.generateEcdhKeyPair()
        val bobKp = CryptoPrimitives.generateEcdhKeyPair()

        val aliceSecret = CryptoPrimitives.computeSharedSecret(aliceKp.private, bobKp.public.encoded)
        val bobSecret = CryptoPrimitives.computeSharedSecret(bobKp.private, aliceKp.public.encoded)

        assertArrayEquals(aliceSecret, bobSecret)
        assertEquals(32, aliceSecret.size) // P-256 secret is 32 bytes
    }

    @Test
    fun testHkdfSha256() {
        val ikm = "secret_material".toByteArray()
        val info = "test_info".toByteArray()

        val derived1 = CryptoPrimitives.hkdfSha256(ikm = ikm, info = info, outputLength = 64)
        val derived2 = CryptoPrimitives.hkdfSha256(ikm = ikm, info = info, outputLength = 64)
        val derived3 = CryptoPrimitives.hkdfSha256(ikm = ikm, info = "different_info".toByteArray(), outputLength = 64)

        assertArrayEquals(derived1, derived2)
        assertEquals(64, derived1.size)
        
        // Different info should produce different output
        var allMatch = true
        for (i in derived1.indices) {
            if (derived1[i] != derived3[i]) {
                allMatch = false
                break
            }
        }
        assertEquals(false, allMatch)
    }

    @Test
    fun testAesGcmEncryption() {
        val key = CryptoPrimitives.generateRandomNonce(32)
        val nonce = CryptoPrimitives.generateRandomNonce(12)
        val aad = "header_data".toByteArray()
        val plaintext = "Hello World".toByteArray()

        val ciphertext = CryptoPrimitives.encryptAesGcm(key, nonce, aad, plaintext)
        val decrypted = CryptoPrimitives.decryptAesGcm(key, nonce, aad, ciphertext)

        assertArrayEquals(plaintext, decrypted)
        assertEquals(plaintext.size + CryptoPrimitives.AES_GCM_TAG_LEN, ciphertext.size)
    }

    @Test
    fun testAesGcmTampering() {
        val key = CryptoPrimitives.generateRandomNonce(32)
        val nonce = CryptoPrimitives.generateRandomNonce(12)
        val aad = "header_data".toByteArray()
        val plaintext = "Hello World".toByteArray()

        val ciphertext = CryptoPrimitives.encryptAesGcm(key, nonce, aad, plaintext)

        // 1. Tamper ciphertext
        val tamperedCiphertext = ciphertext.clone()
        tamperedCiphertext[0] = (tamperedCiphertext[0] + 1).toByte()

        assertThrows(AEADBadTagException::class.java) {
            CryptoPrimitives.decryptAesGcm(key, nonce, aad, tamperedCiphertext)
        }

        // 2. Tamper AAD
        val tamperedAad = "header_datb".toByteArray()
        assertThrows(AEADBadTagException::class.java) {
            CryptoPrimitives.decryptAesGcm(key, nonce, tamperedAad, ciphertext)
        }
    }

    @Test
    fun testSasGeneration() {
        val secret = "shared_secret".toByteArray()
        val transcript = "transcript_hash".toByteArray()

        val sas1 = CryptoPrimitives.deriveSas(secret, transcript)
        val sas2 = CryptoPrimitives.deriveSas(secret, transcript)
        
        assertEquals(sas1, sas2)
        assertEquals(6, sas1.length)
        
        val sas3 = CryptoPrimitives.deriveSas(secret, "different_transcript".toByteArray())
        assertNotEquals(sas1, sas3)
    }
}
