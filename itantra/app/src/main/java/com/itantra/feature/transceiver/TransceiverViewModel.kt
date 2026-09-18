package com.itantra.feature.transceiver

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
    val targetLanguage: Language? = null,
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
    val sasCode: String? = null,
    val continuousListenState: com.itantra.core.inference.ContinuousListenState = com.itantra.core.inference.ContinuousListenState.OFF
)

class TransceiverViewModel(
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
        languagePackRepository.observeTargetLanguage(),
        modeState,
        isTransmittingState,
        metricsRecorder.latest,
        partialTranscriptState,
        finalTranscriptState,
        transportEngine.observeConnectionState(),
        coordinator.messages,
        coordinator.peerCapabilities,
        coordinator.secureSessionManager.state,
        coordinator.secureSessionManager.sasCode,
        coordinator.continuousListenEngine.state
    ) { args: Array<Any?> ->
        val activeCode = args[0] as com.itantra.domain.model.LanguageCode?
        val targetCode = args[1] as com.itantra.domain.model.LanguageCode?
        val mode = args[2] as TransceiverMode
        val isTransmitting = args[3] as Boolean
        val metrics = args[4] as com.itantra.domain.model.InferenceMetrics
        val partial = args[5] as String
        val finalTxt = args[6] as String
        val connState = args[7] as ConnectionState
        @Suppress("UNCHECKED_CAST")
        val messagesList = args[8] as List<TransceiverMessage>
        val caps = args[9] as PeerCapabilities
        val secureState = args[10] as SecureSessionState
        val sasCode = args[11] as String?
        val continuousListenState = args[12] as com.itantra.core.inference.ContinuousListenState

        val connLabel = when (connState) {
            ConnectionState.CONNECTED -> "CONNECTED"
            ConnectionState.CONNECTING -> "CONNECTING..."
            ConnectionState.LISTENING -> "LISTENING..."
            ConnectionState.DISCONNECTED -> "OFFLINE"
            ConnectionState.ERROR -> "ERROR"
        }

        TransceiverUiState(
            activeLanguage = activeCode?.let { LanguageCatalog.byCode(it) },
            targetLanguage = targetCode?.let { LanguageCatalog.byCode(it) },
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
            sasCode = sasCode,
            continuousListenState = continuousListenState
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

    fun setSourceLanguage(code: com.itantra.domain.model.LanguageCode) {
        viewModelScope.launch {
            languagePackRepository.setActiveLanguage(code)
        }
    }

    fun setTargetLanguage(code: com.itantra.domain.model.LanguageCode) {
        viewModelScope.launch {
            languagePackRepository.setTargetLanguage(code)
        }
    }

    fun onHoldToTalkCriticalPressed() {
        coordinator.startRecording(isCritical = true)
    }

    fun confirmCriticalMessage(msgId: Long) {
        coordinator.sendVoiceMessage(msgId)
    }

    fun cancelCriticalMessage(msgId: Long) {
        coordinator.cancelMessage(msgId)
    }
}
