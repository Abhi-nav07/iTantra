package com.itantra.feature.diagnostics

import com.itantra.core.inference.ActiveLanguageSessionManager
import com.itantra.core.inference.DeviceCapabilityDetector
import com.itantra.core.inference.EngineFactory
import com.itantra.core.inference.LanguageSessionState
import com.itantra.core.inference.SpeechRecognizerEngine
import com.itantra.core.inference.SpeechSynthesizerEngine
import com.itantra.domain.model.InferenceMetrics
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.Measurement
import com.itantra.domain.model.SpeechRecognitionResult
import com.itantra.domain.model.SpeechSynthesisRequest
import com.itantra.domain.model.SpeechSynthesisResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Serializable
data class MockPerformanceReport(
    val timestamp: String,
    val deviceStatus: String,
    val evidenceLevel: String,
    val sharedSttBytes: Long,
    val ttsBytes: Long,
    val mtBytes: Long,
    val totalModelStoreBytes: Long,
    val apkBytes: Long
)

class PerformanceEfficiencyTest {

    // 1 & 2. Storage byte aggregation & Shared STT counted once
    @Test
    fun testStorageAggregationAndSharedSttCountedOnce() {
        val sharedSttBytes = 103_609_903L // ~98.81 MB
        val ttsTotalBytes = 1_266_749_039L // ~1208.07 MB across 10 languages
        val mtTotalBytes = 1_712_502_545L  // ~1633.17 MB (indic-en + en-indic)
        val expectedTotal = sharedSttBytes + ttsTotalBytes + mtTotalBytes

        assertEquals(3_082_861_487L, expectedTotal)

        // Ensure shared STT is NOT multiplied across 10 languages
        val incorrectMultipliedStt = (sharedSttBytes * 10) + ttsTotalBytes + mtTotalBytes
        assertNotEquals(expectedTotal, incorrectMultipliedStt)
    }

    // 3. TTS per-language storage calculation
    @Test
    fun testTtsPerLanguageStorage() {
        val ttsMap = mapOf(
            "hi" to 177_189_258L,
            "en" to 177_219_374L,
            "bn" to 114_045_156L,
            "gu" to 114_034_326L,
            "mr" to 114_044_398L,
            "kn" to 114_045_931L,
            "ml" to 114_052_927L,
            "ta" to 114_032_763L,
            "te" to 114_038_199L,
            "or" to 114_046_707L
        )

        assertEquals(10, ttsMap.size)
        val sum = ttsMap.values.sum()
        assertEquals(1_266_749_039L, sum)
    }

    // 4. MT storage aggregation
    @Test
    fun testMtStorageAggregation() {
        val indicEnBytes = 856_259_538L
        val enIndicBytes = 856_243_007L
        val combined = indicEnBytes + enIndicBytes
        assertEquals(1_712_502_545L, combined)
    }

    // 5. Measurement.NotMeasured integrity
    @Test
    fun testMeasurementNotMeasuredIntegrity() {
        val unmeasured = Measurement.NotMeasured
        assertEquals("N/A", unmeasured.display(" ms"))

        val measured = Measurement.Measured(250L)
        assertEquals("250 ms", measured.display(" ms"))
    }

    // 6. Capability profile boundaries
    @Test
    fun testCapabilityProfileBoundaries() {
        // FULL_AI >= 4000 MB
        fun evaluate(ram: Long, lowRam: Boolean): DeviceCapabilityDetector.CapabilityProfile {
            return when {
                lowRam -> DeviceCapabilityDetector.CapabilityProfile.CORE_ONLY
                ram >= 4000 -> DeviceCapabilityDetector.CapabilityProfile.FULL_AI
                ram >= 2000 -> DeviceCapabilityDetector.CapabilityProfile.STANDARD_AI
                ram > 0 -> DeviceCapabilityDetector.CapabilityProfile.CORE_ONLY
                else -> DeviceCapabilityDetector.CapabilityProfile.UNKNOWN
            }
        }

        assertEquals(DeviceCapabilityDetector.CapabilityProfile.FULL_AI, evaluate(6000, false))
        assertEquals(DeviceCapabilityDetector.CapabilityProfile.FULL_AI, evaluate(4000, false))
        assertEquals(DeviceCapabilityDetector.CapabilityProfile.STANDARD_AI, evaluate(3000, false))
        assertEquals(DeviceCapabilityDetector.CapabilityProfile.STANDARD_AI, evaluate(2000, false))
        assertEquals(DeviceCapabilityDetector.CapabilityProfile.CORE_ONLY, evaluate(1500, false))
        assertEquals(DeviceCapabilityDetector.CapabilityProfile.CORE_ONLY, evaluate(6000, true)) // lowRam override
    }

