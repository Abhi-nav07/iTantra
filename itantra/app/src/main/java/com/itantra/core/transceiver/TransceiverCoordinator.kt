package com.itantra.core.transceiver

import android.os.SystemClock
import android.provider.Settings
import com.itantra.core.audio.SpeakerAudioSink
import com.itantra.core.crypto.SecureSessionManager
import com.itantra.core.translation.TRANSLATION_SCOPE_NOTE
import com.itantra.core.crypto.SecureSessionState
import android.content.Context
import com.itantra.core.inference.ActiveLanguageSessionManager
import com.itantra.core.inference.ContinuousListenEngine
import com.itantra.core.inference.ContinuousListenState
import com.itantra.core.inference.MicrophoneAudioSource
import com.itantra.core.metrics.MetricsRecorder
import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.TransportEngine
import com.itantra.core.transport.packet.ItantraPacket
import com.itantra.core.transport.packet.PacketType
import com.itantra.core.transport.packet.ProtocolLanguageMapper
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.MessageSource
import com.itantra.domain.model.MessageState
import com.itantra.domain.model.SpeechSynthesisRequest
import com.itantra.domain.model.TransceiverMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PeerCapabilities(
    val supportedStt: List<LanguageCode> = emptyList(),
    val supportedTts: List<LanguageCode> = emptyList()
)

