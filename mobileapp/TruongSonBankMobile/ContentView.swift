import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = LoginViewModel()

    var body: some View {
        NavigationStack {
            Form {
                Section("API") {
                    TextField("Auth URL", text: $viewModel.baseURL)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                    TextField("Keycloak URL", text: $viewModel.keycloakURL)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                    NavigationLink("API Logs (\(viewModel.apiLogs.entries.count))") {
                        ApiLogsView(store: viewModel.apiLogs)
                    }
                }

                Section("Login") {
                    TextField("Username", text: $viewModel.username)
                        .textInputAutocapitalization(.never)
                    SecureField("PIN", text: $viewModel.pin)

                    Button(viewModel.isLoading ? "Đang đăng nhập..." : "Đăng nhập") {
                        Task { await viewModel.login() }
                    }
                    .disabled(viewModel.isLoading)

                    Button("Đăng nhập bằng sinh trắc học") {
                        Task { await viewModel.biometricLogin() }
                    }
                    .disabled(viewModel.isLoading)

                    if viewModel.biometricEnabled {
                        Button("Tắt sinh trắc học trên app") {
                            viewModel.resetLocalBiometric()
                        }
                        .disabled(viewModel.isLoading)
                    }
                }

                if let session = viewModel.session {
                    Section("Session") {
                        LabeledContent("Username", value: session.username)
                        LabeledContent("Device", value: session.deviceId)
                        LabeledContent("Trusted", value: session.trustedDevice ? "Yes" : "No")
                        LabeledContent("Expires", value: session.expiresAt)
                        Text(session.sessionId)
                            .font(.footnote.monospaced())
                            .textSelection(.enabled)

                        Button("Keep alive") {
                            Task { await viewModel.keepAlive() }
                        }
                        if !session.trustedDevice {
                            Button("Trust this device") {
                                Task { await viewModel.trustDevice() }
                            }
                        } else {
                            Toggle("Login bằng FaceID/TouchID", isOn: $viewModel.biometricEnabled)
                                .onChange(of: viewModel.biometricEnabled) { _, enabled in
                                    Task { await viewModel.setBiometric(enabled) }
                                }
                            Button("Tắt sinh trắc học trên app") {
                                viewModel.resetLocalBiometric()
                            }
                        }
                    }
                }

                if let message = viewModel.message {
                    Section("Status") {
                        Text(message)
                            .foregroundStyle(viewModel.session == nil ? .red : .primary)
                    }
                }
            }
            .navigationTitle("TSB Auth")
        }
    }
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var baseURL = UserDefaults.standard.string(forKey: "auth.baseURL") ?? "http://172.20.10.4:8086/bff/api/auth/v1"
    @Published var keycloakURL = UserDefaults.standard.string(forKey: "auth.keycloakURL") ?? "http://172.20.10.4:8088/realms/truongsonbank/protocol/openid-connect/token"
    @Published var username = "84901234567"
    @Published var pin = "739204"
    @Published var session: AuthSession?
    @Published var message: String?
    @Published var isLoading = false
    @Published var biometricEnabled = UserDefaults.standard.bool(forKey: "auth.biometricEnabled")

    let apiLogs = ApiLogStore()
    private lazy var api = AuthApi(logs: apiLogs)
    private var biometricFailureCount = 0

    func login() async {
        await run {
            let session = try await api.login(baseURL: baseURL, keycloakURL: keycloakURL, username: username, pin: pin)
            self.session = session
            self.biometricFailureCount = 0
            self.message = "Đăng nhập thành công"
            UserDefaults.standard.set(baseURL, forKey: "auth.baseURL")
            UserDefaults.standard.set(keycloakURL, forKey: "auth.keycloakURL")
        }
    }

    func biometricLogin() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let session = try await api.biometricLogin(baseURL: baseURL, keycloakURL: keycloakURL, username: username)
            self.session = session
            self.biometricFailureCount = 0
            self.message = "Đăng nhập sinh trắc học thành công"
            UserDefaults.standard.set(baseURL, forKey: "auth.baseURL")
            UserDefaults.standard.set(keycloakURL, forKey: "auth.keycloakURL")
        } catch {
            biometricFailureCount += 1
            if biometricFailureCount >= 2 {
                message = "FaceID/TouchID đã fail 2 lần. Bạn có thể thử lại hoặc đăng nhập bằng PIN."
            } else {
                message = error.localizedDescription
            }
        }
    }

    func keepAlive() async {
        guard let session else { return }
        await run {
            self.session = try await api.keepAlive(baseURL: baseURL, sessionId: session.sessionId)
            self.message = "Đã gia hạn session"
        }
    }

    func trustDevice() async {
        guard let session else { return }
        await run {
            self.session = try await api.trustDevice(baseURL: baseURL, session: session, pin: pin)
            self.message = "Thiết bị đã được trust"
        }
    }

    func setBiometric(_ enabled: Bool) async {
        guard let session else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            if enabled {
                try await api.enableBiometric(baseURL: baseURL, session: session)
                self.biometricEnabled = true
                self.biometricFailureCount = 0
                UserDefaults.standard.set(true, forKey: "auth.biometricEnabled")
                self.message = "Đã bật login sinh trắc học"
            } else {
                try await api.disableBiometric(baseURL: baseURL, session: session)
                self.biometricEnabled = false
                self.biometricFailureCount = 0
                UserDefaults.standard.set(false, forKey: "auth.biometricEnabled")
                self.message = "Đã tắt login sinh trắc học"
            }
        } catch {
            self.biometricEnabled = !enabled
            UserDefaults.standard.set(!enabled, forKey: "auth.biometricEnabled")
            self.message = error.localizedDescription
        }
    }

    func resetLocalBiometric() {
        BiometricIdentity.deleteKey()
        biometricEnabled = false
        biometricFailureCount = 0
        UserDefaults.standard.set(false, forKey: "auth.biometricEnabled")
        message = "Đã tắt sinh trắc học trên app. Đăng nhập PIN để đồng bộ trạng thái trên server nếu cần."
    }

    private func run(_ action: () async throws -> Void) async {
        isLoading = true
        defer { isLoading = false }
        do {
            try await action()
        } catch {
            message = error.localizedDescription
        }
    }
}

#Preview {
    ContentView()
}
