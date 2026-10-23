import SwiftUI
import Shared

@main
struct WeatherWiseApp: App {
    init() {
        IosWeatherFacadeKt.startKoinForIos()
    }

    var body: some Scene {
        WindowGroup {
            SearchView(viewModel: SearchViewModel(service: IosWeatherFacade()))
        }
    }
}
