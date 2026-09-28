# TruongSonBank Shared Starter Design

Status: draft, not approved for implementation.

## 1. Goal

`sharedpackage` will become a single Spring Boot starter library for TruongSonBank services.

Artifact:

```text
truongsonbank-shared-starter
```

Base package:

```text
vn.com.truongsonbank.shared
```

Config prefix:

```yaml
tsb:
  shared:
```

Main rule:

- Services use annotations for behavior.
- Services configure modules through `application.yml`.
- The starter provides auto-configuration, annotations, shared contracts, and integration utilities.
- `sharedpackage` is not a runtime service.

## 2. Packaging Approach

Decision: one artifact only.

```text
truongsonbank-shared-starter
```

All modules live in one starter and are enabled/disabled by config.

Trade-off:

- Pros: easiest onboarding for services.
- Cons: dependency graph can become heavier.
- Control: each module must be conditional by classpath/config, so unused integrations do not start accidentally.

## 3. Auto-Configuration Shape

Each module owns:

- annotation(s)
- properties class
- auto-configuration class
- metrics names
- error codes if needed
- tests for enabled/disabled behavior

Suggested internal packages:

```text
vn.com.truongsonbank.shared.response
vn.com.truongsonbank.shared.exception
vn.com.truongsonbank.shared.discovery
vn.com.truongsonbank.shared.logging
vn.com.truongsonbank.shared.cache
vn.com.truongsonbank.shared.kafka
vn.com.truongsonbank.shared.protocol
vn.com.truongsonbank.shared.sequence
```

## 4. Response Module

### Decisions

Response wrapper:

```json
{
  "traceId": "abc",
  "success": true,
  "code": "SUCCESS",
  "message": "Success",
  "duration": "10ms",
  "timestamp": "1234",
  "data": {}
}
```

Response wrapping is annotation-based, not global.

Annotations:

```java
@StandardResponse
@RawResponse
```

Usage:

```java
@StandardResponse
@RestController
class UserController {
    @RawResponse
    @GetMapping("/download")
    byte[] download() {
        return content;
    }
}
```

Behavior:

- `@StandardResponse` can be used on class or method.
- `@RawResponse` excludes a method from wrapping.
- File download, streaming, actuator, OpenAPI, and error dispatch should never be wrapped accidentally.

Config:

```yaml
tsb:
  shared:
    response:
      enabled: true
```

## 5. Exception Module

### Decisions

Error declaration is hybrid:

- code/httpStatus/default message in code
- localized message in message bundles

Language resolution:

```text
X-Language -> Accept-Language -> vi
```

Generic exceptions:

```text
BusinessException
ValidationException
UnauthorizedException
ForbiddenException
NotFoundException
ConflictException
RateLimitException
DownstreamException
TimeoutException
```

Domain-specific errors are declared by each service through an `ErrorDescriptor`.

Example:

```java
enum AuthErrors implements ErrorDescriptor {
    INVALID_OTP("AUTH_001", 400, "Invalid OTP");
}
```

Message bundle:

```properties
AUTH_001=OTP khong hop le
```

Config:

```yaml
tsb:
  shared:
    exception:
      enabled: true
      default-language: vi
```

## 6. Service Discovery Module

### Decisions

First implemented provider: Consul over HTTP API.

Service must explicitly choose:

```yaml
tsb:
  shared:
    protocol:
      discovery:
        enabled: true
        provider: consul
        consul-url: http://consul:8500
```

Protocol downstreams can use `service-id`. If discovery is disabled or no passing instance is found, the client falls back to direct `base-url` or `target`.

```yaml
tsb:
  shared:
    protocol:
      discovery:
        enabled: false
      downstreams:
        client2:
          service-id: client2
          base-url: http://client2:8082
        client2-grpc:
          service-id: client2-grpc
          target: client2:9092
```

## 7. Logging Module

### Decisions

Log format:

```text
JSON only
```

Collector mode:

- app writes JSON logs to stdout
- local Docker Compose uses Promtail Docker service discovery to collect container stdout and push to Loki
- Kubernetes should use a node-level collector/agent such as Promtail, Fluent Bit, Vector, or OpenTelemetry Collector DaemonSet to collect pod stdout
- adding a new service must not require editing a per-service log file path in the collector
- no rolling file output in the first version

Masking:

- default mask rules include auth/security and banking PII
- services can override/extend in `application.yml`

Default full mask:

```text
password, pin, otp, token, authorization, accessToken, refreshToken, sessionId, secret, apiKey
```

Default partial mask:

