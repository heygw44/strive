package io.heygw44.strive.domain.participation.dto;

import io.heygw44.strive.domain.participation.entity.Participation;
import io.heygw44.strive.domain.participation.entity.ParticipationStatus;

import java.time.LocalDateTime;

/**
 * 참여 응답 DTO
 */
public record ParticipationResponse(
    Long id,
    Long meetupId,
    Long userId,
    String userNickname,
    ParticipationStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ParticipationResponse from(Participation participation, String userNickname) {
        return new ParticipationResponse(
            participation.getId(),
            participation.getMeetupId(),
            participation.getUserId(),
            userNickname,
            participation.getStatus(),
            participation.getCreatedAt(),
            participation.getUpdatedAt()
        );
    }

    public static ParticipationResponse from(Participation participation) {
        return from(participation, null);
    }
}
