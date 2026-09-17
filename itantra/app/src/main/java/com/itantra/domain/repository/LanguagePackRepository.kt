package com.itantra.domain.repository

import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.LanguagePackManifest
import com.itantra.domain.model.LanguagePackSummary
import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for what language packs exist, their install state, and
 * which one is active.
 *
 * Task 01 provides exactly one implementation:
 * [com.itantra.data.languagepack.MockLanguagePackRepository], which uses
 * only in-memory sample data — no network, no real files, no download
 * logic. It exists to prove the contract and unblock UI/ViewModel work
 * before a real implementation (backed by DownloadManager/WorkManager +
 * on-disk storage + checksum validation) is built.
 */
interface LanguagePackRepository {

    /** Live view of every language's pack summary, in [com.itantra.domain.model.LanguageCatalog] order. */
    fun observePackSummaries(): Flow<List<LanguagePackSummary>>

    /** Live view of whichever language is currently ACTIVE, if any. */
    fun observeActiveLanguage(): Flow<LanguageCode?>

    suspend fun getManifest(code: LanguageCode): LanguagePackManifest?

    /**
     * Marks [code] as the single active language, enforcing the "only one
     * ACTIVE pack at a time" invariant. Fails (returns false) if the pack
     * is not DOWNLOADED. Does not itself load any inference engine — that
     * is [com.itantra.core.inference.ActiveLanguageSessionManager]'s job;
     * this only updates the repository's state-of-record.
     */
    suspend fun setActiveLanguage(code: LanguageCode): Boolean

    // --- Not implemented in Task 01. Declared so the UI/domain layer can
    // already depend on a stable contract; calling these throws
    // NotImplementedError until a real repository lands. ---

    suspend fun startDownload(code: LanguageCode)

    suspend fun cancelDownload(code: LanguageCode)

    suspend fun deleteInstalledPack(code: LanguageCode)
}
