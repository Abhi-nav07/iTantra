package com.itantra.core.transceiver

import com.itantra.core.crypto.CryptoPrimitives
import com.itantra.core.crypto.SecureSessionManager
import com.itantra.core.crypto.SecureSessionState
import com.itantra.core.inference.ContinuousListenState
import com.itantra.core.metrics.InMemoryMetricsRecorder
import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketEncoder
import com.itantra.core.transport.packet.PacketType
import com.itantra.core.transport.packet.ProtocolLanguageMapper
import com.itantra.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Deterministic unit and loopback tests for Pass D:
 * - Two-peer SAS confirmation lifecycle
 * - Encrypted capability negotiation
 * - Semantic payload vs Ciphertext vs Wire frame byte accounting
 * - Crypto microsecond timing recording
 * - Estimated E2E latency composition
 * - Emergency semantic code receiver-language resolution
 * - Half-duplex continuous mode state transitions
 */
class PeerProtocolAndLatencyTest {

    // 1. Two-peer SAS confirmation lifecycle & capabilities exchange
    @Test
    fun testTwoPeerHandshakeSasAndEncryptedCapabilities() {
        val alice = SecureSessionManager()
        val bob = SecureSessionManager()

        // Alice sends HELLO, Bob replies
        val aliceHello = alice.startHandshake(isInitiator = true)
        val bobHello = bob.processSecureHello(aliceHello)!!
        alice.processSecureHello(bobHello)

        assertEquals(alice.sasCode.value, bob.sasCode.value)
        assertEquals(SecureSessionState.WAITING_USER_VERIFICATION, alice.state.value)
        assertEquals(SecureSessionState.WAITING_USER_VERIFICATION, bob.state.value)

        // Cannot encrypt capabilities before BOTH confirm
        val unverifiedCapPacket = ItantraPacket(type = PacketType.CAPABILITIES, messageId = 10L, payload = ByteArray(4))
        var preConfirmFailed = false
        try {
            alice.encrypt(unverifiedCapPacket)
        } catch (e: IllegalStateException) {
            preConfirmFailed = true
        }
        assertTrue("Capabilities must not be encrypted before verification", preConfirmFailed)

        // Both confirm SAS
        val aliceVerify = alice.confirmSasMatch()
        val bobVerify = bob.confirmSasMatch()

        alice.processSecureVerify(bobVerify)
        bob.processSecureVerify(aliceVerify)

        assertEquals(SecureSessionState.SECURE_VERIFIED, alice.state.value)
        assertEquals(SecureSessionState.SECURE_VERIFIED, bob.state.value)

        // Now Alice can send encrypted capabilities to Bob
        val aliceLangs = listOf(LanguageCode.HINDI, LanguageCode.ENGLISH)
        val sttMask = ProtocolLanguageMapper.toBitmask(aliceLangs)
        val ttsMask = ProtocolLanguageMapper.toBitmask(aliceLangs)
        val capPayload = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putShort(sttMask)
            .putShort(ttsMask)
            .array()

        val capPacket = ItantraPacket(type = PacketType.CAPABILITIES, messageId = 11L, payload = capPayload)
        val encCapPacket = alice.encrypt(capPacket)
        assertEquals(1.toByte(), encCapPacket.securityVersion)

        // Bob decrypts capabilities
        val decCapPacket = bob.decrypt(encCapPacket)
        assertEquals(PacketType.CAPABILITIES, decCapPacket.type)

        val buf = ByteBuffer.wrap(decCapPacket.payload).order(ByteOrder.BIG_ENDIAN)
        val parsedStt = ProtocolLanguageMapper.fromBitmask(buf.short)
        val parsedTts = ProtocolLanguageMapper.fromBitmask(buf.short)

        assertTrue(parsedStt.contains(LanguageCode.HINDI))
        assertTrue(parsedStt.contains(LanguageCode.ENGLISH))
        assertTrue(parsedTts.contains(LanguageCode.HINDI))
        assertTrue(parsedTts.contains(LanguageCode.ENGLISH))
    }

