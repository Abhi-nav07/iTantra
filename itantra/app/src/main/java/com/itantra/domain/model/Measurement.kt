package com.itantra.domain.model

/**
 * Wraps a metric value that may or may not have been measured yet.
 *
 * This exists specifically so the metrics/diagnostics layer can NEVER
 * fabricate a number. Every metric field in [InferenceMetrics] and
 * [TransmissionMetrics] is a [Measurement], not a raw nullable number with
 * an implicit "0 means unknown" convention (which is how fake/placeholder
 * data quietly creeps into dashboards).
 */
sealed class Measurement<out T> {
    data class Measured<T>(val value: T) : Measurement<T>()
    data object NotMeasured : Measurement<Nothing>()

    /** UI-safe rendering: never returns a made-up number. */
    fun display(unit: String = "", format: (T) -> String = { it.toString() }): String =
        when (this) {
            is Measured -> "${format(value)}$unit"
            NotMeasured -> "N/A"
        }

    companion object {
        fun <T> of(value: T?): Measurement<T> =
            if (value == null) NotMeasured else Measured(value)
    }
}
