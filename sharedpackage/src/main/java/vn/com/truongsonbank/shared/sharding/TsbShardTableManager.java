package vn.com.truongsonbank.shared.sharding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class TsbShardTableManager {
    private static final Logger log = LoggerFactory.getLogger(TsbShardTableManager.class);

    private final ShardingProperties properties;
    private final Map<String, JdbcTemplate> jdbcTemplates;
    private final Set<String> checkedTables = ConcurrentHashMap.newKeySet();

    public TsbShardTableManager(ShardingProperties properties, Map<String, DataSource> datasources) {
        this.properties = properties;
        this.jdbcTemplates = datasources.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> new JdbcTemplate(entry.getValue())));
    }

    public void ensureTable(ShardRoute route) {
        if (!properties.isAutoCreateTable() || route.logicalTable().equals(route.physicalTable())) {
            return;
        }
        String key = route.datasourceKey() + ":" + route.physicalTable();
        if (!checkedTables.add(key)) {
            return;
        }
        JdbcTemplate jdbcTemplate = jdbcTemplates.get(route.datasourceKey());
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Missing datasource for shard: " + route.datasourceKey());
        }
        try {
            Integer count = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema = database() and table_name = ?",
                    Integer.class, route.physicalTable());
            if (count != null && count > 0) {
                return;
            }
            Map<String, Object> ddlRow = jdbcTemplate.queryForMap("show create table `" + route.logicalTable() + "`");
            String ddl = String.valueOf(ddlRow.values().stream()
                    .filter(value -> String.valueOf(value).startsWith("CREATE TABLE"))
                    .findFirst()
                    .orElseThrow());
            String createPhysical = ddl.replaceFirst("(?i)CREATE TABLE `?" + Pattern.quote(route.logicalTable()) + "`?",
                    "CREATE TABLE `" + route.physicalTable() + "`");
            jdbcTemplate.execute(createPhysical);
            log.info("Created sharded table {} on {}", route.physicalTable(), route.datasourceKey());
        } catch (Exception ex) {
            log.warn("Could not auto-create sharded table {} on {}: {}",
                    route.physicalTable(), route.datasourceKey(), ex.getMessage(), ex);
        }
    }
}
