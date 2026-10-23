package com.weatherwise.shared.data

import com.weatherwise.shared.core.Outcome
import com.weatherwise.shared.core.safeCall
import com.weatherwise.shared.model.City
import com.weatherwise.shared.model.RecentCity
import com.weatherwise.shared.model.WeeklyForecast
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** The one place screens get data from. Network first, saved copy as a fallback. */
class WeatherRepository(
    private val client: OpenMeteoClient,
    private val store: RecentCitiesStore,
    private val clock: Clock,
) {
    suspend fun searchCities(query: String): Outcome<List<City>> {
        return safeCall { client.searchCities(query) }
    }

    suspend fun recentCities(): List<RecentCity> {
        return store.recentCities()
    }

    suspend fun weeklyForecast(city: City): Outcome<WeeklyForecast> {
        val now = clock.now().toEpochMilliseconds()
        val result = safeCall {
            coroutineScope {
                // Waves come from a second API, so ask for both at the same time.
                val waves = async { wavesOrEmpty(city) }
                val forecast = client.weeklyForecast(city, now)
                addWaves(forecast, waves.await())
            }
        }

        if (result is Outcome.Success) {
            store.save(result.value)
            return result
        }

        // The network failed. Use the copy saved for this city, if there is one.
        val saved = store.savedForecast(city.id)
        if (saved != null) return Outcome.Success(saved)
        return result
    }

    // Surfing is one of four activities, so a failed marine call shouldn't fail the whole screen.
    private suspend fun wavesOrEmpty(city: City): Map<LocalDate, Waves> {
        val result = safeCall { client.weeklyWaves(city) }
        if (result is Outcome.Success) return result.value
        return emptyMap()
    }

    // Joined by date, not position: the marine data may start later or miss a day.
    private fun addWaves(forecast: WeeklyForecast, wavesByDate: Map<LocalDate, Waves>): WeeklyForecast {
        val days = forecast.days.map { day ->
            val waves = wavesByDate[day.date]
            day.copy(waveHeightM = waves?.heightM, wavePeriodS = waves?.periodS)
        }
        return forecast.copy(days = days)
    }
}
