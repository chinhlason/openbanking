# Protocol Resilience Brainstorm

## Context

This document records the design discussion for the `sharedpackage` protocol module before implementation.

Existing sharedpackage modules already implemented:

- standardized response wrapper
- exception handling and localized messages
- input validation
- structured logging with masking
- centralized logging through Loki and OpenTelemetry Collector
- cache L1/L2 with Redis invalidation/tracking
- metrics through Prometheus
- tracing export through OpenTelemetry and Tempo

The protocol module is the next candidate module. It should provide reusable client/server communication behavior for service-to-service calls.

## Scope Decisions

### Protocols

Decision: support both HTTP and gRPC.

Implementation should still be sliced to avoid building too much at once:

1. HTTP client resilience first.
2. gRPC client/server interceptors later, using the same policy model where possible.

### Resilience Topics To Clarify

We will clarify these topics in order:

1. Timeout
2. Retry
3. Circuit breaker
4. Fallback
5. Rate limit / bulkhead
6. Idempotency rules
7. Observability: logs, metrics, traces

### Timeout

Decision: timeout must be configurable per downstream service and per operation, with global defaults.

Expected shape:

- global default timeout so new services work without large config
- per downstream override, for example `auth-service`, `core-service`
- per operation override, for example `login`, `transfer`, `payment`

Rationale:

- different downstreams have different latency profiles
- payment/transfer operations may need different timeout from simple lookup calls
- global defaults keep onboarding simple

Open details:

- default timeout values
- behavior when timeout happens

### Timeout Fields

Decision: use two timeout fields:

- `connect-timeout`: maximum time to establish a connection to the downstream.
- `response-timeout`: maximum time to wait for a downstream response after request is sent.

Do not add `total-timeout` in the first implementation.

Rationale:

- `connect-timeout` and `response-timeout` map well to HTTP clients.
- gRPC can use the same concept through channel connection behavior and call deadline/timeout.
- avoiding `total-timeout` keeps the first configuration easier to understand.

### Retry Level

Decision: protocol retry is call-level retry, with idempotency key propagation support.

Meaning:

- If service A calls service B and the outbound call fails with a retryable failure, service A may retry that outbound call.
- The protocol module must not retry the whole business journey by default.
- Journey-level retry belongs to the domain layer, for example outbox, saga, reconciliation, scheduled retry, or payment state machine.

Example journey:

```text
App -> BFF -> Payment -> Account -> Notification
```

Retry can happen per edge:

```text
BFF retry Payment
Payment retry Account
Payment retry Notification
```

But the whole journey must not be replayed automatically by the protocol module.

### Idempotency Key Propagation

Decision: protocol module should propagate idempotency key for outbound calls.

Expected behavior:

- If inbound request contains an idempotency key, outbound clients should forward it by default.
- HTTP header candidate: `Idempotency-Key`.
- gRPC metadata candidate: `idempotency-key`.
- Retry policy may allow retry for non-idempotent methods only when the operation is explicitly marked idempotent or an idempotency key is present.

Rationale:

- supports safe retry across service boundaries
- avoids accidental double payment/double transfer for dangerous operations
- keeps business-level deduplication in domain services, not in the protocol module

### Retryable Failures

Decision: retry only these failures by default:

- network/connect failure
- timeout
- HTTP `502 Bad Gateway`
- HTTP `503 Service Unavailable`
- HTTP `504 Gateway Timeout`
- HTTP `429 Too Many Requests`

Do not retry HTTP `500 Internal Server Error` by default.

For HTTP `429`:

- if response has `Retry-After`, retry delay must follow `Retry-After`
- if `Retry-After` is missing or invalid, use configured backoff

Rationale:

- network/timeout/502/503/504 are commonly transient
- 429 needs special handling to avoid fighting downstream rate limits
- retrying 500 by default can amplify incidents and hide real server bugs

### Retry Backoff

Decision: backoff must be configurable per downstream service and per operation.

Default strategy:

- exponential backoff
- jitter enabled

Config should allow at least:

- strategy: `fixed` or `exponential`
- initial delay
- max delay
- jitter enabled/disabled

For HTTP `429`, `Retry-After` takes priority over configured backoff.

Rationale:

