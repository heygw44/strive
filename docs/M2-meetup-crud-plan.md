# M2 — 모임 CRUD + 조회 구현 계획서

> 기준 문서: `/.claude/prd.md`, `/.claude/rules/patterns.md`, `/.claude/rules/performance.md`

## 개요

M1(인증/인가) 완료 상태에서 **모임(Meetup) 도메인의 핵심 CRUD 기능**과 **목록 조회(필터/정렬/페이징)**를 구현한다. 세션 기반 인증(M1)을 활용하여 작성자 권한 검증을 수행하고, PRD에 정의된 모임 상태 전이 규칙을 적용한다.

## 현재 상태 (M1 완료 항목)

| 항목 | 상태 | 파일 |
|------|------|------|
| 회원가입/로그인/로그아웃 | 완료 | `AuthController`, `AuthService` |
| 세션 기반 인증 | 완료 | `SecurityConfig`, `CustomUserDetails` |
| 프로필 조회/수정 | 완료 | `ProfileController`, `ProfileService` |
| 이메일 인증 | 완료 | `EmailVerificationToken`, `AuthService` |
| 공통 예외/응답 | 완료 | `ErrorCode`, `ApiResponse`, `GlobalExceptionHandler` |

## 관련 PRD 항목

- 마일스톤: **M2 — 모임 CRUD + 조회 1차**
- 수용 기준: AC-MEETUP-01~03, AC-AUTH-03
- 비기능: 인덱스 설계, 페이징, 소프트 삭제

## 수용 기준 (AC) 상세

| AC | 설명 | 검증 방법 |
|----|------|----------|
| AC-MEETUP-01 | OPEN 모임은 목록 조회 필터(region/category/time/status)로 조회 가능 | 통합 테스트 |
| AC-MEETUP-02 | 소프트 삭제된 모임은 목록/상세 조회에서 RES-404로 처리 | 통합 테스트 |
| AC-MEETUP-03 | recruitEndAt 이후 신청/승인 시 MEETUP-409-DEADLINE 반환 | M3에서 검증 (참여 기능 필요) |
| AC-AUTH-03 | 타인의 모임 수정/삭제 시 AUTH-403 반환 | 통합 테스트 |

## 아키텍처/패키지 구조 (M2 추가분)

```
src/main/java/io/heygw44/strive/
├── domain/
│   └── meetup/
│       ├── controller/
│       │   └── MeetupController.java
│       ├── service/
│       │   ├── MeetupService.java
│       │   └── MeetupResponseAssembler.java
│       ├── repository/
│       │   ├── MeetupRepository.java
│       │   ├── CategoryRepository.java
│       │   └── RegionRepository.java
│       ├── entity/
│       │   ├── Meetup.java
│       │   ├── MeetupStatus.java
│       │   ├── Category.java
│       │   └── Region.java
│       └── dto/
│           ├── CreateMeetupRequest.java
│           ├── UpdateMeetupRequest.java
│           ├── MeetupResponse.java
│           ├── MeetupListResponse.java
│           └── MeetupSearchCondition.java
```

## 구현 단계

### Phase 1: 엔티티 및 스키마 정의

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 1 | MeetupStatus enum 생성 | `entity/MeetupStatus.java` | 없음 | 낮음 | AC-MEETUP-01 |
| 2 | Category 엔티티 생성 | `entity/Category.java` | 없음 | 낮음 | AC-MEETUP-01 |
| 3 | Region 엔티티 생성 | `entity/Region.java` | 없음 | 낮음 | AC-MEETUP-01 |
| 4 | Meetup 엔티티 생성 | `entity/Meetup.java` | #1 | 중간 | AC-MEETUP-01, AC-MEETUP-02 |

**MeetupStatus 상태 전이 규칙** (PRD 기준):
```java
public enum MeetupStatus {
    DRAFT,      // 작성중
    OPEN,       // 모집중
    CLOSED,     // 모집마감
    COMPLETED,  // 진행완료
    CANCELLED;  // 취소

    public boolean canTransitionTo(MeetupStatus target) {
        return switch (this) {
            case DRAFT -> target == OPEN;
            case OPEN -> target == CLOSED || target == CANCELLED;
            case CLOSED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
```

