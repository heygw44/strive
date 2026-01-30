package io.heygw44.strive.domain.user.dto;

public record LoginResponse(
        Long id,
        String email,
        String nickname,
        boolean isVerified
) {
}
