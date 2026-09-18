# iTantra Semantic Packet Protocol (ITP v1)

## Overview
Because raw Bluetooth RFCOMM sockets behave as continuous byte streams rather than datagram sockets, we must enforce our own message framing. ITP v1 is a compact binary representation of transceiver events (TEXT translations, ACKs) transmitted between peers.

## Wire Format Structure

All fields are **Big Endian**.

| Offset | Size (bytes) | Type | Description |
| :--- | :--- | :--- | :--- |
| 0 | 4 | `Int` | **Frame Length Prefix**: Total length of the frame content (Header + Payload + CRC). Required for stream boundaries. |
| 4 | 4 | `Int` | **MAGIC**: Always `0x49545031` ("ITP1") |
| 8 | 1 | `Byte` | **Version**: Protocol version, currently `1` |
| 9 | 1 | `Byte` | **Type ID**: `1` = TEXT, `2` = ACK, `3` = CONTROL |
| 10 | 1 | `Byte` | **Flags**: Priority/QoS. `0` = Normal, `1` = High |
| 11 | 1 | `Byte` | **Language ID**: Numeric wire ID for Unicode language (e.g. `1` = Hindi, `2` = English). `0` if N/A. |
| 12 | 8 | `Long` | **Message ID**: Unique identifier, usually timestamp at origin. Crucial for ACK-matching. |
| 20 | 4 | `Int` | **Payload Length N**: Length of the raw UTF-8 string payload. |
| 24 | N | `ByteArray` | **Payload**: Raw payload bytes (e.g., UTF-8 transcribed text). Empty for ACKs. |
| 24+N | 4 | `Int` | **CRC32**: Checksum covering from byte 4 (MAGIC) through the end of the Payload to detect Bluetooth byte corruption. |

## ACK & RTT Handshake
Whenever a device decodes an `ITP` frame with Type `1` (TEXT) and a valid CRC32, it will automatically encode and flush an `ITP` frame with Type `2` (ACK) using the *same Message ID*.

The sender tracks the nanosecond elapsed timestamp when flushing the socket and measures the delta upon receiving the matching ACK, rendering an accurate cross-device Round Trip Time (RTT).
