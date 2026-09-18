package com.itantra.data.languagepack

import android.content.Context
import android.os.StatFs
import com.itantra.core.inference.ModelFileSpecs
import com.itantra.core.storage.LanguagePackStorage
import com.itantra.domain.model.LanguageCatalog
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.LanguagePackAvailability
import com.itantra.domain.model.LanguagePackInstallState
import com.itantra.domain.model.LanguagePackManifest
import com.itantra.domain.model.LanguagePackSummary
import com.itantra.domain.repository.LanguagePackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class RealLanguagePackRepository(
    private val context: Context,
    private val storage: LanguagePackStorage,
    private val manifestParser: LanguagePackManifestParser
) : LanguagePackRepository {

    private val sttStates = MutableStateFlow(buildInitialSttStates())
    private val ttsStates = MutableStateFlow(buildInitialTtsStates())
    private val activeLanguage = MutableStateFlow<LanguageCode?>(
        context.getSharedPreferences("lang_prefs", Context.MODE_PRIVATE).getString("source_lang", null)?.let { LanguageCode.fromWireCode(it) }
    )
    private val targetLanguage = MutableStateFlow<LanguageCode?>(
        context.getSharedPreferences("lang_prefs", Context.MODE_PRIVATE).getString("target_lang", null)?.let { LanguageCode.fromWireCode(it) }
    )
    private val downloadProgress = MutableStateFlow<Map<LanguageCode, Int>>(emptyMap())
    private val downloadJobs = mutableMapOf<LanguageCode, Job>()

    private fun buildInitialSttStates(): Map<LanguageCode, LanguagePackInstallState> {
        return LanguageCatalog.all.associate { lang ->
            val dir = File(storage.packDirectory(lang.code), "stt")
            val spec = ModelFileSpecs.getSttSpec(lang.code)
            if (spec == null) {
                lang.code to LanguagePackInstallState.NOT_INSTALLED
            } else {
                val sttExists = spec.requiredFiles.all { File(dir, it).exists() && File(dir, it).length() > 0 }
                lang.code to if (sttExists) LanguagePackInstallState.INSTALLED else LanguagePackInstallState.NOT_INSTALLED
            }
        }
    }

    private fun buildInitialTtsStates(): Map<LanguageCode, LanguagePackInstallState> {
        return LanguageCatalog.all.associate { lang ->
            val dir = File(storage.packDirectory(lang.code), "tts")
            val spec = ModelFileSpecs.getTtsSpec(lang.code)
            if (spec == null) {
                lang.code to LanguagePackInstallState.NOT_INSTALLED
            } else {
                val ttsExists = spec.requiredFiles.all { File(dir, it).exists() && File(dir, it).length() > 0 }
                lang.code to if (ttsExists) LanguagePackInstallState.INSTALLED else LanguagePackInstallState.NOT_INSTALLED
            }
        }
    }

    override fun observePackSummaries(): Flow<List<LanguagePackSummary>> {
        return combine(sttStates, ttsStates, activeLanguage, downloadProgress) { currentStt, currentTts, active, progressMap ->
            LanguageCatalog.all.map { lang ->
                val sttState = currentStt[lang.code] ?: LanguagePackInstallState.NOT_INSTALLED
                val ttsState = currentTts[lang.code] ?: LanguagePackInstallState.NOT_INSTALLED
                val availability = when {
                    active == lang.code -> LanguagePackAvailability.ACTIVE
                    sttState == LanguagePackInstallState.INSTALLED || ttsState == LanguagePackInstallState.INSTALLED -> LanguagePackAvailability.DOWNLOADED
                    else -> LanguagePackAvailability.AVAILABLE
                }

                var sttSize = 0L
                if (sttState == LanguagePackInstallState.INSTALLED) {
                    val dir = File(storage.packDirectory(lang.code), "stt")
                    ModelFileSpecs.getSttSpec(lang.code)?.requiredFiles?.forEach { f ->
                        val file = File(dir, f)
                        if (file.exists()) sttSize += file.length()
                    }
                }

                var ttsSize = 0L
                if (ttsState == LanguagePackInstallState.INSTALLED) {
                    val dir = File(storage.packDirectory(lang.code), "tts")
                    ModelFileSpecs.getTtsSpec(lang.code)?.requiredFiles?.forEach { f ->
                        val file = File(dir, f)
                        if (file.exists()) ttsSize += file.length()
                    }
                }

                LanguagePackSummary(
                    language = lang,
                    sttInstallState = sttState,
                    ttsInstallState = ttsState,
                    availability = availability,
                    sttSizeBytes = if (sttSize > 0) sttSize else null,
                    ttsSizeBytes = if (ttsSize > 0) ttsSize else null,
                    downloadProgressPercent = progressMap[lang.code]
                )
            }
        }
    }

    override fun observeActiveLanguage(): Flow<LanguageCode?> = activeLanguage

    override fun observeTargetLanguage(): Flow<LanguageCode?> = targetLanguage

    override suspend fun getManifest(code: LanguageCode): LanguagePackManifest? = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("language_packs/${code.wireCode}_dev_manifest.json")
                .bufferedReader().use { it.readText() }
            manifestParser.parseOrNull(json)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun setActiveLanguage(code: LanguageCode): Boolean {
        activeLanguage.value = code
        context.getSharedPreferences("lang_prefs", Context.MODE_PRIVATE).edit().putString("source_lang", code.wireCode).apply()
        return true
    }

    override suspend fun setTargetLanguage(code: LanguageCode): Boolean {
        targetLanguage.value = code
        context.getSharedPreferences("lang_prefs", Context.MODE_PRIVATE).edit().putString("target_lang", code.wireCode).apply()
        return true
    }

    override suspend fun startDownload(code: LanguageCode) {
        val sttSpec = ModelFileSpecs.getSttSpec(code)
        val ttsSpec = ModelFileSpecs.getTtsSpec(code)

        if (sttSpec == null && ttsSpec == null) {
            return
        }

        val manifest = getManifest(code) ?: return

        if (sttSpec != null) updateState(code, LanguagePackInstallState.DOWNLOADING, true)
        if (ttsSpec != null) updateState(code, LanguagePackInstallState.DOWNLOADING, false)
        updateProgress(code, 0)

        val rootDir = storage.packDirectory(code)
        val tmpDir = File(rootDir, ".install_tmp")
        tmpDir.deleteRecursively()
        tmpDir.mkdirs()

        val statFs = StatFs(rootDir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val requiredBytes = (manifest.sttModel.sizeBytes + (manifest.ttsModel?.sizeBytes ?: 0L)) + 50_000_000L

        if (availableBytes < requiredBytes) {
            if (sttSpec != null) updateState(code, LanguagePackInstallState.ERROR, true)
            if (ttsSpec != null) updateState(code, LanguagePackInstallState.ERROR, false)
            updateProgress(code, null)
            return
        }

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (sttSpec != null) {
                    val sttUrlBase = manifest.sttModel.downloadUrl ?: throw Exception("No STT URL")
                    val tmpStt = File(tmpDir, "stt")
                    tmpStt.mkdirs()

                    sttSpec.requiredFiles.forEach { file ->
                        downloadFile("$sttUrlBase/$file", File(tmpStt, file), code, isSecondary = false)
                    }
                }

                if (ttsSpec != null) {
                    val ttsUrlBase = manifest.ttsModel?.downloadUrl ?: throw Exception("No TTS URL")
                    val tmpTts = File(tmpDir, "tts")
                    tmpTts.mkdirs()

                    ttsSpec.requiredFiles.forEach { file ->
                        downloadFile("$ttsUrlBase/$file", File(tmpTts, file), code, isSecondary = false)
                    }
                }

                // Atomic Move
                if (sttSpec != null) {
                    val sttDest = File(rootDir, "stt")
                    sttDest.deleteRecursively()
                    Files.move(File(tmpDir, "stt").toPath(), sttDest.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    updateState(code, LanguagePackInstallState.INSTALLED, true)
                }
                if (ttsSpec != null) {
                    val ttsDest = File(rootDir, "tts")
                    ttsDest.deleteRecursively()
                    Files.move(File(tmpDir, "tts").toPath(), ttsDest.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    updateState(code, LanguagePackInstallState.INSTALLED, false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore, keep valid packs
                if (sttSpec != null) updateState(code, if (File(rootDir, "stt").exists()) LanguagePackInstallState.INSTALLED else LanguagePackInstallState.NOT_INSTALLED, true)
                if (ttsSpec != null) updateState(code, if (File(rootDir, "tts").exists()) LanguagePackInstallState.INSTALLED else LanguagePackInstallState.NOT_INSTALLED, false)
            } catch (e: Exception) {
                e.printStackTrace()
                if (sttSpec != null) updateState(code, LanguagePackInstallState.ERROR, true)
                if (ttsSpec != null) updateState(code, LanguagePackInstallState.ERROR, false)
            } finally {
                tmpDir.deleteRecursively()
                updateProgress(code, null)
                downloadJobs.remove(code)
            }
        }
        downloadJobs[code] = job
    }

    private suspend fun downloadFile(urlStr: String, dest: File, code: LanguageCode, isSecondary: Boolean) = withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 60000

        if (connection.responseCode !in 200..299) {
            throw Exception("HTTP Error ${connection.responseCode} for $urlStr")
        }

        val tempDest = File(dest.absolutePath + ".part")
        val fileLength = connection.contentLength

        connection.inputStream.use { input ->
            FileOutputStream(tempDest).use { output ->
                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    ensureActive()
                    total += count
                    output.write(data, 0, count)
                    if (fileLength > 0 && !isSecondary) {
                        val progress = (total * 100 / fileLength).toInt()
                        updateProgress(code, progress)
                    }
                }
            }
        }
        if (tempDest.length() == 0L) {
            throw Exception("Downloaded file is 0 bytes: $urlStr")
        }
        // Rename part file securely
        Files.move(tempDest.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    override suspend fun cancelDownload(code: LanguageCode) {
        downloadJobs[code]?.cancelAndJoin()
        downloadJobs.remove(code)

        val tmpDir = File(storage.packDirectory(code), ".install_tmp")
        tmpDir.deleteRecursively()

        updateProgress(code, null)
    }

    override suspend fun deleteInstalledPack(code: LanguageCode) {
        storage.deletePack(code)
        updateState(code, LanguagePackInstallState.NOT_INSTALLED, true)
        updateState(code, LanguagePackInstallState.NOT_INSTALLED, false)
        if (activeLanguage.value == code) {
            activeLanguage.value = null
        }
    }

    private fun updateState(code: LanguageCode, state: LanguagePackInstallState, isStt: Boolean) {
        if (isStt) {
            sttStates.update { it.toMutableMap().apply { put(code, state) } }
        } else {
            ttsStates.update { it.toMutableMap().apply { put(code, state) } }
        }
    }

    private fun updateProgress(code: LanguageCode, progress: Int?) {
        downloadProgress.update {
            val newMap = it.toMutableMap()
            if (progress != null) newMap[code] = progress else newMap.remove(code)
            newMap
        }
    }
}