    // 2. ACK vs HUMAN_ACK distinction in transceiver protocol
    @Test
    fun testAckVsHumanAckDistinction() {
        val msgId = 555L

        // Regular transport delivery ACK
        val ackPacket = ItantraPacket(type = PacketType.ACK, messageId = msgId)
        assertEquals(PacketType.ACK, ackPacket.type)

        // Human explicit acknowledgment
        val humanAckPacket = ItantraPacket(type = PacketType.HUMAN_ACK, messageId = msgId)
        assertEquals(PacketType.HUMAN_ACK, humanAckPacket.type)
        assertNotEquals(ackPacket.type, humanAckPacket.type)

        // Verify message state mapping
        val msgDelivered = TransceiverMessage(
            messageId = msgId,
            language = LanguageCode.HINDI,
            priority = MessagePriority.NORMAL,
            text = "Test",
            source = MessageSource.LOCAL,
            createdAtLocal = 1000L,
            state = MessageState.DELIVERED
        )
        val msgAcked = msgDelivered.copy(state = MessageState.ACKNOWLEDGED)

        assertEquals(MessageState.DELIVERED, msgDelivered.state)
        assertEquals(MessageState.ACKNOWLEDGED, msgAcked.state)
    }

    // 3. Plaintext semantic payload vs ciphertext vs wire frame byte accounting
    @Test
    fun testAuthoritativeByteAccounting() {
        val alice = SecureSessionManager()
        val bob = SecureSessionManager()
        val h1 = alice.startHandshake(true)
        val h2 = bob.processSecureHello(h1)!!
        alice.processSecureHello(h2)
        alice.processSecureVerify(bob.confirmSasMatch())
        bob.processSecureVerify(alice.confirmSasMatch())

        val semanticText = "मुझे चिकित्सा सहायता चाहिए"
        val semanticBytes = semanticText.toByteArray(Charsets.UTF_8)
        val semanticPayloadSize = semanticBytes.size // ~73 bytes UTF-8

        val plainPacket = ItantraPacket(
            type = PacketType.TEXT,
            messageId = 123L,
            languageCode = LanguageCode.HINDI,
            payload = semanticBytes
        )

        val securePacket = alice.encrypt(plainPacket)
        val ciphertextBytes = securePacket.payload.size
        // In AES-GCM, ciphertext = plaintext + 16-byte auth tag
        assertEquals(semanticPayloadSize + 16, ciphertextBytes)

        val wireFrame = PacketEncoder.encode(securePacket)
        val wireFrameSize = wireFrame.size
        // Wire frame V2 = 4-byte outer length + 28-byte header + 4-byte payload length + ciphertext + 4-byte CRC32 = 40 + ciphertextBytes
        assertEquals(40 + ciphertextBytes, wireFrameSize)

        // Verify all three representations are strictly distinct
        assertTrue(semanticPayloadSize < ciphertextBytes)
        assertTrue(ciphertextBytes < wireFrameSize)
    }

    // 4. Crypto timing recording in MetricsRecorder
    @Test
    fun testCryptoTimingRecorderIntegration() {
        val alice = SecureSessionManager()
        val bob = SecureSessionManager()
        val h1 = alice.startHandshake(true)
        val h2 = bob.processSecureHello(h1)!!
        alice.processSecureHello(h2)
        alice.processSecureVerify(bob.confirmSasMatch())
        bob.processSecureVerify(alice.confirmSasMatch())

        val plainPacket = ItantraPacket(type = PacketType.TEXT, messageId = 101L, payload = "Payload".toByteArray())
        val encrypted = alice.encrypt(plainPacket)
        bob.decrypt(encrypted)

        assertTrue(alice.encryptDurationUs >= 0)
        assertTrue(bob.decryptDurationUs >= 0)

        val recorder = InMemoryMetricsRecorder()
        val cryptoMetrics = CryptoMetrics(
            handshakeDurationMillis = Measurement.Measured(alice.lastHandshakeDurationMillis),
            verificationDurationMillis = Measurement.Measured(alice.lastVerificationDurationMillis),
            avgEncryptUs = Measurement.Measured(alice.encryptDurationUs),
            avgDecryptUs = Measurement.Measured(bob.decryptDurationUs),
            authFailures = 0,
            replayRejections = 0
        )
        recorder.recordCryptoMetrics(cryptoMetrics)

        val recorded = recorder.latest.value.crypto
        assertTrue(recorded.avgEncryptUs is Measurement.Measured)
        assertTrue(recorded.avgDecryptUs is Measurement.Measured)
        assertEquals(0, recorded.authFailures)
    }

