package com.weatherwise.shared.di

import com.weatherwise.shared.BuildKonfig
import com.weatherwise.shared.data.OpenMeteoClient
import com.weatherwise.shared.data.RecentCitiesStore
import com.weatherwise.shared.data.WeatherRepository
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

const val DATASTORE_FILE_NAME = "weatherwise.preferences_pb"

/** Provides the HTTP engine and DataStore, which are built differently on Android and iOS. */
expect fun platformModule(): Module

/** The device language, so city names come back in the user's language. */
expect fun deviceLanguage(): String

val sharedModule = module {
    single<Clock> { Clock.System }
    single {
        OpenMeteoClient(
            engine = get(),
            language = deviceLanguage(),
            geocodingUrl = BuildKonfig.GEOCODING_BASE_URL,
            forecastUrl = BuildKonfig.FORECAST_BASE_URL,
            marineUrl = BuildKonfig.MARINE_BASE_URL,
        )
    }
    single { RecentCitiesStore(dataStore = get(), clock = get()) }
    single { WeatherRepository(client = get(), store = get(), clock = get()) }
}

fun sharedModules(): List<Module> = listOf(platformModule(), sharedModule)
