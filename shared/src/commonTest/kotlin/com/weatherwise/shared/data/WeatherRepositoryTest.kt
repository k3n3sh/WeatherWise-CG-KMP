package com.weatherwise.shared.data

import com.weatherwise.shared.core.AppError
import com.weatherwise.shared.core.Outcome
import com.weatherwise.shared.model.City
import com.weatherwise.shared.model.WeeklyForecast
import com.weatherwise.shared.support.FakeClock
import com.weatherwise.shared.support.Fixtures
import com.weatherwise.shared.support.city
import com.weatherwise.shared.support.jsonReply
import com.weatherwise.shared.support.testRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRepositoryTest {

    @Test
    fun `search turns results into cities and a missing results key into no match`() = runTest {
        val found = testRepository { jsonReply(Fixtures.GEOCODING_LONDON) }.searchCities("London")
        val none = testRepository { jsonReply(Fixtures.GEOCODING_NO_MATCH) }.searchCities("zzq")

        val london = City(2643743, "London", "England", "United Kingdom", 51.50853, -0.12574)
        assertEquals(Outcome.Success(listOf(london)), found)
        assertEquals(Outcome.Success(emptyList()), none)
    }

    @Test
    fun `http and parsing and timeout and connection failures map to the matching AppError`() = runTest {
        assertEquals(Outcome.Failure(AppError.Server(404)), testRepository { jsonReply("{}", HttpStatusCode.NotFound) }.searchCities("London"))
        assertEquals(Outcome.Failure(AppError.Server(500)), testRepository { jsonReply("{}", HttpStatusCode.InternalServerError) }.searchCities("London"))
        assertEquals(Outcome.Failure(AppError.Parsing), testRepository { jsonReply("not json") }.searchCities("London"))
        assertEquals(Outcome.Failure(AppError.Timeout), testRepository { throw HttpRequestTimeoutException("https://geocoding.test", 20_000) }.searchCities("London"))
        assertEquals(Outcome.Failure(AppError.Network), testRepository { throw IOException("offline") }.searchCities("London"))
    }

    @Test
    fun `cancelling a search in flight cancels it instead of turning it into an error`() = runTest {
        val repository = testRepository {
            delay(10_000)
            jsonReply(Fixtures.GEOCODING_LONDON)
        }
        var outcome: Outcome<List<City>>? = null

        val job = launch { outcome = repository.searchCities("London") }
        advanceTimeBy(100)
        job.cancel()
        job.join()

        // If safeCall swallowed the cancellation, the search would carry on and store a Failure here.
        assertNull(outcome)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `forecast arrays become days with the daily highest snow depth and waves joined by date`() = runTest {
        val repository = testRepository { request ->
            if (request.url.host == "marine.test") jsonReply(Fixtures.MARINE_FROM_SECOND_DAY) else jsonReply(Fixtures.FORECAST_TWO_DAYS)
        }

        val result = repository.weeklyForecast(city())

        val days = assertIs<Outcome.Success<WeeklyForecast>>(result).value.days

        assertEquals(listOf(LocalDate(2026, 10, 22), LocalDate(2026, 10, 23)), days.map { it.date })
        assertEquals(14.2, days[0].maxTempC)
        assertEquals(6.5, days[1].precipitationMm)
        assertEquals(0.30, days[0].snowDepthM)
        assertEquals(0.05, days[1].snowDepthM)
        assertNull(days[0].waveHeightM)
        assertEquals(1.2, days[1].waveHeightM)
        assertEquals(9.0, days[1].wavePeriodS)
    }

    @Test
    fun `a failing marine call still returns the forecast without wave data`() = runTest {
        val repository = testRepository { request ->
            if (request.url.host == "marine.test") jsonReply("{}", HttpStatusCode.InternalServerError) else jsonReply(Fixtures.FORECAST_TWO_DAYS)
        }

        val result = repository.weeklyForecast(city())

        val forecast = assertIs<Outcome.Success<WeeklyForecast>>(result).value
        assertEquals(false, forecast.isFromCache)
        assertEquals(2, forecast.days.size)
        assertTrue(forecast.days.all { it.waveHeightM == null })
    }

    @Test
    fun `when the network fails the saved copy is returned or else the error`() = runTest {
        val clock = FakeClock()
        var online = true
        val repository = testRepository(clock) { request ->
            if (!online) throw IOException("offline")
            if (request.url.host == "marine.test") jsonReply(Fixtures.MARINE_FROM_SECOND_DAY) else jsonReply(Fixtures.FORECAST_TWO_DAYS)
        }
        val savedAt = clock.now().toEpochMilliseconds()
        repository.weeklyForecast(city(id = 1))

        online = false
        clock.advanceBy(2.hours)
        val saved = repository.weeklyForecast(city(id = 1))
        val notSaved = repository.weeklyForecast(city(id = 2, name = "Paris"))

        val savedForecast = assertIs<Outcome.Success<WeeklyForecast>>(saved).value
        assertTrue(savedForecast.isFromCache)
        assertEquals(savedAt, savedForecast.fetchedAtMillis)
        assertEquals(Outcome.Failure(AppError.Network), notSaved)
    }
}
