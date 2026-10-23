package com.weatherwise.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class City(
    val id: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val subtitle: String get() = listOfNotNull(region, country).joinToString(", ")
}

/** One local day of weather. Every value can be null because Open-Meteo sends nulls for gaps. */
@Serializable
data class DailyWeather(
    val date: LocalDate,
    val weatherCode: Int? = null,
    val maxTempC: Double? = null,
    val precipitationMm: Double? = null,
    val snowfallCm: Double? = null,
    val snowDepthM: Double? = null,
    val maxWindKmh: Double? = null,
    val maxGustKmh: Double? = null,
    val waveHeightM: Double? = null,
    val wavePeriodS: Double? = null,
)

/** A city's week of weather, plus when it was fetched and whether it came from the saved copy. */
@Serializable
data class WeeklyForecast(
    val city: City,
    val timezone: String,
    val days: List<DailyWeather>,
    val fetchedAtMillis: Long,
    val isFromCache: Boolean = false,
)

data class RecentCity(
    val city: City,
    val fetchedAtMillis: Long,
)

/** The order here is also the tie-break order when two activities have the same score. */
enum class Activity { OUTDOOR_SIGHTSEEING, INDOOR_SIGHTSEEING, SKIING, SURFING }

enum class Reason {
    THUNDERSTORM,
    FREEZING_RAIN,
    HEAVY_SNOW,
    STRONG_GUSTS,
    EXTREME_HEAT,
    EXTREME_COLD,
    NO_SNOW,
    DANGEROUS_WAVES,
    NO_SEA_DATA,
    MISSING_DATA,
}

/** How one activity did on one day. */
sealed interface DayScore {
    val date: LocalDate

    /** Points from 0 to 100, kept unrounded until the weekly average. */
    data class Scored(override val date: LocalDate, val points: Double) : DayScore

    /** A deal-breaker such as a thunderstorm. Counts as 0 in the weekly average. */
    data class NotSuitable(override val date: LocalDate, val reason: Reason) : DayScore

    /** Not enough data to judge. Left out of the weekly average. */
    data class NoData(override val date: LocalDate, val reason: Reason) : DayScore
}

data class ActivityRanking(
    val activity: Activity,
    val weeklyScore: Int?,
    val bestDay: LocalDate?,
    val days: List<DayScore>,
)
