package vn.com.truongsonbank.shared.protocol;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TsbServiceDiscoveryClient {
    private final ProtocolProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    TsbServiceDiscoveryClient(ProtocolProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    }

    public String httpBaseUrl(String serviceId) {
        ServiceInstance instance = instance(serviceId);
        return instance == null ? null : "http://" + instance.host() + ":" + instance.port();
    }

    public String grpcTarget(String serviceId) {
        ServiceInstance instance = instance(serviceId);
        return instance == null ? null : instance.host() + ":" + instance.port();
    }

    private ServiceInstance instance(String serviceId) {
        if (!properties.getDiscovery().isEnabled() || serviceId == null || serviceId.isBlank()) {
            return null;
        }
        List<ServiceInstance> instances = consulInstances(serviceId);
        if (instances.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(counters.computeIfAbsent(serviceId, ignored -> new AtomicInteger()).getAndIncrement(), instances.size());
        return instances.get(index);
    }

    private List<ServiceInstance> consulInstances(String serviceId) {
        if (!"consul".equalsIgnoreCase(properties.getDiscovery().getProvider())) {
            return List.of();
        }
        try {
            String base = properties.getDiscovery().getConsulUrl();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/v1/health/service/" + serviceId + "?passing=true"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            String body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                return List.of();
            }
            return java.util.stream.StreamSupport.stream(root.spliterator(), false)
                    .map(this::service)
                    .filter(instance -> instance != null && instance.port() > 0)
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ServiceInstance service(JsonNode entry) {
        JsonNode service = entry.get("Service");
        if (service == null) {
            return null;
        }
        String address = text(service.get("Address"));
        String nodeAddress = text(entry.get("Node") == null ? null : entry.get("Node").get("Address"));
        String host = address == null || address.isBlank() ? nodeAddress : address;
        int port = service.get("Port") == null ? 0 : service.get("Port").asInt();
        return host == null || host.isBlank() ? null : new ServiceInstance(host, port);
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private record ServiceInstance(String host, int port) {
    }
}
