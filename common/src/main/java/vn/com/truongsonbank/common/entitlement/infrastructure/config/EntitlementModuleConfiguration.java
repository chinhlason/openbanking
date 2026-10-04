package vn.com.truongsonbank.common.entitlement.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EntitlementProperties.class)
public class EntitlementModuleConfiguration {
}
