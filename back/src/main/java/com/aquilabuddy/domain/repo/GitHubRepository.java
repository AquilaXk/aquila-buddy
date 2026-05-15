package com.aquilabuddy.domain.repo;

/**
 * 외부 저장소 연동에서 공통으로 사용하는 GitHub repository 식별 모델입니다.
 */
public record GitHubRepository(
    String owner,
    String name,
    String defaultBranch
) {
}
