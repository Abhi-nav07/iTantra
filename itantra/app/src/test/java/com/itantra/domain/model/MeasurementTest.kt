package com.itantra.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementTest {

    @Test
    fun `NotMeasured always displays N slash A regardless of unit`() {
        val m: Measurement<Long> = Measurement.NotMeasured
        assertEquals("N/A", m.display(" ms"))
        assertEquals("N/A", m.display(""))
    }

    @Test
    fun `Measured displays value with unit suffix`() {
        val m: Measurement<Long> = Measurement.Measured(42L)
        assertEquals("42 ms", m.display(" ms"))
    }

    @Test
    fun `of() maps null to NotMeasured and non-null to Measured`() {
        assertEquals(Measurement.NotMeasured, Measurement.of<Long>(null))
        assertEquals(Measurement.Measured(7L), Measurement.of(7L))
    }
}
