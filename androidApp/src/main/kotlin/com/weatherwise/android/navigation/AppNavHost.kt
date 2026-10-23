package com.weatherwise.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.weatherwise.android.ui.forecast.ForecastScreen
import com.weatherwise.android.ui.search.SearchScreen
import com.weatherwise.shared.model.City
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

/** The whole city travels in the route, so Android can restore it after the app is killed. */
@Serializable
data class ForecastRoute(
    val id: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    constructor(city: City) : this(city.id, city.name, city.region, city.country, city.latitude, city.longitude)

    fun toCity(): City = City(id, name, region, country, latitude, longitude)
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SearchRoute) {
        composable<SearchRoute> {
            SearchScreen(onCitySelected = { city -> navController.navigate(ForecastRoute(city)) })
        }
        composable<ForecastRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ForecastRoute>()
            ForecastScreen(city = route.toCity(), onBack = { navController.popBackStack() })
        }
    }
}
