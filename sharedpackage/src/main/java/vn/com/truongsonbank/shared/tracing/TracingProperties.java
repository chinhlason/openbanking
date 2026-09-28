package vn.com.truongsonbank.shared.tracing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.shared.tracing")
public class TracingProperties {
    private boolean exportEnabled;
    private String serviceName;
    private String otlpEndpoint = "http://localhost:4317";
    private Duration exportTimeout = Duration.ofSeconds(5);
    private List<String> excludePaths = new ArrayList<>(List.of(
            "/actuator/prometheus",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/metrics",
            "/actuator/metrics/**"));

    public boolean isExportEnabled() {
        return exportEnabled;
    }

    public void setExportEnabled(boolean exportEnabled) {
        this.exportEnabled = exportEnabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    public void setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
    }

    public Duration getExportTimeout() {
        return exportTimeout;
    }

    public void setExportTimeout(Duration exportTimeout) {
        this.exportTimeout = exportTimeout;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
    }
}
