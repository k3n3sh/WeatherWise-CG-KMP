package com.weatherwise.shared.scoring

import com.weatherwise.shared.model.Activity
import com.weatherwise.shared.model.ActivityRanking
import com.weatherwise.shared.model.DailyWeather
import com.weatherwise.shared.model.DayScore
import com.weatherwise.shared.model.Reason
import kotlin.math.roundToInt

// How the app picks the best activity.
// For every day and every activity we ask, in this order:
//   1. Do we know enough?            If not -> NoData (left out of the week)
//   2. Is there a deal-breaker?      If yes -> NotSuitable (counts as 0)
//   3. How good is each condition?   A mark from 0 to 1
//   4. How much does each matter?    Weights -> a score out of 100
// Then each activity's week is averaged and the activities are sorted best first.

private const val STRONG_GUSTS_KMH = 70.0
private const val DANGEROUS_WAVES_M = 4.0

// ---------- The week ----------

fun rankActivities(days: List<DailyWeather>): List<ActivityRanking> {
    val rankings = Activity.entries.map { activity ->
        val dayScores = days.map { day -> scoreDay(activity, day) }
        weeklyRanking(activity, dayScores)
    }
    return sortBestFirst(rankings)
}

// To add an activity: add it to the Activity enum, write its score function, add one line here.
// The compiler won't build until every activity has a line.
fun scoreDay(activity: Activity, day: DailyWeather): DayScore = when (activity) {
    Activity.OUTDOOR_SIGHTSEEING -> scoreOutdoorSightseeing(day)
    Activity.INDOOR_SIGHTSEEING -> scoreIndoorSightseeing(day)
    Activity.SKIING -> scoreSkiing(day)
    Activity.SURFING -> scoreSurfing(day)
}

fun weeklyRanking(activity: Activity, dayScores: List<DayScore>): ActivityRanking {
    // Ruled-out days count as 0; days we couldn't judge are skipped.
    val points = dayScores.mapNotNull { day ->
        when (day) {
            is DayScore.Scored -> day.points
            is DayScore.NotSuitable -> 0.0
            is DayScore.NoData -> null
        }
    }
    val weeklyScore = if (points.isEmpty()) null else points.average().roundToInt()

    // maxByOrNull keeps the first of equal values, so the earlier day wins a tie.
    val bestDay = dayScores
        .filterIsInstance<DayScore.Scored>()
        .filter { it.points > 0 }
        .maxByOrNull { it.points }
        ?.date

    return ActivityRanking(activity, weeklyScore, bestDay, dayScores)
}

fun sortBestFirst(rankings: List<ActivityRanking>): List<ActivityRanking> {
    // Put them in the fixed Activity order first; sorting keeps that order for equal scores.
    val inFixedOrder = rankings.sortedBy { it.activity.ordinal }
    val withScore = inFixedOrder.filter { it.weeklyScore != null }.sortedByDescending { it.weeklyScore }
    val withoutScore = inFixedOrder.filter { it.weeklyScore == null }
    return withScore + withoutScore
}

// ---------- One function per activity ----------

fun scoreSkiing(day: DailyWeather): DayScore {
    if (day.snowDepthM == null && day.snowfallCm == null) {
        return DayScore.NoData(day.date, Reason.MISSING_DATA)
    }

    val noSnow = (day.snowDepthM ?: 0.0) < 0.05 && (day.snowfallCm ?: 0.0) < 0.5
    val dealBreaker = when {
        isThunderstorm(day.weatherCode) -> Reason.THUNDERSTORM
        hasStrongGusts(day) -> Reason.STRONG_GUSTS
        isFreezingRain(day.weatherCode) -> Reason.FREEZING_RAIN
        noSnow -> Reason.NO_SNOW
        else -> null
    }
    if (dealBreaker != null) return DayScore.NotSuitable(day.date, dealBreaker)

    val score = weightedScore(
        Weighted(highIsGood(day.snowDepthM, zeroAt = 0.05, idealFrom = 0.3), weight = 0.4), // snow on the ground (m)
        Weighted(highIsGood(day.snowfallCm, zeroAt = 0.0, idealFrom = 5.0), weight = 0.2), // fresh snow (cm)
        Weighted(goodRange(day.maxTempC, zeroBelow = -25.0, idealFrom = -8.0, idealTo = 2.0, zeroAbove = 10.0), weight = 0.2), // cold
        Weighted(lowIsGood(day.maxWindKmh, idealUpTo = 20.0, zeroAt = 50.0), weight = 0.2), // calm wind
    )
    return scoreOrNoData(day, score)
}

fun scoreSurfing(day: DailyWeather): DayScore {
    // Inland cities have no sea data. That's "can't tell", not "bad surf".
    val waves = day.waveHeightM
    if (waves == null) return DayScore.NoData(day.date, Reason.NO_SEA_DATA)

    val dealBreaker = when {
        isThunderstorm(day.weatherCode) -> Reason.THUNDERSTORM
        hasStrongGusts(day) -> Reason.STRONG_GUSTS
        waves > DANGEROUS_WAVES_M -> Reason.DANGEROUS_WAVES
        else -> null
    }
    if (dealBreaker != null) return DayScore.NotSuitable(day.date, dealBreaker)

    val score = weightedScore(
        Weighted(goodRange(waves, zeroBelow = 0.3, idealFrom = 1.0, idealTo = 2.5, zeroAbove = 4.0), weight = 0.5), // wave height (m)
        Weighted(highIsGood(day.wavePeriodS, zeroAt = 4.0, idealFrom = 10.0), weight = 0.2), // wave period (s)
        Weighted(lowIsGood(day.maxWindKmh, idealUpTo = 20.0, zeroAt = 40.0), weight = 0.2), // light wind
        Weighted(goodRange(day.maxTempC, zeroBelow = 5.0, idealFrom = 18.0, idealTo = 30.0, zeroAbove = 38.0), weight = 0.1), // warm air
    )
    return scoreOrNoData(day, score)
}

