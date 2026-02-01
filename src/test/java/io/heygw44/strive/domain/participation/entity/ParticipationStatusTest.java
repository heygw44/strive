package io.heygw44.strive.domain.participation.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ParticipationStatus 상태 전이 테스트")
class ParticipationStatusTest {

    @Nested
    @DisplayName("REQUESTED 상태에서")
    class FromRequested {

        @Test
        @DisplayName("APPROVED로 전이 가능")
        void canTransitionToApproved() {
            assertThat(ParticipationStatus.REQUESTED.canTransitionTo(ParticipationStatus.APPROVED)).isTrue();
        }

        @Test
        @DisplayName("REJECTED로 전이 가능")
        void canTransitionToRejected() {
            assertThat(ParticipationStatus.REQUESTED.canTransitionTo(ParticipationStatus.REJECTED)).isTrue();
        }

        @Test
        @DisplayName("CANCELLED로 전이 가능")
        void canTransitionToCancelled() {
            assertThat(ParticipationStatus.REQUESTED.canTransitionTo(ParticipationStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("자기 자신으로 전이 불가")
        void cannotTransitionToSelf() {
            assertThat(ParticipationStatus.REQUESTED.canTransitionTo(ParticipationStatus.REQUESTED)).isFalse();
        }
    }

    @Nested
    @DisplayName("APPROVED 상태에서")
    class FromApproved {

        @Test
        @DisplayName("CANCELLED로 전이 가능 (AC-PART-03)")
        void canTransitionToCancelled() {
            assertThat(ParticipationStatus.APPROVED.canTransitionTo(ParticipationStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("REQUESTED로 전이 불가")
        void cannotTransitionToRequested() {
            assertThat(ParticipationStatus.APPROVED.canTransitionTo(ParticipationStatus.REQUESTED)).isFalse();
        }

        @Test
        @DisplayName("REJECTED로 전이 불가 (이미 승인된 상태)")
        void cannotTransitionToRejected() {
            assertThat(ParticipationStatus.APPROVED.canTransitionTo(ParticipationStatus.REJECTED)).isFalse();
        }

        @Test
        @DisplayName("자기 자신으로 전이 불가")
        void cannotTransitionToSelf() {
            assertThat(ParticipationStatus.APPROVED.canTransitionTo(ParticipationStatus.APPROVED)).isFalse();
        }
    }

    @Nested
    @DisplayName("REJECTED 상태에서")
    class FromRejected {

        @Test
        @DisplayName("어떤 상태로도 전이 불가 (종료 상태, AC-PART-04)")
        void cannotTransitionToAny() {
            for (ParticipationStatus status : ParticipationStatus.values()) {
                assertThat(ParticipationStatus.REJECTED.canTransitionTo(status)).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("CANCELLED 상태에서")
    class FromCancelled {

        @Test
        @DisplayName("어떤 상태로도 전이 불가 (종료 상태, AC-PART-04)")
        void cannotTransitionToAny() {
            for (ParticipationStatus status : ParticipationStatus.values()) {
                assertThat(ParticipationStatus.CANCELLED.canTransitionTo(status)).isFalse();
            }
        }
    }
}
