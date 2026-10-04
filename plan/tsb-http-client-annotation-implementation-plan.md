# Plan: Annotation-Based HTTP Client Registration

Status: implemented for the first HTTP client migration; keep the remaining test and migration items for follow-up.

## 1. Goal

Domain services declare HTTP clients as interfaces and inject those interfaces directly. `sharedpackage` creates proxy beans through the existing `TsbHttpClientFactory`, so requests continue to use the same timeouts, retries, circuit breakers, tracing, metrics, logging, signed user context, and service tokens configured in `application.yml`.

Remove the need for a manual `@Bean` per HTTP interface, such as `factory.httpInterface("core", CoreAccountClient.class)`. gRPC stub creation is outside this change.

## 2. Current State and Gaps

- `TsbHttpClientFactory.httpInterface(downstream, interfaceType)` already creates a proxy with Spring's `HttpServiceProxyFactory` and attaches the protocol interceptor.
- Domain services currently declare a `@Bean` for each HTTP interface, such as `ClientSelfHttpClient` and `Client2HttpClient` in `ClientApplication`.
- `registerOperations` currently recognizes only `@GetExchange` and `@PostExchange`.
- `operationForPath` compares the route template declared on the interface with `request.getURI().getPath()`. If `base-url` has a path prefix, or the interface has a class-level `@HttpExchange`, the route may not match and the `@TsbOperation` policy may not be applied.
- Route matching does not distinguish HTTP methods; GET and POST on the same path can select the wrong operation.
- `downstreamConfig` returns an empty configuration when a downstream name is wrong. Annotation registration should fail at startup instead of creating a client with the wrong endpoint.

## 3. Domain Service API

```java
@TsbHttpClient(downstream = "core")
public interface CoreAccountClient {
    @TsbOperation("core.account.open")
    @PostExchange("/core/api/v1/accounts/open")
    TsbResponse<OpenAccountResponse> open(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody OpenAccountRequest request);
}
```

```java
@SpringBootApplication
@EnableTsbHttpClients
public class ClientApplication {
}
```

```java
@Service
class CustomerOnboardingService {
    private final CoreAccountClient coreAccountClient;

    CustomerOnboardingService(CoreAccountClient coreAccountClient) {
        this.coreAccountClient = coreAccountClient;
    }
}
```

By default, `@EnableTsbHttpClients` scans the package of the annotated application class recursively. Use `basePackages` when interfaces live elsewhere. `@TsbHttpClient` contains only the downstream name; URLs and secrets remain in configuration. `@TsbOperation` is optional. Without it, the client uses downstream/default policies and an operation name derived from the HTTP method and route.

Configuration follows the existing convention:

```yaml
tsb:
  shared:
    protocol:
      downstreams:
        core:
          base-url: ${CORE_BASE_URL:http://localhost:8085}
          service-auth:
            enabled: ${TSB_CLIENT_CORE_SERVICE_AUTH_ENABLED:false}
            client-id: ${TSB_CLIENT_SERVICE_CLIENT_ID:client-service}
            client-secret: ${TSB_CLIENT_SERVICE_CLIENT_SECRET}
            token-uri: ${TSB_SERVICE_TOKEN_URI}
            audience: core-service
```

## 4. Bean Registration Architecture

```text
Spring Boot startup
  -> @EnableTsbHttpClients imports the registrar
  -> scan base packages for interfaces annotated with @TsbHttpClient
  -> validate each interface and its downstream configuration
  -> register one bean per interface
  -> create each proxy through TsbHttpClientFactory.httpInterface(...)
  -> domain services inject clients by interface type

Runtime call
  -> Spring HTTP interface proxy
  -> RestClient created by TsbHttpClientFactory
  -> TsbProtocolClientHttpRequestInterceptor
  -> resolve the operation policy and service-auth configuration
  -> attach trace/user context/service token
  -> send the downstream HTTP request
```

Reuse `TsbHttpClientFactory`, `ProtocolProperties`, and the existing interceptor. The registrar/FactoryBean only discovers interfaces and creates beans; it does not implement another HTTP proxy or resilience pipeline. The bean must report the interface from `getObjectType()` so Spring can autowire by type. Use a stable bean name based on the fully qualified interface name. Duplicate names or registrations must fail at startup with a clear error.

### Scanning and Validation Rules

- Scan interfaces annotated with `@TsbHttpClient`. The scanner must accept independent interfaces, not just concrete classes.
- Reject annotations on classes/records, interfaces without HTTP exchange methods, blank downstream names, missing/disabled downstreams, and downstreams without a valid `base-url`, `target`, or `service-id`.
- Every exposed method must use a supported Spring HTTP exchange annotation: `@GetExchange`, `@PostExchange`, `@PutExchange`, `@PatchExchange`, `@DeleteExchange`, or `@HttpExchange`. Helper `default` methods are allowed.
- If `basePackages` is empty, derive it from the class annotated with `@EnableTsbHttpClients`. Explicit `basePackages` can include other packages.
- When `tsb.shared.protocol.enabled=false`, do not register annotation-based clients. A service that still injects one will fail at startup with a missing-bean error.

