package com.itantra.core.transport.peer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.packet.PacketDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Implementation of [PeerTransport] over Bluetooth RFCOMM.
 * Handles length-prefix framing but does NOT parse semantic packets.
 */
@SuppressLint("MissingPermission") // Permissions handled by UI before calling methods
class BluetoothPeerTransport(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) : PeerTransport {

    companion object {
        // Shared stable UUID for iTantra transceivers
        val ITANTRA_UUID: UUID = UUID.fromString("20f01a35-26a1-432a-bc95-021b36d0130a")
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
    private val incomingFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

    private val writeMutex = Mutex()

    private var _isServer = false
    override val isServer: Boolean get() = _isServer

    override val isConnected: Boolean
        get() = stateFlow.value == ConnectionState.CONNECTED

    override fun observeConnectionState(): Flow<ConnectionState> = stateFlow

    suspend fun startServer() {
        disconnect()
        _isServer = true
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
        _isServer = false
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

                    if (frameLength <= 0 || frameLength > PacketDecoder.MAX_FRAME_BODY_SIZE) {
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

                    // 3. Emit deframed raw packet data upward
                    incomingFlow.emit(frameData)
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

    override suspend fun send(bytes: ByteArray) {
        if (!isConnected) return

        withContext(Dispatchers.IO) {
            try {
                writeMutex.withLock {
                    outputStream?.write(bytes)
                    outputStream?.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stateFlow.value = ConnectionState.ERROR
                disconnect()
            }
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

    override fun receive(): Flow<ByteArray> = incomingFlow
}
