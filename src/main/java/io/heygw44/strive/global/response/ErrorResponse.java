package io.heygw44.strive.global.response;

import io.heygw44.strive.global.exception.ErrorCode;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String traceId,
        List<FieldError> fieldErrors
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                currentTraceId(),
                Collections.emptyList()
        );
    }

    public static ErrorResponse validation(List<FieldError> fieldErrors) {
        return new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.getCode(),
                ErrorCode.VALIDATION_ERROR.getMessage(),
                currentTraceId(),
                fieldErrors
        );
    }

    public static ErrorResponse internal() {
        return from(ErrorCode.INTERNAL_ERROR);
    }

    private static String currentTraceId() {
        return MDC.get("traceId");
    }
}
