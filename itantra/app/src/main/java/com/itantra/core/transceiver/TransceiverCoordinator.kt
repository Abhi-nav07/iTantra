package com.itantra.core.transceiver

import android.os.SystemClock
import com.itantra.core.audio.SpeakerAudioSink
import com.itantra.core.crypto.SecureSessionManager
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
    private val translationRouter: com.itantra.core.translation.TranslationRouter
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _messages = MutableStateFlow<List<TransceiverMessage>>(emptyList())
    val messages: StateFlow<List<TransceiverMessage>> = _messages.asStateFlow()

    private val _peerCapabilities = MutableStateFlow(PeerCapabilities())
    val peerCapabilities: StateFlow<PeerCapabilities> = _peerCapabilities.asStateFlow()

    private val messageQueue = mutableListOf<ItantraPacket>()
    private val queueMutex = Mutex()
    private val queueWakeup = Channel<Unit>(Channel.CONFLATED)

    private val sttMutex = Mutex()

    private var cooldownJob: Job? = null
    private var currentTtsJob: Job? = null
    private var currentAudioSink: SpeakerAudioSink? = null
    private var alertJob: Job? = null

    private var isConnected = false
    private var recordingJob: Job? = null
    private val audioSource = MicrophoneAudioSource(scope)
    private var recordingStartTime = 0L

    val continuousListenEngine = ContinuousListenEngine(context)
    private var continuousModeJob: Job? = null
    private var isTtsPlaying = false

    private val currentTargetLanguage = MutableStateFlow<LanguageCode?>(null)

    init {
        scope.launch {
            transportEngine.observeConnectionState().collect { state ->
                val connected = state == ConnectionState.CONNECTED
                if (connected && !isConnected) {
                    isConnected = true
                    // Start handshake if connected. Only the client (initiator) sends the first HELLO.
                    val isInitiator = !transportEngine.isServer
                    val hello = secureSessionManager.startHandshake(isInitiator = isInitiator)
                    if (isInitiator) {
                        transportEngine.send(hello)
                    }
                    sendCapabilities()
                } else if (!connected && isConnected) {
                    isConnected = false
                    _peerCapabilities.value = PeerCapabilities()
                    secureSessionManager.resetSession()
                }
            }
        }

        scope.launch {
            sessionManager.activeLanguage.collect { lang ->
                if (isConnected && lang != null) {
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
    }

    private fun updateMessage(id: Long, update: (TransceiverMessage) -> TransceiverMessage) {
        _messages.value = _messages.value.map { if (it.messageId == id) update(it) else it }
        updateAlertJob()
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
                        continuousListenEngine.stop()

                        val text = "Attention. " + msg.text
                        val req = SpeechSynthesisRequest(msg.language ?: LanguageCode.ENGLISH, text, "alert")
                        val res = sessionManager.currentTtsEngine?.synthesize(req)
                        if (res != null) {
                            val sink = SpeakerAudioSink()
                            sink.init(res.sampleRateHz, android.media.AudioAttributes.USAGE_ALARM)
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
            messageId = SystemClock.elapsedRealtime(),
            payload = payload
        )
        transportEngine.send(packet)
    }

    private fun handleIncomingPacket(packet: ItantraPacket) {
        if (packet.type == PacketType.SECURE_HELLO) {
            val response = secureSessionManager.processSecureHello(packet)
            if (response != null) {
                scope.launch { transportEngine.send(response) }
            }
            return
        }

        if (packet.type == PacketType.SECURE_VERIFY) {
            secureSessionManager.processSecureVerify(packet)
            return
        }

        // Only process application traffic if session is secure
        if (secureSessionManager.state.value != SecureSessionState.SECURE_VERIFIED) {
            // Drop packet or ignore
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
                scope.launch {
                    val isDuplicate = _messages.value.any { it.messageId == decryptedPacket.messageId && it.source == MessageSource.REMOTE }
                    if (!isDuplicate) {
                        if (decryptedPacket.type == PacketType.EMERGENCY_CODE && decryptedPacket.payload.isNotEmpty() && decryptedPacket.payload[0] == com.itantra.domain.model.EmergencyCode.ALL_CLEAR.id) {
                            alertJob?.cancel()
                            alertJob = null
                            isTtsPlaying = false
                            currentTtsJob?.cancel()
                            currentAudioSink?.flushAndStop()
                            if (continuousModeJob?.isActive == true) {
                                continuousListenEngine.resetAndResume()
                            }
                        }
                        queueMutex.withLock {
                            messageQueue.add(decryptedPacket)
                        }
                        if (decryptedPacket.flags.toInt() == com.itantra.domain.model.MessagePriority.CRITICAL) {
                            currentTtsJob?.cancel()
                            currentAudioSink?.flushAndStop()
                        }
                        queueWakeup.trySend(Unit)
                    }
                }
            }
            PacketType.HUMAN_ACK -> {
                updateMessage(decryptedPacket.messageId) {
                    it.copy(state = MessageState.ACKNOWLEDGED)
                }
            }
            PacketType.TTS_STARTED -> {
                updateMessage(decryptedPacket.messageId) {
                    val rasc = SystemClock.elapsedRealtime() - it.createdAtLocal
                    it.copy(state = MessageState.REMOTE_PLAYING, remoteAudioStartConfMillis = rasc)
                }
            }
            PacketType.TTS_COMPLETED -> {
                var ttfa = 0L
                if (decryptedPacket.payload.size == 8) {
                    ttfa = ByteBuffer.wrap(decryptedPacket.payload).order(ByteOrder.BIG_ENDIAN).long
                }
                updateMessage(decryptedPacket.messageId) {
                    it.copy(
                        state = MessageState.REMOTE_PLAYBACK_CONFIRMED,
                        peerTtfaMillis = ttfa,
                        estimatedE2eMillis = it.sttLatencyMillis + (it.rttMillis / 2) + ttfa
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

    private suspend fun processIncomingMessagePacket(packet: ItantraPacket) {
        val isEmergencyCode = packet.type == PacketType.EMERGENCY_CODE
        var text = if (isEmergencyCode && packet.payload.isNotEmpty()) {
            val code = com.itantra.domain.model.EmergencyCode.fromId(packet.payload[0])
            if (code != null) com.itantra.domain.model.EmergencyPhraseResolver.resolve(code, packet.languageCode) else "Unknown Emergency"
        } else {
            String(packet.payload, Charsets.UTF_8)
        }

        val localLanguage = sessionManager.activeLanguage.value ?: LanguageCode.ENGLISH
        val pktLang = packet.targetLanguage ?: packet.languageCode ?: localLanguage

        if (!isEmergencyCode && pktLang != localLanguage) {
            val result = translationRouter.routeAndTranslate(text, pktLang, localLanguage)
            if (result.isSuccessful && result.translatedText.isNotBlank()) {
                text = result.translatedText
            }
        }

        val msg = TransceiverMessage(
            messageId = packet.messageId,
            language = packet.languageCode,
            priority = packet.flags.toInt(),
            text = text,
            source = MessageSource.REMOTE,
            createdAtLocal = SystemClock.elapsedRealtime(),
            state = MessageState.DELIVERED
        )
        addMessage(msg)

        val ackPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.ACK, messageId = packet.messageId))
        scope.launch { transportEngine.send(ackPkt) }

        val engine = sessionManager.currentTtsEngine
        if (engine == null || !engine.isLoaded) {
            updateMessage(msg.messageId) { it.copy(state = MessageState.ERROR, text = it.text + " [Voice pack unavailable]") }
            val failPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.TTS_FAILED, messageId = packet.messageId))
            scope.launch { transportEngine.send(failPkt) }
            return
        }

        val startedPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.TTS_STARTED, messageId = packet.messageId))
        scope.launch { transportEngine.send(startedPkt) }
        updateMessage(msg.messageId) { it.copy(state = MessageState.REMOTE_PLAYING) }

        // TTS Suppression Rule
        cooldownJob?.cancel()
        isTtsPlaying = true
        continuousListenEngine.stop()

        try {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val req = SpeechSynthesisRequest(
                languageCode = engine.languageCode,
                text = text,
                correlationId = msg.messageId.toString()
            )
            val result = engine.synthesize(req)
            val ttfaMillis = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000

            val sink = SpeakerAudioSink()
            currentAudioSink = sink
            val usage = if (packet.flags.toInt() == com.itantra.domain.model.MessagePriority.CRITICAL) {
                android.media.AudioAttributes.USAGE_ALARM
            } else {
                android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
            }
            sink.init(result.sampleRateHz, usage)
            sink.play(result.pcmAudio)
            sink.flushAndStop()

            updateMessage(msg.messageId) { it.copy(state = MessageState.REMOTE_PLAYBACK_CONFIRMED, peerTtfaMillis = ttfaMillis) }

            val payloadBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(ttfaMillis).array()
            val compPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.TTS_COMPLETED, messageId = packet.messageId, payload = payloadBytes))
            scope.launch { transportEngine.send(compPkt) }

        } catch (e: kotlinx.coroutines.CancellationException) {
            // Interrupted by preemption
            updateMessage(msg.messageId) { it.copy(state = MessageState.ERROR, text = it.text + " (Interrupted)") }
        } catch (e: Exception) {
            e.printStackTrace()
            updateMessage(msg.messageId) { it.copy(state = MessageState.ERROR) }
            val failPkt = secureSessionManager.encrypt(ItantraPacket(PacketType.TTS_FAILED, messageId = packet.messageId))
            scope.launch { transportEngine.send(failPkt) }
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
                    continuousListenEngine.state.collect { state ->
                        if (state == ContinuousListenState.SEGMENT_READY) {
                            val audio = continuousListenEngine.lastSegment.value?.samples ?: return@collect
                            processContinuousSegment(audio)
                        }
                    }
                }
            }
        } else {
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
            val msgId = SystemClock.elapsedRealtime()
            val msg = TransceiverMessage(
                messageId = msgId,
                language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
                targetLanguage = currentTargetLanguage.value,
                priority = com.itantra.domain.model.MessagePriority.NORMAL,
                text = "STT Pack Required",
                source = MessageSource.LOCAL,
                createdAtLocal = msgId,
                state = MessageState.ERROR
            )
            addMessage(msg)
            return
        }

        continuousListenEngine.stop()

        val msgId = SystemClock.elapsedRealtime()
        val msg = TransceiverMessage(
            messageId = msgId,
            language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
            targetLanguage = currentTargetLanguage.value,
            priority = com.itantra.domain.model.MessagePriority.NORMAL,
            text = "Recognizing...",
            source = MessageSource.LOCAL,
            createdAtLocal = msgId,
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
                    metricsRecorder.recordSttAudioDuration(durationMillis)

                    if (result.text.isBlank()) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Recognition produced no text.") }
                        return@launch
                    }

                    if (secureSessionManager.state.value != SecureSessionState.SECURE_VERIFIED) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = result.text + " (Secure Link Required)") }
                        return@launch
                    }

                    if (!isConnected) {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = result.text + " (Peer disconnected)") }
                        return@launch
                    }

                    val targetLang = currentTargetLanguage.value
                    val srcLang = sessionManager.activeLanguage.value ?: LanguageCode.HINDI
                    var finalTxt = result.text
                    var origTxt: String? = null
                    var translationStatus = com.itantra.domain.model.TranslationStatus.BYPASSED

                    if (targetLang != null && targetLang != srcLang) {
                        translationStatus = com.itantra.domain.model.TranslationStatus.TRANSLATING
                        updateMessage(msgId) {
                            it.copy(state = MessageState.STT_PROCESSING, text = "Translating...", translationStatus = translationStatus)
                        }
                        val translationRes = translationRouter.routeAndTranslate(result.text, srcLang, targetLang)
                        if (translationRes.isSuccessful && translationRes.translatedText.isNotBlank()) {
                            finalTxt = translationRes.translatedText
                            origTxt = result.text
                            translationStatus = com.itantra.domain.model.TranslationStatus.SUCCESS
                        } else {
                            translationStatus = com.itantra.domain.model.TranslationStatus.FAILED
                            // For failure, do not fake translation and prevent send
                            updateMessage(msgId) {
                                it.copy(
                                    state = MessageState.ERROR,
                                    text = "Translation failed: ${translationRes.error ?: "Unknown"}",
                                    originalText = result.text,
                                    translationStatus = translationStatus
                                )
                            }
                            return@launch
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
                        continuousListenEngine.resetAndResume()
                    }
                }
            }
        }
    }

    fun startRecording(isCritical: Boolean = false) {
        if (recordingJob != null) return
        val engine = sessionManager.currentSttEngine
        if (engine == null || !engine.isLoaded) {
            val msgId = SystemClock.elapsedRealtime()
            addMessage(
                TransceiverMessage(
                    messageId = msgId,
                    language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
                    targetLanguage = currentTargetLanguage.value,
                    priority = 0,
                    text = "STT Pack Required",
                    source = MessageSource.LOCAL,
                    createdAtLocal = msgId,
                    state = MessageState.ERROR
                )
            )
            return
        }

        recordingStartTime = SystemClock.elapsedRealtime()
        val msgId = recordingStartTime

        if (continuousModeJob?.isActive == true) {
            continuousListenEngine.stop()
        }

        val msg = TransceiverMessage(
            messageId = msgId,
            language = engine.languageCode,
            targetLanguage = currentTargetLanguage.value,
            priority = if (isCritical) com.itantra.domain.model.MessagePriority.CRITICAL else com.itantra.domain.model.MessagePriority.NORMAL,
            text = "Listening...",
            source = MessageSource.LOCAL,
            createdAtLocal = recordingStartTime,
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
                    engine.feed(samples)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording(msgId: Long) {
        if (recordingJob == null) return
        recordingJob?.cancel()
        recordingJob = null

        val engine = sessionManager.currentSttEngine ?: return
        val durationMillis = SystemClock.elapsedRealtime() - recordingStartTime

        if (durationMillis < 300) {
            scope.launch {
                sttMutex.withLock { engine.reset() }
                if (continuousModeJob?.isActive == true && !isTtsPlaying) {
                    continuousListenEngine.resetAndResume()
                }
            }
            updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Recording too short") }
            return
        }

        updateMessage(msgId) { it.copy(state = MessageState.STT_PROCESSING, text = "Finalizing...") }

        scope.launch {
            sttMutex.withLock {
                try {
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    val result = engine.finalizeUtterance()
                    val latencyMillis = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000

                    metricsRecorder.recordSttInferenceTime(latencyMillis)
                    metricsRecorder.recordSttAudioDuration(durationMillis)

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

                    val targetLang = msg.targetLanguage
                    val srcLang = msg.language ?: LanguageCode.HINDI
                    var finalTxt = result.text
                    var origTxt: String? = null
                    var translationStatus = com.itantra.domain.model.TranslationStatus.BYPASSED

                    if (targetLang != null && targetLang != srcLang) {
                        translationStatus = com.itantra.domain.model.TranslationStatus.TRANSLATING
                        updateMessage(msgId) {
                            it.copy(state = MessageState.STT_PROCESSING, text = "Translating...", translationStatus = translationStatus)
                        }
                        val translationRes = translationRouter.routeAndTranslate(result.text, srcLang, targetLang)
                        if (translationRes.isSuccessful && translationRes.translatedText.isNotBlank()) {
                            finalTxt = translationRes.translatedText
                            origTxt = result.text
                            translationStatus = com.itantra.domain.model.TranslationStatus.SUCCESS
                        } else {
                            translationStatus = com.itantra.domain.model.TranslationStatus.FAILED
                            // For failure, do not fake translation and prevent send
                            updateMessage(msgId) {
                                it.copy(
                                    state = MessageState.ERROR,
                                    text = "Translation failed: ${translationRes.error ?: "Unknown"}",
                                    originalText = result.text,
                                    translationStatus = translationStatus
                                )
                            }
                            return@withLock
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
                            payloadBytes = payload.size,
                            rawPcmEquivalentBytes = rawPcmEq.toInt(),
                            speechDurationMillis = durationMillis
                        )
                    }

                    if (isConnected) {
                        if (priorityInt == com.itantra.domain.model.MessagePriority.CRITICAL) {
                            updateMessage(msgId) { it.copy(state = MessageState.WAITING_USER_CONFIRMATION) }
                        } else {
                            sendVoiceMessage(msgId)
                        }
                    } else {
                        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = it.text + " (Peer disconnected)") }
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
        updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = it.text + " (Cancelled)") }
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
            val packet = ItantraPacket(
                type = PacketType.TEXT,
                flags = sendPriority.toByte(),
                messageId = msgId,
                languageCode = msg.targetLanguage ?: msg.language, // Transmit with target language so receiver TTS works correctly
                sourceLanguage = msg.language,
                targetLanguage = msg.targetLanguage ?: msg.language,
                translationMode = if (msg.targetLanguage != null && msg.targetLanguage != msg.language) com.itantra.domain.model.TranslationMode.DIRECT else com.itantra.domain.model.TranslationMode.NONE,
                payload = payload
            )

            val maxAttempts = if (sendPriority == com.itantra.domain.model.MessagePriority.CRITICAL) 3 else 1
            var attempt = 0
            var success = false

            while (attempt < maxAttempts && !success) {
                attempt++
                try {
                    val securePacket = secureSessionManager.encrypt(packet) // Encrypt inside loop to get a fresh nonce/counter each time
                    val txMetrics = transportEngine.send(securePacket)
                    val rtt = txMetrics.transmissionLatencyMillis.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0L }
                    val pBytes = txMetrics.packetBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }
                    val semBytes = packet.payload.size
                    val secBytes = txMetrics.secureBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }
                    val frameBytes = txMetrics.finalFrameBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }

                    if (rtt > 0 || sendPriority != com.itantra.domain.model.MessagePriority.CRITICAL) {
                        success = true
                        if (rtt > 0) {
                            updateMessage(msgId) {
                                it.copy(
                                    state = MessageState.DELIVERED,
                                    packetBytes = pBytes,
                                    semanticBytes = semBytes,
                                    secureBytes = secBytes,
                                    finalFrameBytes = frameBytes,
                                    rttMillis = rtt,
                                    estimatedE2eMillis = it.sttLatencyMillis + (rtt / 2)
                                )
                            }
                        } else {
                            updateMessage(msgId) { it.copy(state = MessageState.SENT, packetBytes = pBytes, semanticBytes = semBytes, secureBytes = secBytes, finalFrameBytes = frameBytes) }
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
            secureSessionManager.processSecureVerify(verifyPacket) // apply locally
        }
    }

    fun sendEmergencyCode(code: com.itantra.domain.model.EmergencyCode) {
        scope.launch {
            val msgId = SystemClock.elapsedRealtime()
            val text = com.itantra.domain.model.EmergencyPhraseResolver.resolve(code, sessionManager.activeLanguage.value)
            val msg = TransceiverMessage(
                messageId = msgId,
                language = sessionManager.activeLanguage.value,
                priority = com.itantra.domain.model.MessagePriority.CRITICAL,
                text = text,
                source = MessageSource.LOCAL,
                createdAtLocal = msgId,
                state = MessageState.TRANSMITTING
            )
            addMessage(msg)

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
                    val securePacket = secureSessionManager.encrypt(packet)
                    val txMetrics = transportEngine.send(securePacket)
                    val rtt = txMetrics.transmissionLatencyMillis.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0L }
                    val pBytes = txMetrics.packetBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }
                    val semBytes = packet.payload.size
                    val secBytes = txMetrics.secureBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }
                    val frameBytes = txMetrics.finalFrameBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }

                    if (rtt > 0) {
                        success = true
                        updateMessage(msgId) {
                            it.copy(
                                state = MessageState.DELIVERED,
                                packetBytes = pBytes,
                                semanticBytes = semBytes,
                                secureBytes = secBytes,
                                finalFrameBytes = frameBytes,
                                rttMillis = rtt,
                                estimatedE2eMillis = rtt / 2
                            )
                        }
                    } else {
                        if (attempt < 3) delay(1000)
                        else updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Failed to deliver SOS") }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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

    fun shutdown() {
        scope.cancel()
    }
}
