package com.itantra.core.crypto

import kotlinx.coroutines.runBlocking

import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketType
import com.itantra.domain.model.MessagePriority
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException

class EmergencySecurityTest {

    private lateinit var alice: SecureSessionManager
    private lateinit var bob: SecureSessionManager

    @Before
    fun setup() {
        alice = SecureSessionManager()
        bob = SecureSessionManager()

        // Establish secure session
        val aliceHello = alice.startHandshake(isInitiator = true)
        val bobHello = bob.processSecureHello(aliceHello)!!
        alice.processSecureHello(bobHello)

        val aliceConfirm = alice.confirmSasMatch()
        val bobConfirm = bob.confirmSasMatch()
        alice.processSecureVerify(bobConfirm)
        bob.processSecureVerify(aliceConfirm)
    }

    @Test
    fun `tampering with priority flag causes authentication failure`() {
        runBlocking {
            // Alice sends a NORMAL priority message
            val originalPacket = ItantraPacket(
                type = PacketType.TEXT,
                flags = MessagePriority.NORMAL.toByte(),
                messageId = 12345L,
                payload = "Hello".toByteArray()
            )
            val encryptedPacket = alice.encrypt(originalPacket)

            // Mallory intercepts the packet and tries to escalate it to CRITICAL
            val tamperedPacket = encryptedPacket.copy(
                flags = MessagePriority.CRITICAL.toByte()
            )

            // Bob tries to decrypt the tampered packet
            assertThrows(Exception::class.java) {
                bob.decrypt(tamperedPacket)
            }
        }
    }
}
