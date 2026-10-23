package com.weatherwise.shared.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDomainMask
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun platformModule(): Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single {
        val path = documentsFolder() + "/" + DATASTORE_FILE_NAME
        PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
    }
}

actual fun deviceLanguage(): String = NSLocale.currentLocale.languageCode

@OptIn(ExperimentalForeignApi::class)
private fun documentsFolder(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "Documents folder unavailable" }
}
