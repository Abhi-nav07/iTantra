package com.itantra.core.metrics

object TextNormalizer {

    /**
     * Conservatively normalizes Hindi/Devanagari text for WER calculation.
     * 
     * Allowed normalizations:
     * - Convert to lowercase (mostly relevant for code-switched English words).
     * - Remove standard punctuation (., ?, !, ;, :, ', ", -, (, ), [, ], {, }, /)
     * - Remove Devanagari Danda (।) and Double Danda (॥)
     * - Trim whitespace and collapse multiple spaces into one.
     * 
     * We DO NOT normalize matras, spelling, or numbers to words, to ensure 
     * an honest measurement of the STT output accuracy.
     */
    fun normalize(text: String): String {
        return text
            .lowercase()
            // Remove standard and Devanagari punctuation
            .replace(Regex("[.,?!;:\"'\\-()\\[\\]{}।॥/]"), "")
            // Collapse multiple spaces to single space and trim
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Splits normalized text into a list of word tokens.
     */
    fun tokenize(text: String): List<String> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(" ")
    }
}
