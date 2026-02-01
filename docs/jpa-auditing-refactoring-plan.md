# JPA Auditing 리팩토링 계획

## 개요

현재 프로젝트의 엔티티들(User, Meetup, Participation, EmailVerificationToken)은 `createdAt`/`updatedAt` 필드를 수동으로 `LocalDateTime.now()`를 호출하여 설정하고 있다. Spring Data JPA의 Auditing 기능을 활용하여 이 작업을 자동화하고, 코드 중복을 제거하며 일관성을 보장한다.

## 관련 마일스톤

- **마일스톤**: M0 (프로젝트 셋업 개선)
- **품질 게이트**: 데이터 무결성, 아키텍처 일관성

---

## 현재 상태 분석 (리팩토링 전 기준)

### 영향받는 엔티티

| 엔티티 | createdAt | updatedAt | 특이사항 |
|--------|:---------:|:---------:|----------|
| User | O | O | 생성자, `updateProfile()`, `verifyEmail()` |
| Meetup | O | O | 생성자, `update()`, `transitionTo()`, `softDelete()` |
| Participation | O | O | 생성자, `transitionTo()` |
| EmailVerificationToken | O | X | 생성자만 |
| Category | X | X | 타임스탬프 없음 |
| Region | X | X | 타임스탬프 없음 |

### 수동 설정 위치 (제거 대상)

```
User.java
├── 생성자: this.createdAt = LocalDateTime.now();
├── 생성자: this.updatedAt = LocalDateTime.now();
├── updateProfile(): this.updatedAt = LocalDateTime.now();
└── verifyEmail(): this.updatedAt = LocalDateTime.now();

Meetup.java
├── 생성자: this.createdAt = LocalDateTime.now();
├── 생성자: this.updatedAt = LocalDateTime.now();
├── update(): this.updatedAt = LocalDateTime.now();
├── transitionTo(): this.updatedAt = LocalDateTime.now();
└── softDelete(): this.updatedAt = LocalDateTime.now();

Participation.java
├── 생성자: this.createdAt = LocalDateTime.now();
├── 생성자: this.updatedAt = LocalDateTime.now();
└── transitionTo(): this.updatedAt = LocalDateTime.now();

EmailVerificationToken.java
└── 생성자: this.createdAt = LocalDateTime.now();
```

---

## 아키텍처 변경

### 신규 파일

| 파일 경로 | 설명 |
|----------|------|
| `global/config/JpaAuditingConfig.java` | JPA Auditing 활성화 설정 |
| `global/entity/BaseTimeEntity.java` | 공통 타임스탬프 추상 클래스 |

### 수정 파일

| 파일 경로 | 변경 내용 |
|----------|----------|
| `domain/user/entity/User.java` | BaseTimeEntity 상속, 수동 타임스탬프 설정 제거 |
| `domain/meetup/entity/Meetup.java` | BaseTimeEntity 상속, 수동 타임스탬프 설정 제거 |
| `domain/participation/entity/Participation.java` | BaseTimeEntity 상속, 수동 타임스탬프 설정 제거 |
| `domain/user/entity/EmailVerificationToken.java` | @CreatedDate만 적용 (BaseTimeEntity 미상속) |

---

## 구현 단계

### Phase 1: 기반 구조 생성

#### 1-1. JpaAuditingConfig 생성

**파일**: `src/main/java/io/heygw44/strive/global/config/JpaAuditingConfig.java`

```java
package io.heygw44.strive.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
```

- **작업**: `@EnableJpaAuditing` 어노테이션을 포함한 설정 클래스 생성
- **이유**: JPA Auditing 기능 활성화를 위한 Spring 설정 필요
- **의존성**: 없음
- **리스크**: 낮음

#### 1-2. BaseTimeEntity 생성

**파일**: `src/main/java/io/heygw44/strive/global/entity/BaseTimeEntity.java`

```java
package io.heygw44.strive.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- **작업**: `@MappedSuperclass` 추상 클래스 생성, `@CreatedDate`/`@LastModifiedDate` 적용
- **이유**: 코드 중복 제거, 타임스탬프 관리 일관성 확보
- **의존성**: Phase 1-1 완료
- **리스크**: 낮음

---

### Phase 2: 엔티티 마이그레이션

#### 2-1. User 엔티티 수정

**파일**: `src/main/java/io/heygw44/strive/domain/user/entity/User.java`

**변경 전**:
```java
public class User {
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private User(...) {
        // ...
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProfile(...) {
        // ...
        this.updatedAt = LocalDateTime.now();
    }

    public void verifyEmail() {
        this.isVerified = true;
        this.updatedAt = LocalDateTime.now();
    }
}
```

**변경 후**:
```java
public class User extends BaseTimeEntity {
    // createdAt, updatedAt 필드 제거

