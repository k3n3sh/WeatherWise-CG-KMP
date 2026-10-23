package com.weatherwise.android

import android.app.Application
import com.weatherwise.android.di.appModule
import com.weatherwise.shared.di.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WeatherWiseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WeatherWiseApp)
            modules(sharedModules() + appModule)
        }
    }
}
