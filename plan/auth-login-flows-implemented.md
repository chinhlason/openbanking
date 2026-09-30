# Auth Login Flows Implemented

## 1. PIN Login

Use case: user enters `username + PIN` on native app.

Flow:

1. App calls Keycloak token endpoint:

```http
POST /realms/truongsonbank/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded
DPoP: <proof signed by device key>

grant_type=password
client_id=truongsonbank-mobile
username=84901234567
password=739204
```

2. Keycloak validates credential and returns `access_token`.
3. App sends token to Auth Service:

```http
POST /auth/api/v1/token/exchange
Authorization: DPoP <keycloak_access_token>
DPoP: <proof signed by device key, includes nonce + ath>
Content-Type: application/json

{
  "device": {
    "deviceId": "...",
    "deviceName": "...",
    "platform": "ios",
    "osVersion": "...",
    "appVersion": "...",
    "publicKey": "<dpop-public-key>"
  }
}
```

4. Auth validates Keycloak token, validates DPoP proof, consumes one-time nonce, checks `ath`, calculates `jkt`, upserts device, creates internal session, stores session in Redis and DB.
5. App receives internal `sessionId`.

Result:

- App uses `X-Session-Id` for internal APIs.
- Sensitive Auth APIs require `DPoP` proof signed by the same device private key.
- Internal session is bound to `dpop_jkt`, so replaying `sessionId` from another device key is rejected.

Before `/token/exchange`, app obtains a one-time nonce:

```http
POST /auth/api/v1/dpop/nonce
```

## 2. Trust Device

Use case: user confirms current device is trusted after PIN login.

Flow:

```http
POST /auth/api/v1/devices/{deviceId}/trust
X-Session-Id: <sessionId>
DPoP: <proof>
Content-Type: application/json

{
  "pin": "739204"
}
```

Auth checks:

- session is valid
- DPoP proof is valid and not replayed
- path/method/iat/jti are valid
- device belongs to the current session
- PIN is still valid via Keycloak password grant

Result:

- `auth_device.trusted = true`
- current session is updated to `trustedDevice = true`

## 3. Biometric Enable

Use case: user has trusted device and enables FaceID/TouchID login.

Flow:

1. App calls Auth to request a short-lived enable challenge for the current trusted device:

```http
POST /auth/api/v1/devices/{deviceId}/biometric/enable/challenge
X-Session-Id: <sessionId>
DPoP: <proof signed by trusted device key>
```

Auth checks:

- session is valid
- session device equals `{deviceId}`
- device is trusted and belongs to the same subject
- DPoP proof matches the session `dpopJkt`
- stored trusted device public key thumbprint equals session `dpopJkt`

Response:

```json
{
  "challengeId": "bio_enable_xxx",
  "nonce": "yyy",
  "expiresInSeconds": 60,
  "payload": "enable.bio_enable_xxx.yyy.84901234567.<deviceId>"
}
```

2. App verifies FaceID/TouchID locally, creates/replaces a biometric-protected EC P-256 key pair in iOS Keychain/Secure Enclave policy, then signs the enable payload with the new biometric private key.
3. App sends public key plus signed enable challenge to Auth:

```http
POST /auth/api/v1/devices/{deviceId}/biometric/enable
X-Session-Id: <sessionId>
DPoP: <proof signed by trusted device key>
Content-Type: application/json

{
  "publicKey": "<biometric-public-key>",
  "challengeId": "bio_enable_xxx",
  "nonce": "yyy",
  "signature": "<signature over enable payload>"
}
```

Auth checks:

- session is valid
- device is trusted and belongs to current session
- DPoP proof is valid and bound to the trusted device public key
- enable challenge exists, is single-use, not expired, and belongs to the same `sessionId`, `username`, `deviceId`, and `dpopJkt`
- signature verifies against the submitted biometric public key

Result:

- `auth_device.biometric_enabled = true`
- `auth_device.biometric_public_key = <publicKey>`

## 4. Biometric Passwordless Login

Use case: user logs in with FaceID/TouchID, without sending PIN/password.

Flow:

1. App requests challenge:

```http
POST /auth/api/v1/biometric/challenge
Content-Type: application/json

{
  "username": "84901234567",
  "deviceId": "..."
}
```

2. Auth checks:

- device exists
- device belongs to username
- device is trusted
- biometric is enabled
- biometric public key exists

3. Auth stores a single-use Redis challenge with TTL 60s and returns:

```json
{
  "challengeId": "bio_xxx",
  "nonce": "random",
  "expiresInSeconds": 60,
  "payload": "bio_xxx.random.84901234567.device-id"
}
```

4. App prompts FaceID/TouchID.
5. If biometric succeeds, app signs `payload` with biometric private key.
6. App calls Keycloak custom grant:

```http
POST /realms/truongsonbank/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded
DPoP: <proof signed by device key>

grant_type=biometric
client_id=truongsonbank-mobile
username=84901234567
device_id=<deviceId>
challenge_id=<challengeId>
nonce=<nonce>
signature=<base64-der-signature>
```

7. Keycloak biometric grant calls Auth internal verify endpoint:

```http
POST /auth/api/v1/internal/biometric/verify
X-Keycloak-Biometric-Secret: <shared-secret>
```

8. Auth verifies:

- challenge exists
- challenge matches username/device/nonce
- challenge is single-use
- device is still trusted
- biometric is still enabled
- signature matches stored biometric public key

9. Keycloak returns `access_token`.
10. App calls `/token/exchange` exactly like PIN login, with one-time nonce and `ath`.
11. Auth returns internal `sessionId`.

Result:

- No face/fingerprint data leaves the device.
- PIN is not sent during biometric login.
- Server trusts a signed challenge from a trusted device credential.

## 5. Keep Alive

Use case: app extends active internal session.

```http
POST /auth/api/v1/sessions/keep-alive
X-Session-Id: <sessionId>
DPoP: <proof>
```

Result:

- Auth verifies DPoP.
- Auth extends Redis session TTL and updates DB `expiresAt`.

## 6. Implemented Components

- Auth Service:
  - `/v1/login/pin`
  - `/v1/dpop/nonce`
  - `/v1/token/exchange`
  - `/v1/devices/{deviceId}/trust`
  - `/v1/devices/{deviceId}/biometric/enable`
  - `/v1/devices/{deviceId}/biometric/disable`
  - `/v1/biometric/challenge`
  - `/v1/internal/biometric/verify`

- Keycloak:
  - custom OAuth grant `grant_type=biometric`
  - provider module: `keycloak-biometric-provider`

- iOS app:
  - PIN login
  - DPoP device key
  - trusted device
  - biometric key pair
  - biometric challenge signing
  - biometric passwordless login

## 7. Current Limits

- Risk engine/captcha/step-up is not implemented yet.
- Keycloak DPoP-bound token is enabled for `truongsonbank-mobile`; Auth Service also enforces DPoP at `/token/exchange`, checks token `cnf.jkt`, and binds internal session to `jkt`.
- Server-side `/login/pin` is disabled to avoid a weaker session creation path; native app must call Keycloak directly, then exchange token with Auth.
- Biometric grant uses Keycloak internal OAuth grant SPI, which Keycloak marks as internal and version-sensitive.
