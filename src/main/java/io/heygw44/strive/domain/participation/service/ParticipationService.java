package io.heygw44.strive.domain.participation.service;

import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;
import io.heygw44.strive.domain.meetup.repository.MeetupRepository;
import io.heygw44.strive.domain.participation.dto.ParticipationListResponse;
import io.heygw44.strive.domain.participation.dto.ParticipationResponse;
import io.heygw44.strive.domain.participation.entity.Participation;
import io.heygw44.strive.domain.participation.entity.ParticipationStatus;
import io.heygw44.strive.domain.participation.repository.ParticipationRepository;
import io.heygw44.strive.domain.user.entity.User;
import io.heygw44.strive.domain.user.repository.UserRepository;
import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 참여 비즈니스 로직 서비스
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ParticipationService {

    private final ParticipationRepository participationRepository;
    private final MeetupRepository meetupRepository;
    private final UserRepository userRepository;

    /**
     * 참여 신청
     * AC-PART-01: 중복 신청 방지
     * AC-MEETUP-03: recruitEndAt 이후 신청 금지
     */
    @Transactional
    public ParticipationResponse requestParticipation(Long meetupId, Long userId) {
        // 1. 모임 조회 및 상태 검증 (OPEN, 마감 전)
        Meetup meetup = getMeetupOrThrow(meetupId);
        validateMeetupOpenForParticipation(meetup);

        // 2. 중복 신청 확인 → PART-409-DUPLICATE
        if (participationRepository.existsByMeetupIdAndUserId(meetupId, userId)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE);
        }

        // 3. Participation 생성 (REQUESTED)
        Participation participation = Participation.request(meetupId, userId);
        Participation saved;
        try {
            saved = participationRepository.save(participation);
        } catch (DataIntegrityViolationException ex) {
            // 동시성 상황에서 유니크 제약 위반 발생 시 중복 신청으로 매핑
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE);
        }

        log.info("참여 신청 완료: meetupId={}, userId={}, participationId={}",
            meetupId, userId, saved.getId());

        return toResponse(saved);
    }

    /**
     * 참여 취소
     * AC-PART-03: APPROVED → CANCELLED 전이 허용
     */
    @Transactional
    public void cancelParticipation(Long meetupId, Long userId) {
        // 1. 본인 참여 조회
        Participation participation = participationRepository
            .findByMeetupIdAndUserId(meetupId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. 상태 전이 (REQUESTED/APPROVED → CANCELLED)
        // canTransitionTo에서 상태 전이 규칙 검증
        participation.cancel();

        log.info("참여 취소 완료: meetupId={}, userId={}, participationId={}",
            meetupId, userId, participation.getId());
    }

    /**
     * 참여 승인
     * AC-AUTH-03: Organizer만 승인 가능
     * AC-MEETUP-03: recruitEndAt 이후 승인 금지
     * AC-PART-02: 정원 초과 시 PART-409-CAPACITY (비관적 락으로 동시성 제어)
     */
    @Transactional
    public ParticipationResponse approveParticipation(Long meetupId, Long participationId, Long organizerId) {
        // 1. 비관적 락으로 모임 조회 (동시성 제어 - AC-PART-02)
        Meetup meetup = meetupRepository.findByIdForUpdate(meetupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. Organizer 권한 검증 → AUTH-403
        validateOrganizer(meetup, organizerId);

        // 3. 모임 상태/마감일 검증
        validateMeetupOpenForParticipation(meetup);

        // 4. 참여 조회 및 모임 소속 확인
        Participation participation = getParticipationOrThrow(participationId);
        if (!participation.belongsToMeetup(meetupId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 5. 상태 검증 (REQUESTED만 승인 가능)
        if (!participation.isStatus(ParticipationStatus.REQUESTED)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_INVALID_STATE);
        }

        // 6. 정원 검증 → PART-409-CAPACITY
        validateCapacity(meetup);

        // 7. APPROVED로 전이
        participation.approve();

        log.info("참여 승인 완료: meetupId={}, participationId={}, approvedBy={}",
            meetupId, participationId, organizerId);

        return toResponse(participation);
    }

    /**
     * 참여 거절
     * AC-AUTH-03: Organizer만 거절 가능
     */
    @Transactional
    public ParticipationResponse rejectParticipation(Long meetupId, Long participationId, Long organizerId) {
        // 1. 모임 조회
        Meetup meetup = getMeetupOrThrow(meetupId);

        // 2. Organizer 권한 검증 → AUTH-403
        validateOrganizer(meetup, organizerId);

        // 3. 참여 조회 및 모임 소속 확인
        Participation participation = getParticipationOrThrow(participationId);
        if (!participation.belongsToMeetup(meetupId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 4. REJECTED로 전이 (상태 전이 규칙 검증 포함)
        participation.reject();

        log.info("참여 거절 완료: meetupId={}, participationId={}, rejectedBy={}",
            meetupId, participationId, organizerId);

        return toResponse(participation);
    }

    /**
     * 모임별 참여 목록 조회 (주최자용)
     */
    public ParticipationListResponse getParticipations(Long meetupId, Long organizerId) {
        // 1. 모임 조회
        Meetup meetup = getMeetupOrThrow(meetupId);

        // 2. Organizer 권한 검증
        validateOrganizer(meetup, organizerId);

        // 3. 참여 목록 조회
        List<Participation> participations = participationRepository
            .findByMeetupIdOrderByCreatedAtAsc(meetupId);

        // 4. 사용자 정보 배치 조회 (N+1 방지)
        List<Long> userIds = participations.stream()
            .map(Participation::getUserId)
            .distinct()
            .toList();

        Map<Long, String> userNicknameMap = userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, User::getNickname));

        // 5. 응답 조립
        List<ParticipationResponse> responses = participations.stream()
            .map(p -> ParticipationResponse.from(p, userNicknameMap.get(p.getUserId())))
            .toList();

        long approvedCount = participations.stream()
            .filter(p -> p.isStatus(ParticipationStatus.APPROVED))
            .count();

        return new ParticipationListResponse(
            responses,
            participations.size(),
            approvedCount,
            meetup.getCapacity()
        );
    }

    // === Private Helper Methods ===

    private Meetup getMeetupOrThrow(Long meetupId) {
        return meetupRepository.findByIdAndDeletedAtIsNull(meetupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Participation getParticipationOrThrow(Long participationId) {
        return participationRepository.findById(participationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateOrganizer(Meetup meetup, Long userId) {
        if (!meetup.isOrganizer(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validateMeetupOpenForParticipation(Meetup meetup) {
        // 모임 상태가 OPEN인지 확인
        if (meetup.getStatus() != MeetupStatus.OPEN) {
            throw new BusinessException(ErrorCode.MEETUP_INVALID_STATE);
        }
        // 모집 마감일 전인지 확인
        if (LocalDateTime.now().isAfter(meetup.getRecruitEndAt())) {
            throw new BusinessException(ErrorCode.MEETUP_DEADLINE_PASSED);
        }
    }

    private void validateCapacity(Meetup meetup) {
        long approvedCount = participationRepository.countByMeetupIdAndStatus(
            meetup.getId(), ParticipationStatus.APPROVED);

        if (approvedCount >= meetup.getCapacity()) {
            throw new BusinessException(ErrorCode.PARTICIPATION_CAPACITY_EXCEEDED);
        }
    }

    private ParticipationResponse toResponse(Participation participation) {
        String nickname = userRepository.findById(participation.getUserId())
            .map(User::getNickname)
            .orElse(null);
        return ParticipationResponse.from(participation, nickname);
    }
}
