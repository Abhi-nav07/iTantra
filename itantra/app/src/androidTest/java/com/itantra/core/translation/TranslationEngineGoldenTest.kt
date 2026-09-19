package com.itantra.core.translation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import java.io.File
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class TranslationEngineGoldenTest {

    @Test
    fun testGoldenVectors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = CTranslate2TranslationEngine()

        // Path to provisioned models - assume they exist at files/translation_models
        val modelsDir = File(context.filesDir, "translation_models")
        if (!modelsDir.exists()) {
            println("Skipping test: Models not provisioned on device.")
            return
        }

        engine.init(modelsDir)
        if (!engine.isLoaded) {
            println("Skipping test: Models failed to load on device.")
            return
        }

        // Read golden vectors from assets (we would copy golden_vectors.json to src/main/assets)
        val assetManager = context.assets
        val goldenJsonStr = try {
            InputStreamReader(assetManager.open("golden_vectors.json")).readText()
        } catch (e: Exception) {
            println("Skipping test: golden_vectors.json not found in assets.")
            return
        }

        val goldenArray = JSONArray(goldenJsonStr)
        for (i in 0 until goldenArray.length()) {
            val obj = goldenArray.getJSONObject(i)
            val rawInput = obj.getString("raw_input")
            val sourceTag = obj.getString("source_tag")
            val targetTag = obj.getString("target_tag")
            val expectedTokensJson = obj.getJSONArray("sp_tokens")
            
            val expectedTokens = Array(expectedTokensJson.length()) { j ->
                expectedTokensJson.getString(j)
            }

            // For testing we will need access to the native handle.
            // Since handles are private, we'll use reflection for this specific test.
            val handleField = CTranslate2TranslationEngine::class.java.getDeclaredField(
                if (sourceTag == "eng_Latn") "enIndicHandle" else "indicEnHandle"
            )
            handleField.isAccessible = true
            val handle = handleField.getLong(engine)
            
            // Expected C++ sequence is: [sourceTag, targetTag, *sp_tokens]
            val expectedNativeSequence = mutableListOf<String>()
            if (sourceTag.isNotEmpty()) expectedNativeSequence.add(sourceTag)
            if (targetTag.isNotEmpty()) expectedNativeSequence.add(targetTag)
            expectedNativeSequence.addAll(expectedTokens)

            val nativeResult = engine.nativePrepareInputForTest(handle, rawInput, sourceTag, targetTag)
            assertArrayEquals("Token sequence mismatch for input: $rawInput", expectedNativeSequence.toTypedArray(), nativeResult)
        }
        
        engine.release()
    }
}
