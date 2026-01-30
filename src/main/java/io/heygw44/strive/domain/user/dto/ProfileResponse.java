package io.heygw44.strive.domain.user.dto;

import io.heygw44.strive.domain.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public record ProfileResponse(
        Long id,
        String email,
        String nickname,
        String bioText,
        List<String> preferredCategories,
        String homeRegionCode,
        String experienceLevel,
        boolean isVerified,
        LocalDateTime createdAt
) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getBioText(),
                user.getPreferredCategories(),
                user.getHomeRegionCode(),
                user.getExperienceLevel(),
                user.isVerified(),
                user.getCreatedAt()
        );
    }
}
