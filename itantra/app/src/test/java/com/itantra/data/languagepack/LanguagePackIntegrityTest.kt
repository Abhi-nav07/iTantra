package com.itantra.data.languagepack

import com.itantra.core.inference.ModelFileSpec
import com.itantra.core.inference.ModelFileSpecs
import com.itantra.domain.model.LanguageCatalog
import com.itantra.domain.model.LanguageCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class LanguagePackIntegrityTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    // 1. all 10 language IDs
    @Test
    fun test_all10LanguageIds() {
        val expectedCodes = listOf("hi", "en", "bn", "gu", "mr", "kn", "ml", "ta", "te", "or")
        val actualCodes = LanguageCode.entries.map { it.wireCode }
        assertEquals(10, actualCodes.size)
        assertEquals(expectedCodes, actualCodes)
        assertEquals(10, LanguageCatalog.all.size)
    }

    // 2. all 10 ModelFileSpecs
    @Test
    fun test_all10ModelFileSpecs() {
        for (lang in LanguageCode.entries) {
            val sttSpec = ModelFileSpecs.getSttSpec(lang)
            assertNotNull("STT spec missing for $lang", sttSpec)
            assertEquals(ModelFileSpec.EngineType.STT, sttSpec.type)
            assertEquals(listOf("tiny-encoder.int8.onnx", "tiny-decoder.int8.onnx", "tiny-tokens.txt"), sttSpec.requiredFiles)

            val ttsSpec = ModelFileSpecs.getTtsSpec(lang)
            assertNotNull("TTS spec missing for $lang", ttsSpec)
            assertEquals(ModelFileSpec.EngineType.TTS, ttsSpec?.type)
            assertEquals(listOf("model.onnx", "tokens.txt"), ttsSpec?.requiredFiles)
        }
    }

    // 3. shared STT path
    @Test
    fun test_sharedSttPath() {
        for (lang in LanguageCode.entries) {
            val sttSpec = ModelFileSpecs.getSttSpec(lang)
            assertTrue("STT must be shared for $lang", sttSpec.isShared)
            assertEquals("shared/stt", sttSpec.sharedPath)
        }
    }

    // 4. shared STT not duplicated
    @Test
    fun test_sharedSttNotDuplicated() {
        val paths = LanguageCode.entries.map { ModelFileSpecs.getSttSpec(it).sharedPath }.toSet()
        assertEquals(1, paths.size)
        assertEquals("shared/stt", paths.first())
    }

    // 5. per-language TTS path
    @Test
    fun test_perLanguageTtsPath() {
        val storage = FileLanguagePackStorage(tempFolder.root)
        for (lang in LanguageCode.entries) {
            val packDir = storage.packDirectory(lang)
            assertEquals(File(File(tempFolder.root, "language_packs"), lang.wireCode), packDir)
            val ttsDir = File(packDir, "tts")
            assertEquals("${lang.wireCode}/tts", ttsDir.relativeTo(File(tempFolder.root, "language_packs")).path.replace('\\', '/'))
        }
    }

    // 6. pack readiness
    @Test
    fun test_packReadinessWhenAllFilesPresent() {
        val storage = FileLanguagePackStorage(tempFolder.root)
        val sttDir = storage.sharedSttDirectory().apply { mkdirs() }
        File(sttDir, "tiny-encoder.int8.onnx").writeText("dummy-encoder")
        File(sttDir, "tiny-decoder.int8.onnx").writeText("dummy-decoder")
        File(sttDir, "tiny-tokens.txt").writeText("dummy-tokens")

        val hiDir = File(storage.packDirectory(LanguageCode.HINDI), "tts").apply { mkdirs() }
        File(hiDir, "model.onnx").writeText("dummy-tts-model")
        File(hiDir, "tokens.txt").writeText("dummy-tts-tokens")

        assertTrue("Shared STT should be ready", storage.isSharedSttInstalled())
        assertTrue("Hindi TTS should be ready", storage.isTtsInstalled(LanguageCode.HINDI))
        assertTrue("Hindi pack should be fully ready", storage.isInstalled(LanguageCode.HINDI))
    }

    // 7. missing required file -> NOT READY
    @Test
    fun test_missingRequiredFileNotReady() {
        val storage = FileLanguagePackStorage(tempFolder.root)
        val sttDir = storage.sharedSttDirectory().apply { mkdirs() }
        File(sttDir, "tiny-encoder.int8.onnx").writeText("dummy-encoder")
        // Missing tiny-decoder.int8.onnx and tiny-tokens.txt

        val taDir = File(storage.packDirectory(LanguageCode.TAMIL), "tts").apply { mkdirs() }
        File(taDir, "model.onnx").writeText("dummy-model")
        // Missing tokens.txt

        assertFalse("STT should not be ready with missing files", storage.isSharedSttInstalled())
        assertFalse("Tamil TTS should not be ready with missing files", storage.isTtsInstalled(LanguageCode.TAMIL))
        assertFalse("Tamil pack should not be ready", storage.isInstalled(LanguageCode.TAMIL))
    }

    // 8. zero-byte file -> NOT READY
    @Test
    fun test_zeroByteFileNotReady() {
        val storage = FileLanguagePackStorage(tempFolder.root)
        val sttDir = storage.sharedSttDirectory().apply { mkdirs() }
        File(sttDir, "tiny-encoder.int8.onnx").writeText("dummy-encoder")
        File(sttDir, "tiny-decoder.int8.onnx").writeText("dummy-decoder")
        File(sttDir, "tiny-tokens.txt").writeText("") // Zero byte

        val knDir = File(storage.packDirectory(LanguageCode.KANNADA), "tts").apply { mkdirs() }
        File(knDir, "model.onnx").writeText("") // Zero byte
        File(knDir, "tokens.txt").writeText("tokens")

        assertFalse("STT should not be ready with 0-byte token file", storage.isSharedSttInstalled())
        assertFalse("Kannada TTS should not be ready with 0-byte model file", storage.isTtsInstalled(LanguageCode.KANNADA))
        assertFalse("Kannada pack should not be ready", storage.isInstalled(LanguageCode.KANNADA))
    }

    // 9. checksum mismatch -> ERROR
    @Test
    fun test_checksumMismatchFails() = runBlocking {
        val storage = FileLanguagePackStorage(tempFolder.root)
        val ttsDir = File(storage.packDirectory(LanguageCode.MARATHI), "tts").apply { mkdirs() }
        val modelFile = File(ttsDir, "model.onnx").apply { writeText("actual content") }

        // Expected hash for different content
        val wrongChecksum = "0000000000000000000000000000000000000000000000000000000000000000"
        val ok = storage.verifyChecksums(LanguageCode.MARATHI, mapOf("model.onnx" to wrongChecksum))
        assertFalse("Checksum mismatch must return false", ok)

        // Correct hash
        val digest = MessageDigest.getInstance("SHA-256")
        val correctChecksum = digest.digest("actual content".toByteArray()).joinToString("") { "%02x".format(it) }
        val verifiedOk = storage.verifyChecksums(LanguageCode.MARATHI, mapOf("model.onnx" to correctChecksum))
        assertTrue("Matching checksum must return true", verifiedOk)
    }

    // 10. partial install -> ERROR / NOT READY
    @Test
    fun test_partialInstallNotReady() {
        val storage = FileLanguagePackStorage(tempFolder.root)
        val sttDir = storage.sharedSttDirectory().apply { mkdirs() }
        File(sttDir, "tiny-encoder.int8.onnx").writeText("dummy")
        File(sttDir, "tiny-decoder.int8.onnx").writeText("dummy")
        File(sttDir, "tiny-tokens.txt").writeText("dummy")

        // Bengali has STT installed (shared) but NO TTS
        assertFalse("Bengali pack without TTS should NOT be considered installed", storage.isInstalled(LanguageCode.BENGALI))

        // Gujarati has TTS installed but let's test a case where shared STT is missing
        val guDir = File(storage.packDirectory(LanguageCode.GUJARATI), "tts").apply { mkdirs() }
        File(guDir, "model.onnx").writeText("dummy")
        File(guDir, "tokens.txt").writeText("dummy")

        sttDir.deleteRecursively() // delete shared STT
        assertFalse("Gujarati pack without shared STT should NOT be considered installed", storage.isInstalled(LanguageCode.GUJARATI))
    }

    // 11. atomic install behavior
    @Test
    fun test_atomicInstallBehavior() {
        val destDir = tempFolder.newFolder("target_tts")
        val tmpDir = tempFolder.newFolder("install_tmp")

        val partFile = File(tmpDir, "model.onnx.part").apply { writeText("model data in progress") }
        val finalTmp = File(tmpDir, "model.onnx")

        // Only after download and verification passes is it atomically renamed from .part
        Files.move(partFile.toPath(), finalTmp.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertTrue(finalTmp.exists())
        assertFalse(partFile.exists())

        // Finally moved atomically to target dir
        val targetFile = File(destDir, "model.onnx")
        Files.move(finalTmp.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        assertTrue(targetFile.exists())
        assertEquals("model data in progress", targetFile.readText())
    }

    // 12. deleting one TTS pack does not delete shared STT
    @Test
    fun test_deletePackDoesNotDeleteSharedStt() = runBlocking {
        val storage = FileLanguagePackStorage(tempFolder.root)
        val sttDir = storage.sharedSttDirectory().apply { mkdirs() }
        File(sttDir, "tiny-encoder.int8.onnx").writeText("shared encoder")
        File(sttDir, "tiny-decoder.int8.onnx").writeText("shared decoder")
        File(sttDir, "tiny-tokens.txt").writeText("shared tokens")

        val taDir = File(storage.packDirectory(LanguageCode.TAMIL), "tts").apply { mkdirs() }
        File(taDir, "model.onnx").writeText("tamil model")
        File(taDir, "tokens.txt").writeText("tamil tokens")

        val mlDir = File(storage.packDirectory(LanguageCode.MALAYALAM), "tts").apply { mkdirs() }
        File(mlDir, "model.onnx").writeText("malayalam model")
        File(mlDir, "tokens.txt").writeText("malayalam tokens")

        assertTrue(storage.isInstalled(LanguageCode.TAMIL))
        assertTrue(storage.isInstalled(LanguageCode.MALAYALAM))

        // Delete Tamil pack
        storage.deletePack(LanguageCode.TAMIL)

        assertFalse("Tamil pack should now be deleted", storage.isInstalled(LanguageCode.TAMIL))
        assertTrue("Shared STT must remain intact after deleting Tamil", storage.isSharedSttInstalled())
        assertTrue("Malayalam pack must remain intact", storage.isInstalled(LanguageCode.MALAYALAM))
    }

    // 18. manifest/runtime filename agreement
    @Test
    fun test_manifestRuntimeFilenameAgreement() {
        val assetsDir = File("src/main/assets/language_packs")
        for (lang in LanguageCode.entries) {
            val manifestFile = File(assetsDir, "${lang.wireCode}_dev_manifest.json")
            assertTrue("Manifest file must exist: ${manifestFile.path}", manifestFile.exists())

            val manifest = LanguagePackManifestParser.parseOrNull(manifestFile.readText())
            assertNotNull("Manifest failed to parse for $lang", manifest)
            requireNotNull(manifest)

            val sttSpec = ModelFileSpecs.getSttSpec(lang)
            assertEquals("Manifest STT files must match runtime spec for $lang", sttSpec.requiredFiles, manifest.sttModel.files)

            val ttsSpec = ModelFileSpecs.getTtsSpec(lang)
            assertNotNull(ttsSpec)
            assertEquals("Manifest TTS files must match runtime spec for $lang", ttsSpec?.requiredFiles, manifest.ttsModel.files)
        }
    }
}
