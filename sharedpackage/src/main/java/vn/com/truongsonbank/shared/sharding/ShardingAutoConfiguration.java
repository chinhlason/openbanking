package vn.com.truongsonbank.shared.sharding;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

@AutoConfiguration(beforeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@EnableConfigurationProperties(ShardingProperties.class)
@ConditionalOnProperty(prefix = "tsb.shared.sharding", name = "enabled", havingValue = "true")
public class ShardingAutoConfiguration {
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public DataSource dataSource(ShardingProperties properties,
                                 @Qualifier("tsbShardDatasources") Map<String, DataSource> datasources) {
        TsbShardingDataSource routingDataSource = new TsbShardingDataSource(properties.getDefaultDatasource());
        routingDataSource.setTargetDataSources(new LinkedHashMap<>(datasources));
        routingDataSource.setDefaultTargetDataSource(datasources.get(properties.getDefaultDatasource()));
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Bean
    @ConditionalOnMissingBean(name = "tsbShardDatasources")
    public Map<String, DataSource> tsbShardDatasources(ShardingProperties properties) {
        return shardDatasources(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbShardResolver tsbShardResolver() {
        return new TsbShardResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbShardTableManager tsbShardTableManager(ShardingProperties properties,
                                                     @Qualifier("tsbShardDatasources") Map<String, DataSource> tsbShardDatasources) {
        return new TsbShardTableManager(properties, tsbShardDatasources);
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbShardTemplate tsbShardTemplate(TsbShardResolver resolver, TsbShardTableManager tableManager) {
        return new TsbShardTemplate(resolver, tableManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbReplicatedTemplate tsbReplicatedTemplate(@Qualifier("tsbShardDatasources") Map<String, DataSource> tsbShardDatasources) {
        return new TsbReplicatedTemplate(tsbShardDatasources);
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbShardRepositoryAspect tsbShardRepositoryAspect(TsbShardResolver resolver, TsbShardTableManager tableManager) {
        return new TsbShardRepositoryAspect(resolver, tableManager);
    }

    @Bean
    @ConditionalOnClass(name = "org.hibernate.SessionFactory")
    public HibernatePropertiesCustomizer tsbShardingHibernatePropertiesCustomizer() {
        return new TsbShardingHibernateCustomizer();
    }

    private Map<String, DataSource> shardDatasources(ShardingProperties properties) {
        if (properties.getDatasources().isEmpty()) {
            throw new IllegalStateException("tsb.shared.sharding.datasources is required when sharding is enabled");
        }
        Map<String, DataSource> datasources = new LinkedHashMap<>();
        properties.getDatasources().forEach((name, config) -> {
            DataSourceBuilder<?> builder = DataSourceBuilder.create()
                    .url(config.getUrl())
                    .username(config.getUsername())
                    .password(config.getPassword());
            if (config.getDriverClassName() != null && !config.getDriverClassName().isBlank()) {
                builder.driverClassName(config.getDriverClassName());
            }
            datasources.put(name, builder.build());
        });
        if (!datasources.containsKey(properties.getDefaultDatasource())) {
            throw new IllegalStateException("Default shard datasource not found: " + properties.getDefaultDatasource());
        }
        return datasources;
    }
}
