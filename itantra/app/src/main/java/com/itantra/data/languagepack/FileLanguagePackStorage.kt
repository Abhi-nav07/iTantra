package com.itantra.data.languagepack

import android.content.Context
import com.itantra.core.storage.LanguagePackStorage
import com.itantra.domain.model.LanguageCode
import java.io.File
import java.security.MessageDigest

class FileLanguagePackStorage(
    private val context: Context
) : LanguagePackStorage {

    private val packsDir = File(context.filesDir, "language_packs")

    init {
        if (!packsDir.exists()) {
            packsDir.mkdirs()
        }
    }

    override fun packDirectory(code: LanguageCode): File {
        return File(packsDir, code.wireCode)
    }

    override fun isInstalled(code: LanguageCode): Boolean {
        val dir = packDirectory(code)
        // Basic check: if directory exists and has files, consider it installed
        // In a production app, we'd check for specific manifest/model files here.
        return dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
    }

    override suspend fun verifyChecksums(
        code: LanguageCode,
        expectedChecksums: Map<String, String>
    ): Boolean {
        val dir = packDirectory(code)
        if (!dir.exists()) return false

        for ((filename, expectedSha) in expectedChecksums) {
            val file = File(dir, filename)
            if (!file.exists()) return false
            // Checksum validation omitted for speed in task 02 if not provided
            if (expectedSha.isNotEmpty()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val stream = file.inputStream()
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
                stream.close()
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (actualSha != expectedSha) return false
            }
        }
        return true
    }

    override suspend fun deletePack(code: LanguageCode) {
        packDirectory(code).deleteRecursively()
    }

    override fun totalInstalledBytes(): Long {
        return packsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
