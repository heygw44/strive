package io.heygw44.strive.domain.participation.service;

import io.heygw44.strive.domain.meetup.entity.Category;
import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.Region;
import io.heygw44.strive.domain.meetup.repository.CategoryRepository;
import io.heygw44.strive.domain.meetup.repository.MeetupRepository;
import io.heygw44.strive.domain.meetup.repository.RegionRepository;
import io.heygw44.strive.domain.participation.repository.ParticipationRepository;
import io.heygw44.strive.domain.user.entity.User;
import io.heygw44.strive.domain.user.repository.UserRepository;
import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Participation 동시성 테스트")
class ParticipationConcurrencyTest {

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
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        errors.add(new IllegalStateException("start latch timeout"));
                        return;
                    }
                    participationService.requestParticipation(meetup.getId(), participant.getId());
                    successCount.incrementAndGet();
                } catch (BusinessException ex) {
                    if (ex.getErrorCode() == ErrorCode.PARTICIPATION_DUPLICATE) {
                        duplicateCount.incrementAndGet();
                    } else {
                        errors.add(ex);
                    }
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        if (!ready.await(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            fail("ready latch timeout");
        }
        start.countDown();

        if (!done.await(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            fail("done latch timeout");
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(threadCount - 1);
    }
}
