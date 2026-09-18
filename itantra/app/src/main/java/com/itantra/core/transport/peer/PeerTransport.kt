package com.itantra.core.transport.peer

import com.itantra.core.transport.ConnectionState
import kotlinx.coroutines.flow.Flow

/**
 * The lowest-level byte-oriented transport contract.
 * Transports implementing this interface are responsible for handling stream framing
 * (e.g., reading a 4-byte length prefix and returning the framed payload).
 */
interface PeerTransport {

    /** Current connection state. */
    val isConnected: Boolean

    /** Emits connection state changes. */
    fun observeConnectionState(): Flow<ConnectionState>

    /**
     * Whether this transport instance is currently acting as the server/listener.
     * Used for deterministic cryptographic roles.
     */
    val isServer: Boolean

    /** Disconnects the transport. */
    suspend fun disconnect()

    /**
     * Sends bytes over the transport. The caller is responsible for providing
     * bytes that are already formatted for the wire (e.g., including length prefix).
     */
    suspend fun send(bytes: ByteArray)

    /**
     * Stream of completely read framed payloads. The implementation must consume the
     * length prefix and emit exactly the body bytes.
     */
    fun receive(): Flow<ByteArray>
}
