# Security Protocol v1

This document specifies the wire format and handshake sequence for iTantra Secure Transport v1.

## Handshake Sequence

1. **Connection Established**: Underlying Bluetooth socket connects.
2. **Client Hello**: Client generates ECDH Keypair, 16-byte nonce, and sends `SECURE_HELLO(PubKey_C, Nonce_C)`.
3. **Server Hello**: Server receives `SECURE_HELLO`, generates its ECDH Keypair, 16-byte nonce, and replies with `SECURE_HELLO(PubKey_S, Nonce_S)`.
4. **Derivation**: Both derive `SharedSecret` -> `HKDF-SHA256` -> `(TxKey, RxKey, TxPrefix, RxPrefix, SAS)`.
5. **Verification**: Both UIs display the 6-digit SAS.
6. **Confirm**: User clicks `MATCHES`. Client/Server send `SECURE_VERIFY`.
7. **Secure Flow**: Session transitions to `SECURE_VERIFIED`. All subsequent data is encrypted.

## Packet Format

The `ItantraPacket` header is 29 bytes long:

```
Bytes 00..03 : MAGIC (0x49545031)
Byte  04     : VERSION (1)
Byte  05     : Type (1-9)
Byte  06     : Flags (Bitmask)
Byte  07     : Language ID (0-9)
Bytes 08..15 : Message ID (Long, ms timestamp)
Byte  16     : Security Version (0 = Plain, 1 = AES-GCM)
Bytes 17..24 : Counter (Long, Monotonic)
Bytes 25..28 : Payload Length N (Int)
Bytes 29..N  : Payload (Ciphertext + 16-byte Tag)
Bytes N..N+4 : CRC32
```

## AAD Construction
The AAD (Additional Authenticated Data) for AES-GCM consists of bytes 00..28 of the exact header. This binds the routing and metadata to the encryption tag.

## Nonce Construction
The AES-GCM IV is 12 bytes:
- Bytes 0..3: Directional Prefix (from HKDF)
- Bytes 4..11: 64-bit Packet Counter (Big Endian)
