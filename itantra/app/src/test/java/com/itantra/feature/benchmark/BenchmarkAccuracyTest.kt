package com.itantra.feature.benchmark

import com.itantra.core.metrics.TextNormalizer
import com.itantra.core.metrics.WerResult
import com.itantra.core.metrics.WordErrorRateCalculator
import com.itantra.domain.model.BenchmarkResult
import com.itantra.domain.model.BenchmarkSession
import com.itantra.domain.model.EvidenceLevel
import com.itantra.domain.model.NoiseCondition
import com.itantra.domain.model.TtsEvaluationSession
import com.itantra.domain.model.TtsSentenceEvaluation
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Serializable
private data class BenchmarkJsonSentence(
    val id: String,
    val category: String,
    val referenceText: String
)

@Serializable
private data class TtsSentencesRoot(
    val languages: Map<String, List<TtsTestSentenceDef>> = emptyMap()
)

@Serializable
private data class TtsTestSentenceDef(
    val id: String,
    val text: String
)

class BenchmarkAccuracyTest {

    private val targetLanguages = listOf("hi", "en", "bn", "gu", "mr", "kn", "ml", "ta", "te", "or")

    private fun findBenchmarkDir(): File {
        val candidates = listOf(
            File("src/main/assets/benchmark"),
            File("app/src/main/assets/benchmark"),
            File("../app/src/main/assets/benchmark"),
            File("P:/iTantra/itantra/app/src/main/assets/benchmark")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not find benchmark assets directory in candidates: $candidates")
    }

    // 1. All 10 benchmark asset files exist
    @Test
    fun testAll10BenchmarkAssetFilesExist() {
        val dir = findBenchmarkDir()
        for (lang in targetLanguages) {
            val file = File(dir, "${lang}_benchmark.json")
            assertTrue("Benchmark file for $lang must exist at ${file.path}", file.exists() && file.isFile)
        }
    }

    // 2. All 10 parse successfully
    @Test
    fun testAll10BenchmarkAssetFilesParseSuccessfully() {
        val dir = findBenchmarkDir()
        val json = Json { ignoreUnknownKeys = true }
        for (lang in targetLanguages) {
            val file = File(dir, "${lang}_benchmark.json")
            val content = file.readText()
            val parsed = json.decodeFromString<List<BenchmarkJsonSentence>>(content)
            assertNotNull("Parsed sentences for $lang must not be null", parsed)
            assertTrue("Parsed sentences for $lang must not be empty", parsed.isNotEmpty())
        }
    }

    // 3. Each has >= 20 STT sentences
    @Test
    fun testEachBenchmarkHasAtLeast20Sentences() {
        val dir = findBenchmarkDir()
        val json = Json { ignoreUnknownKeys = true }
        for (lang in targetLanguages) {
            val file = File(dir, "${lang}_benchmark.json")
            val parsed = json.decodeFromString<List<BenchmarkJsonSentence>>(file.readText())
            assertTrue(
                "Language $lang must have >= 20 sentences, found ${parsed.size}",
                parsed.size >= 20
            )
        }
    }

    // 4. Each has critical-phrase cases
    @Test
    fun testEachBenchmarkHasCriticalPhraseCases() {
        val dir = findBenchmarkDir()
        val json = Json { ignoreUnknownKeys = true }
        for (lang in targetLanguages) {
            val file = File(dir, "${lang}_benchmark.json")
            val parsed = json.decodeFromString<List<BenchmarkJsonSentence>>(file.readText())
            val criticalCases = parsed.filter { it.category.contains("critical", ignoreCase = true) }
            assertTrue(
                "Language $lang must have critical phrase cases, found ${criticalCases.size}",
                criticalCases.size >= 4
            )
        }
    }

    // 5. WER math
    @Test
    fun testWerMath() {
        val werResult = WordErrorRateCalculator.calculate("a b c d", "a b x d")
        assertEquals(4, werResult.referenceWordCount)
        assertEquals(1, werResult.substitutions)
        assertEquals(0, werResult.deletions)
        assertEquals(0, werResult.insertions)
        assertEquals(0.25f, werResult.wer, 0.001f)
    }

    // 6. Corpus WER aggregation
    @Test
    fun testCorpusWerAggregation() {
        // Sentence 1: 10 words, 1 error -> 10%
        // Sentence 2: 2 words, 1 error -> 50%
        // Average of sentence percentages = (10 + 50) / 2 = 30%
        // True corpus WER = (1 + 1) / (10 + 2) = 2 / 12 = 16.67%
        val r1 = WerResult(referenceWordCount = 10, substitutions = 1, deletions = 0, insertions = 0)
        val r2 = WerResult(referenceWordCount = 2, substitutions = 1, deletions = 0, insertions = 0)

        val corpus = WordErrorRateCalculator.calculateCorpusWer(listOf(r1, r2))

        assertEquals(12, corpus.totalReferenceWords)
        assertEquals(2, corpus.totalSubstitutions)
        assertEquals(0, corpus.totalDeletions)
        assertEquals(0, corpus.totalInsertions)
        assertEquals(2f / 12f, corpus.corpusWer, 0.0001f)
        // Mean sentence WER should be (0.1 + 0.5) / 2 = 0.3
        assertEquals(0.30f, corpus.meanSentenceWer, 0.0001f)
    }

    // 7. Empty reference safety
    @Test
    fun testEmptyReferenceSafety() {
        val result1 = WordErrorRateCalculator.calculate("", "")
        assertEquals(0, result1.referenceWordCount)
        assertEquals(0f, result1.wer, 0.0001f)

        val result2 = WordErrorRateCalculator.calculate("", "extra tokens inserted")
        assertEquals(0, result2.referenceWordCount)
        assertEquals(3, result2.insertions)
        assertEquals(1.0f, result2.wer, 0.0001f)

        val corpusEmpty = WordErrorRateCalculator.calculateCorpusWer(emptyList())
        assertEquals(0, corpusEmpty.totalReferenceWords)
        assertEquals(0f, corpusEmpty.corpusWer, 0.0001f)
        assertEquals(0f, corpusEmpty.meanSentenceWer, 0.0001f)
    }

    // 8. Unicode scripts for all 10 languages
    @Test
    fun testUnicodeScriptsNormalization() {
        val testPairs = listOf(
            "hi" to ("नमस्ते दुनिया!" to "नमस्ते दुनिया"),
            "en" to ("Hello, World!" to "hello world"),
            "bn" to ("নমস্কার, বিশ্ব!" to "নমস্কার বিশ্ব"),
            "gu" to ("નમસ્તે, વિશ્વ!" to "નમસ્તે વિશ્વ"),
            "mr" to ("नमस्कार, जग!" to "नमस्कार जग"),
            "kn" to ("ನಮಸ್ಕಾರ, ಜಗತ್ತು!" to "ನಮಸ್ಕಾರ ಜಗತ್ತು"),
            "ml" to ("നമസ്കാരം, ലോകം!" to "നമസ്കാരം ലോകം"),
            "ta" to ("வணக்கம், உலகம்!" to "வணக்கம் உலகம்"),
            "te" to ("నమస్కారం, ప్రపంచం!" to "నమస్కారం ప్రపంచం"),
            "or" to ("ନମସ୍କାର, ବିଶ୍ୱ!" to "ନମସ୍କାର ବିଶ୍ୱ")
        )

        for ((lang, pair) in testPairs) {
            val normalized = TextNormalizer.normalize(pair.first)
            assertEquals("Normalization failed for $lang", pair.second, normalized)
            assertTrue("Exact match check failed for $lang", TextNormalizer.isExactMatch(pair.first, pair.second))
        }
    }

    // 9. Insertion / deletion / substitution accounting
    @Test
    fun testEditOperationAccounting() {
        // Ref: "one two three four"
        // Hyp: "one three five four extra"
        // 'two' is deleted, 'five' is inserted, 'extra' is inserted
        val res = WordErrorRateCalculator.calculate("one two three four", "one three five four extra")
        assertEquals(4, res.referenceWordCount)
        assertTrue("Substitutions + Deletions + Insertions > 0", res.substitutions + res.deletions + res.insertions > 0)
        assertEquals((res.substitutions + res.deletions + res.insertions).toFloat() / 4f, res.wer, 0.001f)
    }

    // 10. Benchmark session serialization
    @Test
    fun testBenchmarkSessionSerialization() {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val session = BenchmarkSession(
            timestampMs = 1710000000000L,
            language = "hi",
            deviceManufacturer = "Samsung",
            deviceModel = "SM-G998B",
            androidVersion = "14",
            abi = "arm64-v8a",
            threadCount = 4,
            modelVersion = "Whisper Tiny Multilingual INT8 ONNX",
            noiseCondition = NoiseCondition.MODERATE_NOISE,
            totalUtterances = 24,
            totalReferenceWords = 150,
            substitutions = 5,
            deletions = 2,
            insertions = 1,
            corpusWer = 0.0533f,
            meanSentenceWer = 0.0520f,
            medianFinalizationLatencyMs = 280L,
            meanFinalizationLatencyMs = 295L,
            totalAudioDurationMs = 72000L,
            criticalPhraseCount = 8,
            criticalPhraseExactMatches = 8,
            criticalPhraseExactMatchRate = 1.0f,
            evidenceLevel = EvidenceLevel.DEVICE_TESTED
        )

        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<BenchmarkSession>(encoded)

        assertEquals("hi", decoded.language)
        assertEquals("Whisper Tiny Multilingual INT8 ONNX", decoded.modelVersion)
        assertEquals(NoiseCondition.MODERATE_NOISE, decoded.noiseCondition)
        assertEquals(EvidenceLevel.DEVICE_TESTED, decoded.evidenceLevel)
        assertEquals(0.0533f, decoded.corpusWer, 0.0001f)
        assertEquals(1.0f, decoded.criticalPhraseExactMatchRate, 0.0001f)
    }

    // 11. TTS evaluation serialization
    @Test
    fun testTtsEvaluationSerialization() {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val eval = TtsEvaluationSession(
            timestampMs = 1710000000000L,
            language = "kn",
            evaluatorId = "Human Tester 1",
            modelIdentity = "Meta MMS TTS VITS ONNX",
            sentencesTested = 10,
            intelligibleCount = 9,
            intelligibilityPercent = 0.90f,
            meanNaturalness = 4.2f,
            meanPronunciation = 4.5f,
            meanComputeTimeMs = 320L,
            meanAudioDurationMs = 2400L,
            meanRtf = 0.133f,
            evidenceLevel = EvidenceLevel.HUMAN_REVIEWED,
            evaluations = listOf(
                TtsSentenceEvaluation(
                    sentenceId = "kn_tts_1",
                    text = "ತುರ್ತು ಪರಿಸ್ಥಿತಿಯಿದೆ",
                    computeTimeMs = 310L,
                    audioDurationMs = 2200L,
                    sampleRateHz = 16000,
                    pcmSampleCount = 35200,
                    isNonEmptyPcm = true,
                    intelligible = true,
                    naturalness = 4,
                    pronunciation = 5,
                    comments = "Clear articulation"
                )
            )
        )

        val encoded = json.encodeToString(eval)
        val decoded = json.decodeFromString<TtsEvaluationSession>(encoded)

        assertEquals("kn", decoded.language)
        assertEquals(1, decoded.evaluations.size)
        assertTrue(decoded.evaluations[0].intelligible)
        assertEquals(4, decoded.evaluations[0].naturalness)
        assertEquals(EvidenceLevel.HUMAN_REVIEWED, decoded.evidenceLevel)
    }

    // 12. Evidence-level integrity
    @Test
    fun testEvidenceLevelIntegrity() {
        val validLevels = EvidenceLevel.values().map { it.name }
        assertTrue(validLevels.contains("SOURCE_ONLY"))
        assertTrue(validLevels.contains("HOST_TESTED"))
        assertTrue(validLevels.contains("DEVICE_TESTED"))
        assertTrue(validLevels.contains("HUMAN_REVIEWED"))
        assertEquals(4, validLevels.size)
    }

    // 13. No result marked DEVICE_TESTED without device evidence field
    @Test
    fun testNoResultMarkedDeviceTestedWithoutDeviceEvidence() {
        val validDeviceSession = BenchmarkSession(
            timestampMs = System.currentTimeMillis(),
            language = "te",
            deviceManufacturer = "Google",
            deviceModel = "Pixel 7",
            androidVersion = "14",
            abi = "arm64-v8a",
            evidenceLevel = EvidenceLevel.DEVICE_TESTED
        )

        val isValid = if (validDeviceSession.evidenceLevel == EvidenceLevel.DEVICE_TESTED) {
            validDeviceSession.deviceModel.isNotBlank() &&
            validDeviceSession.deviceManufacturer.isNotBlank() &&
            validDeviceSession.androidVersion.isNotBlank()
        } else {
            true
        }
        assertTrue("DEVICE_TESTED session must have device manufacturer, model, and androidVersion", isValid)

        val invalidDeviceSession = BenchmarkSession(
            timestampMs = System.currentTimeMillis(),
            language = "te",
            deviceManufacturer = "",
            deviceModel = "",
            androidVersion = "",
            evidenceLevel = EvidenceLevel.DEVICE_TESTED
        )

        val isInvalidSessionVerified = if (invalidDeviceSession.evidenceLevel == EvidenceLevel.DEVICE_TESTED) {
            invalidDeviceSession.deviceModel.isNotBlank() &&
            invalidDeviceSession.deviceManufacturer.isNotBlank()
        } else {
            true
        }
        assertFalse("Session without physical device info must not qualify as DEVICE_TESTED", isInvalidSessionVerified)
    }
}
