package io.heygw44.strive.domain.participation.entity;

/**
 * 참가 상태 enum
 * PRD 상태 전이 규칙:
 * - REQUESTED → APPROVED / REJECTED / CANCELLED
 * - APPROVED → CANCELLED
 * - REJECTED → (전이 불가)
 * - CANCELLED → (전이 불가)
 */
public enum ParticipationStatus {
    REQUESTED,  // 신청
    APPROVED,   // 확정
    REJECTED,   // 거절
    CANCELLED;  // 취소

    /**
     * 현재 상태에서 대상 상태로 전이 가능한지 검증
     * @param target 전이 대상 상태
     * @return 전이 가능 여부
     */
    public boolean canTransitionTo(ParticipationStatus target) {
        return switch (this) {
            case REQUESTED -> target == APPROVED || target == REJECTED || target == CANCELLED;
            case APPROVED -> target == CANCELLED;
            case REJECTED, CANCELLED -> false;
        };
    }
}
