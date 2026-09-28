package vn.com.truongsonbank.shared.sequence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;

class TsbRedisSequenceGenerator implements TsbSequenceGenerator {
    private final SequenceProperties properties;
    private final StringRedisTemplate redis;

    TsbRedisSequenceGenerator(SequenceProperties properties, StringRedisTemplate redis) {
        this.properties = properties;
        this.redis = redis;
    }

    @Override
    public long next(String name) {
        Long value = redis.opsForValue().increment(key(name));
        if (value == null) {
            throw new IllegalStateException("Redis sequence increment returned null");
        }
        return value;
    }

    @Override
    public List<Long> nextBatch(String name, int size) {
        int count = Math.max(size, 0);
        if (count == 0) {
            return List.of();
        }
        Long end = redis.opsForValue().increment(key(name), count);
        if (end == null) {
            throw new IllegalStateException("Redis sequence increment returned null");
        }
        long start = end - count + 1;
        List<Long> ids = new ArrayList<>(count);
        for (long id = start; id <= end; id++) {
            ids.add(id);
        }
        return ids;
    }

    private String key(String name) {
        return properties.getKeyPrefix() + ":" + name;
    }
}
