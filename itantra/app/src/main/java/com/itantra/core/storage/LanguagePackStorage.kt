package com.itantra.core.storage

import com.itantra.domain.model.LanguageCode
import java.io.File

/**
 * Contract for where a language pack's files live on disk and how their
 * integrity is checked, once real downloads exist.
 *
 * NO IMPLEMENTATION EXISTS YET. [com.itantra.data.languagepack.MockLanguagePackRepository]
 * does not use this — it works entirely from in-memory sample data. This
 * interface exists so the eventual real repository has a clear, testable
 * seam for file I/O rather than mixing storage logic into the repository
 * itself.
 */
interface LanguagePackStorage {

    /** Root directory for [code]'s installed files, e.g. app-private
     *  external files dir under a per-language subfolder. Does not
     *  guarantee the directory exists yet. */
    fun packDirectory(code: LanguageCode): File

    fun isInstalled(code: LanguageCode): Boolean

    /** Verifies on-disk files against [expectedChecksums] (path -> SHA-256). */
    suspend fun verifyChecksums(code: LanguageCode, expectedChecksums: Map<String, String>): Boolean

    suspend fun deletePack(code: LanguageCode)

    /** Sum of installed pack sizes across all languages, for the
     *  diagnostics screen's "app storage" figure. */
    fun totalInstalledBytes(): Long
}
