# Strive 코드베이스 가이드 (현재 구현 범위)

이 문서는 **현재까지 구현된 범위(M0/M2)**의 주요 코드가 **어떻게 동작하고 왜 이렇게 작성되었는지**를 설명한다.  
패키지 구조, 요청 흐름, 예외/응답 규격, 보안 설정, 테스트 구성까지 정리한다.

---

## 1) 애플리케이션 구조 개요

```
io.heygw44.strive
├── StriveApplication              # Spring Boot 엔트리포인트
├── domain
│   ├── user                       # 사용자/인증/프로필 도메인
│   └── meetup                     # 모임 CRUD/조회 도메인
├── global
│   ├── config                     # 보안/스프링 설정
│   ├── exception                  # 예외 코드/핸들러
│   ├── filter                     # 공통 필터
│   ├── response                   # 공통 응답 포맷
│   └── security                   # 커스텀 UserDetails
└── support                        # 테스트용 컨트롤러
```

### 핵심 설계 의도
- **Controller는 얇게**, Service는 비즈니스 규칙을 담당.
- **도메인 엔티티가 상태 변경 책임**을 가진다.
- 예외는 **ErrorCode + BusinessException**으로 일관 처리.
- 보안(세션/CSRF)은 **Spring Security 표준 흐름**을 따른다.

---

## 2) 애플리케이션 시작

### `StriveApplication`
Spring Boot 앱의 엔트리 포인트다. 별도 설정 없이 기본 자동 구성으로 부팅된다.  
파일: `src/main/java/io/heygw44/strive/StriveApplication.java`

---

## 3) 공통 응답 포맷

### `ApiResponse<T>`
모든 정상 응답은 `{ data, traceId }` 형식으로 반환한다.  
`traceId`는 MDC에 저장된 값을 사용한다.
- 파일: `src/main/java/io/heygw44/strive/global/response/ApiResponse.java`

### `ErrorResponse`
에러는 `{ code, message, traceId, fieldErrors }` 형식으로 반환한다.  
Validation 에러는 `fieldErrors`에 필드별 메시지를 담는다.
- 파일: `src/main/java/io/heygw44/strive/global/response/ErrorResponse.java`
- 파일: `src/main/java/io/heygw44/strive/global/response/FieldError.java`

### `PageResponse<T>`
목록 응답을 위한 표준 페이지 포맷(모임 목록 응답에 사용).
- 파일: `src/main/java/io/heygw44/strive/global/response/PageResponse.java`

---

## 4) 공통 예외 처리

### `ErrorCode`
코드/메시지/HTTP 상태를 정의한다. 인증/검증/리소스 관련 에러들이 포함됨.
- 파일: `src/main/java/io/heygw44/strive/global/exception/ErrorCode.java`

### `BusinessException`
도메인/서비스 계층에서 비즈니스 규칙 위반 시 발생시키는 예외.
- 파일: `src/main/java/io/heygw44/strive/global/exception/BusinessException.java`

### `GlobalExceptionHandler`
모든 예외를 `ErrorResponse`로 매핑한다.
- `BusinessException` → 해당 ErrorCode로 응답
- `MethodArgumentNotValidException` → REQ-400 + fieldErrors
- `SessionAuthenticationException` → 동시 로그인 제한 처리
  - 파일: `src/main/java/io/heygw44/strive/global/exception/GlobalExceptionHandler.java`

---

## 5) Trace ID 필터 (요청 추적)

### `TraceIdFilter`
요청 헤더 `X-Trace-Id`를 확인하여 없으면 UUID 생성.  
MDC에 저장하고 응답 헤더에도 동일한 값을 내려준다.
- 파일: `src/main/java/io/heygw44/strive/global/filter/TraceIdFilter.java`

**의도**: 로그와 응답을 연동해 요청 단위 추적이 가능하게 한다.

---

## 6) 보안 설정

