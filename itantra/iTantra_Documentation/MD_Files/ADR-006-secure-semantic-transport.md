# ADR-006: Application-Layer Secure Semantic Transport

## Status
Accepted

## Context
iTantra uses a semantic packet protocol over raw RFCOMM (Bluetooth) to transmit recognized text and control packets between peers without relying on cloud services. While RFCOMM provides a basic link, the application requires MITM protection, confidentiality, and replay prevention tailored to the application's semantic payload, ensuring sensitive conversations remain secure even over potentially compromised basebands or Wi-Fi Direct in the future. 

The security layer needs to be extremely lightweight to preserve the low-latency STT/TTS pipeline, and it must run entirely offline on low-end devices (`minSdk 26`).

## Decision
We implement a zero-dependency, JCA-based Authenticated Encryption layer (`SecureSessionManager`).

1. **Key Exchange**: Ephemeral ECDH over NIST P-256 (`secp256r1`). This is universally supported on `minSdk 26` without external libraries (like BouncyCastle or Tink).
2. **KDF**: HKDF-SHA256 (Extract-and-Expand as per RFC 5869) to derive 32-byte directional keys (TX/RX) and 4-byte directional nonce prefixes.
3. **Encryption**: AES-256-GCM without padding.
4. **AAD (Additional Authenticated Data)**: The unencrypted packet header is authenticated as AAD to prevent routing or metadata tampering.
5. **Replay Protection**: A strictly increasing 64-bit monotonic counter is embedded in the packet header and checked against the highest previously accepted counter.
6. **Peer Verification**: A 6-digit numeric Short Authentication String (SAS) is derived from the shared secret and handshake transcript. The UI displays this code on both devices, requiring manual confirmation from the user to prevent MITM attacks.

## Consequences
- **Positive**: Complete confidentiality, integrity, and authenticity for semantic packets with < 1ms overhead per message.
- **Positive**: Zero external dependencies added to the `build.gradle.kts`.
- **Positive**: Directional keys and counters prevent reflection attacks.
- **Negative**: Handshake adds an additional round-trip before traffic can flow.
- **Negative**: SAS verification requires user interaction (manual UI confirmation), which slightly slows down the initial connection phase.
