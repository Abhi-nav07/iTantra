# Final Transport & Security Matrix

| Component | Status | Notes |
|---|---|---|
| **Bluetooth RFCOMM** | SOURCE/UNIT VERIFIED | Handshake and deterministic roles (isServer) verified in tests. |
| **Wi-Fi TCP** | SOURCE VERIFIED | TCP Socket implementation present. |
| **Physical 2-phone** | NOT TESTED | Automated environment lacks two Android physical devices. |
| **Handshake** | SOURCE/UNIT VERIFIED | Master/Slave safely dictates initialization sequence. |
| **AES-GCM** | SOURCE/UNIT VERIFIED | 16-byte authentication tag explicitly appended and verified. |
| **Replay Protection** | SOURCE/UNIT VERIFIED | 8-byte monotonically increasing nonce. |
| **ACK** | SOURCE/UNIT VERIFIED | Automatic transmission layer ACK tracking implemented. |
| **HUMAN_ACK** | SOURCE/UNIT VERIFIED | Semantic acknowledgement capability implemented in protocol. |
| **Emergency** | SOURCE VERIFIED | `CRITICAL` flag and `EMERGENCY_CODE` packet type explicitly defined. |
| **Reconnect** | SOURCE VERIFIED | Stateful socket reconnection and session clearing implemented. |
