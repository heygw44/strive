# M4 — 동시성 증명 결과

## 개요

AC-PART-02 수용 기준("정원 10명에 100명 동시 승인 시 APPROVED 최대 10명")을 자동화 테스트로 검증하여 **정원 초과 0건**을 보장한다.

## 테스트 환경

| 항목 | 값 |
|------|-----|
| JDK | 21+ |
| DB | H2 (인메모리, 테스트용) |
| 스레드 풀 | 요청 수와 동일 (최대 동시성) |
| 동시 요청 | 최대 100건 |
| 동시 시작 동기화 | `ConcurrencyTestHelper` (ready/start/done 래치) |
| 락 타임아웃 | 3초 |

## 비관적 락 전략

### 락 획득 순서

```
1. meetupRepository.findByIdForUpdate(meetupId) — 모임 행 락 획득
2. participationRepository.findByIdForUpdate(participationId) — 참여 행 락 획득
3. 정원 검증 (countByMeetupIdAndStatus)
4. 상태 전이 (approve())
5. 트랜잭션 커밋 → 락 해제
```

### 락 설정

```java
// MeetupRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
@Query("SELECT m FROM Meetup m WHERE m.id = :id AND m.deletedAt IS NULL")
Optional<Meetup> findByIdForUpdate(@Param("id") Long id);
```

### 동시성 제어 흐름

```
Request 1 ─┬─▶ findByIdForUpdate(meetup) ─▶ [락 획득] ─▶ 정원 검증 ─▶ approve() ─▶ [커밋/락 해제]
Request 2 ─┤                                                                              │
Request 3 ─┤   [대기] ◀────────────────────────────────────────────────────────────────────┘
...        │
Request N ─┘
```

## 테스트 결과

### AC-PART-02: 100명 동시 승인

| 항목 | 값 |
|------|-----|
| 정원 | 10명 |
| 동시 요청 | 100건 |
| APPROVED | 10 (정확히 정원만큼) |
| PART-409-CAPACITY | 90 |
| **정원 초과** | **0건** |

### 다양한 정원 크기 테스트

| 정원 | 요청 수 | APPROVED | 정원초과 | 결과 |
|------|---------|----------|----------|------|
| 1 | 20 | 1 | 19 | **PASS** |
| 5 | 50 | 5 | 45 | **PASS** |
| 10 | 100 | 10 | 90 | **PASS** |
| 20 | 50 | 20 | 30 | **PASS** |

### AC-PART-03: 취소 + 승인 동시 발생

| 항목 | 값 |
|------|-----|
| 초기 정원 | 10명 (만석) |
| 동시 취소 요청 | 5건 |
| 동시 승인 요청 | 10건 |
| 취소 성공 | 5건 |
| 최종 APPROVED | ≤ 10 (정원 이하 보장) |
| **정원 초과** | **0건** |

## 테스트 파일

```
src/test/java/io/heygw44/strive/domain/participation/service/ParticipationConcurrencyTest.java
```

### 테스트 메서드

| 메서드 | 설명 | AC |
|--------|------|-----|
| `approveParticipation_concurrent100Requests_maxApproved10` | 100명 동시 승인 | AC-PART-02 |
| `approveParticipation_variousCapacity_noOverflow` | 다양한 정원 크기 | AC-PART-02 |
| `cancelAndApprove_concurrent_maintainsCapacityConsistency` | 취소+승인 동시 | AC-PART-03 |

## 테스트 실행 방법

```bash
# 전체 동시성 테스트
./gradlew test --tests "*ParticipationConcurrencyTest"

# 특정 테스트만
./gradlew test --tests "*ParticipationConcurrencyTest.approveParticipation_concurrent100Requests_maxApproved10"

# 강제 재실행
./gradlew test --tests "*ParticipationConcurrencyTest" --rerun
```

## 핵심 코드 (ParticipationService.approveParticipation)

```java
@Transactional
public ParticipationResponse approveParticipation(Long meetupId, Long participationId, Long organizerId) {
    // 1. 비관적 락으로 모임 조회 (동시성 제어 - AC-PART-02)
    Meetup meetup = meetupRepository.findByIdForUpdate(meetupId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

    // 2. Organizer 권한 검증
    validateOrganizer(meetup, organizerId);

    // 3. 모임 상태/마감일 검증
    validateMeetupOpenForParticipation(meetup);

    // 4. 참여 조회 (락)
    Participation participation = getParticipationForUpdateOrThrow(participationId);

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

## 결론

- **AC-PART-02 통과**: 정원 10명 모임에 100명 동시 승인 요청 시 APPROVED 최대 10명, 초과 0건
- **AC-PART-03 통과**: 취소와 승인 동시 발생 시에도 정원 일관성 유지
- **비관적 락 전략**: `PESSIMISTIC_WRITE` 락으로 모임 행을 직렬화하여 정원 검증의 원자성 보장
- **테스트 안정성**: 연속 3회 이상 테스트 통과 확인
