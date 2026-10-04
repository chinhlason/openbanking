package vn.com.truongsonbank.bff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
class EntitlementCacheInvalidationConfiguration {
    @Bean
    RedisMessageListenerContainer entitlementRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            EntitlementCacheInvalidationListener listener,
            @Value("${tsb.bff.entitlement.redis-channel:tsb:entitlement:changed}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(channel));
        return container;
    }
}
