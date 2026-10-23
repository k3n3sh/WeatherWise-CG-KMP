package com.weatherwise.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.weatherwise.android.navigation.AppNavHost
import com.weatherwise.android.ui.theme.WeatherWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherWiseTheme {
                AppNavHost()
            }
        }
    }
}
