# TruongSonBank Common Config Management Design

Status: approved for implementation planning.

## 1. Goal

Build `/common` as a runtime Config Server service.

The service stores non-secret configuration in DB, supports draft-to-publish workflow, and lets domain services load config at startup plus refresh their runtime L1 cache through polling and Redis Pub/Sub.

This module belongs in `/common`, not `sharedpackage`.

`/common` is a modular monolith. It can contain multiple bounded contexts such as `config`, `notification`, `file`, and `sms`, but they are deployed as one application in phase 1. Each bounded context should be structured like a real service using hexagonal architecture so it can be extracted into a microservice later with minimal package movement.

## 2. Approved Scope

Phase 1 supports:

- DB-backed config storage.
- Canonical flat key-value config.
- API response can expose flat map and nested map.
- Shared defaults plus app/profile overrides.
- Draft -> publish workflow.
- Internal API key per service.
- Startup pull by domain services.
- L1 in-memory config snapshot in domain services.
- Interval polling.
- Redis Pub/Sub config change event.
- Runtime invalid reload keeps old config.
- Startup invalid config fails fast.

Phase 1 does not support:

- Secret storage.
- Maker-checker approval.
- UI.
- Spring Cloud Config Server.
- Redis tracking/keyspace notification.
- Push-only config without polling.
- Dynamic rebinding of every Spring bean.

## 3. Architecture

```text
common service
  - modular monolith under /common
  - config module exposes REST API for draft/publish config
  - config module stores canonical key-value config in MySQL
  - config module publishes config-changed events through Redis

domain service
  - pulls config from common at startup
  - injects config PropertySource into Spring Environment at startup
  - stores L1 in-memory config snapshot
  - polls common periodically
  - subscribes to Redis Pub/Sub for faster reload
  - reads dynamic runtime config through ConfigClient API
```

Runtime config split:

- Startup/static config can be injected into Spring Environment.
- Runtime dynamic config should be read through ConfigClient API.
- Already-created Spring beans are not automatically rebound in phase 1.

## 3.1 Common Modular Monolith Structure

`/common` should be organized by module first, not by technical layer first.

Target package shape:

```text
vn.com.truongsonbank.common
  CommonApplication

  config
    domain
      model
      service
      port
    application
      usecase
      dto
    adapter
      inbound
        rest
      outbound
        persistence
        redis
    infrastructure
      config

  notification
    domain
    application
    adapter
    infrastructure

  file
    domain
    application
    adapter
    infrastructure

  sms
    domain
    application
    adapter
    infrastructure
```

Hexagonal rules:

- Domain owns business rules and must not depend on Spring/JPA/Redis/web classes.
- Application layer owns use cases and transaction boundaries.
- Ports are interfaces owned by domain/application.
- Adapters implement ports for REST, DB, Redis, external providers, file storage, and SMS gateways.
- Cross-module calls inside `/common` should go through application/use-case interfaces, not directly through repositories.
- Shared primitives that are truly common to all modules can live under `common.shared`, but module-specific helpers stay inside the module.

Extraction goal:

- `common.config` can later become a standalone `config-service`.
- `common.notification` can later become a standalone `notification-service`.
- `common.file` can later become a standalone `file-service`.
- `common.sms` can later become a standalone `sms-service`.

## 4. Config Scope

Config scope is:

```text
app + profile
```

Examples:

- `application/default`
- `application/local`
- `client/default`
- `client/local`

Merge order:

```text
1. application/default
2. application/{profile}
3. {app}/default
4. {app}/{profile}
```

Local `application.yml` and environment variables can still override values when Spring precedence is higher.

## 5. DB Design

### config_entry

```text
id bigint primary key
app varchar(128) not null
profile varchar(128) not null
config_key varchar(512) not null
config_value text not null
value_type varchar(32) not null      -- STRING, NUMBER, BOOLEAN, JSON
version bigint not null
status varchar(32) not null          -- DRAFT, PUBLISHED
created_at timestamp not null
updated_at timestamp not null
published_at timestamp null
```

Constraints:

- unique draft key: `(app, profile, config_key, status)` for `DRAFT`
- published rows are versioned and immutable after publish

### config_client

```text
id bigint primary key
app varchar(128) not null
api_key_hash varchar(256) not null
enabled boolean not null
created_at timestamp not null
updated_at timestamp not null
```

Rules:

- API key plaintext is never stored.
- API key authorizes a service to read only its own app config plus `application/*` shared defaults.

### config_publish_event