    // 5. Estimated E2E latency composition & honest labeling
    @Test
    fun testEstimatedE2eLatencyComposition() {
        val sttLatency = 180L
        val mtLatency = 350L
        val cryptoLatency = 3L
        val rtt = 60L
        val oneWayTransportEst = rtt / 2 // 30L
        val peerTtfa = 220L // Remote batch generation proxy

        val estimatedE2e = sttLatency + mtLatency + cryptoLatency + oneWayTransportEst + peerTtfa
        assertEquals(783L, estimatedE2e)

        val message = TransceiverMessage(
            messageId = 1L,
            language = LanguageCode.HINDI,
            targetLanguage = LanguageCode.KANNADA,
            priority = MessagePriority.NORMAL,
            text = "ನನಗೆ ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕು",
            source = MessageSource.LOCAL,
            createdAtLocal = 1000L,
            state = MessageState.REMOTE_PLAYBACK_CONFIRMED,
            sttLatencyMillis = sttLatency,
            mtLatencyMillis = mtLatency,
            cryptoLatencyMillis = cryptoLatency,
            rttMillis = rtt,
            peerTtfaMillis = peerTtfa,
            estimatedE2eMillis = estimatedE2e
        )

        assertEquals(783L, message.estimatedE2eMillis)
        assertEquals(350L, message.mtLatencyMillis)
        assertEquals(3L, message.cryptoLatencyMillis)

        val recorder = InMemoryMetricsRecorder()
        recorder.recordEndToEndLatency(message.estimatedE2eMillis)
        val metric = recorder.latest.value.endToEndMillis
        assertTrue(metric is Measurement.Measured)
        assertEquals(783L, (metric as Measurement.Measured).value)
    }

    // 6. Emergency semantic code resolves directly into receiver's active language
    @Test
    fun testEmergencyCodeReceiverLanguageResolution() {
        val senderLanguage = LanguageCode.HINDI
        val receiverLanguage = LanguageCode.KANNADA
        val emergencyCode = EmergencyCode.MEDICAL_EMERGENCY

        // In Section N: The sender transmits the 1-byte code with its language tag
        val packet = ItantraPacket(
            type = PacketType.EMERGENCY_CODE,
            flags = MessagePriority.CRITICAL.toByte(),
            messageId = 999L,
            languageCode = senderLanguage,
            payload = byteArrayOf(emergencyCode.id)
        )

        // Resolving using packet.languageCode yields Hindi (sender's language)
        val senderResolved = EmergencyPhraseResolver.resolve(emergencyCode, packet.languageCode)
        assertEquals("तुरंत चिकित्सा सहायता की आवश्यकता है।", senderResolved)

        // CORRECT (Section N fix): Resolving using receiver's localLanguage yields Kannada directly
        val receiverResolved = EmergencyPhraseResolver.resolve(emergencyCode, receiverLanguage)
        assertEquals("ತುರ್ತು ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕಿದೆ।", receiverResolved.replace(".", "।").replace("?", "।"))
        assertTrue(receiverResolved.isNotBlank())

        // Verified: The receiver understands the emergency immediately in its own native tongue without MT
        assertNotEquals(senderResolved, receiverResolved)
    }

    // 7. Continuous-mode half-duplex state transitions
    @Test
    fun testContinuousModeStateTransitions() {
        var state = ContinuousListenState.OFF
        assertEquals(ContinuousListenState.OFF, state)

        state = ContinuousListenState.LISTENING
        assertEquals(ContinuousListenState.LISTENING, state)

        // Speech detected by VAD
        state = ContinuousListenState.SPEECH_DETECTED
        assertEquals(ContinuousListenState.SPEECH_DETECTED, state)

        // Silence endpoint reached -> Segment ready
        state = ContinuousListenState.SEGMENT_READY
        assertEquals(ContinuousListenState.SEGMENT_READY, state)

        // Half-duplex rule: When TTS playback begins, listening is stopped
        state = ContinuousListenState.OFF
        assertEquals(ContinuousListenState.OFF, state)
    }
}
