import Foundation
import CryptoKit
import Security
import UIKit

enum DeviceIdentity {
    private static let service = "vn.com.truongsonbank.mobile.demo"
    private static let deviceIdAccount = "device-id"
    private static let keyTag = "vn.com.truongsonbank.mobile.demo.device-key"

    static func current() throws -> DeviceInfo {
        DeviceInfo(
            deviceId: try currentDeviceId(),
            deviceName: UIDevice.current.name,
            platform: "ios",
            osVersion: UIDevice.current.systemVersion,
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0",
            publicKey: try publicKey()
        )
    }

    static func currentDeviceId() throws -> String {
        try deviceId()
    }

    static func dpopProof(method: String, url: URL, nonce: String? = nil, accessToken: String? = nil) throws -> String {
        let jwk = try publicJwk()
        let header = #"{"alg":"ES256","typ":"dpop+jwt","jwk":\#(jwk)}"#
        var claims = [
            #""htm":"\#(method.uppercased())""#,
            #""htu":"\#(url.absoluteString)""#,
            #""iat":\#(Int(Date().timeIntervalSince1970))"#,
            #""jti":"\#(UUID().uuidString)""#
        ]
        if let nonce {
            claims.append(#""nonce":"\#(nonce)""#)
        }
        if let accessToken {
            claims.append(#""ath":"\#(base64Url(Data(SHA256.hash(data: Data(accessToken.utf8)))))""#)
        }
        let payload = "{\(claims.joined(separator: ","))}"
        let signingInput = "\(base64Url(Data(header.utf8))).\(base64Url(Data(payload.utf8)))"
        var error: Unmanaged<CFError>?
        guard let signature = SecKeyCreateSignature(try loadOrCreatePrivateKey(),
                                                    .ecdsaSignatureMessageX962SHA256,
                                                    Data(signingInput.utf8) as CFData,
                                                    &error) as Data? else {
            if let error {
                throw error.takeRetainedValue() as Error
            }
            throw AuthApiError.http("Không thể ký DPoP")
        }
        return "\(signingInput).\(base64Url(try derToRaw(signature)))"
    }

    private static func deviceId() throws -> String {
        if let existing = try readPassword(account: deviceIdAccount) {
            return existing
        }
        let value = UUID().uuidString
        try savePassword(value, account: deviceIdAccount)
        return value
    }

    private static func publicKey() throws -> String {
        let privateKey = try loadOrCreatePrivateKey()
        guard let publicKey = SecKeyCopyPublicKey(privateKey),
              let data = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else {
            throw AuthApiError.http("Không thể sinh khóa thiết bị")
        }
        return data.base64EncodedString()
    }

    private static func publicJwk() throws -> String {
        let privateKey = try loadOrCreatePrivateKey()
        guard let publicKey = SecKeyCopyPublicKey(privateKey),
              let data = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else {
            throw AuthApiError.http("Không thể đọc khóa thiết bị")
        }
        let bytes = [UInt8](data)
        guard bytes.count == 65, bytes[0] == 0x04 else {
            throw AuthApiError.http("Public key thiết bị không hợp lệ")
        }
        let x = base64Url(Data(bytes[1..<33]))
        let y = base64Url(Data(bytes[33..<65]))
        return #"{"crv":"P-256","kty":"EC","x":"\#(x)","y":"\#(y)"}"#
    }

    private static func loadOrCreatePrivateKey() throws -> SecKey {
        let tagData = Data(keyTag.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecReturnRef as String: true
        ]
        var item: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let key = item {
            return (key as! SecKey)
        }

        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: tagData
            ]
        ]
        var error: Unmanaged<CFError>?
        guard let key = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else {
            if let error {
                throw error.takeRetainedValue() as Error
            }
            throw AuthApiError.http("Không thể tạo khóa thiết bị")
        }
        return key
    }

    private static func readPassword(account: String) throws -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess, let data = item as? Data else {
            throw AuthApiError.http("Không thể đọc Keychain")
        }
        return String(data: data, encoding: .utf8)
    }

    private static func savePassword(_ value: String, account: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: Data(value.utf8)
        ]
        SecItemDelete(query as CFDictionary)
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw AuthApiError.http("Không thể lưu Keychain")
        }
    }

    private static func base64Url(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func derToRaw(_ der: Data) throws -> Data {
        let bytes = [UInt8](der)
        guard bytes.count > 8, bytes[0] == 0x30 else {
            throw AuthApiError.http("Chữ ký DPoP không hợp lệ")
        }
        var index = 2
        guard bytes[index] == 0x02 else { throw AuthApiError.http("Chữ ký DPoP không hợp lệ") }
        index += 1
        let rLength = Int(bytes[index])
        index += 1
        let r = Array(bytes[index..<index + rLength])
        index += rLength
        guard bytes[index] == 0x02 else { throw AuthApiError.http("Chữ ký DPoP không hợp lệ") }
        index += 1
        let sLength = Int(bytes[index])
        index += 1
        let s = Array(bytes[index..<index + sLength])
        return Data(fixed32(r) + fixed32(s))
    }

    private static func fixed32(_ value: [UInt8]) -> [UInt8] {
        let trimmed = value.first == 0 ? Array(value.dropFirst()) : value
        if trimmed.count >= 32 {
            return Array(trimmed.suffix(32))
        }
        return Array(repeating: 0, count: 32 - trimmed.count) + trimmed
    }
}
