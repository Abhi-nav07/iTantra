package com.itantra.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketDecoder
import com.itantra.core.transport.packet.PacketEncoder
import com.itantra.core.transport.packet.PacketType
import com.itantra.domain.model.Measurement
import com.itantra.domain.model.TransmissionMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Implementation of [TransportEngine] over Bluetooth RFCOMM.
 * Handles framing, encoding/decoding, and RTT measurement.
 */
@SuppressLint("MissingPermission") // Permissions handled by UI before calling methods
class BluetoothTransportEngine(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) : TransportEngine {

    companion object {
        // Shared stable UUID for iTantra transceivers
        val ITANTRA_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP UUID is common for RFCOMM
        const val NAME = "iTantraTransceiver"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null
    private var readJob: Job? = null

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val stateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val incomingFlow = MutableSharedFlow<ItantraPacket>(extraBufferCapacity = 64)
    private val ackFlow = MutableSharedFlow<Long>(extraBufferCapacity = 64)

    private val writeMutex = Mutex()

    override val isConnected: Boolean
        get() = stateFlow.value == ConnectionState.CONNECTED

    override fun observeConnectionState(): Flow<ConnectionState> = stateFlow

    override suspend fun connect() {
        // Use connectToDevice or startServer explicitly below.
        // We override this to throw or do nothing because we need arguments.
        throw UnsupportedOperationException("Call startServer() or connectToDevice(device)")
    }

    suspend fun startServer() {
        disconnect()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            stateFlow.value = ConnectionState.ERROR
            return
        }

        connectionJob = scope.launch {
            stateFlow.value = ConnectionState.CONNECTING // Technically LISTENING
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, ITANTRA_UUID)
                val socket = serverSocket?.accept() // Blocking call
                if (socket != null) {
                    manageConnectedSocket(socket)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stateFlow.value = ConnectionState.ERROR
                disconnect()
            }
        }
    }

    suspend fun connectToDevice(device: BluetoothDevice) {
        disconnect()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            stateFlow.value = ConnectionState.ERROR
            return
        }

        connectionJob = scope.launch {
            stateFlow.value = ConnectionState.CONNECTING
            try {
                val socket = device.createRfcommSocketToServiceRecord(ITANTRA_UUID)
                socket.connect() // Blocking call
                manageConnectedSocket(socket)
            } catch (e: Exception) {
                e.printStackTrace()
                stateFlow.value = ConnectionState.ERROR
                disconnect()
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        activeSocket = socket
        inputStream = socket.inputStream
        outputStream = socket.outputStream
        stateFlow.value = ConnectionState.CONNECTED
        serverSocket?.close() // Stop listening for others
        serverSocket = null

        startReaderLoop()
    }

    private fun startReaderLoop() {
        readJob = scope.launch {
            try {
                val inStream = inputStream ?: return@launch
                while (isActive) {
                    // 1. Read 4-byte frame length
                    val lengthBuffer = ByteArray(4)
                    var bytesRead = 0
                    while (bytesRead < 4) {
                        val read = inStream.read(lengthBuffer, bytesRead, 4 - bytesRead)
                        if (read == -1) throw Exception("Stream closed")
                        bytesRead += read
                    }
                    val frameLength = ByteBuffer.wrap(lengthBuffer).order(ByteOrder.BIG_ENDIAN).getInt()
                    
                    if (frameLength <= 0 || frameLength > PacketDecoder.MAX_PAYLOAD_SIZE + 24) {
                        throw Exception("Invalid frame length: $frameLength")
                    }

                    // 2. Read frame body
                    val frameData = ByteArray(frameLength)
                    bytesRead = 0
                    while (bytesRead < frameLength) {
                        val read = inStream.read(frameData, bytesRead, frameLength - bytesRead)
                        if (read == -1) throw Exception("Stream closed")
                        bytesRead += read
                    }

                    // 3. Decode packet
                    val packet = PacketDecoder.decode(frameData)

                    if (packet.type == PacketType.ACK) {
                        ackFlow.emit(packet.messageId)
                    } else {
                        // Automatically ACK TEXT packets immediately
                        if (packet.type == PacketType.TEXT) {
                            sendAck(packet.messageId)
                        }
                        // Emit packet upward for Coordinator to handle (including CONTROL types)
                        incomingFlow.emit(packet)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (stateFlow.value != ConnectionState.DISCONNECTED) {
                    stateFlow.value = ConnectionState.ERROR
                }
                disconnect()
            }
        }
    }

    private fun sendAck(messageId: Long) {
        scope.launch {
            try {
                val ackPacket = ItantraPacket(type = PacketType.ACK, messageId = messageId)
                val encoded = PacketEncoder.encode(ackPacket)
                writeMutex.withLock {
                    outputStream?.write(encoded)
                    outputStream?.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun send(packet: ItantraPacket): TransmissionMetrics {
        if (!isConnected) return TransmissionMetrics()
        
        return withContext(Dispatchers.IO) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val encoded = PacketEncoder.encode(packet)

            var txLatency: Long? = null

            try {
                // Wait for ACK concurrently ONLY if it's a TEXT packet.
                // We don't ACK control or capabilities packets to save bandwidth.
                val ackDeferred = if (packet.type == PacketType.TEXT) {
                    async {
                        withTimeout(5000L) { // 5 sec timeout
                            // Wait for matching ACK in the flow
                            ackFlow.first { it == packet.messageId }
                            val t1 = SystemClock.elapsedRealtimeNanos()
                            t1 - t0
                        }
                    }
                } else null

                writeMutex.withLock {
                    outputStream?.write(encoded)
                    outputStream?.flush()
                }

                txLatency = ackDeferred?.await() // Returns RTT in nanos
                
            } catch (e: TimeoutCancellationException) {
                // No ACK received in time
                println("ACK timeout for message: ${packet.messageId}")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            TransmissionMetrics(
                payloadBytes = Measurement.Measured(packet.payload.size),
                packetBytes = Measurement.Measured(encoded.size),
                transmissionLatencyMillis = txLatency?.let { Measurement.Measured(it / 1_000_000) } ?: Measurement.NotMeasured
            )
        }
    }

    override suspend fun disconnect() {
        stateFlow.value = ConnectionState.DISCONNECTED
        connectionJob?.cancel()
        readJob?.cancel()
        try { inputStream?.close() } catch (e: Exception) {}
        try { outputStream?.close() } catch (e: Exception) {}
        try { activeSocket?.close() } catch (e: Exception) {}
        try { serverSocket?.close() } catch (e: Exception) {}
        inputStream = null
        outputStream = null
        activeSocket = null
        serverSocket = null
    }

    override fun receive(): Flow<ItantraPacket> = incomingFlow
}
