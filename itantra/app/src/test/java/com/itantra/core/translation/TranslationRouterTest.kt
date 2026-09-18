package com.itantra.core.translation

import com.itantra.domain.model.LanguageCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TranslationRouterTest {

    class MockTranslationEngine : TranslationEngine {
        var invocationCount = 0
            private set

        override var isLoaded: Boolean = true
            private set

        override val supportedSourceLanguages = setOf(LanguageCode.HINDI, LanguageCode.ENGLISH)
        override val supportedTargetLanguages = setOf(LanguageCode.HINDI, LanguageCode.ENGLISH)

        override fun init(modelsDir: File) {
            isLoaded = true
        }

        override fun release() {
            isLoaded = false
        }

        override suspend fun translate(
            text: String,
            sourceLang: LanguageCode,
            targetLang: LanguageCode
        ): TranslationResult {
            invocationCount++
            return TranslationResult(
                originalText = text,
                translatedText = "[TRANSLATED] $text",
                isSuccessful = true,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang
            )
        }
    }

    @Test
    fun `same language bypass does not invoke engine`() = runBlocking {
        val engine = MockTranslationEngine()
        val router = TranslationRouter(engine)

        val result = router.routeAndTranslate("test text", LanguageCode.HINDI, LanguageCode.HINDI)

        assertEquals("test text", result.translatedText)
        assertEquals(true, result.isSuccessful)
        assertEquals(0, engine.invocationCount)
    }

    @Test
    fun `cross language invokes engine`() = runBlocking {
        val engine = MockTranslationEngine()
        val router = TranslationRouter(engine)

        val result = router.routeAndTranslate("test text", LanguageCode.HINDI, LanguageCode.ENGLISH)

        assertEquals("[TRANSLATED] test text", result.translatedText)
        assertEquals(true, result.isSuccessful)
        assertEquals(1, engine.invocationCount)
    }

    @Test
    fun `unsupported language bypasses engine`() = runBlocking {
        val engine = MockTranslationEngine()
        val router = TranslationRouter(engine)

        // Tamil is not in the mock's supported languages
        val result = router.routeAndTranslate("test text", LanguageCode.HINDI, LanguageCode.TAMIL)

        assertEquals(false, result.isSuccessful)
        assertEquals("UNSUPPORTED_ROUTE", result.error)
        assertEquals(0, engine.invocationCount)
    }
}
