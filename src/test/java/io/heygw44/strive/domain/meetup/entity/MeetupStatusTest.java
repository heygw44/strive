package io.heygw44.strive.domain.meetup.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MeetupStatus 상태 전이 테스트")
class MeetupStatusTest {

    @Nested
    @DisplayName("DRAFT 상태에서")
    class FromDraft {

        @Test
        @DisplayName("OPEN으로 전이 가능")
        void canTransitionToOpen() {
            assertThat(MeetupStatus.DRAFT.canTransitionTo(MeetupStatus.OPEN)).isTrue();
        }

        @Test
        @DisplayName("CLOSED로 전이 불가")
        void cannotTransitionToClosed() {
            assertThat(MeetupStatus.DRAFT.canTransitionTo(MeetupStatus.CLOSED)).isFalse();
        }

        @Test
        @DisplayName("COMPLETED로 전이 불가")
        void cannotTransitionToCompleted() {
            assertThat(MeetupStatus.DRAFT.canTransitionTo(MeetupStatus.COMPLETED)).isFalse();
        }

        @Test
        @DisplayName("CANCELLED로 전이 불가")
        void cannotTransitionToCancelled() {
            assertThat(MeetupStatus.DRAFT.canTransitionTo(MeetupStatus.CANCELLED)).isFalse();
        }
    }

    @Nested
    @DisplayName("OPEN 상태에서")
    class FromOpen {

        @Test
        @DisplayName("CLOSED로 전이 가능")
        void canTransitionToClosed() {
            assertThat(MeetupStatus.OPEN.canTransitionTo(MeetupStatus.CLOSED)).isTrue();
        }

        @Test
        @DisplayName("CANCELLED로 전이 가능")
        void canTransitionToCancelled() {
            assertThat(MeetupStatus.OPEN.canTransitionTo(MeetupStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("DRAFT로 전이 불가")
        void cannotTransitionToDraft() {
            assertThat(MeetupStatus.OPEN.canTransitionTo(MeetupStatus.DRAFT)).isFalse();
        }

        @Test
        @DisplayName("COMPLETED로 전이 불가 (CLOSED 거쳐야 함)")
        void cannotTransitionToCompleted() {
            assertThat(MeetupStatus.OPEN.canTransitionTo(MeetupStatus.COMPLETED)).isFalse();
        }
    }

    @Nested
    @DisplayName("CLOSED 상태에서")
    class FromClosed {

        @Test
        @DisplayName("COMPLETED로 전이 가능")
        void canTransitionToCompleted() {
            assertThat(MeetupStatus.CLOSED.canTransitionTo(MeetupStatus.COMPLETED)).isTrue();
        }

        @Test
        @DisplayName("CANCELLED로 전이 가능")
        void canTransitionToCancelled() {
            assertThat(MeetupStatus.CLOSED.canTransitionTo(MeetupStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("OPEN으로 전이 불가")
        void cannotTransitionToOpen() {
            assertThat(MeetupStatus.CLOSED.canTransitionTo(MeetupStatus.OPEN)).isFalse();
        }

        @Test
        @DisplayName("DRAFT로 전이 불가")
        void cannotTransitionToDraft() {
            assertThat(MeetupStatus.CLOSED.canTransitionTo(MeetupStatus.DRAFT)).isFalse();
        }
    }

    @Nested
    @DisplayName("COMPLETED 상태에서")
    class FromCompleted {

        @Test
        @DisplayName("어떤 상태로도 전이 불가 (종료 상태)")
        void cannotTransitionToAny() {
            for (MeetupStatus status : MeetupStatus.values()) {
                assertThat(MeetupStatus.COMPLETED.canTransitionTo(status)).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("CANCELLED 상태에서")
    class FromCancelled {

        @Test
        @DisplayName("어떤 상태로도 전이 불가 (종료 상태)")
        void cannotTransitionToAny() {
            for (MeetupStatus status : MeetupStatus.values()) {
                assertThat(MeetupStatus.CANCELLED.canTransitionTo(status)).isFalse();
            }
        }
    }
}
