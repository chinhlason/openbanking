import Foundation

struct TsbResponse<T: Decodable>: Decodable {
    let success: Bool
    let code: String
    let message: String
    let data: T?
}

struct AuthSession: Decodable {
    let sessionId: String
    let expiresInSeconds: Int
    let expiresAt: String
    let subject: String
    let username: String
    let deviceId: String
    let trustedDevice: Bool
    let roles: [String]
}

struct EntitlementTestResult: Decodable {
    let allowed: Bool
    let operation: String
    let service: String
}

final class AuthApi {
    private let logs: ApiLogStore?

    init(logs: ApiLogStore? = nil) {
        self.logs = logs
    }

    func login(baseURL: String, keycloakURL: String, username: String, pin: String) async throws -> AuthSession {
        let device = try DeviceIdentity.current()
        guard let keycloakTokenURL = URL(string: keycloakURL) else {
            throw AuthApiError.badURL
        }
        let challenge: LoginChallenge = try await post(baseURL: baseURL,
                                                       path: "/login/init",
                                                       body: LoginInitBody(loginType: "PIN", username: username, device: device),
                                                       sessionId: nil,
                                                       bearerToken: nil)
        return try await post(baseURL: baseURL,
                              path: "/login/verify",
                              body: LoginVerifyBody(loginType: "PIN",
                                                    username: username,
                                                    pin: pin,
                                                    password: nil,
                                                    challengeId: challenge.challengeId,
                                                    nonce: challenge.nonce,
                                                    signature: nil,
                                                    device: device,
                                                    keycloakDpopProof: try DeviceIdentity.dpopProof(method: "POST", url: keycloakTokenURL)),
                              sessionId: nil,
                              bearerToken: nil,
                              dpopRequired: true)
    }

    func biometricLogin(baseURL: String, keycloakURL: String, username: String) async throws -> AuthSession {
        let device = try DeviceIdentity.current()
        guard let keycloakTokenURL = URL(string: keycloakURL) else {
            throw AuthApiError.badURL
        }
        let challenge: LoginChallenge = try await post(baseURL: baseURL,
                                                       path: "/login/init",
                                                       body: LoginInitBody(loginType: "BIOMETRIC", username: username, device: device),
                                                       sessionId: nil,
                                                       bearerToken: nil)
        return try await post(baseURL: baseURL,
                              path: "/login/verify",
                              body: LoginVerifyBody(loginType: "BIOMETRIC",
                                                    username: username,
                                                    pin: nil,
                                                    password: nil,
                                                    challengeId: challenge.challengeId,
                                                    nonce: challenge.nonce,
                                                    signature: try BiometricIdentity.sign(payload: challenge.payload),
                                                    device: device,
                                                    keycloakDpopProof: try DeviceIdentity.dpopProof(method: "POST", url: keycloakTokenURL)),
                              sessionId: nil,
                              bearerToken: nil,
                              dpopRequired: true)
    }

    func passkeyLogin(baseURL: String, keycloakURL: String, username: String) async throws -> AuthSession {
        let device = try DeviceIdentity.current()
        guard let keycloakTokenURL = URL(string: keycloakURL) else {
            throw AuthApiError.badURL
        }
        let challenge: LoginChallenge = try await post(baseURL: baseURL,
                                                       path: "/login/init",
                                                       body: LoginInitBody(loginType: "PASSKEY", username: username, device: device),
                                                       sessionId: nil,
                                                       bearerToken: nil)
        return try await post(baseURL: baseURL,
                              path: "/login/verify",
                              body: LoginVerifyBody(loginType: "PASSKEY",
                                                    username: username,
                                                    pin: nil,
                                                    password: nil,
                                                    challengeId: challenge.challengeId,
                                                    nonce: challenge.nonce,
                                                    signature: try PasskeyIdentity.sign(payload: challenge.payload),
                                                    device: device,
                                                    keycloakDpopProof: try DeviceIdentity.dpopProof(method: "POST", url: keycloakTokenURL)),
                              sessionId: nil,
                              bearerToken: nil,
                              dpopRequired: true)
    }

    func keepAlive(baseURL: String, sessionId: String) async throws -> AuthSession {
        try await post(baseURL: baseURL, path: "/sessions/keep-alive", body: EmptyBody(), sessionId: sessionId, bearerToken: nil)
    }

    func trustDevice(baseURL: String, keycloakURL: String, session: AuthSession, pin: String) async throws -> AuthSession {
        guard let keycloakTokenURL = URL(string: keycloakURL) else {
            throw AuthApiError.badURL
        }
        return try await post(baseURL: baseURL,
                              path: "/devices/\(session.deviceId)/trust",
                              body: TrustDeviceBody(pin: pin, keycloakDpopProof: try DeviceIdentity.dpopProof(method: "POST", url: keycloakTokenURL)),
                              sessionId: session.sessionId,
                              bearerToken: nil)
    }

    func enableBiometric(baseURL: String, session: AuthSession) async throws {
        let challenge: BiometricChallenge = try await post(baseURL: baseURL,
                                                           path: "/devices/\(session.deviceId)/biometric/enable/challenge",
                                                           body: EmptyBody(),
                                                           sessionId: session.sessionId,
                                                           bearerToken: nil)
        let publicKey = try await BiometricIdentity.publicKey()
        let body = BiometricEnableBody(publicKey: publicKey,
                                       challengeId: challenge.challengeId,
                                       nonce: challenge.nonce,
                                       signature: try BiometricIdentity.sign(payload: challenge.payload))
        try await post(baseURL: baseURL, path: "/devices/\(session.deviceId)/biometric/enable", body: body, sessionId: session.sessionId, bearerToken: nil) as SimpleResult
    }

