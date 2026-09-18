package com.itantra.core.translation

import android.util.Log
import com.itantra.domain.model.LanguageCode
import java.io.File

/**
 * Native wrapper around CTranslate2 and SentencePiece via JNI.
 * Implements the TranslationEngine interface and manages bidirectional Indic/English translation.
 */
class CTranslate2TranslationEngine : TranslationEngine {

    private var indicEnHandle: Long = 0
    private var enIndicHandle: Long = 0
    
    override var isLoaded: Boolean = false
        private set

    override val supportedSourceLanguages: Set<LanguageCode> = setOf(
        LanguageCode.HINDI, LanguageCode.ENGLISH, LanguageCode.BENGALI,
        LanguageCode.GUJARATI, LanguageCode.MARATHI, LanguageCode.KANNADA,
        LanguageCode.MALAYALAM, LanguageCode.TAMIL, LanguageCode.TELUGU, LanguageCode.ODIA
    )

    override val supportedTargetLanguages: Set<LanguageCode> = supportedSourceLanguages

    override fun init(modelsDir: File) {
        if (isLoaded) return
        try {
            val indicEnPath = File(modelsDir, "indic-en").absolutePath
            val enIndicPath = File(modelsDir, "en-indic").absolutePath
            
            if (File(indicEnPath).exists()) {
                indicEnHandle = nativeCreateEngine(indicEnPath)
            }
            if (File(enIndicPath).exists()) {
                enIndicHandle = nativeCreateEngine(enIndicPath)
            }
            
            if (indicEnHandle != 0L || enIndicHandle != 0L) {
                isLoaded = true
            }
        } catch (e: Exception) {
            Log.e("CTranslate2", "Failed to init MT models", e)
        }
    }

    override suspend fun translate(
        text: String,
        sourceLang: LanguageCode,
        targetLang: LanguageCode
    ): TranslationResult {
        if (!isLoaded) {
            return TranslationResult(text, "", false, sourceLang, targetLang, error = "ENGINE_NOT_LOADED")
        }

        return try {
            if (sourceLang == LanguageCode.ENGLISH) {
                val translated = translateEnToIndic(text, targetLang)
                checkResult(translated, text, sourceLang, targetLang)
            } else if (targetLang == LanguageCode.ENGLISH) {
                val translated = translateIndicToEn(text, sourceLang)
                checkResult(translated, text, sourceLang, targetLang)
            } else {
                // Indic -> Indic Pivot
                val pivotEnglish = translateIndicToEn(text, sourceLang)
                if (pivotEnglish.isNullOrBlank()) {
                    TranslationResult(text, "", false, sourceLang, targetLang, error = "PIVOT_EN_FAILED")
                } else {
                    val translated = translateEnToIndic(pivotEnglish, targetLang)
                    checkResult(translated, text, sourceLang, targetLang)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TranslationResult(text, "", false, sourceLang, targetLang, error = "INFERENCE_FAILED")
        }
    }
    
    private fun checkResult(translated: String?, text: String, source: LanguageCode, target: LanguageCode): TranslationResult {
        return if (!translated.isNullOrBlank()) {
            TranslationResult(text, translated, true, source, target)
        } else {
            TranslationResult(text, "", false, source, target, error = "TRANSLATION_FAILED")
        }
    }

    private fun translateIndicToEn(text: String, source: LanguageCode): String? {
        if (indicEnHandle == 0L) return null
        val sourceTag = getTag(source)
        val targetTag = getTag(LanguageCode.ENGLISH)
        return nativeTranslate(indicEnHandle, text, sourceTag, targetTag)
    }

    private fun translateEnToIndic(text: String, target: LanguageCode): String? {
        if (enIndicHandle == 0L) return null
        val sourceTag = getTag(LanguageCode.ENGLISH)
        val targetTag = getTag(target)
        return nativeTranslate(enIndicHandle, text, sourceTag, targetTag)
    }

    private fun getTag(lang: LanguageCode): String {
        return when (lang) {
            LanguageCode.HINDI -> "__hin_Deva__"
            LanguageCode.ENGLISH -> "__eng_Latn__"
            LanguageCode.BENGALI -> "__ben_Beng__"
            LanguageCode.GUJARATI -> "__guj_Gujr__"
            LanguageCode.MARATHI -> "__mar_Deva__"
            LanguageCode.KANNADA -> "__kan_Knda__"
            LanguageCode.MALAYALAM -> "__mal_Mlym__"
            LanguageCode.TAMIL -> "__tam_Taml__"
            LanguageCode.TELUGU -> "__tel_Telu__"
            LanguageCode.ODIA -> "__ory_Orya__"
        }
    }

    override fun release() {
        if (indicEnHandle != 0L) {
            nativeDestroyEngine(indicEnHandle)
            indicEnHandle = 0L
        }
        if (enIndicHandle != 0L) {
            nativeDestroyEngine(enIndicHandle)
            enIndicHandle = 0L
        }
        isLoaded = false
    }

    private external fun nativeCreateEngine(modelPath: String): Long
    private external fun nativeTranslate(handle: Long, text: String, sourceTag: String, targetTag: String): String
    private external fun nativeDestroyEngine(handle: Long)

    companion object {
        init {
            try {
                System.loadLibrary("itantra_mt_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("CTranslate2", "Could not load libitantra_mt_jni.so", e)
            }
        }
    }
}
