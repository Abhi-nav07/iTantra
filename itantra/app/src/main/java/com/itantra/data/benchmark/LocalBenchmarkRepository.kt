package com.itantra.data.benchmark

import android.content.Context
import com.itantra.domain.model.BenchmarkSession
import com.itantra.domain.model.TtsEvaluationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LocalBenchmarkRepository(private val context: Context) {

    private val benchmarkDir: File
        get() = File(context.filesDir, "benchmarks").apply { mkdirs() }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun saveSession(session: BenchmarkSession): File = withContext(Dispatchers.IO) {
        val fileName = "itantra_stt_benchmark_${session.language}_${session.timestampMs}.json"
        val file = File(benchmarkDir, fileName)

        val content = json.encodeToString(session)
        file.writeText(content)
        file
    }

    suspend fun getAllSessions(): List<BenchmarkSession> = withContext(Dispatchers.IO) {
        val files = benchmarkDir.listFiles()?.filter {
            it.name.startsWith("itantra_stt_benchmark_") || it.name.startsWith("itantra_hindi_benchmark_")
        } ?: emptyList()

        files.mapNotNull { file ->
            try {
                json.decodeFromString<BenchmarkSession>(file.readText())
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.timestampMs }
    }

    suspend fun saveTtsEvaluation(session: TtsEvaluationSession): File = withContext(Dispatchers.IO) {
        val fileName = "itantra_tts_evaluation_${session.language}_${session.timestampMs}.json"
        val file = File(benchmarkDir, fileName)

        val content = json.encodeToString(session)
        file.writeText(content)
        file
    }

    suspend fun getAllTtsEvaluations(): List<TtsEvaluationSession> = withContext(Dispatchers.IO) {
        val files = benchmarkDir.listFiles()?.filter {
            it.name.startsWith("itantra_tts_evaluation_")
        } ?: emptyList()

        files.mapNotNull { file ->
            try {
                json.decodeFromString<TtsEvaluationSession>(file.readText())
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.timestampMs }
    }
}
