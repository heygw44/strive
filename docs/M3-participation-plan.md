# M3 — 참여 기능 (신청/취소/승인/거절) 구현 계획서

> 기준 문서: `/.claude/prd.md`, `/.claude/rules/patterns.md`, `/.claude/rules/testing.md`

## 개요

M2(모임 CRUD) 완료 상태에서 **모임 참여 기능의 핵심 CRUD와 상태 전이 규칙**을 구현한다. 참가자가 모임에 신청/취소하고, 주최자가 승인/거절하는 **Approval Model**을 구현하며, PRD에 정의된 상태 전이 규칙과 도메인 오류 코드를 적용한다. M4 동시성 제어의 기반이 되는 기본 구조를 확립한다.

## 현재 상태 (M2 완료 항목)

| 항목 | 상태 | 파일 |
|------|------|------|
| Meetup 엔티티/상태 전이 | 완료 | `Meetup.java`, `MeetupStatus.java` |
| 비관적 락 조회 | 완료 | `MeetupRepository.findByIdForUpdate()` |
| 모임 CRUD API | 완료 | `MeetupController`, `MeetupService` |
| 모집 마감/상태 검증 | 완료 | `MeetupService.validateRecruitmentOpen()` |
| 참여 관련 오류 코드 | 완료 | `ErrorCode.PARTICIPATION_*` |

## 관련 PRD 항목

- 마일스톤: **M3 — 참여(신청/취소/승인/거절) + 상태 규칙**
- 수용 기준: AC-PART-01~04, AC-MEETUP-03, AC-AUTH-03
- 비기능: 상태 전이 규칙, UNIQUE 제약 (중복 신청 방지)

## 수용 기준 (AC) 상세

| AC | 설명 | 검증 방법 |
|----|------|----------|
| AC-PART-01 | 동일 사용자가 동일 모임에 중복 신청 시 PART-409-DUPLICATE 반환 | 통합 테스트 |
| AC-PART-02 | 정원 10명 모임에 100명 동시 승인 시 APPROVED 최대 10명, 초과 시 PART-409-CAPACITY | M3: 기본 정원 체크, M4: 동시성 테스트 |
| AC-PART-03 | APPROVED 취소 시 CANCELLED 전이, 이후 다른 참가자 승인 가능 (정원 로직 일관성) | 통합 테스트 |
| AC-PART-04 | 허용되지 않는 상태 전이 시 PART-409-STATE 반환 | 단위/통합 테스트 |
| AC-MEETUP-03 | recruitEndAt 이후 신청/승인 시 MEETUP-409-DEADLINE 반환 | 통합 테스트 |
| AC-AUTH-03 | Organizer만 승인/거절 가능, 타인 시 AUTH-403 반환 | 통합 테스트 |

## 아키텍처/패키지 구조 (M3 추가분)

```
src/main/java/io/heygw44/strive/
├── domain/
│   └── participation/
│       ├── controller/
│       │   └── ParticipationController.java
│       ├── service/
│       │   └── ParticipationService.java
│       ├── repository/
│       │   └── ParticipationRepository.java
│       ├── entity/
│       │   ├── Participation.java
│       │   └── ParticipationStatus.java
│       └── dto/
│           ├── ParticipationResponse.java
│           └── ParticipationListResponse.java
```

## 구현 단계

### Phase 1: 도메인 모델 정의

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 1 | ParticipationStatus enum 생성 | `entity/ParticipationStatus.java` | 없음 | 낮음 | AC-PART-04 |
| 2 | Participation 엔티티 생성 | `entity/Participation.java` | #1 | 중간 | AC-PART-01, AC-PART-04 |

**ParticipationStatus 상태 전이 규칙** (PRD 기준):
```java
public enum ParticipationStatus {
    REQUESTED,  // 신청
    APPROVED,   // 확정
    REJECTED,   // 거절
    CANCELLED;  // 취소

    public boolean canTransitionTo(ParticipationStatus target) {
        return switch (this) {
            case REQUESTED -> target == APPROVED || target == REJECTED || target == CANCELLED;
            case APPROVED -> target == CANCELLED;
            case REJECTED, CANCELLED -> false;
        };
    }
}
```

