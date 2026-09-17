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
}
