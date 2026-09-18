# ADR-004: Bluetooth RFCOMM Transport

## Status
Accepted

## Context
iTantra requires off-grid, two-way communication between mobile devices to transmit translated/transcribed speech during network outages. Wi-Fi Direct and Bluetooth Classic are the standard peer-to-peer mechanisms available.

## Decision
We elected to implement the first Transport abstraction over **Bluetooth Classic using RFCOMM (SPP)**.
We implemented `BluetoothTransportEngine` handling socket listening and client connecting under a single shared UUID.
We did not implement Wi-Fi Direct (P2P) in this iteration, though the `TransportEngine` interface design supports dropping it in later.

## Rationale
1. **Compatibility**: RFCOMM is universally supported on all Android hardware without the vendor fragmentation issues often seen in Wi-Fi Direct APIs.
2. **Reliability for Small Packets**: We are transmitting compressed binary text packets (ITP v1), not raw audio bytes. RFCOMM stream bandwidth (approx 2.1 Mbps max) is vastly more than sufficient for < 100 byte text frames.
3. **Power**: Bluetooth Classic uses less power than tearing up a Wi-Fi P2P group.

## Consequences
- Requires Android legacy Bluetooth and Location permissions for devices below API 31, and `BLUETOOTH_CONNECT`/`SCAN` for API 31+.
- RFCOMM acts as an unbounded stream. Thus, we had to implement deterministic stream framing (`PacketEncoder`/`PacketDecoder` chunking with a 4-byte length prefix).
- RTT is measured reliably because we engineered a native `ACK` packet type returned immediately upon decoding.