**Participation 엔티티 설계**:
```java
@Entity
@Table(name = "participation",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_participation_meetup_user",
           columnNames = {"meetup_id", "user_id"}
       ),
       indexes = {
           @Index(name = "idx_participation_meetup_status",
                  columnList = "meetup_id, status")
       })
public class Participation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meetup_id", nullable = false)
    private Long meetupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 팩토리 메서드: request()
    // 상태 전이 메서드: approve(), reject(), cancel()
}
```

### Phase 2: Repository 계층

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 3 | ParticipationRepository 생성 | `repository/ParticipationRepository.java` | #2 | 낮음 | AC-PART-01, AC-PART-02 |

**ParticipationRepository 주요 메서드**:
```java
public interface ParticipationRepository extends JpaRepository<Participation, Long> {
    // 중복 신청 확인 (AC-PART-01)
    boolean existsByMeetupIdAndUserId(Long meetupId, Long userId);

    // 본인 참여 조회 (취소용)
    Optional<Participation> findByMeetupIdAndUserId(Long meetupId, Long userId);

    // APPROVED 카운트 (정원 체크, AC-PART-02)
    long countByMeetupIdAndStatus(Long meetupId, ParticipationStatus status);

    // 모임별 참여 목록 (주최자용)
    List<Participation> findByMeetupIdOrderByCreatedAtAsc(Long meetupId);

    // 특정 상태 참여 목록
    List<Participation> findByMeetupIdAndStatusOrderByCreatedAtAsc(
        Long meetupId, ParticipationStatus status);
}
```

### Phase 3: DTO 정의

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 4 | ParticipationResponse 생성 | `dto/ParticipationResponse.java` | #1, #2 | 낮음 | - |
| 5 | ParticipationListResponse 생성 | `dto/ParticipationListResponse.java` | #4 | 낮음 | - |

**DTO 설계**:
```java
// 단건 응답
public record ParticipationResponse(
    Long id,
    Long meetupId,
    Long userId,
    String userNickname,
    ParticipationStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

// 목록 응답 (주최자용)
public record ParticipationListResponse(
    List<ParticipationResponse> participations,
    long totalCount,
    long approvedCount,
    int capacity
) {}
```

### Phase 4: Service 계층

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 6 | ParticipationService 생성 | `service/ParticipationService.java` | #3-5, MeetupService | 높음 | AC-PART-01~04, AC-MEETUP-03, AC-AUTH-03 |