### `SecurityConfig`
Spring Security의 핵심 정책을 정의한다.
- CSRF: 쿠키 기반 토큰(`CookieCsrfTokenRepository`) 사용, 로그인/회원가입은 CSRF 제외
- 세션:
  - `sessionFixation().newSession()`으로 세션 고정 공격 방지
  - `maximumSessions(1)`로 동시 로그인 제한
  - `SessionAuthenticationStrategy`를 직접 적용하여 로그인 시 위 정책이 실제로 실행되도록 구성
- 인가:
  - `/api/auth/signup`, `/api/auth/login` 공개
  - `/api/me/**`, `/api/auth/logout`, `/api/auth/verify-email/**`는 인증 필요
  - `/api/meetups/**`, `/api/participations/**`는 메소드별 인증 요구
- 인증 실패 시 401로 응답

파일: `src/main/java/io/heygw44/strive/global/config/SecurityConfig.java`

---

## 7) 인증/회원 도메인

### 7.1 User 엔티티
`User`는 사용자 기본 정보 + 프로필 정보를 가진다.
- 이메일/닉네임은 유니크
- `isVerified`로 이메일 인증 여부 관리
- `updateProfile`은 **null이 아닌 값만 반영**(부분 업데이트)
- `verifyEmail()`로 인증 상태 전환

파일: `src/main/java/io/heygw44/strive/domain/user/entity/User.java`

### 7.2 EmailVerificationToken
이메일 인증 토큰을 관리한다.
- 토큰은 UUID로 생성, 해시 값만 저장
- 만료 시간 15분
- `isValid()`가 유효성 판단(미사용 + 만료 전)

파일: `src/main/java/io/heygw44/strive/domain/user/entity/EmailVerificationToken.java`

### 7.3 StringListConverter
`List<String>`을 JSON 문자열로 저장하기 위한 JPA 컨버터.
- null/빈 값은 `"[]"`로 저장
- 파싱 실패 시 빈 리스트 반환

파일: `src/main/java/io/heygw44/strive/domain/user/entity/StringListConverter.java`

### 7.4 Repository
JPA 기반 저장/조회 인터페이스.
- `UserRepository`: 이메일/닉네임 중복 검사
  - 파일: `src/main/java/io/heygw44/strive/domain/user/repository/UserRepository.java`
- `EmailVerificationTokenRepository`: 토큰 조회/삭제
  - 파일: `src/main/java/io/heygw44/strive/domain/user/repository/EmailVerificationTokenRepository.java`

---

## 8) 인증/프로필 서비스 계층

### 8.1 AuthService
인증 관련 비즈니스 규칙의 중심.
- 회원가입: 중복/비밀번호 길이 검증 → 비밀번호 해시 → 저장
- 로그인: 이메일로 사용자 조회 → 비밀번호 매칭
- 이메일 인증 요청: 기존 토큰 삭제 후 새 토큰 저장
- 이메일 인증 확인: 토큰 유효성/소유자 확인 후 인증 처리

파일: `src/main/java/io/heygw44/strive/domain/user/service/AuthService.java`

### 8.2 ProfileService
프로필 조회/수정 로직 담당.
- 조회: 사용자 존재 여부 확인 후 `ProfileResponse`로 변환
- 수정: 닉네임 중복 체크 후 `User.updateProfile` 호출

파일: `src/main/java/io/heygw44/strive/domain/user/service/ProfileService.java`

---

## 9) 컨트롤러

### 9.1 AuthController
인증/세션 관련 HTTP 엔드포인트 제공.
- `POST /api/auth/signup` : 회원가입
- `POST /api/auth/login` : 로그인 및 세션 발급
  - AuthenticationManager로 인증 후
  - `SessionAuthenticationStrategy` 적용
  - `SecurityContextRepository`에 저장
- `POST /api/auth/logout` : 세션 무효화
- `POST /api/auth/verify-email/request` : 이메일 인증 토큰 생성
- `POST /api/auth/verify-email/confirm` : 토큰 확인 후 인증 완료

파일: `src/main/java/io/heygw44/strive/domain/user/controller/AuthController.java`

