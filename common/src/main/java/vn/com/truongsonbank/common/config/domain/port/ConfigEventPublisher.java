package vn.com.truongsonbank.common.config.domain.port;

import vn.com.truongsonbank.common.config.domain.model.ConfigPublishEvent;

public interface ConfigEventPublisher {
    void publish(ConfigPublishEvent event);
}
