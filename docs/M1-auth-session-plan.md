# M1 — 인증/인가(세션) 구현 계획서

> 기준 문서: `/.claude/prd.md`, `/.claude/rules/security.md`, `/.claude/rules/testing.md`

## 개요

M0(프로젝트 셋업) 완료 상태에서 **세션 기반 인증/인가 시스템**을 구현한다. 회원가입, 로그인, 로그아웃, 이메일 인증, 프로필 관리 및 Spring Security 기반 보안 설정을 포함한다.

## 현재 상태 (M0 완료 항목)

| 항목 | 상태 | 파일 |
|------|------|------|
| Spring Boot 프로젝트 구조 | 완료 | `build.gradle` |
| 공통 예외/응답 포맷 | 완료 | `ErrorCode`, `ErrorResponse`, `ApiResponse`, `BusinessException`, `GlobalExceptionHandler` |
| traceId 로깅 | 완료 | `TraceIdFilter`, `logback-spring.xml` |
| 테스트 기반 | 완료 | `GlobalExceptionHandlerTest`, `TraceIdFilterTest` |
| Security Config (임시) | 완료 | `SecurityConfig` (M1에서 강화 필요) |

## 관련 PRD 항목

- 마일스톤: **M1 — 인증/인가(세션)**
- 수용 기준: AC-AUTH-01~04, AC-PROFILE-01~02, AC-SEC-01~04
- 비기능: 세션 보안, CSRF 보호, 이메일 인증 토큰 1회성

## 수용 기준 (AC) 상세

| AC | 설명 | 검증 방법 |
|----|------|----------|
| AC-AUTH-01 | 로그인 성공 시 세션 재발급, 보호 API 200 | 통합 테스트 |
| AC-AUTH-02 | 세션 없이 보호 API → AUTH-401 | 통합 테스트 |
| AC-AUTH-03 | 타인 모임 수정/삭제 → AUTH-403 | M2에서 검증 (모임 엔티티 필요) |
| AC-AUTH-04 | 이메일 인증 confirm → isVerified=true | 통합 테스트 |
| AC-PROFILE-01 | 로그인 상태 GET /api/me → 프로필 반환 | 통합 테스트 |
| AC-PROFILE-02 | PUT /api/me 수정 후 조회에 반영 | 통합 테스트 |
| AC-SEC-01 | 쿠키 HttpOnly/Secure/SameSite 적용 | 통합 테스트 |
| AC-SEC-02 | 로그인 시 세션 재발급, 이전 세션 무효화 | 통합 테스트 |
| AC-SEC-03 | CSRF 토큰 없이 상태 변경 요청 거부 | 통합 테스트 |
| AC-SEC-04 | 이메일 토큰 만료/1회 사용 강제 | 단위/통합 테스트 |

## 아키텍처/패키지 구조 (M1 추가분)

```
src/main/java/io/heygw44/strive/
├── domain/
│   └── user/
│       ├── controller/
│       │   ├── AuthController.java
│       │   └── ProfileController.java
│       ├── service/
│       │   ├── AuthService.java
│       │   └── ProfileService.java
│       ├── repository/
│       │   ├── UserRepository.java
│       │   └── EmailVerificationTokenRepository.java
│       ├── entity/
│       │   ├── User.java
│       │   └── EmailVerificationToken.java
│       └── dto/
│           ├── SignupRequest.java
│           ├── LoginRequest.java
│           ├── ProfileResponse.java
│           ├── ProfileUpdateRequest.java
│           └── VerifyEmailConfirmRequest.java
├── global/
│   ├── config/
│   │   └── SecurityConfig.java (수정)
│   ├── security/
│   │   ├── CustomUserDetails.java
│   │   └── CustomUserDetailsService.java
│   └── exception/
│       └── ErrorCode.java (수정 - 인증 관련 코드 추가)
```

## 구현 단계

