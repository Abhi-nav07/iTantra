package com.itantra.domain.model

/**
 * Stable, ISO-639-1-style identifier for every language iTantra must
 * eventually support end-to-end (STT -> transport -> TTS).
 *
 * This is the SINGLE source of truth for "which languages exist" in the
 * system. Nothing else in the codebase should hardcode a language string
 * or iterate over a duplicated list — everything should derive from
 * [entries] or from [LanguageCatalog].
 *
 * The [wireCode] is what gets embedded in manifests, packet headers, and
 * storage paths. It intentionally matches common ISO-639-1 codes so it is
 * stable and recognizable, independent of how the enum constant is named.
 */
enum class LanguageCode(val wireCode: String) {
    HINDI("hi"),
    ENGLISH("en"),
    BENGALI("bn"),
    GUJARATI("gu"),
    MARATHI("mr"),
    KANNADA("kn"),
    MALAYALAM("ml"),
    TAMIL("ta"),
    TELUGU("te"),
    ODIA("or");

    companion object {
        /**
         * Resolve a wire code (e.g. from a manifest file or a received
         * packet header) back to a [LanguageCode]. Returns null instead of
         * throwing so callers on the receive path can handle an unknown
         * code as a data/version problem rather than crash.
         */
        fun fromWireCode(code: String): LanguageCode? =
            entries.firstOrNull { it.wireCode.equals(code, ignoreCase = true) }
    }
}
