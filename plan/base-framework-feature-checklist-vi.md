# Feature Checklist From Base Framework

Source: `/Users/sonnvt/Downloads/datamasa.md`

Status: draft.

## P0 - Required For The First Architecture

### 1. API-First / OpenAPI

To do:

- Write OpenAPI specs for each public/internal API.
- Generate server interfaces for `auth`, `bff`, `client`, `core`, `t29`.
- Generate typed clients for service-to-service calls.
- Set the API versioning rule: adding a field/endpoint bumps minor, breaking changes bump major.

Do first for:

- `auth`: onboarding, login, session, device, biometric, risk APIs.
- `core`: transfer/payment APIs.
- `t29`: account, balance, transfer mock APIs.
- `client`: customer profile, account ownership APIs.

### 2. Protocol Client For Service-To-Service Calls

To do:

- Use a declarative client instead of manually writing HTTP clients.
- Configure timeout, retry, and circuit breaker per downstream.
- Propagate correlation id for end-to-end tracing.

Initial call flows:

- `bff -> auth`
- `bff -> client`
- `bff -> core`
- `core -> t29`
- `auth -> Keycloak`

### 3. Logging And Sensitive Data Masking

To do:

- Enable structured logging for all services.
- Mask `pin`, `otp`, `token`, `refreshToken`, `authorization`, `sessionId`, `cccd`, `phone`, `cardNumber`, `accountNumber`.
- Put `traceId`, `spanId`, `requestId`, `sessionDisplayId` into logs when available.
- By default, do not log request bodies for auth/token/NFC/payment endpoints.

### 4. Smart Cache

To do:

- Use cache for internal session lookup.
- Cache hot session state:
  - `sessionId -> userId, deviceId, dpop_jkt, idleExpiresAt, absoluteExpiresAt, status`
- Cache DPoP proof `jti` to prevent replay for sensitive APIs.
- Later, cache feature/config metadata if needed.

Keep it simple:

- L2 Redis is enough for multi-instance runs.
- L1 cache is not needed yet unless there is a performance issue.

### 5. Data Crypto

To do:

- Encrypt sensitive fields in the DB:
  - phone
  - cccd
  - biometric public key if policy requires it
  - refresh token reference if stored
  - push token/device token if raw values need to be stored
- Use deterministic encryption only for fields that need exact lookup, for example phone/cccd.
- Use probabilistic encryption for audit payloads or sensitive metadata that does not need equality search.

### 6. Advanced Audit

To do:

- Audit auth events:
  - start/complete onboarding
  - OTP verified/failed
  - login success/failed
  - session exchange
  - logout
  - trust/revoke device
  - enable/disable biometric
  - reset/recovery PIN
- Audit money events:
  - create transfer/payment
  - transfer/payment success/failed
  - request balance change down to T29
- Mask sensitive fields in audit payloads.

## P1 - Needed For UAT / Production-Like

### 7. Message Queue + Outbox

To do when async/audit/event delivery needs to be reliable:

- Publish domain events:
  - `AuthLoginSucceeded`
  - `DeviceTrusted`
  - `PaymentSucceeded`
  - `TransferSucceeded`
- Use outbox for important events so DB commit and event publish do not diverge.
- Enable DLQ and retry.

Skip in the first pass if all flows are synchronous.

### 8. Distributed Lock

Use only around genuinely dangerous concurrency points:

- Approve replace device: prevent 2 approvals from running at the same time.
- Reset/recovery PIN: prevent parallel resets.
- Transfer/payment idempotency: prefer DB unique key first; use distributed lock only if the DB constraint is not enough.

Default rule:

- Use DB transaction/unique constraint first.
- Add distributed lock only when a real race remains.

### 9. Sequence Generator

To do:

- Generate business ids:
  - transaction id
  - payment id
  - audit event id
  - onboarding session id if UUID is not used
- For T29 account numbers, keep a simple sequence/mock until the real format is needed.

### 10. Feature Flags

To do for controlled rollout:

- `auth.biometric-login.enabled`
- `auth.dpop-sensitive-api.enabled`
- `auth.captcha.enabled`
- `payment.transfer.enabled`
- `payment.payment.enabled`
- `core.t29-mock.enabled`

Skip coordinator integration if the first milestone only runs locally.

### 11. Service Discovery

To do when services run separately:

- Register `auth`, `bff`, `client`, `core`, `t29`.
- Call each other by logical service name, not hard-coded URL.
- Allow discovery to be turned off for local standalone runs.

## P2 - Later / Only When Needed

### 12. Batch Processing

Use later for:

- reconciliation jobs
- cleanup expired onboarding/sessions
- cleanup expired trusted devices
- daily audit export
- retry/reconciliation for failed payments

Not needed for the first synchronous flow.

### 13. Distributed Job

Use later if jobs run on multiple pods:

- cleanup session
- outbox relay if the MQ SDK does not handle it
- reconciliation
- report generation

Not needed before there is a real batch job.

### 14. Data Sharding

Skip for now.

Use only when data volume requires sharding, possibly for:

- transactions
- audit logs
- auth events

Do not design sharding into the first DB version.

### 15. Data Specification

Use later for admin/search screens:

- search customer
- search transaction
- search audit
- filter session/device in admin

Skip until portal/admin query requirements are clear.

## Breakdown By Service

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

- Validate session with Auth.
- Enforce DPoP for sensitive APIs.
- Typed clients to Auth/Client/Core.
- Request correlation/logging.

### `client`

- Customer profile by phone/cccd.
- Customer-account mapping.
- Crypto for phone/cccd.
- Audit customer changes.

### `core`

- Orchestrate transfer/payment.
- Idempotency by request id.
- Typed client to T29.
- Audit money transaction changes.
- Optional outbox for success/failure events.

### `t29`

- Account by CCCD.
- Balance.
- Debit/credit/transfer.
- Later: DB persistence and audit.

## Not Doing Now

- Full event-driven architecture.
- Multi-device trust model.
- Data sharding.
- Batch/reconciliation engine.
- Generic admin search framework.
- Custom cache abstraction beyond session/replay cache.