### 9.2 ProfileController
- `GET /api/me` : 내 프로필 조회
- `PUT /api/me` : 내 프로필 수정 (부분 업데이트)

파일: `src/main/java/io/heygw44/strive/domain/user/controller/ProfileController.java`

---

## 10) DTO (요청/응답 모델)

### 요청 DTO
- `SignupRequest` : 이메일/비밀번호/닉네임 검증 포함
- `LoginRequest` : 이메일/비밀번호
- `ProfileUpdateRequest` : 닉네임/소개/지역/경험값 등 (부분 업데이트)
- `VerifyEmailConfirmRequest` : `"tokenId:rawToken"` 문자열

### 응답 DTO
- `SignupResponse`, `LoginResponse`, `ProfileResponse`

파일: `src/main/java/io/heygw44/strive/domain/user/dto/*`

---

## 11) 모임 도메인 (M2)

### 11.1 엔티티
- `Meetup`은 모임의 핵심 정보와 상태 전이 규칙을 가진다.
- `MeetupStatus`는 상태 전이 가능한 흐름만 허용한다.
- `Category`, `Region`은 모임 분류/지역 정보를 제공한다.
- 소프트 삭제(`deletedAt`) 후에는 조회에서 제외된다.

파일:
- `src/main/java/io/heygw44/strive/domain/meetup/entity/Meetup.java`
- `src/main/java/io/heygw44/strive/domain/meetup/entity/MeetupStatus.java`
- `src/main/java/io/heygw44/strive/domain/meetup/entity/Category.java`
- `src/main/java/io/heygw44/strive/domain/meetup/entity/Region.java`

### 11.2 Repository
- `MeetupRepository`: 삭제 제외 조회, 필터/페이징 목록, 비관적 락 조회 제공
- `CategoryRepository`, `RegionRepository`: 카테고리/지역 조회 및 존재 검증

파일:
- `src/main/java/io/heygw44/strive/domain/meetup/repository/MeetupRepository.java`
- `src/main/java/io/heygw44/strive/domain/meetup/repository/CategoryRepository.java`
- `src/main/java/io/heygw44/strive/domain/meetup/repository/RegionRepository.java`

### 11.3 DTO (요청/응답)
- `CreateMeetupRequest`: 생성 요청 + Bean Validation
- `UpdateMeetupRequest`: 부분 업데이트 + 상태 전이 요청
- `MeetupResponse`, `MeetupListResponse`: 상세/목록 응답
- `MeetupSearchCondition`: 기본 OPEN + startAt 정렬

파일:
- `src/main/java/io/heygw44/strive/domain/meetup/dto/CreateMeetupRequest.java`
- `src/main/java/io/heygw44/strive/domain/meetup/dto/UpdateMeetupRequest.java`
- `src/main/java/io/heygw44/strive/domain/meetup/dto/MeetupResponse.java`
- `src/main/java/io/heygw44/strive/domain/meetup/dto/MeetupListResponse.java`
- `src/main/java/io/heygw44/strive/domain/meetup/dto/MeetupSearchCondition.java`

### 11.4 서비스 / 응답 조립기
- `MeetupService`: CRUD + 도메인 규칙 검증
  - 필드 수정은 **OPEN 상태에서만** 허용
  - 상태 전이는 전이 규칙으로 검증
  - 변경 사항 없는 수정 요청은 REQ-400
- `MeetupResponseAssembler`: 응답 DTO 조립 전담, 목록 조회 시 배치 조회로 N+1 방지

파일:
- `src/main/java/io/heygw44/strive/domain/meetup/service/MeetupService.java`
- `src/main/java/io/heygw44/strive/domain/meetup/service/MeetupResponseAssembler.java`

### 11.5 MeetupController
- `POST /api/meetups`: 모임 생성 (인증 필요)
- `GET /api/meetups`: 목록 조회 (필터/정렬/페이징)
- `GET /api/meetups/{id}`: 상세 조회
- `PUT /api/meetups/{id}`: 수정 (작성자만)
- `DELETE /api/meetups/{id}`: 소프트 삭제 (작성자만)

