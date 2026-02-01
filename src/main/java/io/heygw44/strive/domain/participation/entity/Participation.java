package io.heygw44.strive.domain.participation.entity;

import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 참여 엔티티
 */
@Entity
@Table(name = "participation",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_participation_meetup_user",
           columnNames = {"meetup_id", "user_id"}
       ),
       indexes = {
           @Index(name = "idx_participation_meetup_status",
                  columnList = "meetup_id, status")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meetup_id", nullable = false)
    private Long meetupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private Participation(Long meetupId, Long userId) {
        this.meetupId = meetupId;
        this.userId = userId;
        this.status = ParticipationStatus.REQUESTED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 참여 신청 팩토리 메서드
     */
    public static Participation request(Long meetupId, Long userId) {
        return new Participation(meetupId, userId);
    }

    /**
     * 상태 전이
     * PRD 상태 전이 규칙 강제
     */
    private void transitionTo(ParticipationStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_INVALID_STATE);
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 참여 승인 (REQUESTED → APPROVED)
     */
    public void approve() {
        transitionTo(ParticipationStatus.APPROVED);
    }

    /**
     * 참여 거절 (REQUESTED → REJECTED)
     */
    public void reject() {
        transitionTo(ParticipationStatus.REJECTED);
    }

    /**
     * 참여 취소 (REQUESTED/APPROVED → CANCELLED)
     */
    public void cancel() {
        transitionTo(ParticipationStatus.CANCELLED);
    }

    /**
     * 특정 상태인지 확인
     */
    public boolean isStatus(ParticipationStatus status) {
        return this.status == status;
    }

    /**
     * 해당 모임의 참여인지 확인
     */
    public boolean belongsToMeetup(Long meetupId) {
        return this.meetupId.equals(meetupId);
    }
}
