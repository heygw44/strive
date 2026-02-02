# M4 — 동시성 증명 (Concurrency Proof) 구현 계획

## 개요

M3에서 구현된 참여 승인 기능에 대해 **비관적 락 기반 동시성 제어가 올바르게 동작함을 증명**한다. PRD에 정의된 AC-PART-02("정원 10명 모임에 100명 동시 승인 시 APPROVED 최대 10명, 초과 시 PART-409-CAPACITY")를 자동화 테스트로 검증하여 "정원 초과 0건"을 보장한다.

## 현재 상태 분석 (M3 완료)

### 이미 구현된 동시성 제어 메커니즘

| 항목 | 상태 | 파일/메서드 | 설명 |
|------|------|------------|------|
| 모임 비관적 락 | ✅ 완료 | `MeetupRepository.findByIdForUpdate()` | `PESSIMISTIC_WRITE` + 3초 타임아웃 |
| 참여 비관적 락 | ✅ 완료 | `ParticipationRepository.findByIdForUpdate()` | `PESSIMISTIC_WRITE` |
| 정원 검증 로직 | ✅ 완료 | `ParticipationService.validateCapacity()` | APPROVED 카운트 < capacity 검증 |
| 승인 트랜잭션 | ✅ 완료 | `ParticipationService.approveParticipation()` | 모임 락 → 참여 락 → 정원 검증 → 상태 전이 |
| 중복 신청 동시성 테스트 | ✅ 완료 | `ParticipationConcurrencyTest` | 10개 동시 요청에서 1건만 성공 |

### 현재 approveParticipation() 흐름

```java
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
    Participation participation = getParticipationForUpdateOrThrow(participationId);
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

    return toResponse(participation);
}
```

### 미비한 부분 (M4에서 해결)

1. **AC-PART-02 동시성 테스트 누락**: 100건 동시 승인 요청 시나리오 테스트가 없음
2. **AC-PART-03 정원 로직 일관성 테스트**: APPROVED 취소 후 새 승인 시나리오의 동시성 검증 누락
3. **락 타임아웃/데드락 대응**: 문서화 및 테스트 시나리오 부재

## 관련 PRD 항목

- **마일스톤**: M4 — 동시성 증명 (마감 핵심)
- **핵심 수용 기준**: AC-PART-02
- **관련 수용 기준**: AC-PART-03 (정원 로직 일관성)

| AC | 설명 | 현재 상태 |
|----|------|----------|
| AC-PART-02 | 정원 10명에 100명 동시 승인 → APPROVED 최대 10명, 초과 PART-409-CAPACITY | ❌ 미검증 |
| AC-PART-03 | APPROVED 취소 시 CANCELLED 전이, 다른 참가자 승인 가능 (정원 로직 일관성) | ⚠️ 순차 테스트만 완료 |

## 아키텍처 변경

**변경 필요 없음** - M3에서 이미 올바른 동시성 제어 구조가 구현되어 있음.

M4는 기존 구현이 올바르게 동작함을 **증명하는 테스트**에 집중한다.

---

## 구현 단계

### Phase 1: 동시성 테스트 인프라 준비

#### 1-1. 테스트 헬퍼 클래스 생성 (선택적)

**파일**: `src/test/java/io/heygw44/strive/support/ConcurrencyTestHelper.java`

**작업**:
- `ExecutorService`, `CountDownLatch` 기반 동시 요청 실행 유틸리티
- 성공/실패 카운트 집계 로직
- 예외 수집 및 검증 헬퍼

**이유**: 동시성 테스트 보일러플레이트 코드 중복 제거

✅ **구현 완료**: `ConcurrencyTestHelper` 도입으로 동시 시작 동기화 및 예외 수집 로직 공통화

---

### Phase 2: AC-PART-02 동시성 테스트 작성

#### 2-1. 100명 동시 승인 테스트

**파일**: `src/test/java/io/heygw44/strive/domain/participation/service/ParticipationConcurrencyTest.java`

**작업**:
- 정원 10명 모임 생성
- 100명의 REQUESTED 상태 참가자 생성
- 100개의 동시 승인 요청 실행
- APPROVED 최대 10명 검증
- 나머지 90개는 PART-409-CAPACITY 반환 검증

**관련 AC**: AC-PART-02

#### 2-2. 다양한 정원 크기 테스트 (Parameterized)

**작업**:
- `@ParameterizedTest`로 다양한 capacity (1, 5, 10, 20)와 요청 수 조합 테스트
- 모든 케이스에서 정원 초과 0건 보장

---

