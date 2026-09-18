package com.itantra.domain.model

enum class TranslationMode(val id: Byte) {
    NONE(0),
    DIRECT(1),
    PIVOT_ENGLISH(2);

    companion object {
        fun fromId(id: Byte): TranslationMode = entries.find { it.id == id } ?: NONE
    }
}
