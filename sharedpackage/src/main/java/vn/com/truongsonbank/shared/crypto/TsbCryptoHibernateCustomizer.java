package vn.com.truongsonbank.shared.crypto;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;

import java.util.List;
import java.util.Map;

final class TsbCryptoHibernateCustomizer implements HibernatePropertiesCustomizer {
    private final TsbCryptoService cryptoService;

    TsbCryptoHibernateCustomizer(TsbCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.integrator_provider", (IntegratorProvider) () -> List.of(new CryptoIntegrator()));
    }

    private final class CryptoIntegrator implements Integrator {
        @Override
        public void integrate(Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sessionFactory) {
            EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
            TsbCryptoHibernateEventListener listener = new TsbCryptoHibernateEventListener(cryptoService);
            registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listener);
            registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listener);
            registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(listener);
        }

        @Override
        public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        }
    }
}
