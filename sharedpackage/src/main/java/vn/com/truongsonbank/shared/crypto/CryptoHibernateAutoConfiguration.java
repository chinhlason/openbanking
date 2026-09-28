package vn.com.truongsonbank.shared.crypto;

import org.hibernate.SessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(SessionFactory.class)
@ConditionalOnProperty(prefix = "tsb.shared.crypto", name = "enabled", havingValue = "true")
public class CryptoHibernateAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public HibernatePropertiesCustomizer tsbCryptoHibernatePropertiesCustomizer(TsbCryptoService cryptoService) {
        return new TsbCryptoHibernateCustomizer(cryptoService);
    }
}
