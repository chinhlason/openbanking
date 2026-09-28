package vn.com.truongsonbank.shared.protocol;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

class TsbConsulServiceRegistrar implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(TsbConsulServiceRegistrar.class);

    private final ProtocolProperties properties;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final List<String> registeredIds = new ArrayList<>();

    TsbConsulServiceRegistrar(ProtocolProperties properties, Environment environment, ObjectMapper objectMapper) {
        this.properties = properties;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ProtocolProperties.Discovery discovery = properties.getDiscovery();
        ProtocolProperties.Registration registration = discovery.getRegistration();
        if (!properties.isEnabled() || !discovery.isEnabled() || !registration.isEnabled()
                || !"consul".equalsIgnoreCase(discovery.getProvider())) {
            return;
        }
        registerHttp(registration);
        if (registration.getGrpc().isEnabled()) {
            registerGrpc(registration);
        }
    }

    @Override
    public void destroy() {
        for (String id : registeredIds) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(consulUrl() + "/v1/agent/service/deregister/" + id))
                        .timeout(Duration.ofSeconds(2))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ex) {
                log.debug("Consul deregister failed for {}: {}", id, ex.getMessage());
            }
        }
    }

    private void registerHttp(ProtocolProperties.Registration registration) {
        int port = registration.getPort() > 0 ? registration.getPort() : environment.getProperty("server.port", Integer.class, 8080);
        String name = value(registration.getServiceName(), environment.getProperty("spring.application.name", "app"));
        String id = value(registration.getServiceId(), name + "-http");
        String address = address(registration.getAddress());
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("HTTP", "http://" + address + ":" + port + registration.getHealthPath());
        check.put("Interval", registration.getInterval());
        check.put("Timeout", registration.getTimeout());
        register(id, name, address, port, List.of(check), Map.of("protocol", "http"));
    }

    private void registerGrpc(ProtocolProperties.Registration registration) {
        ProtocolProperties.Grpc grpc = registration.getGrpc();
        if (grpc.getPort() <= 0) {
            throw new IllegalArgumentException("gRPC Consul registration requires grpc.port");
        }
        String baseName = value(registration.getServiceName(), environment.getProperty("spring.application.name", "app"));
        String name = value(grpc.getServiceName(), baseName + "-grpc");
        String id = value(grpc.getServiceId(), name);
        String address = address(registration.getAddress());
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("TCP", address + ":" + grpc.getPort());
        check.put("Interval", registration.getInterval());
        check.put("Timeout", registration.getTimeout());
        register(id, name, address, grpc.getPort(), List.of(check), Map.of("protocol", "grpc"));
    }

    private void register(String id, String name, String address, int port, List<Map<String, Object>> checks, Map<String, String> meta) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ID", id);
            body.put("Name", name);
            body.put("Address", address);
            body.put("Port", port);
            body.put("Meta", meta);
            body.put("Checks", checks);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(consulUrl() + "/v1/agent/service/register"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                registeredIds.add(id);
                log.info("Registered service {} to Consul as {}:{} ({})", name, address, port, id);
            } else {
                log.warn("Consul register failed for {} with HTTP {}", id, status);
            }
        } catch (Exception ex) {
            log.warn("Consul register failed for {}: {}", id, ex.getMessage());
        }
    }

    private String consulUrl() {
        return properties.getDiscovery().getConsulUrl();
    }

    private String address(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String hostname = environment.getProperty("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "localhost";
        }
    }

    private String value(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured;
    }
}