```text
phone: keepLast=3
cccd: keepLast=3
accountNumber: keepLast=4
cardNumber: keepFirst=6, keepLast=4
email: keepFirst=2
```

Config:

```yaml
tsb:
  shared:
    logging:
      enabled: true
      format: json
      output: stdout
      mask:
        full:
          - password
          - pin
          - otp
          - token
          - authorization
          - accessToken
          - refreshToken
          - sessionId
          - secret
          - apiKey
        partial:
          phone:
            keys: [phone, phoneNumber, mobile]
            keep-last: 3
          cccd:
            keys: [cccd, idNumber]
            keep-last: 3
          account:
            keys: [accountNumber]
            keep-last: 4
          card:
            keys: [cardNumber, pan]
            keep-first: 6
            keep-last: 4
          email:
            keys: [email]
            keep-first: 2
```

Rule:

- Do not log sensitive data in free text.
- Log sensitive values only as JSON/keyed fields so masking can work.

## 8. Cache Module

### Decisions

L1:

- abstraction is open
- default provider: Caffeine

L2:

- Redis only in first version

Invalidation:

- default: Redis Pub/Sub broadcast `CACHE_EVICT`
- optional: Redis tracking mode when infrastructure supports it

Annotations:

```java
@TsbCacheable
@TsbCacheEvict
@TsbCachePut
@TsbCacheTracking
```

`@TsbCacheable` must support per-cache TTL overrides:

```java
@TsbCacheable(
    name = "customer_profile",
    namespace = "customer",
    key = "#customerId",
    ttl = "60s",
    softTtl = "45s",
    nullTtl = "10s",
    l1Enabled = true,
    lockEnabled = true,
    syncMode = SyncMode.ASYNC_REFRESH,
    ttlJitterEnabled = true
)
CustomerProfile getCustomer(String customerId) {
    ...
}
```

TTL meanings:

- `ttl`: hard TTL. After this duration the cache entry is expired.
- `softTtl`: stale threshold. After this duration the old value can still be returned while async refresh runs.
- `nullTtl`: short TTL for null/not-found values to prevent cache penetration.

Rules:

- `softTtl` must be lower than `ttl`.
- `nullTtl` should be short, normally 10-30 seconds.
- Do not use long `softTtl` for data that must be strongly real-time, such as available balance.
- Enable TTL jitter in production to avoid many keys expiring at the same time.

Config:

```yaml
tsb:
  shared:
    cache:
      enabled: true
      key-prefix: "tsb:${spring.application.name}:"
      key-version: "v1"
      default-ttl: 1h
      default-soft-ttl: 45m
      null-ttl: 30s
      l1:
        enabled: true
        provider: caffeine
      l2:
        enabled: true
        provider: redis
      invalidation:
        mode: pubsub # pubsub | tracking
        channel: tsb:cache:evict
      metrics:
        enabled: true
      tracing:
        enabled: true
```

Expected behavior:

- cache lookup checks L1, then L2, then supplier/method execution.
- cache put writes L2 and L1.
- cache evict publishes invalidation event.
- all instances receive invalidation and clear L1 entry.
- tracking mode is optional and must fail closed to pub/sub or disabled based on config.

## 9. Kafka / Message Queue Module

### Decisions

Kafka only in the first version.

Mechanisms:

- retry: implemented with fixed backoff in listener factory
- DLQ: implemented with topic suffix
- outbox: implemented as DB-backed pending-event writer with best-effort schema initialization
- idempotent consumer: implemented with `@TsbKafkaIdempotent` and Redis state
- topic metadata: implemented through `tsb.shared.kafka.topics`
- security: pass-through Kafka client properties implemented
- transactions: config flag and producer property support implemented, no high-level transaction API yet
- metrics: producer publish, consumer duration, retry, DLQ publish implemented
- monitoring: exposed through actuator/prometheus
- tracing: publisher injects `traceparent`; consumer extracts it and creates a Kafka consumer span

Outbox:

- enable with `tsb.shared.kafka.outbox.enabled=true`
- startup checks whether the outbox table exists.
- if the table is missing and `auto-create-table=true`, startup tries to create it.
- startup does not fail if there is no `DataSource` or table creation fails; it only logs a warning.
- calling `TsbKafkaOutbox.save(...)` fails clearly when no `DataSource` exists or the table is not ready.
- default table: `tsb_outbox_event`
- relay worker polls `PENDING` events, marks `PROCESSING`, publishes to Kafka, then marks `SENT`.
- relay failure moves the row back to `PENDING` with `next_retry_at`; after max attempts it becomes `FAILED`.
- direct Kafka publish still exists; use outbox only when the event must be committed atomically with domain DB changes.
- DLQ replay is implemented for one record by `topic + partition + offset`; it publishes the DLQ record back to the original topic by stripping the DLQ suffix.
- DLQ replay preserves existing Kafka headers and adds `x-dlq-replayed`, `x-dlq-replayed-at`, `x-dlq-source-topic`, `x-dlq-source-partition`, and `x-dlq-source-offset`.

