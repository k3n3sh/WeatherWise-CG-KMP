import Shared

/// Lets the view models run against a fake in tests instead of the real Kotlin facade.
protocol WeatherService {
    func search(query: String) async throws -> CitySearchResult
    func ranking(city: City) async throws -> RankingResult
}

extension IosWeatherFacade: WeatherService {}