## 5. Operation Policy and Precedence

Fix operation registration and matching in `sharedpackage` before migrating a production client:

1. Extract the HTTP method and route template from Spring HTTP exchange annotations, including any interface-level `@HttpExchange` prefix.
2. Normalize routes against the downstream path (`base-url`/`context-path`); ignore the query string when matching.
3. Match by **HTTP method + route template**, including `{pathVariable}` segments. Reject duplicate mappings for the same downstream, method, and route template at startup.
4. Use `@TsbOperation.value` as the operation ID for configuration, metrics, and circuit breakers. If absent, derive a stable ID from the HTTP method and route.
5. Precedence: `operations.<id>` in YAML overrides the corresponding field on `@TsbOperation`; the annotation overrides the downstream policy; the downstream policy overrides global defaults. Merge only explicitly set fields so setting a timeout in YAML does not accidentally discard retry or circuit breaker settings from the annotation.
6. If a request does not match a registered method, retain the `HTTP_METHOD + request path` fallback without selecting another operation's policy.

Do not send `X-TSB-Operation` to the downstream merely to identify the interface method. If an internal header is used between the proxy and interceptor, remove it before sending the request.

## 6. Security and Error Handling

- The proxy must use `TsbProtocolClientHttpRequestInterceptor`. When `service-auth.enabled=true`, it obtains/caches a token and attaches `Authorization: Bearer ...` for the downstream audience.
- The interceptor continues to attach the existing signed user-context and trace headers.
- Never log the `client-secret`, token, or raw Authorization header.
- Missing downstream configuration or an invalid interface must fail at startup and identify both the interface and downstream.
- Token-fetch and downstream failures retain the protocol module's existing error contract and metrics.
- The annotation does not automatically enable retries for POST/transfer operations. Existing idempotency rules still apply.

## 7. Implementation Steps

### Current implementation status

- Step 1 is implemented: `@TsbHttpClient`, `@EnableTsbHttpClients`, interface scanning, proxy bean registration, and startup validation.
- Step 2 is implemented: supported exchange annotations, interface-level path prefixes, method-plus-route matching, duplicate mapping detection, and base URL/context path normalization.
- Step 3 is partially implemented: `ClientSelfHttpClient` uses annotation registration; `Client2HttpClient` still uses the existing manual factory because its downstream is disabled in the current client configuration.
- Step 4 still needs dedicated automated coverage for invalid registrations, operation policy precedence, and service-token behavior.

### Step 1 - Annotation and Bean Registration

- Add `@TsbHttpClient(downstream = ...)` under `sharedpackage.protocol`.
- Add `@EnableTsbHttpClients(basePackages = ...)` and an interface-scanning registrar.
- Register proxy beans through `TsbHttpClientFactory.httpInterface`; reuse the existing HTTP client stack.
- Validate configuration and duplicate registrations at startup.

### Step 2 - Operation Metadata

- Support the relevant Spring exchange annotations, interface-level prefixes, HTTP methods, and routes.
- Update policy lookup so method and route match the actual request.
- Merge policies at the field level; YAML wins when it sets the same field as the annotation.

### Step 3 - Migrate One Existing Client

- Add `@TsbHttpClient(downstream = "client-self")` to `ClientSelfHttpClient` (a GET client with `@TsbOperation`).
- Add `@EnableTsbHttpClients` to `ClientApplication` and remove the corresponding manual `@Bean`.
- After verification, migrate `Client2HttpClient` if the client2 demo is still in use. `CustomerOnboardingService` can continue using `protocol.restClient("core")` initially. Migrating it to `CoreAccountClient` is a later step that must preserve its request/response contract and idempotency key.

### Step 4 - Tests and Verification

- Context test: discover an annotated interface and inject it without a manual `@Bean`.
- Negative tests: unknown downstream, duplicate interface/route, annotation on a class, and disabled downstream.
- HTTP integration test: GET/POST, path variables, and interface-level path prefix. Assert that the expected operation ID actually selects its timeout/retry policy, rather than checking only for HTTP 200.
- Security test: enabled service auth attaches a token with the correct audience; disabled service auth does not call the token endpoint; raw Authorization is not logged.
- Regression checks: existing `factory.httpInterface(...)` and `factory.restClient(...)` calls still work; gRPC stub registration is unchanged.
- Run `sharedpackage` and `client` tests. If onboarding is migrated, smoke test Client -> Core with a Keycloak token and the `core.account.open` permission.

## 8. Definition of Done

- A domain service can inject an HTTP client with `@EnableTsbHttpClients`, an interface annotated with `@TsbHttpClient`, and downstream YAML configuration.
- Annotation-based clients use the same protocol interceptor and policies as manually created clients.
- `@TsbOperation` applies correctly with base URL prefixes, path variables, and supported HTTP methods.
- Invalid configuration fails at startup with an error identifying the interface/downstream; requests cannot silently go to the wrong endpoint.
- No additional HTTP client or scanning dependency is introduced when the current Spring stack suffices.
