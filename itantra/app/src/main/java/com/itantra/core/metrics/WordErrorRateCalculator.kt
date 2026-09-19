package com.itantra.core.metrics

import kotlin.math.min

data class WerResult(
    val referenceWordCount: Int,
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
) {
    val wer: Float
        get() = if (referenceWordCount == 0) {
            if (insertions > 0) 1.0f else 0f
        } else {
            (substitutions + deletions + insertions).toFloat() / referenceWordCount
        }
}

data class CorpusWerResult(
    val totalReferenceWords: Int,
    val totalSubstitutions: Int,
    val totalDeletions: Int,
    val totalInsertions: Int,
    val corpusWer: Float,
    val meanSentenceWer: Float
)

object WordErrorRateCalculator {

    /**
     * Calculates the Word Error Rate (WER) using the Levenshtein distance algorithm
     * at the word level.
     */
    fun calculate(reference: String, hypothesis: String): WerResult {
        val refWords = TextNormalizer.tokenize(reference)
        val hypWords = TextNormalizer.tokenize(hypothesis)

        val n = refWords.size
        val m = hypWords.size

        if (n == 0) {
            return WerResult(0, 0, 0, m)
        }

        // dp[i][j] stores the min operations to convert refWords[0..i-1] to hypWords[0..j-1]
        // State format: Pair(Cost, Triple(Substitutions, Deletions, Insertions))
        val dp = Array(n + 1) { Array(m + 1) { IntArray(4) } }

        for (i in 0..n) {
            for (j in 0..m) {
                if (i == 0) {
                    dp[0][j] = intArrayOf(j, 0, 0, j) // Cost=j, Ins=j
                } else if (j == 0) {
                    dp[i][0] = intArrayOf(i, 0, i, 0) // Cost=i, Del=i
                } else {
                    val cost = if (refWords[i - 1] == hypWords[j - 1]) 0 else 1

                    val sub = dp[i - 1][j - 1][0] + cost
                    val del = dp[i - 1][j][0] + 1
                    val ins = dp[i][j - 1][0] + 1

                    val minCost = min(sub, min(del, ins))

                    if (minCost == sub) {
                        dp[i][j] = intArrayOf(
                            minCost,
                            dp[i - 1][j - 1][1] + cost, // sub
                            dp[i - 1][j - 1][2],        // del
                            dp[i - 1][j - 1][3]         // ins
                        )
                    } else if (minCost == del) {
                        dp[i][j] = intArrayOf(
                            minCost,
                            dp[i - 1][j][1],
                            dp[i - 1][j][2] + 1,
                            dp[i - 1][j][3]
                        )
                    } else {
                        dp[i][j] = intArrayOf(
                            minCost,
                            dp[i][j - 1][1],
                            dp[i][j - 1][2],
                            dp[i][j - 1][3] + 1
                        )
                    }
                }
            }
        }

        val finalState = dp[n][m]
        return WerResult(
            referenceWordCount = n,
            substitutions = finalState[1],
            deletions = finalState[2],
            insertions = finalState[3]
        )
    }

    /**
     * Calculates corpus-level WER by aggregating total substitutions, deletions, and insertions
     * divided by total reference words across all utterances.
     * Formula: (total S + total D + total I) / total reference words
     */
    fun calculateCorpusWer(results: List<WerResult>): CorpusWerResult {
        val totalRef = results.sumOf { it.referenceWordCount }
        val totalSub = results.sumOf { it.substitutions }
        val totalDel = results.sumOf { it.deletions }
        val totalIns = results.sumOf { it.insertions }
        val corpusWer = if (totalRef == 0) {
            if (totalIns > 0) 1.0f else 0.0f
        } else {
            (totalSub + totalDel + totalIns).toFloat() / totalRef
        }
        val meanSentenceWer = if (results.isEmpty()) {
            0.0f
        } else {
            results.map { it.wer }.average().toFloat()
        }
        return CorpusWerResult(
            totalReferenceWords = totalRef,
            totalSubstitutions = totalSub,
            totalDeletions = totalDel,
            totalInsertions = totalIns,
            corpusWer = corpusWer,
            meanSentenceWer = meanSentenceWer
        )
    }
}
