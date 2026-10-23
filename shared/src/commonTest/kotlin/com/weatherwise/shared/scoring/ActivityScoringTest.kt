package com.weatherwise.shared.scoring

import com.weatherwise.shared.model.DayScore
import com.weatherwise.shared.model.Reason
import com.weatherwise.shared.support.TODAY
import com.weatherwise.shared.support.day
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityScoringTest {

    @Test
    fun `marks are full inside the ideal range and slide to zero at the edges`() {
        fun mark(value: Double?) = goodRange(value, zeroBelow = 0.0, idealFrom = 10.0, idealTo = 20.0, zeroAbove = 40.0)

        assertEquals(1.0, mark(15.0))
        assertEquals(1.0, mark(10.0))
        assertEquals(0.0, mark(0.0))
        assertEquals(0.0, mark(-5.0))
        assertEquals(0.0, mark(40.0))
        assertEquals(0.5, mark(5.0)!!, absoluteTolerance = 1e-9)
        assertEquals(0.5, mark(30.0)!!, absoluteTolerance = 1e-9)
        assertNull(mark(null))

        assertEquals(0.5, lowIsGood(3.0, idealUpTo = 1.0, zeroAt = 5.0)!!, absoluteTolerance = 1e-9)
        assertEquals(0.0, lowIsGood(9.0, idealUpTo = 1.0, zeroAt = 5.0))
        assertEquals(0.5, highIsGood(2.5, zeroAt = 0.0, idealFrom = 5.0)!!, absoluteTolerance = 1e-9)
        assertEquals(1.0, highIsGood(8.0, zeroAt = 0.0, idealFrom = 5.0))
    }

    @Test
    fun `skiing scores high with deep snow and fresh snowfall in cold calm weather`() {
        val score = scoreSkiing(day(weatherCode = 73, maxTempC = -3.0, snowfallCm = 6.0, snowDepthM = 1.0, maxWindKmh = 10.0))

        assertTrue(assertIs<DayScore.Scored>(score).points >= 90.0)
    }

    @Test
    fun `skiing is not suitable when there is no snow on the ground and none falling`() {
        val london = day(weatherCode = 3, maxTempC = 14.0, snowfallCm = 0.0, snowDepthM = 0.0)

        assertEquals(DayScore.NotSuitable(TODAY, Reason.NO_SNOW), scoreSkiing(london))
    }

    @Test
    fun `spring skiing on an existing snow base still scores well without fresh snow`() {
        val score = scoreSkiing(day(maxTempC = 5.0, snowfallCm = 0.0, snowDepthM = 0.8, maxWindKmh = 10.0))

        // snow depth 1.0 x 0.4 + fresh snow 0 x 0.2 + cold 0.625 x 0.2 + wind 1.0 x 0.2
        assertEquals(72.5, assertIs<DayScore.Scored>(score).points, absoluteTolerance = 1e-9)
    }

    @Test
    fun `surfing scores high with medium waves with a long period and light wind`() {
        val score = scoreSurfing(day(maxTempC = 22.0, maxWindKmh = 12.0, waveHeightM = 1.8, wavePeriodS = 12.0))

        assertTrue(assertIs<DayScore.Scored>(score).points >= 90.0)
    }

    @Test
    fun `surfing has no data rather than a zero score when there is no sea data`() {
        assertEquals(DayScore.NoData(TODAY, Reason.NO_SEA_DATA), scoreSurfing(day(waveHeightM = null)))
    }

    @Test
    fun `surfing is not suitable when waves are dangerously big`() {
        assertEquals(DayScore.NotSuitable(TODAY, Reason.DANGEROUS_WAVES), scoreSurfing(day(waveHeightM = 4.5, wavePeriodS = 12.0)))
    }

    @Test
    fun `outdoor sightseeing scores high on a dry mild day and low in heavy rain`() {
        val dry = scoreOutdoorSightseeing(day(weatherCode = 0, maxTempC = 20.0, precipitationMm = 0.0, maxWindKmh = 10.0))
        val wet = scoreOutdoorSightseeing(day(weatherCode = 63, maxTempC = 12.0, precipitationMm = 12.0, maxWindKmh = 30.0))

        assertTrue(assertIs<DayScore.Scored>(dry).points >= 90.0)
        assertTrue(assertIs<DayScore.Scored>(wet).points < 50.0)
    }

    @Test
    fun `outdoor sightseeing is not suitable in a thunderstorm or freezing rain or extreme heat`() {
        assertEquals(DayScore.NotSuitable(TODAY, Reason.THUNDERSTORM), scoreOutdoorSightseeing(day(weatherCode = 95)))
        assertEquals(DayScore.NotSuitable(TODAY, Reason.FREEZING_RAIN), scoreOutdoorSightseeing(day(weatherCode = 66)))
        assertEquals(DayScore.NotSuitable(TODAY, Reason.EXTREME_HEAT), scoreOutdoorSightseeing(day(maxTempC = 39.0)))
        assertEquals(DayScore.NotSuitable(TODAY, Reason.STRONG_GUSTS), scoreOutdoorSightseeing(day(maxGustKmh = 75.0)))
    }

    @Test
    fun `indoor sightseeing rises as outdoor falls but stays between 30 and 80`() {
        val perfectOutside = day(weatherCode = 0, maxTempC = 20.0, precipitationMm = 0.0, maxWindKmh = 10.0)
        val stormOutside = day(weatherCode = 95)
        val unknownOutside = day(weatherCode = null, maxTempC = null, precipitationMm = null, maxWindKmh = null, maxGustKmh = null)

        assertEquals(30.0, assertIs<DayScore.Scored>(scoreIndoorSightseeing(perfectOutside)).points, absoluteTolerance = 1e-9)
        assertEquals(80.0, assertIs<DayScore.Scored>(scoreIndoorSightseeing(stormOutside)).points)
        assertEquals(55.0, assertIs<DayScore.Scored>(scoreIndoorSightseeing(unknownOutside)).points)
    }
}
