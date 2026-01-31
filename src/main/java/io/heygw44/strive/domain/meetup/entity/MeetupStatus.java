package io.heygw44.strive.domain.meetup.entity;

/**
 * 모임 상태 enum
 * PRD 상태 전이 규칙:
 * - DRAFT → OPEN
 * - OPEN → CLOSED / CANCELLED
 * - CLOSED → COMPLETED / CANCELLED
 */
public enum MeetupStatus {
    DRAFT,      // 작성중
    OPEN,       // 모집중
    CLOSED,     // 모집마감
    COMPLETED,  // 진행완료
    CANCELLED;  // 취소

    /**
     * 현재 상태에서 대상 상태로 전이 가능한지 검증
     * @param target 전이 대상 상태
     * @return 전이 가능 여부
     */
    public boolean canTransitionTo(MeetupStatus target) {
        return switch (this) {
            case DRAFT -> target == OPEN;
            case OPEN -> target == CLOSED || target == CANCELLED;
            case CLOSED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
