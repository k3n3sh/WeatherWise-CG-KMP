package com.weatherwise.shared.data

import com.weatherwise.shared.support.FakeClock
import com.weatherwise.shared.support.city
import com.weatherwise.shared.support.day
import com.weatherwise.shared.support.forecast
import com.weatherwise.shared.support.InMemoryDataStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class RecentCitiesStoreTest {

    private val clock = FakeClock()
    private val london = city(1, "London")
    private val lisbon = city(2, "Lisbon")
    private val zermatt = city(3, "Zermatt")
    private val paris = city(4, "Paris")

    @Test
    fun `keeps the newest city first and moves a repeat to the top and holds at most three`() = runTest {
        val store = RecentCitiesStore(InMemoryDataStore(), clock)
        val now = clock.now().toEpochMilliseconds()
        suspend fun names() = store.recentCities().map { it.city.name }

        store.save(forecast(london, fetchedAtMillis = now))
        store.save(forecast(lisbon, fetchedAtMillis = now))
        store.save(forecast(zermatt, fetchedAtMillis = now))
        assertEquals(listOf("Zermatt", "Lisbon", "London"), names())

        store.save(forecast(london, fetchedAtMillis = now))
        assertEquals(listOf("London", "Zermatt", "Lisbon"), names())

        store.save(forecast(paris, fetchedAtMillis = now))
        assertEquals(listOf("Paris", "London", "Zermatt"), names())
    }

    @Test
    fun `entries older than 24 hours are removed on read and deleted from storage`() = runTest {
        val store = RecentCitiesStore(InMemoryDataStore(), clock)
        store.save(forecast(london, fetchedAtMillis = clock.now().toEpochMilliseconds()))

        clock.advanceBy(24.hours)
        assertEquals(listOf("London"), store.recentCities().map { it.city.name })

        clock.advanceBy(1.milliseconds)
        assertEquals(emptyList(), store.recentCities())
        assertEquals(null, store.savedForecast(london.id))
    }

    @Test
    fun `days already over are hidden using the city's own timezone`() = runTest {
        val store = RecentCitiesStore(InMemoryDataStore(), clock)
        val week = (21..27).map { day(date = LocalDate(2026, 10, it)) }
        store.save(forecast(london, timezone = "Asia/Tokyo", days = week, fetchedAtMillis = clock.now().toEpochMilliseconds()))
        // 16:00 UTC on the 22nd is already 01:00 on the 23rd in Tokyo.
        clock.now = Instant.parse("2026-10-22T16:00:00Z")

        val days = store.savedForecast(london.id)!!.days

        assertEquals(LocalDate(2026, 10, 23), days.first().date)
        assertEquals(5, days.size)
    }
}
