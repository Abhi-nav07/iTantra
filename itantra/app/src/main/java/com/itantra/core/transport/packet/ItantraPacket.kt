package com.itantra.core.transport.packet

import com.itantra.domain.model.LanguageCode

/**
 * iTantra Semantic Packet Protocol (ITP v1).
 * Compact binary representation of a text message or acknowledgment.
 */
data class ItantraPacket(
    val type: PacketType,
    val flags: Byte = 0, // 0 = NORMAL, 1 = HIGH
    val languageCode: LanguageCode? = null,
    val messageId: Long,
    val securityVersion: Byte = 0,
    val counter: Long = 0,
    val payload: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ItantraPacket

        if (type != other.type) return false
        if (flags != other.flags) return false
        if (languageCode != other.languageCode) return false
        if (messageId != other.messageId) return false
        if (securityVersion != other.securityVersion) return false
        if (counter != other.counter) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + flags
        result = 31 * result + (languageCode?.hashCode() ?: 0)
        result = 31 * result + messageId.hashCode()
        result = 31 * result + securityVersion.hashCode()
        result = 31 * result + counter.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

enum class PacketType(val id: Byte) {
    TEXT(1),
    ACK(2),
    CONTROL(3),
    CAPABILITIES(4),
    TTS_STARTED(5),
    TTS_COMPLETED(6),
    TTS_FAILED(7),
    SECURE_HELLO(8),
    SECURE_VERIFY(9),
    EMERGENCY_CODE(10),
    HUMAN_ACK(11),
    ALERT_STARTED(12);

    companion object {
        fun fromId(id: Byte): PacketType? = entries.find { it.id == id }
    }
}

/**
 * Maps our domain LanguageCode to a stable wire numeric ID.
 */
object ProtocolLanguageMapper {
    private val codeToId = mapOf(
        LanguageCode.HINDI to 1.toByte(),
        LanguageCode.ENGLISH to 2.toByte(),
        LanguageCode.BENGALI to 3.toByte(),
        LanguageCode.GUJARATI to 4.toByte(),
        LanguageCode.MARATHI to 5.toByte(),
        LanguageCode.KANNADA to 6.toByte(),
        LanguageCode.MALAYALAM to 7.toByte(),
        LanguageCode.TAMIL to 8.toByte(),
        LanguageCode.TELUGU to 9.toByte(),
        LanguageCode.ODIA to 10.toByte()
    )
    private val idToCode = codeToId.entries.associate { (k, v) -> v to k }

    fun toWireId(code: LanguageCode?): Byte = code?.let { codeToId[it] } ?: 0.toByte()
    fun fromWireId(id: Byte): LanguageCode? = idToCode[id]

    fun toBitmask(languages: List<LanguageCode>): Short {
        var mask: Short = 0
        for (lang in languages) {
            val bitPosition = toWireId(lang).toInt() - 1
            if (bitPosition in 0..15) {
                mask = (mask.toInt() or (1 shl bitPosition)).toShort()
            }
        }
        return mask
    }

    fun fromBitmask(mask: Short): List<LanguageCode> {
        val list = mutableListOf<LanguageCode>()
        for (i in 0..15) {
            if ((mask.toInt() and (1 shl i)) != 0) {
                val code = fromWireId((i + 1).toByte())
                if (code != null) {
                    list.add(code)
                }
            }
        }
        return list
    }
}
