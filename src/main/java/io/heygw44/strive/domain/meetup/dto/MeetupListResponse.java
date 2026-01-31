package io.heygw44.strive.domain.meetup.dto;

import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;

import java.time.LocalDateTime;

/**
 * 모임 목록 응답 DTO (경량화)
 * 목록 조회 시 불필요한 필드 제외
 */
public record MeetupListResponse(
    Long id,
    String title,
    Long categoryId,
    String categoryName,
    String regionCode,
    String regionName,
    String locationText,
    LocalDateTime startAt,
    LocalDateTime recruitEndAt,
    Integer capacity,
    MeetupStatus status,
    LocalDateTime createdAt
) {
    public static MeetupListResponse from(Meetup meetup, String categoryName, String regionName) {
        return new MeetupListResponse(
            meetup.getId(),
            meetup.getTitle(),
            meetup.getCategoryId(),
            categoryName,
            meetup.getRegionCode(),
            regionName,
            meetup.getLocationText(),
            meetup.getStartAt(),
            meetup.getRecruitEndAt(),
            meetup.getCapacity(),
            meetup.getStatus(),
            meetup.getCreatedAt()
        );
    }
}
