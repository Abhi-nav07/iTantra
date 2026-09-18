package com.itantra.data.languagepack

import com.itantra.domain.model.LanguageCatalog
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.LanguagePackAvailability
import com.itantra.domain.model.LanguagePackInstallState
import com.itantra.domain.model.LanguagePackManifest
import com.itantra.domain.model.LanguagePackSummary
import com.itantra.domain.repository.LanguagePackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Task 01 [LanguagePackRepository] implementation. Uses only in-memory
 * mock/sample state — no network calls, no file downloads, no persisted
 * storage.
 *
 * Initial state matches the product brief's example: Hindi and English
 * pre-"installed" (mock), the remaining 8 languages NOT_INSTALLED. This
 * lets the Language Packs screen and the active-language selector be
 * exercised end-to-end before any real download pipeline exists.
 *
 * IMPORTANT: "installed" here means only "this mock object's state flow
 * says INSTALLED" — no files exist on disk anywhere. See
 * docs/ARCHITECTURE.md for why this distinction matters and how a real
 * implementation will differ (backed by [com.itantra.core.storage.LanguagePackStorage]
 * + WorkManager-driven downloads + checksum validation).
 */
class MockLanguagePackRepository : LanguagePackRepository {

    private data class MockPackEntry(
        val sttInstallState: LanguagePackInstallState,
        val ttsInstallState: LanguagePackInstallState,
        val sttSizeBytes: Long?,
        val ttsSizeBytes: Long?
    )

    private val initialEntries: Map<LanguageCode, MockPackEntry> = buildMap {
        LanguageCode.entries.forEach { code ->
            val preInstalled = code == LanguageCode.HINDI || code == LanguageCode.ENGLISH
            put(
                code,
                MockPackEntry(
                    sttInstallState = if (preInstalled) {
                        LanguagePackInstallState.INSTALLED
                    } else {
                        LanguagePackInstallState.NOT_INSTALLED
                    },
                    ttsInstallState = if (preInstalled) {
                        LanguagePackInstallState.INSTALLED
                    } else {
                        LanguagePackInstallState.NOT_INSTALLED
                    },
                    sttSizeBytes = if (preInstalled) MOCK_INSTALLED_SIZE_BYTES else null,
                    ttsSizeBytes = if (preInstalled) MOCK_INSTALLED_SIZE_BYTES else null
                )
            )
        }
    }

    private val entriesFlow = MutableStateFlow(initialEntries)

    // Hindi starts as the active language, matching the "Hindi active" example
    // in the product brief's model-lifecycle rule.
    private val activeLanguageFlow: MutableStateFlow<LanguageCode?> =
        MutableStateFlow(LanguageCode.HINDI)

    private val targetLanguageFlow: MutableStateFlow<LanguageCode?> =
        MutableStateFlow(LanguageCode.ENGLISH)

    override fun observePackSummaries(): Flow<List<LanguagePackSummary>> =
        entriesFlow.map { entries ->
            LanguageCatalog.all.map { language ->
                val entry = entries.getValue(language.code)
                val isSttDownloaded = entry.sttInstallState == LanguagePackInstallState.INSTALLED || entry.sttInstallState == LanguagePackInstallState.UPDATE_AVAILABLE
                val isTtsDownloaded = entry.ttsInstallState == LanguagePackInstallState.INSTALLED || entry.ttsInstallState == LanguagePackInstallState.UPDATE_AVAILABLE
                
                val availability = when {
                    !isSttDownloaded && !isTtsDownloaded -> LanguagePackAvailability.AVAILABLE
                    activeLanguageFlow.value == language.code -> LanguagePackAvailability.ACTIVE
                    else -> LanguagePackAvailability.DOWNLOADED
                }
                LanguagePackSummary(
                    language = language,
                    sttInstallState = entry.sttInstallState,
                    ttsInstallState = entry.ttsInstallState,
                    availability = availability,
                    sttSizeBytes = entry.sttSizeBytes,
                    ttsSizeBytes = entry.ttsSizeBytes,
                )
            }
        }

    override fun observeActiveLanguage(): Flow<LanguageCode?> = activeLanguageFlow

    override fun observeTargetLanguage(): Flow<LanguageCode?> = targetLanguageFlow

    override suspend fun getManifest(code: LanguageCode): LanguagePackManifest? =
        // Task 01 does not wire asset-file reading into the mock repository
        // (see assets/language_packs/README.md) — only the parser
        // (LanguagePackManifestParser) and the sample JSON exist as a
        // demonstrated, testable seam for a future real implementation.
        null

    override suspend fun setActiveLanguage(code: LanguageCode): Boolean {
        val entry = entriesFlow.value[code] ?: return false
        val isDownloaded = (entry.sttInstallState == LanguagePackInstallState.INSTALLED ||
            entry.sttInstallState == LanguagePackInstallState.UPDATE_AVAILABLE) || 
            (entry.ttsInstallState == LanguagePackInstallState.INSTALLED ||
            entry.ttsInstallState == LanguagePackInstallState.UPDATE_AVAILABLE)
        if (!isDownloaded) return false

        activeLanguageFlow.value = code
        return true
    }

    override suspend fun setTargetLanguage(code: LanguageCode): Boolean {
        targetLanguageFlow.value = code
        return true
    }

    override suspend fun startDownload(code: LanguageCode) {
        throw NotImplementedError(
            "Language pack download is not implemented in Task 01. " +
                "MockLanguagePackRepository only simulates pre-set install state."
        )
    }

    override suspend fun cancelDownload(code: LanguageCode) {
        throw NotImplementedError("Language pack download is not implemented in Task 01.")
    }

    override suspend fun deleteInstalledPack(code: LanguageCode) {
        throw NotImplementedError("Language pack deletion is not implemented in Task 01.")
    }

    companion object {
        private const val MOCK_INSTALLED_SIZE_BYTES = 62_000_000L
        private const val MOCK_DOWNLOAD_SIZE_BYTES = 45_000_000L
    }
}
