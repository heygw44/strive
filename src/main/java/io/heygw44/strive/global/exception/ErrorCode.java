package io.heygw44.strive.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // Common
    AUTH_UNAUTHORIZED("AUTH-401", "인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN("AUTH-403", "권한이 없습니다", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("REQ-400", "입력값이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("RES-404", "리소스를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // Meetup
    MEETUP_INVALID_STATE("MEETUP-409-STATE", "허용되지 않는 모임 상태입니다", HttpStatus.CONFLICT),
    MEETUP_DEADLINE_PASSED("MEETUP-409-DEADLINE", "모집 마감 이후입니다", HttpStatus.CONFLICT),

    // Participation
    PARTICIPATION_DUPLICATE("PART-409-DUPLICATE", "이미 신청한 모임입니다", HttpStatus.CONFLICT),
    PARTICIPATION_CAPACITY_EXCEEDED("PART-409-CAPACITY", "정원이 초과되었습니다", HttpStatus.CONFLICT),
    PARTICIPATION_INVALID_STATE("PART-409-STATE", "허용되지 않는 참가 상태입니다", HttpStatus.CONFLICT),

    INTERNAL_ERROR("SYS-500", "서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