fun scoreOutdoorSightseeing(day: DailyWeather): DayScore {
    val temp = day.maxTempC
    val dealBreaker = when {
        isThunderstorm(day.weatherCode) -> Reason.THUNDERSTORM
        hasStrongGusts(day) -> Reason.STRONG_GUSTS
        isFreezingRain(day.weatherCode) -> Reason.FREEZING_RAIN
        isHeavySnow(day.weatherCode) -> Reason.HEAVY_SNOW
        temp != null && temp >= 38.0 -> Reason.EXTREME_HEAT
        temp != null && temp <= -15.0 -> Reason.EXTREME_COLD
        else -> null
    }
    if (dealBreaker != null) return DayScore.NotSuitable(day.date, dealBreaker)

    val score = weightedScore(
        Weighted(lowIsGood(day.precipitationMm, idealUpTo = 1.0, zeroAt = 5.0), weight = 0.4), // staying dry (mm)
        Weighted(goodRange(temp, zeroBelow = 0.0, idealFrom = 15.0, idealTo = 25.0, zeroAbove = 35.0), weight = 0.3), // mild
        Weighted(lowIsGood(day.maxWindKmh, idealUpTo = 20.0, zeroAt = 50.0), weight = 0.2), // calm wind
        Weighted(skyMark(day.weatherCode), weight = 0.1), // nice sky
    )
    return scoreOrNoData(day, score)
}

// Always possible, and better the worse it is outside: 30 on a perfect day, up to 80.
// It stays a fallback, so a great day for skiing or surfing still beats a museum.
fun scoreIndoorSightseeing(day: DailyWeather): DayScore {
    val points = when (val outdoor = scoreOutdoorSightseeing(day)) {
        is DayScore.Scored -> 30 + (100 - outdoor.points) * 0.5
        is DayScore.NotSuitable -> 80.0
        is DayScore.NoData -> 55.0
    }
    return DayScore.Scored(day.date, points)
}

// ---------- Marks: turn one weather value into a mark from 0 to 1 ----------

// Full marks inside the ideal range, sliding down to 0 at the edges.
fun goodRange(value: Double?, zeroBelow: Double, idealFrom: Double, idealTo: Double, zeroAbove: Double): Double? {
    if (value == null) return null
    if (value <= zeroBelow || value >= zeroAbove) return 0.0
    if (value < idealFrom) return (value - zeroBelow) / (idealFrom - zeroBelow)
    if (value > idealTo) return (zeroAbove - value) / (zeroAbove - idealTo)
    return 1.0
}

// For rain and wind: full marks up to idealUpTo, nothing from zeroAt.
fun lowIsGood(value: Double?, idealUpTo: Double, zeroAt: Double): Double? {
    if (value == null) return null
    if (value <= idealUpTo) return 1.0
    if (value >= zeroAt) return 0.0
    return (zeroAt - value) / (zeroAt - idealUpTo)
}

// For snow and wave period: nothing up to zeroAt, full marks from idealFrom.
fun highIsGood(value: Double?, zeroAt: Double, idealFrom: Double): Double? {
    if (value == null) return null
    if (value >= idealFrom) return 1.0
    if (value <= zeroAt) return 0.0
    return (value - zeroAt) / (idealFrom - zeroAt)
}

// One condition's mark (0 to 1, or null if unknown) and how much it counts.
class Weighted(val mark: Double?, val weight: Double)

// Adds up the known marks. Unknown marks are skipped and the others share their weight.
fun weightedScore(vararg parts: Weighted): Double? {
    var total = 0.0
    var usedWeight = 0.0
    for (part in parts) {
        if (part.mark != null) {
            total += part.mark * part.weight
            usedWeight += part.weight
        }
    }
    if (usedWeight == 0.0) return null
    return total / usedWeight * 100
}

private fun scoreOrNoData(day: DailyWeather, score: Double?): DayScore {
    if (score == null) return DayScore.NoData(day.date, Reason.MISSING_DATA)
    return DayScore.Scored(day.date, score)
}

// ---------- Weather codes (WMO standard, as used by Open-Meteo) ----------

private fun isThunderstorm(code: Int?) = code in 95..99
private fun isFreezingRain(code: Int?) = code == 56 || code == 57 || code == 66 || code == 67
private fun isHeavySnow(code: Int?) = code == 75 || code == 86
private fun hasStrongGusts(day: DailyWeather) = (day.maxGustKmh ?: 0.0) >= STRONG_GUSTS_KMH

private fun skyMark(code: Int?): Double? = when (code) {
    null -> null
    0 -> 1.0 // clear
    1, 2 -> 0.9 // mostly clear
    3 -> 0.7 // overcast
    45, 48 -> 0.3 // fog
    else -> 0.2 // rain or snow, already counted under "staying dry"
}
