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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateSignupRequest(request);

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(request.email(), passwordHash, request.nickname());

        User savedUser = userRepository.save(user);

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getNickname()
        );
    }

    private void validateSignupRequest(SignupRequest request) {
        if (request.password().length() < 10) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_LENGTH);
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    public LoginResponse authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.isVerified()
        );
    }

    @Transactional
    public String requestEmailVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (user.isVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        // 기존 토큰 삭제
        tokenRepository.deleteByUserId(userId);

        // 새 토큰 생성
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = passwordEncoder.encode(rawToken);

        EmailVerificationToken token = EmailVerificationToken.create(tokenHash, userId);
        EmailVerificationToken savedToken = tokenRepository.save(token);

        // 운영 환경에서는 이메일 발송, MVP에서는 로그 출력으로 대체
        log.info("이메일 인증 토큰 생성 - userId={}, tokenId={}, rawToken={}",
                userId, savedToken.getId(), rawToken);

        return savedToken.getId();
    }

    @Transactional
    public void confirmEmailVerification(Long userId, String tokenId, String rawToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (user.isVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        EmailVerificationToken token = tokenRepository.findByIdAndUsedFalse(tokenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID));

        if (!token.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        if (!token.isValid()) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        if (!passwordEncoder.matches(rawToken, token.getTokenHash())) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        token.markAsUsed();
        user.verifyEmail();

        log.info("이메일 인증 완료 - userId={}", userId);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
