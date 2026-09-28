package vn.com.truongsonbank.shared.kafka;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class TsbKafkaOutboxSchema implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TsbKafkaOutboxSchema.class);

    private final ObjectProvider<DataSource> dataSource;
    private final KafkaProperties.Outbox properties;
    private volatile boolean ready;

    TsbKafkaOutboxSchema(ObjectProvider<DataSource> dataSource, KafkaProperties.Outbox properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        DataSource source = dataSource.getIfAvailable();
        if (source == null) {
            log.warn("Kafka outbox is enabled but no DataSource is available");
            return;
        }
        try {
            if (!tableExists(source) && properties.isAutoCreateTable()) {
                new JdbcTemplate(source).execute(createTableSql());
                log.info("Created Kafka outbox table {}", properties.getTableName());
            }
            ready = tableExists(source);
            if (!ready) {
                log.warn("Kafka outbox table {} does not exist", properties.getTableName());
            }
        } catch (Exception ex) {
            ready = false;
            log.warn("Kafka outbox table {} is not ready: {}", properties.getTableName(), ex.getMessage(), ex);
        }
    }

    boolean isReady() {
        return ready;
    }

    private boolean tableExists(DataSource source) throws Exception {
        String table = properties.getTableName();
        try (var connection = source.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return exists(metaData, table) || exists(metaData, table.toUpperCase(Locale.ROOT));
        }
    }

    private boolean exists(DatabaseMetaData metaData, String table) throws Exception {
        try (ResultSet result = metaData.getTables(null, null, table, new String[] { "TABLE" })) {
            return result.next();
        }
    }

    private String createTableSql() {
        return """
                create table %s (
                  id varchar(64) primary key,
                  topic varchar(255) not null,
                  event_key varchar(255),
                  payload text not null,
                  headers text,
                  status varchar(32) not null,
                  retry_count integer not null default 0,
                  next_retry_at timestamp,
                  created_at timestamp not null,
                  sent_at timestamp
                )
                """.formatted(properties.getTableName());
    }
}
