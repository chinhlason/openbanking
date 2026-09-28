package vn.com.truongsonbank.shared.sharding;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class TsbShardingDataSource extends AbstractRoutingDataSource {
    private final String defaultDatasource;

    public TsbShardingDataSource(String defaultDatasource) {
        this.defaultDatasource = defaultDatasource;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TsbShardContext.current()
                .map(ShardRoute::datasourceKey)
                .orElse(defaultDatasource);
    }
}
