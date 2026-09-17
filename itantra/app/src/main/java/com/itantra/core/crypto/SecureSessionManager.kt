package com.itantra.core.crypto

import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketEncoder
import com.itantra.core.transport.packet.PacketType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair

class SecureSessionManager {

    private val _state = MutableStateFlow(SecureSessionState.NO_SESSION)
    val state: StateFlow<SecureSessionState> = _state.asStateFlow()

    private val _sasCode = MutableStateFlow<String?>(null)
    val sasCode: StateFlow<String?> = _sasCode.asStateFlow()

    // Ephemeral Key Pair (Generated freshly per session)
    private var localKeyPair: KeyPair? = null
    private var localNonce: ByteArray? = null

    // Handshake transcript parts
    private var peerPublicKeyBytes: ByteArray? = null
    private var peerNonce: ByteArray? = null
    private var isInitiator: Boolean = false

    // Derived session material
    private var txKey: ByteArray? = null
    private var rxKey: ByteArray? = null
    private var txNoncePrefix: ByteArray? = null
    private var rxNoncePrefix: ByteArray? = null

    // Monotonic counters
    private var txCounter: Long = 0
    private var highestAcceptedRxCounter: Long = -1
    
    // Metrics
    var lastHandshakeDurationMillis: Long = 0
    var lastVerificationDurationMillis: Long = 0
    private var handshakeStartNanos: Long = 0
    private var verificationStartNanos: Long = 0
    
    // Realtime metrics for external observation
    var encryptDurationUs: Long = 0
    var decryptDurationUs: Long = 0
    var authFailures: Int = 0
    var replayRejections: Int = 0

    fun startHandshake(isInitiator: Boolean): ItantraPacket {
        this.isInitiator = isInitiator
        resetSession()
        _state.value = SecureSessionState.HANDSHAKING
        handshakeStartNanos = System.nanoTime()

        val kp = CryptoPrimitives.generateEcdhKeyPair()
        localKeyPair = kp
        localNonce = CryptoPrimitives.generateRandomNonce(16)
        
        val pubKeyBytes = kp.public.encoded
        val payload = ByteBuffer.allocate(pubKeyBytes.size + 16).order(ByteOrder.BIG_ENDIAN)
            .put(localNonce!!)
            .put(pubKeyBytes)
            .array()
            
        return ItantraPacket(
            type = PacketType.SECURE_HELLO,
            messageId = System.currentTimeMillis(),
            payload = payload
        )
    }

    fun processSecureHello(packet: ItantraPacket): ItantraPacket? {
        if (_state.value != SecureSessionState.HANDSHAKING && _state.value != SecureSessionState.NO_SESSION) {
            return null // Reject out of order
        }
        
        // If we didn't start the handshake, we are the responder and need to send our HELLO
        var responsePacket: ItantraPacket? = null
        if (_state.value == SecureSessionState.NO_SESSION) {
            responsePacket = startHandshake(isInitiator = false)
        }

        val payload = packet.payload
        if (payload.size < 16) return null
        
        val pNonce = ByteArray(16)
        val pPubKey = ByteArray(payload.size - 16)
        
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        buffer.get(pNonce)
        buffer.get(pPubKey)
        
        peerNonce = pNonce
        peerPublicKeyBytes = pPubKey
        
        // We now have both sides material, derive keys
        deriveSessionMaterial()
        
        return responsePacket
    }
    
