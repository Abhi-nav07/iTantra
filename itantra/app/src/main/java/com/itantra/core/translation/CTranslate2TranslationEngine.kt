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

    var indicEnReady: Boolean = false
        internal set
    var enIndicReady: Boolean = false
        internal set

    /**
     * isLoaded requires BOTH translation directions (indic-en and en-indic) to be loaded.
     * Do not treat partial single-direction loading as complete engine readiness.
     */
    override val isLoaded: Boolean
        get() = indicEnReady && enIndicReady

    override val supportedSourceLanguages: Set<LanguageCode> = setOf(
        LanguageCode.HINDI, LanguageCode.ENGLISH, LanguageCode.BENGALI,
        LanguageCode.GUJARATI, LanguageCode.MARATHI, LanguageCode.KANNADA,
        LanguageCode.MALAYALAM, LanguageCode.TAMIL, LanguageCode.TELUGU, LanguageCode.ODIA
    )

    override val supportedTargetLanguages: Set<LanguageCode> = supportedSourceLanguages

    private fun isDirectionFilesValid(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val requiredFiles = listOf(
            "model.bin",
            "config.json",
            "source_vocabulary.json",
            "target_vocabulary.json"
        )
        val baseFilesOk = requiredFiles.all {
            val f = File(dir, it)
            f.exists() && f.length() > 0L
        }
        if (!baseFilesOk) return false

        val vocabDir = File(dir, "vocab")
        val srcInVocab = File(vocabDir, "model.SRC")
        val tgtInVocab = File(vocabDir, "model.TGT")
        val srcInRoot = File(dir, "model.SRC")
        val tgtInRoot = File(dir, "model.TGT")

        val srcOk = (srcInVocab.exists() && srcInVocab.length() > 0L) || (srcInRoot.exists() && srcInRoot.length() > 0L)
        val tgtOk = (tgtInVocab.exists() && tgtInVocab.length() > 0L) || (tgtInRoot.exists() && tgtInRoot.length() > 0L)

        return srcOk && tgtOk
    }

    override fun init(modelsDir: File) {
        try {
            val indicEnDir = File(modelsDir, "indic-en")
            val enIndicDir = File(modelsDir, "en-indic")

            if (indicEnHandle == 0L && isDirectionFilesValid(indicEnDir)) {
                try {
                    indicEnHandle = nativeCreateEngine(indicEnDir.absolutePath)
                    if (indicEnHandle != 0L) indicEnReady = true
                } catch (e: Throwable) {
                    logError("CTranslate2", "Failed to create indic-en engine", e)
                    indicEnReady = false
                }
            }

            if (enIndicHandle == 0L && isDirectionFilesValid(enIndicDir)) {
                try {
                    enIndicHandle = nativeCreateEngine(enIndicDir.absolutePath)
                    if (enIndicHandle != 0L) enIndicReady = true
                } catch (e: Throwable) {
                    logError("CTranslate2", "Failed to create en-indic engine", e)
                    enIndicReady = false
                }
            }
        } catch (e: Throwable) {
            logError("CTranslate2", "Failed to init MT models", e)
        }
    }

    override suspend fun translate(
        text: String,
        sourceLang: LanguageCode,
        targetLang: LanguageCode
    ): TranslationResult {
        // Same-language bypasses MT entirely
        if (sourceLang == targetLang) {
            return TranslationResult(text, text, true, sourceLang, targetLang)
        }

        return try {
            if (sourceLang == LanguageCode.ENGLISH) {
                // English -> Indic requires enIndicReady
                if (!enIndicReady) return TranslationResult(text, "", false, sourceLang, targetLang, error = "ENGINE_NOT_LOADED")
                val translated = translateEnToIndic(text, targetLang)
                checkResult(translated, text, sourceLang, targetLang)
            } else if (targetLang == LanguageCode.ENGLISH) {
                // Indic -> English requires indicEnReady
                if (!indicEnReady) return TranslationResult(text, "", false, sourceLang, targetLang, error = "ENGINE_NOT_LOADED")
                val translated = translateIndicToEn(text, sourceLang)
                checkResult(translated, text, sourceLang, targetLang)
            } else {
                // Indic -> Indic requires BOTH directions
                if (!indicEnReady || !enIndicReady) {
                    return TranslationResult(text, "", false, sourceLang, targetLang, error = "ENGINE_NOT_LOADED")
                }

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
        val devanagariText = IndicScriptTransliterator.toDevanagari(text, source)
        return nativeTranslate(indicEnHandle, devanagariText, sourceTag, targetTag)
    }

    private fun translateEnToIndic(text: String, target: LanguageCode): String? {
        if (enIndicHandle == 0L) return null
        val sourceTag = getTag(LanguageCode.ENGLISH)
        val targetTag = getTag(target)
        val raw = nativeTranslate(enIndicHandle, text, sourceTag, targetTag) ?: return null
        return IndicScriptTransliterator.fromDevanagari(raw, target)
    }

    private fun getTag(lang: LanguageCode): String {
        return when (lang) {
            LanguageCode.HINDI -> "hin_Deva"
            LanguageCode.ENGLISH -> "eng_Latn"
            LanguageCode.BENGALI -> "ben_Beng"
            LanguageCode.GUJARATI -> "guj_Gujr"
            LanguageCode.MARATHI -> "mar_Deva"
            LanguageCode.KANNADA -> "kan_Knda"
            LanguageCode.MALAYALAM -> "mal_Mlym"
            LanguageCode.TAMIL -> "tam_Taml"
            LanguageCode.TELUGU -> "tel_Telu"
            LanguageCode.ODIA -> "ory_Orya"
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
        indicEnReady = false
        enIndicReady = false
    }

    private external fun nativeCreateEngine(modelPath: String): Long
    private external fun nativeTranslate(handle: Long, text: String, sourceTag: String, targetTag: String): String
    private external fun nativeDestroyEngine(handle: Long)

    // For Golden Vector testing
    external fun nativePrepareInputForTest(handle: Long, text: String, sourceTag: String, targetTag: String): Array<String>

    companion object {
        init {
            try {
                System.loadLibrary("itantra_mt_jni")
            } catch (e: Throwable) {
                logError("CTranslate2", "Could not load libitantra_mt_jni.so", e)
            }
        }

        private fun logError(tag: String, msg: String, tr: Throwable? = null) {
            try {
                android.util.Log.e(tag, msg, tr)
            } catch (_: Throwable) {
                System.err.println("[$tag] $msg: ${tr?.message ?: ""}")
            }
        }
    }
}
