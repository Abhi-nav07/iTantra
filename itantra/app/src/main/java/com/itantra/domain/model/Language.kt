package com.itantra.domain.model

/**
 * A language iTantra can operate in. This is metadata only — it says
 * nothing about whether a pack is installed or active. See
 * [LanguagePackState] / [ActiveLanguageSelection] for that.
 *
 * @param code stable identifier, see [LanguageCode]
 * @param displayName English/Latin display name shown in UI lists and logs
 * @param nativeDisplayName name rendered in the language's own script,
 *   where practical to include; falls back to [displayName] when a
 *   reliable native rendering isn't available.
 */
data class Language(
    val code: LanguageCode,
    val displayName: String,
    val nativeDisplayName: String,
)

/**
 * Central, hardcoded catalog of the 10 languages iTantra must support.
 *
 * This is the ONLY place language display metadata is defined. Screens,
 * ViewModels, and the mock pack repository all read from here instead of
 * declaring their own language lists, so adding or renaming a language is
 * a one-file change.
 */
object LanguageCatalog {
    val all: List<Language> = listOf(
        Language(LanguageCode.HINDI, "Hindi", "हिन्दी"),
        Language(LanguageCode.ENGLISH, "English", "English"),
        Language(LanguageCode.BENGALI, "Bengali", "বাংলা"),
        Language(LanguageCode.GUJARATI, "Gujarati", "ગુજરાતી"),
        Language(LanguageCode.MARATHI, "Marathi", "मराठी"),
        Language(LanguageCode.KANNADA, "Kannada", "ಕನ್ನಡ"),
        Language(LanguageCode.MALAYALAM, "Malayalam", "മലയാളം"),
        Language(LanguageCode.TAMIL, "Tamil", "தமிழ்"),
        Language(LanguageCode.TELUGU, "Telugu", "తెలుగు"),
        Language(LanguageCode.ODIA, "Odia", "ଓଡ଼ିଆ"),
    )

    init {
        check(all.size == LanguageCode.entries.size) {
            "LanguageCatalog is out of sync with LanguageCode — every " +
                "LanguageCode must have exactly one Language entry."
        }
    }

    fun byCode(code: LanguageCode): Language =
        all.first { it.code == code }
}
