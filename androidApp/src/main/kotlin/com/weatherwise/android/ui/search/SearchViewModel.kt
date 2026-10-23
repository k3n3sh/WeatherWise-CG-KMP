package com.weatherwise.android.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weatherwise.shared.core.AppError
import com.weatherwise.shared.core.Outcome
import com.weatherwise.shared.data.WeatherRepository
import com.weatherwise.shared.model.City
import com.weatherwise.shared.model.RecentCity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val SEARCH_DELAY_MILLIS = 350L
private const val MIN_QUERY_LENGTH = 2
private const val KEY_QUERY = "query"
private const val KEY_EXPANDED = "expanded"

data class SearchScreenState(
    val query: String = "",
    val isExpanded: Boolean = false,
    val recents: List<RecentCity> = emptyList(),
    val results: SearchResults = SearchResults.Idle,
)

sealed interface SearchResults {
    /** Nothing to search yet; the screen shows recent cities instead. */
    data object Idle : SearchResults
    data object Loading : SearchResults
    data class Found(val cities: List<City>) : SearchResults
    data class NoMatch(val query: String) : SearchResults
    data class Failed(val error: AppError) : SearchResults
}

class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: WeatherRepository,
) : ViewModel() {

    // The query and the open/closed search box are restored after the app is killed in the background.
    private val _state = MutableStateFlow(
        SearchScreenState(
            query = savedStateHandle[KEY_QUERY] ?: "",
            isExpanded = savedStateHandle[KEY_EXPANDED] ?: false,
        ),
    )
    val state: StateFlow<SearchScreenState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRecentCities()
        startSearch(_state.value.query)
    }

    fun onQueryChanged(text: String) {
        savedStateHandle[KEY_QUERY] = text
        _state.update { it.copy(query = text) }
        startSearch(text)
    }

    fun onSearchOpened() {
        savedStateHandle[KEY_EXPANDED] = true
        _state.update { it.copy(isExpanded = true) }
        loadRecentCities()
    }

    fun onSearchClosed() {
        savedStateHandle[KEY_EXPANDED] = false
        _state.update { it.copy(isExpanded = false) }
        onQueryChanged("")
    }

    fun onRetry() {
        startSearch(_state.value.query)
    }

    // Coming back from a forecast means the recent cities may have changed.
    fun onScreenShown() {
        loadRecentCities()
    }

    private fun startSearch(text: String) {
        // A newer query cancels the older search, including its wait.
        searchJob?.cancel()

        val query = text.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(results = SearchResults.Idle) }
            return
        }

        _state.update { it.copy(results = SearchResults.Loading) }
        searchJob = viewModelScope.launch {
            // Wait until the user stops typing, so we don't call the API on every letter.
            delay(SEARCH_DELAY_MILLIS)
            val results = when (val outcome = repository.searchCities(query)) {
                is Outcome.Success -> if (outcome.value.isEmpty()) SearchResults.NoMatch(query) else SearchResults.Found(outcome.value)
                is Outcome.Failure -> SearchResults.Failed(outcome.error)
            }
            _state.update { it.copy(results = results) }
        }
    }

    private fun loadRecentCities() {
        viewModelScope.launch {
            val recents = repository.recentCities()
            _state.update { it.copy(recents = recents) }
        }
    }
}
