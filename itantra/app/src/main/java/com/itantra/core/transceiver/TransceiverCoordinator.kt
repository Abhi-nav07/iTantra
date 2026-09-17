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
import kotlinx.coroutines.flow.catch
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
    private val transportEngine: TransportEngine,
    private val metricsRecorder: MetricsRecorder,
    val secureSessionManager: SecureSessionManager
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

    init {
        scope.launch {
            transportEngine.observeConnectionState().collect { state ->
                val connected = state == ConnectionState.CONNECTED
                if (connected && !isConnected) {
                    isConnected = true
                    // Start handshake if connected. Only one side needs to be initiator.
                    // We'll let the one who acts as server (or arbitrarily if unknown) 
                    // However, we can just let both send SECURE_HELLO to each other.
                    // The one who explicitly clicked connect (client) might be initiator.
                    // For simplicity, we just trigger startHandshake(true) when we see CONNECTED,
                    // but wait, if both do it, both think they are initiator.
                    // We can just rely on the first one sending it. We'll start handshake directly:
                    val hello = secureSessionManager.startHandshake(isInitiator = true)
                    transportEngine.send(hello)
                    sendCapabilities()
                } else if (!connected && isConnected) {
                    isConnected = false
                    _peerCapabilities.value = PeerCapabilities()
                    secureSessionManager.resetSession()
                }
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
                        continuousListenEngine.suspendListening()

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
                                continuousListenEngine.resumeListening()
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
        val text = if (isEmergencyCode && packet.payload.isNotEmpty()) {
            val code = com.itantra.domain.model.EmergencyCode.fromId(packet.payload[0])
            if (code != null) com.itantra.domain.model.EmergencyPhraseResolver.resolve(code, packet.languageCode) else "Unknown Emergency"
        } else {
            String(packet.payload, Charsets.UTF_8)
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
        continuousListenEngine.suspendListening()
        
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
                    continuousListenEngine.resumeListening()
                }
            }
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        if (enabled) {
            if (continuousModeJob != null) return
            continuousListenEngine.init()
            continuousModeJob = scope.launch {
                launch {
                    audioSource.stream
                        .catch { e -> e.printStackTrace() }
                        .collect { samples ->
                            if (!isTtsPlaying) {
                                continuousListenEngine.processAudio(samples)
                            }
                        }
                }
                launch {
                    continuousListenEngine.state.collect { state ->
                        if (state == ContinuousListenState.SEGMENT_READY) {
                            val audio = continuousListenEngine.popCapturedAudio()
                            processContinuousSegment(audio)
                        }
                    }
                }
            }
        } else {
            continuousModeJob?.cancel()
            continuousModeJob = null
            continuousListenEngine.release()
        }
    }

    private fun processContinuousSegment(audio: FloatArray) {
        val durationS = audio.size / 16000.0
        metricsRecorder.recordVadSegment(durationS, audio.size)
        
        val engine = sessionManager.currentSttEngine
        if (engine == null || !engine.isLoaded) {
            continuousListenEngine.suspendListening()
            val msgId = SystemClock.elapsedRealtime()
            val msg = TransceiverMessage(
                messageId = msgId,
                language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
                priority = com.itantra.domain.model.MessagePriority.NORMAL,
                text = "STT Pack Required",
                source = MessageSource.LOCAL,
                createdAtLocal = msgId,
                state = MessageState.ERROR
            )
            addMessage(msg)
            return
        }

        continuousListenEngine.suspendListening()

        val msgId = SystemClock.elapsedRealtime()
        val msg = TransceiverMessage(
            messageId = msgId,
            language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
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

                    val payload = result.text.toByteArray(Charsets.UTF_8)
                    val rawPcmEq = (durationMillis * 16000 * 2) / 1000

                    updateMessage(msgId) {
                        it.copy(
                            state = MessageState.STT_COMPLETE,
                            text = result.text,
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

    fun startRecording() {
        if (recordingJob != null) return
        val engine = sessionManager.currentSttEngine
        if (engine == null || !engine.isLoaded) {
            val msgId = SystemClock.elapsedRealtime()
            addMessage(
                TransceiverMessage(
                    messageId = msgId,
                    language = sessionManager.activeLanguage.value ?: LanguageCode.HINDI,
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
        
        val msg = TransceiverMessage(
            messageId = msgId,
            language = engine.languageCode,
            priority = 0,
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
            audioSource.stream
                .catch { e -> e.printStackTrace() }
                .collect { samples ->
                    engine.feed(samples)
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
            scope.launch { engine.reset() }
            updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Recording too short") }
            return
        }

        updateMessage(msgId) { it.copy(state = MessageState.STT_PROCESSING, text = "Finalizing...") }

        scope.launch {
            try {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val result = engine.finalizeUtterance()
                val latencyMillis = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000

                metricsRecorder.recordSttInferenceTime(latencyMillis)
                metricsRecorder.recordSttAudioDuration(durationMillis)

                if (result.text.isBlank()) {
                    updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Speech recognition failed.") }
                    return@launch
                }

                val payload = result.text.toByteArray(Charsets.UTF_8)
                val rawPcmEq = (durationMillis * 16000 * 2) / 1000

                updateMessage(msgId) {
                    it.copy(
                        state = MessageState.STT_COMPLETE,
                        text = result.text,
                        sttLatencyMillis = latencyMillis,
                        payloadBytes = payload.size,
                        rawPcmEquivalentBytes = rawPcmEq.toInt(),
                        speechDurationMillis = durationMillis
                    )
                }

                if (isConnected) {
                    sendVoiceMessage(msgId)
                } else {
                    updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = it.text + " (Peer disconnected)") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Error: ${e.message}") }
            }
        }
    }

    fun sendVoiceMessage(msgId: Long, priority: Int = com.itantra.domain.model.MessagePriority.NORMAL) {
        scope.launch {
            val msg = _messages.value.find { it.messageId == msgId } ?: return@launch
            if (msg.text.isBlank() || msg.state == MessageState.RECORDING || msg.state == MessageState.STT_PROCESSING) return@launch

            updateMessage(msgId) { it.copy(state = MessageState.TRANSMITTING, priority = priority) }
            val payload = msg.text.toByteArray(Charsets.UTF_8)
            val packet = ItantraPacket(
                type = PacketType.TEXT,
                flags = priority.toByte(),
                messageId = msgId,
                languageCode = msg.language,
                payload = payload
            )
            
            try {
                val securePacket = secureSessionManager.encrypt(packet)
                val txMetrics = transportEngine.send(securePacket)
                
                val rtt = txMetrics.transmissionLatencyMillis.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0L }
                val pBytes = txMetrics.packetBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }
                
                if (rtt > 0) {
                    updateMessage(msgId) {
                        it.copy(
                            state = MessageState.DELIVERED,
                            packetBytes = pBytes,
                            rttMillis = rtt,
                            estimatedE2eMillis = it.sttLatencyMillis + (rtt / 2)
                        )
                    }
                } else {
                    updateMessage(msgId) { it.copy(state = MessageState.SENT, packetBytes = pBytes) }
                }
                metricsRecorder.recordTransmission(txMetrics)
            } catch (e: Exception) {
                e.printStackTrace()
                updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Encryption failed") }
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
                payload = byteArrayOf(code.id)
            )
            
            try {
                val securePacket = secureSessionManager.encrypt(packet)
                val txMetrics = transportEngine.send(securePacket)
                val rtt = txMetrics.transmissionLatencyMillis.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0L }
                val pBytes = txMetrics.packetBytes.let { if (it is com.itantra.domain.model.Measurement.Measured) it.value else 0 }
                updateMessage(msgId) {
                    it.copy(
                        state = MessageState.DELIVERED,
                        packetBytes = pBytes,
                        rttMillis = rtt,
                        estimatedE2eMillis = rtt / 2
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateMessage(msgId) { it.copy(state = MessageState.ERROR, text = "Failed to send SOS") }
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
}
