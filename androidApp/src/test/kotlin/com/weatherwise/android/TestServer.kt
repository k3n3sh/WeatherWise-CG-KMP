package com.weatherwise.android

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.weatherwise.shared.data.OpenMeteoClient
import com.weatherwise.shared.data.RecentCitiesStore
import com.weatherwise.shared.data.WeatherRepository
import com.weatherwise.shared.model.City
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.io.IOException
import kotlin.time.Clock

fun city(id: Long = 1, name: String = "London"): City {
    return City(id, name, "England", "United Kingdom", 51.5, -0.12)
}

/**
 * A pretend Open-Meteo server for ViewModel tests.
 * Tests change its fields to decide what the next request gets.
 */
class TestServer {
    var isOnline = true
    var replyDelayMillis = 0L
    var citiesByQuery: Map<String, List<City>> = emptyMap()
    val requestedQueries = mutableListOf<String>()
    val completedQueries = mutableListOf<String>()
}

/** DataStore kept in memory, so tests are fast and never wait on real disk threads. */
class InMemoryDataStore : DataStore<Preferences> {
    private val current = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = current

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(current.value)
        current.value = updated
        return updated
    }
}

class TestBackend(val repository: WeatherRepository, val store: RecentCitiesStore)

/** The real repository and store, wired to [server] and a fresh DataStore file. */
fun TestScope.testBackend(server: TestServer): TestBackend {
    val engine = MockEngine(
        MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                if (!server.isOnline) throw IOException("offline")
                val query = request.url.parameters["name"]
                if (query != null) server.requestedQueries.add(query)
                delay(server.replyDelayMillis)
                if (query != null) server.completedQueries.add(query)

                val body = when (request.url.host) {
                    "geocoding.test" -> geocodingJson(server.citiesByQuery[query].orEmpty())
                    "marine.test" -> MARINE_JSON
                    else -> FORECAST_JSON
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        },
    )
    val client = OpenMeteoClient(
        engine = engine,
        language = "en",
        geocodingUrl = "https://geocoding.test",
        forecastUrl = "https://forecast.test",
        marineUrl = "https://marine.test",
    )
    val store = RecentCitiesStore(InMemoryDataStore(), Clock.System)
    return TestBackend(WeatherRepository(client, store, Clock.System), store)
}

private fun geocodingJson(cities: List<City>): String {
    if (cities.isEmpty()) return """{"generationtime_ms":0.4}"""
    val results = cities.joinToString(",") { city ->
        """{"id":${city.id},"name":"${city.name}","latitude":${city.latitude},"longitude":${city.longitude},"country":"${city.country}","admin1":"${city.region}"}"""
    }
    return """{"results":[$results]}"""
}

private const val FORECAST_JSON = """
    {"timezone":"Europe/London",
     "daily":{"time":["2026-10-22","2026-10-23"],"weather_code":[0,61],"temperature_2m_max":[20.0,12.0],
              "precipitation_sum":[0.0,6.5],"snowfall_sum":[0.0,0.0],"wind_speed_10m_max":[10.0,32.0],
              "wind_gusts_10m_max":[20.0,55.0]}}
"""

private const val MARINE_JSON = """{"daily":{"time":["2026-10-22"],"wave_height_max":[1.5],"wave_period_max":[11.0]}}"""
