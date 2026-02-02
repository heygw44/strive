package io.heygw44.strive.domain.participation.service;

import io.heygw44.strive.domain.meetup.entity.Category;
import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.Region;
import io.heygw44.strive.domain.meetup.repository.CategoryRepository;
import io.heygw44.strive.domain.meetup.repository.MeetupRepository;
import io.heygw44.strive.domain.meetup.repository.RegionRepository;
import io.heygw44.strive.domain.participation.entity.Participation;
import io.heygw44.strive.domain.participation.entity.ParticipationStatus;
import io.heygw44.strive.domain.participation.repository.ParticipationRepository;
import io.heygw44.strive.domain.user.entity.User;
import io.heygw44.strive.domain.user.repository.UserRepository;
import io.heygw44.strive.support.ConcurrencyTestHelper;
import io.heygw44.strive.support.ConcurrencyTestHelper.ExecutionResult;
import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Participation 동시성 테스트")
class ParticipationConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(ParticipationConcurrencyTest.class);
    private static final Duration QUICK_START_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration QUICK_DONE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DONE_TIMEOUT = Duration.ofSeconds(180);

    @Autowired
    private ParticipationService participationService;

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
    private Category category;
    private Region region;
    private Meetup meetup;

    @BeforeEach
    void setUp() {
        organizer = User.create("organizer-concurrent@example.com",
            passwordEncoder.encode("password123"), "organizer-concurrent");
        userRepository.save(organizer);

        participant = User.create("participant-concurrent@example.com",
            passwordEncoder.encode("password123"), "participant-concurrent");
        userRepository.save(participant);

        category = Category.create("러닝");
        categoryRepository.save(category);

        region = Region.createDistrict("SEOUL_GANGNAM", "강남구", null);
        regionRepository.save(region);

        LocalDateTime now = LocalDateTime.now();
        meetup = Meetup.create(
            organizer.getId(), "동시성 테스트 모임", "설명", category.getId(), region.getCode(),
            "장소", now.plusDays(7), now.plusDays(7).plusHours(2), now.plusDays(6),
            10, null
        );
        meetup.publish();
        meetupRepository.save(meetup);
    }

    @AfterEach
    void tearDown() {
        participationRepository.deleteAll();
        meetupRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
    }

    @Test
    @DisplayName("동일 사용자 동시 신청은 1건만 성공하고 나머지는 PART-409-DUPLICATE")
    void requestParticipation_concurrent_duplicate() throws Exception {
        int threadCount = 10;
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    participationService.requestParticipation(meetup.getId(), participant.getId());
                    successCount.incrementAndGet();
                } catch (BusinessException ex) {
                    if (ex.getErrorCode() == ErrorCode.PARTICIPATION_DUPLICATE) {
                        duplicateCount.incrementAndGet();
                        return;
                    }
                    throw ex;
                }
            });
        }

        ExecutionResult result = ConcurrencyTestHelper.runConcurrently(
            tasks, threadCount, QUICK_START_TIMEOUT, QUICK_DONE_TIMEOUT);
        result.logErrors(log);
        assertThat(result.errors()).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(threadCount - 1);
    }

    // ==========================================================================
    // AC-PART-02: 동시 승인 테스트 (정원 초과 0건 보장)
    // ==========================================================================

    @Test
    @DisplayName("AC-PART-02: 정원 10명에 100명 동시 승인 요청 시 정원 초과 0건")
    void approveParticipation_concurrent100Requests_maxApproved10() throws Exception {
        // Given: capacity=10인 모임, 100명의 REQUESTED 참가자
        int capacity = 10;
        int requestCount = 100;
        int threadCount = requestCount; // 모든 요청을 동시에 시작하기 위해 스레드 풀 크기 = 요청 수

        Meetup testMeetup = createMeetupWithCapacity(capacity);
        List<User> participants = createUsers(requestCount);
        List<Participation> participations = createParticipations(testMeetup, participants);

        // When: 100개의 동시 승인 요청
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger capacityExceededCount = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>(participations.size());

        for (Participation p : participations) {
            tasks.add(() -> {
                try {
                    participationService.approveParticipation(
                        testMeetup.getId(), p.getId(), organizer.getId());
                    successCount.incrementAndGet();
                } catch (BusinessException ex) {
                    if (ex.getErrorCode() == ErrorCode.PARTICIPATION_CAPACITY_EXCEEDED) {
                        capacityExceededCount.incrementAndGet();
                        return;
                    }
                    throw ex;
                }
            });
        }

        // Then: 검증
        ExecutionResult result = ConcurrencyTestHelper.runConcurrently(
            tasks, threadCount, START_TIMEOUT, DONE_TIMEOUT);
        result.logErrors(log);
        assertThat(result.errors()).isEmpty();

        // APPROVED는 정확히 capacity만큼
        long approvedCount = participationRepository.countByMeetupIdAndStatus(
            testMeetup.getId(), ParticipationStatus.APPROVED);
        assertThat(approvedCount).isEqualTo(capacity);

        // 성공 카운트도 capacity와 일치
        assertThat(successCount.get()).isEqualTo(capacity);

        // 나머지는 PART-409-CAPACITY
        assertThat(capacityExceededCount.get()).isEqualTo(requestCount - capacity);
        assertThat(successCount.get() + capacityExceededCount.get()).isEqualTo(requestCount);

        log.info("AC-PART-02 동시성 테스트 결과: 성공={}, 정원초과={}, APPROVED={}",
            successCount.get(), capacityExceededCount.get(), approvedCount);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 20",    // 정원 1명, 20명 동시 요청
        "5, 50",    // 정원 5명, 50명 동시 요청
        "20, 50"    // 정원 20명, 50명 동시 요청
    })
    @DisplayName("AC-PART-02: 다양한 정원 크기에서 동시 승인 시 정원 초과 0건")
    void approveParticipation_variousCapacity_noOverflow(int capacity, int requestCount) throws Exception {
        // Given
        Meetup testMeetup = createMeetupWithCapacity(capacity);
        List<User> participants = createUsers(requestCount);
        List<Participation> participations = createParticipations(testMeetup, participants);

        // When: 동시 승인 (스레드 풀 크기 = 요청 수로 동시성 최대화)
        int threadCount = requestCount;
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger capacityExceededCount = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>(participations.size());

        for (Participation p : participations) {
            tasks.add(() -> {
                try {
                    participationService.approveParticipation(
                        testMeetup.getId(), p.getId(), organizer.getId());
                    successCount.incrementAndGet();
                } catch (BusinessException ex) {
                    if (ex.getErrorCode() == ErrorCode.PARTICIPATION_CAPACITY_EXCEEDED) {
                        capacityExceededCount.incrementAndGet();
                        return;
                    }
                    throw ex;
                }
            });
        }

        // Then: 검증
        ExecutionResult result = ConcurrencyTestHelper.runConcurrently(
            tasks, threadCount, START_TIMEOUT, DONE_TIMEOUT);
        result.logErrors(log);
        assertThat(result.errors()).isEmpty();

        // APPROVED는 정확히 min(capacity, requestCount)
        long approvedCount = participationRepository.countByMeetupIdAndStatus(
            testMeetup.getId(), ParticipationStatus.APPROVED);
        int expectedApproved = Math.min(capacity, requestCount);
        assertThat(approvedCount).isEqualTo(expectedApproved);
        assertThat(successCount.get()).isEqualTo(expectedApproved);
        assertThat(capacityExceededCount.get()).isEqualTo(requestCount - expectedApproved);
        assertThat(successCount.get() + capacityExceededCount.get()).isEqualTo(requestCount);

        log.info("다양한 정원 테스트 결과: capacity={}, requests={}, 성공={}, 정원초과={}, APPROVED={}",
            capacity, requestCount, successCount.get(), capacityExceededCount.get(), approvedCount);
    }

    // ==========================================================================
    // AC-PART-03: 취소 + 승인 동시성 테스트 (정원 로직 일관성)
    // ==========================================================================

    @Test
    @DisplayName("AC-PART-03: APPROVED 취소와 새 승인 동시 발생 시 정원 일관성")
    void cancelAndApprove_concurrent_maintainsCapacityConsistency() throws Exception {
        // Given: 정원 10명 만석 상태
        int capacity = 10;
        Meetup testMeetup = createMeetupWithCapacity(capacity);

        // 10명 APPROVED 생성
        List<User> approvedParticipants = createUsers(capacity);
        List<Participation> approvedParticipations = createApprovedParticipations(testMeetup, approvedParticipants);

        // 10명 REQUESTED 대기자 생성
        List<User> waitingParticipants = createUsers(capacity);
        List<Participation> waitingParticipations = createParticipations(testMeetup, waitingParticipants);

        // When: 5명 취소 + 10명 승인 동시 요청
        int cancelCount = 5;
        int approveRequestCount = waitingParticipations.size();
        int totalRequests = cancelCount + approveRequestCount;

        AtomicInteger cancelSuccessCount = new AtomicInteger();
        AtomicInteger approveSuccessCount = new AtomicInteger();
        AtomicInteger capacityExceededCount = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>(totalRequests);

        // 취소 요청 (5명)
        for (int i = 0; i < cancelCount; i++) {
            User user = approvedParticipants.get(i);
            tasks.add(() -> {
                participationService.cancelParticipation(testMeetup.getId(), user.getId());
                cancelSuccessCount.incrementAndGet();
            });
        }

        // 승인 요청 (10명)
        for (Participation p : waitingParticipations) {
            tasks.add(() -> {
                try {
                    participationService.approveParticipation(
                        testMeetup.getId(), p.getId(), organizer.getId());
                    approveSuccessCount.incrementAndGet();
                } catch (BusinessException ex) {
                    if (ex.getErrorCode() == ErrorCode.PARTICIPATION_CAPACITY_EXCEEDED) {
                        capacityExceededCount.incrementAndGet();
                        return;
                    }
                    throw ex;
                }
            });
        }

        // Then: APPROVED는 항상 capacity 이하
        ExecutionResult result = ConcurrencyTestHelper.runConcurrently(
            tasks, totalRequests, START_TIMEOUT, DONE_TIMEOUT);
        result.logErrors(log);
        assertThat(result.errors()).isEmpty();

        long finalApprovedCount = participationRepository.countByMeetupIdAndStatus(
            testMeetup.getId(), ParticipationStatus.APPROVED);
        assertThat(finalApprovedCount).isLessThanOrEqualTo(capacity);

        // 취소는 모두 성공
        assertThat(cancelSuccessCount.get()).isEqualTo(cancelCount);

        // 승인 성공 + 정원초과 = 전체 승인 요청
        assertThat(approveSuccessCount.get() + capacityExceededCount.get())
            .isEqualTo(approveRequestCount);
        assertThat(approveSuccessCount.get()).isLessThanOrEqualTo(cancelCount);

        log.info("AC-PART-03 취소+승인 동시 테스트: 취소성공={}, 승인성공={}, 정원초과={}, 최종APPROVED={}",
            cancelSuccessCount.get(), approveSuccessCount.get(),
            capacityExceededCount.get(), finalApprovedCount);
    }

    // ==========================================================================
    // 헬퍼 메서드
    // ==========================================================================

    /**
     * 지정된 정원의 모임 생성
     */
    private Meetup createMeetupWithCapacity(int capacity) {
        LocalDateTime now = LocalDateTime.now();
        Meetup newMeetup = Meetup.create(
            organizer.getId(),
            "동시성 테스트 모임-" + UUID.randomUUID().toString().substring(0, 8),
            "설명",
            category.getId(),
            region.getCode(),
            "장소",
            now.plusDays(7),
            now.plusDays(7).plusHours(2),
            now.plusDays(6),
            capacity,
            null
        );
        newMeetup.publish();
        return meetupRepository.save(newMeetup);
    }

    /**
     * 다수 사용자 생성
     */
    private List<User> createUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            User user = User.create(
                "participant-" + uniqueId + "@example.com",
                passwordEncoder.encode("password123"),
                "participant-" + uniqueId
            );
            users.add(userRepository.save(user));
        }
        return users;
    }

    /**
     * 다수 REQUESTED 참여 생성
     */
    private List<Participation> createParticipations(Meetup targetMeetup, List<User> users) {
        List<Participation> participations = new ArrayList<>();
        for (User user : users) {
            Participation p = Participation.request(targetMeetup.getId(), user.getId());
            participations.add(participationRepository.save(p));
        }
        return participations;
    }

    /**
     * 다수 APPROVED 참여 생성
     */
    private List<Participation> createApprovedParticipations(Meetup targetMeetup, List<User> users) {
        List<Participation> participations = new ArrayList<>();
        for (User user : users) {
            Participation p = Participation.request(targetMeetup.getId(), user.getId());
            participationRepository.save(p);
            p.approve();
            participations.add(participationRepository.save(p));
        }
        return participations;
    }
}
