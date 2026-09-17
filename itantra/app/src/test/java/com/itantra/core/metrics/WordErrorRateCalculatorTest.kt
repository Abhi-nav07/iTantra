package com.itantra.core.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class WordErrorRateCalculatorTest {

    @Test
    fun `test perfect match`() {
        val result = WordErrorRateCalculator.calculate("मुख्य सड़क बंद है", "मुख्य सड़क बंद है")
        assertEquals(4, result.referenceWordCount)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(0f, result.wer)
    }

    @Test
    fun `test one deletion`() {
        val result = WordErrorRateCalculator.calculate("मुख्य सड़क बंद है", "मुख्य सड़क बंद")
        assertEquals(4, result.referenceWordCount)
        assertEquals(0, result.substitutions)
        assertEquals(1, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(0.25f, result.wer)
    }

    @Test
    fun `test one insertion`() {
        val result = WordErrorRateCalculator.calculate("मुख्य सड़क बंद है", "मुख्य सड़क बंद है क्या")
        assertEquals(4, result.referenceWordCount)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(1, result.insertions)
        assertEquals(0.25f, result.wer)
    }

    @Test
    fun `test one substitution`() {
        val result = WordErrorRateCalculator.calculate("मुख्य सड़क बंद है", "मुख्य रास्ता बंद है")
        assertEquals(4, result.referenceWordCount)
        assertEquals(1, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(0.25f, result.wer)
    }

    @Test
    fun `test normalization punctuation removal`() {
        // Punctuation should be ignored
        val result = WordErrorRateCalculator.calculate("मुख्य सड़क बंद है।", "मुख्य सड़क बंद है?")
        assertEquals(0f, result.wer)
    }

    @Test
    fun `test code switch lowercase`() {
        val result = WordErrorRateCalculator.calculate("Rescue team भेजो", "rescue team भेजो")
        assertEquals(0f, result.wer)
    }

    @Test
    fun `test completely wrong`() {
        val result = WordErrorRateCalculator.calculate("बाढ़ का पानी", "आग लगी है")
        // बाढ़ (sub) का (sub) पानी (sub)
        // reference: 3 words
        // hypothesis: 3 words
        assertEquals(3, result.referenceWordCount)
        assertEquals(3, result.substitutions)
        assertEquals(1.0f, result.wer)
    }
}
