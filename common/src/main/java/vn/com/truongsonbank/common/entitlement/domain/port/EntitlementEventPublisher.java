package vn.com.truongsonbank.common.entitlement.domain.port;

import vn.com.truongsonbank.common.entitlement.domain.model.EntitlementChangeEvent;

public interface EntitlementEventPublisher {
    void publish(EntitlementChangeEvent event);
}
