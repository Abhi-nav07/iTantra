package com.itantra.core.inference

import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.SpeechRecognitionResult
import com.itantra.domain.model.SpeechSynthesisRequest
import com.itantra.domain.model.SpeechSynthesisResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the project's core model-lifecycle rule using fake engines:
 * exactly one language's engines may be loaded at a time, and switching
 * always fully unloads the previous language before loading the next.
 */
class ActiveLanguageSessionManagerTest {

    private class EventLog {
        val events = mutableListOf<String>()
    }

    private class FakeRecognizer(
        override val languageCode: LanguageCode,
        private val log: EventLog,
    ) : SpeechRecognizerEngine {
        private var loaded = false
        override val isLoaded: Boolean get() = loaded
        override suspend fun load() {
            loaded = true
            log.events += "load-stt-${languageCode.wireCode}"
        }
        override suspend fun feed(samples: FloatArray) {}
        override suspend fun reset() {}
        override suspend fun finalizeUtterance(): SpeechRecognitionResult {
            return SpeechRecognitionResult(languageCode, "Test", 0.9f, true, System.currentTimeMillis())
        }
        override suspend fun unload() {
            loaded = false
            log.events += "unload-stt-${languageCode.wireCode}"
        }
    }

    private class FakeSynthesizer(
        override val languageCode: LanguageCode,
        private val log: EventLog,
    ) : SpeechSynthesizerEngine {
        private var loaded = false
        override val isLoaded: Boolean get() = loaded
        override suspend fun load() {
            loaded = true
            log.events += "load-tts-${languageCode.wireCode}"
        }
        override suspend fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult =
            throw NotImplementedError("not needed for this test")
        override suspend fun unload() {
            loaded = false
            log.events += "unload-tts-${languageCode.wireCode}"
        }
    }

    private fun fakeFactory(log: EventLog) = object : EngineFactory {
        override fun createRecognizer(language: LanguageCode) = FakeRecognizer(language, log)
        override fun createSynthesizer(language: LanguageCode) = FakeSynthesizer(language, log)
    }

    @Test
    fun `switching to a new language loads its engines and sets it active`() = runTest {
        val log = EventLog()
        val manager = ActiveLanguageSessionManager(fakeFactory(log))

        manager.switchTo(LanguageCode.HINDI)

        assertEquals(LanguageCode.HINDI, manager.activeLanguage.value)
        assertEquals(listOf("load-stt-hi", "load-tts-hi"), log.events)
    }

    @Test
    fun `switching languages unloads the previous language before loading the next`() = runTest {
        val log = EventLog()
        val manager = ActiveLanguageSessionManager(fakeFactory(log))

        manager.switchTo(LanguageCode.HINDI)
        log.events.clear()

        manager.switchTo(LanguageCode.TAMIL)

        assertEquals(LanguageCode.TAMIL, manager.activeLanguage.value)
        // Both Hindi engines must be unloaded before either Tamil engine loads.
        val unloadHindiIndex = log.events.indexOf("unload-stt-hi")
        val unloadHindiTtsIndex = log.events.indexOf("unload-tts-hi")
        val loadTamilSttIndex = log.events.indexOf("load-stt-ta")
        val loadTamilTtsIndex = log.events.indexOf("load-tts-ta")

        assertTrue(unloadHindiIndex < loadTamilSttIndex)
        assertTrue(unloadHindiTtsIndex < loadTamilTtsIndex)
    }

    @Test
    fun `switching to the already-active language is a no-op`() = runTest {
        val log = EventLog()
        val manager = ActiveLanguageSessionManager(fakeFactory(log))

        manager.switchTo(LanguageCode.HINDI)
        log.events.clear()
        manager.switchTo(LanguageCode.HINDI)

        assertTrue("expected no engine events, got ${log.events}", log.events.isEmpty())
    }

    @Test
    fun `releaseAll unloads engines and clears active language`() = runTest {
        val log = EventLog()
        val manager = ActiveLanguageSessionManager(fakeFactory(log))

        manager.switchTo(LanguageCode.ENGLISH)
        manager.releaseAll()

        assertEquals(null, manager.activeLanguage.value)
        assertTrue(log.events.contains("unload-stt-en"))
        assertTrue(log.events.contains("unload-tts-en"))
    }

