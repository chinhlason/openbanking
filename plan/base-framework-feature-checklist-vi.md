# Checklist Tinh Nang Tu Base Framework

Nguon: `/Users/sonnvt/Downloads/datamasa.md`

Trang thai: draft.

## P0 - Bat Buoc Cho Kien Truc Dau Tien

### 1. API-First / OpenAPI

Can lam:

- Viet OpenAPI spec cho tung API public/internal.
- Generate server interface cho `auth`, `bff`, `client`, `core`, `t29`.
- Generate typed client cho cac service goi nhau.
- Dat rule version API: them field/endpoint thi tang minor, breaking change thi tang major.

Lam truoc cho:

- `auth`: onboarding, login, session, device, biometric, risk APIs.
- `core`: transfer/payment APIs.
- `t29`: account, balance, transfer mock APIs.
- `client`: customer profile, account ownership APIs.

### 2. Protocol Client Cho Goi Service-To-Service

Can lam:

- Dung declarative client thay vi tu viet HTTP client thu cong.
- Cau hinh timeout, retry, circuit breaker theo tung downstream.
- Propagate correlation id de trace end-to-end.

Cac luong goi ban dau:

- `bff -> auth`
- `bff -> client`
- `bff -> core`
- `core -> t29`
- `auth -> Keycloak`

### 3. Logging Va Mask Du Lieu Nhay Cam

Can lam:

- Bat structured logging cho tat ca service.
- Mask `pin`, `otp`, `token`, `refreshToken`, `authorization`, `sessionId`, `cccd`, `phone`, `cardNumber`, `accountNumber`.
- Dua `traceId`, `spanId`, `requestId`, `sessionDisplayId` vao log khi co.
- Mac dinh khong log request body cho auth/token/NFC/payment endpoints.

### 4. Smart Cache

Can lam:

- Dung cache cho tra cuu session noi bo.
- Cache hot state cua session:
  - `sessionId -> userId, deviceId, dpop_jkt, idleExpiresAt, absoluteExpiresAt, status`
- Cache `jti` cua DPoP proof de chong replay cho API nhay cam.
- Sau nay co the cache feature/config metadata neu can.

Lam don gian:

- L2 Redis la du neu chay multi-instance.
- Chua can L1 cache neu chua co van de performance.

### 5. Data Crypto

Can lam:

- Ma hoa cac field nhay cam trong DB:
  - phone
  - cccd
  - biometric public key neu policy yeu cau
  - refresh token reference neu co luu
  - push token/device token neu can luu raw
- Dung deterministic encryption chi cho field can exact lookup, vi du phone/cccd.
- Dung probabilistic encryption cho audit payload hoac metadata nhay cam khong can search equality.

### 6. Advanced Audit

Can lam:

- Audit cac su kien auth:
  - bat dau/hoan tat onboarding
  - OTP verified/failed
  - login success/failed
  - session exchange
  - logout
  - trust/revoke device
  - enable/disable biometric
  - reset/recovery PIN
- Audit cac su kien tien:
  - tao transfer/payment
  - transfer/payment success/failed
  - yeu cau thay doi balance xuong T29
- Mask field nhay cam trong audit payload.

## P1 - Can Cho UAT / Production-Like

### 7. Message Queue + Outbox

Can lam khi can async/audit/event delivery chac chan:

- Publish domain events:
  - `AuthLoginSucceeded`
  - `DeviceTrusted`
  - `PaymentSucceeded`
  - `TransferSucceeded`
- Dung outbox cho event quan trong de DB commit va publish event khong bi lech nhau.
- Bat DLQ va retry.

Bo qua o pass dau neu tat ca flow dang synchronous.

### 8. Distributed Lock

Chi dung quanh cac diem concurrency that su nguy hiem:

- Approve replace device: tranh 2 approval chay cung luc.
- Reset/recovery PIN: tranh reset song song.
- Idempotency transfer/payment: uu tien DB unique key truoc; chi dung distributed lock neu DB constraint chua du.

Rule mac dinh:

- Dung DB transaction/unique constraint truoc.
- Chi them distributed lock khi van con race that.

### 9. Sequence Generator

Can lam:

- Generate business ids:
  - transaction id
  - payment id
  - audit event id
  - onboarding session id neu khong dung UUID
- Account number cua T29 tam giu sequence/mock don gian den khi can format that.

### 10. Feature Flags

Can lam de rollout co kiem soat:

- `auth.biometric-login.enabled`
- `auth.dpop-sensitive-api.enabled`
- `auth.captcha.enabled`
- `payment.transfer.enabled`
- `payment.payment.enabled`
- `core.t29-mock.enabled`

Bo qua coordinator integration neu milestone dau chi chay local.

### 11. Service Discovery

Can lam khi cac service chay rieng:

- Register `auth`, `bff`, `client`, `core`, `t29`.
- Goi nhau bang logical service name, khong hard-code URL.
- Cho phep tat discovery khi local standalone.

## P2 - De Sau / Chi Lam Khi Co Nhu Cau

### 12. Batch Processing

Dung sau cho:

- reconciliation jobs
- cleanup onboarding/session het han
- cleanup trusted device het han
- export audit hang ngay
- retry/reconciliation payment fail

Chua can cho flow synchronous dau tien.

### 13. Distributed Job

Dung sau neu job chay tren nhieu pod:

- cleanup session
- outbox relay neu MQ SDK chua xu ly
- reconciliation
- report generation

Chua can truoc khi co batch job that.

### 14. Data Sharding

Bo qua hien tai.

Chi dung khi volume data bat buoc phai shard, co the la:

- transactions
- audit logs
- auth events

Khong thiet ke sharding trong DB version dau.

### 15. Data Specification

Dung sau cho man hinh admin/search:

- search customer
- search transaction
- search audit
- filter session/device trong admin

Bo qua den khi portal/admin query requirement ro rang.

## Breakdown Theo Service

### `auth`

- OpenAPI spec cho onboarding/session/device/biometric/risk.
- Keycloak custom grant integration.
- Session cache.
- Device approval flow.
- Audit events.
- Crypto cho phone/cccd/device-sensitive fields.
- Logging masking.
- Feature flags cho biometric/captcha/DPoP.

### `bff`

- Validate session voi Auth.
- Enforce DPoP cho API nhay cam.
- Typed clients toi Auth/Client/Core.
- Request correlation/logging.

### `client`

- Customer profile theo phone/cccd.
- Mapping customer-account.
- Crypto cho phone/cccd.
- Audit thay doi customer.

### `core`

- Orchestrate transfer/payment.
- Idempotency bang request id.
- Typed client toi T29.
- Audit thay doi money transaction.
- Optional outbox cho success/failure events.

### `t29`

- Account theo CCCD.
- Balance.
- Debit/credit/transfer.
- Sau nay: DB persistence va audit.

## Chua Lam Luc Nay

- Full event-driven architecture.
- Multi-device trust model.
- Data sharding.
- Batch/reconciliation engine.
- Generic admin search framework.
- Custom cache abstraction ngoai session/replay cache.