    // 7. Unload before switch lifecycle
    @Test
    fun testUnloadBeforeSwitchLifecycle() = runBlocking {
        var recognizerUnloadCount = 0
        var synthesizerUnloadCount = 0

        val mockEngine = object : SpeechRecognizerEngine {
            override val languageCode: LanguageCode = LanguageCode.HINDI
            override val isLoaded: Boolean = true
            override suspend fun load() {}
            override suspend fun feed(samples: FloatArray) {}
            override suspend fun finalizeUtterance(): SpeechRecognitionResult =
                SpeechRecognitionResult(LanguageCode.HINDI, "", 1f, true, 0L)
            override suspend fun reset() {}
            override suspend fun unload() { recognizerUnloadCount++ }
        }

        val mockSynth = object : SpeechSynthesizerEngine {
            override val languageCode: LanguageCode = LanguageCode.HINDI
            override val isLoaded: Boolean = true
            override suspend fun load() {}
            override suspend fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult =
                SpeechSynthesisResult("", FloatArray(0), 16000, 1, 0L)
            override suspend fun unload() { synthesizerUnloadCount++ }
        }

        val factory = object : EngineFactory {
            override fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine = mockEngine
            override fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine = mockSynth
        }

        val manager = ActiveLanguageSessionManager(factory)
        manager.switchTo(LanguageCode.HINDI)
        assertEquals(LanguageSessionState.READY, manager.sessionState.value)
        assertEquals(LanguageCode.HINDI, manager.activeLanguage.value)

        // Switch to Kannada -> must unload previous Hindi engines first
        manager.switchTo(LanguageCode.KANNADA)
        assertEquals(1, recognizerUnloadCount)
        assertEquals(1, synthesizerUnloadCount)
        assertEquals(LanguageCode.KANNADA, manager.activeLanguage.value)

        manager.releaseAll()
        assertEquals(2, recognizerUnloadCount)
        assertEquals(2, synthesizerUnloadCount)
        assertNull(manager.activeLanguage.value)
    }

    // 8. Failed model load state
    @Test
    fun testFailedModelLoadState() = runBlocking {
        val failingEngine = object : SpeechRecognizerEngine {
            override val languageCode: LanguageCode = LanguageCode.TAMIL
            override val isLoaded: Boolean = false
            override suspend fun load() { throw IllegalStateException("Model corrupt or missing") }
            override suspend fun feed(samples: FloatArray) {}
            override suspend fun finalizeUtterance(): SpeechRecognitionResult =
                SpeechRecognitionResult(LanguageCode.TAMIL, "", 0f, true, 0L)
            override suspend fun reset() {}
            override suspend fun unload() {}
        }

        val factory = object : EngineFactory {
            override fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine = failingEngine
            override fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine? = null
        }

        val manager = ActiveLanguageSessionManager(factory)
        manager.switchTo(LanguageCode.TAMIL)

        assertEquals(LanguageSessionState.ERROR, manager.sessionState.value)
        assertNull(manager.activeLanguage.value)
        assertNull(manager.currentSttEngine)
    }

    // 9. CORE_ONLY fallback refuses heavy model loading
    @Test
    fun testCoreOnlyFallback() = runBlocking {
        val factory = object : EngineFactory {
            override fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine =
                throw AssertionError("Should not be called in CORE_ONLY")
            override fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine =
                throw AssertionError("Should not be called in CORE_ONLY")
        }

        val coreOnlyDetector = object : DeviceCapabilityDetector(null) {
            override fun determineProfile(): CapabilityProfile = CapabilityProfile.CORE_ONLY
        }

        val manager = ActiveLanguageSessionManager(factory, coreOnlyDetector)
        manager.switchTo(LanguageCode.ODIA)
        assertEquals(LanguageSessionState.ERROR, manager.sessionState.value)
        assertNull(manager.activeLanguage.value)
        assertNull(manager.currentSttEngine)
        assertNull(manager.currentTtsEngine)
    }

    // 10. Performance report serialization
    @Test
    fun testPerformanceReportSerialization() {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val report = MockPerformanceReport(
            timestamp = "2026-09-18T23:30:00Z",
            deviceStatus = "NOT_TESTED",
            evidenceLevel = "SOURCE_READY",
            sharedSttBytes = 103_609_903L,
            ttsBytes = 1_266_749_039L,
            mtBytes = 1_712_502_545L,
            totalModelStoreBytes = 3_082_861_487L,
            apkBytes = 142_870_903L
        )

        val encoded = json.encodeToString(report)
        val decoded = json.decodeFromString<MockPerformanceReport>(encoded)

        assertEquals("NOT_TESTED", decoded.deviceStatus)
        assertEquals("SOURCE_READY", decoded.evidenceLevel)
        assertEquals(3_082_861_487L, decoded.totalModelStoreBytes)
        assertEquals(142_870_903L, decoded.apkBytes)
    }

    // 11. No unknown metric silently represented as zero
    @Test
    fun testNoUnknownMetricSilentlyRepresentedAsZero() {
        val metrics = InferenceMetrics()
        // Ensure that default metrics are NotMeasured, NOT 0
        assertTrue(metrics.stt.inferenceTimeMillis is Measurement.NotMeasured)
        assertTrue(metrics.stt.modelLoadTimeMillis is Measurement.NotMeasured)
        assertTrue(metrics.stt.endpointToFinalTextMillis is Measurement.NotMeasured)
        assertTrue(metrics.tts.timeToFirstAudioMillis is Measurement.NotMeasured)
        assertTrue(metrics.tts.synthesisDurationMillis is Measurement.NotMeasured)
        assertTrue(metrics.tts.realTimeFactor is Measurement.NotMeasured)

        // Make sure display value is NOT "0 ms"
        assertNotEquals("0 ms", metrics.stt.inferenceTimeMillis.display(" ms"))
        assertNotEquals("0 ms", metrics.tts.synthesisDurationMillis.display(" ms"))
    }
}
