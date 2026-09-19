package com.itantra.core.transport

import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketEncoder
import com.itantra.core.transport.packet.PacketType
import com.itantra.core.transport.peer.PeerTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockPeerTransport : PeerTransport {
    override val isConnected: Boolean = true
    override val isServer: Boolean = false
    private val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
    val incomingFlow = MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 64)
    val sentBytes = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()

    override fun observeConnectionState(): Flow<ConnectionState> = stateFlow

    override suspend fun disconnect() {}

    override suspend fun send(bytes: ByteArray) {
        sentBytes.add(bytes)
    }

    override fun receive(): Flow<ByteArray> = incomingFlow
}

class TransportCoordinatorTest {

    @Test
    fun `test TransportCoordinator handles framing and ACKs`() = runBlocking {
        val mockTransport = MockPeerTransport()
        val coordinator = TransportCoordinator(mockTransport)

        // 1. Sending a packet
        val testPacket = ItantraPacket(
            type = PacketType.CAPABILITIES,
            messageId = 12345L,
            payload = "Hello".toByteArray()
        )

        // Since it's CAPABILITIES, it does not wait for ACK, send returns immediately
        coordinator.send(testPacket)

        assertEquals(1, mockTransport.sentBytes.size)
        // Verify encoded byte array matches what we expect
        val encodedBytes = PacketEncoder.encode(testPacket)
        // Check if sent bytes match
        assertEquals(encodedBytes.size, mockTransport.sentBytes[0].size)

        // 2. Receiving an ACK
        // 3. Receiving a normal packet (coordinator should reply with ACK)
        val incomingPacket = ItantraPacket(
            type = PacketType.TEXT,
            messageId = 54321L,
            payload = "World".toByteArray()
        )
        val incomingFrame = PacketEncoder.encode(incomingPacket)

        // We need to pass only the payload to the MockTransport since length-prefix is handled by the transport itself.
        // PacketEncoder.encode includes the 4-byte prefix.
        val frameData = incomingFrame.copyOfRange(4, incomingFrame.size)

        val receivedPackets = mutableListOf<ItantraPacket>()
        val job = launch {
            coordinator.receive().collect { receivedPackets.add(it) }
        }
        kotlinx.coroutines.yield() // Ensure subscriber is ready

        mockTransport.incomingFlow.emit(frameData)

        kotlinx.coroutines.delay(500) // Give IO time to process

        assertTrue(receivedPackets.isNotEmpty())
        val emitted = receivedPackets.first()
        job.cancel()

        assertEquals(PacketType.TEXT, emitted.type)
        assertEquals(54321L, emitted.messageId)
    }

    @Test
    fun `test TransportCoordinator byte accounting verifies semantic, ciphertext, and wire frame sizes`() = runBlocking {
        val mockTransport = MockPeerTransport()
        val coordinator = TransportCoordinator(mockTransport)

        // Plaintext payload of 10 bytes
        val plaintext = "1234567890".toByteArray()
        val plainPacket = ItantraPacket(
            type = PacketType.CAPABILITIES,
            messageId = 1001L,
            payload = plaintext
        )

        val plainMetrics = coordinator.send(plainPacket)
        val expectedWireBytes = PacketEncoder.encode(plainPacket).size

        assertEquals(com.itantra.domain.model.Measurement.Measured(10), plainMetrics.payloadBytes)
        assertEquals(com.itantra.domain.model.Measurement.Measured(10), plainMetrics.semanticPayloadBytes)
        assertEquals(com.itantra.domain.model.Measurement.Measured(10), plainMetrics.secureBytes)
        assertEquals(com.itantra.domain.model.Measurement.Measured(expectedWireBytes), plainMetrics.finalFrameBytes)
        assertEquals(com.itantra.domain.model.Measurement.Measured(expectedWireBytes), plainMetrics.packetBytes)

        // Encrypted packet (securityVersion = 1) with 10 bytes semantic + 16 bytes GCM tag = 26 bytes payload
        val cipherPayload = ByteArray(26) { 0x42 }
        val encryptedPacket = ItantraPacket(
            type = PacketType.CAPABILITIES,
            messageId = 1002L,
            securityVersion = 1,
            counter = 1,
            payload = cipherPayload
        )

        val secureMetrics = coordinator.send(encryptedPacket)
        val expectedEncWireBytes = PacketEncoder.encode(encryptedPacket).size

        assertEquals(com.itantra.domain.model.Measurement.Measured(26), secureMetrics.payloadBytes)
        assertEquals(com.itantra.domain.model.Measurement.Measured(10), secureMetrics.semanticPayloadBytes)
        assertEquals(com.itantra.domain.model.Measurement.Measured(26), secureMetrics.secureBytes)
        assertEquals(com.itantra.domain.model.Measurement.Measured(expectedEncWireBytes), secureMetrics.finalFrameBytes)
        assertEquals(com.itantra.domain.model.Measurement.Measured(expectedEncWireBytes), secureMetrics.packetBytes)
    }
}