```text
id bigint primary key
app varchar(128) not null
profile varchar(128) not null
version bigint not null
changed_keys_json text not null
published_at timestamp not null
```

This table is for audit and polling fallback.

## 6. REST API

Public client API:

```text
GET /config/v1/apps/{app}/profiles/{profile}
GET /config/v1/apps/{app}/profiles/{profile}/versions/latest
```

Admin API:

```text
POST /config/v1/apps/{app}/profiles/{profile}/draft
POST /config/v1/apps/{app}/profiles/{profile}/publish
GET  /config/v1/apps/{app}/profiles/{profile}/draft
GET  /config/v1/apps/{app}/profiles/{profile}/versions/{version}
```

Client auth:

```text
X-Config-Api-Key: <service-api-key>
```

Example response:

```json
{
  "app": "client",
  "profile": "local",
  "version": 12,
  "flat": {
    "feature.transfer.enabled": "true",
    "limit.transfer.daily": "50000000"
  },
  "nested": {
    "feature": {
      "transfer": {
        "enabled": true
      }
    },
    "limit": {
      "transfer": {
        "daily": 50000000
      }
    }
  }
}
```

## 7. Redis Pub/Sub

Channel:

```text
tsb:config:changed
```

Event:

```json
{
  "app": "client",
  "profile": "local",
  "version": 12,
  "changedKeys": [
    "feature.transfer.enabled"
  ],
  "publishedAt": "2026-09-28T10:00:00Z"
}
```

Rules:

- Common publishes event after successful publish transaction.
- Domain services reload only if event app/profile matches their config scope.
- Polling still runs to recover from missed Pub/Sub events.

## 8. Domain Service Client

Config:

```yaml
tsb:
  config:
    enabled: true
    server-url: http://common:8080
    app: client
    profile: local
    api-key: ${CONFIG_CLIENT_API_KEY}
    polling-interval: 30s
    redis:
      enabled: true
      channel: tsb:config:changed
```

Runtime API:

```java
configClient.getString("feature.transfer.enabled");
configClient.getBoolean("feature.transfer.enabled", false);
configClient.getLong("limit.transfer.daily", 0L);
configClient.snapshot();
```

Startup behavior:

1. Fetch latest published config.
2. Validate response.
3. Inject a PropertySource into Spring Environment.
4. Store L1 snapshot.
5. If fetch/validation fails, fail startup.

Runtime reload behavior:

1. Pub/Sub or polling detects newer version.
2. Fetch latest published config.
3. Validate response.
4. If valid, replace L1 snapshot atomically.
5. If invalid, keep old snapshot and record log/metric.

## 9. Validation

Phase 1 validation:

- key is not blank
- value is not null
- value matches declared `value_type`
- no secret-looking keys are accepted

Secret key rejection patterns:

```text
password
secret
token
api-key
apikey
private-key
credential
```

Rejected secret config should return a clear validation error. Secrets must remain in env/K8s Secret/Vault.

## 10. Observability

Metrics:

- `tsb.config.fetch.duration`
- `tsb.config.reload.count`
- `tsb.config.reload.failure.count`
- `tsb.config.snapshot.version`

Tags:

- `app`
- `profile`
- `source` -- startup, polling, pubsub
- `outcome`

Logs:

- Config publish event summary.
- Service config reload summary.
- Runtime reload failure with old version retained.

Secrets and config values must not be logged.

## 11. Failure Rules

Startup:

- Cannot fetch config: fail fast.
- Invalid config: fail fast.
- Unauthorized API key: fail fast.

Runtime:

- Cannot fetch config: keep old L1 snapshot.
- Invalid config: keep old L1 snapshot.
- Redis unavailable: continue polling.
- Pub/Sub event missed: polling catches up later.

## 12. Implementation Order

1. Turn `/common` into Config Server.
2. Add DB schema creation for config tables.
3. Implement draft and publish APIs.
4. Implement client read API with merge order.
5. Add API key auth.
6. Add Redis Pub/Sub publish on config publish.
7. Add domain service config client in `client` as demo.
8. Add polling reload.
9. Add Redis Pub/Sub reload.
10. Add metrics/logging.

## 13. Demo Plan

Common:

- Create config client for `client`.
- Create draft config:
  - `feature.transfer.enabled=true`
  - `limit.transfer.daily=50000000`
- Publish config.

Client:

- Startup pulls config.
- Endpoint reads dynamic config from L1:
  - `GET /shared-test/config/feature-transfer`
- Update config in common and publish.
- Client reloads by Pub/Sub or polling.
- Endpoint returns new value without restart.
