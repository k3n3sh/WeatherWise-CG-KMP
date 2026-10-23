import SwiftUI
import Shared

struct SearchView: View {
    @StateObject var viewModel: SearchViewModel

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("WeatherWise")
                .searchable(text: $viewModel.query, prompt: "Search a city")
                .navigationDestination(for: City.self) { city in
                    ForecastView(viewModel: ForecastViewModel(city: city, service: viewModel.service))
                }
                .background(Palette.background)
        }
        .tint(Palette.goldText)
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .idle:
            VStack(spacing: 8) {
                Text("Where to this week?").font(.title2.weight(.semibold))
                Text("Find the best activities for the next 7 days").foregroundStyle(.secondary)
                Spacer()
                Text("Weather data by Open-Meteo.com").font(.footnote).foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.center)
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .loading:
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        case .results(let cities):
            List(cities, id: \.id) { city in
                NavigationLink(value: city) {
                    Label {
                        VStack(alignment: .leading) {
                            Text(city.name).font(.headline)
                            if !city.subtitle.isEmpty {
                                Text(city.subtitle).font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    } icon: {
                        Image(systemName: "mappin.and.ellipse").foregroundStyle(Palette.goldText)
                    }
                }
            }
            .listStyle(.plain)
        case .noMatch(let text):
            StatusView(message: "No cities match \"\(text)\"")
        case .failed(let message):
            StatusView(message: message, onRetry: viewModel.retry)
        }
    }
}

struct StatusView: View {
    let message: String
    var onRetry: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Text(message).multilineTextAlignment(.center)
            if let onRetry {
                Button(action: onRetry) {
                    Label("Retry", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.bordered)
                .tint(Palette.goldText)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

enum Palette {
    static let background = Color(red: 0xE4 / 255, green: 0xDC / 255, blue: 0xCC / 255)
    static let gold = Color(red: 0xA2 / 255, green: 0x86 / 255, blue: 0x39 / 255)
    static let goldText = Color(red: 0x6E / 255, green: 0x5A / 255, blue: 0x22 / 255)
    static let warning = Color(red: 0x9A / 255, green: 0x34 / 255, blue: 0x12 / 255)
}
