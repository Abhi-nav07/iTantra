package com.itantra.data.languagepack

import android.content.Context
import com.itantra.core.inference.ModelFileSpecs
import com.itantra.core.storage.LanguagePackStorage
import com.itantra.domain.model.LanguageCode
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class FileLanguagePackStorage(
    private val filesDir: File
) : LanguagePackStorage {

    constructor(context: Context) : this(context.filesDir)

    private val packsDir = File(filesDir, "language_packs")

    init {
        if (!packsDir.exists()) {
            packsDir.mkdirs()
        }
    }

    override fun packDirectory(code: LanguageCode): File {
        return File(packsDir, code.wireCode)
    }

    override fun sharedSttDirectory(): File {
        return File(packsDir, "shared/stt")
    }

    override fun isSharedSttInstalled(): Boolean {
        val spec = ModelFileSpecs.getSttSpec(LanguageCode.HINDI)
        val sttDir = sharedSttDirectory()
        if (!sttDir.exists()) return false

        return spec.requiredFiles.all { fileName ->
            val f = File(sttDir, fileName)
            f.exists() && f.length() > 0L
        }
    }

    override fun isTtsInstalled(code: LanguageCode): Boolean {
        val spec = ModelFileSpecs.getTtsSpec(code) ?: return false
        val ttsDir = File(packDirectory(code), "tts")
        if (!ttsDir.exists()) return false

        return spec.requiredFiles.all { fileName ->
            val f = File(ttsDir, fileName)
            f.exists() && f.length() > 0L
        }
    }

    /**
     * A language pack is genuinely installed only when BOTH its shared STT
     * model and its per-language TTS model are present and non-empty.
     */
    override fun isInstalled(code: LanguageCode): Boolean {
        return isSharedSttInstalled() && isTtsInstalled(code)
    }

    override suspend fun verifyChecksums(
        code: LanguageCode,
        expectedChecksums: Map<String, String>
    ): Boolean {
        val dir = packDirectory(code)
        val ttsDir = File(dir, "tts")
        val sharedDir = sharedSttDirectory()

        for ((filename, expectedSha) in expectedChecksums) {
            val file = when {
                filename.startsWith("shared/stt/") || filename.startsWith("tiny-") -> {
                    val cleanName = filename.removePrefix("shared/stt/")
                    File(sharedDir, cleanName)
                }
                filename.startsWith("tts/") -> {
                    val cleanName = filename.removePrefix("tts/")
                    File(ttsDir, cleanName)
                }
                File(ttsDir, filename).exists() -> File(ttsDir, filename)
                File(sharedDir, filename).exists() -> File(sharedDir, filename)
                else -> File(dir, filename)
            }

            if (!file.exists() || file.length() == 0L) {
                return false
            }

            if (expectedSha.isNotEmpty()) {
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { stream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    return false
                }
            }
        }
        return true
    }

    override suspend fun deletePack(code: LanguageCode) {
        val dir = packDirectory(code)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        // NOTE: Does NOT delete shared STT directory, keeping Whisper available for remaining languages.
    }

    override fun totalInstalledBytes(): Long {
        return packsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Sideload / Import pre-provisioned models from a local directory.
     * Copies shared STT to files/language_packs/shared/stt,
     * TTS to files/language_packs/<lang>/tts,
     * and MT to files/translation_models.
     */
    fun importFromDirectory(sourceDir: File): Boolean {
        return try {
            if (!sourceDir.exists()) return false

            // 1. Shared STT
            val srcStt = File(sourceDir, "shared/stt")
            if (srcStt.exists()) {
                val dstStt = sharedSttDirectory()
                dstStt.mkdirs()
                srcStt.copyRecursively(dstStt, overwrite = true)
            }

            // 2. Language TTS
            for (code in LanguageCode.entries) {
                val srcTts = File(sourceDir, "${code.wireCode}/tts")
                if (srcTts.exists()) {
                    val dstTts = File(packDirectory(code), "tts")
                    dstTts.mkdirs()
                    srcTts.copyRecursively(dstTts, overwrite = true)
                }
            }

            // 3. Translation Models
            val srcMt = File(sourceDir, "mt")
            if (srcMt.exists()) {
                val dstMt = File(filesDir, "translation_models")
                dstMt.mkdirs()
                srcMt.copyRecursively(dstMt, overwrite = true)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
