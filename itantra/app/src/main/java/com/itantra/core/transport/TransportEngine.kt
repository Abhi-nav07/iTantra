package com.itantra.core.transport

import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.domain.model.TransmissionMetrics
import kotlinx.coroutines.flow.Flow

/**
 * Contract for sending/receiving a compact text packet between two
 * devices over a local, offline transport (Bluetooth / Wi-Fi Direct).
 *
 * NO IMPLEMENTATION EXISTS YET. This task does not claim Bluetooth or
 * Wi-Fi Direct support — the interface only reserves the seam so the
 * transceiver feature can be wired against a stable contract later
 * without reshaping the UI/ViewModel layer.
 */
interface TransportEngine {

    val isConnected: Boolean

    fun observeConnectionState(): Flow<ConnectionState>

    suspend fun connect()

    suspend fun disconnect()

    /** Sends a complete packet and returns transmission metrics. */
    suspend fun send(packet: ItantraPacket): TransmissionMetrics

    /** Stream of packets received from a connected peer. */
    fun receive(): Flow<ItantraPacket>
}

enum class ConnectionState {
    DISCONNECTED,
    LISTENING,
    CONNECTING,
    CONNECTED,
    ERROR,
}
