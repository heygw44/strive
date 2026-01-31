package io.heygw44.strive.domain.meetup.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/**
 * 모임 생성 요청 DTO
 */
public record CreateMeetupRequest(
    @NotBlank(message = "모임 제목을 입력해주세요")
    @Size(max = 100, message = "모임 제목은 100자를 초과할 수 없습니다")
    String title,

    @Size(max = 2000, message = "모임 설명은 2000자를 초과할 수 없습니다")
    String description,

    @NotNull(message = "카테고리를 선택해주세요")
    Long categoryId,

    @NotBlank(message = "지역을 선택해주세요")
    @Size(max = 50, message = "지역 코드는 50자를 초과할 수 없습니다")
    String regionCode,

    @NotBlank(message = "만날 장소를 입력해주세요")
    @Size(max = 500, message = "장소 설명은 500자를 초과할 수 없습니다")
    String locationText,

    @NotNull(message = "모임 시작 시간을 입력해주세요")
    @Future(message = "모임 시작 시간은 현재 시간 이후여야 합니다")
    LocalDateTime startAt,

    @NotNull(message = "모임 종료 시간을 입력해주세요")
    @Future(message = "모임 종료 시간은 시작 시간 이후여야 합니다")
    LocalDateTime endAt,

    @NotNull(message = "모집 마감 시간을 입력해주세요")
    @Future(message = "모집 마감 시간은 현재 시간 이후여야 합니다")
    LocalDateTime recruitEndAt,

    @NotNull(message = "모임 정원을 입력해주세요")
    @Min(value = 2, message = "모임 정원은 최소 2명 이상이어야 합니다")
    @Max(value = 100, message = "모임 정원은 최대 100명까지 가능합니다")
    Integer capacity,

    @Size(max = 200, message = "실력/경험 안내는 200자를 초과할 수 없습니다")
    String experienceLevelText
) {}