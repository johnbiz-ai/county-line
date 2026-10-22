package net.johnbiz.countyline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.johnbiz.countyline.county.CountyDetail
import net.johnbiz.countyline.county.CountyDetailSource
import net.johnbiz.countyline.county.CurrentPosition
import net.johnbiz.countyline.county.CurrentPositionSource
import kotlin.coroutines.cancellation.CancellationException

sealed interface CountyDetailUiState {
    data object Loading : CountyDetailUiState
    data object NotFound : CountyDetailUiState

    /** [here] is where the user is, set only while they are inside this county. */
    data class Loaded(val detail: CountyDetail, val here: CurrentPosition? = null) : CountyDetailUiState
}

class CountyDetailViewModel(
    private val fips: String,
    source: CountyDetailSource,
    private val position: CurrentPositionSource,
) : ViewModel() {

    private val detail = MutableStateFlow<CountyDetailUiState>(CountyDetailUiState.Loading)
    private val here = MutableStateFlow<CurrentPosition?>(null)
    private var refreshJob: Job? = null

    val uiState: StateFlow<CountyDetailUiState> = combine(detail, here) { d, h ->
        if (d is CountyDetailUiState.Loaded) d.copy(here = h?.takeIf { it.fips == fips }) else d
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CountyDetailUiState.Loading)

    init {
        viewModelScope.launch {
            // A dataset that fails to load is shown as not found rather than crashing the screen.
            val loaded = try {
                source.load(fips)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            detail.value = loaded?.let(CountyDetailUiState::Loaded) ?: CountyDetailUiState.NotFound
        }
    }

    /** Takes a new one-shot fix; called each time the screen comes into view. Failures just hide the dot. */
    fun refreshPosition() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            here.value = try {
                position.current()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }
}
