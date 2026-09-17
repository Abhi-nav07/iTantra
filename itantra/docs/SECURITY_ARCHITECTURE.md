# iTantra Security Architecture

## Overview
iTantra is an offline, peer-to-peer semantic transceiver. Its security architecture is explicitly designed to assume that the underlying transport (Bluetooth, Wi-Fi Direct) is entirely untrustworthy, unencrypted, and susceptible to MITM (Man-In-The-Middle) and Replay attacks.

Security is implemented completely at the application layer (`SecureSessionManager`).

## Threat Model
- **Eavesdropper (Passive):** Can read raw RFCOMM streams.
- **Tampering (Active):** Can modify bytes in transit.
- **MITM (Active):** Can intercept connections and proxy traffic between peers.
- **Replay (Active):** Can capture valid semantic packets and re-send them later.
- **Reflection (Active):** Can capture a packet from Peer A and send it back to Peer A.

## Mitigations

### 1. Confidentiality & Integrity (Eavesdropping & Tampering)
All semantic traffic (`PacketType.TEXT`, `TTS_STARTED`, `TTS_COMPLETED`) is encrypted using **AES-256-GCM**.
The packet payload becomes the ciphertext + a 16-byte authentication tag.
Any tampering with the ciphertext or the unencrypted header (which is fed into the cipher as AAD) will cause a decryption failure.

### 2. MITM Prevention
The handshake uses Ephemeral ECDH (NIST P-256) to establish a shared secret.
Both peers deterministically hash the handshake transcript (Public Keys + Nonces) and derive a 6-digit **Short Authentication String (SAS)**. 
Both users must visually verify that the 6-digit codes match and click `MATCHES` on the UI. If a MITM attacker proxies the connection, the attacker must establish two separate ECDH sessions, resulting in completely different SAS codes on the two devices.

### 3. Replay & Reflection Protection
Every secure packet includes a strictly monotonic `counter: Long`.
- The receiver stores the `highestAcceptedRxCounter`.
- Any packet with `counter <= highestAcceptedRxCounter` is immediately dropped.
- Reflection attacks are prevented by directional keys. HKDF extracts a unique TX key/nonce for A->B, and a separate RX key/nonce for B->A. A packet encrypted by A will fail decryption if sent back to A.