    @Test
    fun `default NoOp factory throws rather than pretending to load a real engine`() = runTest {
        val manager = ActiveLanguageSessionManager()
        var threw = false
        try {
            manager.switchTo(LanguageCode.HINDI)
        } catch (e: NotImplementedError) {
            threw = true
        }
        assertTrue(threw)
        assertFalse(manager.activeLanguage.value == LanguageCode.HINDI)
    }

    @Test
    fun `STT load failure propagates exception, cleans up and sets ERROR state`() = runTest {
        val log = EventLog()
        val factory = object : EngineFactory {
            override fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine {
                return object : SpeechRecognizerEngine {
                    override val languageCode: LanguageCode = language
                    override val isLoaded: Boolean = false
                    override suspend fun load() { throw IllegalStateException("STT model file missing") }
                    override suspend fun feed(samples: FloatArray) {}
                    override suspend fun reset() {}
                    override suspend fun finalizeUtterance(): SpeechRecognitionResult = throw NotImplementedError()
                    override suspend fun unload() { log.events += "unload-stt" }
                }
            }
            override fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine? = null
        }
        val manager = ActiveLanguageSessionManager(factory)
        var threw = false
        try {
            manager.switchTo(LanguageCode.HINDI)
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("Expected exception to propagate", threw)
        assertEquals(LanguageSessionState.ERROR, manager.sessionState.value)
        assertEquals(null, manager.activeLanguage.value)
        assertEquals(null, manager.currentSttEngine)
        assertEquals(null, manager.currentTtsEngine)
    }

    @Test
    fun `TTS load failure cleanly unloads loaded STT, leaves no leaked engine, and propagates exception`() = runTest {
        val log = EventLog()
        val factory = object : EngineFactory {
            override fun createRecognizer(language: LanguageCode) = FakeRecognizer(language, log)
            override fun createSynthesizer(language: LanguageCode) = object : SpeechSynthesizerEngine {
                override val languageCode: LanguageCode = language
                override val isLoaded: Boolean = false
                override suspend fun load() { throw RuntimeException("TTS out of memory") }
                override suspend fun synthesize(request: SpeechSynthesisRequest) = throw NotImplementedError()
                override suspend fun unload() { log.events += "unload-tts-fail" }
            }
        }
        val manager = ActiveLanguageSessionManager(factory)
        var threw = false
        try {
            manager.switchTo(LanguageCode.HINDI)
        } catch (e: RuntimeException) {
            threw = true
        }
        assertTrue("Expected exception to propagate", threw)
        assertEquals(LanguageSessionState.ERROR, manager.sessionState.value)
        assertEquals(null, manager.activeLanguage.value)
        assertEquals(null, manager.currentSttEngine)
        assertEquals(null, manager.currentTtsEngine)
        assertTrue("STT should have been loaded", log.events.contains("load-stt-hi"))
        assertTrue("STT should have been cleaned up on failure", log.events.contains("unload-stt-hi"))
    }

    @Test
    fun `manager with CORE_ONLY capability detector refuses heavy model loading and sets ERROR`() = runTest {
        val log = EventLog()
        val detector = object : DeviceCapabilityDetector(null) {
            override fun determineProfile(): CapabilityProfile = CapabilityProfile.CORE_ONLY
        }
        val manager = ActiveLanguageSessionManager(fakeFactory(log), detector)
        manager.switchTo(LanguageCode.HINDI)
        assertEquals(LanguageSessionState.ERROR, manager.sessionState.value)
        assertEquals(null, manager.activeLanguage.value)
        assertEquals(null, manager.currentSttEngine)
        assertEquals(null, manager.currentTtsEngine)
        assertTrue("No load events should occur on CORE_ONLY", log.events.isEmpty())
    }

    @Test
    fun `manager with FULL_AI or STANDARD_AI capability detector allows normal model loading`() = runTest {
        val log = EventLog()
        val detector = object : DeviceCapabilityDetector(null) {
            override fun determineProfile(): CapabilityProfile = CapabilityProfile.FULL_AI
        }
        val manager = ActiveLanguageSessionManager(fakeFactory(log), detector)
        manager.switchTo(LanguageCode.HINDI)
        assertEquals(LanguageSessionState.READY, manager.sessionState.value)
        assertEquals(LanguageCode.HINDI, manager.activeLanguage.value)
        assertTrue(log.events.contains("load-stt-hi"))
        assertTrue(log.events.contains("load-tts-hi"))
    }
}