- each downstream may have different rate limit and recovery behavior
- exponential backoff with jitter avoids synchronized retry spikes
- fixed delay remains useful for simple internal services when explicitly configured

### Retry Attempts Default

Decision: default retry attempts is `0`.

Meaning:

- no outbound call is retried unless retry is explicitly enabled by downstream or operation config
- operation config can override attempts for safe calls, for example lookup/read operations

Rationale:

- banking/payment flows should not retry writes by accident
- explicit retry policy forces each downstream/operation to declare intent
- this keeps the protocol module safe by default

### Circuit Breaker Default

Decision: circuit breaker is enabled by default for every downstream.

Circuit states:

- `CLOSED`: call downstream normally
- `OPEN`: block call immediately and fail fast
- `HALF_OPEN`: allow a limited number of test calls to check recovery

Circuit opens based on a sliding window:

```text
Within the recent N calls or recent T seconds,
if failure rate reaches the threshold,
then open circuit.
```

Failures that should count by default:

- timeout
- network/connect error
- HTTP `502`, `503`, `504`
- gRPC `UNAVAILABLE`, `DEADLINE_EXCEEDED`

Failures that should not count by default:

- HTTP `400`, `401`, `403`, `404`
- validation/business error
- HTTP `409`

HTTP `429` should be handled carefully because it means rate limit, not necessarily downstream outage. It may be tracked separately or count only if explicitly configured.

Rationale:

- fail fast protects callers when a downstream is unhealthy
- enabled by default gives baseline protection across services
- default thresholds must be conservative to avoid opening circuits too aggressively

### Circuit Breaker Default Thresholds

Decision: use conservative default thresholds because circuit breaker is enabled by default.

Default:

```yaml
minimum-calls: 20
sliding-window-size: 50
failure-rate-threshold: 60
open-duration: 30s
half-open-calls: 5
```

Meaning:

- circuit breaker starts evaluating only after at least `20` calls
- evaluates the latest `50` calls
- opens when at least `60%` of evaluated calls fail
- stays open for `30s`
- then allows `5` trial calls in half-open state

Rationale:

- avoids opening circuit from a small number of random failures
- still protects the caller when a downstream is clearly unhealthy

### Fallback

Decision: support fallback method, but only when explicitly configured.

Default behavior:

- if no fallback is configured, throw a standardized downstream exception
- fallback must not run implicitly for every failure

Fallback can be triggered by:

- timeout
- retry exhausted
- circuit breaker open
- network/connect error
- retryable HTTP/gRPC failures when policy says fallback is allowed

Rationale:

- fallback is business-sensitive and must be explicit
- avoids hiding downstream problems by accident
- allows read/lookup use cases to degrade gracefully when the service owner wants it

### Fallback Declaration

Decision: support both annotation declaration and `application.yml` override.

Annotation example:

```java
@TsbResilience(fallbackMethod = "getUserFallback")
```

YAML example:

```yaml
tsb:
  shared:
    protocol:
      downstreams:
        user-service:
          operations:
            get-user:
              fallback-method: getUserFallback
```

Expected precedence:

1. operation-level YAML override
2. annotation
3. downstream/global defaults

Rationale:

- annotation keeps fallback visible near the downstream call
- YAML lets an environment override policy without code changes
- operation-level config should win because it is the most specific

### Bulkhead / Concurrency Limit

Decision: bulkhead is disabled by default.

Meaning:

- protocol module does not limit concurrent outbound calls unless configured
- each downstream or operation can enable a concurrency limit when needed

Example config shape:

```yaml
bulkhead:
  enabled: true
  max-concurrent-calls: 50
  max-wait-duration: 100ms
```

Rationale:

- wrong concurrency limits can throttle healthy internal traffic
- services need different sizing based on traffic and downstream capacity
- explicit enablement keeps the first rollout safer

### Outbound Rate Limit

Decision: support outbound rate limit, disabled by default.

Meaning:

- protocol module can limit request rate to a downstream when configured
- no downstream is rate-limited unless explicitly enabled

Example config shape:

```yaml
rate-limit:
  enabled: true
  limit-for-period: 100
  refresh-period: 1s
  timeout-duration: 0ms
```

Rationale:

