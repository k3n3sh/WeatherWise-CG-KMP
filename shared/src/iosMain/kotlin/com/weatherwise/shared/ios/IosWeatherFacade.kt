package com.weatherwise.shared.ios

import com.weatherwise.shared.core.AppError
import com.weatherwise.shared.core.Outcome
import com.weatherwise.shared.data.WeatherRepository
import com.weatherwise.shared.di.sharedModules
import com.weatherwise.shared.model.ActivityRanking
import com.weatherwise.shared.model.City
import com.weatherwise.shared.scoring.rankActivities
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin

fun startKoinForIos() {
    startKoin { modules(sharedModules()) }
}

// Plain result classes, because generic sealed types like Outcome<T> lose their types in Swift.
class CitySearchResult(val cities: List<City>, val errorMessage: String?)

class RankingResult(
    val rankings: List<ActivityRanking>,
    val savedAtMillis: Long?, // only set when the data came from the saved copy
    val errorMessage: String?,
)

/** The one class Swift talks to. Kotlin suspend functions arrive in Swift as async. */
class IosWeatherFacade : KoinComponent {
    private val repository: WeatherRepository by inject()

    suspend fun search(query: String): CitySearchResult {
        val result = repository.searchCities(query.trim())
        return when (result) {
            is Outcome.Success -> CitySearchResult(cities = result.value, errorMessage = null)
            is Outcome.Failure -> CitySearchResult(cities = emptyList(), errorMessage = errorText(result.error))
        }
    }

    suspend fun ranking(city: City): RankingResult {
        val result = repository.weeklyForecast(city)
        return when (result) {
            is Outcome.Success -> {
                val forecast = result.value
                val savedAt = if (forecast.isFromCache) forecast.fetchedAtMillis else null
                RankingResult(rankings = rankActivities(forecast.days), savedAtMillis = savedAt, errorMessage = null)
            }
            is Outcome.Failure -> RankingResult(rankings = emptyList(), savedAtMillis = null, errorMessage = errorText(result.error))
        }
    }
}

private fun errorText(error: AppError): String {
    return when (error) {
        AppError.Network -> "No internet connection."
        AppError.Timeout -> "The request took too long."
        is AppError.Server -> "Weather service error (${error.code})."
        AppError.Parsing -> "Unexpected response from the weather service."
        AppError.Unknown -> "Something went wrong."
    }
}