### Phase 1: User 엔티티 및 Repository 구성

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 1 | User 엔티티 생성 | `domain/user/entity/User.java` | 없음 | 낮음 | AC-AUTH-01, AC-AUTH-04 |
| 2 | EmailVerificationToken 엔티티 생성 | `domain/user/entity/EmailVerificationToken.java` | #1 | 낮음 | AC-SEC-04 |
| 3 | UserRepository 생성 | `domain/user/repository/UserRepository.java` | #1 | 낮음 | AC-AUTH-01 |
| 4 | EmailVerificationTokenRepository 생성 | `domain/user/repository/EmailVerificationTokenRepository.java` | #2 | 낮음 | AC-SEC-04 |

**User 엔티티 필드** (PRD 기준):
- id, email, passwordHash, nickname, bioText
- preferredCategories (JSON), homeRegionCode, experienceLevel
- isVerified, createdAt, updatedAt

**EmailVerificationToken 엔티티 필드**:
- id, tokenHash, userId, expiresAt (15분), used (1회 사용)

### Phase 2: Spring Security 설정 강화

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 5 | CustomUserDetails 생성 | `global/security/CustomUserDetails.java` | #1 | 낮음 | AC-AUTH-01 |
| 6 | CustomUserDetailsService 생성 | `global/security/CustomUserDetailsService.java` | #3, #5 | 낮음 | AC-AUTH-01 |
| 7 | SecurityConfig 강화 | `global/config/SecurityConfig.java` | #6 | 중간 | AC-SEC-01~03 |
| 8 | application.yaml 세션 설정 | `resources/application.yaml` | #7 | 낮음 | AC-SEC-01 |

**SecurityConfig 주요 설정**:
```java
// 세션 정책
.sessionManagement(session -> session
    .sessionFixation().newSession()  // 세션 재발급
    .maximumSessions(1)
)
// CSRF 보호
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
)
// 쿠키 속성 (application.yaml)
server.servlet.session.cookie:
  http-only: true
  secure: true
  same-site: lax
```

### Phase 3: 인증 DTO 및 서비스

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 9 | 인증 DTO 생성 | `domain/user/dto/Signup*.java, Login*.java` | 없음 | 낮음 | AC-AUTH-01 |
| 10 | ErrorCode 추가 | `global/exception/ErrorCode.java` | 없음 | 낮음 | AC-AUTH-01, AC-SEC-04 |
| 11 | AuthService 생성 | `domain/user/service/AuthService.java` | #3, #4, #7, #10 | 중간 | AC-AUTH-01, AC-AUTH-04, AC-SEC-02, AC-SEC-04 |

**추가할 ErrorCode**:
- `INVALID_CREDENTIALS`: 로그인 실패 (계정/비밀번호 구분 안 함)
- `DUPLICATE_EMAIL`: 이메일 중복
- `DUPLICATE_NICKNAME`: 닉네임 중복
- `INVALID_PASSWORD_LENGTH`: 비밀번호 정책 위반 (최소 10자)
- `EMAIL_ALREADY_VERIFIED`: 이미 인증됨
- `VERIFICATION_TOKEN_INVALID`: 토큰 만료/사용됨/잘못됨

**AuthService 메서드**:
- `signup()`: 회원가입 (비밀번호 정책, BCrypt 해시, 중복 검증)
- `login()`: 로그인 (인증 실패 시 일관된 오류)
- `logout()`: 로그아웃 (세션 무효화)
- `requestEmailVerification()`: 인증 메일 발송 요청
- `confirmEmailVerification()`: 인증 완료 (isVerified=true)

### Phase 4: 프로필 DTO 및 서비스

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 12 | 프로필 DTO 생성 | `domain/user/dto/Profile*.java` | #1 | 낮음 | AC-PROFILE-01~02 |
| 13 | ProfileService 생성 | `domain/user/service/ProfileService.java` | #3, #12 | 낮음 | AC-PROFILE-01~02 |

**ProfileService 메서드**:
- `getMyProfile()`: 현재 사용자 프로필 조회
- `updateMyProfile()`: 프로필 수정 (닉네임 중복 검증)

