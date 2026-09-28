package vn.com.truongsonbank.shared.sharding;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.function.Consumer;

public class TsbReplicatedTemplate {
    private final Map<String, JdbcTemplate> jdbcTemplates;

    public TsbReplicatedTemplate(Map<String, DataSource> datasources) {
        this.jdbcTemplates = datasources.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> new JdbcTemplate(entry.getValue())));
    }

    public void executeOnAll(Consumer<JdbcTemplate> callback) {
        jdbcTemplates.values().forEach(callback);
    }
}
