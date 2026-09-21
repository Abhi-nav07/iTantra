package com.itantra.core.crypto

import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketEncoder
import com.itantra.core.transport.packet.PacketType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair

/**
 * Manages the ECDH handshake, SAS verification, and AES-256-GCM session encryption.
 *
 * Handshake Architecture:
 * - Initiator sends the first SECURE_HELLO upon transport connection.
 * - Responder remains in NO_SESSION until incoming SECURE_HELLO arrives.
 * - processSecureHello() in NO_SESSION generates responder keys, derives session material,
 *   transitions to WAITING_USER_VERIFICATION, and returns the responder HELLO packet.
 * - Retransmitted HELLO packets safely return the cached localHelloPacket without state corruption.
 *
 * Cryptographic Serialization & Replay Protection:
 * - TX counter increment and nonce construction are guarded by [encryptMutex] in a suspendable block,
 *   guaranteeing no AES-GCM nonce reuse across concurrent coroutines.
 * - Replay protection uses a sliding [ReplayWindow] (64-packet window):
 *     1. Pre-auth check rejects stale/replayed packets without mutating window state.
 *     2. AEAD decrypts and authenticates ciphertext and AAD.
 *     3. Window commits the counter ONLY after AEAD authentication succeeds.
 */
class SecureSessionManager {

    private val _state = MutableStateFlow(SecureSessionState.NO_SESSION)
    val state: StateFlow<SecureSessionState> = _state.asStateFlow()

    private val _sasCode = MutableStateFlow<String?>(null)
    val sasCode: StateFlow<String?> = _sasCode.asStateFlow()

    private val _peerDeviceName = MutableStateFlow<String?>(null)
    val peerDeviceName: StateFlow<String?> = _peerDeviceName.asStateFlow()

    fun setPeerDeviceName(name: String?) { _peerDeviceName.value = name }

    // Ephemeral Key Pair (Generated freshly per session)
    private var localKeyPair: KeyPair? = null
    private var localNonce: ByteArray? = null

    // Cache the local HELLO packet so it can be retransmitted without regenerating keys
    private var localHelloPacket: ItantraPacket? = null

    // Handshake transcript parts
    private var peerPublicKeyBytes: ByteArray? = null
    private var peerNonce: ByteArray? = null
    private var isInitiator: Boolean = false

    // Derived session material
    private var txKey: ByteArray? = null
    private var rxKey: ByteArray? = null
    private var txNoncePrefix: ByteArray? = null
    private var rxNoncePrefix: ByteArray? = null

    // Mutex protecting TX counter so concurrent encrypt() calls never share the same nonce
    private val encryptMutex = Mutex()
    private var txCounter: Long = 0

    // Sliding-window replay protection (64 packets)
    private val replayWindow = ReplayWindow(windowSize = 64)

    // Two-Party SAS Verification Tracking
    var localSasConfirmed: Boolean = false
        private set
    var peerSasConfirmed: Boolean = false
        private set

    // Metrics
    var lastHandshakeDurationMillis: Long = 0
    var lastVerificationDurationMillis: Long = 0
    private var handshakeStartNanos: Long = 0
    private var verificationStartNanos: Long = 0

    // Realtime metrics
    var encryptDurationUs: Long = 0
    var decryptDurationUs: Long = 0
    var authFailures: Int = 0
    var replayRejections: Int = 0

    /**
     * Starts an initiator handshake (generates a fresh ephemeral keypair).
     * Caches the resulting HELLO packet so it can be retransmitted via [getStoredHello].
     */
    fun startHandshake(isInitiator: Boolean): ItantraPacket {
        this.isInitiator = isInitiator
        resetSession()
        _state.value = SecureSessionState.HANDSHAKING
        handshakeStartNanos = System.nanoTime()

        val kp = CryptoPrimitives.generateEcdhKeyPair()
        localKeyPair = kp
        val lNonce = CryptoPrimitives.generateRandomNonce(16)
        localNonce = lNonce

        val pubKeyBytes = kp.public.encoded
        val payload = ByteBuffer.allocate(pubKeyBytes.size + 16).order(ByteOrder.BIG_ENDIAN)
            .put(lNonce)
            .put(pubKeyBytes)
            .array()

        val hello = ItantraPacket(
            type = PacketType.SECURE_HELLO,
            messageId = System.currentTimeMillis(),
            payload = payload
        )
        localHelloPacket = hello
        return hello
    }

    /**
     * Returns the stored local HELLO packet without regenerating keypair.
     * Null if no handshake has been started this session.
     */
    fun getStoredHello(): ItantraPacket? = localHelloPacket

