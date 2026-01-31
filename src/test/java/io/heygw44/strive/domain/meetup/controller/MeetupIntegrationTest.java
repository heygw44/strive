package io.heygw44.strive.domain.meetup.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.heygw44.strive.domain.meetup.dto.CreateMeetupRequest;
import io.heygw44.strive.domain.meetup.dto.UpdateMeetupRequest;
import io.heygw44.strive.domain.meetup.entity.Category;
import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;
import io.heygw44.strive.domain.meetup.entity.Region;
import io.heygw44.strive.domain.meetup.repository.CategoryRepository;
import io.heygw44.strive.domain.meetup.repository.MeetupRepository;
import io.heygw44.strive.domain.meetup.repository.RegionRepository;
import io.heygw44.strive.domain.user.dto.LoginRequest;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@DisplayName("Meetup API 통합 테스트")
class MeetupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User organizer;
    private User otherUser;
    private Category category;
    private Region region;
    private MockHttpSession organizerSession;
    private MockHttpSession otherUserSession;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트 사용자 생성
        organizer = User.create("organizer@example.com", passwordEncoder.encode("password123"), "organizer");
        userRepository.save(organizer);

        otherUser = User.create("other@example.com", passwordEncoder.encode("password123"), "otheruser");
        userRepository.save(otherUser);

        // 카테고리/지역 생성
        category = Category.create("러닝");
        categoryRepository.save(category);

        region = Region.createDistrict("SEOUL_GANGNAM", "강남구", null);
        regionRepository.save(region);

        // 세션 획득
        organizerSession = loginAndGetSession("organizer@example.com", "password123");
        otherUserSession = loginAndGetSession("other@example.com", "password123");
    }

    private MockHttpSession loginAndGetSession(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    private CreateMeetupRequest createValidRequest() {
        LocalDateTime now = LocalDateTime.now();
        return new CreateMeetupRequest(
            "테스트 러닝 모임",
            "함께 러닝해요",
            category.getId(),
            region.getCode(),
            "강남역 2번 출구",
            now.plusDays(7),
            now.plusDays(7).plusHours(2),
            now.plusDays(6),
            10,
            "초보자 환영"
        );
    }

    @Nested
    @DisplayName("모임 생성 API")
    class CreateMeetupTest {

        @Test
        @DisplayName("유효한 요청으로 모임 생성 성공")
        void createMeetup_withValidRequest_returns201() throws Exception {
            CreateMeetupRequest request = createValidRequest();

            mockMvc.perform(post("/api/meetups")
                            .session(organizerSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title").value("테스트 러닝 모임"))
                    .andExpect(jsonPath("$.data.organizerId").value(organizer.getId()))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.categoryName").value("러닝"))
                    .andExpect(jsonPath("$.data.regionName").value("강남구"));
        }

        @Test
        @DisplayName("세션 없이 모임 생성 시 401")
        void createMeetup_withoutSession_returns401() throws Exception {
            CreateMeetupRequest request = createValidRequest();

            mockMvc.perform(post("/api/meetups")
                            .with(csrf())  // CSRF 토큰 포함
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 카테고리로 생성 시 404")
        void createMeetup_withInvalidCategory_returns404() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            CreateMeetupRequest request = new CreateMeetupRequest(
                "테스트 모임", "설명", 9999L, region.getCode(), "장소",
                now.plusDays(7), now.plusDays(7).plusHours(2), now.plusDays(6),
                10, null
            );

            mockMvc.perform(post("/api/meetups")
                            .session(organizerSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RES-404"));
        }

        @Test
        @DisplayName("필수 필드 누락 시 400")
        void createMeetup_withMissingFields_returns400() throws Exception {
            String invalidJson = """
                {
                    "title": "",
                    "categoryId": null
                }
                """;

            mockMvc.perform(post("/api/meetups")
                            .session(organizerSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("AC-MEETUP-01: 모임 목록 조회 필터링")
    class GetMeetupsFilterTest {

        @Test
        @DisplayName("기본 조회 시 OPEN 모임만 반환")
        void getMeetups_defaultFilter_returnsOpenMeetups() throws Exception {
            // Given: DRAFT 모임과 OPEN 모임 생성
            Meetup draftMeetup = createAndSaveMeetup("DRAFT 모임", MeetupStatus.DRAFT);
            Meetup openMeetup = createAndSaveMeetup("OPEN 모임", MeetupStatus.OPEN);

            // When & Then: 기본 조회 시 OPEN만 반환
            mockMvc.perform(get("/api/meetups"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[?(@.title == 'OPEN 모임')]").exists())
                    .andExpect(jsonPath("$.data.items[?(@.title == 'DRAFT 모임')]").doesNotExist());
        }

        @Test
        @DisplayName("지역 코드로 필터링")
        void getMeetups_filterByRegion_returnsFilteredResults() throws Exception {
            // Given: 다른 지역 추가
            Region otherRegion = Region.createDistrict("SEOUL_SEOCHO", "서초구", null);
            regionRepository.save(otherRegion);

            Meetup gangnamMeetup = createAndSaveMeetupWithRegion("강남 모임", region.getCode());
            Meetup seochoMeetup = createAndSaveMeetupWithRegion("서초 모임", otherRegion.getCode());

            // When & Then: 강남구로 필터링
            mockMvc.perform(get("/api/meetups")
                            .param("regionCode", "SEOUL_GANGNAM"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.title == '강남 모임')]").exists())
                    .andExpect(jsonPath("$.data.items[?(@.title == '서초 모임')]").doesNotExist());
        }

        @Test
        @DisplayName("카테고리로 필터링")
        void getMeetups_filterByCategory_returnsFilteredResults() throws Exception {
            // Given: 다른 카테고리 추가
            Category hiking = Category.create("등산");
            categoryRepository.save(hiking);

            Meetup runningMeetup = createAndSaveMeetupWithCategory("러닝 모임", category.getId());
            Meetup hikingMeetup = createAndSaveMeetupWithCategory("등산 모임", hiking.getId());

            // When & Then: 러닝 카테고리로 필터링
            mockMvc.perform(get("/api/meetups")
                            .param("categoryId", category.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.title == '러닝 모임')]").exists())
                    .andExpect(jsonPath("$.data.items[?(@.title == '등산 모임')]").doesNotExist());
        }

        @Test
        @DisplayName("페이징 동작 확인")
        void getMeetups_withPaging_returnsPaginatedResults() throws Exception {
            // Given: 5개 모임 생성
            for (int i = 0; i < 5; i++) {
                createAndSaveMeetup("모임 " + i, MeetupStatus.OPEN);
            }

            // When & Then: size=2로 조회
            mockMvc.perform(get("/api/meetups")
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.total").value(5))
                    .andExpect(jsonPath("$.data.hasNext").value(true));
        }
    }

    @Nested
    @DisplayName("AC-MEETUP-02: 소프트 삭제된 모임 조회 시 404")
    class DeletedMeetupTest {

        @Test
        @DisplayName("삭제된 모임 상세 조회 시 404")
        void getMeetup_deletedMeetup_returns404() throws Exception {
            // Given: 모임 생성 후 삭제
            Meetup meetup = createAndSaveMeetup("삭제될 모임", MeetupStatus.OPEN);
            meetup.softDelete();
            meetupRepository.save(meetup);

            // When & Then
            mockMvc.perform(get("/api/meetups/" + meetup.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RES-404"));
        }

        @Test
        @DisplayName("삭제된 모임은 목록에서 제외")
        void getMeetups_excludesDeletedMeetups() throws Exception {
            // Given: OPEN 모임과 삭제된 OPEN 모임 생성
            Meetup activeMeetup = createAndSaveMeetup("활성 모임", MeetupStatus.OPEN);
            Meetup deletedMeetup = createAndSaveMeetup("삭제된 모임", MeetupStatus.OPEN);
            deletedMeetup.softDelete();
            meetupRepository.save(deletedMeetup);

            // When & Then
            mockMvc.perform(get("/api/meetups"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.title == '활성 모임')]").exists())
                    .andExpect(jsonPath("$.data.items[?(@.title == '삭제된 모임')]").doesNotExist());
        }
    }

    @Nested
    @DisplayName("AC-AUTH-03: 타인 모임 수정/삭제 시 403")
    class AuthorizationTest {

        @Test
        @DisplayName("작성자가 모임 수정 성공")
        void updateMeetup_byOrganizer_succeeds() throws Exception {
            Meetup meetup = createAndSaveMeetup("원본 제목", MeetupStatus.OPEN);

            UpdateMeetupRequest request = new UpdateMeetupRequest(
                "수정된 제목", null, null, null, null
            );

            mockMvc.perform(put("/api/meetups/" + meetup.getId())
                            .session(organizerSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("수정된 제목"));
        }

        @Test
        @DisplayName("타인이 모임 수정 시 403")
        void updateMeetup_byOtherUser_returns403() throws Exception {
            Meetup meetup = createAndSaveMeetup("모임", MeetupStatus.OPEN);

            UpdateMeetupRequest request = new UpdateMeetupRequest(
                "해킹 시도", null, null, null, null
            );

            mockMvc.perform(put("/api/meetups/" + meetup.getId())
                            .session(otherUserSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH-403"));
        }

        @Test
        @DisplayName("작성자가 모임 삭제 성공")
        void deleteMeetup_byOrganizer_succeeds() throws Exception {
            Meetup meetup = createAndSaveMeetup("삭제할 모임", MeetupStatus.OPEN);

            mockMvc.perform(delete("/api/meetups/" + meetup.getId())
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            // 삭제 후 조회 시 404
            mockMvc.perform(get("/api/meetups/" + meetup.getId()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("타인이 모임 삭제 시 403")
        void deleteMeetup_byOtherUser_returns403() throws Exception {
            Meetup meetup = createAndSaveMeetup("모임", MeetupStatus.OPEN);

            mockMvc.perform(delete("/api/meetups/" + meetup.getId())
                            .session(otherUserSession)
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH-403"));
        }
    }

    @Nested
    @DisplayName("상태 전이 테스트")
    class StatusTransitionTest {

        @Test
        @DisplayName("DRAFT -> OPEN 전이 성공")
        void updateStatus_draftToOpen_succeeds() throws Exception {
            Meetup meetup = createAndSaveMeetup("모임", MeetupStatus.DRAFT);

            UpdateMeetupRequest request = new UpdateMeetupRequest(
                null, null, null, null, MeetupStatus.OPEN
            );

            mockMvc.perform(put("/api/meetups/" + meetup.getId())
                            .session(organizerSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("OPEN"));
        }

        @Test
        @DisplayName("DRAFT -> COMPLETED 전이 실패 (허용되지 않는 전이)")
        void updateStatus_draftToCompleted_returns409() throws Exception {
            Meetup meetup = createAndSaveMeetup("모임", MeetupStatus.DRAFT);

            UpdateMeetupRequest request = new UpdateMeetupRequest(
                null, null, null, null, MeetupStatus.COMPLETED
            );

            mockMvc.perform(put("/api/meetups/" + meetup.getId())
                            .session(organizerSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEETUP-409-STATE"));
        }
    }

    // === Helper Methods ===

    private Meetup createAndSaveMeetup(String title, MeetupStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Meetup meetup = Meetup.create(
            organizer.getId(), title, "설명", category.getId(), region.getCode(),
            "장소", now.plusDays(7), now.plusDays(7).plusHours(2), now.plusDays(6),
            10, null
        );
        if (status != MeetupStatus.DRAFT) {
            meetup.transitionTo(MeetupStatus.OPEN);
            if (status == MeetupStatus.CLOSED) {
                meetup.transitionTo(MeetupStatus.CLOSED);
            }
        }
        return meetupRepository.save(meetup);
    }

    private Meetup createAndSaveMeetupWithRegion(String title, String regionCode) {
        LocalDateTime now = LocalDateTime.now();
        Meetup meetup = Meetup.create(
            organizer.getId(), title, "설명", category.getId(), regionCode,
            "장소", now.plusDays(7), now.plusDays(7).plusHours(2), now.plusDays(6),
            10, null
        );
        meetup.transitionTo(MeetupStatus.OPEN);
        return meetupRepository.save(meetup);
    }

    private Meetup createAndSaveMeetupWithCategory(String title, Long categoryId) {
        LocalDateTime now = LocalDateTime.now();
        Meetup meetup = Meetup.create(
            organizer.getId(), title, "설명", categoryId, region.getCode(),
            "장소", now.plusDays(7), now.plusDays(7).plusHours(2), now.plusDays(6),
            10, null
        );
        meetup.transitionTo(MeetupStatus.OPEN);
        return meetupRepository.save(meetup);
    }
}
