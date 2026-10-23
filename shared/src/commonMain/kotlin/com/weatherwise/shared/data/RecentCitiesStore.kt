package com.weatherwise.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.weatherwise.shared.model.DailyWeather
import com.weatherwise.shared.model.RecentCity
import com.weatherwise.shared.model.WeeklyForecast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private const val MAX_RECENT_CITIES = 3
private const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
private val RECENT_FORECASTS_KEY = stringPreferencesKey("recent_forecasts")

/**
 * Remembers the last three cities the user opened, with their full forecast, for 24 hours.
 * Everything is stored as one JSON string in DataStore.
 */
class RecentCitiesStore(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Stops two coroutines reading and writing the list at the same time.
    private val mutex = Mutex()

    suspend fun recentCities(): List<RecentCity> {
        val forecasts = freshForecasts()
        return forecasts.map { forecast -> RecentCity(forecast.city, forecast.fetchedAtMillis) }
    }

    suspend fun savedForecast(cityId: Long): WeeklyForecast? {
        val forecast = freshForecasts().firstOrNull { it.city.id == cityId }
        if (forecast == null) return null
        return forecast.copy(days = daysFromToday(forecast), isFromCache = true)
    }

    suspend fun save(forecast: WeeklyForecast) {
        mutex.withLock {
            // Newest first, no duplicates, at most three.
            val others = readAll().filter { it.city.id != forecast.city.id }
            val updated = listOf(forecast) + others
            writeAll(updated.take(MAX_RECENT_CITIES))
        }
    }

    private suspend fun freshForecasts(): List<WeeklyForecast> {
        mutex.withLock {
            val all = readAll()
            val now = clock.now().toEpochMilliseconds()
            val fresh = all.filter { now - it.fetchedAtMillis <= MAX_AGE_MILLIS }

            // Stale entries are deleted, not just hidden.
            if (fresh.size != all.size) {
                writeAll(fresh)
            }
            return fresh
        }
    }

    // Days that are already over in the city's own timezone are not shown.
    private fun daysFromToday(forecast: WeeklyForecast): List<DailyWeather> {
        val zone = try {
            TimeZone.of(forecast.timezone)
        } catch (e: IllegalTimeZoneException) {
            TimeZone.UTC
        }
        val today = clock.now().toLocalDateTime(zone).date
        return forecast.days.filter { it.date >= today }
    }

    private suspend fun readAll(): List<WeeklyForecast> {
        try {
            val saved = dataStore.data.first()[RECENT_FORECASTS_KEY]
            if (saved == null) return emptyList()
            return json.decodeFromString<List<WeeklyForecast>>(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken or outdated saved list isn't worth crashing over. Start again empty.
            writeAll(emptyList())
            return emptyList()
        }
    }

    private suspend fun writeAll(forecasts: List<WeeklyForecast>) {
        try {
            dataStore.edit { preferences ->
                if (forecasts.isEmpty()) {
                    preferences.remove(RECENT_FORECASTS_KEY)
                } else {
                    preferences[RECENT_FORECASTS_KEY] = json.encodeToString(forecasts)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Saving is a nice-to-have. A failed write must never fail the screen.
        }
    }
}
