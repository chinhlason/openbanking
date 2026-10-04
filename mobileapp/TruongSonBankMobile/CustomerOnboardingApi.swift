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

    func verifyQr(baseURL: String, onboardingId: String, rawQr: String) async throws -> OnboardingViewResponse {
        try await post(baseURL: baseURL, path: "/onboarding/\(onboardingId)/identity/qr", body: try QrBody(rawQr: rawQr), dpop: false)
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
    let cccd: String
    let oldCccd: String
    let fullName: String
    let dob: String
    let gender: String
    let address: String
    let issueDate: String
    let rawQr: String

    init(rawQr: String) throws {
        let text = rawQr.trimmingCharacters(in: .whitespacesAndNewlines)
        if let values = Self.parseJson(text) {
            self.cccd = try Self.required(values, ["cccd", "id", "idnumber", "identitynumber", "documentnumber"])
            self.oldCccd = Self.optional(values, ["oldcccd", "oldid", "oldidnumber", "cmnd"]) ?? ""
            self.fullName = try Self.required(values, ["fullname", "name", "hoten"])
            self.dob = Self.normalizedDate(Self.optional(values, ["dob", "birthday", "dateofbirth", "ngaysinh"]) ?? "")
            self.gender = Self.optional(values, ["gender", "sex", "gioitinh"]) ?? ""
            self.address = Self.optional(values, ["address", "residentaddress", "recentlocation", "diachi"]) ?? ""
            self.issueDate = Self.normalizedDate(Self.optional(values, ["issuedate", "issue_date", "ngaycap"]) ?? "")
            self.rawQr = text
            return
        }

        let parts = text.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard parts.count >= 7, !parts[0].isEmpty, !parts[2].isEmpty else {
            throw AuthApiError.http("QR CCCD không trả dữ liệu hợp lệ")
        }
        self.cccd = parts[0]
        self.oldCccd = parts[1]
        self.fullName = parts[2]
        self.dob = Self.normalizedDate(parts[3])
        self.gender = parts[4]
        self.address = parts[5]
        self.issueDate = Self.normalizedDate(parts[6])
        self.rawQr = text
    }

    private static func parseJson(_ text: String) -> [String: String]? {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) else {
            return nil
        }
        var values: [String: String] = [:]
        flatten(json, into: &values)
        return values.isEmpty ? nil : values
    }

    private static func flatten(_ value: Any, into values: inout [String: String]) {
        if let dict = value as? [String: Any] {
            for (key, item) in dict {
                let normalizedKey = key.lowercased().replacingOccurrences(of: "_", with: "")
                if let string = item as? String, !string.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    values[normalizedKey] = string
                } else if let number = item as? NSNumber {
                    values[normalizedKey] = number.stringValue
                } else {
                    flatten(item, into: &values)
                }
            }
        } else if let array = value as? [Any] {
            array.forEach { flatten($0, into: &values) }
        }
    }

    private static func required(_ values: [String: String], _ keys: [String]) throws -> String {
        if let value = optional(values, keys) {
            return value
        }
        throw AuthApiError.http("QR CCCD thiếu thông tin bắt buộc")
    }

    private static func optional(_ values: [String: String], _ keys: [String]) -> String? {
        keys.lazy
            .map { $0.lowercased().replacingOccurrences(of: "_", with: "") }
            .compactMap { values[$0]?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty }
    }

    private static func normalizedDate(_ value: String) -> String {
        let digits = value.filter(\.isNumber)
        guard digits.count == 8 else { return value }
        let first4 = Int(digits.prefix(4)) ?? 0
        if (1900...2099).contains(first4) {
            return "\(digits.prefix(4))-\(digits.dropFirst(4).prefix(2))-\(digits.suffix(2))"
        }
        return "\(digits.suffix(4))-\(digits.dropFirst(2).prefix(2))-\(digits.prefix(2))"
    }
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
