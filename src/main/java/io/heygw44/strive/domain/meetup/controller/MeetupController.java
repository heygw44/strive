package io.heygw44.strive.domain.meetup.controller;

import io.heygw44.strive.domain.meetup.dto.*;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;
import io.heygw44.strive.domain.meetup.service.MeetupService;
import io.heygw44.strive.global.response.ApiResponse;
import io.heygw44.strive.global.response.PageResponse;
import io.heygw44.strive.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 모임 REST API 컨트롤러
 * PRD API: POST/GET/PUT/DELETE /api/meetups
 * 계층 분리: Controller는 HTTP 요청/응답 처리만 담당, 비즈니스 로직은 Service에 위임
 */
@RestController
@RequestMapping("/api/meetups")
@RequiredArgsConstructor
@Slf4j
public class MeetupController {

    private final MeetupService meetupService;

    /**
     * 모임 생성
     * POST /api/meetups
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MeetupResponse>> createMeetup(
            @Valid @RequestBody CreateMeetupRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long organizerId = userDetails.getUserId();
        // Service에서 응답 DTO까지 생성
        MeetupResponse response = meetupService.getMeetupResponse(
            meetupService.createMeetup(request, organizerId).getId()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * 모임 목록 조회
     * GET /api/meetups
     * PRD AC-MEETUP-01: OPEN 모임 필터(region/category/time/status)로 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MeetupListResponse>>> getMeetups(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) MeetupStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo,
            @RequestParam(defaultValue = "startAt") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        MeetupSearchCondition condition = new MeetupSearchCondition(
            regionCode, categoryId, status, startFrom, startTo, sort
        );

        Sort sortOrder = "createdAt".equals(sort)
            ? Sort.by("createdAt").descending()
            : Sort.by("startAt").ascending();

        Pageable pageable = PageRequest.of(page, size, sortOrder);

        // Service에서 응답 DTO까지 생성 (계층 분리)
        PageResponse<MeetupListResponse> pageResponse = meetupService.getMeetupsResponse(condition, pageable);

        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    /**
     * 모임 상세 조회
     * GET /api/meetups/{id}
     * PRD AC-MEETUP-02: 삭제된 모임은 RES-404
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MeetupResponse>> getMeetup(@PathVariable Long id) {
        // Service에서 응답 DTO까지 생성 (계층 분리)
        MeetupResponse response = meetupService.getMeetupResponse(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 모임 수정
     * PUT /api/meetups/{id}
     * PRD AC-AUTH-03: 작성자만 수정 가능
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MeetupResponse>> updateMeetup(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMeetupRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long currentUserId = userDetails.getUserId();
        // Service에서 응답 DTO까지 생성 (계층 분리)
        MeetupResponse response = meetupService.updateMeetupAndGetResponse(id, request, currentUserId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 모임 삭제 (소프트 삭제)
     * DELETE /api/meetups/{id}
     * PRD AC-AUTH-03: 작성자만 삭제 가능
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeetup(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long currentUserId = userDetails.getUserId();
        meetupService.deleteMeetup(id, currentUserId);

        return ResponseEntity.noContent().build();
    }
}
