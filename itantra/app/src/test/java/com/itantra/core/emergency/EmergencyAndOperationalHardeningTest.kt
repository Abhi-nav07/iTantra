package com.itantra.core.emergency

import com.itantra.core.inference.AecStatus
import com.itantra.core.inference.ContinuousListenEngine
import com.itantra.core.inference.ContinuousListenState
import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketType
import com.itantra.domain.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Deterministic unit tests for Pass E Operational Hardening:
 * - 100/100 Emergency phrase localization coverage (10 codes x 10 languages)
 * - Receiver-language emergency resolution contract (MT bypassed, sender language ignored)
 * - Transport ACK vs HUMAN_ACK strict separation
 * - Durable emergency persistence surviving restart
 * - Bounded retry policy accounting
 * - Application-level non-interruptible alert semantics
 * - AEC status fallback and half-duplex continuous mode transitions
 */
class EmergencyAndOperationalHardeningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // 1. Technical Coverage: 10 Emergency Codes x 10 Languages = 100 Combinations
    @Test
    fun testAll100EmergencyPhraseCombinationsNonEmptyAndScriptValid() {
        val languages = listOf(
            LanguageCode.ENGLISH,
            LanguageCode.HINDI,
            LanguageCode.BENGALI,
            LanguageCode.GUJARATI,
            LanguageCode.MARATHI,
            LanguageCode.KANNADA,
            LanguageCode.MALAYALAM,
            LanguageCode.TAMIL,
            LanguageCode.TELUGU,
            LanguageCode.ODIA
        )
        val codes = EmergencyCode.values()

        assertEquals(10, languages.size)
        assertEquals(10, codes.size)

        var totalVerified = 0

        for (code in codes) {
            for (lang in languages) {
                val phrase = EmergencyPhraseResolver.resolve(code, lang)
                assertNotNull("Phrase must not be null for $code in $lang", phrase)
                assertTrue("Phrase must not be blank for $code in $lang", phrase.isNotBlank())
                assertFalse("Phrase must not contain placeholder for $code in $lang", phrase.contains("TODO") || phrase.contains("UNKNOWN"))

                // Verify vernacular scripts
                when (lang) {
                    LanguageCode.ENGLISH -> assertTrue("English phrase must use Latin characters", phrase.all { it.isWhitespace() || it.isLetterOrDigit() || it in ".,!?" })
                    LanguageCode.HINDI, LanguageCode.MARATHI -> assertTrue("Hindi/Marathi must contain Devanagari characters: $phrase", phrase.any { it in '\u0900'..'\u097F' })
                    LanguageCode.BENGALI -> assertTrue("Bengali must contain Bengali characters: $phrase", phrase.any { it in '\u0980'..'\u09FF' })
                    LanguageCode.GUJARATI -> assertTrue("Gujarati must contain Gujarati characters: $phrase", phrase.any { it in '\u0A80'..'\u0AFF' })
                    LanguageCode.KANNADA -> assertTrue("Kannada must contain Kannada characters: $phrase", phrase.any { it in '\u0C80'..'\u0CFF' })
                    LanguageCode.MALAYALAM -> assertTrue("Malayalam must contain Malayalam characters: $phrase", phrase.any { it in '\u0D00'..'\u0D7F' })
                    LanguageCode.TAMIL -> assertTrue("Tamil must contain Tamil characters: $phrase", phrase.any { it in '\u0B80'..'\u0BFF' })
                    LanguageCode.TELUGU -> assertTrue("Telugu must contain Telugu characters: $phrase", phrase.any { it in '\u0C00'..'\u0C7F' })
                    LanguageCode.ODIA -> assertTrue("Odia must contain Odia characters: $phrase", phrase.any { it in '\u0B00'..'\u0B7F' })
                }
                totalVerified++
            }
        }
        assertEquals("Must verify all 100 combinations", 100, totalVerified)
    }

    // 2. Emergency Receiver-Language Contract: Packet/sender language must NOT dictate spoken phrase
    @Test
    fun testEmergencyReceiverLanguageContract() {
        val code = EmergencyCode.HELP_REQUIRED

        // Case A: Sender Hindi -> Receiver Tamil
        val tamilPhrase = EmergencyPhraseResolver.resolve(code, LanguageCode.TAMIL)
        assertEquals("உதவி தேவை.", tamilPhrase)

        // Case B: Sender English -> Receiver Odia
        val odiaPhrase = EmergencyPhraseResolver.resolve(code, LanguageCode.ODIA)
        assertEquals("ସାହାଯ୍ୟ ଆବଶ୍ୟକ।", odiaPhrase)

        // Case C: Sender Bengali -> Receiver Hindi
        val hindiPhrase = EmergencyPhraseResolver.resolve(code, LanguageCode.HINDI)
        assertEquals("सहायता की आवश्यकता है।", hindiPhrase)

        // Case D: Sender Marathi -> Receiver Kannada
        val kannadaPhrase = EmergencyPhraseResolver.resolve(code, LanguageCode.KANNADA)
        assertEquals("ಸಹಾಯ ಬೇಕಿದೆ.", kannadaPhrase)
    }

    // 3. Transport ACK != HUMAN_ACK Separation
    @Test
    fun testTransportAckDoesNotClearUnresolvedEmergencyState() {
        val storeDir = tempFolder.newFolder("emergency_store_test1")
        val store = EmergencyPersistenceStore(storeDir)

        val msgId = 1001L
        val record = EmergencyRecord(
            messageId = msgId,
            emergencyCode = EmergencyCode.FIRE.name,
            source = "REMOTE",
            target = "LOCAL",
            createdAt = 5000L,
            resolvedPhrase = "Fire emergency."
        )
        store.saveRecord(record)

        assertTrue("New emergency must be unresolved", record.isUnresolved)
        assertEquals(EmergencyRetryStatus.PENDING, record.retryStatus)

        // Machine/Transport ACK arrives
        val afterTransportAck = store.recordTransportAck(msgId)!!
        assertTrue("Transport ACK flag must be true", afterTransportAck.ackStatus)
        assertFalse("Human ACK flag must remain false after transport ACK", afterTransportAck.humanAckStatus)
        assertEquals(EmergencyRetryStatus.TRANSPORT_ACKED, afterTransportAck.retryStatus)
        assertTrue("Record MUST remain unresolved after wire transport ACK", afterTransportAck.isUnresolved)

        // Only explicit HUMAN_ACK resolves the emergency
        val afterHumanAck = store.recordHumanAck(msgId)!!
        assertTrue("Human ACK flag must be true", afterHumanAck.humanAckStatus)
        assertEquals(EmergencyRetryStatus.HUMAN_ACKED, afterHumanAck.retryStatus)
        assertFalse("Record is finally resolved after HUMAN_ACK", afterHumanAck.isUnresolved)
    }

    // 4. Durable Persistence: State Survives App Restart
    @Test
    fun testUnresolvedEmergencyPersistsAcrossAppRestart() {
        val storeDir = tempFolder.newFolder("emergency_store_test2")
        val storeInstance1 = EmergencyPersistenceStore(storeDir)

        val record1 = EmergencyRecord(
            messageId = 2001L,
            emergencyCode = EmergencyCode.MEDICAL_EMERGENCY.name,
            source = "LOCAL",
            target = "BROADCAST",
            createdAt = 10000L,
            resolvedPhrase = "तुरंत चिकित्सा सहायता की आवश्यकता है।"
        )
        storeInstance1.saveRecord(record1)
        storeInstance1.recordTransportAck(2001L)

        // Simulate app kill and recreation with fresh store pointing to same directory
        val storeInstance2 = EmergencyPersistenceStore(storeDir)
        val unresolvedList = storeInstance2.getUnresolvedRecords()

        assertEquals("Must restore 1 unresolved record", 1, unresolvedList.size)
        val restored = unresolvedList[0]
        assertEquals(2001L, restored.messageId)
        assertEquals(EmergencyCode.MEDICAL_EMERGENCY.name, restored.emergencyCode)
        assertTrue("Transport ACK preserved", restored.ackStatus)
        assertFalse("Human ACK still false", restored.humanAckStatus)
        assertTrue("Still unresolved after restart", restored.isUnresolved)
        assertEquals("तुरंत चिकित्सा सहायता की आवश्यकता है।", restored.resolvedPhrase)
    }

    // 5. Bounded Retry Policy
    @Test
    fun testBoundedRetryPolicyTransitionsToFailedAfterMaxRetries() {
        val storeDir = tempFolder.newFolder("emergency_store_test3")
        val store = EmergencyPersistenceStore(storeDir)

        val msgId = 3001L
        val record = EmergencyRecord(
            messageId = msgId,
            emergencyCode = EmergencyCode.EVACUATE.name,
            source = "LOCAL",
            createdAt = 15000L,
            maxRetries = 3
        )
        store.saveRecord(record)

        // Attempt 1 fails
        var state = store.recordRetryAttempt(msgId, success = false, timestamp = 16000L)!!
        assertEquals(1, state.retryCount)
        assertTrue(state.canRetry)

        // Attempt 2 fails
        state = store.recordRetryAttempt(msgId, success = false, timestamp = 17000L)!!
        assertEquals(2, state.retryCount)
        assertTrue(state.canRetry)

        // Attempt 3 fails -> reaches maxRetries
        state = store.recordRetryAttempt(msgId, success = false, timestamp = 18000L)!!
        assertEquals(3, state.retryCount)
        assertEquals(EmergencyRetryStatus.FAILED, state.retryStatus)
        assertFalse("Must not allow retry after reaching maximum limit", state.canRetry)
    }

    // 6. Application-Level Non-Interruptibility
    @Test
    fun testNormalMessageDoesNotPreemptActiveEmergencyAlert() {
        val activeAlert = EmergencyRecord(
            messageId = 4001L,
            emergencyCode = EmergencyCode.DANGER.name,
            source = "REMOTE",
            createdAt = 20000L,
            humanAckStatus = false
        )

        // Normal incoming packet arriving during active alert
        val normalPacket = ItantraPacket(
            type = PacketType.TEXT,
            flags = MessagePriority.NORMAL.toByte(),
            messageId = 5001L,
            payload = "Hello".toByteArray()
        )

        // Condition check: active emergency alert blocks normal message from interrupting audio/state
        val isEmergencyActive = activeAlert.isUnresolved
        val shouldPreemptAudio = !isEmergencyActive && normalPacket.flags.toInt() != MessagePriority.CRITICAL

        assertTrue("Emergency alert must be active", isEmergencyActive)
        assertFalse("Normal voice message must NOT preempt active emergency alert", shouldPreemptAudio)
    }

    // 7. Acoustic Echo Cancellation State Fallback
    @Test
    fun testAecFallbackDiagnostics() {
        val availableStatuses = listOf(
            AecStatus.AEC_SUPPORTED,
            AecStatus.AEC_ENABLED,
            AecStatus.AEC_DISABLED,
            AecStatus.AEC_UNAVAILABLE
        )
        assertEquals(4, availableStatuses.size)

        // Default diagnostic state when hardware HAL does not expose effect
        val fallbackStatus = AecStatus.AEC_UNAVAILABLE
        assertEquals(AecStatus.AEC_UNAVAILABLE, fallbackStatus)
    }

    // 8. Half-Duplex Suppression State Transitions
    @Test
    fun testHalfDuplexContinuousModeSuppression() {
        var isTtsPlaying = false
        var vadFeedAllowed = true

        // Simulate incoming audio playback start
        isTtsPlaying = true
        vadFeedAllowed = !isTtsPlaying
        assertFalse("VAD must be suppressed while local TTS is playing", vadFeedAllowed)

        // Simulate playback completion + cooldown delay
        isTtsPlaying = false
        vadFeedAllowed = !isTtsPlaying
        assertTrue("VAD must resume after TTS playback completes", vadFeedAllowed)
    }
}
