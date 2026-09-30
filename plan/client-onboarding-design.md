# Client Onboarding Design

Status: draft, waiting for review.

## 1. Decisions

- `client` is the Spring Boot app that starts multiple business domains later: customer, agent, merchant, backoffice.
- The first implemented domain is `customer`.
- `onboarding` is not a standalone bounded context. It is a feature/use case inside the `client` domain.
- `customer` owns the full customer profile and account link.
- `core/account` owns account opening flow and calls `t29`.
- `core` stores only the minimum customer snapshot needed for account/money flows, not the full customer profile.
- `auth` owns Keycloak user, PIN credential, session, trusted device, DPoP verification.
- Onboarding is orchestrated by `client`, not by BFF.
- BFF only forwards requests.
- Mock external providers live in `common/thirdparty` and are exposed through `/common/api/3rd/...`.

## 2. Package Shape

```text
client/src/main/java/vn/com/truongsonbank/client/customer/
  adapter/
    in/
      web/
  application/
  domain/
  infrastructure/
    persistence/
    mfa/
    identity/
    auth/
    core/
  config/
```

Notes:

- This follows the backend hexagonal convention.
- Add ports/interfaces only where there is a real outside dependency:
  - MFA provider
  - identity verification provider
  - Auth service
  - Core account service
- Do not create separate `onboarding/` package unless the feature grows enough to need it.

## 3. Public APIs

Base path:

```text
/client/api/v1/onboarding
```

Endpoints:

```text
POST /start
POST /{onboardingId}/otp/verify
POST /{onboardingId}/identity/verify
POST /{onboardingId}/pin
GET  /{onboardingId}
```

### Start

Request:

```json
{
  "phone": "84901234567"
}
```

Response:

```json
{
  "onboardingId": "ob_...",
  "status": "STARTED",
  "expiresAt": "2026-09-30T10:15:00Z",
  "otpExpiresAt": "2026-09-30T10:05:00Z"
}
```

Behavior:

1. Validate phone.
2. Create onboarding session with 15 minute TTL.
3. Call MFA port to send OTP.
4. MFA adapter is mocked in pass 1.

### Verify OTP

Request:

```json
{
  "otp": "123456"
}
```

Response:

```json
{
  "onboardingId": "ob_...",
  "status": "OTP_VERIFIED"
}
```

Behavior:

1. Require status `STARTED`.
2. Call MFA port to verify OTP.
3. Move status to `OTP_VERIFIED`.

### Verify Identity

Request:

```json
{
  "providerSessionId": "mock-nfc-session"
}
```

Response:

```json
{
  "onboardingId": "ob_...",
  "status": "IDENTITY_VERIFIED",
  "identity": {
    "cccdMasked": "*********123",
    "fullName": "NGUYEN VAN A"
  }
}
```

Behavior:

1. Require status `OTP_VERIFIED`.
2. Call identity verification port.
3. Mock adapter always returns a valid CCCD profile.
4. Store encrypted phone and CCCD plus lookup hashes.
5. Move status to `IDENTITY_VERIFIED`.

### Set PIN / Complete

Request:

```json
{
  "pin": "739204",
  "device": {
    "deviceId": "ios-device-id",
    "deviceName": "Son iPhone",
    "platform": "ios",
    "osVersion": "18.x",
    "appVersion": "0.1.0",
    "publicKey": "base64url-jwk-or-pem"
  },
  "dpop": "<proof>"
}
```

Response:

```json
{
  "onboardingId": "ob_...",
  "status": "COMPLETED",
  "customerId": "cus_...",
  "accountNumber": "100000001",
  "sessionId": "sid_...",
  "trustedDevice": true,
  "expiresInSeconds": 600
}
```

Behavior:

1. Require status `IDENTITY_VERIFIED`, `AUTH_CREATED`, or `ACCOUNT_CREATED`.
2. Validate 6 digit PIN and weak patterns.
3. Call Auth service to create Keycloak user with username = phone.
4. Auth creates PIN credential, internal session, and first trusted device.
5. Customer domain stores customer profile if not already stored.
6. Call Core Account service with a minimum customer snapshot.
7. Core stores/updates its minimal customer record, calls T29, and returns account number.
8. Customer domain stores account link.
9. Move status to `COMPLETED`.

## 4. State Machine

```text
STARTED
  -> OTP_VERIFIED
  -> IDENTITY_VERIFIED
  -> AUTH_CREATED
  -> ACCOUNT_CREATED
  -> COMPLETED
```

Rules:

- Onboarding session expires after 15 minutes.
- OTP expires after 5 minutes.
- `set PIN` is retryable from `IDENTITY_VERIFIED`, `AUTH_CREATED`, or `ACCOUNT_CREATED`.
- No compensation in pass 1.
- If Auth succeeds and Core fails, retry resumes from `AUTH_CREATED`.
- If Core succeeds and final DB update fails, retry resumes from `ACCOUNT_CREATED`.

## 5. Data Model

### `customer_profiles`

Stores the full customer profile owned by the customer domain.

