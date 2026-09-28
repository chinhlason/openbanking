package vn.com.truongsonbank.shared.kafka;

import java.util.HashMap;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@EnableKafka
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "tsb.shared.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    KafkaInstrumentation tsbKafkaInstrumentation(MeterRegistry meterRegistry) {
        return new KafkaInstrumentation(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    ProducerFactory<String, Object> tsbKafkaProducerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, properties.getClientId());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        applySecurity(config, properties);
        if (properties.getTransaction().isEnabled()) {
            config.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, properties.getTransaction().getTransactionIdPrefix() + properties.getClientId());
        }
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    @ConditionalOnMissingBean
    KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    ConsumerFactory<String, Object> tsbKafkaConsumerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.getConsumer().getAutoOffsetReset());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, properties.getTrustedPackages());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Map.class.getName());
        applySecurity(config, properties);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    @ConditionalOnMissingBean
    CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaProperties properties,
            KafkaInstrumentation instrumentation) {
        TsbKafkaDeadLetterPublishingRecoverer recoverer = new TsbKafkaDeadLetterPublishingRecoverer(kafkaTemplate,
                (record, error) -> new TopicPartition(record.topic() + properties.getDlq().getSuffix(), record.partition()),
                instrumentation);
        FixedBackOff backOff = new FixedBackOff(
                properties.getRetry().getBackoff().toMillis(),
                Math.max(properties.getRetry().getAttempts() - 1, 0));
        DefaultErrorHandler handler = properties.getDlq().isEnabled()
                ? new DefaultErrorHandler(recoverer, backOff)
                : new DefaultErrorHandler(backOff);
        handler.setRetryListeners((record, exception, deliveryAttempt) ->
                instrumentation.count("consume.retry", record.topic(), "retry",
                        exception == null ? "none" : exception.getClass().getSimpleName()));
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler,
            TsbKafkaRecordInterceptor recordInterceptor) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setRecordInterceptor(recordInterceptor);
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    TsbKafkaRecordInterceptor tsbKafkaRecordInterceptor(
            OpenTelemetry openTelemetry,
            KafkaInstrumentation instrumentation) {
        return new TsbKafkaRecordInterceptor(openTelemetry, instrumentation);
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbKafkaPublisher tsbKafkaPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaInstrumentation instrumentation,
            ObjectProvider<Tracer> tracer) {
        return new TsbKafkaPublisher(kafkaTemplate, instrumentation, tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbKafkaDlqReplay tsbKafkaDlqReplay(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaProperties properties,
            KafkaInstrumentation instrumentation) {
        return new TsbKafkaDlqReplay(consumerFactory, kafkaTemplate, properties.getDlq(), instrumentation);
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbKafkaDlqStatus tsbKafkaDlqStatus(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaProperties properties) {
        return new TsbKafkaDlqStatus(consumerFactory, properties.getDlq());
    }

    @Bean
    @ConditionalOnMissingBean
    public TsbKafkaDlqMonitor tsbKafkaDlqMonitor(
            TsbKafkaDlqStatus status,
            KafkaInstrumentation instrumentation,
            KafkaProperties properties) {
        return new TsbKafkaDlqMonitor(status, instrumentation, properties.getDlq());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    TsbKafkaIdempotentAspect tsbKafkaIdempotentAspect(
            StringRedisTemplate redis,
            KafkaInstrumentation instrumentation) {
        return new TsbKafkaIdempotentAspect(redis, instrumentation);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "tsb.shared.kafka.outbox", name = "enabled", havingValue = "true")
    TsbKafkaOutboxSchema tsbKafkaOutboxSchema(
            ObjectProvider<DataSource> dataSource,
            KafkaProperties properties) {
        return new TsbKafkaOutboxSchema(dataSource, properties.getOutbox());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "tsb.shared.kafka.outbox", name = "enabled", havingValue = "true")
    public TsbKafkaOutbox tsbKafkaOutbox(
            ObjectProvider<DataSource> dataSource,
            ObjectProvider<ObjectMapper> objectMapper,
            KafkaProperties properties,
            TsbKafkaOutboxSchema schema,
            ObjectProvider<Tracer> tracer) {
        return new TsbKafkaOutbox(dataSource, objectMapper.getIfAvailable(ObjectMapper::new),
                properties.getOutbox(), schema, tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "tsb.shared.kafka.outbox", name = "enabled", havingValue = "true")
    TsbKafkaOutboxRelayWorker tsbKafkaOutboxRelayWorker(
            ObjectProvider<DataSource> dataSource,
            ObjectProvider<ObjectMapper> objectMapper,
            KafkaProperties properties,
            TsbKafkaOutboxSchema schema,
            TsbKafkaPublisher publisher,
            KafkaInstrumentation instrumentation) {
        return new TsbKafkaOutboxRelayWorker(dataSource, objectMapper.getIfAvailable(ObjectMapper::new),
                properties.getOutbox(), schema, publisher, instrumentation);
    }

    private static void applySecurity(Map<String, Object> config, KafkaProperties properties) {
        if (properties.getSecurity().isEnabled()) {
            config.putAll(properties.getSecurity().getProperties());
        }
    }
}
