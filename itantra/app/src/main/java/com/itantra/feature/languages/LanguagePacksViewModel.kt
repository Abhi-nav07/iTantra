package com.itantra.feature.languages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.inference.ActiveLanguageSessionManager
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.LanguagePackSummary
import com.itantra.domain.repository.LanguagePackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LanguagePacksUiState(
    val packs: List<LanguagePackSummary> = emptyList(),
)

class LanguagePacksViewModel(
    private val repository: LanguagePackRepository,
    private val sessionManager: ActiveLanguageSessionManager? = null,
) : ViewModel() {

    val uiState: StateFlow<LanguagePacksUiState> = repository.observePackSummaries()
        .map { packs -> LanguagePacksUiState(packs = packs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LanguagePacksUiState(),
        )

    /**
     * Activates the selected language pack in repository and switches active inference
     * engines in [sessionManager].
     */
    fun activateLanguage(code: LanguageCode) {
        viewModelScope.launch {
            val success = repository.setActiveLanguage(code)
            if (success && sessionManager != null) {
                val pack = repository.observePackSummaries().firstOrNull()?.find { it.language.code == code }
                val loadStt = pack?.isSttDownloaded ?: true
                val loadTts = pack?.isTtsDownloaded ?: true
                try {
                    sessionManager.switchTo(code, loadStt = loadStt, loadTts = loadTts)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
