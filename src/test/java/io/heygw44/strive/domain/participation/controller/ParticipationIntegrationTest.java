package io.heygw44.strive.domain.participation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.heygw44.strive.domain.meetup.entity.Category;
import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;
import io.heygw44.strive.domain.meetup.entity.Region;
import io.heygw44.strive.domain.meetup.repository.CategoryRepository;
import io.heygw44.strive.domain.meetup.repository.MeetupRepository;
import io.heygw44.strive.domain.meetup.repository.RegionRepository;
import io.heygw44.strive.domain.participation.entity.Participation;
import io.heygw44.strive.domain.participation.entity.ParticipationStatus;
import io.heygw44.strive.domain.participation.repository.ParticipationRepository;
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
@DisplayName("Participation API 통합 테스트")
class ParticipationIntegrationTest {

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
    private ParticipationRepository participationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User organizer;
    private User participant;
    private User anotherParticipant;
    private Category category;
    private Region region;
    private MockHttpSession organizerSession;
    private MockHttpSession participantSession;
    private MockHttpSession anotherParticipantSession;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트 사용자 생성
        organizer = User.create("organizer@example.com", passwordEncoder.encode("password123"), "organizer");
        userRepository.save(organizer);

        participant = User.create("participant@example.com", passwordEncoder.encode("password123"), "participant");
        userRepository.save(participant);

        anotherParticipant = User.create("another@example.com", passwordEncoder.encode("password123"), "another");
        userRepository.save(anotherParticipant);

        // 카테고리/지역 생성
        category = Category.create("러닝");
        categoryRepository.save(category);

        region = Region.createDistrict("SEOUL_GANGNAM", "강남구", null);
        regionRepository.save(region);

        // 세션 획득
        organizerSession = loginAndGetSession("organizer@example.com", "password123");
        participantSession = loginAndGetSession("participant@example.com", "password123");
        anotherParticipantSession = loginAndGetSession("another@example.com", "password123");
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

    @Nested
    @DisplayName("AC-PART-01: 중복 신청 방지")
    class DuplicateParticipationTest {

