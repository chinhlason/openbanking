package vn.com.truongsonbank.shared.sharding;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

public class TsbShardTemplate {
    private final TsbShardResolver resolver;
    private final TsbShardTableManager tableManager;

    public TsbShardTemplate(TsbShardResolver resolver, TsbShardTableManager tableManager) {
        this.resolver = resolver;
        this.tableManager = tableManager;
    }

    public <T> T execute(Class<?> entityType, Object shardKey, Object timeKey, Supplier<T> supplier) {
        ShardRoute route = resolver.resolve(entityType, shardKey, timeKey);
        tableManager.ensureTable(route);
        return TsbShardContext.runWith(route, supplier);
    }

    public List<String> tablesForRange(Class<?> entityType, LocalDateTime from, LocalDateTime to) {
        return resolver.tablesForRange(entityType, from, to);
    }
}
