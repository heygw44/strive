package io.heygw44.strive.domain.participation.repository;

import io.heygw44.strive.domain.participation.entity.Participation;
import io.heygw44.strive.domain.participation.entity.ParticipationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    /**
     * 중복 신청 확인 (AC-PART-01)
     */
    boolean existsByMeetupIdAndUserId(Long meetupId, Long userId);

    /**
     * 본인 참여 조회 (취소용)
     */
    Optional<Participation> findByMeetupIdAndUserId(Long meetupId, Long userId);

    /**
     * APPROVED 카운트 (정원 체크, AC-PART-02)
     */
    long countByMeetupIdAndStatus(Long meetupId, ParticipationStatus status);

    /**
     * 모임별 참여 목록 (주최자용)
     */
    List<Participation> findByMeetupIdOrderByCreatedAtAsc(Long meetupId);

    /**
     * 특정 상태 참여 목록
     */
    List<Participation> findByMeetupIdAndStatusOrderByCreatedAtAsc(
        Long meetupId, ParticipationStatus status);
}
