package com.aquilabuddy.domain.user;

import java.util.List;

/**
 * 현재 사용자의 기여 추천 맥락을 구성하는 프로필 도메인 모델입니다.
 */
public record UserProfile(
    long githubId,
    String login,
    String name,
    String email,
    List<String> primaryLanguages,
    List<String> topics,
    boolean profileReady,
    String vectorUpdatedAt
) {
}
