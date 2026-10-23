package com.weatherwise.shared.support

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import com.weatherwise.shared.data.OpenMeteoClient
import com.weatherwise.shared.data.RecentCitiesStore
import com.weatherwise.shared.data.WeatherRepository
import com.weatherwise.shared.model.City
import com.weatherwise.shared.model.DailyWeather
import com.weatherwise.shared.model.WeeklyForecast
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

val TODAY = LocalDate(2026, 10, 22)

fun day(
    date: LocalDate = TODAY,
    weatherCode: Int? = 0,
    maxTempC: Double? = 20.0,
    precipitationMm: Double? = 0.0,
    snowfallCm: Double? = 0.0,
    snowDepthM: Double? = 0.0,
    maxWindKmh: Double? = 10.0,
    maxGustKmh: Double? = 20.0,
    waveHeightM: Double? = null,
    wavePeriodS: Double? = null,
): DailyWeather {
    return DailyWeather(date, weatherCode, maxTempC, precipitationMm, snowfallCm, snowDepthM, maxWindKmh, maxGustKmh, waveHeightM, wavePeriodS)
}

fun city(id: Long = 1, name: String = "London"): City {
    return City(id, name, "England", "United Kingdom", 51.5, -0.12)
}

fun forecast(
    city: City = city(),
    timezone: String = "Europe/London",
    days: List<DailyWeather> = listOf(day()),
    fetchedAtMillis: Long = 0,
): WeeklyForecast {
    return WeeklyForecast(city, timezone, days, fetchedAtMillis)
}

class FakeClock(var now: Instant = Instant.parse("2026-10-22T09:00:00Z")) : Clock {
    override fun now(): Instant = now

    fun advanceBy(duration: Duration) {
        now += duration
    }
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

/**
 * The real repository, talking to a fake server.
 * [server] decides what each request gets back; it runs on the test clock, so delays are instant.
 */
fun TestScope.testRepository(clock: Clock = FakeClock(), server: MockRequestHandler): WeatherRepository {
    val engine = MockEngine(
        MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(server)
        },
    )
    val client = OpenMeteoClient(
        engine = engine,
        language = "en",
        geocodingUrl = "https://geocoding.test",
        forecastUrl = "https://forecast.test",
        marineUrl = "https://marine.test",
    )
    return WeatherRepository(client, RecentCitiesStore(InMemoryDataStore(), clock), clock)
}

fun MockRequestHandleScope.jsonReply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

object Fixtures {
    const val GEOCODING_LONDON = """
        {"results":[{"id":2643743,"name":"London","latitude":51.50853,"longitude":-0.12574,
        "country":"United Kingdom","admin1":"England","timezone":"Europe/London"}],"generationtime_ms":0.6}
    """

    // Open-Meteo leaves "results" out entirely when nothing matches.
    const val GEOCODING_NO_MATCH = """{"generationtime_ms":0.4}"""

    const val FORECAST_TWO_DAYS = """
        {"latitude":51.5,"longitude":-0.12,"timezone":"Europe/London",
         "daily":{"time":["2026-10-22","2026-10-23"],"weather_code":[3,61],"temperature_2m_max":[14.2,12.0],
                  "precipitation_sum":[0.0,6.5],"snowfall_sum":[0.0,0.0],"wind_speed_10m_max":[18.0,32.0],
                  "wind_gusts_10m_max":[30.0,55.0]},
         "hourly":{"time":["2026-10-22T00:00","2026-10-22T12:00","2026-10-23T00:00","2026-10-23T12:00"],
                   "snow_depth":[0.10,0.30,null,0.05]}}
    """

    // Starts a day later than the forecast on purpose, to prove the join is by date.
    const val MARINE_FROM_SECOND_DAY = """
        {"daily":{"time":["2026-10-23","2026-10-24"],"wave_height_max":[1.2,2.0],"wave_period_max":[9.0,11.0]}}
    """
}