파일:
- `src/main/java/io/heygw44/strive/domain/meetup/controller/MeetupController.java`

---

## 12) API 시퀀스 다이어그램

### 로그인
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant AuthController
    participant AuthService
    participant AuthenticationManager
    participant SessionStrategy
    participant SecurityContextRepo
    Client->>AuthController: POST /api/auth/login
    AuthController->>AuthService: authenticate(email, password)
    AuthService-->>AuthController: LoginResponse
    AuthController->>AuthenticationManager: authenticate(token)
    AuthenticationManager-->>AuthController: Authentication
    AuthController->>SessionStrategy: onAuthentication(...)
    AuthController->>SecurityContextRepo: saveContext(...)
    AuthController-->>Client: 200 OK + ApiResponse
```

### 이메일 인증 요청
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant AuthController
    participant AuthService
    participant TokenRepo
    Client->>AuthController: POST /api/auth/verify-email/request
    AuthController->>AuthService: requestEmailVerification(userId)
    AuthService->>TokenRepo: deleteByUserId(userId)
    AuthService->>TokenRepo: save(new token)
    AuthService-->>AuthController: tokenId
    AuthController-->>Client: 200 OK + tokenId
```

### 프로필 수정
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant ProfileController
    participant ProfileService
    participant UserRepo
    participant User
    Client->>ProfileController: PUT /api/me
    ProfileController->>ProfileService: updateMyProfile(userId, request)
    ProfileService->>UserRepo: findById(userId)
    UserRepo-->>ProfileService: User
    ProfileService->>UserRepo: existsByNicknameAndIdNot(...)
    ProfileService->>User: updateProfile(...)
    ProfileService-->>ProfileController: ProfileResponse
    ProfileController-->>Client: 200 OK + ApiResponse
```

### 모임 생성
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant MeetupController
    participant MeetupService
    participant CategoryRepo
    participant RegionRepo
    participant MeetupRepo
    participant MeetupAssembler
    participant UserRepo
    Client->>MeetupController: POST /api/meetups
    MeetupController->>MeetupService: createMeetup(request, organizerId)
    MeetupService->>CategoryRepo: existsById(categoryId)
    MeetupService->>RegionRepo: existsByCode(regionCode)
    MeetupService->>MeetupRepo: save(meetup)
    MeetupService->>MeetupAssembler: toMeetupResponse(meetup)
    MeetupAssembler->>UserRepo: findById(organizerId)
    MeetupAssembler->>CategoryRepo: findById(categoryId)
    MeetupAssembler->>RegionRepo: findById(regionCode)
    MeetupAssembler-->>MeetupService: MeetupResponse
    MeetupService-->>MeetupController: MeetupResponse
    MeetupController-->>Client: 201 Created + ApiResponse
```

### 모임 목록 조회
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant MeetupController
    participant MeetupService
    participant MeetupRepo
    participant MeetupAssembler
    participant CategoryRepo
    participant RegionRepo
    Client->>MeetupController: GET /api/meetups?filters&page&size
    MeetupController->>MeetupService: getMeetupsResponse(condition, pageable)
    MeetupService->>MeetupRepo: findByFilters(...)
    MeetupService->>MeetupAssembler: toMeetupListResponses(meetups)
    MeetupAssembler->>CategoryRepo: findAllById(categoryIds)
    MeetupAssembler->>RegionRepo: findAllById(regionCodes)
    MeetupAssembler-->>MeetupService: List<MeetupListResponse>
    MeetupService-->>MeetupController: PageResponse
    MeetupController-->>Client: 200 OK + ApiResponse
