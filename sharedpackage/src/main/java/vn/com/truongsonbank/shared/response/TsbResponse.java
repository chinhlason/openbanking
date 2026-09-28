package vn.com.truongsonbank.shared.response;

import java.time.Instant;

public record TsbResponse<T>(
        String traceId,
        boolean success,
        String code,
        String message,
        String duration,
        Instant timestamp,
        T data) {

    public static <T> TsbResponse<T> success(String traceId, String duration, T data) {
        return new TsbResponse<>(traceId, true, "SUCCESS", "Success", duration, Instant.now(), data);
    }
}
