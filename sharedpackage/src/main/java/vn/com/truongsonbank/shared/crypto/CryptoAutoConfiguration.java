package vn.com.truongsonbank.shared.crypto;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
@ConditionalOnProperty(prefix = "tsb.shared.crypto", name = "enabled", havingValue = "true")
public class CryptoAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public TsbCryptoService tsbCryptoService(CryptoProperties properties) {
        TsbCryptoService service = new DefaultTsbCryptoService(properties);
        TsbCryptoEntityListener.setCryptoService(service);
        return service;
    }
}
