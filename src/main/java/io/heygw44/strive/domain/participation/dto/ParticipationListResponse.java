package io.heygw44.strive.domain.participation.dto;

import java.util.List;

/**
 * 참여 목록 응답 DTO (주최자용)
 */
public record ParticipationListResponse(
    List<ParticipationResponse> participations,
    long totalCount,
    long approvedCount,
    int capacity
) {
}