    func disableBiometric(baseURL: String, session: AuthSession) async throws {
        try await post(baseURL: baseURL, path: "/devices/\(session.deviceId)/biometric/disable", body: EmptyBody(), sessionId: session.sessionId, bearerToken: nil) as SimpleResult
        BiometricIdentity.deleteKey()
    }

    func enablePasskey(baseURL: String, session: AuthSession) async throws {
        let challenge: BiometricChallenge = try await post(baseURL: baseURL,
                                                           path: "/devices/\(session.deviceId)/passkey/enable/challenge",
                                                           body: EmptyBody(),
                                                           sessionId: session.sessionId,
                                                           bearerToken: nil)
        let body = BiometricEnableBody(publicKey: try PasskeyIdentity.publicKey(),
                                       challengeId: challenge.challengeId,
                                       nonce: challenge.nonce,
                                       signature: try PasskeyIdentity.sign(payload: challenge.payload))
        try await post(baseURL: baseURL, path: "/devices/\(session.deviceId)/passkey/enable", body: body, sessionId: session.sessionId, bearerToken: nil) as SimpleResult
    }

    func disablePasskey(baseURL: String, session: AuthSession) async throws {
        try await post(baseURL: baseURL, path: "/devices/\(session.deviceId)/passkey/disable", body: EmptyBody(), sessionId: session.sessionId, bearerToken: nil) as SimpleResult
        PasskeyIdentity.deleteKey()
    }

    func entitlementTest(baseURL: String, sessionId: String) async throws -> EntitlementTestResult {
        try await get(baseURL: baseURL, path: "/shared-test/entitlement/test2", sessionId: sessionId)
    }

    private func post<T: Decodable, B: Encodable>(baseURL: String, path: String, body: B, sessionId: String?, bearerToken: String?, dpopNonce: String? = nil, dpopAccessToken: String? = nil, dpopRequired: Bool = false) async throws -> T {
        guard let url = URL(string: baseURL + path) else {
            throw AuthApiError.badURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let sessionId {
            request.setValue(sessionId, forHTTPHeaderField: "X-Session-Id")
        }
        if let bearerToken {
            request.setValue("DPoP \(bearerToken)", forHTTPHeaderField: "Authorization")
        }
        if sessionId != nil || dpopNonce != nil || dpopRequired {
            request.setValue(try DeviceIdentity.dpopProof(method: request.httpMethod ?? "POST", url: url, nonce: dpopNonce, accessToken: dpopAccessToken), forHTTPHeaderField: "DPoP")
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

    private func get<T: Decodable>(baseURL: String, path: String, sessionId: String?) async throws -> T {
        guard let url = URL(string: baseURL + path) else {
            throw AuthApiError.badURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let sessionId {
            request.setValue(sessionId, forHTTPHeaderField: "X-Session-Id")
        }

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

    private func urlEncode(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private func record(request: URLRequest, data: Data, response: URLResponse) {
        let entry = ApiCallLogEntry(method: request.httpMethod ?? "-",
                                    url: request.url?.absoluteString ?? "-",
                                    requestHeaders: headers(request.allHTTPHeaderFields ?? [:]),
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
                                    requestHeaders: headers(request.allHTTPHeaderFields ?? [:]),
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
                                    requestHeaders: headers(request.allHTTPHeaderFields ?? [:]),
                                    requestBody: String(data: request.httpBody ?? Data(), encoding: .utf8) ?? "",
                                    status: nil,
                                    responseBody: error.localizedDescription)
        Task { @MainActor in
            logs?.add(entry)
        }
    }

    private func headers(_ headers: [String: String]) -> String {
        headers
            .map { key, value in
                "\(key): \(value)"
            }
            .sorted()
            .joined(separator: "\n")
    }
}

struct KeycloakToken: Decodable {
    let accessToken: String

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
    }
}

struct ExchangeBody: Encodable {
    let device: DeviceInfo
}

struct DeviceInfo: Encodable {
    let deviceId: String
    let deviceName: String
    let platform: String
    let osVersion: String
    let appVersion: String
    let publicKey: String
}

struct EmptyBody: Encodable {
}

struct DpopNonce: Decodable {
    let nonce: String
    let expiresInSeconds: Int
}

struct LoginInitBody: Encodable {
    let loginType: String
    let username: String
    let device: DeviceInfo
}

struct LoginVerifyBody: Encodable {
    let loginType: String
    let username: String
    let pin: String?
    let password: String?
    let challengeId: String
    let nonce: String
    let signature: String?
    let device: DeviceInfo
    let keycloakDpopProof: String
}

struct LoginChallenge: Decodable {
    let challengeId: String
    let nonce: String
    let expiresInSeconds: Int
    let payload: String
    let availableMethods: [String]
    let trustedDeviceRequired: Bool
}

struct TrustDeviceBody: Encodable {
    let pin: String
    let keycloakDpopProof: String
}

struct BiometricEnableBody: Encodable {
    let publicKey: String
    let challengeId: String
    let nonce: String
    let signature: String
}

struct BiometricChallengeBody: Encodable {
    let username: String
    let deviceId: String
}

struct BiometricLoginVerifyBody: Encodable {
    let username: String
    let deviceId: String
    let challengeId: String
    let nonce: String
    let signature: String
    let device: DeviceInfo
    let keycloakDpopProof: String
}

struct BiometricChallenge: Decodable {
    let challengeId: String
    let nonce: String
    let expiresInSeconds: Int
    let payload: String
}

struct SimpleResult: Decodable {
}

enum AuthApiError: LocalizedError {
    case badURL
    case http(String)

    var errorDescription: String? {
        switch self {
        case .badURL:
            return "Base URL không hợp lệ"
        case .http(let message):
            return message
        }
    }
}
