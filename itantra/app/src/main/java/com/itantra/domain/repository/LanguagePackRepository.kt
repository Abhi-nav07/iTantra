package com.itantra.domain.repository

import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.LanguagePackManifest
import com.itantra.domain.model.LanguagePackSummary
import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for what language packs exist, their install state, and
 * which one is active.
 */
interface LanguagePackRepository {

    /** Live view of every language's pack summary, in [com.itantra.domain.model.LanguageCatalog] order. */
    fun observePackSummaries(): Flow<List<LanguagePackSummary>>

    /** Live view of whichever language is currently ACTIVE, if any. */
    fun observeActiveLanguage(): Flow<LanguageCode?>

    /** Live view of the currently selected TARGET language, if any. */
    fun observeTargetLanguage(): Flow<LanguageCode?>

    suspend fun getManifest(code: LanguageCode): LanguagePackManifest?

    /**
     * Marks [code] as the single active language, enforcing the "only one
     * ACTIVE pack at a time" invariant. Fails (returns false) if the pack
     * is not DOWNLOADED. Does not itself load any inference engine — that
     * is [com.itantra.core.inference.ActiveLanguageSessionManager]'s job;
     * this only updates the repository's state-of-record.
     */
    suspend fun setActiveLanguage(code: LanguageCode): Boolean

    /**
     * Sets the active target language. Target languages only require the
     * cross-language translation model and TTS capability.
     */
    suspend fun setTargetLanguage(code: LanguageCode): Boolean

    // --- Not implemented in Task 01. Declared so the UI/domain layer can
    // already depend on a stable contract; calling these throws
    // NotImplementedError until a real repository lands. ---

    suspend fun startDownload(code: LanguageCode)

    suspend fun cancelDownload(code: LanguageCode)

    suspend fun deleteInstalledPack(code: LanguageCode)
}
