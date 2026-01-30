package io.heygw44.strive.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.heygw44.strive.domain.user.dto.LoginRequest;
import io.heygw44.strive.domain.user.dto.ProfileUpdateRequest;
import io.heygw44.strive.domain.user.entity.User;
import io.heygw44.strive.domain.user.repository.UserRepository;
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

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class ProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.create("test@example.com", passwordEncoder.encode("password123"), "testuser");
        userRepository.save(testUser);

        // 세션 획득을 위해 로그인
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        session = (MockHttpSession) loginResult.getRequest().getSession();
    }

    @Nested
    @DisplayName("AC-PROFILE-01: 로그인 상태에서 GET /api/me 프로필 반환")
    class GetProfileTest {

        @Test
        @DisplayName("로그인 상태에서 내 프로필 조회 성공")
        void getProfile_whenLoggedIn_returnsProfile() throws Exception {
            mockMvc.perform(get("/api/me")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("test@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("testuser"))
                    .andExpect(jsonPath("$.data.isVerified").value(false));
        }

        @Test
        @DisplayName("비로그인 상태에서 프로필 조회 시 401 반환")
        void getProfile_whenNotLoggedIn_returns401() throws Exception {
            mockMvc.perform(get("/api/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("AC-PROFILE-02: PUT /api/me 수정 후 조회에 반영")
    class UpdateProfileTest {

        @Test
        @DisplayName("프로필 수정 후 조회 시 변경사항 반영")
        void updateProfile_thenGet_reflectsChanges() throws Exception {
            ProfileUpdateRequest updateRequest = new ProfileUpdateRequest(
                    "updatedNick",
                    "Updated bio text",
                    List.of("HIKING", "CAMPING"),
                    "SEOUL_GANGNAM",
                    "INTERMEDIATE"
            );

            // 프로필 업데이트
            mockMvc.perform(put("/api/me")
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("updatedNick"))
                    .andExpect(jsonPath("$.data.bioText").value("Updated bio text"))
                    .andExpect(jsonPath("$.data.homeRegionCode").value("SEOUL_GANGNAM"))
                    .andExpect(jsonPath("$.data.experienceLevel").value("INTERMEDIATE"));

            // 변경 사항이 저장되었는지 확인
            mockMvc.perform(get("/api/me")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("updatedNick"))
                    .andExpect(jsonPath("$.data.bioText").value("Updated bio text"));
        }

        @Test
        @DisplayName("중복 닉네임으로 수정 시 409 반환")
        void updateProfile_withDuplicateNickname_returns409() throws Exception {
            // 다른 사용자 생성
            User anotherUser = User.create("another@example.com", passwordEncoder.encode("password123"), "existingNick");
            userRepository.save(anotherUser);

            ProfileUpdateRequest updateRequest = new ProfileUpdateRequest(
                    "existingNick",
                    null,
                    null,
                    null,
                    null
            );

            mockMvc.perform(put("/api/me")
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUTH-409-NICKNAME"));
        }

        @Test
        @DisplayName("CSRF 토큰 없이 프로필 수정 시 403 반환")
        void updateProfile_withoutCsrf_returns403() throws Exception {
            ProfileUpdateRequest updateRequest = new ProfileUpdateRequest(
                    "newNick",
                    null,
                    null,
                    null,
                    null
            );

            mockMvc.perform(put("/api/me")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());
        }
    }
}
