package vn.com.truongsonbank.shared.sharding;

import jakarta.persistence.Table;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;

public class TsbShardResolver {
    public ShardRoute resolve(Object entity) {
        Objects.requireNonNull(entity, "entity is required");
        ShardEntity shardEntity = shardEntity(entity.getClass());
        Object shardKey = readAnnotatedField(entity, ShardKey.class, shardEntity.dbStrategy() == ShardStrategy.HASH);
        Object timeKey = readAnnotatedField(entity, ShardTimeKey.class, shardEntity.tbStrategy() == ShardStrategy.TIME);
        return resolve(entity.getClass(), shardKey, timeKey);
    }

    public ShardRoute resolve(Class<?> entityType, Object shardKey, Object timeKey) {
        ShardEntity shardEntity = shardEntity(entityType);
        String logicalTable = logicalTable(entityType);
        int dbIndex = shardEntity.dbStrategy() == ShardStrategy.HASH
                ? positiveHash(required(shardKey, "@ShardKey")) % shardEntity.db()
                : 0;
        String physicalTable = physicalTable(logicalTable, shardEntity, shardKey, timeKey);
        return new ShardRoute("shard-" + dbIndex, dbIndex, logicalTable, physicalTable, entityType);
    }

    public String physicalTable(Class<?> entityType, Object shardKey, Object timeKey) {
        ShardRoute route = resolve(entityType, shardKey, timeKey);
        return route.physicalTable();
    }

    public List<String> tablesForRange(Class<?> entityType, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Valid from/to range is required");
        }
        ShardEntity shardEntity = shardEntity(entityType);
        if (shardEntity.tbStrategy() != ShardStrategy.TIME) {
            return List.of(logicalTable(entityType));
        }
        List<String> tables = new ArrayList<>();
        LocalDateTime cursor = truncate(from, shardEntity.timeUnit());
        LocalDateTime end = truncate(to, shardEntity.timeUnit());
        while (!cursor.isAfter(end)) {
            tables.add(renderTimePattern(shardEntity.tablePattern(), cursor));
            cursor = next(cursor, shardEntity.timeUnit());
        }
        return tables;
    }

    private String physicalTable(String logicalTable, ShardEntity shardEntity, Object shardKey, Object timeKey) {
        if (shardEntity.tbStrategy() == ShardStrategy.TIME) {
            return renderTimePattern(requiredPattern(shardEntity.tablePattern()), toTemporal(required(timeKey, "@ShardTimeKey")));
        }
        if (shardEntity.tbStrategy() == ShardStrategy.HASH) {
            int tableIndex = positiveHash(required(shardKey, "@ShardKey")) % shardEntity.tb();
            return logicalTable + "_" + tableIndex;
        }
        return logicalTable;
    }

    private ShardEntity shardEntity(Class<?> entityType) {
        ShardEntity annotation = entityType.getAnnotation(ShardEntity.class);
        if (annotation == null) {
            throw new IllegalArgumentException(entityType.getName() + " is not annotated with @ShardEntity");
        }
        if (entityType.isAnnotationPresent(ReplicatedEntity.class)) {
            throw new IllegalArgumentException("@ShardEntity cannot be combined with @ReplicatedEntity: " + entityType.getName());
        }
        validateSingleField(entityType, ShardKey.class);
        validateSingleField(entityType, ShardTimeKey.class);
        return annotation;
    }

    private String logicalTable(Class<?> entityType) {
        Table table = entityType.getAnnotation(Table.class);
        if (table == null || table.name().isBlank()) {
            throw new IllegalArgumentException("@Table(name) is required for sharded entity: " + entityType.getName());
        }
        return table.name();
    }

    private void validateSingleField(Class<?> type, Class<? extends java.lang.annotation.Annotation> annotationType) {
        int count = 0;
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(annotationType)) {
                    count++;
                }
            }
            current = current.getSuperclass();
        }
        if (count > 1) {
            throw new IllegalArgumentException("Only one @" + annotationType.getSimpleName() + " is allowed on " + type.getName());
        }
    }

    private Object readAnnotatedField(Object entity, Class<? extends java.lang.annotation.Annotation> annotationType, boolean required) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(annotationType)) {
                    try {
                        field.setAccessible(true);
                        return field.get(entity);
                    } catch (IllegalAccessException ex) {
                        throw new IllegalStateException("Could not read " + field.getName(), ex);
                    }
                }
            }
            current = current.getSuperclass();
        }
        if (required) {
            throw new IllegalArgumentException("Missing @" + annotationType.getSimpleName() + " on " + entity.getClass().getName());
        }
        return null;
    }

    private int positiveHash(Object value) {
        CRC32 crc = new CRC32();
        crc.update(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Math.floorMod((int) crc.getValue(), Integer.MAX_VALUE);
    }

    private Object required(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private String requiredPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("tablePattern is required for TIME table strategy");
        }
        return pattern;
    }

    private TemporalAccessor toTemporal(Object value) {
        if (value instanceof TemporalAccessor temporal) {
            return temporal;
        }
        throw new IllegalArgumentException("@ShardTimeKey must be a java.time value");
    }

    private String renderTimePattern(String pattern, TemporalAccessor value) {
        return pattern
                .replace("${yyyyMMdd}", DateTimeFormatter.ofPattern("yyyyMMdd").format(value))
                .replace("${yyyyMM}", DateTimeFormatter.ofPattern("yyyyMM").format(value))
                .replace("${yyyy}", DateTimeFormatter.ofPattern("yyyy").format(value));
    }

    private LocalDateTime truncate(LocalDateTime value, ShardTimeUnit unit) {
        return switch (unit) {
            case DAY -> value.toLocalDate().atStartOfDay();
            case MONTH -> LocalDateTime.of(value.getYear(), value.getMonth(), 1, 0, 0);
            case YEAR -> LocalDateTime.of(value.getYear(), 1, 1, 0, 0);
        };
    }

    private LocalDateTime next(LocalDateTime value, ShardTimeUnit unit) {
        return switch (unit) {
            case DAY -> value.plusDays(1);
            case MONTH -> value.plusMonths(1);
            case YEAR -> value.plusYears(1);
        };
    }
}
