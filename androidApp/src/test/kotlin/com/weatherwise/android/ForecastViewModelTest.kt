package com.weatherwise.android

import com.weatherwise.android.ui.forecast.ForecastScreenState
import com.weatherwise.android.ui.forecast.ForecastViewModel
import com.weatherwise.shared.core.AppError
import com.weatherwise.shared.model.WeeklyForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val server = TestServer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saved data reaches the screen flagged as from cache so the last updated label shows`() = runTest(dispatcher) {
        val backend = testBackend(server)
        val savedAt = Clock.System.now().toEpochMilliseconds()
        backend.store.save(WeeklyForecast(city(), "Europe/London", emptyList(), savedAt))
        server.isOnline = false

        val viewModel = ForecastViewModel(city(), backend.repository)
        advanceUntilIdle()

        val ready = assertIs<ForecastScreenState.Ready>(viewModel.state.value)
        assertTrue(ready.isFromCache)
        assertEquals(savedAt, ready.fetchedAtMillis)
        assertEquals(4, ready.rankings.size)
    }

    @Test
    fun `retry after a failure goes back to loading and then shows the ranking`() = runTest(dispatcher) {
        server.isOnline = false
        server.replyDelayMillis = 100
        val viewModel = ForecastViewModel(city(), testBackend(server).repository)
        advanceUntilIdle()
        assertEquals(ForecastScreenState.Failed(AppError.Network), viewModel.state.value)

        server.isOnline = true
        viewModel.onRetry()
        runCurrent()
        assertEquals(ForecastScreenState.Loading, viewModel.state.value)

        advanceUntilIdle()
        val ready = assertIs<ForecastScreenState.Ready>(viewModel.state.value)
        assertEquals(false, ready.isFromCache)
    }
}
