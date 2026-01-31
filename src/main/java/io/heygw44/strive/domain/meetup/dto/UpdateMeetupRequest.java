package io.heygw44.strive.domain.meetup.dto;

import io.heygw44.strive.domain.meetup.entity.MeetupStatus;
import jakarta.validation.constraints.Size;

/**
 * 모임 수정 요청 DTO
 * 상태 전이도 이 DTO로 요청 가능
 */
public record UpdateMeetupRequest(
    @Size(max = 100, message = "제목은 100자 이내여야 합니다")
    String title,

    @Size(max = 2000, message = "설명은 2000자 이내여야 합니다")
    String description,

    @Size(max = 500, message = "장소 설명은 500자 이내여야 합니다")
    String locationText,

    @Size(max = 200, message = "실력/경험 안내는 200자 이내여야 합니다")
    String experienceLevelText,

    MeetupStatus status
) {}
