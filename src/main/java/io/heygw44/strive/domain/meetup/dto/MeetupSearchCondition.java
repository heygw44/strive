package io.heygw44.strive.domain.meetup.dto;

import io.heygw44.strive.domain.meetup.entity.MeetupStatus;

import java.time.LocalDateTime;

/**
 * 모임 검색 조건 DTO
 * 정렬: startAt(기본, 가까운 일정순) / createdAt(최신순)
 */
public record MeetupSearchCondition(
    String regionCode,
    Long categoryId,
    MeetupStatus status,
    LocalDateTime startFrom,
    LocalDateTime startTo,
    String sort
) {
    /**
     * 기본값 적용 생성자
     * status 미지정 시 OPEN으로 기본 설정
     * sort 미지정 시 startAt으로 기본 설정
     */
    public MeetupSearchCondition {
        if (status == null) {
            status = MeetupStatus.OPEN;
        }
        if (sort == null || sort.isBlank()) {
            sort = "startAt";
        }
    }

    /**
     * 기본 검색 조건 (OPEN 모임, 가까운 일정순)
     */
    public static MeetupSearchCondition defaultCondition() {
        return new MeetupSearchCondition(null, null, MeetupStatus.OPEN, null, null, "startAt");
    }
}
