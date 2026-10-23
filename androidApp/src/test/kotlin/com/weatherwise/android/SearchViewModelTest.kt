package com.weatherwise.android

import androidx.lifecycle.SavedStateHandle
import com.weatherwise.android.ui.search.SEARCH_DELAY_MILLIS
import com.weatherwise.android.ui.search.SearchResults
import com.weatherwise.android.ui.search.SearchViewModel
import com.weatherwise.shared.core.AppError
import com.weatherwise.shared.model.WeeklyForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

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
    fun `a query shorter than two characters never calls the API and keeps showing recents`() = runTest(dispatcher) {
        val backend = testBackend(server)
        val zermatt = city(3, "Zermatt")
        backend.store.save(WeeklyForecast(zermatt, "Europe/Zurich", emptyList(), Clock.System.now().toEpochMilliseconds()))
        val viewModel = SearchViewModel(SavedStateHandle(), backend.repository)

        viewModel.onSearchOpened()
        viewModel.onQueryChanged("L")
        advanceUntilIdle()

        assertEquals(emptyList(), server.requestedQueries)
        assertEquals(SearchResults.Idle, viewModel.state.value.results)
        assertEquals(listOf("Zermatt"), viewModel.state.value.recents.map { it.city.name })
    }

    @Test
    fun `fast typing makes one call and a newer query cancels the one in flight`() = runTest(dispatcher) {
        val rome = city(2, "Rome")
        server.citiesByQuery = mapOf("Rome" to listOf(rome))
        server.replyDelayMillis = 1_000
        val viewModel = SearchViewModel(SavedStateHandle(), testBackend(server).repository)

        viewModel.onQueryChanged("Lo")
        advanceTimeBy(100)
        viewModel.onQueryChanged("Lon")
        advanceTimeBy(100)
        viewModel.onQueryChanged("Lond")
        advanceTimeBy(SEARCH_DELAY_MILLIS + 1)
        assertEquals(listOf("Lond"), server.requestedQueries)

        // "Lond" is still waiting for its reply when the user types "Rome".
        viewModel.onQueryChanged("Rome")
        advanceUntilIdle()

        assertEquals(listOf("Lond", "Rome"), server.requestedQueries)
        assertEquals(listOf("Rome"), server.completedQueries)
        assertEquals(SearchResults.Found(listOf(rome)), viewModel.state.value.results)
    }

    @Test
    fun `no match and errors are shown and retry runs the same query again`() = runTest(dispatcher) {
        val london = city()
        val viewModel = SearchViewModel(SavedStateHandle(), testBackend(server).repository)

        viewModel.onQueryChanged("zzq")
        advanceUntilIdle()
        assertEquals(SearchResults.NoMatch("zzq"), viewModel.state.value.results)

        server.isOnline = false
        viewModel.onQueryChanged("London")
        advanceUntilIdle()
        assertEquals(SearchResults.Failed(AppError.Network), viewModel.state.value.results)

        server.isOnline = true
        server.citiesByQuery = mapOf("London" to listOf(london))
        viewModel.onRetry()
        advanceUntilIdle()
        assertEquals(SearchResults.Found(listOf(london)), viewModel.state.value.results)
    }
}
