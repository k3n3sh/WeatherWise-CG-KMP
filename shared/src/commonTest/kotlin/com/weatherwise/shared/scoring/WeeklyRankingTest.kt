package com.weatherwise.shared.scoring

import com.weatherwise.shared.model.Activity
import com.weatherwise.shared.model.DayScore
import com.weatherwise.shared.model.Reason
import com.weatherwise.shared.support.TODAY
import com.weatherwise.shared.support.day
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyRankingTest {

    private val dates = listOf(0, 1, 2, 3).map { TODAY.plus(DatePeriod(days = it)) }

    private fun scoredDays(vararg points: Double): List<DayScore> {
        return points.mapIndexed { index, value -> DayScore.Scored(dates[index], value) }
    }

    @Test
    fun `weekly score averages judged days and skips days without data and rounds once`() {
        val mixedWeek = listOf(
            DayScore.Scored(dates[0], 80.0),
            DayScore.NotSuitable(dates[1], Reason.THUNDERSTORM),
            DayScore.NoData(dates[2], Reason.MISSING_DATA),
            DayScore.NoData(dates[3], Reason.MISSING_DATA),
        )

        assertEquals(40, weeklyRanking(Activity.SKIING, mixedWeek).weeklyScore)
        // Rounding each day first would give (71 + 70) / 2 = 70.5 -> 71 instead of 70.
        assertEquals(70, weeklyRanking(Activity.SURFING, scoredDays(70.5, 70.4)).weeklyScore)
    }

    @Test
    fun `rankings go high to low with fixed tie order and no-data activities last`() {
        val rankings = listOf(
            weeklyRanking(Activity.SURFING, scoredDays(50.0)),
            weeklyRanking(Activity.SKIING, listOf(DayScore.NoData(dates[0], Reason.MISSING_DATA))),
            weeklyRanking(Activity.INDOOR_SIGHTSEEING, scoredDays(80.0)),
            weeklyRanking(Activity.OUTDOOR_SIGHTSEEING, scoredDays(50.0)),
        )

        val order = sortBestFirst(rankings).map { it.activity }

        assertEquals(listOf(Activity.INDOOR_SIGHTSEEING, Activity.OUTDOOR_SIGHTSEEING, Activity.SURFING, Activity.SKIING), order)
    }

    @Test
    fun `best day is the highest scoring day and the earlier day wins a tie`() {
        val ranking = weeklyRanking(Activity.OUTDOOR_SIGHTSEEING, scoredDays(60.0, 90.0, 90.0, 30.0))

        assertEquals(dates[1], ranking.bestDay)
    }

    @Test
    fun `every activity appears in the ranking exactly once with a score for every day`() {
        val week = dates.map { day(date = it) }

        val rankings = rankActivities(week)

        assertEquals(Activity.entries.toSet(), rankings.map { it.activity }.toSet())
        assertEquals(Activity.entries.size, rankings.size)
        rankings.forEach { assertEquals(week.size, it.days.size) }
    }
}