```yaml
tsb:
  shared:
    kafka:
      outbox:
        enabled: true
        relay-enabled: true
        auto-create-table: true
        table-name: tsb_outbox_event
        batch-size: 20
        poll-interval-ms: 1000
        max-attempts: 5
        retry-backoff: 10s
```

Publish API:

- first slice uses manual publisher only

```java
tsbKafkaPublisher.send(topic, key, payload);
```

```java
tsbKafkaDlqReplay.replay("payment.events.DLQ", 0, 42);
```

```java
@KafkaListener(topics = "payment.events")
@TsbKafkaIdempotent
void onPaymentEvent(Map<String, Object> payload) {
    ...
}
```

Idempotent consumer:

- default key is `topic:partition:offset` when a `ConsumerRecord` argument is present.
- custom key can use SpEL against method args, for example `@TsbKafkaIdempotent(key = "#p0['eventId']")`.
- success stores `DONE` in Redis until `ttlSeconds`.
- failure deletes the Redis key so Kafka retry can run.
- in-progress state has a short TTL to recover from consumer crash while processing.

Kafka key rule:

- if topic config has `ordered=true`, key is required
- for unordered topics, key is optional

Topic creation rule:

- application services must not create Kafka topics.
- `tsb.shared.kafka.topics` is metadata for documentation/validation only.
- topics must be provisioned by infrastructure, for example Helm, Terraform, ArgoCD job, or Kafka operator.
- even if a service config contains topic metadata, sharedpackage must not register `KafkaAdmin.NewTopics`.

Retry/DLQ default:

- retry 3 times
- fixed backoff
- final failure goes to DLQ

Config:

```yaml
tsb:
  shared:
    kafka:
      enabled: true
      bootstrap-servers:
        - localhost:9092
      client-id: client
      group-id: client
      retry:
        attempts: 3
        backoff: 500ms
      dlq:
        enabled: true
        suffix: .DLQ
      consumer:
        auto-offset-reset: latest # latest | earliest | none
      security:
        enabled: false
        properties:
          security.protocol: SASL_SSL
          sasl.mechanism: SCRAM-SHA-512
      transaction:
        enabled: false
        transaction-id-prefix: tsb-tx-
      topics:
        tsb.demo.events:
          partitions: 1
          replicas: 1
          ordered: true
        tsb.demo.events.DLQ:
          partitions: 1
          replicas: 1
```

Demo:

```bash
curl -X POST 'http://localhost:8081/shared-test/kafka/demo-ok' \
  -H 'Content-Type: application/json' \
  -d '{"id":"demo-ok","amount":100}'

curl -X POST 'http://localhost:8081/shared-test/kafka/demo-fail' \
  -H 'Content-Type: application/json' \
  -d '{"id":"demo-fail","fail":true}'
```

Metrics:

```bash
curl 'http://localhost:8081/actuator/metrics/tsb.kafka.publish.duration'
curl 'http://localhost:8082/actuator/metrics/tsb.kafka.consume.duration'
curl 'http://localhost:8082/actuator/metrics/tsb.kafka.consume.retry'
curl 'http://localhost:8082/actuator/metrics/tsb.kafka.dlq.publish'
curl 'http://localhost:8081/actuator/prometheus' | grep tsb_kafka
```

Grafana dashboard:

- `TSB Kafka Observability`

## 10. Protocol Module

### Decisions

Supported from first version:

- HTTP
- gRPC

Annotation:

```java
@TsbClient(name = "core-service", protocol = HTTP)
```

Resilience:

- timeout
- retry
- circuit breaker
- fallback

Retry rule:

- retry defaults must be conservative.
- retry only for idempotent methods by default, or when annotation/config explicitly allows retry.

Config:

```yaml
tsb:
  shared:
    protocol:
      enabled: true
      defaults:
        timeout: 1500ms
        retry:
          enabled: true
          max-attempts: 3
        circuit-breaker:
          enabled: true
          failure-rate-threshold: 50
        fallback:
          enabled: true
      clients:
        core-service:
          protocol: http
          service-id: core-service
          url: http://core-service:8082
        auth-service:
          protocol: grpc
          service-id: auth-service-grpc
          target: dns:///auth-service:9090
          tls:
            enabled: true
```

