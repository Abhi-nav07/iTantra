package com.itantra.regression

import com.itantra.core.crypto.SecureSessionManager
import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketType
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class CryptoCounterConcurrencyTest {

    @Test
    fun testConcurrentEncryptionProducesUniqueCountersAndValidDecryption() = runBlocking {
        val alice = SecureSessionManager()
        val bob = SecureSessionManager()

        // Establish session
        val aliceHello = alice.startHandshake(isInitiator = true)
        val bobHello = bob.processSecureHello(aliceHello)!!
        alice.processSecureHello(bobHello)

        val aliceVerify = alice.confirmSasMatch()
        val bobVerify = bob.confirmSasMatch()
        alice.processSecureVerify(bobVerify)
        bob.processSecureVerify(aliceVerify)

        val count = 50
        val encryptedPackets = ConcurrentHashMap<Long, ItantraPacket>()

        // Encrypt concurrently from multiple coroutine workers
        val jobs = (1..count).map { i ->
            async(Dispatchers.Default) {
                val plain = ItantraPacket(
                    type = PacketType.TEXT,
                    messageId = i.toLong(),
                    payload = "Payload #$i".toByteArray(Charsets.UTF_8)
                )
                val enc = alice.encrypt(plain)
                encryptedPackets[enc.counter] = enc
            }
        }
        jobs.awaitAll()

        // Verify strictly unique counters with no collisions
        assertEquals("All $count packets must have unique counters", count, encryptedPackets.size)
        val counters = encryptedPackets.keys.sorted()
        for (i in 1..count) {
            assertEquals("Counters must be strictly sequential starting at 1", i.toLong(), counters[i - 1])
        }

        // Bob decrypts all packets successfully
        for (counter in counters) {
            val enc = encryptedPackets[counter]!!
            val dec = bob.decrypt(enc)
            assertNotNull("Bob must successfully decrypt packet $counter", dec)
            assertEquals(0.toByte(), dec.securityVersion)
        }
    }
}
