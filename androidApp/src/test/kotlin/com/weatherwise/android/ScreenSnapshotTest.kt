package com.weatherwise.android

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.weatherwise.android.ui.forecast.ForecastContent
import com.weatherwise.android.ui.forecast.ForecastScreenState
import com.weatherwise.android.ui.search.SearchContent
import com.weatherwise.android.ui.search.SearchScreenState
import com.weatherwise.android.ui.theme.WeatherWiseTheme
import com.weatherwise.shared.model.DailyWeather
import com.weatherwise.shared.model.RecentCity
import com.weatherwise.shared.scoring.rankActivities
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import kotlin.test.Test

class ScreenSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val zermatt = city(3, "Zermatt").copy(region = "Valais", country = "Switzerland")
    private val savedAt = 1_792_677_900_000L // 22 Oct 2026, 14:05 UTC

    private val week = (0 until 7).map { offset ->
        DailyWeather(
            date = LocalDate(2026, 10, 22).plus(DatePeriod(days = offset)),
            weatherCode = if (offset == 3) 95 else 73,
            maxTempC = -4.0 + offset,
            precipitationMm = 2.0,
            snowfallCm = if (offset < 3) 6.0 else 0.0,
            snowDepthM = 0.9,
            maxWindKmh = 15.0 + offset * 3,
            maxGustKmh = 30.0,
        )
    }

    @Test
    fun searchStart() = paparazzi.snapshot {
        WeatherWiseTheme {
            SearchContent(
                state = SearchScreenState(recents = listOf(RecentCity(zermatt, savedAt), RecentCity(city(), savedAt))),
                onQueryChanged = {},
                onSearchOpened = {},
                onSearchClosed = {},
                onRetry = {},
                onCitySelected = {},
            )
        }
    }

    @Test
    fun forecastFromSavedData() = paparazzi.snapshot {
        WeatherWiseTheme {
            ForecastContent(
                city = zermatt,
                state = ForecastScreenState.Ready(rankActivities(week), fetchedAtMillis = savedAt, isFromCache = true),
                onBack = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun forecastDarkMode() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        paparazzi.snapshot {
            WeatherWiseTheme {
                ForecastContent(
                    city = zermatt,
                    state = ForecastScreenState.Ready(rankActivities(week), fetchedAtMillis = savedAt, isFromCache = false),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
    }
}