    /**
     * Processes an incoming SECURE_HELLO.
     * - Responder (NO_SESSION): validates payload, generates responder keypair, derives session material,
     *   transitions to WAITING_USER_VERIFICATION, and returns responder HELLO packet.
     * - Responder retransmission (WAITING_USER_VERIFICATION): re-sends stored HELLO without state mutation.
     * - Initiator (HANDSHAKING): derives session material, transitions to WAITING_USER_VERIFICATION, returns null.
     * - Any other state: returns null (idempotent/drop).
     */
    fun processSecureHello(packet: ItantraPacket): ItantraPacket? {
        // Safe retransmission: if responder already processed HELLO and is waiting for SAS verification,
        // re-send the cached response without corrupting session state.
        if (!isInitiator && _state.value == SecureSessionState.WAITING_USER_VERIFICATION) {
            return localHelloPacket
        }

        // Validate payload before state mutation: 16-byte nonce + at least 32-byte public key
        val payload = packet.payload
        if (payload.size < 16 + 32) return null

        val pNonce = ByteArray(16)
        val pPubKey = ByteArray(payload.size - 16)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        buffer.get(pNonce)
        buffer.get(pPubKey)

        if (_state.value == SecureSessionState.NO_SESSION) {
            // RESPONDER PATH: Generate responder material and derive session
            this.isInitiator = false
            handshakeStartNanos = System.nanoTime()

            val kp = CryptoPrimitives.generateEcdhKeyPair()
            val lNonce = CryptoPrimitives.generateRandomNonce(16)
            localKeyPair = kp
            localNonce = lNonce

            val pubKeyBytes = kp.public.encoded
            val respPayload = ByteBuffer.allocate(pubKeyBytes.size + 16).order(ByteOrder.BIG_ENDIAN)
                .put(lNonce)
                .put(pubKeyBytes)
                .array()

            val hello = ItantraPacket(
                type = PacketType.SECURE_HELLO,
                messageId = System.currentTimeMillis(),
                payload = respPayload
            )
            localHelloPacket = hello

            peerNonce = pNonce
            peerPublicKeyBytes = pPubKey

            deriveSessionMaterial()
            return hello
        } else if (_state.value == SecureSessionState.HANDSHAKING && isInitiator) {
            // INITIATOR PATH: Received responder's HELLO
            peerNonce = pNonce
            peerPublicKeyBytes = pPubKey

            deriveSessionMaterial()
            return null
        } else {
            return null
        }
    }

    private fun deriveSessionMaterial() {
        try {
            val localKp = localKeyPair ?: return
            val localPrivKey = localKp.private
            val peerPubKey = peerPublicKeyBytes ?: return
            val lNonce = localNonce ?: return
            val pNonce = peerNonce ?: return

            val sharedSecret = CryptoPrimitives.computeSharedSecret(localPrivKey, peerPubKey)

            val transcript = if (isInitiator) {
                localKp.public.encoded + lNonce + peerPubKey + pNonce
            } else {
                peerPubKey + pNonce + localKp.public.encoded + lNonce
            }
            val transcriptHash = CryptoPrimitives.sha256(transcript)

            // Derive 72 bytes: 32+32 AES keys, 4+4 nonce prefixes
            val keyMaterial = CryptoPrimitives.hkdfSha256(
                ikm = sharedSecret,
                info = "iTantra Secure Transport v1".toByteArray(),
                outputLength = 72
            )
            val buf = ByteBuffer.wrap(keyMaterial)
            val aToBKey = ByteArray(32).also { buf.get(it) }
            val bToAKey = ByteArray(32).also { buf.get(it) }
            val aToBNonce = ByteArray(4).also { buf.get(it) }
            val bToANonce = ByteArray(4).also { buf.get(it) }

            if (isInitiator) {
                txKey = aToBKey; rxKey = bToAKey
                txNoncePrefix = aToBNonce; rxNoncePrefix = bToANonce
            } else {
                txKey = bToAKey; rxKey = aToBKey
                txNoncePrefix = bToANonce; rxNoncePrefix = aToBNonce
            }

            _sasCode.value = CryptoPrimitives.deriveSas(sharedSecret, transcriptHash)
            lastHandshakeDurationMillis = (System.nanoTime() - handshakeStartNanos) / 1_000_000
            verificationStartNanos = System.nanoTime()
            _state.value = SecureSessionState.WAITING_USER_VERIFICATION

        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = SecureSessionState.FAILED
        }
    }

    fun confirmSasMatch(): ItantraPacket {
        localSasConfirmed = true
        checkVerificationState()
        return ItantraPacket(type = PacketType.SECURE_VERIFY, messageId = System.currentTimeMillis())
    }

