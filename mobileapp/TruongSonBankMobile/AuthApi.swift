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

final class AuthApi {
    private let logs: ApiLogStore?

    init(logs: ApiLogStore? = nil) {
        self.logs = logs
    }

    func login(baseURL: String, keycloakURL: String, username: String, pin: String) async throws -> AuthSession {
        let token = try await keycloakToken(keycloakURL: keycloakURL, username: username, pin: pin)
        let nonce = try await dpopNonce(baseURL: baseURL)
        let body = ExchangeBody(device: try DeviceIdentity.current())
        return try await post(baseURL: baseURL, path: "/token/exchange", body: body, sessionId: nil, bearerToken: token, dpopNonce: nonce.nonce, dpopAccessToken: token)
    }

    func biometricLogin(baseURL: String, keycloakURL: String, username: String) async throws -> AuthSession {
        let deviceId = try DeviceIdentity.currentDeviceId()
        let challenge: BiometricChallenge = try await post(baseURL: baseURL,
                                                           path: "/biometric/challenge",
                                                           body: BiometricChallengeBody(username: username, deviceId: deviceId),
                                                           sessionId: nil,
                                                           bearerToken: nil)
        let signature = try BiometricIdentity.sign(payload: challenge.payload)
        let token = try await biometricKeycloakToken(keycloakURL: keycloakURL,
                                                     username: username,
                                                     deviceId: deviceId,
                                                     challenge: challenge,
                                                     signature: signature)
        let nonce = try await dpopNonce(baseURL: baseURL)
        let body = ExchangeBody(device: try DeviceIdentity.current())
        return try await post(baseURL: baseURL, path: "/token/exchange", body: body, sessionId: nil, bearerToken: token, dpopNonce: nonce.nonce, dpopAccessToken: token)
    }

    func keepAlive(baseURL: String, sessionId: String) async throws -> AuthSession {
        try await post(baseURL: baseURL, path: "/sessions/keep-alive", body: EmptyBody(), sessionId: sessionId, bearerToken: nil)
    }

    func trustDevice(baseURL: String, session: AuthSession, pin: String) async throws -> AuthSession {
        try await post(baseURL: baseURL, path: "/devices/\(session.deviceId)/trust", body: TrustDeviceBody(pin: pin), sessionId: session.sessionId, bearerToken: nil)
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

    private func keycloakToken(keycloakURL: String, username: String, pin: String) async throws -> String {
        guard let url = URL(string: keycloakURL) else {
            throw AuthApiError.badURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(try DeviceIdentity.dpopProof(method: request.httpMethod ?? "POST", url: url), forHTTPHeaderField: "DPoP")
        request.httpBody = [
            "grant_type=password",
            "client_id=truongsonbank-mobile",
            "username=\(urlEncode(username))",
            "password=\(urlEncode(pin))"
        ].joined(separator: "&").data(using: .utf8)

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
            throw AuthApiError.http(String(data: data, encoding: .utf8) ?? "Keycloak login failed")
        }
        let token = try JSONDecoder().decode(KeycloakToken.self, from: data)
        return token.accessToken
    }

    private func biometricKeycloakToken(keycloakURL: String,
                                        username: String,
                                        deviceId: String,
                                        challenge: BiometricChallenge,
                                        signature: String) async throws -> String {
        guard let url = URL(string: keycloakURL) else {
            throw AuthApiError.badURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(try DeviceIdentity.dpopProof(method: request.httpMethod ?? "POST", url: url), forHTTPHeaderField: "DPoP")
        request.httpBody = [
            "grant_type=biometric",
            "client_id=truongsonbank-mobile",
            "username=\(urlEncode(username))",
            "device_id=\(urlEncode(deviceId))",
            "challenge_id=\(urlEncode(challenge.challengeId))",
            "nonce=\(urlEncode(challenge.nonce))",
            "signature=\(urlEncode(signature))"
        ].joined(separator: "&").data(using: .utf8)

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
            throw AuthApiError.http(String(data: data, encoding: .utf8) ?? "Keycloak biometric login failed")
        }
        return try JSONDecoder().decode(KeycloakToken.self, from: data).accessToken
    }

    private func dpopNonce(baseURL: String) async throws -> DpopNonce {
        try await post(baseURL: baseURL, path: "/dpop/nonce", body: EmptyBody(), sessionId: nil, bearerToken: nil)
    }

    private func post<T: Decodable, B: Encodable>(baseURL: String, path: String, body: B, sessionId: String?, bearerToken: String?, dpopNonce: String? = nil, dpopAccessToken: String? = nil) async throws -> T {
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
        if sessionId != nil || dpopNonce != nil {
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

struct TrustDeviceBody: Encodable {
    let pin: String
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