## 11. Sequence Generator Module

### Decisions

Default strategy:

```text
local Snowflake
```

Source-of-truth strategies supported in first version:

```text
Redis
Consul KV
```

DB sequence is deferred.

No annotation for field generation in the first version. Use service injection.

API:

```java
long nextLong(String name);
List<Long> nextLongs(String name, int size);

String nextString(String name);
List<String> nextStrings(String name, int size);
```

Default return style should support `String` ids with prefix.

Config:

```yaml
tsb:
  shared:
    sequence:
      enabled: true
      default-strategy: snowflake # snowflake | redis | consul
      snowflake:
        worker-id-source: local # local | redis | consul
      formats:
        payment:
          prefix: PAY
          date-pattern: yyyyMMdd
        transaction:
          prefix: TXN
          date-pattern: yyyyMMdd
```

## 12. Cross-Cutting Metrics And Tracing

Every module that performs infrastructure work should expose metrics.

Minimum metrics:

- cache hit/miss/evict
- cache invalidation received/published
- kafka publish success/failure/retry/DLQ/outbox lag
- protocol client latency/retry/circuit breaker/fallback
- sequence generation latency/failure
- exception count by error code

Tracing:

- default tracing provider is OpenTelemetry.
- propagate trace context through the W3C `traceparent`/`tracestate` headers for HTTP/gRPC and message headers for Kafka.
- sharedpackage must not generate its own trace id; missing trace id means OpenTelemetry is not active for that request.
- include OpenTelemetry trace id in response envelope.
- include OpenTelemetry trace id in JSON logs.

## 13. Chaos Testing

Official Chaos Monkey for Spring Boot was evaluated for resilience experiments, but it is not enabled in `sharedpackage` right now.

Reason:

- Current services use Spring Boot `4.1.1`.
- `de.codecentric:chaos-monkey-spring-boot:4.0.0` fails at startup because it references `org.springframework.boot.restclient.RestTemplateCustomizer`, which is not available on this stack.

Current decision:

- Do not include official Chaos Monkey as a transitive dependency until a compatible version exists.
- Use lightweight demo chaos endpoints in downstream services for resilience tests.
- Keep real resilience behavior in the protocol module: timeout, retry, circuit breaker, metrics, trace.

Demo chaos endpoints:

```bash
curl http://localhost:8081/shared-test/protocol/client2/flaky/demo-1
curl http://localhost:8081/shared-test/protocol/client2/slow/3000
curl http://localhost:8081/shared-test/protocol/client2/fail/503
```

These endpoints trigger downstream `503`, latency, and flaky behavior so the protocol module can be tested end to end.

Future option:

- Revisit official Chaos Monkey when it publishes a version compatible with Spring Boot `4.1.x`.
- Or add a tiny internal `tsb-chaos` module for local/dev/staging only.

<!-- Historical notes kept below for the official Chaos Monkey shape if it becomes compatible later. -->

Official Chaos Monkey usage shape:

Default behavior:

- disabled unless the service enables the `chaos-monkey` profile and `chaos.monkey.enabled=true`
- controlled through Spring Boot Actuator
- useful for local/dev/staging resilience tests, not enabled by default in production

Minimal service config:

```yaml
spring:
  profiles:
    active: chaos-monkey

chaos:
  monkey:
    enabled: true
    watcher:
      service: true
      rest-controller: true
    assaults:
      level: 1
      latency-active: true
      latency-range-start: 500
      latency-range-end: 1500

management:
  endpoint:
    chaosmonkey:
      access: unrestricted
  endpoints:
    web:
      exposure:
        include: health,prometheus,chaosmonkey
```

Useful endpoints:

```bash
curl http://localhost:8081/actuator/chaosmonkey/status
curl -X POST http://localhost:8081/actuator/chaosmonkey/enable
curl -X POST http://localhost:8081/actuator/chaosmonkey/disable
curl http://localhost:8081/actuator/metrics | grep chaos
```

## 14. First Implementation Slice Recommendation

Implement in this order:

1. response + exception
2. logging
3. cache
4. sequence
5. protocol
6. kafka
7. discovery integration

Reason:

- response/exception/logging define service contract and observability baseline.
- cache/sequence are self-contained.
- protocol/kafka/discovery have larger dependency and infrastructure impact.

## 15. Not In First Version

- multiple starter artifacts
- DB-backed sequence generator
- Hazelcast L2 cache
- file-based log output
- RabbitMQ
- full OpenAPI generator integration inside this starter
- data crypto
- audit module
- feature flag module
- batch/distributed job
