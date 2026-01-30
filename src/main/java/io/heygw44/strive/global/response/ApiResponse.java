package io.heygw44.strive.global.response;

import org.slf4j.MDC;

public record ApiResponse<T>(T data, String traceId) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, currentTraceId());
    }

    private static String currentTraceId() {
        return MDC.get("traceId");
    }
}
