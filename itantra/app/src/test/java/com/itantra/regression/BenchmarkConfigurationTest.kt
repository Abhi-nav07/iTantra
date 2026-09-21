package com.itantra.regression

import com.itantra.domain.model.NoiseCondition
import com.itantra.domain.model.EvidenceLevel
import org.junit.Assert.*
import org.junit.Test

class BenchmarkConfigurationTest {

    @Test
    fun testBenchmarkCategories() {
        val conditions = NoiseCondition.values()
        assertTrue(conditions.isNotEmpty())
        val levels = EvidenceLevel.values()
        assertTrue(levels.isNotEmpty())
    }
}
