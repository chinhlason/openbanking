package vn.com.truongsonbank.shared.sharding;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;

import java.util.Map;

final class TsbShardingHibernateCustomizer implements HibernatePropertiesCustomizer {
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.session_factory.statement_inspector", new TsbShardingStatementInspector());
    }
}
