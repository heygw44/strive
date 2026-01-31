package io.heygw44.strive.domain.meetup.service;

import io.heygw44.strive.domain.meetup.dto.MeetupListResponse;
import io.heygw44.strive.domain.meetup.dto.MeetupResponse;
import io.heygw44.strive.domain.meetup.entity.Category;
import io.heygw44.strive.domain.meetup.entity.Meetup;
import io.heygw44.strive.domain.meetup.entity.Region;
import io.heygw44.strive.domain.meetup.repository.CategoryRepository;
import io.heygw44.strive.domain.meetup.repository.RegionRepository;
import io.heygw44.strive.domain.user.entity.User;
import io.heygw44.strive.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 모임 응답 DTO 조립기 (조회 전용 책임 분리)
 */
@Component
@RequiredArgsConstructor
public class MeetupResponseAssembler {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RegionRepository regionRepository;

    public MeetupResponse toMeetupResponse(Meetup meetup) {
        String organizerNickname = userRepository.findById(meetup.getOrganizerId())
            .map(User::getNickname)
            .orElse("알 수 없음");

        String categoryName = categoryRepository.findById(meetup.getCategoryId())
            .map(Category::getName)
            .orElse("알 수 없음");

        String regionName = regionRepository.findById(meetup.getRegionCode())
            .map(Region::getName)
            .orElse("알 수 없음");

        return MeetupResponse.from(meetup, organizerNickname, categoryName, regionName);
    }

    /**
     * 목록 응답 생성 (N+1 방지: 배치 조회)
     */
    public List<MeetupListResponse> toMeetupListResponses(List<Meetup> meetups) {
        if (meetups.isEmpty()) {
            return List.of();
        }

        // 카테고리 ID 수집 및 배치 조회
        Set<Long> categoryIds = meetups.stream()
            .map(Meetup::getCategoryId)
            .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = categoryRepository.findAllById(categoryIds).stream()
            .collect(Collectors.toMap(Category::getId, Category::getName));

        // 지역 코드 수집 및 배치 조회
        Set<String> regionCodes = meetups.stream()
            .map(Meetup::getRegionCode)
            .collect(Collectors.toSet());
        Map<String, String> regionNameMap = regionRepository.findAllById(regionCodes).stream()
            .collect(Collectors.toMap(Region::getCode, Region::getName));

        return meetups.stream()
            .map(meetup -> MeetupListResponse.from(
                meetup,
                categoryNameMap.getOrDefault(meetup.getCategoryId(), "알 수 없음"),
                regionNameMap.getOrDefault(meetup.getRegionCode(), "알 수 없음")
            ))
            .toList();
    }
}
