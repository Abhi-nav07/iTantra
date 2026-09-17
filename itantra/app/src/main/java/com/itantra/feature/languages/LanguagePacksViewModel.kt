package com.itantra.feature.languages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.LanguagePackSummary
import com.itantra.domain.repository.LanguagePackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LanguagePacksUiState(
    val packs: List<LanguagePackSummary> = emptyList(),
)

class LanguagePacksViewModel(
    private val repository: LanguagePackRepository,
) : ViewModel() {

    val uiState: StateFlow<LanguagePacksUiState> = repository.observePackSummaries()
        .map { packs -> LanguagePacksUiState(packs = packs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LanguagePacksUiState(),
        )

    /**
     * Only meaningful for packs that are already DOWNLOADED (mock-installed).
     * For NOT_INSTALLED packs this is a no-op today — Task 01 does not
     * implement download, so there is nothing to "activate" yet, and the
     * screen must not pretend otherwise.
     */
    fun activateLanguage(code: LanguageCode) {
        viewModelScope.launch {
            repository.setActiveLanguage(code)
        }
    }
}
