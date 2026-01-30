package io.heygw44.strive.domain.user.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfileUpdateRequest(
        @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다")
        String nickname,

        @Size(max = 500, message = "자기소개는 500자 이하여야 합니다")
        String bioText,

        List<String> preferredCategories,

        @Size(max = 50, message = "지역 코드는 50자 이하여야 합니다")
        String homeRegionCode,

        @Size(max = 20, message = "경험 레벨은 20자 이하여야 합니다")
        String experienceLevel
) {
}
