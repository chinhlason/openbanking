package vn.com.truongsonbank.shared.sequence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class TsbSelfSequenceGenerator implements TsbSequenceGenerator {
    private static final int MAX_SEQUENCE = 999;

    private final int nodeId;
    private final AtomicInteger counter = new AtomicInteger();

    TsbSelfSequenceGenerator(SequenceProperties properties) {
        this.nodeId = Math.floorMod(properties.getNodeId(), 1000);
    }

    @Override
    public long next(String name) {
        long timestamp = System.currentTimeMillis();
        int sequence = counter.updateAndGet(value -> value >= MAX_SEQUENCE ? 0 : value + 1);
        return timestamp * 1_000_000L + nodeId * 1_000L + sequence;
    }

    @Override
    public List<Long> nextBatch(String name, int size) {
        int count = Math.max(size, 0);
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(next(name));
        }
        return ids;
    }
}
