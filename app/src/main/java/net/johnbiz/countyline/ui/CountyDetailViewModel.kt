package net.johnbiz.countyline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.johnbiz.countyline.county.CountyDetail
import net.johnbiz.countyline.county.CountyDetailSource

sealed interface CountyDetailUiState {
    data object Loading : CountyDetailUiState
    data object NotFound : CountyDetailUiState
    data class Loaded(val detail: CountyDetail) : CountyDetailUiState
}

class CountyDetailViewModel(fips: String, source: CountyDetailSource) : ViewModel() {

    private val state = MutableStateFlow<CountyDetailUiState>(CountyDetailUiState.Loading)
    val uiState: StateFlow<CountyDetailUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = source.load(fips)?.let(CountyDetailUiState::Loaded) ?: CountyDetailUiState.NotFound
        }
    }
}
