import SwiftUI
import UIKit
import ICSdkEKYC

@MainActor
final class VnptSdkPresenter: NSObject, ICEkycCameraDelegate {
    static let shared = VnptSdkPresenter()

    private var ekycContinuation: CheckedContinuation<String, Error>?
    private var ekycResultKind: EkycResultKind = .ocr

    func openQrScan() async throws -> String {
        try await openEkyc(flow: scanQR, resultKind: .qr)
    }

    func openLiveness() async throws -> String {
        try await openEkyc(flow: face, resultKind: .face)
    }

    func openNfc() async throws -> String {
        "mock-nfc-result"
    }

    private func openEkyc(flow: FlowType, resultKind: EkycResultKind) async throws -> String {
        let config = VnptSdkConfig.load()
        guard config.isEkycConfigured else {
            throw VnptSdkError.sdk("Chưa cấu hình token eKYC trong VnptSdk.env")
        }
        return try await withCheckedThrowingContinuation { continuation in
            ekycContinuation = continuation
            ekycResultKind = resultKind
            let controller = ICEkycCameraRouter.createModule() as! ICEkycCameraViewController
            controller.cameraDelegate = self
            controller.accessToken = config.ekycAccessToken
            controller.tokenId = config.ekycTokenId
            controller.tokenKey = config.ekycTokenKey
            controller.languageSdk = "icekyc_vi"
            controller.flowEKYC = ICEKYCNTB
            controller.flowType = flow
            controller.documentType = IDCardChipBased
            controller.versionSdk = ProOval
            controller.isShowTutorial = true
            controller.isEnableGotIt = true
            controller.isEnableScanQRCode = flow == scanQR
            controller.checkLivenessFace = flow == face ? IBeta : NoneCheckFace
            controller.modalPresentationStyle = .fullScreen
            topViewController()?.present(controller, animated: true)
        }
    }

    func icEkycGetResult() {
        let saved = ICEKYCSavedData.shared()
        let candidates: [String]
        switch ekycResultKind {
        case .qr:
            candidates = [saved.qrCodeResult, saved.qrCodeResultDetail, saved.ocrResult]
        case .face:
            candidates = [saved.livenessFaceResult, saved.verifyFaceResult, saved.clientSessionResult]
        case .ocr:
            candidates = [saved.ocrResult]
        }
        let result = candidates.first { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } ?? ""
        guard !result.isEmpty else {
            ekycContinuation?.resume(throwing: VnptSdkError.sdk("SDK không trả dữ liệu hợp lệ"))
            ekycContinuation = nil
            return
        }
        ekycContinuation?.resume(returning: result)
        ekycContinuation = nil
    }

    func icEkycCameraClosed(with type: ScreenType) {
        ekycContinuation?.resume(throwing: VnptSdkError.sdk("Đã đóng eKYC SDK ở bước \(type.rawValue)"))
        ekycContinuation = nil
    }

    private func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

private enum EkycResultKind {
    case qr
    case face
    case ocr
}

enum VnptSdkError: LocalizedError {
    case sdk(String)

    var errorDescription: String? {
        switch self {
        case .sdk(let message):
            return message
        }
    }
}