    private fun deriveSessionMaterial() {
        try {
            val localPrivKey = localKeyPair?.private ?: return
            val peerPubKey = peerPublicKeyBytes ?: return
            val lNonce = localNonce ?: return
            val pNonce = peerNonce ?: return
            
            val sharedSecret = CryptoPrimitives.computeSharedSecret(localPrivKey, peerPubKey)
            
            // Construct canonical transcript: Initiator PubKey + Initiator Nonce + Responder PubKey + Responder Nonce
            val transcript = if (isInitiator) {
                localKeyPair!!.public.encoded + lNonce + peerPubKey + pNonce
            } else {
                peerPubKey + pNonce + localKeyPair!!.public.encoded + lNonce
            }
            val transcriptHash = CryptoPrimitives.sha256(transcript)
            
            // Derive directional keys using HKDF
            // 32 bytes for A->B AES Key
            // 32 bytes for B->A AES Key
            // 4 bytes for A->B Nonce Prefix
            // 4 bytes for B->A Nonce Prefix
            // Total 72 bytes
            val keyMaterial = CryptoPrimitives.hkdfSha256(
                ikm = sharedSecret,
                info = "iTantra Secure Transport v1".toByteArray(),
                outputLength = 72
            )
            
            val buffer = ByteBuffer.wrap(keyMaterial)
            val aToBKey = ByteArray(32).also { buffer.get(it) }
            val bToAKey = ByteArray(32).also { buffer.get(it) }
            val aToBNonce = ByteArray(4).also { buffer.get(it) }
            val bToANonce = ByteArray(4).also { buffer.get(it) }
            
            if (isInitiator) {
                txKey = aToBKey
                rxKey = bToAKey
                txNoncePrefix = aToBNonce
                rxNoncePrefix = bToANonce
            } else {
                txKey = bToAKey
                rxKey = aToBKey
                txNoncePrefix = bToANonce
                rxNoncePrefix = aToBNonce
            }
            
            // Derive SAS
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
        lastVerificationDurationMillis = (System.nanoTime() - verificationStartNanos) / 1_000_000
        return ItantraPacket(
            type = PacketType.SECURE_VERIFY,
            messageId = System.currentTimeMillis()
        )
    }

    fun rejectSas() {
        resetSession()
        _state.value = SecureSessionState.FAILED
    }

    fun processSecureVerify(packet: ItantraPacket) {
        if (_state.value == SecureSessionState.WAITING_USER_VERIFICATION) {
            _state.value = SecureSessionState.SECURE_VERIFIED
        }
    }

    /**
     * Constructs the 12-byte nonce for GCM.
     */
    private fun constructNonce(prefix: ByteArray, counter: Long): ByteArray {
        val nonce = ByteArray(12)
        System.arraycopy(prefix, 0, nonce, 0, 4)
        val buffer = ByteBuffer.wrap(nonce, 4, 8).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(counter)
        return nonce
    }

    /**
     * Encrypts the packet payload and replaces it with the ciphertext.
     */
    fun encrypt(packet: ItantraPacket): ItantraPacket {
        if (_state.value != SecureSessionState.SECURE_VERIFIED) {
            throw IllegalStateException("Cannot encrypt: session is not secure (State: ${_state.value})")
        }
        
        val key = txKey ?: throw IllegalStateException("Missing TX key")
        val prefix = txNoncePrefix ?: throw IllegalStateException("Missing TX nonce prefix")
        
        txCounter++
        val currentCounter = txCounter
        
        val securePacket = packet.copy(
            securityVersion = 1,
            counter = currentCounter
        )
        
        val aad = PacketEncoder.extractAad(securePacket)
        val nonce = constructNonce(prefix, currentCounter)
        
        val t0 = System.nanoTime()
        val ciphertext = CryptoPrimitives.encryptAesGcm(key, nonce, aad, packet.payload)
        encryptDurationUs = (System.nanoTime() - t0) / 1000
        
        return securePacket.copy(payload = ciphertext)
    }

    /**
     * Decrypts and authenticates the packet payload.
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
        
        // Replay Protection
        if (packet.counter <= highestAcceptedRxCounter) {
            replayRejections++
            throw SecurityException("Replay attack detected. Packet counter ${packet.counter} <= $highestAcceptedRxCounter")
        }
        
        val aad = PacketEncoder.extractAad(packet)
        val nonce = constructNonce(prefix, packet.counter)
        
        val t0 = System.nanoTime()
        try {
            val plaintext = CryptoPrimitives.decryptAesGcm(key, nonce, aad, packet.payload)
            decryptDurationUs = (System.nanoTime() - t0) / 1000
            
            // Replay update ONLY after successful auth
            highestAcceptedRxCounter = packet.counter
            
            return packet.copy(
                securityVersion = 0,
                payload = plaintext
            )
        } catch (e: Exception) {
            authFailures++
            throw SecurityException("Packet authentication failed", e)
        }
    }

    fun resetSession() {
        _state.value = SecureSessionState.NO_SESSION
        _sasCode.value = null
        localKeyPair = null
        localNonce = null
        peerPublicKeyBytes = null
        peerNonce = null
        txKey = null
        rxKey = null
        txNoncePrefix = null
        rxNoncePrefix = null
        txCounter = 0
        highestAcceptedRxCounter = -1
    }
}
