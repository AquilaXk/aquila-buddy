package com.aquilabuddy.domain.user;

import java.util.List;
import java.util.Objects;

/**
 * 현재 사용자 프로필을 추천 맥락에 맞게 갱신하기 위한 도메인 요청 모델입니다.
 */
public record UserProfileUpdateRequest(
    String name,
    String email,
    List<String> primaryLanguages,
    List<String> topics
) {

    public UserProfileUpdateRequest {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
        primaryLanguages = List.copyOf(Objects.requireNonNull(primaryLanguages, "primaryLanguages must not be null"));
        topics = List.copyOf(Objects.requireNonNull(topics, "topics must not be null"));
    }
}
