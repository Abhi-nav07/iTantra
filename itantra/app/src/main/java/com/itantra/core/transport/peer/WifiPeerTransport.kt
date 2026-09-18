package com.itantra.core.transport.peer

import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.packet.PacketDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementation of [PeerTransport] over local Wi-Fi TCP sockets.
 * Handles length-prefix framing but does NOT parse semantic packets.
 */
class WifiPeerTransport : PeerTransport {

    companion object {
        const val DEFAULT_PORT = 8888
        const val CONNECTION_TIMEOUT_MS = 5000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null
    private var readJob: Job? = null

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
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

    suspend fun startServer(port: Int = DEFAULT_PORT) {
        disconnect()
        _isServer = true

        connectionJob = scope.launch {
            stateFlow.value = ConnectionState.CONNECTING // LISTENING
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                val socket = serverSocket?.accept() // Blocking call
                if (socket != null) {
                    manageConnectedSocket(socket)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    e.printStackTrace()
                    stateFlow.value = ConnectionState.ERROR
                }
                disconnect()
            }
        }
    }

    suspend fun connectToAddress(host: String, port: Int = DEFAULT_PORT) {
        disconnect()
        _isServer = false

        connectionJob = scope.launch {
            stateFlow.value = ConnectionState.CONNECTING
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS)
                manageConnectedSocket(socket)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    e.printStackTrace()
                    stateFlow.value = ConnectionState.ERROR
                }
                disconnect()
            }
        }
    }

    private fun manageConnectedSocket(socket: Socket) {
        activeSocket = socket
        inputStream = socket.getInputStream()
        outputStream = socket.getOutputStream()
        stateFlow.value = ConnectionState.CONNECTED

        // Stop listening if we were acting as a server
        try { serverSocket?.close() } catch (e: Exception) {}
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
                if (e !is CancellationException) {
                    e.printStackTrace()
                    if (stateFlow.value != ConnectionState.DISCONNECTED) {
                        stateFlow.value = ConnectionState.ERROR
                    }
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