```

### 모임 수정
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant MeetupController
    participant MeetupService
    participant MeetupRepo
    participant MeetupAssembler
    participant UserRepo
    participant CategoryRepo
    participant RegionRepo
    Client->>MeetupController: PUT /api/meetups/{id}
    MeetupController->>MeetupService: updateMeetupAndGetResponse(id, request, userId)
    MeetupService->>MeetupRepo: findByIdAndDeletedAtIsNull(id)
    MeetupService->>MeetupService: validateOrganizer / validateUpdateRequest
    MeetupService->>MeetupService: validateStatusTransition (if needed)
    MeetupService->>MeetupAssembler: toMeetupResponse(meetup)
    MeetupAssembler->>UserRepo: findById(organizerId)
    MeetupAssembler->>CategoryRepo: findById(categoryId)
    MeetupAssembler->>RegionRepo: findById(regionCode)
    MeetupAssembler-->>MeetupService: MeetupResponse
    MeetupService-->>MeetupController: MeetupResponse
    MeetupController-->>Client: 200 OK + ApiResponse
```

### 모임 삭제
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant MeetupController
    participant MeetupService
    participant MeetupRepo
    Client->>MeetupController: DELETE /api/meetups/{id}
    MeetupController->>MeetupService: deleteMeetup(id, userId)
    MeetupService->>MeetupRepo: findByIdAndDeletedAtIsNull(id)
    MeetupService->>MeetupService: validateOrganizer
    MeetupService->>MeetupService: softDelete()
    MeetupService-->>MeetupController: void
    MeetupController-->>Client: 204 No Content
```

---

## 13) API 요청/응답 예시

### 모임 API 스펙 요약 (M2)

| API | 인증 | 요청 바디 | 응답 | 주요 에러 |
| --- | --- | --- | --- | --- |
| POST `/api/meetups` | 필요 | CreateMeetupRequest | MeetupResponse | REQ-400, RES-404, AUTH-401 |
| GET `/api/meetups` | 불필요 | 없음 | PageResponse\<MeetupListResponse> | REQ-400 |
| GET `/api/meetups/{id}` | 불필요 | 없음 | MeetupResponse | RES-404 |
| PUT `/api/meetups/{id}` | 필요 | UpdateMeetupRequest | MeetupResponse | REQ-400, AUTH-401, AUTH-403, RES-404, MEETUP-409-STATE |
| DELETE `/api/meetups/{id}` | 필요 | 없음 | 204 No Content | AUTH-401, AUTH-403, RES-404 |

### 모임 에러 케이스 (대표)
- REQ-400: 필수/형식 검증 실패, 변경 사항 없는 수정 요청
- RES-404: 존재하지 않거나 삭제된 모임/카테고리/지역
- AUTH-401: 인증되지 않은 요청
- AUTH-403: 작성자 권한 위반
- MEETUP-409-STATE: 허용되지 않는 상태 전이 또는 비-OPEN에서 필드 수정

### 회원가입
요청:
```http
POST /api/auth/signup
Content-Type: application/json

{
  "email": "new@example.com",
  "password": "password123",
  "nickname": "newuser"
}
```

응답:
```json
{
  "data": {
    "id": 1,
    "email": "new@example.com",
    "nickname": "newuser"
  },
  "traceId": "..."
}
```

### 로그인
요청:
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "password123"
}
```

응답:
```json
{
  "data": {
    "id": 1,
    "email": "test@example.com",
    "nickname": "testuser",
    "isVerified": false
  },
  "traceId": "..."
}
```

### 이메일 인증 토큰 요청
요청:
```http
POST /api/auth/verify-email/request
Content-Type: application/json
Cookie: JSESSIONID=...
```

응답:
```json
{
  "data": "token-id",
  "traceId": "..."
}
```

### 이메일 인증 확인
요청:
```http
POST /api/auth/verify-email/confirm
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{
  "token": "tokenId:rawToken"
}
```

응답:
```json
{
  "data": null,
  "traceId": "..."
}
```

### 내 프로필 조회
요청:
```http
GET /api/me
Cookie: JSESSIONID=...
```

응답:
```json
{
  "data": {
    "email": "test@example.com",
    "nickname": "testuser",
    "bioText": null,
    "preferredCategories": [],
    "homeRegionCode": null,
    "experienceLevel": null,
    "isVerified": false
  },
  "traceId": "..."
}
```

