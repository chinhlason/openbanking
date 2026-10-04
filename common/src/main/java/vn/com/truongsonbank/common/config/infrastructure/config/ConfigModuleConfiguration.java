package vn.com.truongsonbank.common.config.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.com.truongsonbank.common.config.domain.port.ConfigRepository;

@Configuration
@EnableConfigurationProperties(ConfigServerProperties.class)
public class ConfigModuleConfiguration {
    private final ConfigServerProperties properties;
    private final ConfigRepository repository;

    public ConfigModuleConfiguration(ConfigServerProperties properties, ConfigRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @PostConstruct
    void bootstrapClients() {
        properties.getBootstrapClients().forEach(repository::ensureClient);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
