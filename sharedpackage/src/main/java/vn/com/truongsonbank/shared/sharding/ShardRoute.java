package vn.com.truongsonbank.shared.sharding;

public record ShardRoute(
        String datasourceKey,
        int databaseShard,
        String logicalTable,
        String physicalTable,
        Class<?> entityType
) {
}
