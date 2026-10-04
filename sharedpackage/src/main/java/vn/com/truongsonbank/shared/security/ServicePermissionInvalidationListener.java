package vn.com.truongsonbank.shared.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

final class ServicePermissionInvalidationListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(ServicePermissionInvalidationListener.class);
    private final ObjectMapper objectMapper;
    private final ServicePermissionResolver resolver;

    ServicePermissionInvalidationListener(ObjectMapper objectMapper, ServicePermissionResolver resolver) {
        this.objectMapper = objectMapper;
        this.resolver = resolver;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode event = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            String scope = event.path("scope").asText("ALL");
            if ("SUBJECT".equalsIgnoreCase(scope)) {
                String subject = event.path("subjectId").asText(null);
                if (subject != null && !subject.isBlank()) resolver.evict(subject);
            } else {
                resolver.evictAll();
            }
        } catch (Exception exception) {
            log.warn("Unable to process service permission invalidation event: {}", exception.getMessage());
        }
    }
}
