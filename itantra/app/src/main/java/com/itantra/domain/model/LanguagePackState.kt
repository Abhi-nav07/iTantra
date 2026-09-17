package com.itantra.domain.model

/**
 * Lifecycle state of a single language pack's on-disk install.
 *
 * This describes STORAGE state only — whether the pack's files exist on
 * disk and in what condition. It is deliberately separate from whether the
 * pack is the one currently loaded for inference (see
 * [LanguagePackSummary.isActive] / [ActiveLanguageSelection]).
 */
enum class LanguagePackInstallState {
    /** Pack is known (metadata exists) but no files have been downloaded. */
    NOT_INSTALLED,

    /** Download in progress. */
    DOWNLOADING,

    /** Files downloaded, verification/unpacking/setup in progress. */
    INSTALLING,

    /** Fully installed and checksum-verified; eligible to become active. */
    INSTALLED,

    /** Installed, but a newer [LanguagePackManifest.packVersion] exists. */
    UPDATE_AVAILABLE,

    /** Installed files failed checksum validation and cannot be trusted. */
    CORRUPTED,

    /** Download or install failed for a reason other than corruption. */
    ERROR,
}

/**
 * The three concepts the spec calls out as needing to be visually and
 * architecturally distinct:
 *
 * - AVAILABLE: the language exists in the catalog and could be downloaded,
 *   but nothing has been fetched yet ([LanguagePackInstallState.NOT_INSTALLED]).
 * - DOWNLOADED: pack files are present and verified on disk
 *   ([LanguagePackInstallState.INSTALLED] or [LanguagePackInstallState.UPDATE_AVAILABLE]),
 *   but this pack is not necessarily loaded into memory right now.
 * - ACTIVE: this pack is DOWNLOADED *and* is the single language currently
 *   loaded into inference memory (see ActiveLanguageSessionManager).
 *
 * A pack can be ACTIVE only if it is also DOWNLOADED. At most one pack in
 * the whole system may be ACTIVE at any time — that invariant is enforced
 * by the active-language session manager, not by this enum.
 */
enum class LanguagePackAvailability {
    AVAILABLE,
    DOWNLOADED,
    ACTIVE,
}

/**
 * Combined, UI-ready view of one language's pack: static catalog info plus
 * live install/activity state. Repositories emit this; screens render it
 * directly without recomputing availability themselves.
 */
data class LanguagePackSummary(
    val language: Language,
    val sttInstallState: LanguagePackInstallState,
    val ttsInstallState: LanguagePackInstallState,
    val availability: LanguagePackAvailability,
    val sttSizeBytes: Long? = null,
    val ttsSizeBytes: Long? = null,
    val downloadProgressPercent: Int? = null,
) {
    val isActive: Boolean get() = availability == LanguagePackAvailability.ACTIVE
    
    val isSttDownloaded: Boolean get() =
        sttInstallState == LanguagePackInstallState.INSTALLED || sttInstallState == LanguagePackInstallState.UPDATE_AVAILABLE
        
    val isTtsDownloaded: Boolean get() =
        ttsInstallState == LanguagePackInstallState.INSTALLED || ttsInstallState == LanguagePackInstallState.UPDATE_AVAILABLE

    // Pack is considered "downloaded" overall if at least one component is available locally
    val isDownloaded: Boolean get() =
        availability == LanguagePackAvailability.DOWNLOADED || isActive || isSttDownloaded || isTtsDownloaded
}