### 내 프로필 수정
요청:
```http
PUT /api/me
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{
  "nickname": "updatedNick",
  "bioText": "Updated bio text",
  "preferredCategories": ["HIKING", "CAMPING"],
  "homeRegionCode": "SEOUL_GANGNAM",
  "experienceLevel": "INTERMEDIATE"
}
```

응답:
```json
{
  "data": {
    "email": "test@example.com",
    "nickname": "updatedNick",
    "bioText": "Updated bio text",
    "preferredCategories": ["HIKING", "CAMPING"],
    "homeRegionCode": "SEOUL_GANGNAM",
    "experienceLevel": "INTERMEDIATE",
    "isVerified": false
  },
  "traceId": "..."
}
```

### 모임 생성
요청:
```http
POST /api/meetups
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{
  "title": "주말 러닝 모임",
  "description": "함께 러닝해요",
  "categoryId": 1,
  "regionCode": "SEOUL_GANGNAM",
  "locationText": "강남역 2번 출구",
  "startAt": "2026-02-07T09:00:00",
  "endAt": "2026-02-07T11:00:00",
  "recruitEndAt": "2026-02-06T23:59:00",
  "capacity": 10,
  "experienceLevelText": "초보자 환영"
}
```

응답:
```json
{
  "data": {
    "id": 1,
    "organizerId": 1,
    "organizerNickname": "organizer",
    "title": "주말 러닝 모임",
    "description": "함께 러닝해요",
    "categoryId": 1,
    "categoryName": "러닝",
    "regionCode": "SEOUL_GANGNAM",
    "regionName": "강남구",
    "locationText": "강남역 2번 출구",
    "startAt": "2026-02-07T09:00:00",
    "endAt": "2026-02-07T11:00:00",
    "recruitEndAt": "2026-02-06T23:59:00",
    "capacity": 10,
    "status": "DRAFT",
    "experienceLevelText": "초보자 환영",
    "createdAt": "2026-01-31T10:00:00",
    "updatedAt": "2026-01-31T10:00:00"
  },
  "traceId": "..."
}
```

### 모임 목록 조회 (필터/페이징)
요청:
```http
GET /api/meetups?regionCode=SEOUL_GANGNAM&sort=startAt&page=0&size=2
```

응답:
```json
{
  "data": {
    "items": [
      {
        "id": 1,
        "title": "주말 러닝 모임",
        "categoryId": 1,
        "categoryName": "러닝",
        "regionCode": "SEOUL_GANGNAM",
        "regionName": "강남구",
        "locationText": "강남역 2번 출구",
        "startAt": "2026-02-07T09:00:00",
        "recruitEndAt": "2026-02-06T23:59:00",
        "capacity": 10,
        "status": "OPEN",
        "createdAt": "2026-01-31T10:00:00"
      }
    ],
    "total": 1,
    "page": 0,
    "size": 2,
    "hasNext": false
  },
  "traceId": "..."
}
```

### 모임 수정
요청:
```http
PUT /api/meetups/1
Content-Type: application/json
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...

{
  "title": "주말 러닝 모임 (수정)",
  "description": "함께 러닝해요 - 업데이트"
}
```

응답:
```json
{
  "data": {
    "id": 1,
    "organizerId": 1,
    "organizerNickname": "organizer",
    "title": "주말 러닝 모임 (수정)",
    "description": "함께 러닝해요 - 업데이트",
    "categoryId": 1,
    "categoryName": "러닝",
    "regionCode": "SEOUL_GANGNAM",
    "regionName": "강남구",
    "locationText": "강남역 2번 출구",
    "startAt": "2026-02-07T09:00:00",
    "endAt": "2026-02-07T11:00:00",
    "recruitEndAt": "2026-02-06T23:59:00",
    "capacity": 10,
    "status": "OPEN",
    "experienceLevelText": "초보자 환영",
    "createdAt": "2026-01-31T10:00:00",
    "updatedAt": "2026-01-31T11:00:00"
  },
  "traceId": "..."
}
```

