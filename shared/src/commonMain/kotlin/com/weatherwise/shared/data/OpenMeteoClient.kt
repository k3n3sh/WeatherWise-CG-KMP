package com.weatherwise.shared.data

import com.weatherwise.shared.core.MalformedResponseException
import com.weatherwise.shared.model.City
import com.weatherwise.shared.model.DailyWeather
import com.weatherwise.shared.model.WeeklyForecast
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Waves(val heightM: Double?, val periodS: Double?)

/**
 * Talks to the three Open-Meteo APIs and turns their JSON into our models.
 * Any function here can throw; WeatherRepository catches and turns errors into an Outcome.
 */
class OpenMeteoClient(
    engine: HttpClientEngine,
    private val language: String,
    private val geocodingUrl: String,
    private val forecastUrl: String,
    private val marineUrl: String,
) {
    private val http = HttpClient(engine) {
        // Throw on any non-2xx response, so an error page is never parsed as data.
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 15_000
            requestTimeoutMillis = 20_000
        }
    }

    suspend fun searchCities(name: String): List<City> {
        val response: GeocodingJson = http.get("$geocodingUrl/v1/search") {
            parameter("name", name)
            parameter("count", 10)
            parameter("language", language)
        }.body()

        // Open-Meteo leaves "results" out entirely when nothing matches.
        val results = response.results
        if (results == null) return emptyList()

        return results.map { result ->
            City(
                id = result.id,
                name = result.name,
                region = result.admin1,
                country = result.country,
                latitude = result.latitude,
                longitude = result.longitude,
            )
        }
    }

    suspend fun weeklyForecast(city: City, fetchedAtMillis: Long): WeeklyForecast {
        val response: ForecastJson = http.get("$forecastUrl/v1/forecast") {
            parameter("latitude", city.latitude)
            parameter("longitude", city.longitude)
            parameter(
                "daily",
                "weather_code,temperature_2m_max,precipitation_sum,snowfall_sum,wind_speed_10m_max,wind_gusts_10m_max",
            )
            // Snow depth only exists hourly, so we keep each day's highest value below.
            parameter("hourly", "snow_depth")
            // "auto" makes a day mean a day in that city, not on the phone.
            parameter("timezone", "auto")
            parameter("forecast_days", 7)
            parameter("temperature_unit", "celsius")
            parameter("wind_speed_unit", "kmh")
            parameter("precipitation_unit", "mm")
        }.body()

        // Open-Meteo sends parallel arrays: time[i] belongs with every other array's [i].
        val daily = response.daily
        val dayCount = daily.time.size
        checkSize(dayCount, daily.weatherCode.size)
        checkSize(dayCount, daily.maxTemp.size)
        checkSize(dayCount, daily.precipitation.size)
        checkSize(dayCount, daily.snowfall.size)
        checkSize(dayCount, daily.maxWind.size)
        checkSize(dayCount, daily.maxGust.size)

        val snowDepthByDate = highestSnowDepthPerDay(response.hourly)

        val days = mutableListOf<DailyWeather>()
        for (i in 0 until dayCount) {
            val date = LocalDate.parse(daily.time[i])
            days.add(
                DailyWeather(
                    date = date,
                    weatherCode = daily.weatherCode.getOrNull(i),
                    maxTempC = daily.maxTemp.getOrNull(i),
                    precipitationMm = daily.precipitation.getOrNull(i),
                    snowfallCm = daily.snowfall.getOrNull(i),
                    snowDepthM = snowDepthByDate[date],
                    maxWindKmh = daily.maxWind.getOrNull(i),
                    maxGustKmh = daily.maxGust.getOrNull(i),
                ),
            )
        }
        return WeeklyForecast(city = city, timezone = response.timezone, days = days, fetchedAtMillis = fetchedAtMillis)
    }

    suspend fun weeklyWaves(city: City): Map<LocalDate, Waves> {
        val response: MarineJson = http.get("$marineUrl/v1/marine") {
            parameter("latitude", city.latitude)
            parameter("longitude", city.longitude)
            parameter("daily", "wave_height_max,wave_period_max")
            parameter("timezone", "auto")
            parameter("forecast_days", 7)
        }.body()

        val daily = response.daily
        if (daily == null) return emptyMap()
        checkSize(daily.time.size, daily.waveHeight.size)
        checkSize(daily.time.size, daily.wavePeriod.size)

        val wavesByDate = mutableMapOf<LocalDate, Waves>()
        for (i in daily.time.indices) {
            val date = LocalDate.parse(daily.time[i])
            wavesByDate[date] = Waves(heightM = daily.waveHeight.getOrNull(i), periodS = daily.wavePeriod.getOrNull(i))
        }
        return wavesByDate
    }

    private fun highestSnowDepthPerDay(hourly: HourlyJson?): Map<LocalDate, Double> {
        val highest = mutableMapOf<LocalDate, Double>()
        if (hourly == null) return highest
        checkSize(hourly.time.size, hourly.snowDepth.size)

        for (i in hourly.time.indices) {
            val depth = hourly.snowDepth.getOrNull(i)
            if (depth == null) continue

            // Hourly times look like "2026-10-22T13:00"; the first 10 characters are the date.
            val date = LocalDate.parse(hourly.time[i].take(10))
            val current = highest[date]
            if (current == null || depth > current) {
                highest[date] = depth
            }
        }
        return highest
    }

    // A missing key arrives as an empty list (no data). A list of the wrong length is a broken response.
    private fun checkSize(expected: Int, actual: Int) {
        if (actual != 0 && actual != expected) {
            throw MalformedResponseException("Expected $expected values but got $actual")
        }
    }
}

// ---------- JSON shapes, exactly as Open-Meteo sends them ----------

@Serializable
private class GeocodingJson(val results: List<CityJson>? = null)

@Serializable
private class CityJson(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

@Serializable
private class ForecastJson(
    val timezone: String,
    val daily: DailyJson,
    val hourly: HourlyJson? = null,
)

@Serializable
private class DailyJson(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val maxTemp: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitation: List<Double?> = emptyList(),
    @SerialName("snowfall_sum") val snowfall: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m_max") val maxWind: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m_max") val maxGust: List<Double?> = emptyList(),
)

@Serializable
private class HourlyJson(
    val time: List<String>,
    @SerialName("snow_depth") val snowDepth: List<Double?> = emptyList(),
)

@Serializable
private class MarineJson(val daily: MarineDailyJson? = null)

@Serializable
private class MarineDailyJson(
    val time: List<String>,
    @SerialName("wave_height_max") val waveHeight: List<Double?> = emptyList(),
    @SerialName("wave_period_max") val wavePeriod: List<Double?> = emptyList(),
)
