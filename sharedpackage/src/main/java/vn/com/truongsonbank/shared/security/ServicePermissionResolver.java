package vn.com.truongsonbank.shared.security;

import java.util.Collection;

public interface ServicePermissionResolver {
    ServicePermissions resolve(String serviceCode);

    default void evict(String serviceCode) { }

    default void evictAll() { }

    record ServicePermissions(Collection<String> allow, Collection<String> deny, long version) {
        public ServicePermissions {
            allow = allow == null ? java.util.List.of() : java.util.List.copyOf(allow);
            deny = deny == null ? java.util.List.of() : java.util.List.copyOf(deny);
        }

        public boolean matches(String[] required, MatchMode mode) {
            java.util.Set<String> denied = java.util.Set.copyOf(deny);
            java.util.Set<String> allowed = java.util.Set.copyOf(allow);
            if (required == null || required.length == 0) return true;
            if (mode == MatchMode.ALL) {
                for (String operation : required) {
                    if (denied.contains(operation) || !allowed.contains(operation)) return false;
                }
                return true;
            }
            for (String operation : required) {
                if (!denied.contains(operation) && allowed.contains(operation)) return true;
            }
            return false;
        }
    }
}