### Phase 3: AC-PART-03 정원 로직 일관성 동시성 테스트

#### 3-1. 취소 + 승인 동시 요청 테스트

**작업**:
- 정원 만석 상태에서 취소와 새 승인이 동시에 발생하는 시나리오
- 취소로 빈 자리가 생기면 대기 중인 승인이 성공해야 함
- 전체적으로 APPROVED <= capacity 유지

**관련 AC**: AC-PART-03

---

### Phase 4: 락 타임아웃/경합 시나리오 테스트

#### 4-1. 락 타임아웃 테스트

**작업**:
- 장시간 락 점유 시나리오 (의도적 지연)
- 다른 요청의 타임아웃 발생 확인
- 적절한 예외 처리 검증

---

### Phase 5: 테스트 데이터 생성 헬퍼 메서드

#### 5-1. ParticipationConcurrencyTest 헬퍼 메서드 추가

**작업**:
- 대량 사용자 생성 헬퍼
- 대량 참여 생성 헬퍼
- APPROVED 상태 참여 생성 헬퍼

---

### Phase 6: 문서화 및 산출물

#### 6-1. 동시성 테스트 결과 문서

**파일**: `docs/M4-concurrency-proof.md`

**작업**:
- AC-PART-02 테스트 결과 기록
- 비관적 락 전략 설명
- 테스트 환경 및 재현 방법

#### 6-2. codebase-guide.md 업데이트

**작업**:
- M4 동시성 테스트 섹션 추가
- 테스트 실행 명령어 추가

---

## 파일 단위 체크리스트

### 신규 생성

| 파일 | 설명 | Phase |
|------|------|-------|
| `src/test/java/.../support/ConcurrencyTestHelper.java` (선택) | 동시성 테스트 유틸리티 | 1 |
| `docs/M4-concurrency-proof.md` | 동시성 증명 결과 문서 | 6 |

### 수정

| 파일 | 설명 | Phase |
|------|------|-------|
| `src/test/java/.../participation/service/ParticipationConcurrencyTest.java` | AC-PART-02 동시성 테스트 추가 | 2, 3, 4, 5 |
| `docs/codebase-guide.md` | M4 테스트 섹션 추가 | 6 |

### 변경 없음 (검증만)

| 파일 | 설명 |
|------|------|
| `ParticipationService.java` | 기존 구현 검증 |
| `MeetupRepository.java` | `findByIdForUpdate()` 검증 |
| `ParticipationRepository.java` | `findByIdForUpdate()` 검증 |

---

## 리스크 및 대응

| 리스크 | 수준 | 대응 |
|--------|------|------|
| 동시성 테스트 불안정성 (Flaky) | 높음 | 충분한 타임아웃 설정, 재시도 로직, `@RepeatedTest` 활용 |
| H2 vs MySQL 락 동작 차이 | 중간 | H2에서 기본 검증 후, MySQL Docker 환경에서 추가 검증 (M5) |
| 테스트 실행 시간 증가 | 낮음 | 동시성 테스트를 별도 그룹으로 분리 가능 |
| 데드락 발생 | 낮음 | 락 획득 순서 일관성 유지 (Meetup → Participation), 3초 타임아웃 |

---

## 성공 기준 (Done Definition)

- [x] **AC-PART-02 통과**: `approveParticipation_concurrent100Requests_maxApproved10` 테스트 성공
- [x] 다양한 정원 크기 테스트 통과 (1, 5, 20)
- [x] 취소+승인 동시 시나리오 테스트 통과 (AC-PART-03 동시성)
- [x] 예상치 못한 예외 0건 (`unexpectedErrors.isEmpty()`)
- [x] 테스트 반복 실행 안정성 확인 (3회 연속 성공)
- [x] 문서화 완료 (`docs/M4-concurrency-proof.md`)
- [x] 빌드 성공 (`./gradlew test`)

**M4 완료일: 2026-02-02**

---

## 테스트 실행 명령어

```bash
# 전체 동시성 테스트
./gradlew test --tests "*ParticipationConcurrencyTest"

# 특정 테스트만
./gradlew test --tests "*ParticipationConcurrencyTest.approveParticipation_concurrent100Requests_maxApproved10"

# 전체 테스트 (빌드 검증)
./gradlew test
```

---

## 다음 단계 (M5 연계)

M4 완료 후:
1. `feature/M4-concurrency` → `main` PR 생성
2. M5 성능 튜닝 착수
   - MySQL Docker 환경에서 동시성 테스트 재검증
   - 목록 조회 p95 300ms 목표 달성
   - EXPLAIN 분석 및 인덱스 최적화
