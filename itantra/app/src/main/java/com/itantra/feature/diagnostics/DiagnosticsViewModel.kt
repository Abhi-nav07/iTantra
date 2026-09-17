package com.itantra.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.metrics.MetricsRecorder
import com.itantra.domain.model.InferenceMetrics
import com.itantra.domain.model.Language
import com.itantra.domain.model.LanguageCatalog
import com.itantra.domain.repository.LanguagePackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiagnosticsUiState(
    val activeLanguage: Language? = null,
    val metrics: InferenceMetrics = InferenceMetrics(),
    val installedPackCount: Int = 0,
)

class DiagnosticsViewModel(
    private val metricsRecorder: MetricsRecorder,
    private val languagePackRepository: LanguagePackRepository,
) : ViewModel() {

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        languagePackRepository.observeActiveLanguage(),
        languagePackRepository.observePackSummaries(),
        metricsRecorder.latest,
    ) { activeCode, packs, metrics ->
        DiagnosticsUiState(
            activeLanguage = activeCode?.let { LanguageCatalog.byCode(it) },
            metrics = metrics,
            installedPackCount = packs.count { it.isDownloaded },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiagnosticsUiState(),
    )
}
