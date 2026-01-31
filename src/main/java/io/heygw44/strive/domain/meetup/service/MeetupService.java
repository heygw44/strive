package io.heygw44.strive.domain.meetup.service;

import io.heygw44.strive.domain.meetup.dto.*;
import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;
import io.heygw44.strive.domain.meetup.repository.CategoryRepository;
import io.heygw44.strive.domain.meetup.repository.MeetupRepository;
import io.heygw44.strive.domain.meetup.repository.RegionRepository;
import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import io.heygw44.strive.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 비즈니스 로직 서비스
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class MeetupService {

    private final MeetupRepository meetupRepository;
    private final CategoryRepository categoryRepository;
    private final RegionRepository regionRepository;
    private final MeetupResponseAssembler meetupResponseAssembler;

    /**
     * 모임 생성
     * 도메인 규칙: recruitEndAt <= startAt, startAt < endAt
     */
    @Transactional
    public Meetup createMeetup(CreateMeetupRequest request, Long organizerId) {
        // 카테고리/지역 존재 검증
        validateCategoryExists(request.categoryId());
        validateRegionExists(request.regionCode());

        // 도메인 규칙 검증
        validateCreateRequest(request);

        // 엔티티 생성 및 저장
        Meetup meetup = Meetup.create(
            organizerId,
            request.title(),
            request.description(),
            request.categoryId(),
            request.regionCode(),
            request.locationText(),
            request.startAt(),
            request.endAt(),
            request.recruitEndAt(),
            request.capacity(),
            request.experienceLevelText()
        );

        Meetup saved = meetupRepository.save(meetup);
        log.info("모임 생성 완료: meetupId={}, organizerId={}", saved.getId(), organizerId);
        return saved;
    }

    /**
     * 모임 상세 조회
     */
    public Meetup getMeetup(Long meetupId) {
        return meetupRepository.findByIdAndDeletedAtIsNull(meetupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 모임 상세 응답 조회 (계층 분리: Controller에서 Repository 직접 참조 제거)
     */
    public MeetupResponse getMeetupResponse(Long meetupId) {
        Meetup meetup = getMeetup(meetupId);
        return meetupResponseAssembler.toMeetupResponse(meetup);
    }

    /**
     * 모임 목록 조회 (필터링 + 페이징)
     */
    public Page<Meetup> getMeetups(MeetupSearchCondition condition, Pageable pageable) {
        return meetupRepository.findByFilters(
            condition.regionCode(),
            condition.categoryId(),
            condition.status(),
            condition.startFrom(),
            condition.startTo(),
            pageable
        );
    }

    /**
     * 모임 목록 응답 조회 (계층 분리: Controller에서 Repository 직접 참조 제거)
     */
    public PageResponse<MeetupListResponse> getMeetupsResponse(
            MeetupSearchCondition condition, Pageable pageable) {
        Page<Meetup> meetupPage = getMeetups(condition, pageable);
        List<MeetupListResponse> items = meetupResponseAssembler
            .toMeetupListResponses(meetupPage.getContent());

        return new PageResponse<>(
            items,
            meetupPage.getTotalElements(),
            meetupPage.getNumber(),
            meetupPage.getSize(),
            meetupPage.hasNext()
        );
    }

    /**
     * 모임 수정
     */
    @Transactional
    public Meetup updateMeetup(Long meetupId, UpdateMeetupRequest request, Long currentUserId) {
        Meetup meetup = getMeetup(meetupId);

        // 권한 검증: 작성자만 수정 가능
        validateOrganizer(meetup, currentUserId);

        // 요청 유효성 검증 (필드/상태)
        validateUpdateRequest(meetup, request);

        // 상태 전이 요청 시 검증
        MeetupStatus targetStatus = request.status();
        MeetupStatus effectiveStatus = meetup.getStatus();
        if (targetStatus != null && targetStatus != effectiveStatus) {
            validateStatusTransition(effectiveStatus, targetStatus);
            effectiveStatus = targetStatus;
        }

        // 필드 수정 가능 상태 검증 (OPEN에서만 허용)
        if (hasFieldUpdates(request) && effectiveStatus != MeetupStatus.OPEN) {
            throw new BusinessException(ErrorCode.MEETUP_INVALID_STATE);
        }

        if (targetStatus != null && targetStatus != meetup.getStatus()) {
            meetup.transitionTo(targetStatus);
        }

        if (hasFieldUpdates(request)) {
            meetup.update(
                request.title(),
                request.description(),
                request.locationText(),
                request.experienceLevelText()
            );
        }

        log.info("모임 수정 완료: meetupId={}, updatedBy={}", meetupId, currentUserId);
        return meetup;
    }

    /**
     * 모임 수정 응답 반환 (계층 분리)
     */
    @Transactional
    public MeetupResponse updateMeetupAndGetResponse(Long meetupId, UpdateMeetupRequest request, Long currentUserId) {
        Meetup meetup = updateMeetup(meetupId, request, currentUserId);
        return meetupResponseAssembler.toMeetupResponse(meetup);
    }

    /**
     * 모임 삭제 (소프트 삭제)
     * PRD AC-AUTH-03: 작성자만 삭제 가능
     * PRD AC-MEETUP-02: 삭제 후 조회 시 RES-404
     */
    @Transactional
    public void deleteMeetup(Long meetupId, Long currentUserId) {
        Meetup meetup = getMeetup(meetupId);

        // 권한 검증: 작성자만 삭제 가능
        validateOrganizer(meetup, currentUserId);

        meetup.softDelete();
        log.info("모임 소프트 삭제 완료: meetupId={}, deletedBy={}", meetupId, currentUserId);
    }

    /**
     * 모집 마감 검증 (M3 참여 기능에서 사용)
     * PRD AC-MEETUP-03: recruitEndAt 이후 신청/승인 시 MEETUP-409-DEADLINE
     */
    public void validateRecruitmentOpen(Meetup meetup) {
        if (LocalDateTime.now().isAfter(meetup.getRecruitEndAt())) {
            throw new BusinessException(ErrorCode.MEETUP_DEADLINE_PASSED);
        }
    }

    /**
     * 모임 상태가 OPEN인지 검증 (M3 참여 기능에서 사용)
     */
    public void validateMeetupOpen(Meetup meetup) {
        if (meetup.getStatus() != MeetupStatus.OPEN) {
            throw new BusinessException(ErrorCode.MEETUP_INVALID_STATE);
        }
    }

    // === Private Helper Methods ===

    private void validateOrganizer(Meetup meetup, Long currentUserId) {
        if (!meetup.isOrganizer(currentUserId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validateStatusTransition(MeetupStatus current, MeetupStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.MEETUP_INVALID_STATE);
        }
    }

    private void validateUpdateRequest(Meetup meetup, UpdateMeetupRequest request) {
        boolean hasFieldUpdates = hasFieldUpdates(request);
        boolean hasStatusChange = request.status() != null && request.status() != meetup.getStatus();

        if (!hasFieldUpdates && !hasStatusChange) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        validateRequiredText(request.title());
        validateRequiredText(request.locationText());
    }

    private void validateRequiredText(String value) {
        if (value != null && !StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean hasFieldUpdates(UpdateMeetupRequest request) {
        return request.title() != null
            || request.description() != null
            || request.locationText() != null
            || request.experienceLevelText() != null;
    }

    private void validateCreateRequest(CreateMeetupRequest request) {
        // PRD 도메인 규칙: recruitEndAt <= startAt
        if (request.recruitEndAt().isAfter(request.startAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        // PRD 도메인 규칙: startAt < endAt
        if (!request.startAt().isBefore(request.endAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateCategoryExists(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void validateRegionExists(String regionCode) {
        if (!regionRepository.existsByCode(regionCode)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
