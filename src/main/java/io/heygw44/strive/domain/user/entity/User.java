package io.heygw44.strive.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(name = "bio_text", length = 500)
    private String bioText;

    @Convert(converter = StringListConverter.class)
    @Column(name = "preferred_categories", columnDefinition = "JSON")
    private List<String> preferredCategories;

    @Column(name = "home_region_code", length = 50)
    private String homeRegionCode;

    @Column(name = "experience_level", length = 20)
    private String experienceLevel;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.isVerified = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static User create(String email, String passwordHash, String nickname) {
        return new User(email, passwordHash, nickname);
    }

    public void updateProfile(String nickname, String bioText, List<String> preferredCategories,
                              String homeRegionCode, String experienceLevel) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        this.bioText = bioText;
        this.preferredCategories = preferredCategories;
        this.homeRegionCode = homeRegionCode;
        this.experienceLevel = experienceLevel;
        this.updatedAt = LocalDateTime.now();
    }

    public void verifyEmail() {
        this.isVerified = true;
        this.updatedAt = LocalDateTime.now();
    }

    // Lombok @Getter는 boolean 필드에 대해 isXxx() 생성
    // isVerified 필드는 isVerified() 또는 isIsVerified()가 될 수 있어 명시적으로 정의
    public boolean isVerified() {
        return isVerified;
    }
}
