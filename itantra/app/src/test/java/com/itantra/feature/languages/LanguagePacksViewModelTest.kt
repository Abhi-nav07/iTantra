package com.itantra.feature.languages

import com.itantra.core.inference.ActiveLanguageSessionManager
import com.itantra.core.inference.EngineFactory
import com.itantra.core.inference.SpeechRecognizerEngine
import com.itantra.core.inference.SpeechSynthesizerEngine
import com.itantra.data.languagepack.MockLanguagePackRepository
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.SpeechRecognitionResult
import com.itantra.domain.model.SpeechSynthesisRequest
import com.itantra.domain.model.SpeechSynthesisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguagePacksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeRecognizer(override val languageCode: LanguageCode) : SpeechRecognizerEngine {
        private var loaded = false
        override val isLoaded: Boolean get() = loaded
        override suspend fun load() { loaded = true }
        override suspend fun feed(samples: FloatArray) {}
        override suspend fun reset() {}
        override suspend fun finalizeUtterance(): SpeechRecognitionResult =
            SpeechRecognitionResult(languageCode, "test", 1.0f, true, 0L)
        override suspend fun unload() { loaded = false }
    }

    private class FakeSynthesizer(override val languageCode: LanguageCode) : SpeechSynthesizerEngine {
        private var loaded = false
        override val isLoaded: Boolean get() = loaded
        override suspend fun load() { loaded = true }
        override suspend fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult =
            throw NotImplementedError()
        override suspend fun unload() { loaded = false }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun activateLanguage_switchesSessionManagerWhenInstalled() = runTest(testDispatcher) {
        val repo = MockLanguagePackRepository()
        // Hindi is installed by default in MockLanguagePackRepository
        val engineFactory = object : EngineFactory {
            override fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine =
                FakeRecognizer(language)
            override fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine =
                FakeSynthesizer(language)
        }
        val sessionManager = ActiveLanguageSessionManager(engineFactory)
        val viewModel = LanguagePacksViewModel(repo, sessionManager)

        viewModel.activateLanguage(LanguageCode.HINDI)
        advanceUntilIdle()

        assertEquals(LanguageCode.HINDI, sessionManager.activeLanguage.value)
        assertNotNull(sessionManager.currentSttEngine)
        assertNotNull(sessionManager.currentTtsEngine)
    }

    @Test
    fun activateLanguage_doesNotSwitchSessionManagerWhenNotInstalled() = runTest(testDispatcher) {
        val repo = MockLanguagePackRepository()
        // Tamil is NOT installed by default in MockLanguagePackRepository
        val engineFactory = object : EngineFactory {
            override fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine =
                FakeRecognizer(language)
            override fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine =
                FakeSynthesizer(language)
        }
        val sessionManager = ActiveLanguageSessionManager(engineFactory)
        val viewModel = LanguagePacksViewModel(repo, sessionManager)

        viewModel.activateLanguage(LanguageCode.TAMIL)
        advanceUntilIdle()

        // Tamil was not installed, so setActiveLanguage returns false, sessionManager remains null
        assertNull(sessionManager.activeLanguage.value)
        assertNull(sessionManager.currentSttEngine)
        assertNull(sessionManager.currentTtsEngine)
    }
}
