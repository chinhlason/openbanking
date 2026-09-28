# Base Framework Feature Checklist

Source: `/Users/sonnvt/Downloads/datamasa.md`

Status: draft.

## P0 - Must Have For First Real Architecture

### 1. API-First / OpenAPI

Need to do:

- Write OpenAPI specs for each public/internal service API.
- Generate server interfaces for `auth`, `bff`, `client`, `core`, `t29`.
- Generate typed clients for service-to-service calls.
- Add API versioning rule: additive changes are minor, breaking changes require major version.

Apply first to:

- `auth`: onboarding, login/session/device/biometric/risk APIs.
- `core`: transfer/payment APIs.
- `t29`: account/balance/transfer mock APIs.
- `client`: customer profile/account ownership APIs.

### 2. Protocol Client For Service-To-Service Calls

Need to do:

- Use declarative clients instead of hand-written HTTP calls.
- Configure timeout, retry, circuit breaker per downstream.
- Propagate correlation id.

Initial service calls:

- `bff -> auth`
- `bff -> client`
- `bff -> core`
- `core -> t29`
- `auth -> Keycloak`

### 3. Logging And Sensitive Data Masking

Need to do:

- Enable structured logging for all services.
- Mask `pin`, `otp`, `token`, `refreshToken`, `authorization`, `sessionId`, `cccd`, `phone`, `cardNumber`, `accountNumber`.
- Include `traceId`, `spanId`, `requestId`, `sessionDisplayId` where available.
- Do not log request body for auth/token/NFC/payment endpoints by default.

### 4. Smart Cache

Need to do:

- Use cache for internal session lookup.
- Cache session hot state:
  - `sessionId -> userId, deviceId, dpop_jkt, idleExpiresAt, absoluteExpiresAt, status`
- Cache DPoP replay `jti` for sensitive APIs.
- Cache feature/config metadata later if needed.

Keep it simple:

- L2 Redis is enough for multi-instance.
- Skip L1 cache unless performance requires it.

### 5. Data Crypto

Need to do:

- Encrypt sensitive DB fields:
  - phone
  - cccd
  - biometric public key if required by policy
  - refresh token reference if stored
  - device push token hash/raw token handling
- Use deterministic encryption only for fields that need exact lookup, such as phone/cccd.
- Use probabilistic encryption for audit payloads or sensitive metadata not searched by equality.

### 6. Advanced Audit

Need to do:

- Audit auth events:
  - onboarding started/completed
  - OTP verified/failed
  - login success/failed
  - session exchange
  - logout
  - device trust/revoke
  - biometric enable/disable
  - PIN reset/recovery
- Audit money events:
  - transfer/payment created
  - transfer/payment success/failed
  - balance mutation request to T29
- Mask sensitive fields in audit payload.

## P1 - Needed For UAT / Production-Like

### 7. Message Queue + Outbox

Need to do when async/audit/event delivery matters:

- Publish domain events:
  - `AuthLoginSucceeded`
  - `DeviceTrusted`
  - `PaymentSucceeded`
  - `TransferSucceeded`
- Use outbox for important events so DB commit and event publish do not diverge.
- Enable DLQ and retry.

Skip in first pass if all flows are synchronous.

### 8. Distributed Lock

Need to do only around critical concurrency points:

- Device replacement approval: avoid two approvals racing.
- PIN reset/recovery: avoid concurrent reset attempts.
- Transfer/payment idempotency: prefer DB unique key first; use distributed lock only if DB constraint is not enough.

Default rule:

- Use DB transaction/unique constraint first.
- Add distributed lock only where real race remains.

### 9. Sequence Generator

Need to do:

- Generate business ids:
  - transaction id
  - payment id
  - audit event id
  - onboarding session id if not UUID
- For T29 account number, keep simple sequence/mock until real account format is required.

### 10. Feature Flags

Need to do for controlled rollout:

- `auth.biometric-login.enabled`
- `auth.dpop-sensitive-api.enabled`
- `auth.captcha.enabled`
- `payment.transfer.enabled`
- `payment.payment.enabled`
- `core.t29-mock.enabled`

Skip coordinator integration if first milestone is local only.

### 11. Service Discovery

Need to do when services run separately:

- Register `auth`, `bff`, `client`, `core`, `t29`.
- Use logical service names, no hard-coded URLs.
- Disable discovery for local standalone mode if needed.

## P2 - Later / Only If Requirement Appears

### 12. Batch Processing

Use later for:

- reconciliation jobs
- expired onboarding/session cleanup
- expired trusted device cleanup
- daily audit export
- failed payment retry/reconciliation

Not needed for first synchronous flow.

### 13. Distributed Job

Use later if jobs run on multiple pods:

- session cleanup
- outbox relay if not handled by MQ SDK
- reconciliation
- report generation

Not needed before batch jobs exist.

### 14. Data Sharding

Skip for now.

Use only when data volume requires sharding, likely for:

- transactions
- audit logs
- auth events

Do not design sharding in the first DB version.

### 15. Data Specification

Use later for admin/search screens:

- customer search
- transaction search
- audit search
- session/device admin filtering

Skip until portal/admin query requirements are clear.

## Feature Work Breakdown By Service

### `auth`

- OpenAPI spec for onboarding/session/device/biometric/risk.
- Keycloak custom grant integration.
- Session cache.
- Device approval flow.
- Audit events.
- Crypto for phone/cccd/device-sensitive fields.
- Logging masking.
- Feature flags for biometric/captcha/DPoP.

### `bff`

- Session validation with Auth.
- DPoP enforcement for sensitive APIs.
- Typed clients to Auth/Client/Core.
- Request correlation/logging.

### `client`

- Customer profile by phone/cccd.
- Customer-account mapping.
- Crypto for phone/cccd.
- Audit customer changes.

### `core`

- Transfer/payment orchestration.
- Idempotency by request id.
- Typed client to T29.
- Audit money transaction changes.
- Optional outbox for success/failure events.

### `t29`

- Account by CCCD.
- Balance.
- Debit/credit/transfer.
- Later: DB persistence and audit.

## Do Not Build Yet

- Full event-driven architecture.
- Multi-device trust model.
- Data sharding.
- Batch/reconciliation engine.
- Generic admin search framework.
- Custom cache abstraction beyond session/replay cache.