    private User(...) {
        // ...
        // createdAt, updatedAt 설정 제거
    }

    public void updateProfile(...) {
        // ...
        // this.updatedAt = LocalDateTime.now(); 제거
    }

    public void verifyEmail() {
        this.isVerified = true;
        // this.updatedAt = LocalDateTime.now(); 제거
    }
}
```

#### 2-2. Meetup 엔티티 수정

**파일**: `src/main/java/io/heygw44/strive/domain/meetup/entity/Meetup.java`

- `BaseTimeEntity` 상속 추가
- `createdAt`/`updatedAt` 필드 제거
- 생성자 및 `update()`, `transitionTo()`, `softDelete()` 메서드에서 수동 타임스탬프 설정 제거

#### 2-3. Participation 엔티티 수정

**파일**: `src/main/java/io/heygw44/strive/domain/participation/entity/Participation.java`

- `BaseTimeEntity` 상속 추가
- `createdAt`/`updatedAt` 필드 제거
- 생성자 및 `transitionTo()` 메서드에서 수동 타임스탬프 설정 제거

#### 2-4. EmailVerificationToken 엔티티 수정

**파일**: `src/main/java/io/heygw44/strive/domain/user/entity/EmailVerificationToken.java`

이 엔티티는 `updatedAt`이 없으므로 BaseTimeEntity 상속 대신 직접 적용한다.

**변경 후**:
```java
@Entity
@EntityListeners(AuditingEntityListener.class)
public class EmailVerificationToken {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 생성자에서 this.createdAt = LocalDateTime.now(); 제거
}
```

---

### Phase 3: 검증

#### 3-1. 기존 테스트 실행

```bash
./gradlew test
```

- **작업**: 전체 테스트 스위트 실행
- **이유**: 리팩토링이 기존 기능에 영향을 주지 않는지 확인
- **의존성**: Phase 2 완료

#### 3-2. 빌드 확인

```bash
./gradlew build
```

---

## 테스트 전략

### 기존 테스트 (변경 없음)

- `AuthIntegrationTest`: 회원가입/로그인 플로우 검증
- `ProfileIntegrationTest`: 프로필 수정 후 응답에 타임스탬프 포함 확인
- `MeetupIntegrationTest`: 모임 CRUD 플로우 검증
- `ParticipationIntegrationTest`: 참여 상태 전이 검증
- `ParticipationConcurrencyTest`: 동시성 제어 검증

### 검증 포인트

- 엔티티 생성 시 `createdAt` 자동 설정
- 엔티티 수정 시 `updatedAt` 자동 갱신
- `createdAt`은 생성 후 변경되지 않음 (`updatable = false`)
- API 응답에서 타임스탬프 필드 정상 반환

---

## 리스크 및 대응

| 리스크 | 영향도 | 대응 방안 |
|--------|:------:|----------|
| 테스트에서 타임스탬프 검증 실패 | 낮음 | `@SpringBootTest`에서 JPA Auditing 자동 활성화됨 |
| 기존 DB 데이터와의 호환성 | 없음 | 컬럼 정의 변경 없음, 기존 데이터 영향 없음 |
| `@DataJpaTest` 사용 시 Auditing 미활성화 | 중간 | `@Import(JpaAuditingConfig.class)` 추가 |

---

## 파일 변경 요약

### 신규 생성 (2개)

```
src/main/java/io/heygw44/strive/global/config/JpaAuditingConfig.java
src/main/java/io/heygw44/strive/global/entity/BaseTimeEntity.java
```

### 수정 (4개)

```
src/main/java/io/heygw44/strive/domain/user/entity/User.java
src/main/java/io/heygw44/strive/domain/meetup/entity/Meetup.java
src/main/java/io/heygw44/strive/domain/participation/entity/Participation.java
src/main/java/io/heygw44/strive/domain/user/entity/EmailVerificationToken.java
```

---

## 진행 현황

- [x] `JpaAuditingConfig` 추가
- [x] `BaseTimeEntity` 추가
- [x] User/Meetup/Participation에 BaseTimeEntity 적용
- [x] EmailVerificationToken에 @CreatedDate 적용
- [x] 수동 `LocalDateTime.now()` 타임스탬프 설정 제거
- [x] `./gradlew test` 성공 (2026-02-01 21:45:15)

---

**문서 작성일**: 2026-02-01
