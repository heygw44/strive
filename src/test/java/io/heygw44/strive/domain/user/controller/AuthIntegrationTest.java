package io.heygw44.strive.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.heygw44.strive.domain.user.dto.LoginRequest;
import io.heygw44.strive.domain.user.dto.SignupRequest;
import io.heygw44.strive.domain.user.entity.User;
import io.heygw44.strive.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", passwordEncoder.encode("password123"), "testuser");
        userRepository.save(testUser);
    }

    @Nested
    @DisplayName("회원가입 테스트")
    class SignupTest {

        @Test
        @DisplayName("유효한 요청으로 회원가입 성공")
        void signup_withValidRequest_returns201() throws Exception {
            SignupRequest request = new SignupRequest("new@example.com", "password123", "newuser");

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.email").value("new@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("newuser"));
        }

        @Test
        @DisplayName("이메일 중복시 409 반환")
        void signup_withDuplicateEmail_returns409() throws Exception {
            SignupRequest request = new SignupRequest("test@example.com", "password123", "newuser");

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUTH-409-EMAIL"));
        }
    }

    @Nested
    @DisplayName("AC-AUTH-01: 로그인 성공 시 세션 재발급")
    class LoginSessionTest {

        @Test
        @DisplayName("로그인 성공 시 새 세션 발급 후 보호 API 접근 가능")
        void login_success_canAccessProtectedApi() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "password123");

            // 로그인
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("test@example.com"))
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
            assertThat(session).isNotNull();

            // 보호된 API 접근
            mockMvc.perform(get("/api/me")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("test@example.com"));
        }
    }

    @Nested
    @DisplayName("AC-AUTH-02: 세션 없이 보호 API 호출 시 401")
    class UnauthorizedAccessTest {

        @Test
        @DisplayName("세션 없이 /api/me 호출 시 401 반환")
        void accessProtectedApi_withoutSession_returns401() throws Exception {
            mockMvc.perform(get("/api/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("세션 없이 로그아웃 호출 시 401 반환")
        void logout_withoutSession_returns401() throws Exception {
            mockMvc.perform(post("/api/auth/logout")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("AC-SEC-02: 로그인 시 세션 재발급, 이전 세션 무효화")
    class SessionFixationTest {

        @Test
        @DisplayName("로그인 성공 시 세션 ID가 변경됨")
        void login_regeneratesSession() throws Exception {
            // 첫 로그인
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            MvcResult firstLogin = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            String firstSessionId = firstLogin.getRequest().getSession().getId();

            // 로그아웃
            mockMvc.perform(post("/api/auth/logout")
                            .session((MockHttpSession) firstLogin.getRequest().getSession())
                            .with(csrf()))
                    .andExpect(status().isOk());

            // 두 번째 로그인
            MvcResult secondLogin = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            String secondSessionId = secondLogin.getRequest().getSession().getId();

            // 세션 ID가 달라야 함
            assertThat(secondSessionId).isNotEqualTo(firstSessionId);
        }
    }

    @Nested
    @DisplayName("AC-SEC-03: CSRF 토큰 없이 상태 변경 요청 거부")
    class CsrfProtectionTest {

        @Test
        @DisplayName("CSRF 토큰 없이 로그아웃 요청 시 403 반환")
        void logout_withoutCsrf_returns403() throws Exception {
            // 세션을 얻기 위해 먼저 로그인
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

            // CSRF 토큰 없이 로그아웃 시도
            mockMvc.perform(post("/api/auth/logout")
                            .session(session))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CSRF 토큰과 함께 로그아웃 요청 시 성공")
        void logout_withCsrf_succeeds() throws Exception {
            // 세션을 얻기 위해 먼저 로그인
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

            // CSRF 토큰으로 로그아웃
            mockMvc.perform(post("/api/auth/logout")
                            .session(session)
                            .with(csrf()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("잘못된 자격증명 테스트")
    class InvalidCredentialsTest {

        @Test
        @DisplayName("존재하지 않는 이메일로 로그인 시 401 반환")
        void login_withNonExistentEmail_returns401() throws Exception {
            LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH-401-CREDENTIALS"));
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인 시 401 반환")
        void login_withWrongPassword_returns401() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH-401-CREDENTIALS"));
        }
    }
}
