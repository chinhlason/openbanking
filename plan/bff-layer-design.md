# BFF Layer Design

## Goal

Design the BFF layer for both mobile and portal while keeping BFF thin:

- authenticate requests
- build trusted auth context
- strip and sign internal auth headers
- forward typed HTTP calls to downstream services
- avoid business logic, aggregation, persistence, and domain authorization

## Public API Boundary

BFF exposes two channel prefixes:

```text
/bff/api/mobile/**
/bff/api/portal/**
```

Service config:

```yaml
server:
  port: 8080
  servlet:
    context-path: /bff/api
```

Controllers use channel paths:

```text
/mobile/**
/portal/**
```

## BFF Responsibilities

BFF does:

- authenticate mobile requests using internal session id
- authenticate portal requests using Keycloak access token
- verify DPoP for mobile sensitive APIs
- remove client-supplied internal headers
- create `X-Auth-*` and `X-DPoP-*` headers
- resolve entitlement snapshot for the authenticated subject
- enrich and sign entitlement context for downstream services
- sign auth headers with HMAC
- forward request to downstream typed HTTP clients
- propagate OpenTelemetry trace context
- forward raw downstream response wrapper
- return BFF-local errors with `BFF_*` codes

BFF does not:

- hold a database
- aggregate home/dashboard responses
- make business decisions
- authorize domain permissions
- calculate package/group entitlement graph
- own account, transfer, payment, config, or customer logic
- rewrite downstream response wrappers

## Authentication

### Mobile

Mobile sends:

```text
X-Session-Id: <internal-session-id>
DPoP: <proof-jwt>   # sensitive APIs only
```

BFF reads session from Redis, written by Auth Service:

```text
auth:session:{sessionId}
```

Expected session fields:

```json
{
  "sessionId": "sid",
  "userId": "u123",
  "customerId": "c123",
  "channel": "MOBILE",
  "roles": ["CUSTOMER"],
  "scopes": ["transfer:write", "account:read"],
  "deviceId": "dev123",
  "trustedDevice": true,
  "dpopJkt": "thumbprint",
  "idleExpiresAt": "2026-09-29T10:00:00Z",
  "absoluteExpiresAt": "2026-09-30T10:00:00Z",
  "status": "ACTIVE"
}
```

### Entitlement enrichment

After session/token validation, BFF reads the session projection from Redis L2. The projection contains subject, roles and service packages. BFF then looks up role/package metadata in its L1 cache, merges the concrete operations, and includes the resulting allow/deny lists, version and expiry in the signed internal auth context. Client-supplied `X-Auth-Entitlements`, deny, version and reference headers are stripped first.

The domain remains the final authorization owner through sharedpackage `@RequireEntitlement`; BFF only transports a verified, signed context. See [entitlement-enrichment-implementation-plan.md](entitlement-enrichment-implementation-plan.md).

BFF validates:

- session exists
- status is `ACTIVE`
- channel is `MOBILE`
- idle and absolute expiry are valid

### Portal

Portal logs in through Keycloak Web UI and sends:

```text
Authorization: Bearer <keycloak-access-token>
```

BFF validates Keycloak token offline using JWKS:

- signature
- issuer
- expiry
- not-before
- audience/client id when available

Because Keycloak does not yet own this system's authorization model, BFF uses the token only as identity proof. BFF calls Auth/Internal User Service to map Keycloak identity to internal principal:

```text
keycloak sub / username / email -> internal principal + roles/scopes
```

BFF caches portal principal briefly:

```text
bff:portal:principal:{issuer}:{sub}
```

Suggested TTL:

```text
30s - 120s
```

## DPoP

Mobile sensitive APIs require DPoP in phase one.

Sensitive routes:

```text
POST /mobile/transfers/**
POST /mobile/payments/**
/mobile/profile/security/**
/mobile/devices/trust/**
```

BFF verifies:

