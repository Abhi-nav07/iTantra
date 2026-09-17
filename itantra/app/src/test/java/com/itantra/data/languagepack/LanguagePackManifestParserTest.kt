package com.itantra.data.languagepack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LanguagePackManifestParserTest {

    // Mirrors app/src/main/assets/language_packs/hi_dev_manifest.json.
    // Kept inline (rather than read from the assets dir, which isn't on
    // the JVM unit test classpath) so this test doesn't require
    // Robolectric or an instrumented test just to validate parsing.
    private val validSampleJson = """
        {
          "schemaVersion": 1,
          "languageCode": "hi",
          "displayName": "Hindi",
          "packVersion": "0.0.1-dev",
          "downloadSizeBytes": 45000000,
          "installedSizeBytes": 62000000,
          "minimumAppVersion": 1,
          "sttModel": {
            "modelId": "example-streaming-stt-hi",
            "format": "onnx",
            "quantization": "int8",
            "checksumsSha256": {},
            "downloadUrl": null,
            "sizeBytes": 38000000
          },
          "ttsModel": {
            "modelId": "example-fastpitch-hifigan-hi",
            "format": "onnx",
            "quantization": "int8",
            "checksumsSha256": {},
            "downloadUrl": null,
            "sizeBytes": 24000000
          },
          "minimumRamBytes": 536870912
        }
    """.trimIndent()

    @Test
    fun `parses the dev sample manifest correctly`() {
        val manifest = LanguagePackManifestParser.parseOrNull(validSampleJson)

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(1, manifest.schemaVersion)
        assertEquals("hi", manifest.languageCode)
        assertEquals("Hindi", manifest.displayName)
        assertEquals("onnx", manifest.sttModel.format)
        assertEquals("int8", manifest.ttsModel.quantization)
        assertNull(manifest.sttModel.downloadUrl)
    }

    @Test
    fun `rejects an unsupported schema version instead of guessing`() {
        val futureSchemaJson = validSampleJson.replace(
            "\"schemaVersion\": 1",
            "\"schemaVersion\": 99",
        )
        assertNull(LanguagePackManifestParser.parseOrNull(futureSchemaJson))
    }

    @Test
    fun `returns null for malformed JSON instead of throwing`() {
        assertNull(LanguagePackManifestParser.parseOrNull("{ not valid json"))
    }
}