**Meetup 엔티티 필드** (PRD 기준):
- id, organizerId, title, description
- categoryId, regionCode, locationText
- startAt, endAt, recruitEndAt, capacity
- status, experienceLevelText
- deletedAt, version, createdAt, updatedAt

**인덱스 설계** (PRD 기준):
```java
@Table(name = "meetup", indexes = {
    @Index(name = "idx_meetup_list", columnList = "region_code, category_id, status, start_at"),
    @Index(name = "idx_meetup_recruit_end", columnList = "recruit_end_at"),
    @Index(name = "idx_meetup_organizer", columnList = "organizer_id")
})
```

### Phase 2: Repository 계층

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 5 | CategoryRepository 생성 | `repository/CategoryRepository.java` | #2 | 낮음 | AC-MEETUP-01 |
| 6 | RegionRepository 생성 | `repository/RegionRepository.java` | #3 | 낮음 | AC-MEETUP-01 |
| 7 | MeetupRepository 생성 | `repository/MeetupRepository.java` | #4 | 중간 | AC-MEETUP-01, AC-MEETUP-02 |

**MeetupRepository 주요 메서드**:
```java
// 기본 조회 (삭제되지 않은 모임)
Optional<Meetup> findByIdAndDeletedAtIsNull(Long id);

// 비관적 락 조회 (M3/M4 동시성 제어용)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
Optional<Meetup> findByIdForUpdate(@Param("id") Long id);

// 목록 조회 (필터링 + 페이징)
Page<Meetup> findByFilters(regionCode, categoryId, status, startFrom, startTo, pageable);
```

### Phase 3: DTO 정의

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 8 | CreateMeetupRequest 생성 | `dto/CreateMeetupRequest.java` | 없음 | 낮음 | AC-MEETUP-01 |
| 9 | UpdateMeetupRequest 생성 | `dto/UpdateMeetupRequest.java` | #1 | 낮음 | AC-MEETUP-01 |
| 10 | MeetupResponse 생성 | `dto/MeetupResponse.java` | #1, #4 | 낮음 | AC-MEETUP-01 |
| 11 | MeetupListResponse 생성 | `dto/MeetupListResponse.java` | #1, #4 | 낮음 | AC-MEETUP-01 |
| 12 | MeetupSearchCondition 생성 | `dto/MeetupSearchCondition.java` | #1 | 낮음 | AC-MEETUP-01 |

**CreateMeetupRequest Bean Validation**:
```java
public record CreateMeetupRequest(
    @NotBlank @Size(max = 100) String title,
    @Size(max = 2000) String description,
    @NotNull Long categoryId,
    @NotBlank @Size(max = 50) String regionCode,
    @NotBlank @Size(max = 500) String locationText,
    @NotNull @Future LocalDateTime startAt,
    @NotNull @Future LocalDateTime endAt,
    @NotNull @Future LocalDateTime recruitEndAt,
    @NotNull @Min(2) @Max(100) Integer capacity,
    @Size(max = 200) String experienceLevelText
) {}
```

### Phase 4: Service 계층

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 13 | MeetupService 생성 | `service/MeetupService.java` | #5-12 | 중간 | AC-MEETUP-01~03, AC-AUTH-03 |

**MeetupService 메서드**:
- `createMeetup()`: 모임 생성 (도메인 규칙 검증)
- `getMeetup()`: 상세 조회 (삭제된 모임 404)
- `getMeetupResponse()`: 상세 응답 조회 (계층 분리)
- `getMeetupsResponse()`: 목록 응답 조회 (N+1 방지)
- `updateMeetup()`: 수정 (작성자 권한 검증, 상태 전이 검증)
- `updateMeetupAndGetResponse()`: 수정 응답 반환 (계층 분리)
- `deleteMeetup()`: 소프트 삭제 (작성자 권한 검증)
- `validateRecruitmentOpen()`: 모집 마감 검증 (M3에서 사용)
- `validateMeetupOpen()`: OPEN 상태 검증 (M3에서 사용)