class TransceiverCoordinator(
    private val context: Context,
    private val sessionManager: ActiveLanguageSessionManager,
    private val languagePackRepository: com.itantra.domain.repository.LanguagePackRepository,
    private val transportEngine: TransportEngine,
    private val metricsRecorder: MetricsRecorder,
    val secureSessionManager: SecureSessionManager,
    private val translationRouter: com.itantra.core.translation.TranslationRouter,
    private val messageDao: com.itantra.data.db.MessageDao? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val deviceIdSalt: Long by lazy {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "0"
        (androidId.hashCode().toLong() and 0xFFF)
    }

    private fun nextMessageId(): Long =
        (System.currentTimeMillis() shl 12) or deviceIdSalt

    private val _messages = MutableStateFlow<List<TransceiverMessage>>(emptyList())
    val messages: StateFlow<List<TransceiverMessage>> = _messages.asStateFlow()

    init {
        if (messageDao != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val persisted = messageDao.getAll().map { it.toDomain() }
                    if (persisted.isNotEmpty()) {
                        val settled = persisted.map { m ->
                            when {
                                m.priority == com.itantra.domain.model.MessagePriority.CRITICAL -> m
                                m.source == MessageSource.LOCAL && m.state in setOf(
                                    MessageState.RECORDING,
                                    MessageState.STT_PROCESSING,
                                    MessageState.STT_COMPLETE,
                                    MessageState.TRANSMITTING,
                                    MessageState.WAITING_USER_CONFIRMATION
                                ) -> m.copy(
                                    state = MessageState.ERROR,
                                    statusDetail = m.statusDetail ?: "Interrupted - app closed before this was sent"
                                )
                                m.source == MessageSource.REMOTE && m.state in setOf(
                                    MessageState.REMOTE_TTS_READY,
                                    MessageState.REMOTE_PLAYING
                                ) -> m.copy(state = MessageState.DELIVERED)
                                else -> m
                            }
                        }
                        settled.filterIndexed { i, m -> m != persisted[i] }.forEach {
                            dbWriteMutex.withLock {
                                messageDao.insert(com.itantra.data.db.MessageEntity.fromDomain(it))
                            }
                        }
                        val loadedIds = settled.map { it.messageId }.toSet()
                        _messages.value = settled + _messages.value.filter { it.messageId !in loadedIds }
                        updateAlertJob()
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("TransceiverCoord", "Error loading persisted messages", e)
                }
            }
        }
    }

    private val _peerCapabilities = MutableStateFlow(PeerCapabilities())
    val peerCapabilities: StateFlow<PeerCapabilities> = _peerCapabilities.asStateFlow()

    private val messageQueue = mutableListOf<ItantraPacket>()
    private val queueMutex = Mutex()
    private val queueWakeup = Channel<Unit>(Channel.CONFLATED)

    private val sttMutex = Mutex()

    private val dbWriteMutex = Mutex()

    private var cooldownJob: Job? = null
    private var currentTtsJob: Job? = null
    private var currentAudioSink: SpeakerAudioSink? = null
    private var alertJob: Job? = null

    private var isConnected = false
    private var recordingJob: Job? = null
    private val audioSource = MicrophoneAudioSource(scope)
    private var recordingStartTime = 0L
    private var activeRecordingMessageId = 0L
    private val activeRecordingChunks = java.util.Collections.synchronizedList(mutableListOf<FloatArray>())

    val continuousListenEngine = ContinuousListenEngine(context)
    private var continuousModeJob: Job? = null
    private var isTtsPlaying = false

    val emergencyStore = com.itantra.core.emergency.EmergencyPersistenceStore(context.filesDir)
    private val _activeEmergencyAlert = MutableStateFlow<com.itantra.domain.model.EmergencyRecord?>(null)
    val activeEmergencyAlert: StateFlow<com.itantra.domain.model.EmergencyRecord?> = _activeEmergencyAlert.asStateFlow()

    // Bounded deduplication cache (up to 500 entries) scoped to connection/session
    private val seenMessageIds = java.util.Collections.synchronizedSet(
        object : java.util.LinkedHashSet<Long>() {
            override fun add(element: Long): Boolean {
                if (size >= 500) {
                    val it = iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
                return super.add(element)
            }
        }
    )

    private val currentTargetLanguage = MutableStateFlow<LanguageCode?>(null)

    @Volatile
    internal var debugBeepWhenNoVoice: Boolean = false

    /**
     * Resolves which language an outgoing message should be translated into.
     *
     * Previously this fell back to a hardcoded Hindi<->English binary guess
     * (`if (source == HINDI) ENGLISH else HINDI`) whenever the user hadn't
     * explicitly picked a target in settings - meaning for 8 of the 10
     * supported languages, an unconfigured session would silently default
     * to Hindi rather than the language the connected peer actually speaks.
     *
     * We now prefer, in order:
     *  1. An explicit target the user picked (`currentTargetLanguage`).
     *  2. The peer's own advertised language, learned from the CAPABILITIES
     *     handshake (`peerCapabilities`) - this is real data we already
     *     collect but weren't using for this decision.
     *  3. The old Hindi<->English guess, only as a last resort (e.g. before
     *     a handshake has completed).
     */
    private fun resolveTargetLanguage(sourceLanguage: LanguageCode): LanguageCode {
        currentTargetLanguage.value?.let { return it }

        // 1. If peer advertises support for the same language, route same-language
        if (_peerCapabilities.value.supportedTts.contains(sourceLanguage)) {
            return sourceLanguage
        }

        // 2. If peer advertises a different language, route to that peer's language
        val peerDifferent = _peerCapabilities.value.supportedTts.firstOrNull { it != sourceLanguage }
        if (peerDifferent != null) return peerDifferent

        // 3. Pass 3 default: Same-Language Routing First
        return sourceLanguage
    }

    init {
        // Reload unresolved emergency state from durable persistence on initialization/restart.
        // Origin-aware restoration: only REMOTE emergencies trigger audio/alarm restoration on restart.
        // LOCAL unresolved emergencies (sent from this device) must NOT trigger the sender's own alarm.
        val unresolved = emergencyStore.getUnresolvedRecords()
        val remoteUnresolved = unresolved.filter { it.source == "REMOTE" }
        if (remoteUnresolved.isNotEmpty()) {
            val lastRemoteUnresolved = remoteUnresolved.last()
            _activeEmergencyAlert.value = lastRemoteUnresolved
            com.itantra.core.service.OperationalForegroundService.triggerEmergency(context, lastRemoteUnresolved.resolvedPhrase)
        }

        scope.launch {
            transportEngine.observeConnectionState().collect { state ->
                val connected = state == ConnectionState.CONNECTED
                if (connected && !isConnected) {
                    isConnected = true
                    // Start handshake if connected. Only the client (initiator) sends the first HELLO.
                    // Responder remains in NO_SESSION, ready to process incoming SECURE_HELLO.
                    val isInitiator = !transportEngine.isServer
                    if (isInitiator) {
                        val hello = secureSessionManager.startHandshake(isInitiator = true)
                        transportEngine.send(hello)
                    }
                } else if (!connected && isConnected) {
                    isConnected = false
                    _peerCapabilities.value = PeerCapabilities()
                    secureSessionManager.resetSession()
                    seenMessageIds.clear()
                }
            }
        }

        scope.launch {
            sessionManager.activeLanguage.collect { lang ->
                if (isConnected && lang != null && secureSessionManager.state.value == SecureSessionState.SECURE_VERIFIED) {
                    sendCapabilities()
                }
            }
        }

        scope.launch {
            languagePackRepository.observeTargetLanguage().collect { lang ->
                currentTargetLanguage.value = lang
            }
        }

        scope.launch {
            transportEngine.receive().collect { packet ->
                handleIncomingPacket(packet)
            }
        }

        scope.launch {
            while(true) {
                val nextPacket = queueMutex.withLock {
                    if (messageQueue.isEmpty()) null
                    else {
                        // Priority ordering (descending), then FIFO
                        messageQueue.sortByDescending { it.flags.toInt() }
                        messageQueue.removeAt(0)
                    }
                }
                if (nextPacket != null) {
                    currentTtsJob = scope.launch {
                        processIncomingMessagePacket(nextPacket)
                    }
                    currentTtsJob?.join()
                    currentTtsJob = null
                } else {
                    queueWakeup.receive() // wait for signal
                }
            }
        }
    }

    private fun addMessage(msg: TransceiverMessage) {
        _messages.value = _messages.value + msg
        updateAlertJob()
        if (messageDao != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    dbWriteMutex.withLock {
                        messageDao.insert(com.itantra.data.db.MessageEntity.fromDomain(msg))
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("TransceiverCoord", "Error persisting message", e)
                }
            }
        }
    }

    private fun updateMessage(id: Long, update: (TransceiverMessage) -> TransceiverMessage) {
        var updatedMsg: TransceiverMessage? = null
        _messages.value = _messages.value.map {
            if (it.messageId == id) {
                val u = update(it)
                updatedMsg = u
                u
            } else it
        }
        updateAlertJob()
        if (messageDao != null && updatedMsg != null) {
            val u = updatedMsg!!
            scope.launch(Dispatchers.IO) {
                try {
                    dbWriteMutex.withLock {
                        messageDao.insert(com.itantra.data.db.MessageEntity.fromDomain(u))
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("TransceiverCoord", "Error updating persisted message", e)
                }
            }
        }
    }

    private fun updateAlertJob() {
        val hasUnack = _messages.value.any { it.source == MessageSource.REMOTE && it.priority == com.itantra.domain.model.MessagePriority.CRITICAL && it.state != MessageState.ACKNOWLEDGED }
        if (hasUnack && alertJob == null) {
            alertJob = scope.launch {
                var count = 0
                while (count < 3) {
                    delay(10_000)
                    val unack = _messages.value.filter { it.source == MessageSource.REMOTE && it.priority == com.itantra.domain.model.MessagePriority.CRITICAL && it.state != MessageState.ACKNOWLEDGED }
                    if (unack.isEmpty()) break
                    val msg = unack.first()
                    try {
                        cooldownJob?.cancel()
                        isTtsPlaying = true
                        continuousListenEngine.pauseListening()

                        val text = "Attention. " + msg.text
                        val req = SpeechSynthesisRequest(msg.language ?: LanguageCode.ENGLISH, text, "alert")
                        val res = sessionManager.currentTtsEngine?.synthesize(req)
                        if (res != null) {
                            val sink = SpeakerAudioSink(context)
                            sink.init(res.sampleRateHz, android.media.AudioAttributes.USAGE_ALARM, requestMaxVolume = true)
                            sink.play(res.pcmAudio)
                            sink.flushAndStop()
                            sink.release()
                        }
                    } catch (e: Exception) {}
                    finally {
                        cooldownJob = scope.launch {
                            delay(300)
                            isTtsPlaying = false
                            if (continuousModeJob?.isActive == true) {
                                continuousListenEngine.resetAndResume()
                            }
                        }
                    }
                    count++
                }
                alertJob = null
            }
        } else if (!hasUnack && alertJob != null) {
            alertJob?.cancel()
            alertJob = null
        }
    }

    private suspend fun sendCapabilities() {
        if (secureSessionManager.state.value != SecureSessionState.SECURE_VERIFIED) return

        val sttLangs = listOfNotNull(sessionManager.activeLanguage.value)
        val ttsLangs = listOfNotNull(sessionManager.activeLanguage.value)

        val sttMask = ProtocolLanguageMapper.toBitmask(sttLangs)
        val ttsMask = ProtocolLanguageMapper.toBitmask(ttsLangs)

        val payload = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putShort(sttMask)
            .putShort(ttsMask)
            .array()

        val packet = ItantraPacket(
            type = PacketType.CAPABILITIES,
            messageId = nextMessageId(),
            payload = payload
        )
        try {
            val securePacket = secureSessionManager.encrypt(packet)
            transportEngine.send(securePacket)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    internal fun handleIncomingPacket(packet: ItantraPacket) {
        if (packet.type == PacketType.SECURE_HELLO) {
            val response = secureSessionManager.processSecureHello(packet)
            if (response != null) {
                scope.launch { transportEngine.send(response) }
            }
            return
        }

        if (packet.type == PacketType.SECURE_VERIFY) {
            secureSessionManager.processSecureVerify(packet)
            if (secureSessionManager.state.value == SecureSessionState.SECURE_VERIFIED) {
                scope.launch {
                    sendCapabilities()
                    retryUnresolvedEmergencies()
                }
            }
            return
        }

        // Only process application traffic if session is secure
        if (secureSessionManager.state.value != SecureSessionState.SECURE_VERIFIED) {
            // Drop packet before both parties confirm SAS
            return
        }

        val decryptedPacket = try {
            secureSessionManager.decrypt(packet)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        when (decryptedPacket.type) {
            PacketType.ACK -> {
                transportEngine.notifyAckReceived(decryptedPacket.messageId)
                updateMessage(decryptedPacket.messageId) {
                    it.copy(state = MessageState.DELIVERED)
                }
                emergencyStore.recordTransportAck(decryptedPacket.messageId)
            }
            PacketType.CAPABILITIES -> {
                if (decryptedPacket.payload.size >= 4) {
                    val buffer = ByteBuffer.wrap(decryptedPacket.payload).order(ByteOrder.BIG_ENDIAN)
                    val sttMask = buffer.short
                    val ttsMask = buffer.short
                    _peerCapabilities.value = PeerCapabilities(
                        supportedStt = ProtocolLanguageMapper.fromBitmask(sttMask),
                        supportedTts = ProtocolLanguageMapper.fromBitmask(ttsMask)
                    )
                }
            }
            PacketType.TEXT, PacketType.EMERGENCY_CODE -> {
                // Immediate ACK through established secure session
                scope.launch {
                    try {
                        val ackPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.ACK, messageId = decryptedPacket.messageId))
                        transportEngine.send(ackPkt)
                    } catch (e: Exception) {
                        android.util.Log.w("TransceiverCoord", "Could not send immediate ACK: ${e.message}")
                    }
                }

                val isDuplicate = seenMessageIds.contains(decryptedPacket.messageId) ||
                        _messages.value.any { it.messageId == decryptedPacket.messageId && it.source == MessageSource.REMOTE }

                if (isDuplicate) {
                    // Re-ACK has already been transmitted above. Drop duplicate without processing twice.
                    return
                }
                seenMessageIds.add(decryptedPacket.messageId)

                // Dedicated ALL_CLEAR handling:
                // Terminal control packet that resolves emergency, stops siren immediately, and returns without creating EmergencyRecord.
                if (decryptedPacket.type == PacketType.EMERGENCY_CODE &&
                    decryptedPacket.payload.isNotEmpty() &&
                    decryptedPacket.payload[0] == com.itantra.domain.model.EmergencyCode.ALL_CLEAR.id) {

                    alertJob?.cancel()
                    alertJob = null
                    isTtsPlaying = false
                    currentTtsJob?.cancel()
                    currentAudioSink?.stopImmediate()
                    currentAudioSink?.release()
                    currentAudioSink = null

                    _activeEmergencyAlert.value = null
                    com.itantra.core.service.OperationalForegroundService.resolveEmergency(context)
                    emergencyStore.resolveAllEmergencies()

                    if (continuousModeJob?.isActive == true) {
                        continuousListenEngine.resetAndResume()
                    }

                    val allClearMsg = TransceiverMessage(
                        messageId = decryptedPacket.messageId,
                        language = sessionManager.activeLanguage.value ?: LanguageCode.ENGLISH,
                        priority = com.itantra.domain.model.MessagePriority.NORMAL,
                        text = "ALL CLEAR — Emergency Resolved",
                        source = MessageSource.REMOTE,
                        createdAtLocal = System.currentTimeMillis(),
                        state = MessageState.DELIVERED
                    )
                    addMessage(allClearMsg)
                    return // Terminal return: do not enqueue into messageQueue, do not build EmergencyRecord!
                }

                enqueueMessagePacketForPlayback(decryptedPacket)
            }
            PacketType.HUMAN_ACK -> {
                updateMessage(decryptedPacket.messageId) {
                    it.copy(state = MessageState.ACKNOWLEDGED)
                }
                emergencyStore.recordHumanAck(decryptedPacket.messageId)
                if (_activeEmergencyAlert.value?.messageId == decryptedPacket.messageId) {
                    _activeEmergencyAlert.value = null
                    com.itantra.core.service.OperationalForegroundService.resolveEmergency(context)
                }
            }
            PacketType.TTS_STARTED -> {
                updateMessage(decryptedPacket.messageId) {
                    val rasc = (System.currentTimeMillis() - it.createdAtLocal).coerceAtLeast(0L)
                    it.copy(state = MessageState.REMOTE_PLAYING, remoteAudioStartConfMillis = rasc)
                }
            }
            PacketType.TTS_COMPLETED -> {
                var ttfa = 0L
                if (decryptedPacket.payload.size == 8) {
                    ttfa = ByteBuffer.wrap(decryptedPacket.payload).order(ByteOrder.BIG_ENDIAN).long
                }
                updateMessage(decryptedPacket.messageId) {
                    val fullEstimatedE2e = it.sttLatencyMillis + it.mtLatencyMillis + it.cryptoLatencyMillis + (it.rttMillis / 2) + ttfa
                    // Pass 4: Do not substitute an estimate into MetricsRecorder when cross-device start/end events do not exist.
                    it.copy(
                        state = MessageState.REMOTE_PLAYBACK_CONFIRMED,
                        peerTtfaMillis = ttfa,
                        estimatedE2eMillis = fullEstimatedE2e
                    )
                }
            }
            PacketType.TTS_FAILED -> {
                updateMessage(decryptedPacket.messageId) {
                    it.copy(state = MessageState.ERROR)
                }
            }
            else -> {}
        }
    }

    internal fun enqueueMessagePacketForPlayback(packet: ItantraPacket) {
        scope.launch {
            queueMutex.withLock {
                messageQueue.add(packet)
            }
            if (packet.flags.toInt() == com.itantra.domain.model.MessagePriority.CRITICAL || packet.type == PacketType.EMERGENCY_CODE) {
                currentTtsJob?.cancel()
                currentAudioSink?.stopImmediate()
                currentAudioSink?.release()
                currentAudioSink = null
            }
            queueWakeup.trySend(Unit)
        }
    }

    private suspend fun processIncomingMessagePacket(packet: ItantraPacket) {
        val isEmergencyCode = packet.type == PacketType.EMERGENCY_CODE
        val localLanguage = sessionManager.activeLanguage.value ?: LanguageCode.ENGLISH
        var text = if (isEmergencyCode && packet.payload.isNotEmpty()) {
            val code = com.itantra.domain.model.EmergencyCode.fromId(packet.payload[0])
            // Emergency code bypasses MT and resolves directly into receiver's active local language (Section N)
            if (code != null) com.itantra.domain.model.EmergencyPhraseResolver.resolve(code, localLanguage) else "Unknown Emergency"
        } else {
            String(packet.payload, Charsets.UTF_8)
        }

        val pktLang = packet.targetLanguage ?: packet.languageCode ?: localLanguage
        var textLanguage = if (isEmergencyCode) localLanguage else pktLang

        if (!isEmergencyCode) {
            if (pktLang == localLanguage) {
                // Direct TTS: Same-language packet routes directly without translation.
                textLanguage = localLanguage
            } else {
                // Pass 3: Cross-language translation is deferred.
                // Do NOT translate, do NOT silently change language, do NOT fallback to English.
                android.util.Log.i("TransceiverCoord", "CROSS_LANGUAGE_DEFERRED: packet language $pktLang != local $localLanguage. Translation bypassed, transcript preserved.")
            }
        }

        val msg = TransceiverMessage(
            messageId = packet.messageId,
            language = packet.languageCode,
            priority = packet.flags.toInt(),
            text = text,
            source = MessageSource.REMOTE,
            createdAtLocal = System.currentTimeMillis(),
            state = MessageState.DELIVERED
        )
        addMessage(msg)

        if (isEmergencyCode) {
            val code = if (packet.payload.isNotEmpty()) com.itantra.domain.model.EmergencyCode.fromId(packet.payload[0]) else null
            val emergencyRec = com.itantra.domain.model.EmergencyRecord(
                messageId = packet.messageId,
                emergencyCode = code?.name ?: "UNKNOWN",
                source = "REMOTE",
                target = "LOCAL",
                createdAt = System.currentTimeMillis(),
                retryStatus = com.itantra.domain.model.EmergencyRetryStatus.PENDING,
                humanAckStatus = false,
                resolvedPhrase = text
            )
            emergencyStore.saveRecord(emergencyRec)
            _activeEmergencyAlert.value = emergencyRec
            com.itantra.core.service.OperationalForegroundService.triggerEmergency(context, text)
        } else if (_activeEmergencyAlert.value != null) {
            // Application-level non-interruptible: normal message is delivered to history,
            // but does NOT preempt active emergency audio alert or clear alert state
            return
        }

        val engine = sessionManager.currentTtsEngine
        val isCritical = packet.flags.toInt() == com.itantra.domain.model.MessagePriority.CRITICAL || isEmergencyCode

        val canSpeak = engine != null && engine.isLoaded && engine.languageCode == textLanguage

        if (!canSpeak && !isCritical && !debugBeepWhenNoVoice) {
            updateMessage(msg.messageId) {
                it.copy(state = MessageState.DELIVERED, statusDetail = com.itantra.domain.model.VOICE_OUTPUT_UNAVAILABLE_NOTE)
            }
            return
        }

        if (secureSessionManager.state.value == SecureSessionState.SECURE_VERIFIED) {
            try {
                val startedPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.TTS_STARTED, messageId = packet.messageId))
                scope.launch { transportEngine.send(startedPkt) }
            } catch (e: Exception) {
                android.util.Log.w("TransceiverCoord", "Could not send TTS_STARTED: ${e.message}")
            }
        }
        updateMessage(msg.messageId) { it.copy(state = MessageState.REMOTE_PLAYING) }

        // TTS Suppression Rule
        cooldownJob?.cancel()
        isTtsPlaying = true
        continuousListenEngine.pauseListening()

        try {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val pcmAudio: FloatArray
            val sampleRate: Int

            if (engine != null && canSpeak) {
                val req = SpeechSynthesisRequest(
                    languageCode = engine.languageCode,
                    text = text,
                    correlationId = msg.messageId.toString()
                )
                val result = engine.synthesize(req)
                pcmAudio = result.pcmAudio
                sampleRate = result.sampleRateHz
            } else if (isCritical) {
                // Safety-critical emergency tone: generate high-penetration multi-tone alarm PCM (800Hz / 1000Hz alternating warble)
                // Guaranteed audible on device speaker regardless of voice pack status
                sampleRate = 16000
                val durationSeconds = 2.0
                val numSamples = (sampleRate * durationSeconds).toInt()
                pcmAudio = FloatArray(numSamples) { i ->
                    val freq = if ((i / 4000) % 2 == 0) 800.0 else 1000.0
                    (Math.sin(2.0 * Math.PI * freq * i / sampleRate) * 0.7).toFloat()
                }
            } else {
                // Debug-only placeholder tone
                sampleRate = 16000
                val durationSeconds = 3.0
                val numSamples = (sampleRate * durationSeconds).toInt()
                pcmAudio = FloatArray(numSamples) { i ->
                    (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 0.3).toFloat()
                }
            }

            val ttfaMillis = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000

            val sink = SpeakerAudioSink(context)
            currentAudioSink = sink
            val usage = if (isCritical) {
                android.media.AudioAttributes.USAGE_ALARM
            } else {
                android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
            }
            sink.init(sampleRate, usage, requestMaxVolume = isCritical)
            sink.play(pcmAudio)
            sink.flushAndStop()

            updateMessage(msg.messageId) { it.copy(state = MessageState.REMOTE_PLAYBACK_CONFIRMED, peerTtfaMillis = ttfaMillis) }

            val payloadBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(ttfaMillis).array()
            if (secureSessionManager.state.value == SecureSessionState.SECURE_VERIFIED) {
                try {
                    val compPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.TTS_COMPLETED, messageId = packet.messageId, payload = payloadBytes))
                    scope.launch { transportEngine.send(compPkt) }
                } catch (e: Exception) {
                    android.util.Log.w("TransceiverCoord", "Could not send TTS_COMPLETED: ${e.message}")
                }
            }

        } catch (e: kotlinx.coroutines.CancellationException) {
            // Interrupted by preemption
            updateMessage(msg.messageId) { it.copy(state = MessageState.ERROR, text = it.text + " (Interrupted)") }
        } catch (e: Exception) {
            e.printStackTrace()
            updateMessage(msg.messageId) { it.copy(state = MessageState.ERROR) }
            if (secureSessionManager.state.value == SecureSessionState.SECURE_VERIFIED) {
                try {
                    val failPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.TTS_FAILED, messageId = packet.messageId))
                    scope.launch { transportEngine.send(failPkt) }
                } catch (ex: Exception) {
                    android.util.Log.w("TransceiverCoord", "Could not send TTS_FAILED: ${ex.message}")
                }
            }
        } finally {
            currentAudioSink?.release()
            currentAudioSink = null

            // Resume VAD after TTS
            cooldownJob = scope.launch {
                delay(300)
                isTtsPlaying = false
                if (continuousModeJob?.isActive == true) {
                    continuousListenEngine.resetAndResume()
                }
            }
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        if (enabled) {
            if (continuousModeJob != null) return
            com.itantra.core.service.OperationalForegroundService.startContinuous(context)
            continuousListenEngine.start()
            continuousModeJob = scope.launch {
                launch {
                    try {
                        audioSource.stream.collect { samples ->
                            if (!isTtsPlaying) {
                                continuousListenEngine.feedAudio(samples)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                launch {
                    continuousListenEngine.segmentEvents.collect { seg ->
                        processContinuousSegment(seg.samples)
                    }
                }
            }
        } else {
            com.itantra.core.service.OperationalForegroundService.stopContinuous(context)
            continuousModeJob?.cancel()
            continuousModeJob = null
            continuousListenEngine.stop()
        }
    }

    private fun processContinuousSegment(audio: FloatArray) {
        val durationS = audio.size / 16000.0
        metricsRecorder.recordVadSegment(durationS, audio.size)

        val engine = sessionManager.currentSttEngine
        if (engine == null || !engine.isLoaded) {
            continuousListenEngine.stop()
            val lastMsg = _messages.value.lastOrNull()
            if (lastMsg != null && lastMsg.state == MessageState.ERROR && lastMsg.text == "STT Pack Required") {
                return
            }
            val msgId = nextMessageId()
            val targetLang = resolveTargetLanguage(sessionManager.activeLanguage.value ?: LanguageCode.HINDI)
            val msg = TransceiverMessage(
                messageId = msgId,
                language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
                targetLanguage = targetLang,
                priority = com.itantra.domain.model.MessagePriority.NORMAL,
                text = "STT Pack Required",
                source = MessageSource.LOCAL,
                createdAtLocal = System.currentTimeMillis(),
                state = MessageState.ERROR
            )
            addMessage(msg)
            return
        }

        continuousListenEngine.pauseListening()

        val msgId = nextMessageId()
        val targetLang = resolveTargetLanguage(sessionManager.activeLanguage.value ?: LanguageCode.HINDI)
        val msg = TransceiverMessage(
            messageId = msgId,
            language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
            targetLanguage = targetLang,
            priority = com.itantra.domain.model.MessagePriority.NORMAL,
            text = "Recognizing...",
            source = MessageSource.LOCAL,
            createdAtLocal = System.currentTimeMillis(),
            state = MessageState.STT_PROCESSING
        )
        addMessage(msg)

        scope.launch {
            sttMutex.withLock {
                try {
                    engine.feed(audio)
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    val result = engine.finalizeUtterance()
                    val latencyMillis = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                    val durationMillis = (audio.size.toLong() * 1000) / 16000

                    metricsRecorder.recordSttInferenceTime(latencyMillis)
                    metricsRecorder.recordSttEndpointToFinalText(latencyMillis)
                    metricsRecorder.recordSttAudioDuration(durationMillis)

                    if (result.text.isBlank()) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Recognition produced no text.") }
                        return@launch
                    }

                    if (secureSessionManager.state.value != SecureSessionState.SECURE_VERIFIED) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = result.text, statusDetail = "Secure Link Required") }
                        return@launch
                    }

                    if (!isConnected) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = result.text, statusDetail = "Peer disconnected") }
                        return@launch
                    }

                    val srcLang = sessionManager.activeLanguage.value ?: LanguageCode.HINDI
                    // Was `currentTargetLanguage.value` (nullable, skips translation entirely
                    // when unset). Now resolves via the peer's advertised language first, so an
                    // unconfigured session still translates into what the peer can actually
                    // read instead of silently sending untranslated text.
                    val targetLang = resolveTargetLanguage(srcLang)
                    var finalTxt = result.text
                    var origTxt: String? = null
                    var translationStatus = com.itantra.domain.model.TranslationStatus.BYPASSED
                    var failureDetail: String? = null

                    // No "Translating..." interim flash here: translation is either genuinely
                    // fast (real engine) or immediate (UnavailableTranslationEngine's instant
                    // no-op) - either way the flash added a moment that looked like the app
                    // was struggling with something, when there's nothing to wait on.
                    if (targetLang != srcLang) {
                        val translationRes = translationRouter.routeAndTranslate(result.text, srcLang, targetLang)
                        if (translationRes.isSuccessful && translationRes.translatedText.isNotBlank()) {
                            finalTxt = translationRes.translatedText
                            origTxt = result.text
                            translationStatus = com.itantra.domain.model.TranslationStatus.SUCCESS
                        } else {
                            // Deliberate scope decision, not a failure: fall through and send
                            // the transcript as-is rather than blocking transmission entirely.
                            translationStatus = com.itantra.domain.model.TranslationStatus.FAILED
                            failureDetail = if (translationRes.error == "NOT_INCLUDED_IN_BUILD") {
                                TRANSLATION_SCOPE_NOTE
                            } else {
                                "Translation failed: ${translationRes.error ?: "Unknown"}"
                            }
                            finalTxt = result.text
                            origTxt = null
                        }
                    }

                    val payload = finalTxt.toByteArray(Charsets.UTF_8)
                    val rawPcmEq = (durationMillis * 16000 * 2) / 1000

                    updateMessage(msgId) {
                        it.copy(
                            state = MessageState.STT_COMPLETE,
                            text = finalTxt,
                            originalText = origTxt,
                            translationStatus = translationStatus,
                            statusDetail = failureDetail,
                            sttLatencyMillis = latencyMillis,
                            payloadBytes = payload.size,
                            rawPcmEquivalentBytes = rawPcmEq.toInt(),
                            speechDurationMillis = durationMillis
                        )
                    }

                    sendVoiceMessage(msgId, com.itantra.domain.model.MessagePriority.NORMAL)
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Error: ${e.message}") }
                } finally {
                    if (!isTtsPlaying && continuousModeJob?.isActive == true) {
                        continuousListenEngine.resumeListening()
                    }
                }
            }
        }
    }

    fun startRecording(isCritical: Boolean = false) {
        if (recordingJob != null) return
        val engine = sessionManager.currentSttEngine
        if (engine == null || !engine.isLoaded) {
            val lastMsg = _messages.value.lastOrNull()
            if (lastMsg != null && lastMsg.state == MessageState.ERROR && lastMsg.text == "STT Pack Required") {
                return
            }
            val msgId = nextMessageId()
            addMessage(
                TransceiverMessage(
                    messageId = msgId,
                    language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
                    targetLanguage = currentTargetLanguage.value,
                    priority = 0,
                    text = "STT Pack Required",
                    source = MessageSource.LOCAL,
                    createdAtLocal = System.currentTimeMillis(),
                    state = MessageState.ERROR
                )
            )
            return
        }

        activeRecordingChunks.clear()
        recordingStartTime = SystemClock.elapsedRealtime()
        val msgId = nextMessageId()
        activeRecordingMessageId = msgId

        if (continuousModeJob?.isActive == true) {
            continuousListenEngine.pauseListening()
        }

        val targetLang = resolveTargetLanguage(engine.languageCode)
        val msg = TransceiverMessage(
            messageId = msgId,
            language = engine.languageCode,
            targetLanguage = targetLang,
            priority = if (isCritical) com.itantra.domain.model.MessagePriority.CRITICAL else com.itantra.domain.model.MessagePriority.NORMAL,
            text = "Listening...",
            source = MessageSource.LOCAL,
            createdAtLocal = System.currentTimeMillis(),
            state = MessageState.RECORDING
        )
        addMessage(msg)

        recordingJob = scope.launch {
            launch {
                delay(60_000L) // 60s max
                stopRecording(msgId)
            }
            try {
                audioSource.stream.collect { samples ->
                    if (samples.isNotEmpty()) {
                        activeRecordingChunks.add(samples.clone())
                        engine.feed(samples)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun processTestAudio(samples: FloatArray) {
        val engine = sessionManager.currentSttEngine ?: return
        val msgId = nextMessageId()
        val targetLang = resolveTargetLanguage(engine.languageCode)
        val msg = TransceiverMessage(
            messageId = msgId,
            language = engine.languageCode,
            targetLanguage = targetLang,
            priority = com.itantra.domain.model.MessagePriority.NORMAL,
            text = "Recognizing...",
            source = MessageSource.LOCAL,
            createdAtLocal = System.currentTimeMillis(),
            state = MessageState.STT_PROCESSING
        )
        addMessage(msg)

        synchronized(activeRecordingChunks) {
            activeRecordingChunks.clear()
            activeRecordingChunks.add(samples)
        }
        recordingStartTime = SystemClock.elapsedRealtime()
        activeRecordingMessageId = msgId
        recordingJob = scope.launch {
            sttMutex.withLock {
                engine.reset()
                engine.feed(samples)
            }
            stopRecording(msgId)
        }
    }

    fun stopActiveRecording() {
        if (activeRecordingMessageId != 0L) {
            stopRecording(activeRecordingMessageId)
        }
    }

    fun stopRecording(msgId: Long) {
        if (recordingJob == null) return
        recordingJob?.cancel()
        recordingJob = null

        val engine = sessionManager.currentSttEngine ?: return

        val chunksSnapshot = synchronized(activeRecordingChunks) {
            val copy = activeRecordingChunks.toList()
            activeRecordingChunks.clear()
            copy
        }

        val totalSamples = chunksSnapshot.sumOf { it.size }
        val durationMillis = if (totalSamples > 0) {
            (totalSamples * 1000L) / 16000L
        } else {
            SystemClock.elapsedRealtime() - recordingStartTime
        }

        // Save raw audio to debug WAV file for inspection (gated behind BuildConfig.DEBUG)
        if (com.example.itantra.BuildConfig.DEBUG && totalSamples > 0) {
            try {
                val fullPcm = FloatArray(totalSamples)
                var offset = 0
                for (chunk in chunksSnapshot) {
                    System.arraycopy(chunk, 0, fullPcm, offset, chunk.size)
                    offset += chunk.size
                }
                val extWav = java.io.File(context.getExternalFilesDir(null), "debug_ptt.wav")
                val cacheWav = java.io.File(context.cacheDir, "debug_ptt.wav")
                com.itantra.core.audio.WavWriter.writeWavFile(extWav, fullPcm, 16000, 1)
                com.itantra.core.audio.WavWriter.writeWavFile(cacheWav, fullPcm, 16000, 1)
                android.util.Log.i("TransceiverCoordinator", "Saved debug_ptt.wav: $totalSamples samples (${durationMillis}ms) to ${extWav.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("TransceiverCoordinator", "Error saving debug_ptt.wav", e)
            }
        }

        var sumSquares = 0.0
        var totalSamplesCount = 0
        for (chunk in chunksSnapshot) {
            for (s in chunk) {
                sumSquares += (s * s)
                totalSamplesCount++
            }
        }
        val rms = if (totalSamplesCount > 0) Math.sqrt(sumSquares / totalSamplesCount).toFloat() else 0f
        val rmsDbfs = if (rms > 1e-9f) (20.0 * Math.log10(rms.toDouble())).toFloat() else -100f
        android.util.Log.i("TransceiverCoordinator", "VAD RMS check: samples=$totalSamplesCount, rms=$rms (${rmsDbfs} dBFS)")

        if (rms < 0.003f) {
            android.util.Log.i("TransceiverCoordinator", "Silence/low-energy detected (RMS=$rms, ${rmsDbfs} dBFS < 0.003 threshold). Short-circuiting STT.")
            scope.launch {
                sttMutex.withLock { engine.reset() }
                if (continuousModeJob?.isActive == true && !isTtsPlaying) {
                    continuousListenEngine.resetAndResume()
                }
            }
            updateMessage(msgId) {
                it.copy(
                    state = MessageState.ERROR,
                    text = "No speech detected",
                    speechDurationMillis = durationMillis,
                    statusDetail = "Silence / Energy below threshold"
                )
            }
            return
        }

        // Bumped from 200ms to 300ms - accidental taps/brushes on the PTT button were still
        // occasionally clearing this bar and reaching STT with near-empty audio.
        if (durationMillis < 300) {
            scope.launch {
                sttMutex.withLock { engine.reset() }
                if (continuousModeJob?.isActive == true && !isTtsPlaying) {
                    continuousListenEngine.resetAndResume()
                }
            }
            // Cleanly discard micro-tap without leaving persistent error card in UI
            _messages.value = _messages.value.filter { it.messageId != msgId }
            if (messageDao != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        messageDao.delete(msgId)
                    } catch (e: Throwable) {
                        // ignore
                    }
                }
            }
            return
        }

        updateMessage(msgId) { it.copy(state = MessageState.STT_PROCESSING, text = "Finalizing...") }

        scope.launch {
            sttMutex.withLock {
                try {
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    val result = engine.finalizeUtterance()
                    val latencyMillis = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000

                    // Preserved: full finalizeUtterance() latency (buffering + padding + decode) for end-to-end latency
                    metricsRecorder.recordSttInferenceTime(latencyMillis)
                    metricsRecorder.recordSttEndpointToFinalText(latencyMillis)
                    metricsRecorder.recordSttAudioDuration(durationMillis)

                    // Isolated: decode-only time used exclusively for STT RTF calculation
                    metricsRecorder.recordSttPureInferenceTime(result.pureInferenceMs)
                    if (durationMillis > 0) {
                        val rtf = result.pureInferenceMs.toDouble() / durationMillis.toDouble()
                        metricsRecorder.recordSttRealTimeFactor(rtf)
                    }

                    if (result.text.isBlank()) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Speech recognition failed.") }
                        return@withLock
                    }

                    val msg = _messages.value.find { it.messageId == msgId } ?: return@withLock

                    val priorityInt = if (msg.priority == com.itantra.domain.model.MessagePriority.CRITICAL) {
                        com.itantra.domain.model.MessagePriority.CRITICAL
                    } else {
                        com.itantra.domain.model.MessagePriority.NORMAL
                    }

                    val targetLang = msg.targetLanguage ?: resolveTargetLanguage(msg.language ?: LanguageCode.HINDI)
                    val srcLang = msg.language ?: LanguageCode.HINDI
                    var finalTxt = result.text
                    var origTxt: String? = null
                    var translationStatus = com.itantra.domain.model.TranslationStatus.BYPASSED
                    var failureDetail: String? = null

                    var mtLatency = 0L
                    android.util.Log.i("ITANTRA_MT_CALL", "Evaluating MT condition: targetLang=$targetLang, srcLang=$srcLang, msgId=$msgId")
                    if (targetLang != srcLang) {
                        // No "Translating..." interim flash: with UnavailableTranslationEngine
                        // the result is immediate, and with a real engine the STT_PROCESSING
                        // state the message is already in covers the wait without implying a
                        // distinct, currently-failing "translating" step.
                        if (com.example.itantra.BuildConfig.DEBUG) android.util.Log.i("ITANTRA_MT_CALL", "BEFORE translation call: text='${result.text}', srcLang=$srcLang, targetLang=$targetLang")
                        val tMt0 = SystemClock.elapsedRealtimeNanos()
                        val translationRes = translationRouter.routeAndTranslate(result.text, srcLang, targetLang)
                        if (com.example.itantra.BuildConfig.DEBUG) android.util.Log.i("ITANTRA_MT_CALL", "AFTER translation call: isSuccessful=${translationRes.isSuccessful}, translated='${translationRes.translatedText}', error=${translationRes.error}")
                        mtLatency = (SystemClock.elapsedRealtimeNanos() - tMt0) / 1_000_000

                        if (translationRes.isSuccessful && translationRes.translatedText.isNotBlank()) {
                            finalTxt = translationRes.translatedText
                            origTxt = result.text
                            translationStatus = com.itantra.domain.model.TranslationStatus.SUCCESS
                        } else {
                            // Deliberate scope decision, not a failure - see
                            // UnavailableTranslationEngine's doc comment.
                            translationStatus = com.itantra.domain.model.TranslationStatus.FAILED
                            failureDetail = if (translationRes.error == "NOT_INCLUDED_IN_BUILD" || translationRes.error == "ENGINE_NOT_LOADED") {
                                TRANSLATION_SCOPE_NOTE
                            } else {
                                "Translation failed: ${translationRes.error ?: "Unknown"}"
                            }
                            finalTxt = result.text
                            origTxt = null
                        }
                    }

                    val payload = finalTxt.toByteArray(Charsets.UTF_8)
                    val rawPcmEq = (durationMillis * 16000 * 2) / 1000

                    updateMessage(msgId) {
                        it.copy(
                            state = MessageState.STT_COMPLETE,
                            text = finalTxt,
                            originalText = origTxt,
                            translationStatus = translationStatus,
                            sttLatencyMillis = latencyMillis,
                            mtLatencyMillis = mtLatency,
                            payloadBytes = payload.size,
                            rawPcmEquivalentBytes = rawPcmEq.toInt(),
                            speechDurationMillis = durationMillis,
                            statusDetail = failureDetail
                        )
                    }

                    if (isConnected) {
                        if (priorityInt == com.itantra.domain.model.MessagePriority.CRITICAL) {
                            updateMessage(msgId) { it.copy(state = MessageState.WAITING_USER_CONFIRMATION) }
                        } else {
                            sendVoiceMessage(msgId)
                        }
                    } else {
                        updateMessage(msgId) {
                            it.copy(
                                state = MessageState.ERROR,
                                text = finalTxt,
                                statusDetail = failureDetail ?: "Peer disconnected"
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Error: ${e.message}") }
                } finally {
                    if (continuousModeJob?.isActive == true && !isTtsPlaying) {
                        continuousListenEngine.resetAndResume()
                    }
                }
            }
        }
    }

    fun cancelMessage(msgId: Long) {
        updateMessage(msgId) { it.copy(state = MessageState.ERROR, statusDetail = "Cancelled") }
    }

    fun sendVoiceMessage(msgId: Long, priority: Int = com.itantra.domain.model.MessagePriority.NORMAL) {
        scope.launch {
            val msg = _messages.value.find { it.messageId == msgId } ?: return@launch
            if (msg.text.isBlank() || msg.state == MessageState.RECORDING || msg.state == MessageState.STT_PROCESSING) return@launch

            val sendPriority = if (msg.priority == com.itantra.domain.model.MessagePriority.CRITICAL) {
                com.itantra.domain.model.MessagePriority.CRITICAL
            } else {
                priority
            }

            updateMessage(msgId) { it.copy(state = MessageState.TRANSMITTING, priority = sendPriority) }
            val payload = msg.text.toByteArray(Charsets.UTF_8)
            val wasTranslated = msg.translationStatus == com.itantra.domain.model.TranslationStatus.SUCCESS
            val payloadLanguage = if (wasTranslated) (msg.targetLanguage ?: msg.language) else msg.language
            val packet = ItantraPacket(
                type = PacketType.TEXT,
                flags = sendPriority.toByte(),
                messageId = msgId,
                languageCode = payloadLanguage,
                sourceLanguage = msg.language,
                targetLanguage = payloadLanguage,
                translationMode = if (wasTranslated && payloadLanguage != msg.language) com.itantra.domain.model.TranslationMode.DIRECT else com.itantra.domain.model.TranslationMode.NONE,
                payload = payload
            )

            val maxAttempts = if (sendPriority == com.itantra.domain.model.MessagePriority.CRITICAL) 3 else 1
            var attempt = 0
            var success = false

            while (attempt < maxAttempts && !success) {
                attempt++
                try {
                    val tCrypto0 = SystemClock.elapsedRealtimeNanos()
                    val securePacket = secureSessionManager.encrypt(packet) // Encrypt inside loop to get a fresh nonce/counter each time
                    val cryptoLatency = (SystemClock.elapsedRealtimeNanos() - tCrypto0) / 1_000_000

                    metricsRecorder.recordCryptoMetrics(
                        com.itantra.domain.model.CryptoMetrics(
                            handshakeDurationMillis = com.itantra.domain.model.Measurement.Measured(secureSessionManager.lastHandshakeDurationMillis),
                            verificationDurationMillis = com.itantra.domain.model.Measurement.Measured(secureSessionManager.lastVerificationDurationMillis),
                            avgEncryptUs = com.itantra.domain.model.Measurement.Measured(secureSessionManager.encryptDurationUs),
                            avgDecryptUs = com.itantra.domain.model.Measurement.Measured(secureSessionManager.decryptDurationUs),
                            authFailures = secureSessionManager.authFailures,
                            replayRejections = secureSessionManager.replayRejections
                        )
                    )

                    val txMetrics = transportEngine.send(securePacket)
                    val rtt = txMetrics.transmissionLatencyMillis.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0L }
                    val pBytes = txMetrics.packetBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }
                    val semBytes = packet.payload.size
                    val secBytes = securePacket.payload.size
                    val frameBytes = txMetrics.finalFrameBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }

                    if (rtt > 0 || sendPriority != com.itantra.domain.model.MessagePriority.CRITICAL) {
                        success = true
                        val estE2e = msg.sttLatencyMillis + msg.mtLatencyMillis + cryptoLatency + (rtt / 2)
                        if (rtt > 0) {
                            updateMessage(msgId) {
                                it.copy(
                                    state = MessageState.DELIVERED,
                                    cryptoLatencyMillis = cryptoLatency,
                                    packetBytes = pBytes,
                                    semanticBytes = semBytes,
                                    secureBytes = secBytes,
                                    finalFrameBytes = frameBytes,
                                    rttMillis = rtt,
                                    estimatedE2eMillis = estE2e
                                )
                            }
                        } else {
                            updateMessage(msgId) {
                                it.copy(
                                    state = MessageState.SENT,
                                    cryptoLatencyMillis = cryptoLatency,
                                    packetBytes = pBytes,
                                    semanticBytes = semBytes,
                                    secureBytes = secBytes,
                                    finalFrameBytes = frameBytes,
                                    estimatedE2eMillis = estE2e
                                )
                            }
                        }
                        metricsRecorder.recordTransmission(txMetrics)
                    } else {
                        // Failed to get ACK for CRITICAL, retry if we have attempts left
                        if (attempt < maxAttempts) {
                            delay(1000)
                        } else {
                            updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "${msg.text} (Failed to deliver)") }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (attempt == maxAttempts) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "${msg.text} (Encryption failed)") }
                    } else {
                        delay(1000)
                    }
                }
            }
        }
    }

    fun confirmPeerVerification() {
        scope.launch {
            val verifyPacket = secureSessionManager.confirmSasMatch()
            transportEngine.send(verifyPacket)
            if (secureSessionManager.state.value == SecureSessionState.SECURE_VERIFIED) {
                sendCapabilities()
                retryUnresolvedEmergencies()
            }
        }
    }

    var emergencyPlaybackVolume: Int = 100
        private set
    var emergencyRequireConfirmation: Boolean = true
        private set
    var emergencyOverrideSilent: Boolean = true
        private set
    var emergencyTtsAnnounce: Boolean = true
        private set
    var vadSensitivity: Int = 2
        private set
    var noiseSuppressionDb: Int = 18
        private set

    fun attachSettings(repository: com.example.itantra.data.settings.SettingsRepository) {
        scope.launch {
            repository.settings.collect { settings ->
                emergencyPlaybackVolume = settings.emergencyPlaybackVolume
                emergencyRequireConfirmation = settings.emergencyRequireConfirmation
                emergencyOverrideSilent = settings.emergencyOverrideSilent
                emergencyTtsAnnounce = settings.emergencyTtsAnnounce
                vadSensitivity = settings.vadSensitivity
                noiseSuppressionDb = settings.noiseSuppressionDb
            }
        }
    }

    fun sendEmergencyCode(code: com.itantra.domain.model.EmergencyCode) {
        scope.launch {
            val msgId = nextMessageId()
            val text = com.itantra.domain.model.EmergencyPhraseResolver.resolve(code, sessionManager.activeLanguage.value)
            val msg = TransceiverMessage(
                messageId = msgId,
                language = sessionManager.activeLanguage.value,
                priority = com.itantra.domain.model.MessagePriority.CRITICAL,
                text = text,
                source = MessageSource.LOCAL,
                createdAtLocal = System.currentTimeMillis(),
                state = MessageState.TRANSMITTING
            )
            addMessage(msg)

            val record = com.itantra.domain.model.EmergencyRecord(
                messageId = msgId,
                emergencyCode = code.name,
                source = "LOCAL",
                target = "BROADCAST",
                createdAt = System.currentTimeMillis(),
                retryStatus = com.itantra.domain.model.EmergencyRetryStatus.PENDING,
                retryCount = 0,
                resolvedPhrase = text
            )
            emergencyStore.saveRecord(record)

            val packet = ItantraPacket(
                type = PacketType.EMERGENCY_CODE,
                flags = com.itantra.domain.model.MessagePriority.CRITICAL.toByte(),
                messageId = msgId,
                languageCode = msg.language,
                sourceLanguage = msg.language,
                targetLanguage = msg.language,
                translationMode = com.itantra.domain.model.TranslationMode.NONE,
                payload = byteArrayOf(code.id)
            )

            var attempt = 0
            var success = false

            while (attempt < 3 && !success) {
                attempt++
                try {
                    val tCrypto0 = SystemClock.elapsedRealtimeNanos()
                    val securePacket = secureSessionManager.encrypt(packet)
                    val cryptoLatency = (SystemClock.elapsedRealtimeNanos() - tCrypto0) / 1_000_000

                    metricsRecorder.recordCryptoMetrics(
                        com.itantra.domain.model.CryptoMetrics(
                            handshakeDurationMillis = com.itantra.domain.model.Measurement.Measured(secureSessionManager.lastHandshakeDurationMillis),
                            verificationDurationMillis = com.itantra.domain.model.Measurement.Measured(secureSessionManager.lastVerificationDurationMillis),
                            avgEncryptUs = com.itantra.domain.model.Measurement.Measured(secureSessionManager.encryptDurationUs),
                            avgDecryptUs = com.itantra.domain.model.Measurement.Measured(secureSessionManager.decryptDurationUs),
                            authFailures = secureSessionManager.authFailures,
                            replayRejections = secureSessionManager.replayRejections
                        )
                    )

                    val txMetrics = transportEngine.send(securePacket)
                    val rtt = txMetrics.transmissionLatencyMillis.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0L }
                    val pBytes = txMetrics.packetBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }
                    val semBytes = packet.payload.size
                    val secBytes = securePacket.payload.size
                    val frameBytes = txMetrics.finalFrameBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }

                    if (rtt > 0) {
                        success = true
                        emergencyStore.recordTransportAck(msgId)
                        val estE2e = cryptoLatency + (rtt / 2)
                        updateMessage(msgId) {
                            it.copy(
                                state = MessageState.DELIVERED,
                                cryptoLatencyMillis = cryptoLatency,
                                packetBytes = pBytes,
                                semanticBytes = semBytes,
                                secureBytes = secBytes,
                                finalFrameBytes = frameBytes,
                                rttMillis = rtt,
                                estimatedE2eMillis = estE2e
                            )
                        }
                        metricsRecorder.recordTransmission(txMetrics)
                    } else {
                        emergencyStore.recordRetryAttempt(msgId, false, System.currentTimeMillis())
                        if (attempt < 3) delay(1000)
                        else updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Failed to deliver SOS") }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emergencyStore.recordRetryAttempt(msgId, false, System.currentTimeMillis())
                    if (attempt == 3) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Failed to send SOS") }
                    } else {
                        delay(1000)
                    }
                }
            }
        }
    }

    fun sendHumanAck(msgId: Long) {
        scope.launch {
            updateMessage(msgId) { it.copy(state = MessageState.ACKNOWLEDGED) }
            emergencyStore.recordHumanAck(msgId)
            if (_activeEmergencyAlert.value?.messageId == msgId) {
                _activeEmergencyAlert.value = null
                com.itantra.core.service.OperationalForegroundService.resolveEmergency(context)
            }
            val packet = ItantraPacket(
                type = PacketType.HUMAN_ACK,
                messageId = msgId,
                payload = ByteArray(0)
            )
            try {
                val securePacket = secureSessionManager.encrypt(packet)
                transportEngine.send(securePacket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resolveActiveEmergency() {
        val alert = _activeEmergencyAlert.value ?: return
        _activeEmergencyAlert.value = null
        emergencyStore.recordHumanAck(alert.messageId)
        com.itantra.core.service.OperationalForegroundService.resolveEmergency(context)
    }

    private suspend fun retryUnresolvedEmergencies() {
        val unresolved = emergencyStore.getUnresolvedRecords().filter { it.canRetry && it.source == "LOCAL" }
        for (rec in unresolved) {
            val code = com.itantra.domain.model.EmergencyCode.values().find { it.name == rec.emergencyCode } ?: continue
            try {
                val packet = ItantraPacket(
                    type = PacketType.EMERGENCY_CODE,
                    flags = com.itantra.domain.model.MessagePriority.CRITICAL.toByte(),
                    messageId = rec.messageId,
                    payload = byteArrayOf(code.id)
                )
                val securePacket = secureSessionManager.encrypt(packet)
                val txMetrics = transportEngine.send(securePacket)
                val rtt = txMetrics.transmissionLatencyMillis.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0L }
                val ok = rtt > 0
                emergencyStore.recordRetryAttempt(rec.messageId, ok, System.currentTimeMillis())
                if (ok) {
                    emergencyStore.recordTransportAck(rec.messageId)
                }
            } catch (e: Exception) {
                emergencyStore.recordRetryAttempt(rec.messageId, false, System.currentTimeMillis())
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