- useful for protecting external/third-party dependencies or fragile services
- disabled by default avoids throttling internal traffic unexpectedly
- downstream-specific config keeps capacity decisions close to the dependency

### Observability: Trace And Log

Decision:

- tracing is always enabled for protocol outbound calls
- outbound logging is configurable

Expected tracing behavior:

- create client span for each outbound HTTP/gRPC call
- propagate trace context to downstream:
  - HTTP: `traceparent`, `tracestate`
  - gRPC: metadata equivalent
- add useful span attributes:
  - protocol: `http` or `grpc`
  - downstream name
  - operation name
  - method
  - status/result
  - retry attempt when applicable
  - circuit breaker state when applicable

Expected logging behavior:

- default should avoid logging every successful outbound call unless configured
- log failures by default
- allow per downstream/operation config to log all outbound calls for debugging

Rationale:

- trace is needed for distributed tracing across services
- configurable logs avoid high log volume in normal traffic
- failures should still be visible without needing to enable verbose logging

### Observability: Metrics

Decision: metrics are always enabled for protocol outbound calls.

Metrics should include at least:

- outbound call duration
- outbound call count
- error count
- timeout count
- retry count
- retry exhausted count
- circuit breaker state/count
- circuit breaker open/reject count
- fallback count
- bulkhead reject count when bulkhead is enabled
- rate limit reject/wait count when rate limit is enabled

Suggested metric prefix:

```text
tsb.protocol.*
```

Suggested common tags:

- `protocol`: `http` or `grpc`
- `downstream`
- `operation`
- `method`
- `outcome`
- `status`

Rationale:

- protocol behavior must be visible in Prometheus/Grafana by default
- retry/circuit/fallback overhead must be measurable against total request duration
- metrics overhead is acceptable compared with debugging blind spots

### Developer API: HTTP And gRPC

Decision:

- HTTP should support annotation-based client.
- gRPC should not use annotation client in the first design.
- gRPC should use generated stubs created through a common factory from sharedpackage.

HTTP annotation client example:

```java
@TsbHttpClient(name = "account-service", baseUrl = "${clients.account-service.url}")
public interface AccountClient {

    @TsbGet(path = "/accounts/{accountNo}", operation = "get-account")
    AccountDto getAccount(@PathVariable String accountNo);
}
```

gRPC factory example:

```java
@Bean
AccountServiceGrpc.AccountServiceBlockingStub accountStub(TsbGrpcClientFactory factory) {
    return factory.blockingStub(
            "account-service",
            AccountServiceGrpc::newBlockingStub
    );
}
```

Domain service then uses the generated stub normally:

```java
AccountReply reply = accountStub.getAccount(request);
```

Rationale:

- HTTP does not have a strong generated client contract by default, so annotation client is useful.
- gRPC already has generated type-safe stubs from `.proto`.
- wrapping gRPC behind annotation clients would duplicate generated stub behavior and add too much proxy/mapping code.
- a shared gRPC factory can still apply common behavior:
  - trace propagation
  - idempotency metadata
  - deadline from config
  - metrics
  - configurable outbound logs
  - resilience integration for unary calls

Initial gRPC scope:

- generated stub + shared factory
- client interceptor for trace/metadata/log/metrics/deadline
- resilience support should focus on unary calls first
- streaming should not retry by default

## Notes From Uber Retry Storm And Dependency Analysis Articles

Sources:

- https://www.uber.com/vn/en/blog/protecting-against-retry-storms/
- https://www.uber.com/us/en/blog/automated-dependency-analysis/

### Retry Storm Risk

Useful insight:

- Retry at every hop can amplify traffic exponentially across deep call chains.
- Even one retry at every layer can multiply load on a degraded downstream.
- Retry budgets reduce the blast radius, but they are still not enough when every layer retries the same propagated error.

Design impact:

- Protocol retry must remain disabled by default.
- Retry attempts must be explicit per downstream/operation.
- Retry should remain call-level, not journey-level.
- Write operations must require idempotency protection before retry.

### Error Ownership

Useful insight:

- A service returning an error is not always the owner of the error.
- If a service returns error because its fail-close downstream failed, the downstream is the likely owner.
- Retrying at every ancestor in the call chain is wasteful; retry should happen as close as possible to the service that owns the error.

