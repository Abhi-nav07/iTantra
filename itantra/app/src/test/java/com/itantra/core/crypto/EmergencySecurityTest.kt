package com.itantra.core.crypto

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
        // Alice sends a NORMAL priority message
        val originalPacket = ItantraPacket(
            type = PacketType.TEXT,
            flags = MessagePriority.NORMAL.toByte(),
            messageId = 12345L,
            payload = "Hello".toByteArray()
        )
        val encryptedPacket = alice.encrypt(originalPacket)

        // Mallory intercepts the packet and tries to escalate it to CRITICAL
        // Note: flags is byte 10 in the encoder, but we can just mutate the ItantraPacket's flags
        // since SecureSessionManager.decrypt re-encodes the header to verify AAD.
        // Wait, the ItantraPacket passed to decrypt is parsed by PacketDecoder.
        // If an attacker modifies the byte on the wire, the decoded packet will have flags = CRITICAL.
        // Let's simulate the decoded packet with tampered flags.
        val tamperedPacket = encryptedPacket.copy(
            flags = MessagePriority.CRITICAL.toByte()
        )

        // Bob tries to decrypt the tampered packet
        assertThrows(Exception::class.java) {
            bob.decrypt(tamperedPacket)
        }
    }
}
