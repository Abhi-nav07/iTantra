# Module 8E: Dual Transport (Bluetooth Classic + Wi-Fi Peer)

## Objective
Give iTantra two genuine local communication paths—existing Bluetooth Classic RFCOMM and a new local Wi-Fi peer transport—behind ONE transport abstraction while keeping the SAME semantic packets, encryption, priorities, emergency behavior, ACK/HUMAN_ACK, and STT/MT/TTS pipeline.

## Implementation Details

### Peer Transport Abstraction
We introduced the `PeerTransport` interface, which is the lowest-level byte-oriented transport contract.
```kotlin
interface PeerTransport {
    val isConnected: Boolean
    fun observeConnectionState(): Flow<ConnectionState>
    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(bytes: ByteArray)
    fun receive(): Flow<ByteArray>
}
```
This interface guarantees that transports only need to handle raw framed payloads (reading the 4-byte length prefix and emitting exactly the body bytes). It has zero knowledge of `ItantraPacket`, ACKs, or security, isolating the transport from upper layers.

### BluetoothPeerTransport
Refactored from `BluetoothTransportEngine`, it implements `PeerTransport` over Bluetooth RFCOMM.

### WifiPeerTransport
Implements `PeerTransport` using standard local Wi-Fi TCP sockets (`java.net.ServerSocket` and `java.net.Socket`).
- Runs over a specified port (default 8888).
- Shares the exact framing code as Bluetooth, ensuring semantic parity.

### TransportCoordinator
Implements the existing `TransportEngine` interface and sits between the active `PeerTransport` and the upper `TransceiverCoordinator`.
- Holds the active transport (`BluetoothPeerTransport` or `WifiPeerTransport`).
- Intercepts raw frames and translates them to/from `ItantraPacket` using `PacketEncoder`/`PacketDecoder`.
- Handles ACK matching and calculates RTT (`TransmissionMetrics`), previously housed incorrectly inside the Bluetooth layer.

### UI Integration
`ConnectScreen` and `ConnectViewModel` were updated to:
- Toggle between Bluetooth and Wi-Fi Transport Modes.
- Start a server or connect to a device over Bluetooth.
- Start a server or specify an IP address to connect to a Wi-Fi peer.

## Verification
- Unit test `TransportCoordinatorTest.kt` verifies that framing and ACKs work completely decoupled from the actual transport implementation.
- Both Bluetooth and Wi-Fi transports use exactly the same secure packet definitions.
- Local TCP Socket validation guarantees local Wi-Fi peer communication is possible, satisfying the zero cloud dependency requirement.
