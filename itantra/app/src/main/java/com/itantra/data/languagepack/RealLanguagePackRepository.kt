package com.itantra.data.languagepack

import android.content.Context
import android.os.StatFs
import com.itantra.core.storage.LanguagePackStorage
import com.itantra.domain.model.Language
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RealLanguagePackRepository(
    private val context: Context,
    private val storage: LanguagePackStorage,
    private val manifestParser: LanguagePackManifestParser
) : LanguagePackRepository {

    private val sttStates = MutableStateFlow(buildInitialSttStates())
    private val ttsStates = MutableStateFlow(buildInitialTtsStates())
    private val activeLanguage = MutableStateFlow<LanguageCode?>(null)
    private val downloadProgress = MutableStateFlow<Map<LanguageCode, Int>>(emptyMap())
    private val downloadJobs = mutableMapOf<LanguageCode, Job>()

    private fun buildInitialSttStates(): Map<LanguageCode, LanguagePackInstallState> {
        return LanguageCatalog.all.associate { lang ->
            val dir = storage.packDirectory(lang.code)
            val sttExists = File(dir, "model.int8.onnx").exists() && File(dir, "tokens.txt").exists()
            lang.code to if (sttExists) LanguagePackInstallState.INSTALLED else LanguagePackInstallState.NOT_INSTALLED
        }
    }
    
    private fun buildInitialTtsStates(): Map<LanguageCode, LanguagePackInstallState> {
        return LanguageCatalog.all.associate { lang ->
            val dir = storage.packDirectory(lang.code)
            val ttsExists = File(dir, "tts_model.onnx").exists() && File(dir, "lexicon.txt").exists() && File(dir, "tts_tokens.txt").exists()
            lang.code to if (ttsExists) LanguagePackInstallState.INSTALLED else LanguagePackInstallState.NOT_INSTALLED
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
                    val dir = storage.packDirectory(lang.code)
                    listOf("model.int8.onnx", "tokens.txt").forEach { f ->
                        val file = File(dir, f)
                        if (file.exists()) sttSize += file.length()
                    }
                }
                
                var ttsSize = 0L
                if (ttsState == LanguagePackInstallState.INSTALLED) {
                    val dir = storage.packDirectory(lang.code)
                    listOf("tts_model.onnx", "lexicon.txt", "tts_tokens.txt").forEach { f ->
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
        if (sttStates.value[code] != LanguagePackInstallState.INSTALLED && ttsStates.value[code] != LanguagePackInstallState.INSTALLED) {
            return false
        }
        activeLanguage.value = code
        return true
    }

    override suspend fun startDownload(code: LanguageCode) {
        val manifest = getManifest(code) ?: return
        
        updateState(code, LanguagePackInstallState.DOWNLOADING, true)
        updateState(code, LanguagePackInstallState.DOWNLOADING, false)
        updateProgress(code, 0)
        
        val dir = storage.packDirectory(code)
        dir.mkdirs()
        
        val statFs = StatFs(dir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val requiredBytes = manifest.sttModel.sizeBytes + 50_000_000L // 50MB safety margin
        if (availableBytes < requiredBytes) {
            updateState(code, LanguagePackInstallState.ERROR, true)
            updateState(code, LanguagePackInstallState.ERROR, false)
            updateProgress(code, null)
            return
        }
        
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val sttUrlBase = manifest.sttModel.downloadUrl ?: throw Exception("No STT URL")
                
                if (manifest.sttModel.files.isNotEmpty()) {
                    manifest.sttModel.files.forEach { file ->
                        val localName = if (file.endsWith("onnx")) "model.int8.onnx" else "tokens.txt"
                        downloadFile("$sttUrlBase/$file", File(dir, localName), code, isSecondary = file != manifest.sttModel.files.first())
                    }
                } else {
                    downloadFile("$sttUrlBase/hi/model.int8.onnx", File(dir, "model.int8.onnx"), code)
                    downloadFile("$sttUrlBase/tokens.txt", File(dir, "tokens.txt"), code, isSecondary = true)
                }
                
                val ttsUrlBase = manifest.ttsModel.downloadUrl
                if (ttsUrlBase != null) {
                    if (manifest.ttsModel.files.isNotEmpty()) {
                        manifest.ttsModel.files.forEach { file ->
                            val localName = when {
                                file.endsWith("onnx") -> "tts_model.onnx"
                                file.endsWith("lexicon.txt") -> "lexicon.txt"
                                else -> "tts_tokens.txt"
                            }
                            downloadFile("$ttsUrlBase/$file", File(dir, localName), code, isSecondary = file != manifest.ttsModel.files.first())
                        }
                    } else {
                        downloadFile("$ttsUrlBase/vits-mms-hin.onnx", File(dir, "tts_model.onnx"), code, isSecondary = true)
                        downloadFile("$ttsUrlBase/lexicon.txt", File(dir, "lexicon.txt"), code, isSecondary = true)
                        downloadFile("$ttsUrlBase/tokens.txt", File(dir, "tts_tokens.txt"), code, isSecondary = true)
                    }
                }
                
                updateState(code, LanguagePackInstallState.INSTALLED, true)
                updateState(code, LanguagePackInstallState.INSTALLED, false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateState(code, LanguagePackInstallState.NOT_INSTALLED, true)
                updateState(code, LanguagePackInstallState.NOT_INSTALLED, false)
            } catch (e: Exception) {
                e.printStackTrace()
                updateState(code, LanguagePackInstallState.ERROR, true)
                updateState(code, LanguagePackInstallState.ERROR, false)
            } finally {
                updateProgress(code, null)
                downloadJobs.remove(code)
            }
        }
        downloadJobs[code] = job
    }

    private suspend fun downloadFile(urlStr: String, dest: File, code: LanguageCode, isSecondary: Boolean = false) = withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        
        val tempDest = File(dest.absolutePath + ".tmp")
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
        tempDest.renameTo(dest)
    }

    override suspend fun cancelDownload(code: LanguageCode) {
        downloadJobs[code]?.cancelAndJoin()
        downloadJobs.remove(code)
        
        val dir = storage.packDirectory(code)
        File(dir, "model.int8.onnx.tmp").delete()
        File(dir, "tokens.txt.tmp").delete()
        File(dir, "tts_model.onnx.tmp").delete()
        File(dir, "lexicon.txt.tmp").delete()
        File(dir, "tts_tokens.txt.tmp").delete()
        
        updateState(code, LanguagePackInstallState.NOT_INSTALLED, true)
        updateState(code, LanguagePackInstallState.NOT_INSTALLED, false)
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