        @Test
        @DisplayName("정상적인 참여 신청 성공")
        void requestParticipation_success() throws Exception {
            Meetup meetup = createOpenMeetup(10);

            mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/participations")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.meetupId").value(meetup.getId()))
                    .andExpect(jsonPath("$.data.userId").value(participant.getId()))
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"));
        }

        @Test
        @DisplayName("동일 모임 중복 신청 시 PART-409-DUPLICATE")
        void requestParticipation_duplicate_returns409() throws Exception {
            Meetup meetup = createOpenMeetup(10);

            // 첫 번째 신청 성공
            mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/participations")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isCreated());

            // 두 번째 신청 실패
            mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/participations")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PART-409-DUPLICATE"));
        }

        @Test
        @DisplayName("세션 없이 참여 신청 시 401")
        void requestParticipation_withoutSession_returns401() throws Exception {
            Meetup meetup = createOpenMeetup(10);

            mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/participations")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("AC-MEETUP-03: 모집 마감 후 신청/승인 금지")
    class DeadlineTest {

        @Test
        @DisplayName("모집 마감 후 신청 시 MEETUP-409-DEADLINE")
        void requestParticipation_afterDeadline_returns409() throws Exception {
            // 모집 마감일이 과거인 모임 생성
            Meetup meetup = createMeetupWithPastDeadline();

            mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/participations")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEETUP-409-DEADLINE"));
        }

        @Test
        @DisplayName("모집 마감 후 승인 시 MEETUP-409-DEADLINE")
        void approveParticipation_afterDeadline_returns409() throws Exception {
            // 모집 마감 전 모임 생성 및 참여 신청
            Meetup meetup = createOpenMeetup(10);
            Participation participation = createParticipation(meetup.getId(), participant.getId());

            // 모집 마감일을 과거로 변경 (테스트용)
            updateMeetupRecruitEndAt(meetup.getId(), LocalDateTime.now().minusDays(1));

            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + participation.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEETUP-409-DEADLINE"));
        }
    }

    @Nested
    @DisplayName("AC-PART-03: APPROVED 취소 후 정원 로직")
    class ApprovedCancellationTest {

        @Test
        @DisplayName("REQUESTED 상태 참가자 취소 성공")
        void cancelParticipation_requested_success() throws Exception {
            Meetup meetup = createOpenMeetup(10);

            // 참여 신청
            mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/participations")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isCreated());

            // 취소
            mockMvc.perform(delete("/api/meetups/" + meetup.getId() + "/participations/me")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            // 상태 확인
            Participation participation = participationRepository
                    .findByMeetupIdAndUserId(meetup.getId(), participant.getId())
                    .orElseThrow();
            assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        }

        @Test
        @DisplayName("APPROVED 참가자 취소 후 CANCELLED 상태")
        void cancelParticipation_approved_becomesCancelled() throws Exception {
            Meetup meetup = createOpenMeetup(10);
            Participation participation = createParticipation(meetup.getId(), participant.getId());

            // 승인
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + participation.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isOk());

            // 취소
            mockMvc.perform(delete("/api/meetups/" + meetup.getId() + "/participations/me")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            // 상태 확인
            Participation updated = participationRepository.findById(participation.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        }

        @Test
        @DisplayName("APPROVED 취소 후 다른 참가자 승인 가능 (정원 로직 일관성)")
        void cancelParticipation_approved_allowsNewApproval() throws Exception {
            // 정원 1명 모임 생성
            Meetup meetup = createOpenMeetup(1);

            // 첫 번째 참가자 신청 및 승인
            Participation first = createParticipation(meetup.getId(), participant.getId());
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + first.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isOk());

            // 두 번째 참가자 신청
            Participation second = createParticipation(meetup.getId(), anotherParticipant.getId());

            // 두 번째 참가자 승인 시도 → 정원 초과
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + second.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PART-409-CAPACITY"));

            // 첫 번째 참가자 취소
            mockMvc.perform(delete("/api/meetups/" + meetup.getId() + "/participations/me")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            // 두 번째 참가자 승인 성공
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + second.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
        }
    }

    @Nested
    @DisplayName("AC-PART-04: 허용되지 않는 상태 전이")
    class InvalidStateTransitionTest {

        @Test
        @DisplayName("CANCELLED 상태에서 승인 시 PART-409-STATE")
        void approveParticipation_cancelled_returns409() throws Exception {
            Meetup meetup = createOpenMeetup(10);
            Participation participation = createParticipation(meetup.getId(), participant.getId());

            // 취소
            participation.cancel();
            participationRepository.save(participation);

            // 승인 시도
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + participation.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PART-409-STATE"));
        }

        @Test
        @DisplayName("REJECTED 상태에서 승인 시 PART-409-STATE")
        void approveParticipation_rejected_returns409() throws Exception {
            Meetup meetup = createOpenMeetup(10);
            Participation participation = createParticipation(meetup.getId(), participant.getId());

            // 거절
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + participation.getId() + "/reject")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isOk());

            // 승인 시도
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + participation.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PART-409-STATE"));
        }

        @Test
        @DisplayName("이미 취소된 참여 취소 시 PART-409-STATE")
        void cancelParticipation_alreadyCancelled_returns409() throws Exception {
            Meetup meetup = createOpenMeetup(10);

            // 신청
            mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/participations")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isCreated());

            // 첫 번째 취소 성공
            mockMvc.perform(delete("/api/meetups/" + meetup.getId() + "/participations/me")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            // 두 번째 취소 실패
            mockMvc.perform(delete("/api/meetups/" + meetup.getId() + "/participations/me")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PART-409-STATE"));
        }
    }

    @Nested
    @DisplayName("AC-AUTH-03: Organizer 권한")
    class OrganizerAuthorizationTest {

        @Test
        @DisplayName("Organizer만 승인 가능")
        void approveParticipation_byNonOrganizer_returns403() throws Exception {
            Meetup meetup = createOpenMeetup(10);
            Participation participation = createParticipation(meetup.getId(), participant.getId());

            // 참가자가 승인 시도 (자신의 참여)
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + participation.getId() + "/approve")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH-403"));
        }

        @Test
        @DisplayName("Organizer만 거절 가능")
        void rejectParticipation_byNonOrganizer_returns403() throws Exception {
            Meetup meetup = createOpenMeetup(10);
            Participation participation = createParticipation(meetup.getId(), participant.getId());

            // 다른 참가자가 거절 시도
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + participation.getId() + "/reject")
                            .session(anotherParticipantSession)
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH-403"));
        }

        @Test
        @DisplayName("Organizer만 참여 목록 조회 가능")
        void getParticipations_byNonOrganizer_returns403() throws Exception {
            Meetup meetup = createOpenMeetup(10);

            mockMvc.perform(get("/api/meetups/" + meetup.getId() + "/participations")
                            .session(participantSession))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH-403"));
        }

        @Test
        @DisplayName("Organizer 참여 목록 조회 성공")
        void getParticipations_byOrganizer_success() throws Exception {
            Meetup meetup = createOpenMeetup(10);
            createParticipation(meetup.getId(), participant.getId());
            createParticipation(meetup.getId(), anotherParticipant.getId());

            mockMvc.perform(get("/api/meetups/" + meetup.getId() + "/participations")
                            .session(organizerSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalCount").value(2))
                    .andExpect(jsonPath("$.data.capacity").value(10))
                    .andExpect(jsonPath("$.data.participations").isArray());
        }
    }

    @Nested
    @DisplayName("AC-PART-02 기본: 정원 초과 방지")
    class CapacityTest {

        @Test
        @DisplayName("정원 초과 시 승인 거부")
        void approveParticipation_capacityExceeded_returns409() throws Exception {
            // 정원 2명 모임 생성
            Meetup meetup = createOpenMeetup(2);

            // 3명 신청
            Participation p1 = createParticipation(meetup.getId(), participant.getId());
            Participation p2 = createParticipation(meetup.getId(), anotherParticipant.getId());

            // 새 사용자 생성 및 신청
            User third = User.create("third@example.com", passwordEncoder.encode("password123"), "third");
            userRepository.save(third);
            Participation p3 = createParticipation(meetup.getId(), third.getId());

            // 2명 승인
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + p1.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isOk());

            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + p2.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isOk());

            // 3번째 승인 시도 → 정원 초과
            mockMvc.perform(patch("/api/meetups/" + meetup.getId() + "/participations/" + p3.getId() + "/approve")
                            .session(organizerSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PART-409-CAPACITY"));
        }
    }

    @Nested
    @DisplayName("모임 상태 검증")
    class MeetupStatusTest {

        @Test
        @DisplayName("OPEN이 아닌 모임에 신청 시 MEETUP-409-STATE")
        void requestParticipation_notOpenMeetup_returns409() throws Exception {
            // DRAFT 상태 모임 생성
            Meetup meetup = createDraftMeetup();

            mockMvc.perform(post("/api/meetups/" + meetup.getId() + "/participations")
                            .session(participantSession)
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEETUP-409-STATE"));
        }
    }

    // === Helper Methods ===

    private Meetup createOpenMeetup(int capacity) {
        LocalDateTime now = LocalDateTime.now();
        Meetup meetup = Meetup.create(
            organizer.getId(), "테스트 모임", "설명", category.getId(), region.getCode(),
            "장소", now.plusDays(7), now.plusDays(7).plusHours(2), now.plusDays(6),
            capacity, null
        );
        meetup.transitionTo(MeetupStatus.OPEN);
        return meetupRepository.save(meetup);
    }

    private Meetup createDraftMeetup() {
        LocalDateTime now = LocalDateTime.now();
        Meetup meetup = Meetup.create(
            organizer.getId(), "테스트 모임", "설명", category.getId(), region.getCode(),
            "장소", now.plusDays(7), now.plusDays(7).plusHours(2), now.plusDays(6),
            10, null
        );
        return meetupRepository.save(meetup);
    }

    private Meetup createMeetupWithPastDeadline() {
        LocalDateTime now = LocalDateTime.now();
        Meetup meetup = Meetup.create(
            organizer.getId(), "마감된 모임", "설명", category.getId(), region.getCode(),
            "장소", now.plusDays(7), now.plusDays(7).plusHours(2), now.minusDays(1), // 어제가 마감
            10, null
        );
        meetup.transitionTo(MeetupStatus.OPEN);
        return meetupRepository.save(meetup);
    }

    private Participation createParticipation(Long meetupId, Long userId) {
        Participation participation = Participation.request(meetupId, userId);
        return participationRepository.save(participation);
    }

    private void updateMeetupRecruitEndAt(Long meetupId, LocalDateTime newRecruitEndAt) {
        // 테스트용으로 직접 SQL 업데이트 (JPA 엔티티는 수정 불가)
        meetupRepository.findById(meetupId).ifPresent(meetup -> {
            // Reflection으로 recruitEndAt 수정 (테스트 전용)
            try {
                java.lang.reflect.Field field = Meetup.class.getDeclaredField("recruitEndAt");
                field.setAccessible(true);
                field.set(meetup, newRecruitEndAt);
                meetupRepository.save(meetup);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
