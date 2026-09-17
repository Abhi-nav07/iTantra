package com.itantra.data.languagepack

import com.itantra.domain.model.LanguagePackManifest
import kotlinx.serialization.json.Json

/**
 * Parses a [LanguagePackManifest] from raw JSON text.
 *
 * Kept as a standalone object (rather than inline in the repository) so it
 * can be unit tested against the schema independent of any Android
 * context or file I/O.
 */
object LanguagePackManifestParser {

    private val json = Json {
        ignoreUnknownKeys = true // forward-compatible with additive schema changes
        isLenient = false
        prettyPrint = false
    }

    /**
     * Returns the parsed manifest, or null if [rawJson] is not valid JSON
     * or fails to match the schema. Deliberately does not throw for
     * malformed input — a real repository loading manifests from disk or
     * network must treat a bad manifest as a data problem (surface as
     * [com.itantra.domain.model.LanguagePackInstallState.ERROR]), not a
     * crash.
     */
    fun parseOrNull(rawJson: String): LanguagePackManifest? =
        try {
            val manifest = json.decodeFromString(LanguagePackManifest.serializer(), rawJson)
            if (manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                null
            } else {
                manifest
            }
        } catch (_: Exception) {
            null
        }

    const val SUPPORTED_SCHEMA_VERSION = 1
}
