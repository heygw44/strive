package io.heygw44.strive.domain.user.dto;

public record SignupResponse(
        Long id,
        String email,
        String nickname
) {
}
