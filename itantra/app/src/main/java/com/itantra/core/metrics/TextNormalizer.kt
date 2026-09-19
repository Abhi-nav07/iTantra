package com.itantra.core.metrics

object TextNormalizer {

    /**
     * Conservatively normalizes multilingual text across Latin and Indic scripts
     * (Devanagari, Bengali, Gujarati, Kannada, Malayalam, Tamil, Telugu, Odia).
     *
     * Rules:
     * - Convert to lowercase.
     * - Remove punctuation (Latin punctuation + Devanagari/Bengali Danda । and Double Danda ॥).
     * - Collapse consecutive whitespace and trim.
     */
    fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[.,?!;:\"'\\-()\\[\\]{}।॥/`~@#$%^&*+=<>_]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokenize(text: String): List<String> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(" ")
    }

    fun isExactMatch(reference: String, hypothesis: String): Boolean {
        return normalize(reference) == normalize(hypothesis)
    }
}
