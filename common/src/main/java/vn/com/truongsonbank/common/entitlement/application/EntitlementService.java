package vn.com.truongsonbank.common.entitlement.application;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence.EntitlementSnapshotEntity;
import vn.com.truongsonbank.common.entitlement.adapter.outbound.persistence.EntitlementSnapshotRepository;
import vn.com.truongsonbank.common.entitlement.domain.EntitlementErrors;
import vn.com.truongsonbank.shared.exception.TsbException;

@Service
public class EntitlementService {
    private final EntitlementSnapshotRepository repository;

    public EntitlementService(EntitlementSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String type, String id) {
        EntitlementSnapshotEntity snapshot = repository.findBySubjectTypeAndSubjectId(normalize(type), id)
                .orElseThrow(() -> new TsbException(EntitlementErrors.NOT_FOUND));
        return view(snapshot);
    }

    @Transactional
    public Map<String, Object> upsert(String type, String id, List<String> allow, List<String> deny,
                                      Long version, Instant expiresAt) {
        String subjectType = normalize(type);
        if (id == null || id.isBlank() || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new TsbException(EntitlementErrors.REQUEST_INVALID);
        }
        EntitlementSnapshotEntity snapshot = repository.findBySubjectTypeAndSubjectId(subjectType, id).orElse(null);
        if (snapshot == null) {
            snapshot = new EntitlementSnapshotEntity(subjectType, id, csv(allow), csv(deny), version == null ? 1 : version, expiresAt);
        } else {
            long nextVersion = version == null ? snapshot.getVersion() + 1 : version;
            if (nextVersion <= snapshot.getVersion()) {
                throw new TsbException(EntitlementErrors.VERSION_STALE);
            }
            snapshot.update(csv(allow), csv(deny), nextVersion, expiresAt);
        }
        return view(repository.save(snapshot));
    }

    public boolean allowed(Map<String, Object> snapshot, String operation) {
        if (snapshot == null || operation == null) return false;
        List<String> deny = values(snapshot.get("deny"));
        if (deny.contains(operation)) return false;
        return values(snapshot.get("allow")).contains(operation)
                && Instant.parse(String.valueOf(snapshot.get("expiresAt"))).isAfter(Instant.now());
    }

    private Map<String, Object> view(EntitlementSnapshotEntity snapshot) {
        return Map.of("subjectType", snapshot.getSubjectType(), "subjectId", snapshot.getSubjectId(),
                "version", snapshot.getVersion(), "allow", values(snapshot.getAllowOperations()),
                "deny", values(snapshot.getDenyOperations()), "expiresAt", snapshot.getExpiresAt());
    }

    private String csv(List<String> values) {
        return values == null ? "" : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().collect(Collectors.joining(","));
    }

    private List<String> values(Object raw) {
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : values(String.valueOf(raw));
    }

    private List<String> values(String raw) {
        return raw == null || raw.isBlank() ? List.of() : Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
