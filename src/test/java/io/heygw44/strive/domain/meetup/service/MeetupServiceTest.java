package io.heygw44.strive.domain.meetup.service;

import io.heygw44.strive.domain.meetup.dto.CreateMeetupRequest;
import io.heygw44.strive.domain.meetup.dto.UpdateMeetupRequest;
import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.MeetupStatus;
import io.heygw44.strive.domain.meetup.repository.CategoryRepository;
import io.heygw44.strive.domain.meetup.repository.MeetupRepository;
import io.heygw44.strive.domain.meetup.repository.RegionRepository;
import io.heygw44.strive.domain.user.repository.UserRepository;
import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeetupService 단위 테스트")
class MeetupServiceTest {

    @InjectMocks
    private MeetupService meetupService;

    @Mock
    private MeetupRepository meetupRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeetupResponseAssembler meetupResponseAssembler;

    private CreateMeetupRequest validRequest;
    private Long organizerId;
    private Long categoryId;
    private String regionCode;

    @BeforeEach
    void setUp() {
        organizerId = 1L;
        categoryId = 1L;
        regionCode = "SEOUL_GANGNAM";

        LocalDateTime now = LocalDateTime.now();
        validRequest = new CreateMeetupRequest(
            "테스트 러닝 모임",
            "함께 러닝해요",
            categoryId,
            regionCode,
            "강남역 2번 출구",
            now.plusDays(7),      // startAt
            now.plusDays(7).plusHours(2),  // endAt
            now.plusDays(6),      // recruitEndAt (startAt 이전)
            10,
            "초보자 환영"
        );
    }

    @Nested
    @DisplayName("모임 생성")
    class CreateMeetup {

