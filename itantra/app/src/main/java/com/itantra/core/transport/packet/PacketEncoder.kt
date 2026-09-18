package com.itantra.core.transport.packet

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Encodes an [ItantraPacket] into a framed ByteArray ready for a stream.
 *
 * Wire Format V2 (Big Endian):
 * 0..3   Frame Length (Int) -> Header(23) + Payload(N) + CRC(4) = 27 + N
 * 4..7   MAGIC ('I','T','P','1') -> 0x49545031
 * 8      Version (Byte) -> 2
 * 9      Type (Byte)
 * 10     Flags (Byte)
 * 11     Legacy Language ID (Byte)
 * 12     Source Language ID (Byte)
 * 13     Target Language ID (Byte)
 * 14     Translation Mode ID (Byte)
 * 15..22 Message ID (Long)
 * 23     Security Version (Byte)
 * 24..31 Counter (Long)
 * 32..35 Payload Length N (Int)
 * 36..   Payload (N Bytes)
 * end    CRC32 (Int) -> Computed over Bytes 4..(end-4)
 */
object PacketEncoder {
    const val MAGIC = 0x49545031
    const val VERSION: Byte = 2

    /**
     * Extracts exactly the header bytes from the packet (the bytes that form the Additional Authenticated Data).
     */
    fun extractAad(packet: ItantraPacket): ByteArray {
        // AAD covers the fixed-size header fields only.
        // payload.size is NOT included because encrypt() computes AAD with
        // the plaintext payload while decrypt() would recompute it with the
        // ciphertext payload (plaintext + 16-byte GCM tag), causing a mismatch.
        // GCM already authenticates the ciphertext length implicitly.
        val buffer = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.put(VERSION)
        buffer.put(packet.type.id)
        buffer.put(packet.flags)
        buffer.put(ProtocolLanguageMapper.toWireId(packet.languageCode))
        buffer.put(ProtocolLanguageMapper.toWireId(packet.sourceLanguage ?: packet.languageCode))
        buffer.put(ProtocolLanguageMapper.toWireId(packet.targetLanguage ?: packet.languageCode))
        buffer.put(packet.translationMode.id)
        buffer.putLong(packet.messageId)
        buffer.put(packet.securityVersion)
        buffer.putLong(packet.counter)
        return buffer.array()
    }

    fun encode(packet: ItantraPacket): ByteArray {
        val payloadLen = packet.payload.size
        // 32 bytes header + N payload + 4 CRC
        val frameContentLength = 32 + payloadLen + 4 
        val totalFrameLength = 4 + frameContentLength // including the 4 byte frame size prefix

        val buffer = ByteBuffer.allocate(totalFrameLength).order(ByteOrder.BIG_ENDIAN)

        // 1. Frame Length Prefix
        buffer.putInt(frameContentLength)

        // Track start of CRC covered data
        val crcStart = buffer.position()

        // 2. Header
        buffer.putInt(MAGIC)
        buffer.put(VERSION)
        buffer.put(packet.type.id)
        buffer.put(packet.flags)
        buffer.put(ProtocolLanguageMapper.toWireId(packet.languageCode))
        buffer.put(ProtocolLanguageMapper.toWireId(packet.sourceLanguage ?: packet.languageCode))
        buffer.put(ProtocolLanguageMapper.toWireId(packet.targetLanguage ?: packet.languageCode))
        buffer.put(packet.translationMode.id)
        buffer.putLong(packet.messageId)
        buffer.put(packet.securityVersion)
        buffer.putLong(packet.counter)
        buffer.putInt(payloadLen)

        // 3. Payload
        if (payloadLen > 0) {
            buffer.put(packet.payload)
        }

        // Calculate CRC32
        val crcEnd = buffer.position()
        val crcData = ByteArray(crcEnd - crcStart)
        buffer.position(crcStart)
        buffer.get(crcData)

        val crc32 = CRC32()
        crc32.update(crcData)
        val crcValue = crc32.value.toInt()

        // 4. CRC
        buffer.position(crcEnd)
        buffer.putInt(crcValue)

        return buffer.array()
    }
}
