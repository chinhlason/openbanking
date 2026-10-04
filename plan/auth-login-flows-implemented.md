# Auth Login Flows Implemented

## 1. PIN Login

Use case: user enters `username + PIN` on native app.

Flow:

1. App requests an Auth login challenge through BFF/Auth:

```http
POST /bff/api/auth/v1/login/init
Content-Type: application/json

{
  "loginType": "PIN",
  "username": "84901234567",
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

2. Auth stores `challengeId + nonce + dpopJkt + username + deviceId` in Redis.
3. App submits PIN plus DPoP proof:

```http
POST /bff/api/auth/v1/login/verify
DPoP: <proof signed by device key>
Content-Type: application/json

{
  "loginType": "PIN",
  "username": "84901234567",
  "pin": "739204",
  "challengeId": "login_xxx",
  "nonce": "yyy",
  "keycloakDpopProof": "<proof for Keycloak token endpoint>",
  "device": {
    "deviceId": "...",
    "publicKey": "<dpop-public-key>"
  }
}
```

4. Auth validates DPoP, consumes challenge, calls Keycloak custom grant `grant_type=tsb-pin`, validates Keycloak token, creates internal session, stores session in Redis and DB.
5. App receives internal `sessionId`.

Result:

- App uses `X-Session-Id` for internal APIs.
- Sensitive Auth APIs require `DPoP` proof signed by the same device private key.
- Internal session is bound to `dpop_jkt`, so replaying `sessionId` from another device key is rejected.

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
- PIN is still valid via Keycloak custom grant `grant_type=tsb-pin`

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
- Keycloak credential store saves one credential row:
  - table: `keycloakdb.CREDENTIAL`
  - `TYPE = tsb-biometric`
  - `USER_LABEL = biometric:{deviceId}`
  - `CREDENTIAL_DATA` contains `deviceId` and biometric public key
- Auth DB does not store the biometric public key.

## 4. Biometric Passwordless Login

Use case: user logs in with FaceID/TouchID, without sending PIN/password.

Flow:

1. App requests the unified login challenge:

```http
POST /bff/api/auth/v1/login/init
Content-Type: application/json

{
  "loginType": "BIOMETRIC",
  "username": "84901234567",
  "device": {
    "deviceId": "...",
    "publicKey": "<dpop-public-key>"
  }
}
```

2. Auth checks:

- device exists
- device belongs to username
- device is trusted
- biometric is enabled
- Keycloak has a `tsb-biometric` credential for the device
- DPoP public key belongs to the trusted device

3. Auth stores a single-use Redis challenge with TTL 60s and returns:

```json
{
  "challengeId": "login_xxx",
  "nonce": "random",
  "expiresInSeconds": 60,
  "payload": "login_xxx.random.84901234567.device-id"
}
```

4. App prompts FaceID/TouchID.
5. If biometric succeeds, app signs `payload` with biometric private key.
6. App sends the signed challenge to Auth through BFF:

```http
POST /bff/api/auth/v1/login/verify
DPoP: <proof signed by device key>
Content-Type: application/json

{
  "loginType": "BIOMETRIC",
  "username": "84901234567",
  "challengeId": "<challengeId>",
  "nonce": "<nonce>",
  "signature": "<base64-der-signature>",
  "keycloakDpopProof": "<proof for Keycloak token endpoint>",
  "device": {
    "deviceId": "...",
    "publicKey": "<dpop-public-key>"
  }
}
```

7. Auth validates the banking login context before calling Keycloak:

- DPoP proof matches the challenge `dpopJkt`
- challenge exists, matches username/device/nonce/loginType, and is single-use
- device is trusted
- biometric is enabled for that device

8. Auth calls Keycloak custom grant `grant_type=biometric` and forwards the signed challenge.
9. Keycloak biometric grant verifies the signature using the public key stored in the Keycloak credential row with `TYPE = tsb-biometric`.
10. Keycloak returns `access_token` to Auth.
11. Auth validates token, creates internal session, and returns `sessionId` to app.

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

## 6. Passkey Passwordless Login

Current implementation is passkey-like for native-flow testing: device creates a Secure Enclave EC key, Auth issues a single-use challenge, Keycloak stores the public key as a credential row and verifies the signed challenge.

Keycloak storage:

- table: `keycloakdb.CREDENTIAL`
- `TYPE = tsb-passkey`
- `USER_LABEL = passkey:{deviceId}`
- `CREDENTIAL_DATA` contains `deviceId` and public key

Endpoints:

- `POST /auth/api/v1/devices/{deviceId}/passkey/enable/challenge`
- `POST /auth/api/v1/devices/{deviceId}/passkey/enable`
- `POST /auth/api/v1/login/init` with `loginType=PASSKEY`
- `POST /auth/api/v1/login/verify` with `loginType=PASSKEY`
- Keycloak token grant: `grant_type=passkey`

Passkey login follows the same ownership split as biometric login: Auth verifies DPoP, challenge, trusted device, and passkey-enabled state; Keycloak verifies the passkey credential signature and mints the token.

Auth DB stores only the device state flag:

- `auth_device.passkey_enabled`

## 7. Implemented Components

- Auth Service:
  - `/v1/dpop/nonce`
  - `/v1/token/exchange`
  - `/v1/login/init`
  - `/v1/login/verify`
  - `/v1/devices/{deviceId}/trust`
  - `/v1/devices/{deviceId}/biometric/enable/challenge`
  - `/v1/devices/{deviceId}/biometric/enable`
  - `/v1/devices/{deviceId}/biometric/disable`
  - `/v1/devices/{deviceId}/passkey/enable/challenge`
  - `/v1/devices/{deviceId}/passkey/enable`
  - `/v1/devices/{deviceId}/passkey/disable`

- Keycloak:
  - custom OAuth grant `grant_type=tsb-pin`
  - custom OAuth grant `grant_type=biometric`
  - custom OAuth grant `grant_type=passkey`
  - credential types `tsb-pin`, `tsb-biometric`, `tsb-passkey`
  - provider module: `keycloak-biometric-provider`

- iOS app:
- PIN login
  - DPoP device key
  - trusted device
  - biometric key pair
  - biometric challenge signing
  - biometric passwordless login
  - passkey-like key pair
  - passkey challenge signing
  - passkey passwordless login

## 8. Current Limits

- Risk engine/captcha/step-up is not implemented yet.
- Auth Service enforces DPoP on challenge verify endpoints and binds internal session to `jkt`.
- `/token/exchange` remains for portal/legacy integration, but mobile PIN/biometric login now uses Auth challenge endpoints.
- Biometric/passkey grants use Keycloak internal OAuth grant SPI, which Keycloak marks as internal and version-sensitive.
- Keycloak biometric/passkey grants do not call Auth back; Auth verifies login context before requesting the Keycloak token.
- Passkey flow does not yet implement full WebAuthn attestation/assertion verification.