- proof JWT signature using public JWK from proof header
- JWK thumbprint equals session `dpopJkt`
- `htm` equals HTTP method
- `htu` equals normalized request URL/path
- `iat` is fresh, for example within 60 seconds
- `jti` has not been used before

BFF forwards DPoP verification context, not the original proof as authority:

```text
X-DPoP-Verified: true
X-DPoP-Jkt: <thumbprint>
X-DPoP-Jti: <proof-jti>
```

## Internal Auth Headers

BFF strips client-supplied internal headers before forwarding:

```text
X-Auth-*
X-DPoP-Verified
X-DPoP-Jkt
X-DPoP-Jti
X-Internal-*
```

BFF may accept from clients:

```text
X-Session-Id
Authorization
DPoP
traceparent
tracestate
Idempotency-Key
X-Request-Id
```

BFF generates:

```text
X-Auth-Channel: MOBILE|PORTAL
X-Auth-Principal-Id: <internal principal id>
X-Auth-User-Id: <user id>
X-Auth-Customer-Id: <customer id>
X-Auth-Session-Id: <session id>
X-Auth-Device-Id: <device id>
X-Auth-Trusted-Device: true|false
X-Auth-Roles: ROLE_A,ROLE_B
X-Auth-Scopes: scope:a,scope:b
X-DPoP-Verified: true|false
X-DPoP-Jkt: <thumbprint>
X-DPoP-Jti: <jti>
X-Auth-Timestamp: <epoch millis or ISO instant>
X-Auth-Nonce: <random nonce>
X-Auth-Signature: <hmac>
```

## HMAC Signing

BFF signs a canonical representation of selected request data and auth headers.

Canonical input:

```text
method
path
timestamp
nonce
x-auth-channel
x-auth-principal-id
x-auth-user-id
x-auth-customer-id
x-auth-session-id
x-auth-device-id
x-auth-trusted-device
x-auth-roles
x-auth-scopes
x-dpop-verified
x-dpop-jkt
x-dpop-jti
```

Downstream verifies:

- signature is valid
- timestamp is within max skew
- nonce has not been used
- required `X-Auth-*` headers are present

Nonce replay store:

```text
Redis first
local Caffeine fallback for dev/local
TTL = max-skew
key = tsb:internal-auth:nonce:{issuer}:{nonce}
```

## Authorization Location

BFF does not authorize roles/scopes.

Domain services authorize with annotations from sharedpackage:

```java
@RequireRole(value = {"CONFIG_ADMIN", "SUPER_ADMIN"}, mode = MatchMode.ANY)
@RequireScope(value = {"config:write", "config:approve"}, mode = MatchMode.ALL)
```

Default match mode:

```text
ANY
```

Shared errors:

```text
INTERNAL_AUTH_MISSING
INTERNAL_AUTH_INVALID_SIGNATURE
INTERNAL_AUTH_REPLAY
INTERNAL_AUTH_FORBIDDEN
```

## Response And Error Strategy

Trace id is created at the first entrypoint, normally BFF, then propagated downstream by OpenTelemetry headers:

```text
traceparent
tracestate
```

Downstream response:

- BFF forwards raw downstream wrapper.
- If tracing works correctly, downstream wrapper `traceId` equals BFF trace id.

BFF-local error:

- BFF returns sharedpackage wrapper with `BFF_*` code.

Suggested BFF error codes:

```text
BFF_SESSION_MISSING
BFF_SESSION_INVALID
BFF_SESSION_EXPIRED
BFF_SESSION_REVOKED
BFF_PORTAL_TOKEN_INVALID
BFF_DPOP_REQUIRED
BFF_DPOP_INVALID
BFF_DPOP_REPLAY
BFF_DOWNSTREAM_UNAVAILABLE
```

## Downstream Calls

BFF uses typed HTTP clients through sharedpackage protocol:

```java
@Bean
CommonConfigHttpClient commonConfigHttpClient(TsbHttpClientFactory factory) {
    return factory.httpInterface("common", CommonConfigHttpClient.class);
}
```