**ParticipationService 메서드**:

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ParticipationService {

    private final ParticipationRepository participationRepository;
    private final MeetupRepository meetupRepository;
    private final UserRepository userRepository;

    /**
     * 참여 신청 (POST /api/meetups/{id}/participations)
     * AC-PART-01: 중복 신청 방지
     * AC-MEETUP-03: recruitEndAt 이후 신청 금지
     */
    @Transactional
    public Participation requestParticipation(Long meetupId, Long userId) {
        // 1. 모임 조회 및 상태 검증 (OPEN, 마감 전)
        Meetup meetup = meetupRepository.findByIdAndDeletedAtIsNull(meetupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        validateMeetupOpenForParticipation(meetup);

        // 2. 중복 신청 확인 → PART-409-DUPLICATE
        if (participationRepository.existsByMeetupIdAndUserId(meetupId, userId)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE);
        }

        // 3. Participation 생성 (REQUESTED)
        Participation participation = Participation.request(meetupId, userId);
        try {
            return participationRepository.save(participation);
        } catch (DataIntegrityViolationException ex) {
            // 동시성 상황에서 유니크 제약 위반 시 중복 신청으로 매핑
            throw new BusinessException(ErrorCode.PARTICIPATION_DUPLICATE);
        }
    }

    /**
     * 참여 취소 (DELETE /api/meetups/{id}/participations/me)
     * AC-PART-03: APPROVED → CANCELLED 전이 허용
     */
    @Transactional
    public void cancelParticipation(Long meetupId, Long userId) {
        // 1. 본인 참여 조회
        Participation participation = participationRepository
            .findByMeetupIdAndUserId(meetupId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. 상태 전이 검증 → PART-409-STATE
        // 3. CANCELLED로 전이
        participation.cancel();
    }

    /**
     * 참여 승인 (PATCH /api/meetups/{id}/participations/{participationId}/approve)
     * AC-AUTH-03: Organizer만 승인 가능
     * AC-MEETUP-03: recruitEndAt 이후 승인 금지
     * AC-PART-02: 정원 초과 시 PART-409-CAPACITY
     */
    @Transactional
    public Participation approveParticipation(Long meetupId, Long participationId, Long organizerId) {
        // 1. 모임 조회 (FOR UPDATE로 정원 동시성 제어)
        Meetup meetup = meetupRepository.findByIdForUpdate(meetupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. Organizer 권한 검증 → AUTH-403
        validateOrganizer(meetup, organizerId);

        // 3. 모임 상태/마감일 검증
        validateMeetupOpenForParticipation(meetup);

        // 4. 참여 조회 및 상태 전이 검증 → PART-409-STATE
        Participation participation = participationRepository.findById(participationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!participation.isStatus(ParticipationStatus.REQUESTED)) {
            throw new BusinessException(ErrorCode.PARTICIPATION_INVALID_STATE);
        }

        // 5. 정원 검증 → PART-409-CAPACITY
        validateCapacity(meetup);

        // 6. APPROVED로 전이
        participation.approve();
        return participation;
    }

    /**
     * 참여 거절 (PATCH /api/meetups/{id}/participations/{participationId}/reject)
     * AC-AUTH-03: Organizer만 거절 가능
     */
    @Transactional
    public Participation rejectParticipation(Long meetupId, Long participationId, Long organizerId) {
        // 1. 모임 조회
        Meetup meetup = meetupRepository.findByIdAndDeletedAtIsNull(meetupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. Organizer 권한 검증 → AUTH-403
        validateOrganizer(meetup, organizerId);

        // 3. 참여 조회 및 상태 전이 검증 → PART-409-STATE
        Participation participation = participationRepository.findById(participationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 4. REJECTED로 전이
        participation.reject();
        return participation;
    }

    /**
     * 모임별 참여 목록 조회 (주최자용)
     */
    public ParticipationListResponse getParticipations(Long meetupId, Long organizerId) {
        // 1. 모임 조회
        Meetup meetup = meetupRepository.findByIdAndDeletedAtIsNull(meetupId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. Organizer 권한 검증
        validateOrganizer(meetup, organizerId);

        // 3. 참여 목록 반환
        List<Participation> participations = participationRepository
            .findByMeetupIdOrderByCreatedAtAsc(meetupId);

        long approvedCount = participations.stream()
            .filter(p -> p.getStatus() == ParticipationStatus.APPROVED)
            .count();

        return new ParticipationListResponse(
            participations.stream().map(this::toResponse).toList(),
            participations.size(),
            approvedCount,
            meetup.getCapacity()
        );
    }
}
```

### Phase 5: Controller 계층

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 7 | ParticipationController 생성 | `controller/ParticipationController.java` | #6 | 중간 | 전체 |

**API 엔드포인트** (PRD 기준):
```
POST   /api/meetups/{meetupId}/participations                      # 신청 (인증 필수)
DELETE /api/meetups/{meetupId}/participations/me                   # 취소 (본인만)
PATCH  /api/meetups/{meetupId}/participations/{id}/approve         # 승인 (Organizer만)
PATCH  /api/meetups/{meetupId}/participations/{id}/reject          # 거절 (Organizer만)
GET    /api/meetups/{meetupId}/participations                      # 목록 (Organizer만)
```

**Controller 구현**:
```java
@RestController
@RequestMapping("/api/meetups/{meetupId}/participations")
@RequiredArgsConstructor
@Slf4j
public class ParticipationController {

    private final ParticipationService participationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ParticipationResponse>> requestParticipation(
            @PathVariable Long meetupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Participation participation = participationService.requestParticipation(
            meetupId, userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(toResponse(participation)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> cancelParticipation(
            @PathVariable Long meetupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        participationService.cancelParticipation(meetupId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{participationId}/approve")
    public ResponseEntity<ApiResponse<ParticipationResponse>> approveParticipation(
            @PathVariable Long meetupId,
            @PathVariable Long participationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Participation participation = participationService.approveParticipation(
            meetupId, participationId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(toResponse(participation)));
    }

    @PatchMapping("/{participationId}/reject")
    public ResponseEntity<ApiResponse<ParticipationResponse>> rejectParticipation(
            @PathVariable Long meetupId,
            @PathVariable Long participationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Participation participation = participationService.rejectParticipation(
            meetupId, participationId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(toResponse(participation)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ParticipationListResponse>> getParticipations(
            @PathVariable Long meetupId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ParticipationListResponse response = participationService.getParticipations(
            meetupId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

### Phase 6: 단위 테스트

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 8 | ParticipationStatus 상태 전이 테스트 | `test/.../ParticipationStatusTest.java` | #1 | 낮음 | AC-PART-04 |
| 9 | ParticipationService 단위 테스트 | `test/.../ParticipationServiceTest.java` | #6 | 중간 | 전체 |

**ParticipationStatusTest 케이스**:
```java
@Test
@DisplayName("REQUESTED → APPROVED 전이 허용")
void requested_to_approved_allowed() { ... }

@Test
@DisplayName("REQUESTED → REJECTED 전이 허용")
void requested_to_rejected_allowed() { ... }

@Test
@DisplayName("REQUESTED → CANCELLED 전이 허용")
void requested_to_cancelled_allowed() { ... }

@Test
@DisplayName("APPROVED → CANCELLED 전이 허용")
void approved_to_cancelled_allowed() { ... }

@Test
@DisplayName("APPROVED → REJECTED 전이 금지")
void approved_to_rejected_not_allowed() { ... }

@Test
@DisplayName("REJECTED → 모든 전이 금지")
void rejected_to_any_not_allowed() { ... }

@Test
@DisplayName("CANCELLED → 모든 전이 금지")
void cancelled_to_any_not_allowed() { ... }
```

### Phase 7: 통합 테스트

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 10 | ParticipationIntegrationTest | `test/.../ParticipationIntegrationTest.java` | #7 | 중간 | 전체 |

**통합 테스트 케이스**:
```java
@Nested
@DisplayName("AC-PART-01: 중복 신청 방지")
class DuplicateParticipationTest {
    @Test
    @DisplayName("동일 모임 중복 신청 시 PART-409-DUPLICATE")
    void requestParticipation_duplicate_returns409() { ... }
}

@Nested
@DisplayName("AC-MEETUP-03: 모집 마감 후 신청/승인 금지")
class DeadlineTest {
    @Test
    @DisplayName("모집 마감 후 신청 시 MEETUP-409-DEADLINE")
    void requestParticipation_afterDeadline_returns409() { ... }

    @Test
    @DisplayName("모집 마감 후 승인 시 MEETUP-409-DEADLINE")
    void approveParticipation_afterDeadline_returns409() { ... }
}

@Nested
@DisplayName("AC-PART-03: APPROVED 취소 후 정원 로직")
class ApprovedCancellationTest {
    @Test
    @DisplayName("APPROVED 참가자 취소 후 CANCELLED 상태")
    void cancelParticipation_approved_becomesCancelled() { ... }

    @Test
    @DisplayName("APPROVED 취소 후 다른 참가자 승인 가능")
    void cancelParticipation_approved_allowsNewApproval() { ... }
}

@Nested
@DisplayName("AC-PART-04: 허용되지 않는 상태 전이")
class InvalidStateTransitionTest {
    @Test
    @DisplayName("CANCELLED 상태에서 승인 시 PART-409-STATE")
    void approveParticipation_cancelled_returns409() { ... }

    @Test
    @DisplayName("REJECTED 상태에서 승인 시 PART-409-STATE")
    void approveParticipation_rejected_returns409() { ... }
}

@Nested
@DisplayName("AC-AUTH-03: Organizer 권한")
class OrganizerAuthorizationTest {
    @Test
    @DisplayName("Organizer만 승인 가능")
    void approveParticipation_byNonOrganizer_returns403() { ... }

    @Test
    @DisplayName("Organizer만 거절 가능")
    void rejectParticipation_byNonOrganizer_returns403() { ... }
}

@Nested
@DisplayName("AC-PART-02 기본: 정원 초과 방지")
class CapacityTest {
    @Test
    @DisplayName("정원 초과 시 승인 거부")
    void approveParticipation_capacityExceeded_returns409() { ... }
}
```

## 테스트 전략

### 단위 테스트
- `ParticipationStatus.canTransitionTo()` 모든 상태 조합 테스트
- `Participation.approve()`, `reject()`, `cancel()` 상태 전이 메서드 테스트
- `ParticipationService` Mock 기반 비즈니스 로직 테스트

### 통합 테스트
- API 엔드포인트별 성공/실패 시나리오
- 세션 인증 필수 확인
- 권한 검증 (Organizer vs 일반 사용자)
- 도메인 오류 코드 반환 확인

### 동시성 테스트 (M4 준비)
- 중복 신청 동시성(유니크 제약) 테스트는 M3에서 추가 완료
- 정원 동시성(AC-PART-02) 검증은 M4에서 `ExecutorService` + `CountDownLatch` 기반으로 추가
- AC-PART-02 완전 통과는 M4 목표

## 리스크 및 대응

| 리스크 | 수준 | 대응 |
|--------|------|------|
| 정원 체크 동시성 (AC-PART-02) | 높음 | M3: 기본 정원 체크, M4: 비관적 락 적용 |
| DB UNIQUE 제약 위반 시 예외 처리 | 중간 | `existsByMeetupIdAndUserId()` 선행 체크 + save 시 `DataIntegrityViolationException`을 `PART-409-DUPLICATE`로 매핑 |
| API 경로와 SecurityConfig 불일치 | 낮음 | 기존 `POST/PATCH/DELETE /api/meetups/**` 패턴으로 커버됨 |

## 성공 기준 (Done Definition)

- [x] AC-PART-01: 중복 신청 시 PART-409-DUPLICATE 반환
- [x] AC-PART-02 (기본): 정원 초과 시 PART-409-CAPACITY 반환 (순차 요청)
- [x] AC-PART-03: APPROVED 취소 후 CANCELLED 상태, 새 승인 가능
- [x] AC-PART-04: 허용되지 않는 상태 전이 시 PART-409-STATE 반환
- [x] AC-MEETUP-03: 모집 마감 후 신청/승인 시 MEETUP-409-DEADLINE 반환
- [x] AC-AUTH-03: Organizer만 승인/거절 가능, 타인 시 AUTH-403 반환
- [x] 상태 전이 규칙 단위 테스트 통과
- [ ] 테스트 커버리지 80%+ (participation 도메인) — M4에서 서비스 단위 테스트 추가 후 확인
- [x] 빌드 성공 (`./gradlew build`)

## 파일 단위 체크리스트 (M3)

### 엔티티
- [x] `domain/participation/entity/ParticipationStatus.java`: 참가 상태 enum (상태 전이 규칙)
- [x] `domain/participation/entity/Participation.java`: 참여 엔티티 (UNIQUE 제약, 인덱스)

### Repository
- [x] `domain/participation/repository/ParticipationRepository.java`: 참여 Repository

### DTO
- [x] `domain/participation/dto/ParticipationResponse.java`: 단건 응답
- [x] `domain/participation/dto/ParticipationListResponse.java`: 목록 응답 (주최자용)

### Service
- [x] `domain/participation/service/ParticipationService.java`: 참여 비즈니스 로직

### Controller
- [x] `domain/participation/controller/ParticipationController.java`: 참여 API

### 테스트
- [x] `test/.../entity/ParticipationStatusTest.java`: 상태 전이 단위 테스트
- [x] `test/.../service/ParticipationConcurrencyTest.java`: 중복 신청 동시성/유니크 제약 테스트
- [ ] `test/.../service/ParticipationServiceTest.java`: 서비스 단위 테스트 (M4에서 추가 예정)
- [x] `test/.../controller/ParticipationIntegrationTest.java`: API 통합 테스트

## 예상 작업량

| Phase | 작업 수 | 복잡도 |
|-------|---------|--------|
| Phase 1: 도메인 모델 | 2 | 중간 |
| Phase 2: Repository | 1 | 낮음 |
| Phase 3: DTO | 2 | 낮음 |
| Phase 4: Service | 1 | 높음 |
| Phase 5: Controller | 1 | 중간 |
| Phase 6: 단위 테스트 | 2 | 중간 |
| Phase 7: 통합 테스트 | 1 | 중간 |

**총 10개 작업**, Phase별 순차 진행

## M4 연계 포인트

M3에서 비관적 락 이미 적용됨:
- `approveParticipation()`에서 `meetupRepository.findByIdForUpdate()` 사용 (동시성 제어)

M4에서 추가할 부분:

1. **동시성 테스트 추가**:
   - `ExecutorService` + `CountDownLatch` 기반 100건 동시 승인 테스트
   - AC-PART-02 완전 검증 (정원 10명에 100명 동시 승인 시 APPROVED 최대 10명)

## 다음 단계

- M3 완료 후 `feature/M3-participation` → `main` PR 생성
- M4 동시성 증명 구현 착수
  - 비관적 락으로 `approveParticipation()` 수정
  - 100명 동시 승인 테스트 작성 (AC-PART-02)
- M5 성능 튜닝 (필요시)
