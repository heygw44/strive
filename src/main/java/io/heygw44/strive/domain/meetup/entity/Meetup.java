package io.heygw44.strive.domain.meetup.entity;

import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 모임 엔티티
 */
@Entity
@Table(name = "meetup", indexes = {
    @Index(name = "idx_meetup_list", columnList = "region_code, category_id, status, start_at"),
    @Index(name = "idx_meetup_recruit_end", columnList = "recruit_end_at"),
    @Index(name = "idx_meetup_organizer", columnList = "organizer_id"),
    @Index(name = "idx_meetup_deleted", columnList = "deleted_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meetup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "region_code", nullable = false, length = 50)
    private String regionCode;

    @Column(name = "location_text", nullable = false, length = 500)
    private String locationText;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "recruit_end_at", nullable = false)
    private LocalDateTime recruitEndAt;

    @Column(nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetupStatus status;

    @Column(name = "experience_level_text", length = 200)
    private String experienceLevelText;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private Meetup(Long organizerId, String title, String description, Long categoryId,
                   String regionCode, String locationText, LocalDateTime startAt,
                   LocalDateTime endAt, LocalDateTime recruitEndAt, Integer capacity,
                   String experienceLevelText) {
        this.organizerId = organizerId;
        this.title = title;
        this.description = description;
        this.categoryId = categoryId;
        this.regionCode = regionCode;
        this.locationText = locationText;
        this.startAt = startAt;
        this.endAt = endAt;
        this.recruitEndAt = recruitEndAt;
        this.capacity = capacity;
        this.experienceLevelText = experienceLevelText;
        this.status = MeetupStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 모임 생성 팩토리 메서드
     */
    public static Meetup create(Long organizerId, String title, String description,
                                 Long categoryId, String regionCode, String locationText,
                                 LocalDateTime startAt, LocalDateTime endAt,
                                 LocalDateTime recruitEndAt, Integer capacity,
                                 String experienceLevelText) {
        return new Meetup(organizerId, title, description, categoryId, regionCode,
                          locationText, startAt, endAt, recruitEndAt, capacity,
                          experienceLevelText);
    }

    /**
     * 모임 정보 수정
     * PRD 정책: OPEN 상태에서 title/description/locationText/experienceLevelText 수정 가능
     */
    public void update(String title, String description, String locationText,
                       String experienceLevelText) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (locationText != null) {
            this.locationText = locationText;
        }
        if (experienceLevelText != null) {
            this.experienceLevelText = experienceLevelText;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상태 전이
     * PRD 상태 전이 규칙 강제
     */
    public void transitionTo(MeetupStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new BusinessException(ErrorCode.MEETUP_INVALID_STATE);
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 모임 공개 (DRAFT → OPEN)
     */
    public void publish() {
        transitionTo(MeetupStatus.OPEN);
    }

    /**
     * 모집 마감 (OPEN → CLOSED)
     */
    public void closeRecruitment() {
        transitionTo(MeetupStatus.CLOSED);
    }

    /**
     * 모임 완료 (CLOSED → COMPLETED)
     */
    public void complete() {
        transitionTo(MeetupStatus.COMPLETED);
    }

    /**
     * 모임 취소 (OPEN/CLOSED → CANCELLED)
     */
    public void cancel() {
        transitionTo(MeetupStatus.CANCELLED);
    }

    /**
     * 소프트 삭제
     * PRD 정책: deletedAt 설정, 외부 조회에서 제외
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // 삭제 시 상태도 CANCELLED로 변경 (OPEN/CLOSED인 경우만)
        if (this.status == MeetupStatus.OPEN || this.status == MeetupStatus.CLOSED) {
            this.status = MeetupStatus.CANCELLED;
        }
    }

    /**
     * 삭제 여부 확인
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 모집 중인지 확인
     */
    public boolean isOpen() {
        return status == MeetupStatus.OPEN && !isDeleted();
    }

    /**
     * 모집 마감일 이전인지 확인
     */
    public boolean isRecruitmentOpen() {
        return LocalDateTime.now().isBefore(recruitEndAt);
    }

    /**
     * 작성자 확인
     */
    public boolean isOrganizer(Long userId) {
        return this.organizerId.equals(userId);
    }
}
