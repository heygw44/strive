package io.heygw44.strive.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // Common
    AUTH_UNAUTHORIZED("AUTH-401", "인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN("AUTH-403", "권한이 없습니다", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("REQ-400", "입력값이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("RES-404", "리소스를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // Auth
    INVALID_CREDENTIALS("AUTH-401-CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다", HttpStatus.UNAUTHORIZED),
    DUPLICATE_EMAIL("AUTH-409-EMAIL", "이미 사용 중인 이메일입니다", HttpStatus.CONFLICT),
    DUPLICATE_NICKNAME("AUTH-409-NICKNAME", "이미 사용 중인 닉네임입니다", HttpStatus.CONFLICT),
    INVALID_PASSWORD_LENGTH("AUTH-400-PASSWORD", "비밀번호는 10자 이상이어야 합니다", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_VERIFIED("AUTH-409-VERIFIED", "이미 인증된 이메일입니다", HttpStatus.CONFLICT),
    VERIFICATION_TOKEN_INVALID("AUTH-400-TOKEN", "유효하지 않거나 만료된 인증 토큰입니다", HttpStatus.BAD_REQUEST),

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