BFF does not use a generic reverse proxy.

Route shape stays close to downstream APIs:

```text
BFF:
GET /bff/api/portal/config/apps/{app}/profiles/{profile}/versions/latest

Common:
GET /common/api/config/v1/apps/{app}/profiles/{profile}/versions/latest
```

## Service Discovery

BFF should call downstream through service discovery where available.

Resolution rule:

```text
if discovery enabled and service-id resolves:
  base = discovered scheme://host:port + context-path
else:
  base = base-url fallback
```

BFF does not implement load balancing as a goal for phase one. Discovery, service mesh, or infrastructure load balancer should own instance choice. If discovery returns multiple instances locally, sharedpackage may temporarily pick the first healthy endpoint.

Desired config:

```yaml
tsb:
  shared:
    protocol:
      discovery:
        enabled: true
        provider: consul
      downstreams:
        common:
          service-id: common
          context-path: /common/api
          base-url: http://localhost:8083/common/api
        auth:
          service-id: auth
          context-path: /auth/api
          base-url: http://localhost:8084/auth/api
        core:
          service-id: core
          context-path: /core/api
          base-url: http://localhost:8085/core/api
        client:
          service-id: client
          context-path: /client/api
          base-url: http://localhost:8081/client/api
```

This requires sharedpackage protocol support for:

```text
ProtocolProperties.Downstream.contextPath
TsbHttpClientFactory appends contextPath to discovered base URL
```

## Common Config Client In Sharedpackage

Common Config Client moves from service-local code into sharedpackage.

It is opt-in:

```yaml
tsb:
  shared:
    common-config:
      enabled: true
      app: bff
      profile: local
      base-url: http://localhost:8083/common/api
      api-key: ${COMMON_CONFIG_API_KEY}
      polling-interval: 30s
      fail-fast: true
      redis:
        enabled: true
        channel: tsb:config:changed
```

Public bean:

```text
TsbCommonConfigClient
```

API:

```text
snapshot()
getString(key, defaultValue)
getBoolean(key, defaultValue)
getLong(key, defaultValue)
reload(source, failFast)
```

It pulls config on startup, caches L1, polls periodically, and subscribes to Redis pub/sub for config changes.

## Internal Auth Secret Source

HMAC secret priority:

```text
1. Common Config L1 snapshot
2. application.yml/env fallback
3. missing -> fail fast if internal-auth enabled
```

Config keys:

```text
security.internal-auth.hmac-secret
security.internal-auth.issuer
security.internal-auth.max-skew-seconds
```

Fallback:

```yaml
tsb:
  shared:
    internal-auth:
      enabled: true
      hmac-secret: ${TSB_INTERNAL_AUTH_HMAC_SECRET:local-dev-secret}
      issuer: ${spring.application.name}
      max-skew: 60s
```

## Implementation Order

1. Sharedpackage protocol:
   - add downstream `context-path`
   - append context path when service discovery resolves host/port

2. Sharedpackage common-config client:
   - opt-in auto-configuration
   - startup pull
   - L1 snapshot
   - polling
   - Redis pub/sub reload

3. Sharedpackage internal-auth:
   - auth context model
   - signer
   - verifier filter
   - nonce replay store
   - context holder
   - `@RequireRole`
   - `@RequireScope`

4. BFF:
   - context path `/bff/api`
   - mobile session auth from Redis
   - portal JWT verify through JWKS
   - portal principal lookup from Auth service and short cache
   - DPoP verification for sensitive mobile APIs
   - typed controllers and typed downstream clients

5. Domain services:
   - enable internal-auth verification
   - add authorization annotations where needed

## Non-Goals For First Pass

- BFF database
- generic reverse proxy
- BFF route-level authorization
- home/dashboard aggregation
- service-side load balancing in BFF
- service mesh integration
- full auth service implementation
- portal permission model stored in Keycloak
