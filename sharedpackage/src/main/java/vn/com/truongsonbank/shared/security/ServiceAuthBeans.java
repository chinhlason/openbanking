package vn.com.truongsonbank.shared.security;

import java.util.concurrent.atomic.AtomicReference;

final class ServiceAuthBeans {
    static final AtomicReference<ServicePermissionResolver> RESOLVER = new AtomicReference<>();

    private ServiceAuthBeans() {
    }
}
