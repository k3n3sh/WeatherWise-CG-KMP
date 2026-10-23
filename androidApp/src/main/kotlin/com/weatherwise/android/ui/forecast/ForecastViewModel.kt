package com.weatherwise.android.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weatherwise.shared.core.AppError
import com.weatherwise.shared.core.Outcome
import com.weatherwise.shared.data.WeatherRepository
import com.weatherwise.shared.model.ActivityRanking
import com.weatherwise.shared.model.City
import com.weatherwise.shared.scoring.rankActivities
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ForecastScreenState {
    data object Loading : ForecastScreenState

    /** [isFromCache] means the network failed and this is the saved copy from [fetchedAtMillis]. */
    data class Ready(
        val rankings: List<ActivityRanking>,
        val fetchedAtMillis: Long,
        val isFromCache: Boolean,
    ) : ForecastScreenState

    data class Failed(val error: AppError) : ForecastScreenState
}

/** [city] comes from the navigation route, which Android restores after the app is killed. */
class ForecastViewModel(
    val city: City,
    private val repository: WeatherRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ForecastScreenState>(ForecastScreenState.Loading)
    val state: StateFlow<ForecastScreenState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    private fun load() {
        // Never let two loads race each other.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = ForecastScreenState.Loading
            when (val outcome = repository.weeklyForecast(city)) {
                is Outcome.Success -> {
                    val forecast = outcome.value
                    _state.value = ForecastScreenState.Ready(
                        rankings = rankActivities(forecast.days),
                        fetchedAtMillis = forecast.fetchedAtMillis,
                        isFromCache = forecast.isFromCache,
                    )
                }
                is Outcome.Failure -> _state.value = ForecastScreenState.Failed(outcome.error)
            }
        }
    }
}
