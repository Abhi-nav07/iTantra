package com.itantra.domain.model

enum class EmergencyCode(val id: Byte) {
    HELP_REQUIRED(0x01),
    MEDICAL_EMERGENCY(0x02),
    FIRE(0x03),
    FLOOD(0x04),
    LANDSLIDE(0x05),
    EVACUATE(0x06),
    ROAD_BLOCKED(0x07),
    SEND_RESCUE_TEAM(0x08),
    DANGER(0x09),
    ALL_CLEAR(0x0A);

    companion object {
        fun fromId(id: Byte): EmergencyCode? = entries.find { it.id == id }
    }
}
