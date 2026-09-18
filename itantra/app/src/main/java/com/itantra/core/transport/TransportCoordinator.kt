package com.itantra.core.transport


import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketDecoder
import com.itantra.core.transport.packet.PacketEncoder
import com.itantra.core.transport.packet.PacketType
import com.itantra.core.transport.peer.PeerTransport
import com.itantra.domain.model.Measurement
import com.itantra.domain.model.TransmissionMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Manages the active [PeerTransport] (e.g., Bluetooth or Wi-Fi), and handles framing,
 * packet encoding/decoding, and ACK-based RTT measurement.
 * Implements [TransportEngine] so that upper layers remain unaware of the underlying transport mechanism.
 */
class TransportCoordinator(
    private var activeTransport: PeerTransport
) : TransportEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val incomingFlow = MutableSharedFlow<ItantraPacket>(extraBufferCapacity = 64)
    private val ackFlow = MutableSharedFlow<Long>(extraBufferCapacity = 64)
    
    private var readJob: Job? = null
    
    init {
        startObservingTransport()
    }

    /**
     * Switches the active transport dynamically.
     */
    suspend fun switchTransport(newTransport: PeerTransport) {
        val oldTransport = activeTransport
        if (oldTransport === newTransport) return

        oldTransport.disconnect()
        activeTransport = newTransport
        startObservingTransport()
    }

    private fun startObservingTransport() {
        readJob?.cancel()
        readJob = scope.launch {
            activeTransport.receive().collect { frameData ->
                try {
                    val packet = PacketDecoder.decode(frameData)
                    if (packet.type == PacketType.ACK) {
                        ackFlow.emit(packet.messageId)
                    } else {
                        // Automatically ACK TEXT and EMERGENCY_CODE packets immediately
                        if (packet.type == PacketType.TEXT || packet.type == PacketType.EMERGENCY_CODE) {
                            sendAck(packet.messageId)
                        }
                        // Emit packet upward
                        incomingFlow.emit(packet)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun sendAck(messageId: Long) {
        scope.launch {
            try {
                val ackPacket = ItantraPacket(type = PacketType.ACK, messageId = messageId)
                val encoded = PacketEncoder.encode(ackPacket)
                activeTransport.send(encoded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override val isConnected: Boolean
        get() = activeTransport.isConnected

    override fun observeConnectionState(): Flow<ConnectionState> {
        return activeTransport.observeConnectionState()
    }

    override suspend fun connect() {
        activeTransport.connect()
    }

    override suspend fun disconnect() {
        activeTransport.disconnect()
    }

    override suspend fun send(packet: ItantraPacket): TransmissionMetrics {
        if (!isConnected) return TransmissionMetrics()
        
        return withContext(Dispatchers.IO) {
            val t0 = System.nanoTime()
            val encoded = PacketEncoder.encode(packet)
            var txLatency: Long? = null

            try {
                val ackDeferred = if (packet.type == PacketType.TEXT || packet.type == PacketType.EMERGENCY_CODE) {
                    async {
                        withTimeout(5000L) { // 5 sec timeout
                            ackFlow.first { it == packet.messageId }
                            val t1 = System.nanoTime()
                            t1 - t0
                        }
                    }
                } else null

                activeTransport.send(encoded)
                txLatency = ackDeferred?.await()
                
            } catch (e: TimeoutCancellationException) {
                // No ACK received in time
                println("ACK timeout for message: ${packet.messageId}")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            TransmissionMetrics(
                payloadBytes = Measurement.Measured(packet.payload.size), // Keep for backward compatibility, it's actually secure payload size
                secureBytes = Measurement.Measured(packet.payload.size + 32), // 32 is header size, rough estimate of secure packet size
                finalFrameBytes = Measurement.Measured(encoded.size),
                packetBytes = Measurement.Measured(encoded.size),
                transmissionLatencyMillis = txLatency?.let { Measurement.Measured(it / 1_000_000) } ?: Measurement.NotMeasured
            )
        }
    }

    override fun receive(): Flow<ItantraPacket> = incomingFlow
}
