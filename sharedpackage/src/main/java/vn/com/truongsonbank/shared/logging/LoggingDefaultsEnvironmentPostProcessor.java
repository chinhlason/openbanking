package vn.com.truongsonbank.shared.logging;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class LoggingDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "tsbSharedLoggingDefaults";
    private static final String CONSOLE_PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level "
            + "app=${spring.application.name:app} traceId=%X{traceId:-} spanId=%X{spanId:-} "
            + "[%thread] %logger{36} - %msg%n${logging.exception-conversion-word:%wEx}";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.ofEntries(
                Map.entry("logging.pattern.console", CONSOLE_PATTERN),
                Map.entry("logging.exception-conversion-word", "%ex{full}"),
                Map.entry("logging.level.root", "INFO"),
                Map.entry("logging.level.org.apache.kafka.common.config.AbstractConfig", "ERROR"),
                Map.entry("logging.level.org.apache.kafka.common.metrics.Metrics", "ERROR"),
                Map.entry("logging.level.org.apache.kafka.common.telemetry.internals", "ERROR"),
                Map.entry("logging.level.org.apache.kafka.common.utils.AppInfoParser", "ERROR"),
                Map.entry("logging.level.org.apache.kafka.clients.Metadata", "ERROR"),
                Map.entry("logging.level.org.apache.kafka.clients.NetworkClient", "ERROR"),
                Map.entry("logging.level.org.apache.kafka.clients.consumer.internals", "ERROR"),
                Map.entry("logging.level.org.apache.kafka.clients.consumer.ConsumerConfig", "ERROR"),
                Map.entry("logging.level.org.apache.kafka.clients.admin.AdminClientConfig", "ERROR")
        )));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
