package com.weatherwise.android.di

import com.weatherwise.android.ui.forecast.ForecastViewModel
import com.weatherwise.android.ui.search.SearchViewModel
import com.weatherwise.shared.model.City
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { SearchViewModel(savedStateHandle = get(), repository = get()) }
    viewModel { parameters -> ForecastViewModel(city = parameters.get<City>(), repository = get()) }
}
