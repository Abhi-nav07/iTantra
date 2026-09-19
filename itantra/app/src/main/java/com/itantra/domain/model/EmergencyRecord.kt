package com.itantra.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class EmergencyRetryStatus {
    PENDING,
    SENT,
    TRANSPORT_ACKED,
    HUMAN_ACKED,
    FAILED
}

@Serializable
data class EmergencyRecord(
    val messageId: Long,
    val emergencyCode: String,
    val source: String,
    val target: String? = null,
    val createdAt: Long,
    val ackStatus: Boolean = false,
    val humanAckStatus: Boolean = false,
    val retryStatus: EmergencyRetryStatus = EmergencyRetryStatus.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val lastAttempt: Long = 0L,
    val resolvedPhrase: String = ""
) {
    val isUnresolved: Boolean
        get() = !humanAckStatus && retryStatus != EmergencyRetryStatus.HUMAN_ACKED

    val canRetry: Boolean
        get() = isUnresolved && retryCount < maxRetries && retryStatus != EmergencyRetryStatus.FAILED
}