Important columns:

- `id`
- `phone_encrypted`
- `phone_hash`
- `cccd_encrypted`
- `cccd_hash`
- `full_name`
- `dob`
- `status`
- `created_at`
- `updated_at`

Constraints:

- unique `phone_hash`
- unique `cccd_hash`

### `customer_account_links`

Stores account ownership link visible to customer domain.

Important columns:

- `id`
- `client_profile_id`
- `account_number`
- `status`
- `created_at`
- `updated_at`

Constraints:

- unique `account_number`
- index `client_profile_id`

### `customer_onboarding_sessions`

Stores resumable onboarding state.

Important columns:

- `id`
- `phone_encrypted`
- `phone_hash`
- `cccd_encrypted`
- `cccd_hash`
- `full_name`
- `status`
- `auth_subject`
- `session_id`
- `account_number`
- `expires_at`
- `otp_expires_at`
- `created_at`
- `updated_at`
- `failure_code`
- `failure_message`

## 6. Ports

### MFA Port

Used by onboarding for OTP.

Pass 1 adapter:

- call Common 3rd-party mock SMS/MFA API
- mock send OTP
- mock verify OTP
- local/debug may expose fixed or generated OTP through response/config only if enabled

Future adapter:

- call real MFA service or SMS provider

### Identity Verification Port

Used for NFC/eKYC.

Pass 1 adapter:

- call Common 3rd-party mock NFC/eKYC API
- mock success
- return deterministic valid CCCD profile

Future adapter:

- call NFC/eKYC provider
- optionally add liveness result

## 6.1 Common 3rd-Party Mock Module

Common should contain a `thirdparty` module that mocks calls to systems outside TruongSonBank.

Suggested package:

```text
common/src/main/java/vn/com/truongsonbank/common/thirdparty/
  adapter/
    in/
      web/
  application/
  domain/
  infrastructure/
    sms/
    nfc/
    ekyc/
  config/
```

Suggested API path:

```text
/common/api/3rd/sms/otp/send
/common/api/3rd/sms/otp/verify
/common/api/3rd/nfc/cccd/verify
```

Notes:

- Use `thirdparty` in Java package names because `3rd` is not a valid Java package segment.
- Use `/3rd` in API paths if we want the shorter business-facing name.
- This module is only for external-provider mocks and adapters.
- Domain services should still depend on their own ports, not directly on provider implementation classes.
- Later, `common/thirdparty` can be replaced by real provider adapters or split into notification/eKYC services.

### Auth Port

Client calls Auth to:

- create Keycloak user
- set PIN credential
- create internal session
- bind DPoP key
- trust first device

### Core Account Port

Client calls Core to:

- open account by minimum customer identity snapshot
- receive account number

Core should persist only fields required for account ownership and money operations, for example:

- `customer_id`
- `phone_hash`
- `cccd_hash`
- `full_name`
- `status`

## 7. Security

- `phone` and `cccd` are encrypted using the sharedpackage crypto annotation.
- `phone_hash` and `cccd_hash` are used for exact lookup and uniqueness.
- PIN is never stored in client DB.
- DPoP proof and device public key are required in the final step.
- Auth verifies DPoP and owns trusted device state.
- Logs must not include raw PIN, OTP, CCCD, phone, DPoP, session id, or tokens outside local debug mode.

## 8. Failure Handling

- Invalid state returns a business error, not a generic 500.
- Expired onboarding returns `ONBOARDING_EXPIRED`.
- Duplicate phone/CCCD returns a clear conflict error.
- Auth failure stores failure info and keeps status retryable when possible.
- Core/account failure stores failure info and keeps status retryable when possible.
- No rollback/compensation in pass 1.

## 9. Implementation Order

1. Add client domain package structure.
2. Add JPA entities/repositories for profile, account link, onboarding session.
3. Add crypto fields and hash lookup.
4. Add Common 3rd-party mock SMS/MFA APIs.
5. Add Common 3rd-party mock NFC/eKYC APIs.
6. Add customer MFA adapter that calls Common 3rd-party mock.
7. Add customer identity verification adapter that calls Common 3rd-party mock.
8. Add Auth HTTP port.
9. Add Core Account HTTP port.
10. Add onboarding application service.
11. Add onboarding controller.
12. Add mobile app screens for onboarding steps.
13. Add curl tests and one end-to-end local run.

## 10. Non-Goals For Pass 1

- No real SMS gateway.
- No real NFC reader integration in backend.
- No real liveness provider.
- No compensation workflow.
- No event-driven onboarding.
- No admin review/manual approval.

## 11. Self-Review

- No `TODO` or `TBD` placeholders.
- Boundary is aligned with the latest decision: onboarding is a feature inside domain `customer`, not a standalone microservice.
- Account creation remains in `core/account`.
- Full customer profile remains in `customer`; Core stores only a minimum customer snapshot.
- Auth remains owner of Keycloak/PIN/session/trusted device.
- The implementation pass is intentionally synchronous and retryable before adding event-driven complexity.
