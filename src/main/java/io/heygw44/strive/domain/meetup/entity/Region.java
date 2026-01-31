package io.heygw44.strive.domain.meetup.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지역 엔티티
 * 시/구(또는 시/군/구) 2단계 수준
 * 예: SEOUL(서울특별시), SEOUL_GANGNAM(강남구)
 */
@Entity
@Table(name = "region")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_code", length = 50)
    private String parentCode;

    private Region(String code, String name, String parentCode) {
        this.code = code;
        this.name = name;
        this.parentCode = parentCode;
    }

    /**
     * 시/도 지역 생성 (최상위)
     */
    public static Region createCity(String code, String name) {
        return new Region(code, name, null);
    }

    /**
     * 구/군 지역 생성 (하위)
     */
    public static Region createDistrict(String code, String name, String parentCode) {
        return new Region(code, name, parentCode);
    }

    /**
     * 최상위 지역(시/도)인지 확인
     */
    public boolean isTopLevel() {
        return parentCode == null;
    }
}
