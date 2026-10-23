import Foundation
import Shared

@MainActor
final class SearchViewModel: ObservableObject {
    enum State: Equatable {
        case idle
        case loading
        case results([City])
        case noMatch(String)
        case failed(String)
    }

    @Published var query = "" {
        didSet { if query != oldValue { scheduleSearch() } }
    }
    @Published private(set) var state: State = .idle

    let service: WeatherService
    private let debounceNanos: UInt64
    private var searchTask: Task<Void, Never>?

    init(service: WeatherService, debounceNanos: UInt64 = 350_000_000) {
        self.service = service
        self.debounceNanos = debounceNanos
    }

    func retry() {
        scheduleSearch()
    }

    /// Test hook: waits for the current debounce and request to finish.
    func waitForSearch() async {
        await searchTask?.value
    }

    private func scheduleSearch() {
        // Cancelling the previous task drops both its debounce wait and any request in flight.
        searchTask?.cancel()
        let text = query.trimmingCharacters(in: .whitespaces)
        guard text.count >= 2 else {
            state = .idle
            return
        }
        searchTask = Task { [weak self, debounceNanos] in
            try? await Task.sleep(nanoseconds: debounceNanos)
            guard let self, !Task.isCancelled else { return }
            state = .loading
            do {
                let result = try await service.search(query: text)
                guard !Task.isCancelled else { return }
                if let message = result.errorMessage {
                    state = .failed(message)
                } else if result.cities.isEmpty {
                    state = .noMatch(text)
                } else {
                    state = .results(result.cities)
                }
            } catch {
                if !Task.isCancelled { state = .failed("Something went wrong.") }
            }
        }
    }
}
