package vn.com.truongsonbank.bff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
class EntitlementCacheInvalidationListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(EntitlementCacheInvalidationListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthSessionForwardingFilter forwardingFilter;

    EntitlementCacheInvalidationListener(AuthSessionForwardingFilter forwardingFilter) {
        this.forwardingFilter = forwardingFilter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        log.info("Consumed entitlement change event channel={} payload={}", channel, payload);
        try {
            JsonNode event = objectMapper.readTree(payload);
            String scope = event.path("scope").asText("ALL");
            String packageCode = event.path("packageCode").asText(null);
            forwardingFilter.invalidateEntitlementCache(scope, packageCode);
            String action = "SUBJECT".equalsIgnoreCase(scope) ? "ignored-until-next-login" : "invalidated";
            log.info("Applied entitlement cache event eventId={} scope={} packageCode={} changeType={} action={}",
                    event.path("eventId").asText("UNKNOWN"), scope, packageCode,
                    event.path("changeType").asText("UNKNOWN"), action);
        } catch (Exception ex) {
            log.warn("Invalid entitlement cache invalidation event: {}", ex.getMessage());
        }
    }
}
