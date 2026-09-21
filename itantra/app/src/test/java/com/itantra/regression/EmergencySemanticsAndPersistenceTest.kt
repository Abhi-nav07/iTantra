package com.itantra.regression

import com.itantra.core.emergency.EmergencyPersistenceStore
import com.itantra.domain.model.EmergencyCode
import com.itantra.domain.model.EmergencyRecord
import com.itantra.domain.model.EmergencyRetryStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EmergencySemanticsAndPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLocalVsRemoteUnresolvedDistinctionOnRestart() {
        val store = EmergencyPersistenceStore(tempFolder.root)

        val localSos = EmergencyRecord(
            messageId = 1001L,
            emergencyCode = EmergencyCode.HELP_REQUIRED.name,
            source = "LOCAL",
            target = "REMOTE",
            createdAt = 1000L,
            retryStatus = EmergencyRetryStatus.PENDING,
            humanAckStatus = false,
            resolvedPhrase = "SOS Help"
        )

        val remoteSos = EmergencyRecord(
            messageId = 2002L,
            emergencyCode = EmergencyCode.HELP_REQUIRED.name,
            source = "REMOTE",
            target = "LOCAL",
            createdAt = 1005L,
            retryStatus = EmergencyRetryStatus.PENDING,
            humanAckStatus = false,
            resolvedPhrase = "Incoming Emergency"
        )

        store.saveRecord(localSos)
        store.saveRecord(remoteSos)

        val allUnresolved = store.getUnresolvedRecords()
        assertEquals(2, allUnresolved.size)

        // On restart, TransceiverCoordinator filters for REMOTE unresolved only:
        val remoteUnresolved = allUnresolved.filter { it.source == "REMOTE" }
        assertEquals(1, remoteUnresolved.size)
        assertEquals(2002L, remoteUnresolved.last().messageId)

        // LOCAL record remains in store for retry tracking, but will NOT trigger sender's own alarm
        val localUnresolved = allUnresolved.filter { it.source == "LOCAL" }
        assertEquals(1, localUnresolved.size)
        assertEquals(1001L, localUnresolved.last().messageId)
    }

    @Test
    fun testResolvedRecordsDoNotTriggerAlarmOnRestart() {
        val store = EmergencyPersistenceStore(tempFolder.root)

        val sos = EmergencyRecord(
            messageId = 3001L,
            emergencyCode = EmergencyCode.HELP_REQUIRED.name,
            source = "REMOTE",
            target = "LOCAL",
            createdAt = 2000L,
            retryStatus = EmergencyRetryStatus.HUMAN_ACKED,
            humanAckStatus = true,
            resolvedPhrase = "SOS Resolved"
        )
        store.saveRecord(sos)

        val unresolved = store.getUnresolvedRecords()
        assertTrue("Resolved records must not be returned by getUnresolvedRecords", unresolved.isEmpty())
        val remoteUnresolved = unresolved.filter { it.source == "REMOTE" }
        assertTrue("No alarm should be restored on restart", remoteUnresolved.isEmpty())
    }

    @Test
    fun testMultipleRecordsSelectsLatestRemoteUnresolved() {
        val store = EmergencyPersistenceStore(tempFolder.root)

        val r1 = EmergencyRecord(
            messageId = 4001L,
            emergencyCode = EmergencyCode.HELP_REQUIRED.name,
            source = "REMOTE",
            target = "LOCAL",
            createdAt = 1000L,
            retryStatus = EmergencyRetryStatus.PENDING,
            humanAckStatus = false,
            resolvedPhrase = "First Alert"
        )
        val r2 = EmergencyRecord(
            messageId = 4002L,
            emergencyCode = EmergencyCode.FIRE.name,
            source = "REMOTE",
            target = "LOCAL",
            createdAt = 2000L,
            retryStatus = EmergencyRetryStatus.PENDING,
            humanAckStatus = false,
            resolvedPhrase = "Second Alert"
        )
        store.saveRecord(r1)
        store.saveRecord(r2)

        val remoteUnresolved = store.getUnresolvedRecords().filter { it.source == "REMOTE" }
        assertEquals(2, remoteUnresolved.size)
        assertEquals(4002L, remoteUnresolved.last().messageId)
        assertEquals("Second Alert", remoteUnresolved.last().resolvedPhrase)
    }

    @Test
    fun testHumanAckResolvesRecord() {
        val store = EmergencyPersistenceStore(tempFolder.root)

        val sos = EmergencyRecord(
            messageId = 5001L,
            emergencyCode = EmergencyCode.HELP_REQUIRED.name,
            source = "REMOTE",
            target = "LOCAL",
            createdAt = 2000L,
            retryStatus = EmergencyRetryStatus.PENDING,
            humanAckStatus = false,
            resolvedPhrase = "SOS Help"
        )
        store.saveRecord(sos)
        assertTrue(store.getRecord(5001L)!!.isUnresolved)

        // Transport ACK does NOT resolve
        store.recordTransportAck(5001L)
        assertTrue(store.getRecord(5001L)!!.isUnresolved)

        // Human ACK resolves
        store.recordHumanAck(5001L)
        assertFalse(store.getRecord(5001L)!!.isUnresolved)
        assertEquals(EmergencyRetryStatus.HUMAN_ACKED, store.getRecord(5001L)!!.retryStatus)
    }
}
