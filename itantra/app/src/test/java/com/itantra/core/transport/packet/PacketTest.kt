package com.itantra.core.transport.packet

import com.itantra.domain.model.LanguageCode
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketTest {

    @Test
    fun testEncodeDecodeRoundTrip() {
        val payload = "भूस्खलन के कारण सड़क बंद है।".toByteArray(Charsets.UTF_8)
        val packet = ItantraPacket(
            type = PacketType.TEXT,
            flags = 0,
            languageCode = LanguageCode.ENGLISH,
            sourceLanguage = LanguageCode.HINDI,
            targetLanguage = LanguageCode.ENGLISH,
            translationMode = com.itantra.domain.model.TranslationMode.DIRECT,
            messageId = 123456789L,
            payload = payload
        )

        val framedBytes = PacketEncoder.encode(packet)

        // Strip the 4-byte frame length prefix for the decoder
        val buffer = ByteBuffer.wrap(framedBytes).order(ByteOrder.BIG_ENDIAN)
        val frameLength = buffer.getInt()
        assertEquals(framedBytes.size - 4, frameLength)

        val frameData = ByteArray(frameLength)
        buffer.get(frameData)

        val decoded = PacketDecoder.decode(frameData)

        assertEquals(packet, decoded)
        assertEquals(LanguageCode.ENGLISH, decoded.languageCode)
        assertEquals(LanguageCode.HINDI, decoded.sourceLanguage)
        assertEquals(LanguageCode.ENGLISH, decoded.targetLanguage)
        assertEquals(com.itantra.domain.model.TranslationMode.DIRECT, decoded.translationMode)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun testEmptyPayload() {
        val packet = ItantraPacket(
            type = PacketType.ACK,
            messageId = 999L
        )

        val framedBytes = PacketEncoder.encode(packet)
        val buffer = ByteBuffer.wrap(framedBytes).order(ByteOrder.BIG_ENDIAN)
        val frameLength = buffer.getInt()
        val frameData = ByteArray(frameLength)
        buffer.get(frameData)

        val decoded = PacketDecoder.decode(frameData)
        assertEquals(packet, decoded)
        assertTrue(decoded.payload.isEmpty())
    }

    @Test(expected = PacketDecodeException::class)
    fun testCorruptCrc() {
        val packet = ItantraPacket(type = PacketType.TEXT, messageId = 1L)
        val framedBytes = PacketEncoder.encode(packet)

        val buffer = ByteBuffer.wrap(framedBytes).order(ByteOrder.BIG_ENDIAN)
        val frameLength = buffer.getInt()
        val frameData = ByteArray(frameLength)
        buffer.get(frameData)

        // Corrupt payload
        frameData[10] = frameData[10].inc()

        PacketDecoder.decode(frameData) // Should throw
    }
}
