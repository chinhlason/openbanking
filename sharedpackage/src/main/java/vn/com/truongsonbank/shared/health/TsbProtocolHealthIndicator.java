package vn.com.truongsonbank.shared.health;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import vn.com.truongsonbank.shared.protocol.ProtocolProperties;
import vn.com.truongsonbank.shared.protocol.TsbServiceDiscoveryClient;

class TsbProtocolHealthIndicator extends AbstractHealthIndicator {
    private final ProtocolProperties properties;
    private final TsbServiceDiscoveryClient discoveryClient;
    private final Duration timeout;
    private final HttpClient httpClient;

    TsbProtocolHealthIndicator(
            ProtocolProperties properties,
            TsbServiceDiscoveryClient discoveryClient,
            HealthProperties healthProperties) {
        this.properties = properties;
        this.discoveryClient = discoveryClient;
        this.timeout = healthProperties.getTimeout();
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean up = true;
        for (Map.Entry<String, ProtocolProperties.Downstream> entry : properties.getDownstreams().entrySet()) {
            if (!entry.getValue().isEnabled()) {
                details.put(entry.getKey(), Map.of("status", "disabled"));
                continue;
            }
            DownstreamHealth health = health(entry.getKey(), entry.getValue());
            details.put(entry.getKey(), health.details());
            up = up && health.up();
        }
        if (up) {
            builder.up();
        } else {
            builder.down();
        }
        builder.withDetail("downstreams", details);
    }

    private DownstreamHealth health(String name, ProtocolProperties.Downstream downstream) {
        if (isGrpc(downstream)) {
            String target = target(downstream);
            return tcp(name, target);
        }
        String baseUrl = baseUrl(downstream);
        return http(name, baseUrl);
    }

    private boolean isGrpc(ProtocolProperties.Downstream downstream) {
        return "grpc".equalsIgnoreCase(downstream.getProtocol())
                || (downstream.getTarget() != null && downstream.getBaseUrl() == null);
    }

    private String baseUrl(ProtocolProperties.Downstream downstream) {
        String discovered = discoveryClient == null ? null : discoveryClient.httpBaseUrl(downstream.getServiceId());
        return discovered == null || discovered.isBlank() ? downstream.getBaseUrl() : discovered;
    }

    private String target(ProtocolProperties.Downstream downstream) {
        String discovered = discoveryClient == null ? null : discoveryClient.grpcTarget(downstream.getServiceId());
        return discovered == null || discovered.isBlank() ? downstream.getTarget() : discovered;
    }

    private DownstreamHealth http(String name, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DownstreamHealth.down("missing-base-url");
        }
        if (baseUrl.contains("://localhost") || baseUrl.contains("://127.0.0.1")) {
            return new DownstreamHealth(true, Map.of("type", "http", "url", baseUrl, "check", "skipped-self"));
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/actuator/health"))
                    .timeout(timeout)
                    .GET()
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return new DownstreamHealth(status >= 200 && status < 400,
                    Map.of("type", "http", "url", baseUrl, "status", status));
        } catch (Exception ex) {
            return DownstreamHealth.down(Map.of("type", "http", "url", baseUrl, "error", ex.getClass().getSimpleName()));
        }
    }

    private DownstreamHealth tcp(String name, String target) {
        if (target == null || target.isBlank() || !target.contains(":")) {
            return DownstreamHealth.down("missing-target");
        }
        String host = target.substring(0, target.lastIndexOf(':'));
        int port = Integer.parseInt(target.substring(target.lastIndexOf(':') + 1));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.toIntExact(timeout.toMillis()));
            return new DownstreamHealth(true, Map.of("type", "grpc", "target", target));
        } catch (Exception ex) {
            return DownstreamHealth.down(Map.of("type", "grpc", "target", target, "error", ex.getClass().getSimpleName()));
        }
    }

    private record DownstreamHealth(boolean up, Map<String, Object> details) {
        static DownstreamHealth down(String reason) {
            return down(Map.of("reason", reason));
        }

        static DownstreamHealth down(Map<String, Object> details) {
            return new DownstreamHealth(false, details);
        }
    }
}
