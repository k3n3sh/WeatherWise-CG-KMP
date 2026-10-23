import SwiftUI
import Shared

struct ForecastView: View {
    @StateObject var viewModel: ForecastViewModel

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            case .failed(let message):
                StatusView(message: message) { Task { await viewModel.load() } }
            case .ready(let rankings, let savedAt):
                List {
                    if let savedAt {
                        Label("Showing saved data · Last updated \(savedAt.formatted(date: .abbreviated, time: .shortened))",
                              systemImage: "icloud.slash")
                            .font(.footnote)
                            .foregroundStyle(Palette.warning)
                    }
                    ForEach(Array(rankings.enumerated()), id: \.offset) { index, ranking in
                        RankingRow(rank: index + 1, ranking: ranking)
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle(viewModel.city.name)
        .task { await viewModel.load() }
    }
}

private struct RankingRow: View {
    let rank: Int
    let ranking: ActivityRanking

    private var score: Int? { ranking.weeklyScore?.intValue }
    private var name: String { Labels.activity(ranking.activity) }
    private var reason: String? {
        ranking.days.compactMap { $0 as? DayScoreNoData }.first.map { Labels.reason($0.reason) }
    }

    var body: some View {
        HStack(spacing: 12) {
            Text(score == nil ? "–" : "\(rank)")
                .font(.title3.bold())
                .foregroundStyle(Palette.goldText)
                .frame(width: 28)
            Image(systemName: Labels.symbol(ranking.activity))
                .foregroundStyle(score == nil ? Color.secondary : Palette.goldText)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                Text(name).font(.headline)
                if let score {
                    ProgressView(value: Double(score), total: 100).tint(Palette.goldText)
                } else if let reason {
                    Text(reason).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text(score.map { "\($0)/100" } ?? "No data").font(.headline)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(score.map { "Rank \(rank), \(name), score \($0) out of 100" } ?? "\(name), \(reason ?? "No data")")
    }
}

private enum Labels {
    static func activity(_ activity: Activity) -> String {
        switch activity {
        case .outdoorSightseeing: return "Outdoor sightseeing"
        case .indoorSightseeing: return "Indoor sightseeing"
        case .skiing: return "Skiing"
        case .surfing: return "Surfing"
        default: return activity.name
        }
    }

    static func symbol(_ activity: Activity) -> String {
        switch activity {
        case .outdoorSightseeing: return "figure.walk"
        case .indoorSightseeing: return "building.columns"
        case .skiing: return "figure.skiing.downhill"
        case .surfing: return "figure.surfing"
        default: return "questionmark"
        }
    }

    static func reason(_ reason: Reason) -> String {
        switch reason {
        case .noSeaData: return "No sea data for this location"
        case .missingData: return "No data"
        default: return reason.name.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }
}
