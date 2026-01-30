package io.heygw44.strive.domain.user.controller;

import io.heygw44.strive.domain.user.dto.*;
import io.heygw44.strive.domain.user.service.AuthService;
import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import io.heygw44.strive.global.response.ApiResponse;
import io.heygw44.strive.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {

        SignupResponse response = authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // 인증 처리
        LoginResponse response = authService.authenticate(request);

        // 인증 토큰 생성 및 SecurityContext에 설정
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 세션 고정 방지 및 동시 세션 제어 적용
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        // SecurityContext 저장
        securityContextRepository.saveContext(SecurityContextHolder.getContext(), httpRequest, httpResponse);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/verify-email/request")
    public ResponseEntity<ApiResponse<String>> requestEmailVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String tokenId = authService.requestEmailVerification(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(tokenId));
    }

    @PostMapping("/verify-email/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmEmailVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody VerifyEmailConfirmRequest request) {

        // 클라이언트는 "tokenId:rawToken" 형식으로 전송
        // 실제 운영에서는 단일 복합 토큰 또는 URL 쿼리 파라미터로 전달 가능
        String[] parts = request.token().split(":");
        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        String tokenId = parts[0];
        String rawToken = parts[1];

        authService.confirmEmailVerification(userDetails.getUserId(), tokenId, rawToken);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
