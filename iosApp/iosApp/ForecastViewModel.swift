import Foundation
import Shared

@MainActor
final class ForecastViewModel: ObservableObject {
    enum State {
        case loading
        case ready([ActivityRanking], savedAt: Date?)
        case failed(String)
    }

    @Published private(set) var state: State = .loading

    let city: City
    private let service: WeatherService

    init(city: City, service: WeatherService) {
        self.city = city
        self.service = service
    }

    func load() async {
        state = .loading
        do {
            let result = try await service.ranking(city: city)
            if let message = result.errorMessage {
                state = .failed(message)
                return
            }
            // Only set when the network failed and this is the saved copy.
            var savedAt: Date? = nil
            if let millis = result.savedAtMillis {
                savedAt = Date(timeIntervalSince1970: Double(millis.int64Value) / 1000)
            }
            state = .ready(result.rankings, savedAt: savedAt)
        } catch {
            state = .failed("Something went wrong.")
        }
    }
}
