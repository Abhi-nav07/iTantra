package com.itantra.regression

import com.itantra.core.crypto.SecureSessionManager
import com.itantra.core.crypto.SecureSessionState
import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketType
import org.junit.Assert.*
import org.junit.Test

class SecureHandshakeIntegrationTest {

    @Test
    fun testTwoPartyHandshakeFullLifecycle() {
        val initiator = SecureSessionManager()
        val responder = SecureSessionManager()

        // Invariant: Both start in NO_SESSION
        assertEquals(SecureSessionState.NO_SESSION, initiator.state.value)
        assertEquals(SecureSessionState.NO_SESSION, responder.state.value)

        // 1. Initiator sends HELLO
        val initHello = initiator.startHandshake(isInitiator = true)
        assertEquals(SecureSessionState.HANDSHAKING, initiator.state.value)
        assertNotNull(initHello)
        assertEquals(PacketType.SECURE_HELLO, initHello.type)

        // 2. Responder in NO_SESSION receives HELLO -> derives keys & sends response HELLO
        val respHello = responder.processSecureHello(initHello)
        assertNotNull("Responder must return its HELLO response", respHello)
        assertEquals(PacketType.SECURE_HELLO, respHello!!.type)
        assertEquals(SecureSessionState.WAITING_USER_VERIFICATION, responder.state.value)

        // 3. Initiator receives responder HELLO -> derives keys, sends null (no further HELLO)
        val secondReply = initiator.processSecureHello(respHello)
        assertNull("Initiator does not send another HELLO", secondReply)
        assertEquals(SecureSessionState.WAITING_USER_VERIFICATION, initiator.state.value)

        // 4. SAS codes match exactly
        assertNotNull(initiator.sasCode.value)
        assertEquals(initiator.sasCode.value, responder.sasCode.value)

        // 5. User verification (SAS match confirmed on both devices)
        val initVerify = initiator.confirmSasMatch()
        val respVerify = responder.confirmSasMatch()

        initiator.processSecureVerify(respVerify)
        responder.processSecureVerify(initVerify)

        // 6. Both sides reach SECURE_VERIFIED
        assertEquals(SecureSessionState.SECURE_VERIFIED, initiator.state.value)
        assertEquals(SecureSessionState.SECURE_VERIFIED, responder.state.value)
    }

    @Test
    fun testResponderDoesNotIndependentlySendFirstHello() {
        val responder = SecureSessionManager()
        // Responder remains in NO_SESSION waiting for incoming HELLO
        assertEquals(SecureSessionState.NO_SESSION, responder.state.value)
        assertNull(responder.getStoredHello())
    }

    @Test
    fun testMalformedHelloDoesNotEstablishSession() {
        val responder = SecureSessionManager()
        val malformedPacket = ItantraPacket(
            type = PacketType.SECURE_HELLO,
            messageId = 1L,
            payload = ByteArray(5) // Too short (< 48 bytes)
        )
        val reply = responder.processSecureHello(malformedPacket)
        assertNull("Malformed HELLO must be rejected", reply)
        assertEquals("State must remain NO_SESSION", SecureSessionState.NO_SESSION, responder.state.value)
    }

    @Test
    fun testDuplicateHelloDoesNotCorruptSession() {
        val initiator = SecureSessionManager()
        val responder = SecureSessionManager()

        val initHello = initiator.startHandshake(isInitiator = true)
        val respHello = responder.processSecureHello(initHello)!!
        initiator.processSecureHello(respHello)

        assertEquals(SecureSessionState.WAITING_USER_VERIFICATION, responder.state.value)

        // Peer sends duplicate HELLO while responder is in WAITING_USER_VERIFICATION
        val dupReply = responder.processSecureHello(initHello)
        // Must return the cached HELLO response without corrupting session
        assertNotNull(dupReply)
        assertEquals(respHello.messageId, dupReply!!.messageId)
        assertEquals(SecureSessionState.WAITING_USER_VERIFICATION, responder.state.value)
    }

    @Test
    fun testDisconnectResetsSessionCleanly() {
        val manager = SecureSessionManager()
        manager.startHandshake(isInitiator = true)
        assertEquals(SecureSessionState.HANDSHAKING, manager.state.value)
        assertNotNull(manager.getStoredHello())

        manager.resetSession()
        assertEquals(SecureSessionState.NO_SESSION, manager.state.value)
        assertNull(manager.getStoredHello())
    }
}
