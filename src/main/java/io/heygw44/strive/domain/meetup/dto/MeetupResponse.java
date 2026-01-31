package io.heygw44.strive.domain.meetup.dto;

import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;

import java.time.LocalDateTime;

/**
 * 모임 상세 응답 DTO
 * 주최자 닉네임, 카테고리명, 지역명 포함
 */
public record MeetupResponse(
    Long id,
    Long organizerId,
    String organizerNickname,
    String title,
    String description,
    Long categoryId,
    String categoryName,
    String regionCode,
    String regionName,
    String locationText,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime recruitEndAt,
    Integer capacity,
    MeetupStatus status,
    String experienceLevelText,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static MeetupResponse from(Meetup meetup, String organizerNickname,
                                       String categoryName, String regionName) {
        return new MeetupResponse(
            meetup.getId(),
            meetup.getOrganizerId(),
            organizerNickname,
            meetup.getTitle(),
            meetup.getDescription(),
            meetup.getCategoryId(),
            categoryName,
            meetup.getRegionCode(),
            regionName,
            meetup.getLocationText(),
            meetup.getStartAt(),
            meetup.getEndAt(),
            meetup.getRecruitEndAt(),
            meetup.getCapacity(),
            meetup.getStatus(),
            meetup.getExperienceLevelText(),
            meetup.getCreatedAt(),
            meetup.getUpdatedAt()
        );
    }
}
