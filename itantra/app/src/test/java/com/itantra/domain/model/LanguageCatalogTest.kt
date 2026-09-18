package com.itantra.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCatalogTest {

    @Test
    fun `catalog contains exactly the 10 required languages`() {
        val expectedCodes = setOf(
            LanguageCode.HINDI,
            LanguageCode.ENGLISH,
            LanguageCode.BENGALI,
            LanguageCode.GUJARATI,
            LanguageCode.MARATHI,
            LanguageCode.KANNADA,
            LanguageCode.MALAYALAM,
            LanguageCode.TAMIL,
            LanguageCode.TELUGU,
            LanguageCode.ODIA,
        )
        val actualCodes = LanguageCatalog.all.map { it.code }.toSet()
        assertEquals(10, LanguageCatalog.all.size)
        assertEquals(expectedCodes, actualCodes)
    }

    @Test
    fun `every language has a non-blank display name and native name`() {
        LanguageCatalog.all.forEach { language ->
            assertTrue(
                "displayName blank for ${language.code}",
                language.displayName.isNotBlank(),
            )
            assertTrue(
                "nativeDisplayName blank for ${language.code}",
                language.nativeDisplayName.isNotBlank(),
            )
        }
    }

    @Test
    fun `byCode returns the matching language`() {
        val hindi = LanguageCatalog.byCode(LanguageCode.HINDI)
        assertEquals(LanguageCode.HINDI, hindi.code)
        assertEquals("Hindi", hindi.displayName)
    }

    @Test
    fun `wire codes match expected ISO-639-1-style values`() {
        assertEquals("hi", LanguageCode.HINDI.wireCode)
        assertEquals("en", LanguageCode.ENGLISH.wireCode)
        assertEquals("or", LanguageCode.ODIA.wireCode)
        assertEquals("kn", LanguageCode.KANNADA.wireCode)

        val hasKt = LanguageCatalog.all.any { it.code.wireCode == "kt" }
        org.junit.Assert.assertFalse("No language should use 'kt' as code", hasKt)
    }

    @Test
    fun `fromWireCode resolves known codes and returns null for unknown`() {
        assertEquals(LanguageCode.TAMIL, LanguageCode.fromWireCode("ta"))
        assertEquals(LanguageCode.TAMIL, LanguageCode.fromWireCode("TA"))
        assertEquals(null, LanguageCode.fromWireCode("xx"))
    }
}
