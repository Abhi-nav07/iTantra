package com.itantra.data.languagepack

import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.LanguagePackAvailability
import com.itantra.domain.model.LanguagePackInstallState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLanguagePackRepositoryTest {

    @Test
    fun `initial state has Hindi active, Hindi and English installed, rest not installed`() = runTest {
        val repo = MockLanguagePackRepository()
        val summaries = repo.observePackSummaries().first()

        assertEquals(10, summaries.size)

        val hindi = summaries.first { it.language.code == LanguageCode.HINDI }
        val english = summaries.first { it.language.code == LanguageCode.ENGLISH }
        val tamil = summaries.first { it.language.code == LanguageCode.TAMIL }

        assertEquals(LanguagePackAvailability.ACTIVE, hindi.availability)
        assertEquals(LanguagePackInstallState.INSTALLED, hindi.sttInstallState)
        assertEquals(LanguagePackInstallState.INSTALLED, hindi.ttsInstallState)

        assertEquals(LanguagePackAvailability.DOWNLOADED, english.availability)
        assertEquals(LanguagePackInstallState.INSTALLED, english.sttInstallState)
        assertEquals(LanguagePackInstallState.INSTALLED, english.ttsInstallState)

        assertEquals(LanguagePackAvailability.AVAILABLE, tamil.availability)
        assertEquals(LanguagePackInstallState.NOT_INSTALLED, tamil.sttInstallState)
        assertEquals(LanguagePackInstallState.NOT_INSTALLED, tamil.ttsInstallState)

        assertEquals(LanguageCode.HINDI, repo.observeActiveLanguage().first())
    }

    @Test
    fun `setActiveLanguage succeeds for a downloaded pack and enforces single active`() = runTest {
        val repo = MockLanguagePackRepository()

        val result = repo.setActiveLanguage(LanguageCode.ENGLISH)

        assertTrue(result)
        assertEquals(LanguageCode.ENGLISH, repo.observeActiveLanguage().first())

        val summaries = repo.observePackSummaries().first()
        val activeCount = summaries.count { it.availability == LanguagePackAvailability.ACTIVE }
        assertEquals(1, activeCount)
        assertEquals(
            LanguagePackAvailability.DOWNLOADED,
            summaries.first { it.language.code == LanguageCode.HINDI }.availability,
        )
    }

    @Test
    fun `setActiveLanguage fails for a pack that is not installed`() = runTest {
        val repo = MockLanguagePackRepository()

        val result = repo.setActiveLanguage(LanguageCode.TAMIL)

        assertFalse(result)
        // Active language must remain unchanged on failure.
        assertEquals(LanguageCode.HINDI, repo.observeActiveLanguage().first())
    }

    @Test(expected = NotImplementedError::class)
    fun `startDownload is not implemented in Task 01`() = runTest {
        MockLanguagePackRepository().startDownload(LanguageCode.TAMIL)
    }
}
