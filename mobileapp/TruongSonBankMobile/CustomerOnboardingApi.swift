import Foundation

struct OnboardingViewResponse: Decodable {
    let onboardingId: String
    let status: String
    let expiresAt: String?
    let otpExpiresAt: String?
    let phoneMasked: String?
    let cccdMasked: String?
    let fullName: String?
    let accountNumber: String?
    let nextStep: String?
}

struct OnboardingCompleteResponse: Decodable {
    let onboardingId: String
    let status: String
    let customerId: String
    let accountNumber: String
    let sessionId: String
    let expiresInSeconds: Int
    let expiresAt: String?
    let subject: String
    let username: String
    let deviceId: String
    let trustedDevice: Bool
    let roles: [String]
    let nextStep: String

    var authSession: AuthSession {
        AuthSession(sessionId: sessionId,
                    expiresInSeconds: expiresInSeconds,
                    expiresAt: expiresAt ?? "",
                    subject: subject,
                    username: username,
                    deviceId: deviceId,
                    trustedDevice: trustedDevice,
                    roles: roles)
    }
}

final class CustomerOnboardingApi {
    private let logs: ApiLogStore?

    init(logs: ApiLogStore? = nil) {
        self.logs = logs
    }

    func start(baseURL: String, phone: String) async throws -> OnboardingViewResponse {
        try await post(baseURL: baseURL, path: "/onboarding/start", body: StartBody(phone: phone), dpop: false)
    }

    func verifyOtp(baseURL: String, onboardingId: String, otp: String) async throws -> OnboardingViewResponse {
        try await post(baseURL: baseURL, path: "/onboarding/\(onboardingId)/otp/verify", body: OtpBody(otp: otp), dpop: false)
    }

    func verifyQr(baseURL: String, onboardingId: String) async throws -> OnboardingViewResponse {
        try await post(baseURL: baseURL, path: "/onboarding/\(onboardingId)/identity/qr", body: QrBody(), dpop: false)
    }

    func verifyNfc(baseURL: String, onboardingId: String) async throws -> OnboardingViewResponse {
        try await post(baseURL: baseURL, path: "/onboarding/\(onboardingId)/identity/nfc", body: NfcBody(), dpop: false)
    }

    func verifyLiveness(baseURL: String, onboardingId: String) async throws -> OnboardingViewResponse {
        try await post(baseURL: baseURL, path: "/onboarding/\(onboardingId)/identity/liveness", body: LivenessBody(), dpop: false)
    }

    func complete(baseURL: String, onboardingId: String, pin: String) async throws -> OnboardingCompleteResponse {
        try await post(baseURL: baseURL,
                       path: "/onboarding/\(onboardingId)/pin",
                       body: CompleteBody(pin: pin, device: try DeviceIdentity.current()),
                       dpop: true)
    }

    private func post<T: Decodable, B: Encodable>(baseURL: String, path: String, body: B, dpop: Bool) async throws -> T {
        guard let url = URL(string: baseURL + path) else {
            throw AuthApiError.badURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if dpop {
            request.setValue(try DeviceIdentity.dpopProof(method: request.httpMethod ?? "POST", url: url), forHTTPHeaderField: "DPoP")
        }
        request.httpBody = try JSONEncoder().encode(body)
        recordStart(request: request)
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            recordError(request: request, error: error)
            throw error
        }
        record(request: request, data: data, response: response)
        guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
            throw AuthApiError.http(String(data: data, encoding: .utf8) ?? "HTTP error")
        }
        let wrapped = try JSONDecoder().decode(TsbResponse<T>.self, from: data)
        guard wrapped.success, let data = wrapped.data else {
            throw AuthApiError.http(wrapped.message)
        }
        return data
    }

    private func record(request: URLRequest, data: Data, response: URLResponse) {
        let entry = ApiCallLogEntry(method: request.httpMethod ?? "-",
                                    url: request.url?.absoluteString ?? "-",
                                    requestHeaders: (request.allHTTPHeaderFields ?? [:]).map { "\($0): \($1)" }.sorted().joined(separator: "\n"),
                                    requestBody: String(data: request.httpBody ?? Data(), encoding: .utf8) ?? "",
                                    status: (response as? HTTPURLResponse)?.statusCode,
                                    responseBody: String(data: data, encoding: .utf8) ?? "")
        Task { @MainActor in
            logs?.add(entry)
        }
    }

    private func recordStart(request: URLRequest) {
        let entry = ApiCallLogEntry(method: request.httpMethod ?? "-",
                                    url: request.url?.absoluteString ?? "-",
                                    requestHeaders: (request.allHTTPHeaderFields ?? [:]).map { "\($0): \($1)" }.sorted().joined(separator: "\n"),
                                    requestBody: String(data: request.httpBody ?? Data(), encoding: .utf8) ?? "",
                                    status: nil,
                                    responseBody: "Đang gọi API...")
        Task { @MainActor in
            logs?.add(entry)
        }
    }

    private func recordError(request: URLRequest, error: Error) {
        let entry = ApiCallLogEntry(method: request.httpMethod ?? "-",
                                    url: request.url?.absoluteString ?? "-",
                                    requestHeaders: (request.allHTTPHeaderFields ?? [:]).map { "\($0): \($1)" }.sorted().joined(separator: "\n"),
                                    requestBody: String(data: request.httpBody ?? Data(), encoding: .utf8) ?? "",
                                    status: nil,
                                    responseBody: error.localizedDescription)
        Task { @MainActor in
            logs?.add(entry)
        }
    }
}

