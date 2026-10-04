import Foundation

struct VnptSdkConfig {
    let ekycAccessToken: String
    let ekycTokenId: String
    let ekycTokenKey: String
    let ekycBaseUrl: String
    let nfcAccessToken: String
    let nfcTokenId: String
    let nfcTokenKey: String
    let nfcBaseUrl: String
    let nfcEkycAccessToken: String
    let nfcEkycTokenId: String
    let nfcEkycTokenKey: String

    static func load() -> VnptSdkConfig {
        let values = readEnv()
        return VnptSdkConfig(
            ekycAccessToken: values["VNPT_EKYC_ACCESS_TOKEN"] ?? "",
            ekycTokenId: values["VNPT_EKYC_TOKEN_ID"] ?? "",
            ekycTokenKey: values["VNPT_EKYC_TOKEN_KEY"] ?? "",
            ekycBaseUrl: values["VNPT_EKYC_BASE_URL"] ?? "",
            nfcAccessToken: values["VNPT_NFC_ACCESS_TOKEN"] ?? "",
            nfcTokenId: values["VNPT_NFC_TOKEN_ID"] ?? "",
            nfcTokenKey: values["VNPT_NFC_TOKEN_KEY"] ?? "",
            nfcBaseUrl: values["VNPT_NFC_BASE_URL"] ?? "",
            nfcEkycAccessToken: values["VNPT_NFC_EKYC_ACCESS_TOKEN"] ?? values["VNPT_EKYC_ACCESS_TOKEN"] ?? "",
            nfcEkycTokenId: values["VNPT_NFC_EKYC_TOKEN_ID"] ?? values["VNPT_EKYC_TOKEN_ID"] ?? "",
            nfcEkycTokenKey: values["VNPT_NFC_EKYC_TOKEN_KEY"] ?? values["VNPT_EKYC_TOKEN_KEY"] ?? ""
        )
    }

    var isEkycConfigured: Bool {
        !ekycAccessToken.isBlankPlaceholder && !ekycTokenId.isBlankPlaceholder && !ekycTokenKey.isBlankPlaceholder
    }

    var isNfcConfigured: Bool {
        !nfcAccessToken.isBlankPlaceholder && !nfcTokenId.isBlankPlaceholder && !nfcTokenKey.isBlankPlaceholder
    }

    private static func readEnv() -> [String: String] {
        guard let url = Bundle.main.url(forResource: "VnptSdk", withExtension: "env"),
              let text = try? String(contentsOf: url, encoding: .utf8) else {
            return [:]
        }
        return Dictionary(uniqueKeysWithValues: text
            .split(whereSeparator: \.isNewline)
            .compactMap { line -> (String, String)? in
                let raw = String(line).trimmingCharacters(in: .whitespacesAndNewlines)
                guard !raw.isEmpty, !raw.hasPrefix("#"), let index = raw.firstIndex(of: "=") else {
                    return nil
                }
                let key = raw[..<index].trimmingCharacters(in: .whitespacesAndNewlines)
                let value = raw[raw.index(after: index)...].trimmingCharacters(in: .whitespacesAndNewlines)
                return (key, value)
            })
    }
}

private extension String {
    var isBlankPlaceholder: Bool {
        isEmpty || contains("<")
    }
}
