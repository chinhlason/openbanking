import Foundation
import LocalAuthentication
import Security

enum BiometricIdentity {
    private static let keyTag = "vn.com.truongsonbank.mobile.demo.biometric-key"

    static func isAvailable() -> Bool {
        var error: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    static func publicKey() async throws -> String {
        deleteKey()
        let privateKey = try createPrivateKey()
        guard let publicKey = SecKeyCopyPublicKey(privateKey),
              let data = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else {
            throw AuthApiError.http("Không thể sinh khóa biometric")
        }
        return data.base64EncodedString()
    }

    static func sign(payload: String) throws -> String {
        let key = try loadExistingPrivateKey()
        var error: Unmanaged<CFError>?
        guard let signature = SecKeyCreateSignature(key,
                                                    .ecdsaSignatureMessageX962SHA256,
                                                    Data(payload.utf8) as CFData,
                                                    &error) as Data? else {
            if let error {
                throw friendly(error.takeRetainedValue() as Error)
            }
            throw AuthApiError.http("Không thể ký biometric challenge")
        }
        return signature.base64EncodedString()
    }

    static func deleteKey() {
        SecItemDelete([
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: Data(keyTag.utf8),
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom
        ] as CFDictionary)
    }

    private static func loadExistingPrivateKey() throws -> SecKey {
        let tag = Data(keyTag.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tag,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecReturnRef as String: true,
            kSecUseOperationPrompt as String: "Xác thực để đăng nhập sinh trắc học"
        ]
        var item: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let key = item {
            return (key as! SecKey)
        }
        throw AuthApiError.http("Chưa bật đăng nhập sinh trắc học trên thiết bị này")
    }

    private static func createPrivateKey() throws -> SecKey {
        let tag = Data(keyTag.utf8)
        guard let access = SecAccessControlCreateWithFlags(nil,
                                                           kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
                                                           [.privateKeyUsage, .biometryCurrentSet],
                                                           nil) else {
            throw AuthApiError.http("Không thể tạo biometric access control")
        }
        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256,
            kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: tag,
                kSecAttrAccessControl as String: access
            ]
        ]
        var error: Unmanaged<CFError>?
        guard let key = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else {
            if let error {
                throw friendly(error.takeRetainedValue() as Error)
            }
            throw AuthApiError.http("Không thể tạo khóa biometric")
        }
        return key
    }

    private static func friendly(_ error: Error) -> Error {
        let message = error.localizedDescription.lowercased()
        if message.contains("lock") || message.contains("khóa") {
            return AuthApiError.http("FaceID/TouchID đang bị khóa do thử nhiều lần. Hãy khóa màn hình, mở iPhone bằng passcode rồi thử lại, hoặc đăng nhập bằng PIN.")
        }
        return error
    }
}
