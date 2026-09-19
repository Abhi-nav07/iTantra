package com.itantra.core.translation

import com.itantra.domain.model.LanguageCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MTDirectionalReadinessTest {

    // 16. same-language MT bypass: must succeed without engine loaded
    @Test
    fun test_sameLanguageBypassDoesNotRequireEngine() = runBlocking {
        val engine = CTranslate2TranslationEngine()
        engine.indicEnReady = false
        engine.enIndicReady = false
        assertFalse(engine.isLoaded)

        val resultHi = engine.translate("नमस्ते", LanguageCode.HINDI, LanguageCode.HINDI)
        assertTrue("Same language Hindi->Hindi must succeed", resultHi.isSuccessful)
        assertEquals("नमस्ते", resultHi.translatedText)

        val resultEn = engine.translate("Hello world", LanguageCode.ENGLISH, LanguageCode.ENGLISH)
        assertTrue("Same language English->English must succeed", resultEn.isSuccessful)
        assertEquals("Hello world", resultEn.translatedText)
    }

    // 13. indic-en only readiness
    @Test
    fun test_indicEnOnlyReadiness() = runBlocking {
        val engine = CTranslate2TranslationEngine()
        engine.indicEnReady = true
        engine.enIndicReady = false

        // isLoaded must be false because both directions are not ready
        assertFalse("isLoaded must require both directions", engine.isLoaded)
        assertTrue("indicEnReady must be true", engine.indicEnReady)
        assertFalse("enIndicReady must be false", engine.enIndicReady)

        // English -> Indic requires enIndicReady, should fail with ENGINE_NOT_LOADED
        val enToHi = engine.translate("Help", LanguageCode.ENGLISH, LanguageCode.HINDI)
        assertFalse("English->Hindi must fail when enIndic is not ready", enToHi.isSuccessful)
        assertEquals("ENGINE_NOT_LOADED", enToHi.error)

        // Indic -> Indic requires both, should fail with ENGINE_NOT_LOADED
        val hiToTa = engine.translate("नमस्ते", LanguageCode.HINDI, LanguageCode.TAMIL)
        assertFalse("Hindi->Tamil must fail when enIndic is not ready", hiToTa.isSuccessful)
        assertEquals("ENGINE_NOT_LOADED", hiToTa.error)
    }

    // 14. en-indic only readiness
    @Test
    fun test_enIndicOnlyReadiness() = runBlocking {
        val engine = CTranslate2TranslationEngine()
        engine.indicEnReady = false
        engine.enIndicReady = true

        assertFalse("isLoaded must require both directions", engine.isLoaded)
        assertFalse("indicEnReady must be false", engine.indicEnReady)
        assertTrue("enIndicReady must be true", engine.enIndicReady)

        // Indic -> English requires indicEnReady, should fail with ENGINE_NOT_LOADED
        val hiToEn = engine.translate("मदद", LanguageCode.HINDI, LanguageCode.ENGLISH)
        assertFalse("Hindi->English must fail when indicEn is not ready", hiToEn.isSuccessful)
        assertEquals("ENGINE_NOT_LOADED", hiToEn.error)

        // Indic -> Indic requires both, should fail with ENGINE_NOT_LOADED
        val bnToMr = engine.translate("সাহায্য", LanguageCode.BENGALI, LanguageCode.MARATHI)
        assertFalse("Bengali->Marathi must fail when indicEn is not ready", bnToMr.isSuccessful)
        assertEquals("ENGINE_NOT_LOADED", bnToMr.error)
    }

    // 15. both MT models readiness
    @Test
    fun test_bothMTModelsReadiness() {
        val engine = CTranslate2TranslationEngine()
        engine.indicEnReady = true
        engine.enIndicReady = true

        assertTrue("isLoaded must be true when both directions are ready", engine.isLoaded)
        assertTrue(engine.indicEnReady)
        assertTrue(engine.enIndicReady)
    }

    // 17. Indic->Indic requires both directions
    @Test
    fun test_indicToIndicRequiresBothDirections() = runBlocking {
        val engine = CTranslate2TranslationEngine()

        // Neither ready
        engine.indicEnReady = false
        engine.enIndicReady = false
        val r0 = engine.translate("पानी", LanguageCode.HINDI, LanguageCode.KANNADA)
        assertFalse(r0.isSuccessful)
        assertEquals("ENGINE_NOT_LOADED", r0.error)

        // Only Indic->En ready
        engine.indicEnReady = true
        engine.enIndicReady = false
        val r1 = engine.translate("पानी", LanguageCode.HINDI, LanguageCode.KANNADA)
        assertFalse(r1.isSuccessful)
        assertEquals("ENGINE_NOT_LOADED", r1.error)

        // Only En->Indic ready
        engine.indicEnReady = false
        engine.enIndicReady = true
        val r2 = engine.translate("पानी", LanguageCode.HINDI, LanguageCode.KANNADA)
        assertFalse(r2.isSuccessful)
        assertEquals("ENGINE_NOT_LOADED", r2.error)
    }
}
