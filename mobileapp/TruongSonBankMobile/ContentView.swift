import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = LoginViewModel()
    @State private var mode = "onboarding"

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    header

                    Picker("", selection: $mode) {
                        Text("Onboarding").tag("onboarding")
                        Text("Login").tag("login")
                        Text("Config").tag("config")
                    }
                    .pickerStyle(.segmented)

                    if mode == "onboarding" {
                        onboardingView
                    } else if mode == "login" {
                        loginView
                    } else {
                        configView
                    }

                    if let session = viewModel.session {
                        sessionView(session)
                    }

                    if let message = viewModel.message {
                        Text(message)
                            .font(.callout)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                            .background(.thinMaterial)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                }
                .padding()
            }
            .navigationTitle("TSB")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink {
                        ApiLogsView(store: viewModel.apiLogs)
                    } label: {
                        Label("\(viewModel.apiLogs.entries.count)", systemImage: "doc.text.magnifyingglass")
                    }
                }
            }
            .task {
                await viewModel.loadStartupConfig()
            }
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("TruongSonBank")
                    .font(.title2.bold())
                Text(viewModel.session == nil ? "Mở tài khoản số" : "Tài khoản đã sẵn sàng")
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: viewModel.session == nil ? "person.crop.circle.badge.plus" : "checkmark.shield.fill")
                .font(.system(size: 38))
                .foregroundStyle(.green)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private var onboardingView: some View {
        VStack(spacing: 14) {
            appCard {
                VStack(spacing: 12) {
                    TextField("Số điện thoại", text: $viewModel.onboardingPhone)
                        .keyboardType(.phonePad)
                        .textInputAutocapitalization(.never)
                    SecureField("PIN 6 số", text: $viewModel.pin)
                        .keyboardType(.numberPad)
                    if viewModel.needsOtp {
                        TextField("OTP", text: $viewModel.onboardingOtp)
                            .keyboardType(.numberPad)
                    }
                }
            }

            appCard {
                VStack(spacing: 10) {
                    OnboardingStepRow(index: 1, title: "Số điện thoại", done: viewModel.stepDone(1), active: viewModel.currentOnboardingStep == 1)
                    OnboardingStepRow(index: 2, title: "OTP SMS", done: viewModel.stepDone(2), active: viewModel.currentOnboardingStep == 2)
                    OnboardingStepRow(index: 3, title: "QR CCCD", done: viewModel.stepDone(3), active: viewModel.currentOnboardingStep == 3)
                    OnboardingStepRow(index: 4, title: "NFC CCCD", done: viewModel.stepDone(4), active: viewModel.currentOnboardingStep == 4)
                    OnboardingStepRow(index: 5, title: "Face liveness", done: viewModel.stepDone(5), active: viewModel.currentOnboardingStep == 5)
                    OnboardingStepRow(index: 6, title: "PIN & account", done: viewModel.stepDone(6), active: viewModel.currentOnboardingStep == 6)
                }
            }

            if let onboarding = viewModel.onboarding {
                appCard {
                    VStack(alignment: .leading, spacing: 8) {
                        LabeledContent("Onboarding ID", value: onboarding.onboardingId)
                LabeledContent("Status", value: onboarding.status)
                        if let nextStep = onboarding.nextStep {
                            LabeledContent("Next step", value: nextStep)
                        }
                        if let cccd = onboarding.cccdMasked {
                            LabeledContent("CCCD", value: cccd)
                        }
                        if let name = onboarding.fullName {
                            LabeledContent("Họ tên", value: name)
                        }
                        if let account = onboarding.accountNumber {
                            LabeledContent("Account", value: account)
                        }
                    }
                    .font(.callout)
                }
            }

            Button {
                Task { await viewModel.performOnboardingPrimaryAction() }
            } label: {
                HStack {
                    if viewModel.isLoading {
                        ProgressView()
                    }
                    Text(viewModel.primaryOnboardingTitle)
                        .fontWeight(.semibold)
                }
                .frame(maxWidth: .infinity)
                .padding()
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isLoading)
        }
    }

    private var loginView: some View {
        appCard {
            VStack(spacing: 12) {
                TextField("Username / phone", text: $viewModel.username)
                    .textInputAutocapitalization(.never)
                SecureField("PIN", text: $viewModel.pin)
                    .keyboardType(.numberPad)
                Button("Đăng nhập bằng PIN") {
                    Task { await viewModel.login() }
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                Button("Đăng nhập bằng FaceID/TouchID") {
                    Task { await viewModel.biometricLogin() }
                }
                .buttonStyle(.bordered)
                if viewModel.biometricEnabled {
                    Button("Tắt sinh trắc học trên app") {
                        viewModel.resetLocalBiometric()
                    }
                }
            }
        }
    }

    private var configView: some View {
        appCard {
            VStack(spacing: 12) {
                TextField("Customer URL", text: $viewModel.customerURL)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                TextField("Auth URL", text: $viewModel.baseURL)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                TextField("Keycloak URL", text: $viewModel.keycloakURL)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
            }
        }
    }

    private func sessionView(_ session: AuthSession) -> some View {
        appCard {
            VStack(alignment: .leading, spacing: 10) {
                LabeledContent("Username", value: session.username)
                LabeledContent("Device", value: session.deviceId)
                LabeledContent("Trusted", value: session.trustedDevice ? "Yes" : "No")
                LabeledContent("Expires", value: session.expiresAt)
                Text(session.sessionId)
                    .font(.caption.monospaced())
                    .textSelection(.enabled)
                HStack {
                    Button("Keep alive") {
                        Task { await viewModel.keepAlive() }
                    }
                    if session.trustedDevice {
                        Toggle("Bio", isOn: $viewModel.biometricEnabled)
                            .onChange(of: viewModel.biometricEnabled) { _, enabled in
                                Task { await viewModel.setBiometric(enabled) }
                            }
                    } else {
                        Button("Trust device") {
                            Task { await viewModel.trustDevice() }
                        }
                    }
                }
            }
        }
    }

    private func appCard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct OnboardingStepRow: View {
    let index: Int
    let title: String
    let done: Bool
    let active: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: done ? "checkmark.circle.fill" : "\(index).circle")
                .foregroundStyle(done ? .green : active ? .blue : .secondary)
                .font(.title3)
            Text(title)
                .fontWeight(active ? .semibold : .regular)
            Spacer()
        }
        .frame(height: 30)
    }
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var baseURL = LoginViewModel.savedURL("auth.baseURL", fallback: "http://192.168.0.101:8086/bff/api/auth/v1")
    @Published var keycloakURL = LoginViewModel.savedURL("auth.keycloakURL", fallback: "http://192.168.0.101:8088/realms/truongsonbank/protocol/openid-connect/token")
    @Published var customerURL = LoginViewModel.savedURL("customer.baseURL", fallback: "http://192.168.0.101:8086/bff/api/client/v1")
    @Published var commonURL = LoginViewModel.savedURL("common.baseURL", fallback: "http://192.168.0.101:8086/bff/api/common")
    @Published var username = "84901234567"
    @Published var pin = "739204"
    @Published var onboardingPhone = "84901234567"
    @Published var onboardingOtp = "123456"
    @Published var onboarding: OnboardingViewResponse?
    @Published var session: AuthSession?
    @Published var message: String?
    @Published var isLoading = false
    @Published var biometricEnabled = UserDefaults.standard.bool(forKey: "auth.biometricEnabled")

    let apiLogs = ApiLogStore()
    private lazy var api = AuthApi(logs: apiLogs)
    private lazy var onboardingApi = CustomerOnboardingApi(logs: apiLogs)
    private lazy var configApi = AppConfigApi(logs: apiLogs)
    private var didLoadStartupConfig = false

    private static func savedURL(_ key: String, fallback: String) -> String {
        let value = UserDefaults.standard.string(forKey: key) ?? fallback
        if value.contains("172.20.10.4") {
            let migrated = value.replacingOccurrences(of: "172.20.10.4", with: "192.168.0.101")
            UserDefaults.standard.set(migrated, forKey: key)
            return migrated
        }
        if value.contains(":8081/client/api/v1") {
            let migrated = value
                .replacingOccurrences(of: ":8081/client/api/v1", with: ":8086/bff/api/client/v1")
            UserDefaults.standard.set(migrated, forKey: key)
            return migrated
        }
        return value
    }
    private var biometricFailureCount = 0

    var currentOnboardingStep: Int {
        switch onboarding?.nextStep ?? statusNextStep {
        case nil:
            return 1
        case "OTP":
            return 2
        case "QR":
            return 3
        case "NFC":
            return 4
        case "LIVENESS":
            return 5
        case "PIN":
            return 6
        default:
            return 7
        }
    }

    private var statusNextStep: String? {
        switch onboarding?.status {
        case nil:
            return nil
        case "STARTED":
            return "OTP"
        case "OTP_VERIFIED":
            return "QR"
        case "QR_VERIFIED":
            return "NFC"
        case "NFC_VERIFIED":
            return "LIVENESS"
        case "LIVENESS_VERIFIED", "AUTH_CREATED", "ACCOUNT_CREATED":
            return "PIN"
        default:
            return "DONE"
        }
    }

    var needsOtp: Bool {
        currentOnboardingStep == 2
    }

    var primaryOnboardingTitle: String {
        switch currentOnboardingStep {
        case 1:
            return "Tiếp tục"
        case 2:
            return "Xác thực OTP"
        case 3:
            return "Quét QR CCCD"
        case 4:
            return "Đọc NFC CCCD"
        case 5:
            return "Xác thực khuôn mặt"
        case 6:
            return "Hoàn tất đăng ký"
        default:
            return "Đã hoàn tất"
        }
    }

    func stepDone(_ step: Int) -> Bool {
        currentOnboardingStep > step
    }

    func performOnboardingPrimaryAction() async {
        switch currentOnboardingStep {
        case 1:
            await startOnboarding()
        case 2:
            await verifyOnboardingOtp()
        case 3:
            await verifyOnboardingQr()
        case 4:
            await verifyOnboardingNfc()
        case 5:
            await verifyOnboardingLiveness()
        case 6:
            await completeOnboarding()
        default:
            message = "Onboarding đã hoàn tất"
        }
    }

    func login() async {
        await run {
            let session = try await api.login(baseURL: baseURL, keycloakURL: keycloakURL, username: username, pin: pin)
            self.session = session
            self.biometricFailureCount = 0
            self.message = "Đăng nhập thành công"
            UserDefaults.standard.set(baseURL, forKey: "auth.baseURL")
            UserDefaults.standard.set(keycloakURL, forKey: "auth.keycloakURL")
            UserDefaults.standard.set(customerURL, forKey: "customer.baseURL")
        }
    }

    func loadStartupConfig() async {
        guard !didLoadStartupConfig else { return }
        didLoadStartupConfig = true
        do {
            let snapshot = try await configApi.load(baseURL: commonURL)
            message = "Loaded config v\(snapshot.version)"
            UserDefaults.standard.set(commonURL, forKey: "common.baseURL")
        } catch {
            message = "Không load được config ban đầu: \(error.localizedDescription)"
        }
    }

    func startOnboarding() async {
        await run {
            onboarding = try await onboardingApi.start(baseURL: customerURL, phone: onboardingPhone)
            message = "OTP mock: 123456"
            UserDefaults.standard.set(customerURL, forKey: "customer.baseURL")
        }
    }

    func verifyOnboardingOtp() async {
        guard let id = onboarding?.onboardingId else { return }
        await run {
            onboarding = try await onboardingApi.verifyOtp(baseURL: customerURL, onboardingId: id, otp: onboardingOtp)
            message = "OTP verified"
        }
    }

    func verifyOnboardingQr() async {
        guard let id = onboarding?.onboardingId else { return }
        await run {
            onboarding = try await onboardingApi.verifyQr(baseURL: customerURL, onboardingId: id)
            message = "QR CCCD verified"
        }
    }

    func verifyOnboardingNfc() async {
        guard let id = onboarding?.onboardingId else { return }
        await run {
            onboarding = try await onboardingApi.verifyNfc(baseURL: customerURL, onboardingId: id)
            message = "NFC CCCD verified"
        }
    }

    func verifyOnboardingLiveness() async {
        guard let id = onboarding?.onboardingId else { return }
        await run {
            onboarding = try await onboardingApi.verifyLiveness(baseURL: customerURL, onboardingId: id)
            message = "Liveness verified"
        }
    }

    func completeOnboarding() async {
        guard let id = onboarding?.onboardingId else { return }
        await run {
            let completed = try await onboardingApi.complete(baseURL: customerURL, onboardingId: id, pin: pin)
            session = completed.authSession
            username = completed.username
            onboarding = OnboardingViewResponse(onboardingId: completed.onboardingId,
                                                status: completed.status,
                                                expiresAt: nil,
                                                otpExpiresAt: nil,
                                                phoneMasked: nil,
                                                cccdMasked: nil,
                                                fullName: nil,
                                                accountNumber: completed.accountNumber,
                                                nextStep: completed.nextStep)
            message = "Onboarding hoàn tất, account \(completed.accountNumber)"
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
