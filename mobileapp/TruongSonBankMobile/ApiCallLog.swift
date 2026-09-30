import SwiftUI

struct ApiCallLogEntry: Identifiable {
    let id = UUID()
    let time = Date()
    let method: String
    let url: String
    let requestHeaders: String
    let requestBody: String
    let status: Int?
    let responseBody: String
}

@MainActor
final class ApiLogStore: ObservableObject {
    @Published private(set) var entries: [ApiCallLogEntry] = []

    func add(_ entry: ApiCallLogEntry) {
        if entry.responseBody != "Đang gọi API..." {
            entries.removeAll {
                $0.status == nil
                    && $0.responseBody == "Đang gọi API..."
                    && $0.method == entry.method
                    && $0.url == entry.url
                    && $0.requestBody == entry.requestBody
            }
        }
        entries.insert(entry, at: 0)
        if entries.count > 100 {
            entries.removeLast(entries.count - 100)
        }
    }

    func clear() {
        entries.removeAll()
    }
}

struct ApiLogsView: View {
    @ObservedObject var store: ApiLogStore

    var body: some View {
        List {
            if store.entries.isEmpty {
                Text("Chưa có request nào")
                    .foregroundStyle(.secondary)
            } else {
                Button("Xoá log") {
                    store.clear()
                }

                ForEach(store.entries) { entry in
                    NavigationLink {
                        ApiLogDetailView(entry: entry)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(entry.method)
                                    .font(.caption.monospaced().bold())
                                Text(entry.status.map(String.init) ?? "...")
                                    .font(.caption.monospaced())
                                    .foregroundStyle(statusColor(entry.status))
                            }
                            Text(entry.url)
                                .font(.caption)
                                .lineLimit(2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("API Logs")
    }

    private func statusColor(_ status: Int?) -> Color {
        guard let status else { return .blue }
        return 200..<300 ~= status ? .green : .red
    }
}

struct ApiLogDetailView: View {
    let entry: ApiCallLogEntry

    var body: some View {
        List {
            Section("Request") {
                LabeledContent("Method", value: entry.method)
                Text(entry.url)
                    .font(.footnote.monospaced())
                    .textSelection(.enabled)
                LogBlock(title: "Headers", text: entry.requestHeaders)
                LogBlock(title: "Body", text: entry.requestBody)
            }

            Section("Response") {
                LabeledContent("Status", value: entry.status.map(String.init) ?? "Pending")
                LogBlock(title: "Body", text: entry.responseBody)
            }
        }
        .navigationTitle(entry.method)
    }
}

private struct LogBlock: View {
    let title: String
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(text.isEmpty ? "-" : text)
                .font(.caption.monospaced())
                .textSelection(.enabled)
        }
    }
}
