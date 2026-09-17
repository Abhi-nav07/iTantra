package com.itantra.feature.transceiver

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.metrics.MetricsRecorder
import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.TransportEngine
import com.itantra.domain.model.InferenceMetrics
import com.itantra.domain.model.Language
import com.itantra.domain.model.LanguageCatalog
import com.itantra.domain.repository.LanguagePackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.itantra.core.transceiver.TransceiverCoordinator
import com.itantra.core.transceiver.PeerCapabilities
import com.itantra.core.crypto.SecureSessionState
import com.itantra.domain.model.TransceiverMessage

enum class TransceiverMode { PTT, CONTINUOUS, SOS }

data class TransceiverUiState(
    val activeLanguage: Language? = null,
    val mode: TransceiverMode = TransceiverMode.PTT,
    val isTransmitting: Boolean = false,
    val metrics: InferenceMetrics = InferenceMetrics(),
    val connectionStatusLabel: String = "OFFLINE",
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val messages: List<TransceiverMessage> = emptyList(),
    val peerCapabilities: PeerCapabilities = PeerCapabilities(),
    val secureState: SecureSessionState = SecureSessionState.NO_SESSION,
    val sasCode: String? = null
)

class TransceiverViewModel(
    private val context: Context,
    private val languagePackRepository: LanguagePackRepository,
    private val metricsRecorder: MetricsRecorder,
    private val transportEngine: TransportEngine,
    private val coordinator: TransceiverCoordinator
) : ViewModel() {

    private val modeState = MutableStateFlow(TransceiverMode.PTT)
    private val isTransmittingState = MutableStateFlow(false)
    private val partialTranscriptState = MutableStateFlow("")
    private val finalTranscriptState = MutableStateFlow("")

    val uiState: StateFlow<TransceiverUiState> = combine(
        languagePackRepository.observeActiveLanguage(),
        modeState,
        isTransmittingState,
        metricsRecorder.latest,
        partialTranscriptState,
        finalTranscriptState,
        transportEngine.observeConnectionState(),
        coordinator.messages,
        coordinator.peerCapabilities,
        coordinator.secureSessionManager.state,
        coordinator.secureSessionManager.sasCode
    ) { args: Array<Any?> ->
        val activeCode = args[0] as com.itantra.domain.model.LanguageCode?
        val mode = args[1] as TransceiverMode
        val isTransmitting = args[2] as Boolean
        val metrics = args[3] as com.itantra.domain.model.InferenceMetrics
        val partial = args[4] as String
        val finalTxt = args[5] as String
        val connState = args[6] as ConnectionState
        @Suppress("UNCHECKED_CAST")
        val messagesList = args[7] as List<TransceiverMessage>
        val caps = args[8] as PeerCapabilities
        val secureState = args[9] as SecureSessionState
        val sasCode = args[10] as String?

        val connLabel = when (connState) {
            ConnectionState.CONNECTED -> "CONNECTED"
            ConnectionState.CONNECTING -> "CONNECTING..."
            ConnectionState.LISTENING -> "LISTENING..."
            ConnectionState.DISCONNECTED -> "OFFLINE"
            ConnectionState.ERROR -> "ERROR"
        }

        TransceiverUiState(
            activeLanguage = activeCode?.let { LanguageCatalog.byCode(it) },
            mode = mode,
            isTransmitting = isTransmitting,
            metrics = metrics,
            connectionStatusLabel = connLabel,
            partialTranscript = partial,
            finalTranscript = finalTxt,
            connectionState = connState,
            messages = messagesList,
            peerCapabilities = caps,
            secureState = secureState,
            sasCode = sasCode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransceiverUiState(),
    )

    init {
    }

    fun selectMode(mode: TransceiverMode) {
        modeState.value = mode
        coordinator.setContinuousMode(mode == TransceiverMode.CONTINUOUS)
    }

    fun onHoldToTalkPressed() {
        coordinator.startRecording()
    }

    fun onHoldToTalkReleased() {
        // Coordinator manages timeouts, but UI can hint release
        // We find the currently recording message
        val msg = coordinator.messages.value.find { it.state == com.itantra.domain.model.MessageState.RECORDING }
        if (msg != null) {
            coordinator.stopRecording(msg.messageId)
        }
    }

    fun confirmPeerVerification() {
        coordinator.confirmPeerVerification()
    }

    fun rejectPeerVerification() {
        coordinator.secureSessionManager.rejectSas()
    }

    fun sendEmergencyCode(code: com.itantra.domain.model.EmergencyCode) {
        coordinator.sendEmergencyCode(code)
    }

    fun sendHumanAck(msgId: Long) {
        coordinator.sendHumanAck(msgId)
    }



    fun sendManualTestText(text: String) {
        viewModelScope.launch {
            if (transportEngine.isConnected && text.isNotBlank()) {
                finalTranscriptState.value = "You: $text"
                // For test sending directly if needed without coordinator,
                // but actually we should just inject it through coordinator if possible.
                // Keeping this simple for the test box.
                val payload = text.toByteArray(Charsets.UTF_8)
                val txMetrics = transportEngine.send(com.itantra.core.transport.packet.ItantraPacket(
                    type = com.itantra.core.transport.packet.PacketType.TEXT,
                    messageId = SystemClock.elapsedRealtime(),
                    payload = payload
                ))
                metricsRecorder.recordTransmission(txMetrics)
            }
        }
    }
}
