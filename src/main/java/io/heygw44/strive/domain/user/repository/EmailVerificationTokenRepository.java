package io.heygw44.strive.domain.user.repository;

import io.heygw44.strive.domain.user.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, String> {

    Optional<EmailVerificationToken> findByIdAndUsedFalse(String id);

    void deleteByUserId(Long userId);
}