### Phase 5: Controller 구현

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 14 | AuthController 생성 | `domain/user/controller/AuthController.java` | #9, #11 | 중간 | AC-AUTH-01, AC-AUTH-04 |
| 15 | ProfileController 생성 | `domain/user/controller/ProfileController.java` | #12, #13 | 낮음 | AC-PROFILE-01~02 |

**API 엔드포인트**:
```
POST /api/auth/signup          # 회원가입
POST /api/auth/login           # 로그인 (세션 재발급)
POST /api/auth/logout          # 로그아웃
POST /api/auth/verify-email/request   # 인증 메일 발송
POST /api/auth/verify-email/confirm   # 인증 완료

GET  /api/me                   # 내 프로필 조회 (인증 필수)
PUT  /api/me                   # 내 프로필 수정 (인증 필수)
```

### Phase 6: 테스트 작성

| # | 작업 | 파일 | 의존성 | 리스크 | 관련 AC |
|---|------|------|--------|--------|---------|
| 16 | AuthService 단위 테스트 | `test/.../AuthServiceTest.java` | #11 | 낮음 | AC-AUTH-01, AC-AUTH-04 |
| 17 | 인증 통합 테스트 | `test/.../AuthIntegrationTest.java` | #14 | 중간 | AC-AUTH-01~02, AC-SEC-01~04 |
| 18 | 프로필 통합 테스트 | `test/.../ProfileIntegrationTest.java` | #15 | 낮음 | AC-PROFILE-01~02 |

## 테스트 전략

### 단위 테스트
- `AuthServiceTest`: 비밀번호 정책, 중복 검증, 토큰 생성/검증 로직
- `UserTest`: 엔티티 생성, 상태 변경 메서드

### 통합 테스트
```java
@Test
@DisplayName("AC-AUTH-01: 로그인 성공 시 세션 재발급")
void loginSuccess_shouldRegenerateSession() {
    // Given: 사용자 등록
    // When: 로그인 요청
    // Then: 새 세션 발급, 보호 API 접근 가능
}

@Test
@DisplayName("AC-SEC-02: 로그인 성공 시 이전 세션 무효화")
void login_success_invalidatesPreviousSession() {
    String oldSessionId = getSessionId();
    performLogin(validCredentials);
    String newSessionId = getSessionId();
    assertThat(newSessionId).isNotEqualTo(oldSessionId);
    // 이전 세션으로 보호 API 접근 시 401
}

@Test
@DisplayName("AC-SEC-03: CSRF 토큰 없이 상태 변경 요청 거부")
void postRequest_withoutCsrfToken_returnsForbidden() {
    mockMvc.perform(post("/api/auth/logout"))
        .andExpect(status().isForbidden());
}

@Test
@DisplayName("AC-SEC-04: 이메일 토큰 재사용 시 실패")
void verifyEmail_reuseToken_fails() {
    // Given: 토큰 생성 및 1회 사용 완료
    // When: 동일 토큰으로 재검증 시도
    // Then: VERIFICATION_TOKEN_INVALID
}
```

## 리스크 및 대응

| 리스크 | 수준 | 대응 |
|--------|------|------|
| 세션 재발급 로직 복잡성 | 중간 | Spring Security `sessionFixation().newSession()` 활용, 통합 테스트로 검증 |
| CSRF 설정과 API 테스트 충돌 | 중간 | 테스트에서 `with(csrf())` 사용, API 클라이언트 CSRF 토큰 전달 방식 문서화 |
| 이메일 발송 구현 범위 | 낮음 | MVP에서는 토큰 생성/검증만 구현, 실제 메일 발송은 로그 출력으로 대체 (인터페이스 분리로 확장 가능) |
| 비밀번호 정책 검증 누락 | 낮음 | 단위 테스트에서 정책 위반 케이스 명시적 검증 |

## 성공 기준 (Done Definition)

