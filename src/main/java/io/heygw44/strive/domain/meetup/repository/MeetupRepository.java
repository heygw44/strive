package io.heygw44.strive.domain.meetup.repository;

import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {

    /**
     * 기본 조회 (삭제되지 않은 모임)
     */
    Optional<Meetup> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 비관적 락 조회 (M3/M4 동시성 제어용)
     * 타임아웃 3초로 설정하여 데드락 방지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT m FROM Meetup m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<Meetup> findByIdForUpdate(@Param("id") Long id);

    /**
     * 목록 조회 (필터링 + 페이징)
     * 인덱스: idx_meetup_list (region_code, category_id, status, start_at)
     */
    @Query("""
        SELECT m FROM Meetup m
        WHERE m.deletedAt IS NULL
        AND (:regionCode IS NULL OR m.regionCode = :regionCode)
        AND (:categoryId IS NULL OR m.categoryId = :categoryId)
        AND (:status IS NULL OR m.status = :status)
        AND (:startFrom IS NULL OR m.startAt >= :startFrom)
        AND (:startTo IS NULL OR m.startAt <= :startTo)
        """)
    Page<Meetup> findByFilters(
        @Param("regionCode") String regionCode,
        @Param("categoryId") Long categoryId,
        @Param("status") MeetupStatus status,
        @Param("startFrom") LocalDateTime startFrom,
        @Param("startTo") LocalDateTime startTo,
        Pageable pageable
    );

    /**
     * 특정 주최자의 모임 목록 조회
     */
    @Query("""
        SELECT m FROM Meetup m
        WHERE m.deletedAt IS NULL
        AND m.organizerId = :organizerId
        ORDER BY m.createdAt DESC
        """)
    Page<Meetup> findByOrganizerId(@Param("organizerId") Long organizerId, Pageable pageable);

    /**
     * 모집 마감 임박 모임 조회 (스케줄러용, P1)
     */
    @Query("""
        SELECT m FROM Meetup m
        WHERE m.deletedAt IS NULL
        AND m.status = 'OPEN'
        AND m.recruitEndAt <= :threshold
        """)
    Page<Meetup> findRecruitmentEndingSoon(
        @Param("threshold") LocalDateTime threshold,
        Pageable pageable
    );
}