**MeetupResponseAssembler 역할**:
- 응답 DTO 조립을 담당 (서비스 책임 분리)
- 목록 조회 시 카테고리/지역 이름 배치 조회로 N+1 방지

**도메인 규칙 검증**:
- `recruitEndAt <= startAt` (모집 마감은 시작 전)
- `startAt < endAt` (시작은 종료 전)
- 카테고리/지역 존재 검증
- 작성자 권한 검증 (organizerId == currentUserId)
- 필드 수정은 OPEN 상태에서만 허용 (상태 전이만 요청하는 경우는 예외)
- 변경 사항이 없는 수정 요청은 REQ-400 처리

### Phase 5: Controller 계층

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 14 | MeetupController 생성 | `controller/MeetupController.java` | #13 | 중간 | AC-MEETUP-01~02, AC-AUTH-03 |

**API 엔드포인트**:
```
POST   /api/meetups           # 모임 생성 (인증 필수)
GET    /api/meetups           # 목록 조회 (공개)
GET    /api/meetups/{id}      # 상세 조회 (공개)
PUT    /api/meetups/{id}      # 수정 (작성자만)
DELETE /api/meetups/{id}      # 소프트 삭제 (작성자만)
```

**목록 조회 파라미터**:
- `regionCode`: 지역 코드 필터
- `categoryId`: 카테고리 ID 필터
- `status`: 모임 상태 필터 (기본: OPEN)
- `startFrom`, `startTo`: 시작 시간 범위 필터
- `sort`: 정렬 (startAt: 가까운 일정순, createdAt: 최신순)
- `page`, `size`: 페이징

### Phase 6: 테스트 작성

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 15 | MeetupStatus 단위 테스트 | `test/.../MeetupStatusTest.java` | #1 | 낮음 | AC-MEETUP-01 |
| 16 | MeetupService 단위 테스트 | `test/.../MeetupServiceTest.java` | #13 | 중간 | AC-MEETUP-01~03, AC-AUTH-03 |
| 17 | MeetupController 통합 테스트 | `test/.../MeetupIntegrationTest.java` | #14 | 중간 | AC-MEETUP-01~02, AC-AUTH-03 |

## 테스트 전략

### 단위 테스트
- `MeetupStatusTest`: 상태 전이 규칙 검증 (DRAFT→OPEN 허용, DRAFT→CLOSED 금지 등)
- `MeetupServiceTest`: 도메인 규칙 검증, 권한 검증, 상태 전이 검증

### 통합 테스트
```java
@Test
@DisplayName("AC-MEETUP-01: OPEN 모임 필터로 조회 가능")
void getMeetups_filterByRegionAndCategory_returnsFilteredResults() {
    // Given: OPEN 상태 모임 여러 개 생성 (다양한 region/category)
    // When: regionCode=SEOUL_GANGNAM, categoryId=1 필터로 조회
    // Then: 해당 조건에 맞는 모임만 반환
}

@Test
@DisplayName("AC-MEETUP-02: 소프트 삭제된 모임은 RES-404")
void getMeetup_deletedMeetup_returns404() {
    // Given: 모임 생성 후 소프트 삭제
    // When: 상세 조회
    // Then: RES-404 반환
}

@Test
@DisplayName("AC-AUTH-03: 타인 모임 수정 시 AUTH-403")
void updateMeetup_byNonOrganizer_returns403() {
    // Given: 사용자A가 모임 생성
    // When: 사용자B가 수정 시도
    // Then: AUTH-403 반환
}
```

## 리스크 및 대응

| 리스크 | 수준 | 대응 |
|--------|------|------|
| N+1 쿼리 (목록 조회 시 카테고리/지역 조회) | 중간 | 배치 조회로 해결, M5에서 MyBatis 전환 검토 |
| 동적 필터 쿼리 인덱스 활용 저해 | 중간 | OR 패턴 사용, M5에서 Querydsl/MyBatis 전환 |
| 상세 조회 시 N+1 (User/Category/Region) | 낮음 | 단건 조회는 허용, 빈번한 경우 JOIN FETCH 적용 |
| 비관적 락 데드락 | 중간 | 타임아웃 3초 설정으로 방지 |