- [x] AC-AUTH-01: 로그인 성공 시 세션 재발급, 보호 API 200 반환
- [x] AC-AUTH-02: 세션 없이 보호 API 호출 시 AUTH-401 반환
- [x] AC-AUTH-04: 이메일 인증 완료 시 isVerified=true, 프로필 조회에서 확인
- [x] AC-PROFILE-01: 로그인 상태에서 GET /api/me 프로필 반환
- [x] AC-PROFILE-02: PUT /api/me 수정 후 조회에 반영
- [x] AC-SEC-01: 세션 쿠키 HttpOnly/Secure/SameSite 적용
- [x] AC-SEC-02: 로그인 성공 시 세션 재발급, 이전 세션 무효화
- [x] AC-SEC-03: CSRF 토큰 없이 상태 변경 요청 거부
- [x] AC-SEC-04: 이메일 인증 토큰 만료/1회 사용 강제
- [x] 테스트 커버리지 80%+ (user 도메인)
- [x] 빌드 성공 (`./gradlew build`)

> **참고**: AC-AUTH-03 (타인 모임 수정/삭제 → AUTH-403)은 M2에서 검증 (모임 엔티티 필요)

## 파일 단위 체크리스트 (M1)

### 엔티티/Repository
- [x] `domain/user/entity/User.java`: 사용자 엔티티
- [x] `domain/user/entity/EmailVerificationToken.java`: 이메일 인증 토큰 엔티티
- [x] `domain/user/entity/StringListConverter.java`: JSON 변환기 (추가)
- [x] `domain/user/repository/UserRepository.java`: 사용자 Repository
- [x] `domain/user/repository/EmailVerificationTokenRepository.java`: 토큰 Repository

### Security
- [x] `global/security/CustomUserDetails.java`: UserDetails 구현체
- [x] `global/security/CustomUserDetailsService.java`: UserDetailsService 구현체
- [x] `global/config/SecurityConfig.java`: 보안 설정 강화

### DTO
- [x] `domain/user/dto/SignupRequest.java`: 회원가입 요청
- [x] `domain/user/dto/SignupResponse.java`: 회원가입 응답 (추가)
- [x] `domain/user/dto/LoginRequest.java`: 로그인 요청
- [x] `domain/user/dto/LoginResponse.java`: 로그인 응답 (추가)
- [x] `domain/user/dto/ProfileResponse.java`: 프로필 응답
- [x] `domain/user/dto/ProfileUpdateRequest.java`: 프로필 수정 요청
- [x] `domain/user/dto/VerifyEmailConfirmRequest.java`: 이메일 인증 확인 요청

### Service
- [x] `domain/user/service/AuthService.java`: 인증 서비스
- [x] `domain/user/service/ProfileService.java`: 프로필 서비스

### Controller
- [x] `domain/user/controller/AuthController.java`: 인증 API
- [x] `domain/user/controller/ProfileController.java`: 프로필 API

### 설정
- [x] `global/exception/ErrorCode.java`: 인증 관련 오류 코드 추가
- [x] `resources/application.yaml`: 세션/쿠키 설정 추가

### 테스트
- [x] `test/.../service/AuthServiceTest.java`: 인증 서비스 단위 테스트
- [x] `test/.../controller/AuthIntegrationTest.java`: 인증 통합 테스트
- [x] `test/.../controller/ProfileIntegrationTest.java`: 프로필 통합 테스트

## 예상 작업량

| Phase | 작업 수 | 복잡도 |
|-------|---------|--------|
| Phase 1: 엔티티/Repository | 4 | 낮음 |
| Phase 2: Security 설정 | 4 | 중간 |
| Phase 3: 인증 DTO/Service | 3 | 중간 |
| Phase 4: 프로필 DTO/Service | 2 | 낮음 |
| Phase 5: Controller | 2 | 중간 |
| Phase 6: 테스트 | 3 | 중간 |

**총 18개 작업**, Phase별 순차 진행 권장

## 다음 단계

- M1 완료 후 `feature/M1-auth-session` → `develop` PR 생성
- M2 모임 CRUD 구현 착수
