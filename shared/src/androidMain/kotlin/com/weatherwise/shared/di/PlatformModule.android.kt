package com.weatherwise.shared.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Locale

actual fun platformModule(): Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single {
        val file = androidContext().filesDir.resolve(DATASTORE_FILE_NAME)
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
    }
}

actual fun deviceLanguage(): String = Locale.getDefault().language
