package com.itantra.domain.model

/**
 * Measurable performance of one transport hop (sender's compact text
 * packet -> receiver). Populated by a real [com.itantra.core.transport.TransportEngine]
 * implementation, which does not exist yet in Task 01.
 */
data class TransmissionMetrics(
    val payloadBytes: Measurement<Int> = Measurement.NotMeasured,
    val semanticPayloadBytes: Measurement<Int> = Measurement.NotMeasured,
    val secureBytes: Measurement<Int> = Measurement.NotMeasured,
    val finalFrameBytes: Measurement<Int> = Measurement.NotMeasured,
    val packetBytes: Measurement<Int> = Measurement.NotMeasured,
    val transmissionLatencyMillis: Measurement<Long> = Measurement.NotMeasured,
)
