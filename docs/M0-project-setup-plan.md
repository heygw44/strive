# M0 — 프로젝트 셋업 구현 계획서

> 기준 문서: `/.claude/prd.md`, `/.claude/agents/planner.md`

## 개요
MVP 전체 구현의 기반이 되는 **공통 구조/응답/예외/로깅/테스트 기반**을 먼저 확립한다. PRD의 품질 게이트(계층 분리, 표준 응답 포맷, traceId, 테스트 기반)를 M0에서 완료해 이후 마일스톤 구현을 가속한다.

## 관련 PRD 항목
- 마일스톤: **M0 — 프로젝트 셋업**
- 품질 게이트: 계층 분리, 표준 응답 포맷, traceId 로깅, 테스트 기반
- 비기능: 관측 가능성(요청 식별자), 표준 오류 코드

## 결정 사항 (M0 범위)
- **Java 17+ + Spring Boot 3.x + Gradle**
- **JPA + MyBatis 하이브리드**(CRUD=JPA, 성능 조회=MyBatis)
- **DB 마이그레이션 도구**: Flyway (권장사항을 M0에서 확정)
- **시간 정책**: 서버 내부 UTC 저장/연산, API ISO-8601
- **응답 포맷**: ApiResponse / ErrorResponse / PageResponse

## 아키텍처/패키지 구조 (초기 골격)
```
src/main/java/io/heygw44/strive/
├── global/
│   ├── config/          # 공통 설정 (Security, JPA, MyBatis, Jackson)
│   ├── exception/       # BusinessException, ErrorCode, GlobalExceptionHandler
│   ├── response/        # ApiResponse, ErrorResponse, PageResponse
│   └── filter/          # TraceIdFilter (MDC 연동)
├── domain/
│   ├── user/            # 이후 M1~
│   ├── meetup/          # 이후 M2~
│   └── participation/   # 이후 M3~
└── StriveApplication.java
```

## 구현 단계

### Phase 1: 프로젝트 기본 설정
1. **Gradle 의존성 정리**
   - Spring Boot Web, Data JPA, Validation, Security
   - MyBatis starter
   - MySQL/H2
   - Lombok
   - Test (spring-boot-starter-test, security-test)
2. **환경 설정 파일 분리**
   - `application.yaml` 기본
   - `application-local.yaml` (H2)
   - `application-prod.yaml` (MySQL)
3. **시간/타임존 설정**
   - Jackson/hibernate UTC 설정
   - ISO-8601 응답 포맷 기본값 설정

### Phase 2: 공통 응답/예외 체계
1. **표준 응답 DTO**
   - `ApiResponse<T>`
   - `ErrorResponse`
   - `PageResponse<T>`
2. **도메인 예외 + 오류 코드**
   - `ErrorCode` (공통 + 도메인 placeholder)
   - `BusinessException`
3. **GlobalExceptionHandler**
   - Bean Validation 오류 매핑
   - BusinessException → ErrorResponse
   - 시스템 예외 → 500

### Phase 3: 관측/로깅(TraceId)
1. **TraceIdFilter 구현**
   - 요청 시작 시 traceId 생성
   - MDC에 저장
   - 응답 헤더에 `X-Trace-Id` 추가
2. **Logback 패턴 설정**
   - traceId 포함 출력
   - 민감정보 로깅 금지 규칙 적용

### Phase 4: 테스트 기반 구축
1. **테스트 기본 베이스**
   - 통합 테스트용 Base 클래스
   - `@SpringBootTest`, `@Transactional` 기본
2. **TestConfig**
   - Clock 고정(UTC)
   - H2 프로파일 설정
3. **샘플 스모크 테스트**
   - 컨텍스트 로딩 테스트
   - 예외 핸들러 동작 테스트 (단순 케이스)

### Phase 5: 품질 게이트 체크리스트 도입
- M0 완료 시 다음을 문서화/체크
  - 계층 분리 규칙
  - 표준 응답/오류 코드 사용
  - traceId 로깅
  - 테스트 베이스 구축

## 테스트 전략
- **스모크 테스트**: Application context load
- **예외 처리 테스트**: validation error, BusinessException 매핑
- **로그/traceId 테스트**: 응답 헤더 포함 여부

## 리스크 및 대응
- **리스크**: 공통 구조가 M1 이후 변경될 가능성
  - **대응**: 확장 가능 구조로 설계 (global 패키지 중심)
- **리스크**: 보안 설정(M1)과 충돌 가능
  - **대응**: M0에서는 최소 설정만 적용, M1에서 강화

## 성공 기준 (Done Definition)
- [x] `./gradlew build` 성공
- [x] 표준 응답/오류 코드 구조 확정
- [x] GlobalExceptionHandler 정상 동작
- [x] TraceId 로그 및 응답 헤더 포함
- [x] 스모크 테스트 통과
- [x] 패키지 구조 및 기본 설정 확정

## 파일 단위 체크리스트 (M0)
- [x] `build.gradle`: Spring Boot/Web/JPA/Validation/Security/MyBatis/MySQL/H2/Test 의존성 확인
- [x] `src/main/resources/application.yaml`: 공통 설정(UTC, Jackson, 로깅, 프로파일 기본값)
- [x] `src/main/resources/application-local.yaml`: H2 로컬 설정
- [x] `src/main/resources/application-prod.yaml`: MySQL 운영 유사 설정
- [x] `src/main/java/io/heygw44/strive/StriveApplication.java`: 애플리케이션 엔트리
- [x] `src/main/java/io/heygw44/strive/global/response/ApiResponse.java`: 표준 성공 응답
- [x] `src/main/java/io/heygw44/strive/global/response/ErrorResponse.java`: 표준 에러 응답
- [x] `src/main/java/io/heygw44/strive/global/response/PageResponse.java`: 페이징 응답
- [x] `src/main/java/io/heygw44/strive/global/exception/ErrorCode.java`: 오류 코드 열거형
- [x] `src/main/java/io/heygw44/strive/global/exception/BusinessException.java`: 도메인 예외
- [x] `src/main/java/io/heygw44/strive/global/exception/GlobalExceptionHandler.java`: 예외 매핑
- [x] `src/main/java/io/heygw44/strive/global/filter/TraceIdFilter.java`: traceId 생성/주입
- [x] `src/main/resources/logback-spring.xml`: MDC(traceId) 포함 로그 포맷
- [x] `src/test/java/io/heygw44/strive/StriveApplicationTests.java`: 컨텍스트 로딩 스모크 테스트
- [x] `src/test/java/io/heygw44/strive/global/exception/GlobalExceptionHandlerTest.java`: 예외 매핑 테스트
- [x] `src/test/java/io/heygw44/strive/global/filter/TraceIdFilterTest.java`: traceId 헤더/로그 테스트

## 다음 단계
- M1 인증/인가 구현 착수 (세션, CSRF, 이메일 인증)
- M2 도메인 CRUD 설계로 확장