### 모임 삭제
요청:
```http
DELETE /api/meetups/1
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...
```

응답:
```http
204 No Content
```

---

## 14) 보안용 사용자 정보

### `CustomUserDetails`
Spring Security가 사용하는 사용자 모델.  
`User`를 감싸며 `ROLE_USER` 권한 부여.

### `CustomUserDetailsService`
이메일로 사용자를 조회해 `CustomUserDetails`를 반환한다.

파일:
- `src/main/java/io/heygw44/strive/global/security/CustomUserDetails.java`
- `src/main/java/io/heygw44/strive/global/security/CustomUserDetailsService.java`

---

## 15) 테스트 구성

### 통합 테스트
Spring Boot + MockMvc 기반.
- `AuthIntegrationTest`: 로그인/세션 재발급/CSRF/401/토큰 형식 검증
- `ProfileIntegrationTest`: 프로필 조회/수정/중복 닉네임 검증
- `MeetupIntegrationTest`: 모임 CRUD/필터/권한/상태 전이 검증
- `GlobalExceptionHandlerTest`: 검증/비즈니스 예외 응답 확인
- `TraceIdFilterTest`: TraceId 생성/반환 확인

파일:
- `src/test/java/io/heygw44/strive/domain/user/controller/AuthIntegrationTest.java`
- `src/test/java/io/heygw44/strive/domain/user/controller/ProfileIntegrationTest.java`
- `src/test/java/io/heygw44/strive/domain/meetup/controller/MeetupIntegrationTest.java`
- `src/test/java/io/heygw44/strive/global/exception/GlobalExceptionHandlerTest.java`
- `src/test/java/io/heygw44/strive/global/filter/TraceIdFilterTest.java`

### 단위 테스트
Mockito 기반 서비스 테스트.
- `AuthServiceTest`: 회원가입/로그인/토큰 요청 검증
- `MeetupServiceTest`: 모임 생성/수정/삭제 도메인 규칙 검증
- `MeetupStatusTest`: 상태 전이 규칙 검증

파일:
- `src/test/java/io/heygw44/strive/domain/user/service/AuthServiceTest.java`
- `src/test/java/io/heygw44/strive/domain/meetup/service/MeetupServiceTest.java`
- `src/test/java/io/heygw44/strive/domain/meetup/entity/MeetupStatusTest.java`

---

## 16) 환경 설정

### `application.yaml`
기본 공통 설정.
- Jackson 시간대 UTC
- JPA Open Session 비활성화
- 세션 쿠키: `http-only=true`, `secure=true`, `same-site=lax`

파일: `src/main/resources/application.yaml`

### `application-local.yaml`
로컬 전용 설정.
- H2 인메모리 DB
- JPA create-drop
- 세션 쿠키 `secure=false` (로컬 HTTP 테스트용)

파일: `src/main/resources/application-local.yaml`

---

## 17) 개발 관점 요약 (왜 이렇게 작성했나)

1) **컨트롤러 얇게, 서비스에 규칙 집중**
   - 테스트와 유지보수에 유리.
2) **예외/응답 규격 통일**
   - 클라이언트가 에러 처리 일관되게 가능.
3) **보안은 표준 흐름**
   - 직접 구현보다 Spring Security 전략 사용이 안전.
4) **부분 업데이트는 null 보호**
   - 기존 데이터 손실 방지.
5) **응답 조립 책임 분리**
   - 조회 전용 로직을 분리해 서비스 복잡도를 낮춤.

---

## 18) 다음에 확장될 가능성이 높은 포인트

- Participation 도메인 추가 시:
  - 정원/승인 동시성 처리 (락/버전)
  - 권한/상태 전이 ErrorCode 확장
- 이메일 인증: 실 운영에서는 토큰을 URL로 전달하고, 인증 엔드포인트 공개 필요 가능성

---

필요하면 이 문서를 기반으로 **더 세부 설계 문서(시퀀스 다이어그램, 상태 전이, API 스펙)**까지 확장해줄 수 있다.
