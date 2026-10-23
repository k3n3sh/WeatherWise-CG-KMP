import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    android {
        namespace = "com.weatherwise.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            api(libs.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            api(libs.koin.core)
            implementation(libs.datastore.preferences)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

buildkonfig {
    packageName = "com.weatherwise.shared"
    defaultConfigs {
        buildConfigField(STRING, "GEOCODING_BASE_URL", "https://geocoding-api.open-meteo.com")
        buildConfigField(STRING, "FORECAST_BASE_URL", "https://api.open-meteo.com")
        buildConfigField(STRING, "MARINE_BASE_URL", "https://marine-api.open-meteo.com")
    }
}
