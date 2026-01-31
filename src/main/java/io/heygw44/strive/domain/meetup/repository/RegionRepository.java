package io.heygw44.strive.domain.meetup.repository;

import io.heygw44.strive.domain.meetup.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, String> {

    boolean existsByCode(String code);

    /**
     * 특정 상위 지역의 하위 지역 조회
     */
    List<Region> findByParentCode(String parentCode);

    /**
     * 최상위 지역(시/도) 조회
     */
    List<Region> findByParentCodeIsNull();
}