## 성공 기준 (Done Definition)

- [x] AC-MEETUP-01: OPEN 모임 필터(region/category/time/status) 조회 동작
- [x] AC-MEETUP-02: 소프트 삭제 모임 목록/상세에서 404 반환
- [x] AC-AUTH-03: 타인 모임 수정/삭제 시 403 반환
- [x] 상태 전이 규칙 단위 테스트 통과 (DRAFT→OPEN→CLOSED→COMPLETED)
- [x] 테스트 커버리지 80%+ (meetup 도메인)
- [x] 빌드 성공 (`./gradlew build`)

> **참고**: AC-MEETUP-03 (recruitEndAt 이후 신청/승인 시 MEETUP-409-DEADLINE)은 M3에서 검증 (참여 기능 필요)

## 파일 단위 체크리스트 (M2)

### 엔티티
- [x] `domain/meetup/entity/MeetupStatus.java`: 모임 상태 enum (상태 전이 규칙 포함)
- [x] `domain/meetup/entity/Category.java`: 카테고리 엔티티
- [x] `domain/meetup/entity/Region.java`: 지역 엔티티
- [x] `domain/meetup/entity/Meetup.java`: 모임 엔티티 (인덱스 설계 포함)

### Repository
- [x] `domain/meetup/repository/CategoryRepository.java`: 카테고리 Repository
- [x] `domain/meetup/repository/RegionRepository.java`: 지역 Repository
- [x] `domain/meetup/repository/MeetupRepository.java`: 모임 Repository (비관적 락 + 타임아웃)

### DTO
- [x] `domain/meetup/dto/CreateMeetupRequest.java`: 생성 요청 (Bean Validation)
- [x] `domain/meetup/dto/UpdateMeetupRequest.java`: 수정 요청
- [x] `domain/meetup/dto/MeetupResponse.java`: 상세 응답
- [x] `domain/meetup/dto/MeetupListResponse.java`: 목록 응답 (경량화)
- [x] `domain/meetup/dto/MeetupSearchCondition.java`: 검색 조건

### Service
- [x] `domain/meetup/service/MeetupService.java`: 모임 비즈니스 로직

### Controller
- [x] `domain/meetup/controller/MeetupController.java`: 모임 API (계층 분리 준수)

### 테스트
- [x] `test/.../entity/MeetupStatusTest.java`: 상태 전이 단위 테스트
- [x] `test/.../service/MeetupServiceTest.java`: 서비스 단위 테스트
- [x] `test/.../controller/MeetupIntegrationTest.java`: API 통합 테스트

## 예상 작업량

| Phase | 작업 수 | 복잡도 |
|-------|---------|--------|
| Phase 1: 엔티티 | 4 | 낮음~중간 |
| Phase 2: Repository | 3 | 중간 |
| Phase 3: DTO | 5 | 낮음 |
| Phase 4: Service | 1 | 중간 |
| Phase 5: Controller | 1 | 중간 |
| Phase 6: 테스트 | 3 | 중간 |

**총 17개 작업**, Phase별 순차 진행

## 코드 리뷰 결과 반영

### 해결된 이슈
- **계층 분리 위반 수정**: Controller에서 Repository 직접 참조 제거, Service로 응답 DTO 생성 로직 이동
- **비관적 락 타임아웃 설정**: 3초 타임아웃으로 데드락 방지

### M5에서 해결 예정 (성능 튜닝)
- `findByFilters` OR 패턴 → 동적 쿼리(Querydsl/MyBatis) 전환
- 상세 조회 N+1 → JOIN FETCH 또는 배치 조회 적용

## 다음 단계

- M2 완료 후 `feature/M2-meetup-crud` → `develop` PR 생성
- M3 참여(신청/취소/승인/거절) 구현 착수
  - `POST /api/meetups/{id}/participations` - 신청
  - `DELETE /api/meetups/{id}/participations/me` - 취소
  - `PATCH /api/meetups/{id}/participations/{id}/approve` - 승인
  - `PATCH /api/meetups/{id}/participations/{id}/reject` - 거절
- M4 동시성 증명 (AC-PART-02: 정원 10명에 100명 동시 승인)
