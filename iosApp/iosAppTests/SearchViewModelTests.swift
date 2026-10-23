import XCTest
import Shared
@testable import WeatherWise

private struct SampleError: Error {}

private final class FakeWeatherService: WeatherService {
    var searchResult = CitySearchResult(cities: [], errorMessage: nil)

    func search(query: String) async throws -> CitySearchResult { searchResult }
    func ranking(city: City) async throws -> RankingResult { throw SampleError() }
}

@MainActor
final class SearchViewModelTests: XCTestCase {

    func testSearchShowsCitiesFromTheSharedModule() async {
        let london = City(id: 1, name: "London", region: "England", country: "United Kingdom", latitude: 51.5, longitude: -0.12)
        let service = FakeWeatherService()
        service.searchResult = CitySearchResult(cities: [london], errorMessage: nil)
        let viewModel = SearchViewModel(service: service, debounceNanos: 0)

        viewModel.query = "London"
        await viewModel.waitForSearch()

        XCTAssertEqual(viewModel.state, .results([london]))
    }

    func testSearchErrorIsShownAndShortQueriesStayIdle() async {
        let service = FakeWeatherService()
        service.searchResult = CitySearchResult(cities: [], errorMessage: "No internet connection.")
        let viewModel = SearchViewModel(service: service, debounceNanos: 0)

        viewModel.query = "L"
        XCTAssertEqual(viewModel.state, .idle)

        viewModel.query = "London"
        await viewModel.waitForSearch()
        XCTAssertEqual(viewModel.state, .failed("No internet connection."))
    }
}
