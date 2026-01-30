package io.heygw44.strive.domain.user.service;

import io.heygw44.strive.domain.user.dto.LoginRequest;
import io.heygw44.strive.domain.user.dto.LoginResponse;
import io.heygw44.strive.domain.user.dto.SignupRequest;
import io.heygw44.strive.domain.user.dto.SignupResponse;
import io.heygw44.strive.domain.user.entity.EmailVerificationToken;
import io.heygw44.strive.domain.user.entity.User;
import io.heygw44.strive.domain.user.repository.EmailVerificationTokenRepository;
import io.heygw44.strive.domain.user.repository.UserRepository;
import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("회원가입 테스트")
    class SignupTest {

        @Test
        @DisplayName("유효한 요청으로 회원가입 성공")
        void signup_withValidRequest_success() {
            // 준비
            SignupRequest request = new SignupRequest("test@example.com", "password123", "nickname");
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByNickname(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("hashedPassword");
            given(userRepository.save(any(User.class))).willAnswer(invocation -> {
                User user = invocation.getArgument(0);
                // ID 할당을 시뮬레이션
                return user;
            });

            // 실행
            SignupResponse response = authService.signup(request);

            // 검증
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.nickname()).isEqualTo("nickname");
            verify(passwordEncoder).encode("password123");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("비밀번호가 10자 미만이면 실패")
        void signup_withShortPassword_fails() {
            // 준비
            SignupRequest request = new SignupRequest("test@example.com", "short", "nickname");

            // 실행 및 검증
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PASSWORD_LENGTH);
        }

        @Test
        @DisplayName("이메일이 중복이면 실패")
        void signup_withDuplicateEmail_fails() {
            // 준비
            SignupRequest request = new SignupRequest("existing@example.com", "password123", "nickname");
            given(userRepository.existsByEmail("existing@example.com")).willReturn(true);

            // 실행 및 검증
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        }

        @Test
        @DisplayName("닉네임이 중복이면 실패")
        void signup_withDuplicateNickname_fails() {
            // 준비
            SignupRequest request = new SignupRequest("test@example.com", "password123", "existingNick");
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByNickname("existingNick")).willReturn(true);

            // 실행 및 검증
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Nested
    @DisplayName("로그인 테스트")
    class LoginTest {

        @Test
        @DisplayName("유효한 자격증명으로 로그인 성공")
        void authenticate_withValidCredentials_success() {
            // 준비
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            User user = User.create("test@example.com", "hashedPassword", "nickname");
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password123", "hashedPassword")).willReturn(true);

            // 실행
            LoginResponse response = authService.authenticate(request);

            // 검증
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.nickname()).isEqualTo("nickname");
            assertThat(response.isVerified()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 로그인 실패")
        void authenticate_withNonExistentEmail_fails() {
            // 준비
            LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");
            given(userRepository.findByEmail("nonexistent@example.com")).willReturn(Optional.empty());

            // 실행 및 검증
            assertThatThrownBy(() -> authService.authenticate(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인 실패")
        void authenticate_withWrongPassword_fails() {
            // 준비
            LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");
            User user = User.create("test@example.com", "hashedPassword", "nickname");
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPassword", "hashedPassword")).willReturn(false);

            // 실행 및 검증
            assertThatThrownBy(() -> authService.authenticate(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Nested
    @DisplayName("이메일 인증 테스트")
    class EmailVerificationTest {

        private User user;

        @BeforeEach
        void setUp() {
            user = User.create("test@example.com", "hashedPassword", "nickname");
        }

        @Test
        @DisplayName("인증 토큰 요청 성공")
        void requestEmailVerification_success() {
            // 준비
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(passwordEncoder.encode(anyString())).willReturn("hashedToken");
            given(tokenRepository.save(any(EmailVerificationToken.class))).willAnswer(invocation -> {
                return invocation.getArgument(0);
            });

            // 실행
            String tokenId = authService.requestEmailVerification(1L);

            // 검증
            assertThat(tokenId).isNotNull();
            verify(tokenRepository).deleteByUserId(1L);
            verify(tokenRepository).save(any(EmailVerificationToken.class));
        }

        @Test
        @DisplayName("이미 인증된 사용자는 토큰 요청 실패")
        void requestEmailVerification_alreadyVerified_fails() {
            // 준비
            user.verifyEmail();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            // 실행 및 검증
            assertThatThrownBy(() -> authService.requestEmailVerification(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
    }
}
