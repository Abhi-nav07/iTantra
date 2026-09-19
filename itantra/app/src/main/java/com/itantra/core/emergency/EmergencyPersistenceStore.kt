package com.itantra.core.emergency

import com.itantra.domain.model.EmergencyRecord
import com.itantra.domain.model.EmergencyRetryStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Durable persistence store for emergency SOS messages.
 * Ensures unresolved emergency state survives activity recreation, process restart,
 * and temporary transport disconnects.
 *
 * Strict protocol rule: Transport ACK only confirms wire reception.
 * Only HUMAN_ACK or explicit operator action clears the unresolved emergency state.
 */
class EmergencyPersistenceStore(
    private val storageDir: File
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storeFile = File(storageDir, "emergency_records.json")
    private val records = mutableMapOf<Long, EmergencyRecord>()
    private val lock = Any()

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        loadFromDisk()
    }

    fun saveRecord(record: EmergencyRecord) {
        synchronized(lock) {
            records[record.messageId] = record
            flushToDisk()
        }
    }

    fun getRecord(messageId: Long): EmergencyRecord? {
        synchronized(lock) {
            return records[messageId]
        }
    }

    fun getAllRecords(): List<EmergencyRecord> {
        synchronized(lock) {
            return records.values.toList()
        }
    }

    fun getUnresolvedRecords(): List<EmergencyRecord> {
        synchronized(lock) {
            return records.values.filter { it.isUnresolved }
        }
    }

    /**
     * Records machine/transport wire ACK.
     * Crucially: ackStatus is set to true and retryStatus becomes TRANSPORT_ACKED,
     * but humanAckStatus remains false, so the record remains UNRESOLVED.
     */
    fun recordTransportAck(messageId: Long): EmergencyRecord? {
        synchronized(lock) {
            val existing = records[messageId] ?: return null
            val updated = existing.copy(
                ackStatus = true,
                retryStatus = if (existing.humanAckStatus) EmergencyRetryStatus.HUMAN_ACKED else EmergencyRetryStatus.TRANSPORT_ACKED
            )
            records[messageId] = updated
            flushToDisk()
            return updated
        }
    }

    /**
     * Records explicit human acknowledgement from an operator.
     * Sets humanAckStatus to true and retryStatus to HUMAN_ACKED.
     * This is the ONLY automatic protocol transition that resolves the emergency state.
     */
    fun recordHumanAck(messageId: Long): EmergencyRecord? {
        synchronized(lock) {
            val existing = records[messageId] ?: return null
            val updated = existing.copy(
                humanAckStatus = true,
                retryStatus = EmergencyRetryStatus.HUMAN_ACKED
            )
            records[messageId] = updated
            flushToDisk()
            return updated
        }
    }

    /**
     * Bounded retry accounting. Increments retry count and sets last attempt timestamp.
     */
    fun recordRetryAttempt(messageId: Long, success: Boolean, timestamp: Long): EmergencyRecord? {
        synchronized(lock) {
            val existing = records[messageId] ?: return null
            val nextCount = existing.retryCount + 1
            val newStatus = when {
                success -> EmergencyRetryStatus.SENT
                nextCount >= existing.maxRetries -> EmergencyRetryStatus.FAILED
                else -> existing.retryStatus
            }
            val updated = existing.copy(
                retryCount = nextCount,
                retryStatus = newStatus,
                lastAttempt = timestamp
            )
            records[messageId] = updated
            flushToDisk()
            return updated
        }
    }

    private fun flushToDisk() {
        try {
            val serialized = json.encodeToString(records.values.toList())
            val tempFile = File(storageDir, "emergency_records.json.tmp")
            tempFile.writeText(serialized, Charsets.UTF_8)
            if (storeFile.exists()) {
                storeFile.delete()
            }
            tempFile.renameTo(storeFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadFromDisk() {
        synchronized(lock) {
            records.clear()
            if (storeFile.exists() && storeFile.length() > 0L) {
                try {
                    val content = storeFile.readText(Charsets.UTF_8)
                    val list = json.decodeFromString<List<EmergencyRecord>>(content)
                    list.forEach { records[it.messageId] = it }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