    fun rejectSas() {
        resetSession()
        _state.value = SecureSessionState.FAILED
    }

    fun processSecureVerify(packet: ItantraPacket) {
        if (_state.value != SecureSessionState.WAITING_USER_VERIFICATION &&
            _state.value != SecureSessionState.SECURE_VERIFIED) {
            return
        }
        peerSasConfirmed = true
        checkVerificationState()
    }

    private fun checkVerificationState() {
        if (localSasConfirmed && peerSasConfirmed) {
            if (_state.value == SecureSessionState.WAITING_USER_VERIFICATION) {
                lastVerificationDurationMillis = (System.nanoTime() - verificationStartNanos) / 1_000_000
                _state.value = SecureSessionState.SECURE_VERIFIED
            }
        }
    }

    private fun constructNonce(prefix: ByteArray, counter: Long): ByteArray {
        val nonce = ByteArray(12)
        System.arraycopy(prefix, 0, nonce, 0, 4)
        ByteBuffer.wrap(nonce, 4, 8).order(ByteOrder.BIG_ENDIAN).putLong(counter)
        return nonce
    }

    /**
     * Serialized encryption through [encryptMutex].
     * Atomic critical section: counter check/increment -> nonce construction -> AES-GCM encryption.
     */
    suspend fun encrypt(packet: ItantraPacket): ItantraPacket = encryptMutex.withLock {
        if (_state.value != SecureSessionState.SECURE_VERIFIED) {
            throw IllegalStateException("Cannot encrypt: session is not secure (State: ${_state.value})")
        }
        val key = txKey ?: throw IllegalStateException("Missing TX key")
        val prefix = txNoncePrefix ?: throw IllegalStateException("Missing TX nonce prefix")

        txCounter++
        val currentCounter = txCounter

        val securePacket = packet.copy(securityVersion = 1, counter = currentCounter)
        val aad = PacketEncoder.extractAad(securePacket)
        val nonce = constructNonce(prefix, currentCounter)

        val t0 = System.nanoTime()
        val ciphertext = CryptoPrimitives.encryptAesGcm(key, nonce, aad, packet.payload)
        encryptDurationUs = (System.nanoTime() - t0) / 1000

        securePacket.copy(payload = ciphertext)
    }

    /**
     * Authenticated decryption with sliding-window replay protection.
     * 1. Pre-auth replay check rejects duplicates/stale packets without mutating state.
     * 2. AEAD decryption authenticates ciphertext and AAD.
     * 3. Counter commits to ReplayWindow ONLY after successful authentication.
     */
    fun decrypt(packet: ItantraPacket): ItantraPacket {
        if (packet.securityVersion != 1.toByte()) {
            throw IllegalArgumentException("Packet is not encrypted")
        }
        if (_state.value != SecureSessionState.SECURE_VERIFIED) {
            authFailures++
            throw IllegalStateException("Cannot decrypt: session is not secure")
        }
        val key = rxKey ?: throw IllegalStateException("Missing RX key")
        val prefix = rxNoncePrefix ?: throw IllegalStateException("Missing RX nonce prefix")

        // Step 1: Pre-auth check (does not mutate replay state)
        if (!replayWindow.checkAcceptable(packet.counter)) {
            replayRejections++
            throw SecurityException(
                "Replay/stale packet rejected: counter=${packet.counter}, highest=${replayWindow.highestAccepted()}"
            )
        }

        val aad = PacketEncoder.extractAad(packet)
        val nonce = constructNonce(prefix, packet.counter)

        val t0 = System.nanoTime()
        val plaintext = try {
            CryptoPrimitives.decryptAesGcm(key, nonce, aad, packet.payload)
        } catch (e: Exception) {
            authFailures++
            throw SecurityException("Packet authentication failed", e)
        }
        decryptDurationUs = (System.nanoTime() - t0) / 1000

        // Step 2: Commit counter ONLY AFTER successful AEAD authentication
        replayWindow.commit(packet.counter)

        return packet.copy(securityVersion = 0, payload = plaintext)
    }

    fun resetSession() {
        _state.value = SecureSessionState.NO_SESSION
        _sasCode.value = null
        _peerDeviceName.value = null
        localKeyPair = null
        localNonce = null
        localHelloPacket = null
        peerPublicKeyBytes = null
        peerNonce = null
        txKey = null; rxKey = null
        txNoncePrefix = null; rxNoncePrefix = null
        txCounter = 0
        replayWindow.reset()
        localSasConfirmed = false
        peerSasConfirmed = false
    }
}
