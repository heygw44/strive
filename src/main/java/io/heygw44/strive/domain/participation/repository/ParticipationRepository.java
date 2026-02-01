package io.heygw44.strive.domain.participation.repository;

import io.heygw44.strive.domain.participation.entity.Participation;
import io.heygw44.strive.domain.participation.entity.ParticipationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

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
     * 본인 참여 조회 (취소용, PESSIMISTIC_WRITE)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participation p where p.meetupId = :meetupId and p.userId = :userId")
    Optional<Participation> findByMeetupIdAndUserIdForUpdate(
        @Param("meetupId") Long meetupId, @Param("userId") Long userId);

    /**
     * 참여 단건 조회 (PESSIMISTIC_WRITE)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participation p where p.id = :participationId")
    Optional<Participation> findByIdForUpdate(@Param("participationId") Long participationId);

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
