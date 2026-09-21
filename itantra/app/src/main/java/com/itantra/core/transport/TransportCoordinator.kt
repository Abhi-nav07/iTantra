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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages the active [PeerTransport] (e.g., Bluetooth or Wi-Fi), and handles framing,
 * packet encoding/decoding, ACK-based RTT measurement, and connection liveness
 * (heartbeat + idle watchdog).
 * Implements [TransportEngine] so that upper layers remain unaware of the underlying transport mechanism.
 */
class TransportCoordinator(
    initialTransport: PeerTransport
) : TransportEngine {

    private val _activeTransport = kotlinx.coroutines.flow.MutableStateFlow(initialTransport)
    val activeTransportFlow: kotlinx.coroutines.flow.StateFlow<PeerTransport> = _activeTransport

    private val activeTransport: PeerTransport
        get() = _activeTransport.value

    companion object {
        private const val ACK_TIMEOUT_MS = 5000L
        private const val HEARTBEAT_INTERVAL_MS = 4000L
        private const val WATCHDOG_CHECK_INTERVAL_MS = 2000L
        // Must be comfortably larger than HEARTBEAT_INTERVAL_MS to tolerate one or two missed beats
        // before declaring the link dead.
        private const val WATCHDOG_TIMEOUT_MS = 13000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val incomingFlow = MutableSharedFlow<ItantraPacket>(extraBufferCapacity = 64)

    // Fix: previously ACK waits were done via `ackFlow.first { it == messageId }` started
    // *after* activeTransport.send() was already in flight. Because the underlying flow has
    // no replay, a fast peer reply could arrive and be emitted before the collector subscribed,
    // silently dropping the ACK and forcing a full 5s timeout even though delivery succeeded.
    // Registering a waiter for this exact messageId *before* sending removes that race.
    private val ackWaiters = ConcurrentHashMap<Long, CompletableDeferred<Long>>()

    private var readJob: Job? = null
    private var heartbeatJob: Job? = null
    private var watchdogJob: Job? = null
    private val lastRxAtMs = AtomicLong(0L)

    init {
        startObservingTransport()
    }

    /**
     * Switches the active transport dynamically.
     */
    suspend fun switchTransport(newTransport: PeerTransport) {
        val oldTransport = _activeTransport.value
        if (oldTransport === newTransport) return

        oldTransport.disconnect()
        _activeTransport.value = newTransport
        startObservingTransport()
    }

    private fun startObservingTransport() {
        readJob?.cancel()
        heartbeatJob?.cancel()
        watchdogJob?.cancel()
        lastRxAtMs.set(System.currentTimeMillis())

        readJob = scope.launch {
            activeTransport.receive().collect { frameData ->
                lastRxAtMs.set(System.currentTimeMillis())
                try {
                    val packet = PacketDecoder.decode(frameData)
                    if (packet.type == PacketType.HEARTBEAT) {
                        // Liveness-only frame; consumed here and never forwarded upward.
                        return@collect
                    }
                    // Emit packet upward for decryption/authentication
                    incomingFlow.emit(packet)
                } catch (e: Exception) {
                    // A single corrupted frame shouldn't necessarily be fatal for the link,
                    // but we don't have a resync strategy at this layer yet - log and keep going,
                    // the underlying transport will tear the connection down itself if the
                    // stream framing is actually broken.
                    e.printStackTrace()
                }
            }
        }

        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!activeTransport.isConnected) continue
                try {
                    val heartbeat = ItantraPacket(type = PacketType.HEARTBEAT, messageId = 0L)
                    activeTransport.send(PacketEncoder.encode(heartbeat))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_CHECK_INTERVAL_MS)
                if (!activeTransport.isConnected) continue
                val idleFor = System.currentTimeMillis() - lastRxAtMs.get()
                if (idleFor > WATCHDOG_TIMEOUT_MS) {
                    // No data and no heartbeat from the peer for too long - the socket/RFCOMM
                    // channel may still look "open" while the other side is actually gone
                    // (app killed, radio silently dropped). Force a disconnect so the UI and
                    // any retry logic notice instead of hanging forever on the next read.
                    android.util.Log.w("TransportCoordinator", "Watchdog: no traffic for ${idleFor}ms, disconnecting")
                    activeTransport.disconnect()
                }
            }
        }
    }

    override val isConnected: Boolean
        get() = activeTransport.isConnected

    override val isServer: Boolean
        get() = activeTransport.isServer

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeConnectionState(): Flow<ConnectionState> {
        return _activeTransport.flatMapLatest { transport ->
            transport.observeConnectionState()
        }
    }

    override suspend fun disconnect() {
        activeTransport.disconnect()
        readJob?.cancel()
        heartbeatJob?.cancel()
        watchdogJob?.cancel()
        // Fail any in-flight ACK waiters instead of leaving them to time out naturally.
        ackWaiters.values.forEach { it.cancel() }
        ackWaiters.clear()
    }

    override fun notifyAckReceived(messageId: Long) {
        // Completed synchronously (no scope.launch dispatch hop) so the waiting send() call
        // resumes as soon as possible - this was previously going through a SharedFlow emit
        // on a freshly-launched coroutine, adding an avoidable dispatch delay on the exact
        // path being latency-measured.
        ackWaiters.remove(messageId)?.complete(System.nanoTime())
    }

    override suspend fun send(packet: ItantraPacket): TransmissionMetrics {
        if (!isConnected) return TransmissionMetrics()

        return withContext(Dispatchers.IO) {
            val t0 = System.nanoTime()
            val encoded = PacketEncoder.encode(packet)
            var txLatency: Long? = null

            val expectsAck = packet.type == PacketType.TEXT || packet.type == PacketType.EMERGENCY_CODE
            val ackDeferred = if (expectsAck) CompletableDeferred<Long>() else null
            if (ackDeferred != null) {
                // Register the waiter BEFORE sending, so an ACK that comes back before we'd
                // otherwise have started listening can never be missed.
                ackWaiters[packet.messageId] = ackDeferred
            }

            try {
                activeTransport.send(encoded)
                if (ackDeferred != null) {
                    val t1 = withTimeout(ACK_TIMEOUT_MS) { ackDeferred.await() }
                    txLatency = t1 - t0
                }
            } catch (e: TimeoutCancellationException) {
                // No ACK received in time
                println("ACK timeout for message: ${packet.messageId}")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                ackWaiters.remove(packet.messageId)
            }

            val ciphertextBytes = packet.payload.size
            val semanticBytes = if (packet.securityVersion == 1.toByte() && packet.payload.size >= 16) {
                packet.payload.size - 16
            } else {
                packet.payload.size
            }
            val wireBytes = encoded.size

            TransmissionMetrics(
                payloadBytes = Measurement.Measured(packet.payload.size),
                semanticPayloadBytes = Measurement.Measured(semanticBytes),
                secureBytes = Measurement.Measured(ciphertextBytes),
                finalFrameBytes = Measurement.Measured(wireBytes),
                packetBytes = Measurement.Measured(wireBytes),
                transmissionLatencyMillis = txLatency?.let { Measurement.Measured(it / 1_000_000) } ?: Measurement.NotMeasured
            )
        }
    }

    override fun receive(): Flow<ItantraPacket> = incomingFlow
}
