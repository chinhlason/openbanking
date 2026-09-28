package vn.com.truongsonbank.client2;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import vn.com.truongsonbank.shared.response.ResponseWrapper;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
class Client2EchoController {
    private final Map<String, AtomicInteger> flakyCalls = new ConcurrentHashMap<>();

    @ResponseWrapper
    @GetMapping("/client2/echo/{id}")
    Map<String, Object> echo(
            @PathVariable String id,
            @RequestHeader(value = "traceparent", required = false) String traceparent,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Map.of(
                "service", "client2",
                "id", id,
                "incomingTraceparent", traceparent == null ? "" : traceparent,
                "incomingIdempotencyKey", idempotencyKey == null ? "" : idempotencyKey,
                "timestamp", Instant.now().toString());
    }

    @GetMapping("/client2/fail/{status}")
    ResponseEntity<Map<String, Object>> fail(@PathVariable int status) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (status == 429) {
            builder.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return builder.body(Map.of("service", "client2", "status", status));
    }

    @GetMapping("/client2/slow/{millis}")
    Map<String, Object> slow(@PathVariable long millis) throws InterruptedException {
        Thread.sleep(millis);
        return Map.of("service", "client2", "sleptMs", millis);
    }

    @GetMapping("/client2/flaky/{key}")
    ResponseEntity<Map<String, Object>> flaky(@PathVariable String key) {
        int attempt = flakyCalls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (attempt <= 2) {
            return ResponseEntity.status(503).body(Map.of("service", "client2", "attempt", attempt));
        }
        return ResponseEntity.ok(Map.of("service", "client2", "attempt", attempt));
    }
}