struct AppConfigSnapshot: Decodable {
    let app: String
    let profile: String
    let version: Int
    let flat: [String: String]
}

final class AppConfigApi {
    private let logs: ApiLogStore?

    init(logs: ApiLogStore? = nil) {
        self.logs = logs
    }

    func load(baseURL: String) async throws -> AppConfigSnapshot {
        guard let url = URL(string: baseURL + "/config/v1/apps/client/profiles/local/versions/latest") else {
            throw AuthApiError.badURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("local-config-key", forHTTPHeaderField: "X-Config-Api-Key")
        recordStart(request)
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            recordError(request, error)
            throw error
        }
        record(request, data, response)
        guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
            throw AuthApiError.http(String(data: data, encoding: .utf8) ?? "Config load failed")
        }
        let wrapped = try JSONDecoder().decode(TsbResponse<AppConfigSnapshot>.self, from: data)
        guard wrapped.success, let data = wrapped.data else {
            throw AuthApiError.http(wrapped.message)
        }
        return data
    }

    private func recordStart(_ request: URLRequest) {
        Task { @MainActor in
            logs?.add(ApiCallLogEntry(method: request.httpMethod ?? "-",
                                      url: request.url?.absoluteString ?? "-",
                                      requestHeaders: headers(request),
                                      requestBody: "",
                                      status: nil,
                                      responseBody: "Đang gọi API..."))
        }
    }

    private func record(_ request: URLRequest, _ data: Data, _ response: URLResponse) {
        Task { @MainActor in
            logs?.add(ApiCallLogEntry(method: request.httpMethod ?? "-",
                                      url: request.url?.absoluteString ?? "-",
                                      requestHeaders: headers(request),
                                      requestBody: "",
                                      status: (response as? HTTPURLResponse)?.statusCode,
                                      responseBody: String(data: data, encoding: .utf8) ?? ""))
        }
    }

    private func recordError(_ request: URLRequest, _ error: Error) {
        Task { @MainActor in
            logs?.add(ApiCallLogEntry(method: request.httpMethod ?? "-",
                                      url: request.url?.absoluteString ?? "-",
                                      requestHeaders: headers(request),
                                      requestBody: "",
                                      status: nil,
                                      responseBody: error.localizedDescription))
        }
    }

    private func headers(_ request: URLRequest) -> String {
        (request.allHTTPHeaderFields ?? [:]).map { "\($0): \($1)" }.sorted().joined(separator: "\n")
    }
}

struct StartBody: Encodable {
    let phone: String
}

struct OtpBody: Encodable {
    let otp: String
}

struct QrBody: Encodable {
    let cccd = "001201000123"
    let oldCccd = "012345678"
    let fullName = "NGUYEN VAN A"
    let dob = "2001-01-01"
    let gender = "M"
    let address = "Ha Noi"
    let issueDate = "2022-01-01"
    let rawQr = "001201000123|012345678|NGUYEN VAN A|20010101|M|Ha Noi|20220101"
}

struct NfcBody: Encodable {
    let providerSessionId = "mock-nfc-session"
    let documentNumber = "001201000123"
}

struct LivenessBody: Encodable {
    let providerSessionId = "mock-liveness-session"
    let livenessToken = "mock-token"
}

struct CompleteBody: Encodable {
    let pin: String
    let device: DeviceInfo
}
