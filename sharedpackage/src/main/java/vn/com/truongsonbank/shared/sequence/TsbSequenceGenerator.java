package vn.com.truongsonbank.shared.sequence;

import java.util.List;

public interface TsbSequenceGenerator {
    long next(String name);

    List<Long> nextBatch(String name, int size);
}