        @Test
        @DisplayName("유효한 요청으로 모임 생성 성공")
        void createMeetup_withValidRequest_success() {
            // given
            given(categoryRepository.existsById(categoryId)).willReturn(true);
            given(regionRepository.existsByCode(regionCode)).willReturn(true);
            given(meetupRepository.save(any(Meetup.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            Meetup result = meetupService.createMeetup(validRequest, organizerId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("테스트 러닝 모임");
            assertThat(result.getOrganizerId()).isEqualTo(organizerId);
            assertThat(result.getStatus()).isEqualTo(MeetupStatus.DRAFT);
            verify(meetupRepository).save(any(Meetup.class));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리로 생성 시 실패")
        void createMeetup_withInvalidCategory_throwsException() {
            // given
            given(categoryRepository.existsById(categoryId)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> meetupService.createMeetup(validRequest, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 지역으로 생성 시 실패")
        void createMeetup_withInvalidRegion_throwsException() {
            // given
            given(categoryRepository.existsById(categoryId)).willReturn(true);
            given(regionRepository.existsByCode(regionCode)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> meetupService.createMeetup(validRequest, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("recruitEndAt이 startAt 이후면 실패")
        void createMeetup_withInvalidRecruitEndAt_throwsException() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CreateMeetupRequest invalidRequest = new CreateMeetupRequest(
                "테스트 모임",
                "설명",
                categoryId,
                regionCode,
                "장소",
                now.plusDays(7),      // startAt
                now.plusDays(7).plusHours(2),
                now.plusDays(8),      // recruitEndAt > startAt (잘못됨)
                10,
                null
            );

            given(categoryRepository.existsById(categoryId)).willReturn(true);
            given(regionRepository.existsByCode(regionCode)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> meetupService.createMeetup(invalidRequest, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("startAt이 endAt 이후면 실패")
        void createMeetup_withInvalidTimeRange_throwsException() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CreateMeetupRequest invalidRequest = new CreateMeetupRequest(
                "테스트 모임",
                "설명",
                categoryId,
                regionCode,
                "장소",
                now.plusDays(7).plusHours(3),  // startAt > endAt (잘못됨)
                now.plusDays(7).plusHours(2),  // endAt
                now.plusDays(6),
                10,
                null
            );

            given(categoryRepository.existsById(categoryId)).willReturn(true);
            given(regionRepository.existsByCode(regionCode)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> meetupService.createMeetup(invalidRequest, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("모임 조회")
    class GetMeetup {

        @Test
        @DisplayName("존재하는 모임 조회 성공")
        void getMeetup_withExistingId_success() {
            // given
            Meetup meetup = createMeetup();
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            // when
            Meetup result = meetupService.getMeetup(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("테스트 모임");
        }

        @Test
        @DisplayName("AC-MEETUP-02: 존재하지 않는 모임 조회 시 RES-404")
        void getMeetup_withNonExistingId_throwsException() {
            // given
            given(meetupRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> meetupService.getMeetup(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("모임 수정")
    class UpdateMeetup {

        @Test
        @DisplayName("작성자가 모임 수정 성공")
        void updateMeetup_byOrganizer_success() {
            // given
            Meetup meetup = createMeetup(MeetupStatus.OPEN);
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            UpdateMeetupRequest request = new UpdateMeetupRequest(
                "수정된 제목",
                "수정된 설명",
                null,
                null,
                null
            );

            // when
            Meetup result = meetupService.updateMeetup(1L, request, organizerId);

            // then
            assertThat(result.getTitle()).isEqualTo("수정된 제목");
            assertThat(result.getDescription()).isEqualTo("수정된 설명");
        }

        @Test
        @DisplayName("AC-AUTH-03: 작성자가 아닌 사용자가 수정 시 AUTH-403")
        void updateMeetup_byNonOrganizer_throwsException() {
            // given
            Meetup meetup = createMeetup(MeetupStatus.OPEN);
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            UpdateMeetupRequest request = new UpdateMeetupRequest("수정", null, null, null, null);
            Long otherUserId = 999L;

            // when & then
            assertThatThrownBy(() -> meetupService.updateMeetup(1L, request, otherUserId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("상태 전이 요청 시 유효한 전이만 허용")
        void updateMeetup_withValidStatusTransition_success() {
            // given
            Meetup meetup = createMeetup(MeetupStatus.DRAFT);  // DRAFT 상태
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            UpdateMeetupRequest request = new UpdateMeetupRequest(null, null, null, null, MeetupStatus.OPEN);

            // when
            Meetup result = meetupService.updateMeetup(1L, request, organizerId);

            // then
            assertThat(result.getStatus()).isEqualTo(MeetupStatus.OPEN);
        }

        @Test
        @DisplayName("허용되지 않는 상태 전이 시 MEETUP-409-STATE")
        void updateMeetup_withInvalidStatusTransition_throwsException() {
            // given
            Meetup meetup = createMeetup(MeetupStatus.DRAFT);  // DRAFT 상태
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            // DRAFT -> COMPLETED는 불가
            UpdateMeetupRequest request = new UpdateMeetupRequest(null, null, null, null, MeetupStatus.COMPLETED);

            // when & then
            assertThatThrownBy(() -> meetupService.updateMeetup(1L, request, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEETUP_INVALID_STATE);
        }

        @Test
        @DisplayName("DRAFT 상태에서 필드 수정 시 MEETUP-409-STATE")
        void updateMeetup_inDraftWithFieldUpdate_throwsException() {
            // given
            Meetup meetup = createMeetup(MeetupStatus.DRAFT);
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            UpdateMeetupRequest request = new UpdateMeetupRequest("수정", null, null, null, null);

            // when & then
            assertThatThrownBy(() -> meetupService.updateMeetup(1L, request, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEETUP_INVALID_STATE);
        }

        @Test
        @DisplayName("변경 사항 없는 요청 시 REQ-400")
        void updateMeetup_withNoChanges_throwsException() {
            // given
            Meetup meetup = createMeetup(MeetupStatus.OPEN);
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            UpdateMeetupRequest request = new UpdateMeetupRequest(null, null, null, null, null);

            // when & then
            assertThatThrownBy(() -> meetupService.updateMeetup(1L, request, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("모임 삭제")
    class DeleteMeetup {

        @Test
        @DisplayName("작성자가 모임 소프트 삭제 성공")
        void deleteMeetup_byOrganizer_success() {
            // given
            Meetup meetup = createMeetup();
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            // when
            meetupService.deleteMeetup(1L, organizerId);

            // then
            assertThat(meetup.isDeleted()).isTrue();
            assertThat(meetup.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("AC-AUTH-03: 작성자가 아닌 사용자가 삭제 시 AUTH-403")
        void deleteMeetup_byNonOrganizer_throwsException() {
            // given
            Meetup meetup = createMeetup();
            given(meetupRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(meetup));

            Long otherUserId = 999L;

            // when & then
            assertThatThrownBy(() -> meetupService.deleteMeetup(1L, otherUserId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // 테스트용 Meetup 생성 헬퍼
    private Meetup createMeetup() {
        return createMeetup(MeetupStatus.DRAFT);
    }

    private Meetup createMeetup(MeetupStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Meetup meetup = Meetup.create(
            organizerId,
            "테스트 모임",
            "설명",
            categoryId,
            regionCode,
            "장소",
            now.plusDays(7),
            now.plusDays(7).plusHours(2),
            now.plusDays(6),
            10,
            null
        );
        if (status == MeetupStatus.OPEN) {
            meetup.transitionTo(MeetupStatus.OPEN);
        } else if (status == MeetupStatus.CLOSED) {
            meetup.transitionTo(MeetupStatus.OPEN);
            meetup.transitionTo(MeetupStatus.CLOSED);
        }
        return meetup;
    }
}
