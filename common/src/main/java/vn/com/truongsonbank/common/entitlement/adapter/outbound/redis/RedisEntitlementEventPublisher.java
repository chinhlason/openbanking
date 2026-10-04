package vn.com.truongsonbank.common.entitlement.adapter.outbound.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.com.truongsonbank.common.entitlement.domain.model.EntitlementChangeEvent;
import vn.com.truongsonbank.common.entitlement.domain.port.EntitlementEventPublisher;
import vn.com.truongsonbank.common.entitlement.infrastructure.config.EntitlementProperties;

@Component
public class RedisEntitlementEventPublisher implements EntitlementEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisEntitlementEventPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final EntitlementProperties properties;

    public RedisEntitlementEventPublisher(StringRedisTemplate redisTemplate,
                                          EntitlementProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(EntitlementChangeEvent event) {
        Runnable publish = () -> publishNow(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
    }

    private void publishNow(EntitlementChangeEvent event) {
        try {
            redisTemplate.convertAndSend(properties.getRedisChannel(), json(event));
        } catch (Exception ex) {
            log.warn("Could not publish entitlement change event type={}: {}", event.changeType(), ex.getMessage());
        }
    }

    private String json(EntitlementChangeEvent event) {
        return "{" +
                "\"eventId\":" + quote(event.eventId()) + "," +
                "\"changeType\":" + quote(event.changeType()) + "," +
                "\"scope\":" + quote(event.scope()) + "," +
                "\"packageCode\":" + nullable(event.packageCode()) + "," +
                "\"subjectId\":" + nullable(event.subjectId()) + "," +
                "\"occurredAt\":" + quote(event.occurredAt().toString()) +
                "}";
    }

    private String nullable(String value) {
        return value == null ? "null" : quote(value);
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
