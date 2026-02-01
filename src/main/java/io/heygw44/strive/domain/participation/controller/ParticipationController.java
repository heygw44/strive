package io.heygw44.strive.domain.participation.controller;

import io.heygw44.strive.domain.participation.dto.ParticipationListResponse;
import io.heygw44.strive.domain.participation.dto.ParticipationResponse;
import io.heygw44.strive.domain.participation.service.ParticipationService;
import io.heygw44.strive.global.response.ApiResponse;
import io.heygw44.strive.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 참여 REST API 컨트롤러
 * PRD API: POST/DELETE/PATCH/GET /api/meetups/{meetupId}/participations
 */
@RestController
@RequestMapping("/api/meetups/{meetupId}/participations")
@RequiredArgsConstructor
@Slf4j
public class ParticipationController {

    private final ParticipationService participationService;

    /**
     * 참여 신청
     * POST /api/meetups/{meetupId}/participations
     * AC-PART-01: 중복 신청 방지
     * AC-MEETUP-03: 모집 마감 후 신청 금지
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ParticipationResponse>> requestParticipation(
            @PathVariable Long meetupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        ParticipationResponse response = participationService.requestParticipation(
            meetupId, userDetails.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * 참여 취소 (본인)
     * DELETE /api/meetups/{meetupId}/participations/me
     * AC-PART-03: APPROVED → CANCELLED 전이 허용
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> cancelParticipation(
            @PathVariable Long meetupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        participationService.cancelParticipation(meetupId, userDetails.getUserId());

        return ResponseEntity.noContent().build();
    }

    /**
     * 참여 승인 (주최자)
     * PATCH /api/meetups/{meetupId}/participations/{participationId}/approve
     * AC-AUTH-03: Organizer만 승인 가능
     * AC-MEETUP-03: 모집 마감 후 승인 금지
     * AC-PART-02: 정원 초과 시 PART-409-CAPACITY
     */
    @PatchMapping("/{participationId}/approve")
    public ResponseEntity<ApiResponse<ParticipationResponse>> approveParticipation(
            @PathVariable Long meetupId,
            @PathVariable Long participationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        ParticipationResponse response = participationService.approveParticipation(
            meetupId, participationId, userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 참여 거절 (주최자)
     * PATCH /api/meetups/{meetupId}/participations/{participationId}/reject
     * AC-AUTH-03: Organizer만 거절 가능
     */
    @PatchMapping("/{participationId}/reject")
    public ResponseEntity<ApiResponse<ParticipationResponse>> rejectParticipation(
            @PathVariable Long meetupId,
            @PathVariable Long participationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        ParticipationResponse response = participationService.rejectParticipation(
            meetupId, participationId, userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 참여 목록 조회 (주최자)
     * GET /api/meetups/{meetupId}/participations
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ParticipationListResponse>> getParticipations(
            @PathVariable Long meetupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        ParticipationListResponse response = participationService.getParticipations(
            meetupId, userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
