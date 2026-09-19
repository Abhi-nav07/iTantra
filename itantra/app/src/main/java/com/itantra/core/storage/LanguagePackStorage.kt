package com.itantra.core.storage

import com.itantra.domain.model.LanguageCode
import java.io.File

/**
 * Contract for where language pack files live on disk and how their
 * integrity is checked at runtime.
 */
interface LanguagePackStorage {

    /** Root directory for [code]'s installed files, e.g. app-private
     *  files dir under a per-language subfolder. */
    fun packDirectory(code: LanguageCode): File

    /** Returns true only if BOTH shared STT and [code]'s TTS are fully installed and verified. */
    fun isInstalled(code: LanguageCode): Boolean

    /** Verifies on-disk files against [expectedChecksums] (path/filename -> SHA-256). */
    suspend fun verifyChecksums(code: LanguageCode, expectedChecksums: Map<String, String>): Boolean

    /** Deletes [code]'s language pack (e.g. TTS) without removing shared models like shared STT. */
    suspend fun deletePack(code: LanguageCode)

    /** Sum of installed pack sizes across all languages. */
    fun totalInstalledBytes(): Long

    /** Returns the shared STT directory path (e.g. files/language_packs/shared/stt). */
    fun sharedSttDirectory(): File = File(packDirectory(LanguageCode.HINDI).parentFile, "shared/stt")

    /** Returns true if all required shared STT model files exist and are non-empty. */
    fun isSharedSttInstalled(): Boolean = false

    /** Returns true if all required TTS model files for [code] exist and are non-empty. */
    fun isTtsInstalled(code: LanguageCode): Boolean = false
}