Candidate design for TSB:

- Add an optional propagated header/metadata to mark whether an error is owned by the current service.
- HTTP candidate:
  - `X-TSB-Error-Owner: claimed | unclaimed`
  - `X-TSB-Error-Origin-Service: <service-name>`
  - `X-TSB-Retry-Eligible: true | false`
- gRPC metadata candidate:
  - `x-tsb-error-owner`
  - `x-tsb-error-origin-service`
  - `x-tsb-retry-eligible`

Simple rule:

- If current service fails without outbound failure correlation, it may claim the error.
- If current service fails because a fail-close downstream failed, it should propagate the error as unclaimed.
- Caller should not retry an unclaimed propagated error by default.

This is not required for phase 1, but it is a strong candidate for phase 2 because it needs inbound/outbound correlation.

### Inbound/Outbound Correlation

Useful insight:

- Dependency analysis needs correlation between inbound request and outbound calls made while handling that request.
- Uber uses middleware context and shared in-memory tracking to correlate inbound outcome with outbound failures.

Candidate design for TSB:

- Add a lightweight per-request tracker in protocol module:
  - inbound service endpoint
  - outbound downstream calls
  - outbound operation
  - outbound outcome
  - final inbound outcome
- Use this to emit metrics for dependency behavior:
  - caller endpoint failed/succeeded
  - callee operation failed/succeeded
  - fail-close probability approximation

Potential metric:

```text
tsb.protocol.dependency.outcome
tags: service, endpoint, downstream, operation, inbound_outcome, outbound_outcome
```

This can later support:

- dependency-aware alerting
- identifying hard dependencies
- suppressing retry for propagated downstream-owned errors

### Fail-Close / Fail-Open Classification

Useful insight:

- Fail-close dependency: downstream failure causes caller failure.
- Fail-open dependency: downstream failure does not cause caller failure.
- Classification can be inferred from production metrics:

```text
Pc = caller_failed_when_callee_failed / all_callee_failures
```

Uber heuristic:

- `Pc >= 0.8`: fail-close
- `Pc <= 0.2`: fail-open
- otherwise unknown

Candidate design for TSB:

- Do not auto-classify dependencies in phase 1.
- Emit enough metrics so classification can be built later.
- Allow manual config first:

```yaml
dependency:
  mode: fail-close # fail-close | fail-open | unknown
```

### Retry Budget

Useful insight:

- Retry budget limits retry volume as a fraction of original traffic.
- This prevents retries from becoming unbounded during degradation.

Candidate design for TSB:

- Add optional retry budget per downstream/operation.
- Keep retry attempts explicit and default `0`.
- If retry is enabled, also allow:

```yaml
retry:
  attempts: 1
  budget:
    enabled: true
    ratio: 0.1
```

This can be deferred until retry is implemented because it requires rolling counters.

## Additional Design Additions Before Implementation

### Retry Budget Requirement

Decision: retry policy should support retry budget, but implementation can be deferred until retry itself is implemented.

Expected shape:

```yaml
retry:
  attempts: 1
  budget:
    enabled: true
    ratio: 0.1
    window: 1m
```

Meaning:

- retry volume is capped as a ratio of original traffic in a rolling window
- default remains no retry

### Retry-After Hard Cap

Decision: `Retry-After` must have a hard cap.

Expected shape:

```yaml
retry:
  max-retry-after: 5s
```

Meaning:

- HTTP `429` should honor `Retry-After`
- if `Retry-After` is larger than the cap, do not wait forever in the request thread

### Retry Classification

Decision: protocol module should classify failures before retry/circuit/fallback decisions.

Classification:

- retryable transport error
- retryable status code
- non-retryable business error
- propagated downstream error

### Request Context Tracker

Decision: phase 1 should emit enough data for later dependency analysis, but should not implement full automated dependency analysis.

Record:

- inbound endpoint
- outbound downstream/operation
- outbound outcome
- final inbound outcome

### Policy Precedence

Decision: policy resolution order is:

```text
operation config > downstream config > global default > library default
```

### Phase 1 Non-Goals

- no automatic fail-open/fail-close classification
- no automatic journey retry
- no retry for gRPC streaming
- no automatic fallback from cache
- no production-grade error ownership propagation yet
