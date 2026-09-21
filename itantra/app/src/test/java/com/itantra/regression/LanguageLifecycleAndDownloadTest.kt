package com.itantra.regression

import com.itantra.domain.model.LanguageCatalog
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.LanguagePackAvailability
import com.itantra.domain.model.LanguagePackInstallState
import com.itantra.domain.model.LanguagePackSummary
import org.junit.Assert.*
import org.junit.Test

class LanguageLifecycleAndDownloadTest {

    @Test
    fun testLanguageCatalogIntegrity() {
        val all = LanguageCatalog.all
        assertEquals("Must support exactly 10 SIH languages", 10, all.size)

        val codes = all.map { it.code }.toSet()
        assertTrue(codes.contains(LanguageCode.HINDI))
        assertTrue(codes.contains(LanguageCode.ENGLISH))
        assertTrue(codes.contains(LanguageCode.TAMIL))
        assertTrue(codes.contains(LanguageCode.TELUGU))
        assertTrue(codes.contains(LanguageCode.BENGALI))
        assertTrue(codes.contains(LanguageCode.MARATHI))
        assertTrue(codes.contains(LanguageCode.GUJARATI))
        assertTrue(codes.contains(LanguageCode.KANNADA))
        assertTrue(codes.contains(LanguageCode.MALAYALAM))
        assertTrue(codes.contains(LanguageCode.ODIA))
    }

    @Test
    fun testLanguagePackSummaryProperties() {
        val hindi = LanguageCatalog.byCode(LanguageCode.HINDI)
        val summary = LanguagePackSummary(
            language = hindi,
            sttInstallState = LanguagePackInstallState.INSTALLED,
            ttsInstallState = LanguagePackInstallState.INSTALLED,
            availability = LanguagePackAvailability.DOWNLOADED
        )
        assertTrue(summary.isDownloaded)
    }
}
